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
package de.learnlib.api.oracle;

import net.automatalib.words.Word;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Answers {@link de.learnlib.api.query.OmegaQuery}s.
 *
 * @author Jeroen Meijer
 *
 * @see {@link OmegaMembershipOracle}.
 * @see {@link QueryAnswerer}.
 */
public interface OmegaQueryAnswerer<S, I, D> {

    @Nullable
    default Pair<D, List<S>> answerQuery(Word<I> input, Set<Integer> indices) {
        return answerQuery(Word.epsilon(), input, indices);
    }

    @Nullable
    Pair<D, List<S>> answerQuery(Word<I> prefix, Word<I> suffix, Set<Integer> indices);

    @Nonnull
    OmegaMembershipOracle<S, I, D> asOracle();
    // TODO    {
    //        return new QueryAnswererOracle<>(this);
    //    }
}
