/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.drools.core.common;

import java.util.Map;

import org.drools.core.impl.RuleBase;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.util.Iterator;
import org.kie.api.KieBase;

public class TerminalNodeIterator implements Iterator {
    private RuleBase kBase;
    private TerminalNode[][] nodes;

    private int i = 0;
    private int j = 0;
    
    TerminalNodeIterator() {
        
    }

    private TerminalNodeIterator(KieBase kBase) {
        this.kBase = (RuleBase)kBase;
        Map<String, TerminalNode[]> rules = this.kBase.getReteooBuilder().getTerminalNodes();
        nodes = rules.values().toArray( new TerminalNode[rules.size()][] );
    }
    
    public static Iterator iterator(KieBase kBase) {
        return new TerminalNodeIterator(kBase);
    }

    public Object next() {
        if ( i >= nodes.length ) {
            return null;
        }
        TerminalNode node = nodes[i][j];
        
        // now set to the next node
        j++;                
        if ( j >= nodes[i].length ) {
            i++;
            j = 0;
        }
        return node;
    }

}
