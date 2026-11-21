/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import com.ash.rachana.el.check.ElType;
import com.ash.rachana.el.check.Fields;
import java.util.List;
import java.util.Optional;

/**
 * The fields of one host table as checked mode sees them (SPEC 4.14): its columns with their types (PROTOCOL 3.1 mapped to checked mode's
 * types: {@code number} and {@code decimal} are numeric, {@code list} a list, {@code time} and {@code object} any) and their masks, named
 * as a row's fields ({@code @.revenue}) or bare ({@code revenue}). Nothing else is a field: no aliases, measures or parameters.
 */
final class TableFields implements Fields {

    private final TableSchema schema;

    TableFields(TableSchema schema) {
        this.schema = schema;
    }

    @Override
    public Optional<Field> field(List<String> parts) {
        List<String> p = !parts.isEmpty() && (parts.get(0).equals("@") || parts.get(0).equals("$")) ? parts.subList(1, parts.size()) : parts;
        if (p.isEmpty()) {
            return Optional.empty();
        }
        TableSchema.Column c = schema.column(p.get(0));
        if (c == null) {
            return Optional.empty();
        }
        ElType t = p.size() > 1 ? ElType.ANY : switch (c.type()) {
            case "number" -> ElType.NUMBER;
            case "list" -> ElType.LIST;
            case "time", "object" -> ElType.ANY;
            default -> ElType.ofColumn(c.type());
        };
        return Optional.of(new Field(String.join(".", parts), t, c.masked()));
    }

    @Override
    public boolean alias(String name) {
        return false;
    }

    @Override
    public Optional<String> measure(String nameOrId) {
        return Optional.empty();
    }

    @Override
    public List<String> names() {
        return schema.names();
    }
}
