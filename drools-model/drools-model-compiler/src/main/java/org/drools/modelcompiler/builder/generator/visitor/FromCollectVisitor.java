/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.modelcompiler.builder.generator.visitor;

import org.drools.drl.ast.descr.AccumulateDescr;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.CollectDescr;
import org.drools.drl.ast.descr.PatternDescr;

public class FromCollectVisitor {

    private final ModelGeneratorVisitor parentVisitor;

    public FromCollectVisitor(ModelGeneratorVisitor parentVisitor) {
        this.parentVisitor = parentVisitor;
    }

    public void trasformFromCollectToCollectList(PatternDescr pattern, CollectDescr collectDescr) {
        // The inner pattern of the "from collect" needs to be processed to have the binding
        final PatternDescr collectDescrInputPattern = collectDescr.getInputPattern();
        if (!parentVisitor.initPattern( collectDescrInputPattern )) {
            return;
        }

        final AccumulateDescr accumulateDescr = new AccumulateDescr();
        accumulateDescr.setInputPattern(collectDescrInputPattern);
        accumulateDescr.addFunction("collectList", null, false, new String[]{collectDescrInputPattern.getIdentifier()});

        final PatternDescr transformedPatternDescr = new PatternDescr(pattern.getObjectType(), pattern.getIdentifier());
        for (BaseDescr o : pattern.getConstraint().getDescrs()) {
            transformedPatternDescr.addConstraint(o);
        }
        transformedPatternDescr.setSource(accumulateDescr);
        transformedPatternDescr.accept(parentVisitor);
    }
}
