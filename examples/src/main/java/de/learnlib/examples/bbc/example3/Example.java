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
package de.learnlib.examples.bbc.example3;

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
import de.learnlib.modelchecking.modelchecker.LTSminLTLAlternatingBuilder;
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
 * Run a black-box checking experiment with a Mealy machine and alternating edge semantics.
 *
 * The main difference with {@link de.learnlib.examples.bbc.example2.Example} is how the LTL formula is written.
 *
 * @see de.learnlib.examples.bbc.example2.Example
 *
 * @author Jeroen Meijer
 */
public class Example {

    public static final Function<String, Character> edgeParser = s -> s.charAt(0);

    public static void main(String[] args) {

        Alphabet<Character> sigma = Alphabets.characters('a', 'a');

        MealyMachine<?, Character, ?, ?> mealy = AutomatonBuilders.newMealy(sigma).
                withInitial("q0").
                from("q0").
                on('a').withOutput('1').to("q1").
                from("q1").
                on('a').withOutput('2').to("q0").
                create();

        MealyOmegaMembershipOracle<Integer, Character, ?> omqOracle = new MealySimulatorOmegaOracle(mealy);
        MealyMembershipOracle<Character, ?> mqOracle = omqOracle.getMealyMembershipOracle();

        MealyEquivalenceOracle eqOracle = new MealyWpMethodEQOracle(3, mqOracle);

        MealyLearner<Character, ?> learner = new TTTLearnerMealy(sigma, mqOracle, AcexAnalyzers.LINEAR_FWD);

        MealyModelCheckerLasso<Character, ?, String> modelChecker =
                new LTSminLTLAlternatingBuilder().withString2Input(edgeParser).withString2Output(edgeParser).create();

        MealyLassoEmptinessOracle<?, Character, ?> emptinessOracle = new MealyLassoMealyEmptinessOracle(omqOracle);

        MealyInclusionOracle<Character, ?> inclusionOracle = new MealyBreadthFirstInclusionOracle(1, mqOracle);

        MealyBlackBoxProperty<String, Character, Character> ltl = new MealyBBPropertyMealyLasso(
                modelChecker,
                emptinessOracle,
                inclusionOracle,
                "X X X letter==\"2\"");

        MealyBlackBoxOracle<Character, Character> blackBoxOracle = new CExFirstMealyBBOracle(ltl);

        MealyBBCExperiment<Character, ?> experiment = new MealyBBCExperiment(learner, eqOracle, sigma, blackBoxOracle);

        experiment.run();

        MealyMachine<?, Character, ?, ?> result = experiment.getFinalHypothesis();

        assert DeterministicEquivalenceTest.findSeparatingWord(mealy, result, sigma) == null;
    }
}
