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
package de.learnlib.oracle.inclusion;

import de.learnlib.api.oracle.InclusionOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.DFAMembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.util.BreadthFirstOracle.DefaultBreadthFirstOracle;
import net.automatalib.automata.concepts.Output;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.ts.simple.SimpleDTS;
import net.automatalib.words.Word;

/**
 * An {@link InclusionOracle} that generates words in a breadth-first manner.
 *
 * @author Jeroen Meijer
 *
 * @see InclusionOracle
 * @see de.learnlib.util.BreadthFirstOracle
 */
public abstract class BreadthFirstInclusionOracle<A extends Output<I, D> & SimpleDTS<?, I>, I, D>
        extends DefaultBreadthFirstOracle<A, I, D>
        implements InclusionOracle<A, I, D, DefaultQuery<I, D>> {

    public BreadthFirstInclusionOracle(int maxWords, MembershipOracle<I, D> membershipOracle) {
        super(maxWords, membershipOracle);
    }

    public static class DFABreadthFirstInclusionOracle<I>
            extends BreadthFirstInclusionOracle<DFA<?, I>, I, Boolean>
            implements DFAInclusionOracle<I> {

        public DFABreadthFirstInclusionOracle(int maxWords, DFAMembershipOracle<I> membershipOracle) {
            super(maxWords, membershipOracle);
        }
    }

    public static class MealyBreadthFirstInclusionOracle<I, O>
            extends BreadthFirstInclusionOracle<MealyMachine<?, I, ?, O>, I, Word<O>>
            implements MealyInclusionOracle<I, O> {

        public MealyBreadthFirstInclusionOracle(int maxWords, MealyMembershipOracle<I, O> membershipOracle) {
            super(maxWords, membershipOracle);
        }
    }
}
