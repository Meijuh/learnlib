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

import de.learnlib.api.oracle.OmegaMembershipOracle;
import de.learnlib.api.oracle.OmegaQueryAnswerer;
import de.learnlib.api.query.OmegaQuery;
import de.learnlib.util.MQUtil;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;

/**
 * @author Jeroen Meijer
 *
 * @see QueryAnswererOracle
 *
 * @param <S> the state type
 * @param <I> the input type
 * @param <D> the output type
 */
@ParametersAreNonnullByDefault
public abstract class OmegaQueryAnswererOracle<S, I, D> implements OmegaMembershipOracle<S, I, D> {

    private final OmegaQueryAnswerer<S, I, D> answerer;

    public OmegaQueryAnswererOracle(OmegaQueryAnswerer<S, I, D> answerer) {
        this.answerer = answerer;
    }

    @Override
    public void processQueries(Collection<? extends OmegaQuery<S, I, D>> queries) {
        MQUtil.answerOmegaQueries(answerer, queries);
    }

}
