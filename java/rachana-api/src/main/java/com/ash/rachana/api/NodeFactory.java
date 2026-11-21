/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.api;

import java.util.List;

/**
 * How a host's own tree is built: the one hook through which the lists and records an expression makes come out as the host's node type
 * instead of Rachana's own. A host whose node type is not Rachana's returns its factory from {@link Node#nodes()} on every node it
 * owns; an expression evaluated over a document of that type then hands back every list and record it builds (a filter, a function's
 * result, a list or record literal) through this factory. Nothing else changes: Rachana reads any node through the {@link Node}
 * interface, and a host node that is already the host's is never copied.
 *
 * <pre>{@code
 * public sealed interface DataNode extends Node ... {
 *     NodeFactory NODES = new NodeFactory() {
 *         public Node of(Object value) { return DataNode.of(value); }            // converts a foreign node, keeps its own
 *         public Node list(List<? extends Node> elements) { return DataNode.of(elements); }
 *     };
 *     default NodeFactory nodes() { return NODES; }
 * }
 * }</pre>
 */
public interface NodeFactory {

    /**
     * This host's tree of a value: a plain Java value (maps, lists, texts, numbers, booleans, null), or any node. A node of this
     * host's type is returned as it is; any other node (Rachana's own) is converted with what it holds.
     */
    Node of(Object value);

    /** A list of these elements in this host's type; an element that is not of this host's type is converted as {@link #of} does. */
    Node list(List<? extends Node> elements);

    /** Rachana's own nodes ({@link Node#of(Object)}, {@link Node#list(List)}): what a node that names no factory uses. */
    static NodeFactory core() {
        return CoreNodes.INSTANCE;
    }
}

/** Rachana's own factory. */
final class CoreNodes implements NodeFactory {

    static final CoreNodes INSTANCE = new CoreNodes();

    private CoreNodes() {}

    @Override
    public Node of(Object value) {
        return Node.of(value);
    }

    @Override
    public Node list(List<? extends Node> elements) {
        return Node.list(elements);
    }
}
