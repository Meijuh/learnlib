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
package de.learnlib.oracle.membership;

import de.learnlib.api.SUL;
import de.learnlib.api.exception.SULException;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.oracle.OmegaMembershipOracle.MealyOmegaMembershipOracle;
import de.learnlib.api.query.OmegaQuery;
import lombok.AccessLevel;
import lombok.Getter;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * An omega membership oracle for a {@link SUL}.
 *
 * The behavior is similar to a {@link SULOracle}, except that this class answers {@link OmegaQuery}s.
 *
 * After some symbols (as specified in {@link OmegaQuery#getIndices()}) in an input word the state of the {@link SUL}
 * is retrieved, and used to answer the query.
 *
 * Like {@link SULOracle} this class is thread-safe.
 *
 * @author Jeroen Meijer
 *
 * @param <S> the state type
 * @param <I> the input type
 * @param <O> the output type
 */
public abstract class SULOmegaOracle<S, I, O> implements MealyOmegaMembershipOracle<S, I, O> {

    @Getter(AccessLevel.PROTECTED)
    private final SUL<I, O> sul;
    private final ThreadLocal<SUL<I, O>> localSul;

    protected SULOmegaOracle(SUL<I, O> sul) {
        this.sul = sul;
        if (sul.canFork()) {
            this.localSul = ThreadLocal.withInitial(sul::fork);
        } else {
            this.localSul = null;
        }
    }

    @Override
    public void processQueries(Collection<? extends OmegaQuery<S, I, Word<O>>> queries) {
        if (localSul != null) {
            processQueries(localSul.get(), queries);
        } else {
            synchronized (sul) {
                processQueries(sul, queries);
            }
        }
    }

    private void processQueries(SUL<I, O> sul, Collection<? extends OmegaQuery<S, I, Word<O>>> queries) {
        for (OmegaQuery<S, I, Word<O>> q : queries) {
            final Pair<Word<O>, List<S>> output = answerQuery(sul, q.getPrefix(), q.getSuffix(), q.getIndices());
            q.answer(output.getKey());
            q.setStates(output.getValue());
        }
    }

    protected abstract S getState(SUL<I, O> sul);

    @Nonnull
    private Pair<Word<O>, List<S>> answerQuery(SUL<I, O> sul, Word<I> prefix, Word<I> suffix, Set<Integer> indices)
            throws SULException {
        sul.pre();
        try {
            int index = 0;
            final List<S> states = new ArrayList();

            // Prefix: Execute symbols, don't record output
            for (I sym : prefix) sul.step(sym);

            if (indices.contains(index++)) states.add(getState(sul));

            // Suffix: Execute symbols, outputs constitute output word
            WordBuilder<O> wb = new WordBuilder<>(suffix.length());
            for (I sym : suffix) {
                wb.add(sul.step(sym));
                if (indices.contains(index++)) states.add(getState(sul));
            }

            return Pair.of(wb.toWord(), states);
        } finally {
            sul.post();
        }
    }

    @Override
    public MealyMembershipOracle<I, O> getMealyMembershipOracle() {
        return new SULOracle(sul);
    }

    /**
     * A {@link SULOmegaOracle} that uses {@link Object#hashCode()}, and {@link Object#equals(Object)} to test for
     * state equivalence. When the hash codes of two states are equal this class will use two access sequences to
     * move two {@link SUL}s to those states and perform an equality check.
     *
     * @author Jeroen Meijer
     *
     * @param <I> the input type
     * @param <O> the output type
     */
    private static class ShallowCopySULOmegaOracle<I, O> extends SULOmegaOracle<Integer, I, O> {

        /**
         * A forked {@link SUL} is necessary when we need to step to two particular states at the same time.
         */
        private final SUL<I, O> forkedSUL;

        /**
         * Constructs a new {@link ShallowCopySULOmegaOracle}, use {@link #newOracle(SUL)} to create an instance.
         * This method makes sure the invariants of the {@link SUL} are satisfied (i.e. the {@link SUL} must be
         * forkable, i.e. ({@code {@link SUL#canFork()} == true}.
         *
         * @param sul
         */
        private ShallowCopySULOmegaOracle(SUL<I, O> sul) {
            super(sul);
            assert sul.canFork();
            forkedSUL = sul.fork();
        }

        /**
         * Returns the state as a hash code.
         * @param sul the {@link SUL} to retrieve the current state from.
         *
         * @return the hash code of the state.
         */
        @Override
        protected Integer getState(SUL<I, O> sul) {
            return sul.getState().hashCode();
        }

        /**
         * Test for state equivalence, by means of {@link Object#hashCode()}, and {@link Object#equals(Object)}.
         *
         * @see de.learnlib.api.oracle.OmegaMembershipOracle#isSameState(Word, Object, Word, Object).
         *
         * @return whether the following conditions hold:
         *  1. the hash codes are the same, i.e. {@code s1.equals(s2)}, and
         *  2. the two access sequences lead to the same state.
         */
        @Override
        public boolean isSameState(Word<I> input1, Integer s1, Word<I> input2, Integer s2) {
            final boolean result;
            if(!s1.equals(s2)) result = false;
            else {
                // in this case the hash codes are equal, now we must check if we accidentally had a hash-collision.
                final SUL<I, O> sul1 = getSul();
                final SUL<I, O> sul2 = forkedSUL;
                sul1.pre();
                try {
                    // step through the first SUL
                    for (I sym : input1) sul1.step(sym);
                    sul2.pre();
                    try {
                        // step through the second SUL
                        for (I sym : input2) sul2.step(sym);

                        assert sul1.getState().hashCode() == sul2.getState().hashCode();
                        assert s1.equals(sul1.getState().hashCode());
                        assert s2.equals(sul2.getState().hashCode());

                        // check for state equivalence
                        result = sul1.getState().equals(sul2.getState());
                    } finally {
                        sul2.post();
                    }

                } finally {
                    sul1.post();
                }
            }

            return result;
        }
    }

    /**
     * A {@link SULOmegaOracle} for states that are deep copies. When a state is a deep copy, this means we can
     * simply invoke {@link Object#equals(Object)} on both.
     *
     * @author Jeroen Meijer
     *
     * @param <I> the input type
     * @param <O> the output type
     */
    private static class DeepCopySULOmegaOracle<I, O> extends SULOmegaOracle<Object, I, O> {

        /**
         * Constructs a {@link DeepCopySULOmegaOracle}, use {@link #newOracle(SUL, boolean)} to create an actual
         * instance. This method will make sure the invariants of the {@link SUL} are satisfied.
         *
         * @param sul the {@link SUL}.
         */
        private DeepCopySULOmegaOracle(SUL<I, O> sul) {
            super(sul);
        }

        /**
         * Returns the current state of the {@link SUL}.
         *
         * @param sul the {@link SUL} to retrieve the current state from.
         *
         * @return the current state.
         */
        @Override
        protected Object getState(SUL<I, O> sul) {
            return sul.getState();
        }

        /**
         * Test for state equivalence using {@link Object#equals(Object)}.
         *
         * @see de.learnlib.api.oracle.OmegaMembershipOracle#isSameState(Word, Object, Word, Object).
         */
        @Override
        public boolean isSameState(Word<I> input1, Object s1, Word<I> input2, Object s2) {
            return s1.equals(s2);
        }
    }

    /**
     * Creates a new {@link SULOmegaOracle}, while making sure the invariants of the {@link SUL} are satisfied.
     *
     * @param sul the {@link SUL} to wrap around.
     * @param deepCopies whether to test for state equivalence directly on the retrieved state.
     *
     * @param <I> the input type
     * @param <O> the output type
     *
     * @return the {@link SULOmegaOracle}.
     */
    public static <I, O> SULOmegaOracle newOracle(SUL<I, O> sul, boolean deepCopies) {
        if (!sul.canRetrieveState()) throw new IllegalArgumentException("SUL can not copy states");

        final SULOmegaOracle<?, I, O> sulOmegaOracle;
        if (deepCopies) {
            if (!sul.deepCopies()) throw new IllegalArgumentException("SUL can not make deep copies of states.");
            else sulOmegaOracle = new DeepCopySULOmegaOracle(sul);
        } else {
            if (!sul.canFork()) throw new IllegalArgumentException("SUL must be forkable.");
            else sulOmegaOracle = new ShallowCopySULOmegaOracle(sul);
        }

        return sulOmegaOracle;
    }

    /**
     * Creates a new {@link SULOmegaOracle} that assumes the {@link SUL} can not make deep copies.
     *
     * @see #newOracle(SUL, boolean).
     */
    public static <I, O> SULOmegaOracle<?, I, O> newOracle(SUL<I, O> sul) {
        return newOracle(sul, !sul.canFork());
    }
}
