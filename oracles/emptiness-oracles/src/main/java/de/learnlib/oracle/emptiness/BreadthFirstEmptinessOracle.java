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
package de.learnlib.oracle.emptiness;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.DFAMembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.oracle.EmptinessOracle;
import de.learnlib.util.BreadthFirstOracle.DefaultBreadthFirstOracle;
import net.automatalib.automata.concepts.Output;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.ts.simple.SimpleDTS;
import net.automatalib.words.Word;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * An {@link EmptinessOracle} that generates words in a breadth-first manner.
 *
 * @author Jeroen Meijer
 *
 * @see EmptinessOracle
 * @see de.learnlib.util.BreadthFirstOracle
 */
@ParametersAreNonnullByDefault
public abstract class BreadthFirstEmptinessOracle<A extends Output<I, D> & SimpleDTS<?, I>, I, D>
        extends DefaultBreadthFirstOracle<A, I, D>
        implements EmptinessOracle<A, I, D, DefaultQuery<I, D>> {

    protected BreadthFirstEmptinessOracle(int maxWords, MembershipOracle<I, D> membershipOracle) {
        super(maxWords, membershipOracle);
    }

    public static class DFABFEmptinessOracle<I>
            extends BreadthFirstEmptinessOracle<DFA<?, I>, I, Boolean>
            implements DFAEmptinessOracle<I> {

        public DFABFEmptinessOracle(int maxWords, DFAMembershipOracle<I> membershipOracle) {
            super(maxWords, membershipOracle);
        }
    }

    public static class MealyBreadthFirstEmptinessOracle<I, O>
            extends BreadthFirstEmptinessOracle<MealyMachine<?, I, ?, O>, I, Word<O>>
            implements MealyEmptinessOracle<I, O> {

        public MealyBreadthFirstEmptinessOracle(int maxWords, MealyMembershipOracle<I, O> membershipOracle) {
            super(maxWords, membershipOracle);
        }
    }
}
