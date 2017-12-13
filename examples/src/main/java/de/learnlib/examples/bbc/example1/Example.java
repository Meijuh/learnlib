/* Copyright (C) 2013-2017 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.examples.bbc.example1;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.ttt.dfa.TTTLearnerDFA;
import de.learnlib.api.algorithm.LearningAlgorithm.DFALearner;
import de.learnlib.api.logging.LoggingBlackBoxProperty.DFALoggingBlackBoxProperty;
import de.learnlib.api.modelchecking.modelchecker.ModelChecker.DFAModelCheckerLasso;
import de.learnlib.api.oracle.BlackBoxOracle.DFABlackBoxOracle;
import de.learnlib.api.oracle.BlackBoxOracle.DFABlackBoxProperty;
import de.learnlib.api.oracle.EmptinessOracle.DFALassoEmptinessOracle;
import de.learnlib.api.oracle.EquivalenceOracle.DFAEquivalenceOracle;
import de.learnlib.api.oracle.InclusionOracle.DFAInclusionOracle;
import de.learnlib.api.oracle.MembershipOracle.DFAMembershipOracle;
import de.learnlib.api.oracle.OmegaMembershipOracle.DFAOmegaMembershipOracle;
import de.learnlib.modelchecking.modelchecker.LTSminLTLDFABuilder;
import de.learnlib.oracle.blackbox.CExFirstBBOracle.CExFirstDFABBOracle;
import de.learnlib.oracle.blackbox.ModelCheckingBBProperty.DFABBPropertyDFALasso;
import de.learnlib.oracle.emptiness.LassoAutomatonEmptinessOracle.DFALassoDFAEmptinessOracle;
import de.learnlib.oracle.equivalence.WpMethodEQOracle.DFAWpMethodEQOracle;
import de.learnlib.oracle.inclusion.BreadthFirstInclusionOracle.DFABreadthFirstInclusionOracle;
import de.learnlib.oracle.membership.SimulatorOmegaOracle.DFASimulatorOmegaOracle;
import de.learnlib.util.BBCExperiment.DFABBCExperiment;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.util.automata.equivalence.DeterministicEquivalenceTest;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;

import java.util.function.Function;

/**
 * Runs a black-box checking experiment for a DFA.
 *
 * @author Jeroen Meijer
 */
public class Example {

    /**
     * A function that transforms edges in an FSM source to actual input for a DFA.
     */
    public static final Function<String, Character> edgeParser = s -> s.charAt(0);

    public static void main(String[] args) {

        // define the alphabet
        Alphabet<Character> sigma = Alphabets.characters('a', 'b');

        // create the DFA to be verified/learned
        DFA<Integer, Character> dfa = AutomatonBuilders.newDFA(sigma).
                withInitial("q0").withAccepting("q0").withAccepting("q1").
                from("q0").on('a').to("q1").
                from("q1").on('b').to("q0").

                from("q0").on('b').to("TRAP").
                from("q1").on('a').to("TRAP").
                from("TRAP").
                    on('a').loop().
                    on('b').loop().
                create();

        // create an omega membership oracle
        DFAOmegaMembershipOracle<Integer, Character> omqOracle = new DFASimulatorOmegaOracle(dfa);

        // create a regular membership oracle
        DFAMembershipOracle<Character> mqOracle = omqOracle.getDFAMembershipOracle();

        // create an equivalence oracle
        DFAEquivalenceOracle eqOracle = new DFAWpMethodEQOracle(3, mqOracle);

        // create a learner
        DFALearner<Character> learner = new TTTLearnerDFA(sigma, mqOracle, AcexAnalyzers.LINEAR_FWD);

        // create a model checker
        DFAModelCheckerLasso<Character, String> modelChecker =
                new LTSminLTLDFABuilder().withString2Input(edgeParser).create();

        // create an emptiness oracle, that is used to disprove properties
        DFALassoEmptinessOracle emptinessOracle = new DFALassoDFAEmptinessOracle(omqOracle);

        // create an inclusion oracle, that is used to find counterexamples to hypotheses
        DFAInclusionOracle<Character> inclusionOracle = new DFABreadthFirstInclusionOracle(1, mqOracle);

        // create an LTL formula
        DFABlackBoxProperty<String, Character> ltl = new DFALoggingBlackBoxProperty(new DFABBPropertyDFALasso(
                modelChecker,
                emptinessOracle,
                inclusionOracle,
                "letter==\"b\""));

        // create a black-box oracle
        DFABlackBoxOracle<Character> blackBoxOracle = new CExFirstDFABBOracle(ltl);

        // create a black-box checking experiment
        DFABBCExperiment<Character> experiment = new DFABBCExperiment(learner, eqOracle, sigma, blackBoxOracle);

        // run the experiment
        experiment.run();

        // get the result
        final DFA<?, Character> result = experiment.getFinalHypothesis();

        // assert we have the correct result
        assert DeterministicEquivalenceTest.findSeparatingWord(dfa, result, sigma) == null;
    }
}
