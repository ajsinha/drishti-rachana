/*
 *  Copyright 2012 Alexey Andreev. Licensed under the Apache License, Version 2.0 (below).
 *  Modified by Ashutosh Sinha for Drishti Rachana (2026, G1 R1): domainOptimizationEnabled() returns false (marked "Drishti
 *  Rachana patch"); otherwise TeaVM 0.16.0's file as published in its sources jar.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.dependency;

import java.util.Properties;
import java.util.function.Supplier;
import org.teavm.common.ServiceRepository;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.ResourceProvider;

public class PreciseDependencyAnalyzer extends DependencyAnalyzer {
    private DependencyNode allArrayItemsNode;
    private static final int DEGREE_THRESHOLD = 2;

    public PreciseDependencyAnalyzer(ClassReaderSource classSource, ResourceProvider resourceProvider,
            ClassLoader classLoader, ServiceRepository services, Diagnostics diagnostics,
            ReferenceCache referenceCache, String[] platformTags, Supplier<Properties> properties) {
        super(classSource, resourceProvider, classLoader, services, diagnostics, referenceCache, platformTags,
                properties);
    }

    @Override
    protected void processMethod(MethodDependency methodDep) {
        DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder(this);
        graphBuilder.buildGraph(methodDep);
    }

    @Override
    DependencyNode createParameterNode(MethodReference method, ValueType type, int index) {
        DependencyNode node = createNode(type);
        node.method = method;
        if (shouldTag) {
            node.setTag(method + ":" + index);
        }
        return node;
    }

    @Override
    DependencyNode createResultNode(MethodReference method) {
        DependencyNode node = createNode(method.getReturnType());
        node.method = method;
        if (shouldTag) {
            node.setTag(method + ":RESULT");
        }
        return node;
    }

    @Override
    DependencyNode createThrownNode(MethodReference method) {
        DependencyNode node = createNode();
        node.method = method;
        if (shouldTag) {
            node.setTag(method + ":THROWN");
        }
        return node;
    }

    @Override
    DependencyNode createFieldNode(FieldReference field, ValueType type) {
        DependencyNode node = createNode(type);
        if (shouldTag) {
            node.setTag(field.getClassName() + "#" + field.getFieldName());
        }
        return node;
    }

    @Override
    DependencyNode createArrayItemNode(DependencyNode parent) {
        ValueType itemTypeFilter = parent.typeFilter instanceof ValueType.Array
                ? ((ValueType.Array) parent.typeFilter).getItemType()
                : null;
        DependencyNode node;
        if (parent.degree > DEGREE_THRESHOLD) {
            if (allArrayItemsNode == null) {
                allArrayItemsNode = createNode(null);
                if (DependencyAnalyzer.shouldTag) {
                    allArrayItemsNode.tag = "<any array item>";
                }
            }
            node = allArrayItemsNode;
        } else {
            node = createNode(itemTypeFilter);
            node.degree = parent.degree + 1;
            node.method = parent.method;
            if (DependencyAnalyzer.shouldTag) {
                node.tag = parent.tag + "[";
            }
        }
        return node;
    }

    @Override
    DependencyNode createClassValueNode(int degree, DependencyNode parent) {
        DependencyNode node = createNode();
        node.degree = degree;
        node.classValueNode = node;
        node.classNodeParent = parent;
        if (DependencyAnalyzer.shouldTag) {
            node.tag = parent.tag + "@";
        }
        return node;
    }

    // Drishti Rachana patch (G1 R1): no domain merging. Transition.mergeDomains lets a node share its source's type set when the
    // node's types are a subset at that moment; the source's other types then count as present at the node without reaching its
    // listeners. Which nodes merge depends on the order of propagation, which varies from run to run (identity hashes), so the
    // analysis lost types in some runs: at 52e112f no Boolean reached Plain.asBoolean, booleanValue() stayed a virtual call with no
    // implementation emitted, and the runtime bundle failed on every named format. Without merging every type travels the edges.
    @Override
    boolean domainOptimizationEnabled() {
        return false;
    }

    @Override
    public boolean isPrecise() {
        return true;
    }
}