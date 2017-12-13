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
package de.learnlib.util;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.eventbus.EventBus;
import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.util.statistics.SimpleProfiler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

import static de.learnlib.api.algorithm.LearningAlgorithm.DFALearner;
import static de.learnlib.api.algorithm.LearningAlgorithm.MealyLearner;

import static de.learnlib.api.oracle.EquivalenceOracle.DFAEquivalenceOracle;
import static de.learnlib.api.oracle.EquivalenceOracle.MealyEquivalenceOracle;

/**
 * runs a learning experiment.
 *
 * @param <A>
 * @param <I>
 * @param <D>
 *
 * @author falkhowar
 * @author Jeroen Meijer
 */
@ParametersAreNonnullByDefault
public class Experiment<A, I, D> {

    private static final LearnLogger LOGGER = LearnLogger.getLogger(Experiment.class);

    @Getter
    @Setter
    private boolean logModels;

    @Setter
    private boolean profile = false;

    @Getter
    private final Counter rounds = new Counter("learning rounds", "#");

    private A finalHypothesis;

    @Getter
    private final LearningAlgorithm<? extends A, I, D> learningAlgorithm;

    @Getter
    private final EquivalenceOracle<? super A, I, D> equivalenceAlgorithm;

    @Getter
    private final Alphabet<I> inputs;

    public Experiment(LearningAlgorithm<? extends A, I, D> learningAlgorithm,
                      EquivalenceOracle<? super A, I, D> equivalenceAlgorithm,
                      Alphabet<I> inputs) {
        this.learningAlgorithm = learningAlgorithm;
        this.equivalenceAlgorithm = equivalenceAlgorithm;
        this.inputs = inputs;
    }

    protected void init() {
        getRounds().increment();
        LOGGER.logPhase("Starting round " + getRounds().getCount());
        LOGGER.logPhase("Learning");
        profileStart("Learning");
        getLearningAlgorithm().startLearning();
        profileStop("Learning");
    }

    protected boolean equivalenceRound() {
        A hyp = getLearningAlgorithm().getHypothesisModel();

        if (isLogModels()) LOGGER.logModel(hyp);

        LOGGER.logPhase("Searching for counterexample");
        profileStart("Searching for counterexample");
        DefaultQuery<I, D> ce = getEquivalenceAlgorithm().findCounterExample(hyp, getInputs());
        profileStop("Searching for counterexample");
        if (ce != null) {
            LOGGER.logCounterexample(ce.toString());

            // next round ...
            getRounds().increment();
            LOGGER.logPhase("Starting round " + getRounds().getCount());
            LOGGER.logPhase("Learning");
            profileStart("Learning");
            getLearningAlgorithm().refineHypothesis(ce);
            profileStop("Learning");
        }
        return ce != null;
    }

    @Nonnull
    public A run() {

        init();

        while (equivalenceRound());

        return finalHypothesis = getLearningAlgorithm().getHypothesisModel();
    }

    protected void setFinalHypothesis(A hyp) {
        finalHypothesis = hyp;
    }

    @Nonnull
    public A getFinalHypothesis() {
        if (finalHypothesis == null) {
            throw new IllegalStateException("Experiment has not yet been run");
        }
        return finalHypothesis;
    }

    protected void profileStart(String taskname) {
        if (profile) {
            SimpleProfiler.start(taskname);
        }
    }

    protected void profileStop(String taskname) {
        if (profile) {
            SimpleProfiler.stop(taskname);
        }
    }

    public static class DFAExperiment<I> extends Experiment<DFA<?, I>, I, Boolean> {

        public DFAExperiment(DFALearner<I> learningAlgorithm,
                             DFAEquivalenceOracle<I> equivalenceAlgorithm,
                             Alphabet<I> inputs) {
            super(learningAlgorithm, equivalenceAlgorithm, inputs);
        }
    }

    public static class MealyExperiment<I, O> extends Experiment<MealyMachine<?, I, ?, O>, I, Word<O>> {

        public MealyExperiment(MealyLearner<I, O> learningAlgorithm,
                               MealyEquivalenceOracle<I, O> equivalenceAlgorithm,
                               Alphabet<I> inputs) {
            super(learningAlgorithm, equivalenceAlgorithm, inputs);
        }
    }
}

