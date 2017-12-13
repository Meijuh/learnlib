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
package de.learnlib.examples.bbc.example2;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.ttt.mealy.TTTLearnerMealy;
import de.learnlib.api.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.api.modelchecking.modelchecker.ModelChecker.MealyModelCheckerLasso;
import de.learnlib.api.oracle.BlackBoxOracle.MealyBlackBoxOracle;
import de.learnlib.api.oracle.BlackBoxOracle.MealyBlackBoxProperty;
import de.learnlib.api.oracle.EmptinessOracle.MealyLassoEmptinessOracle;
import de.learnlib.api.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.api.oracle.InclusionOracle.MealyInclusionOracle;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.oracle.OmegaMembershipOracle.MealyOmegaMembershipOracle;
import de.learnlib.modelchecking.modelchecker.LTSminLTLIOBuilder;
import de.learnlib.oracle.blackbox.CExFirstBBOracle.CExFirstMealyBBOracle;
import de.learnlib.oracle.blackbox.ModelCheckingBBProperty.MealyBBPropertyMealyLasso;
import de.learnlib.oracle.emptiness.LassoAutomatonEmptinessOracle.MealyLassoMealyEmptinessOracle;
import de.learnlib.oracle.equivalence.WpMethodEQOracle.MealyWpMethodEQOracle;
import de.learnlib.oracle.inclusion.BreadthFirstInclusionOracle.MealyBreadthFirstInclusionOracle;
import de.learnlib.oracle.membership.SimulatorOmegaOracle.MealySimulatorOmegaOracle;
import de.learnlib.util.BBCExperiment.MealyBBCExperiment;
import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.util.automata.equivalence.DeterministicEquivalenceTest;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;

import java.util.function.Function;

/**
 * Run a black-box checking experiment with Mealy machines and straightforward edge semantics.
 *
 * The main difference with {@link de.learnlib.examples.bbc.example3.Example} is how the LTL formula is written.
 *
 * @see de.learnlib.examples.bbc.example3.Example
 *
 * @author Jeroen Meijer
 */
public class Example {

    /**
     * A function that transforms edges in an FSM source to actual input, and output in the Mealy machine.
     */
    public static final Function<String, Character> edgeParser = s -> s.charAt(0);

    public static void main(String[] args) {

        // define the alphabet
        Alphabet<Character> sigma = Alphabets.characters('a', 'a');

        // define the Mealy machine to be verified/learned
        MealyMachine<?, Character, ?, ?> mealy = AutomatonBuilders.newMealy(sigma).
                withInitial("q0").
                from("q0").
                on('a').withOutput('1').to("q1").
                from("q1").
                on('a').withOutput('2').to("q0").
                create();

        // create an omega membership oracle
        MealyOmegaMembershipOracle<Integer, Character, ?> omqOracle = new MealySimulatorOmegaOracle(mealy);

        // create a regular membership oracle
        MealyMembershipOracle<Character, ?> mqOracle = omqOracle.getMealyMembershipOracle();

        // create an equivalence oracle
        MealyEquivalenceOracle eqOracle = new MealyWpMethodEQOracle(3, mqOracle);

        // create a learner
        MealyLearner<Character, ?> learner = new TTTLearnerMealy(sigma, mqOracle, AcexAnalyzers.LINEAR_FWD);

        // create a model checker
        MealyModelCheckerLasso<Character, ?, String> modelChecker =
                new LTSminLTLIOBuilder().withString2Input(edgeParser).withString2Output(edgeParser).create();

        // create an emptiness oracle, that is used to disprove properties
        MealyLassoEmptinessOracle<?, Character, ?> emptinessOracle = new MealyLassoMealyEmptinessOracle(omqOracle);

        // create an inclusion oracle, that is used to find counterexamples to hypotheses
        MealyInclusionOracle<Character, ?> inclusionOracle = new MealyBreadthFirstInclusionOracle(1, mqOracle);

        // create an ltl formula
        MealyBlackBoxProperty<String, Character, Character> ltl = new MealyBBPropertyMealyLasso(
                modelChecker,
                emptinessOracle,
                inclusionOracle,
                "X output==\"2\"");

        // create a black-box oracle
        MealyBlackBoxOracle<Character, Character> blackBoxOracle = new CExFirstMealyBBOracle(ltl);

        // create an experiment
        MealyBBCExperiment<Character, ?> experiment = new MealyBBCExperiment(learner, eqOracle, sigma, blackBoxOracle);

        // run the experiment
        experiment.run();

        // get the final result
        MealyMachine<?, Character, ?, ?> result = experiment.getFinalHypothesis();

        // check we have the correct result
        assert DeterministicEquivalenceTest.findSeparatingWord(mealy, result, sigma) == null;
    }
}
