/* Copyright (C) 2013 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 * 
 * LearnLib is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 3.0 as published by the Free Software Foundation.
 * 
 * LearnLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with LearnLib; if not, see
 * <http://www.gnu.de/documents/lgpl.en.html>.
 */
package de.learnlib.dhc.mealy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.automata.transout.impl.FastMealy;
import net.automatalib.automata.transout.impl.FastMealyState;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.SimpleAlphabet;
import de.learnlib.api.CEXHandlerSuffixes;
import de.learnlib.api.LearningAlgorithm;
import de.learnlib.api.MembershipOracle;
import de.learnlib.api.SupportsCEXHandlerSuffixes;
import de.learnlib.cexhandlers.CEXHandlerAllSuffixes;
import de.learnlib.oracles.DefaultQuery;

/**
 *
 * @author Maik Merten <maikmerten@googlemail.com>
 */
public class MealyDHC<I, O> implements LearningAlgorithm<MealyMachine<?, I, ?, O>, I, Word<O>>,
        SupportsCEXHandlerSuffixes<I, Word<O>> {

    private Alphabet<I> alphabet;
    private MembershipOracle<I, Word<O>> oracle;
    private SimpleAlphabet<Word<I>> splitters = new SimpleAlphabet<>();
    private FastMealy<I, O> hypothesis;
    private CEXHandlerSuffixes<I, Word<O>> cexhandler = new CEXHandlerAllSuffixes<>();
	
	private class QueueElement {
		private FastMealyState<O> parentState;
		private QueueElement parentElement;
		private I transIn;
		private O transOut;
		
		private QueueElement(FastMealyState<O> parentState, QueueElement parentElement, I transIn, O transOut) {
			this.parentState = parentState;
			this.parentElement = parentElement;
			this.transIn = transIn;
			this.transOut = transOut;
		}
	}
	

    public MealyDHC(Alphabet<I> alphabet, MembershipOracle<I, Word<O>> oracle) {
        this.alphabet = alphabet;
        this.oracle = oracle;
    }

    @Override
    public void startLearning() {

        // the effective alphabet is the concatenation of the real alphabet
        // wrapped in Words and the list of splitters
        SimpleAlphabet<Word<I>> effectivealpha = new SimpleAlphabet<>();
        for (I input : alphabet) {
            effectivealpha.add(Word.fromLetter(input));
        }
        effectivealpha.addAll(splitters);

        // initialize structure to store state output signatures
        Map<List<Word<O>>, FastMealyState<O>> signatures = new HashMap<>();

        // set up new hypothesis machine
        hypothesis = new FastMealy<>(alphabet);

        // initialize exploration queue
        Queue<QueueElement> queue = new LinkedList<>();
		
		// first element to be explored represents the initial state with no predecessor
        queue.add(new QueueElement(null, null, null, null));

        while (!queue.isEmpty()) {
            // get element to be explored from queue
            QueueElement elem = queue.poll();

            // determine access sequence for state
            Word<I> access = assembleAccessSequence(elem);

            // assemble queries
            ArrayList<DefaultQuery<I, Word<O>>> queries = new ArrayList<>(effectivealpha.size());
            for (Word<I> suffix : effectivealpha) {
                queries.add(new DefaultQuery<I, Word<O>>(access, suffix));
            }

            // retrieve answers
            oracle.processQueries(queries);

            // assemble output signature
            List<Word<O>> sig = new ArrayList<>(effectivealpha.size());
            for (DefaultQuery<I, Word<O>> query : queries) {
                sig.add(query.getOutput());
            }
			
			FastMealyState<O> sibling = signatures.get(sig);

            if (sibling != null) {
                // this element does not possess a new output signature
				// create a transition from parent state to sibling
				hypothesis.addTransition(elem.parentState, elem.transIn, sibling, elem.transOut);
            } else {
                // this is actually an observably distinct state! Progress!
                // Create state and connect via transition to parent
				FastMealyState<O> state = elem.parentElement == null ? hypothesis.addInitialState() : hypothesis.addState();
				if(elem.parentElement != null) {
					hypothesis.addTransition(elem.parentState, elem.transIn, state, elem.transOut);
				}
				signatures.put(sig, state);
				
                scheduleSuccessors(elem, state, queue, sig);
            }
        }
    }

	private Word<I> assembleAccessSequence(QueueElement elem) {
        List<I> sequence = new ArrayList<>();
        
        QueueElement pre = elem.parentElement;
        I sym = elem.transIn;
        while(pre != null && sym != null) {
            sequence.add(sym);
			sym = pre.transIn;
            pre = pre.parentElement;
        }

		Collections.reverse(sequence);
        return Word.fromList(sequence);
    }

    private void scheduleSuccessors(QueueElement elem, FastMealyState<O> state, Queue<QueueElement> queue, List<Word<O>> sig) throws IllegalArgumentException {
        for (int i = 0; i < alphabet.size(); ++i) {
            // retrieve I/O for transition
            I input = alphabet.getSymbol(i);
            O output = sig.get(i).getSymbol(0);
			
            // create successor element and schedule for exploration
            queue.add(new QueueElement(state, elem, input, output));
        }
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ceQuery) {
        if (hypothesis == null) {
            throw new IllegalStateException("No hypothesis learned yet");
        }

        int oldsize = hypothesis.size();

        ArrayList<Word<I>> suffixes = new ArrayList<>();
        cexhandler.createSuffixes(ceQuery, suffixes);
        for (Word<I> suffix : suffixes) {
            if (suffix.size() > 1 && !splitters.contains(suffix)) {
                splitters.add(suffix);
            }
        }

        startLearning();

        return oldsize != hypothesis.size();
    }

    @Override
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        if (hypothesis == null) {
            throw new IllegalStateException("No hypothesis learned yet");
        }
        return (MealyMachine<?, I, ?, O>) hypothesis;
    }

    @Override
    public void setCEXHandlerSuffixes(CEXHandlerSuffixes<I, Word<O>> handler) {
        this.cexhandler = handler;
    }
}