/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The column pass of a typed fragment (decision L-21, rule 3): the text of a Core column parameter, {@code @.<column>} over the table, a
 * lambda element's field and the field of a row of the table renamed to the caller's; the rest refused ({@code RCH-2121}); and the
 * column parameters the pass knows equal the {@code role: column} marks of {@code spec/1/functions.yaml}. The behaviour in a Sutra is
 * pinned by the conformance suites {@code sutra/fragments-calls} and {@code problem/fragments-calls}.
 */
class FragmentColumnsTest {

    /** One table {@code rows} with columns desk and amount, mapped to BOOK_DESK and MTM_USD; and a value parameter {@code limit}. */
    private static final class Rows implements FragmentColumns.Tables {
        final Map<String, String> map = Map.of("desk", "BOOK_DESK", "amount", "MTM_USD");
        final Set<String> read = new TreeSet<>();

        @Override
        public String table(String afterArg) {
            return afterArg.equals("rows") ? "rows" : null;
        }

        @Override
        public Set<String> columns(String table) {
            return "rows".equals(table) ? Set.of("desk", "amount") : null;
        }

        @Override
        public String rename(String table, String column) {
            return map.getOrDefault(column, column);
        }

        @Override
        public void read(String table, String column) {
            read.add(column);
        }

        @Override
        public List<String> tableNames() {
            return List.of("rows");
        }
    }

    private static String renamed(String text, String row) {
        List<String> problems = new ArrayList<>();
        String out = FragmentColumns.rename(text, row, true, new Rows(), problems);
        assertThat(problems).as(text).isEmpty();
        return out;
    }

    private static List<String> refused(String text, String row) {
        List<String> problems = new ArrayList<>();
        FragmentColumns.rename(text, row, true, new Rows(), problems);
        return problems;
    }

    @Test
    void columnParametersAreRenamedKeepingTheSpelling() {
        assertThat(renamed("sum(arg.rows, 'amount')", null)).isEqualTo("sum(arg.rows, 'MTM_USD')");
        assertThat(renamed("sum(arg.rows, \"amount\") / 2", null)).isEqualTo("sum(arg.rows, \"MTM_USD\") / 2");
        assertThat(renamed("percentile(arg.rows, 'amount', 0.5)", null)).isEqualTo("percentile(arg.rows, 'MTM_USD', 0.5)");
        assertThat(renamed("wavg(arg.rows, 'amount', 'amount')", null)).isEqualTo("wavg(arg.rows, 'MTM_USD', 'MTM_USD')");
        assertThat(renamed("group(arg.rows, 'desk', 'amount')", null)).isEqualTo("group(arg.rows, 'BOOK_DESK', 'MTM_USD')");
        assertThat(renamed("count(arg.rows |> sort(@.amount) |> take(3), 'desk')", null))
                .isEqualTo("count(arg.rows |> sort(@.MTM_USD) |> take(3), 'BOOK_DESK')");
        assertThat(renamed("sum(arg.rows[?@.amount > arg.limit], 'amount')", null)).isEqualTo("sum(arg.rows[?@.MTM_USD > arg.limit], 'MTM_USD')");
        assertThat(renamed("sum(@, 'amount')", "rows")).isEqualTo("sum(@, 'MTM_USD')");
    }

    @Test
    void rowsAndElementsOfTheTableAreRenamed() {
        assertThat(renamed("map(arg.rows, @.amount * 2)", null)).isEqualTo("map(arg.rows, @.MTM_USD * 2)");
        assertThat(renamed("sort(arg.rows, lambda r: r.amount, true)", null)).isEqualTo("sort(arg.rows, lambda r: r.MTM_USD, true)");
        assertThat(renamed("reduce(arg.rows, 0, lambda acc, r: acc + r.amount)", null)).isEqualTo("reduce(arg.rows, 0, lambda acc, r: acc + r.MTM_USD)");
        assertThat(renamed("first(arg.rows).desk + arg.rows[-1].desk", null)).isEqualTo("first(arg.rows).BOOK_DESK + arg.rows[-1].BOOK_DESK");
        assertThat(renamed("@.amount > 0 && @.legs[0].amount > 0", "rows")).isEqualTo("@.MTM_USD > 0 && @.legs[0].amount > 0");
        assertThat(renamed("map(@.legs, @.amount)", "rows")).isEqualTo("map(@.legs, @.amount)");
        assertThat(renamed("@.amount", null)).isEqualTo("@.amount");
    }

    @Test
    void whatTheMappingCannotReachIsRefused() {
        assertThat(refused("sum(arg.rows, arg.measure)", null)).singleElement().asString().contains("computed text ('arg.measure')");
        assertThat(refused("sum(arg.rows, 'qty')", null)).singleElement().asString().contains("names column 'qty' of table rows");
        assertThat(refused("sum(map(arg.rows, @), 'amount')", null)).singleElement().asString().contains("over a list that is not the table");
        assertThat(refused("amount > 0", "rows")).singleElement().asString().contains("as a bare name").contains("write @.amount");
        assertThat(refused("{amount: 1}.amount + let(x = 1, x)", null)).isEmpty();
        assertThat(refused("sum(@.legs, 'notional')", "rows")).isEmpty();
    }

    @Test
    void textsThatAreNoExpressionAreLeftToTheCaller() {
        assertThat(FragmentColumns.rename("Desk's total", "rows", false, new Rows(), new ArrayList<>())).isNull();
        assertThat(FragmentColumns.template("Total ${sum(arg.rows, 'amount')} for ${@.desk}", "rows", new Rows(), new ArrayList<>()))
                .isEqualTo("Total ${sum(arg.rows, 'MTM_USD')} for ${@.BOOK_DESK}");
    }

    @Test
    void theColumnParametersAreTheCataloguesRoleMarks() throws Exception {
        String yaml = Files.readString(Path.of("../../spec/1/functions.yaml"));
        Map<String, List<String>> marked = new LinkedHashMap<>();
        String function = null;
        int place = 0;
        for (String line : yaml.split("\n")) {
            Matcher n = Pattern.compile("^- name: (\\S+)").matcher(line);
            Matcher p = Pattern.compile("^  - \\{name: \"([^\"]+)\", type: \"([^\"]+)\".*}$").matcher(line);
            if (n.find()) {
                function = n.group(1);
                place = 0;
            } else if (function != null && p.find()) {
                if (line.contains("role: \"column\"")) {
                    marked.computeIfAbsent(function, k -> new ArrayList<>()).add(place + ":" + p.group(1) + ":" + "text".equals(p.group(2)));
                }
                place++;
            } else if (!line.startsWith(" ") && !line.startsWith("-")) {
                function = null;
            }
        }
        Map<String, List<String>> known = new LinkedHashMap<>();
        for (FragmentColumns.ColumnParam c : FragmentColumns.COLUMN_PARAMS) {
            known.computeIfAbsent(c.function(), k -> new ArrayList<>()).add(c.place() + ":" + c.name() + ":" + c.textOnly());
        }
        assertThat(known).isEqualTo(marked);
        assertThat(yaml).contains("\nroles_since: \"1.2\"\n");
    }
}
