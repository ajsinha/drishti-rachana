/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.Sutra;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Shared panel blocks: written once beside the Sutras, used by many, parameters filled, versions chosen, mistakes named (RCH-2048). */
class SutraBlocksTest {

    @TempDir Path dir;

    static final String TICKET = """
            rachana: 1
            block: ticket
            version: 1
            description: A ticket.
            params: { rate-fmt: price4 }
            panels:
              - id: ticket
                kind: kv
                columns:
                  - { label: Price, bind: $.PRICE, fmt: "%{rate-fmt}" }
              - { id: built, kind: provenance, title: How this view was built }
            """;

    static String sutra(String panels) {
        return "rachana: 1\nsutra: deal\nversion: 1\nmatch: { kind: trade, where: \"$.SOURCE_SYSTEM == 'ENDUR' && $.PRODUCT_TYPE == 'Cash'\" }\n"
                + "title: { id: $.TRADE_ID }\npanels:\n" + panels;
    }

    @AfterEach
    void empty() {
        com.ash.rachana.TestGrammar.blocks().load(List.of());
    }

    @Test
    void aUseIsReplacedByTheBlocksPanelsWithItsParametersFilled() throws Exception {
        Files.createDirectories(dir.resolve("common"));
        Files.writeString(dir.resolve("common/ticket.v1.block.yaml"), TICKET);
        Files.writeString(dir.resolve("common/ticket.v2.block.yaml"), TICKET.replace("version: 1", "version: 2").replace("Price", "Price v2"));
        assertThat(com.ash.rachana.TestGrammar.blocks().load(List.of(dir))).isEmpty();
        assertThat(com.ash.rachana.TestGrammar.blocks().library().get("ticket")).containsOnlyKeys(1, 2);

        Sutra s = com.ash.rachana.TestGrammar.parser().parse(sutra("  - { use: ticket@1, with: { rate-fmt: pct4 } }\n  - { id: notes, kind: markdown, text: hi }\n"), "deal.v1.sutra.yaml", "endur");
        assertThat(s.panels()).extracting(Panel::id).containsExactly("ticket", "built", "notes");
        assertThat(s.panels().get(0).columns().get(0).fmt()).isEqualTo("pct4");
        Sutra latest = com.ash.rachana.TestGrammar.parser().parse(sutra("  - { use: ticket }\n"), "deal.v1.sutra.yaml", "endur");
        assertThat(latest.panels().get(0).columns().get(0).label()).isEqualTo("Price v2");                // no version: the newest
        assertThat(latest.panels().get(0).columns().get(0).fmt()).isEqualTo("price4");                   // the default
    }

    @Test
    void anUnknownBlockOrParameterIsDrs2048AndABlockMayNotUseAnother() throws Exception {
        Files.writeString(dir.resolve("ticket.v1.block.yaml"), TICKET);
        com.ash.rachana.TestGrammar.blocks().load(List.of(dir));
        assertThatThrownBy(() -> com.ash.rachana.TestGrammar.parser().parse(sutra("  - { use: nothing@1 }\n"), "deal.v1.sutra.yaml", "x"))
                .isInstanceOf(SutraException.class).satisfies(e -> assertThat(((SutraException) e).problems())
                        .extracting(SutraProblem::code).contains(SutraBlocks.BLOCK));
        assertThatThrownBy(() -> com.ash.rachana.TestGrammar.parser().parse(sutra("  - { use: ticket@1, with: { colour: red } }\n"), "deal.v1.sutra.yaml", "x"))
                .isInstanceOf(SutraException.class).hasMessageContaining("no parameter 'colour'");
        Files.writeString(dir.resolve("nested.v1.block.yaml"), "rachana: 1\nblock: nested\nversion: 1\npanels:\n  - { use: ticket@1 }\n");
        assertThat(com.ash.rachana.TestGrammar.blocks().load(List.of(dir))).extracting(SutraProblem::message).anyMatch(m -> m.contains("uses another block"));
        Files.writeString(dir.resolve("wrong.v1.block.yaml"), TICKET);
        assertThat(com.ash.rachana.TestGrammar.blocks().load(List.of(dir))).extracting(SutraProblem::message).anyMatch(m -> m.contains("name it ticket.v1.block.yaml"));
    }

    static final String SECTION = """
            rachana: 1
            fragment: trade-swapone
            version: 1
            description: SWAPONE's own fields on the trade page
            doc:
              summary: SWAPONE's own fields on the trade page
              description: The native fields of SWAPONE with no canonical trade field.
              params: {}
              emits: []
              expects: []
              phone: more
              examples:
              - use: trade-swapone@1
              since: 2.9.11
            panels:
            - id: swapone-fields
              kind: kv
              title: SWAPONE fields
              when: $.SOURCE_SYSTEM == 'SWAPONE'
              columns:
              - label: brokerRef
                bind: nonblank($.brokerRef, '-')
            """;

    @Test
    void aModulesSectionJoinsEverySutraOfItsKindWhileTheModuleRunsAndLeavesWithIt() {
        com.ash.rachana.TestGrammar.blocks().contribute("source-swapone", "trade", SECTION, "trade-swapone.v1.fragment.yaml");
        try {
            Sutra s = com.ash.rachana.TestGrammar.parser().parse(sutra("  - { id: notes, kind: markdown, text: hi }\n"), "deal.v1.sutra.yaml", "endur");
            assertThat(s.panels()).extracting(Panel::id).containsExactly("notes", "swapone-fields");
            Sutra other = com.ash.rachana.TestGrammar.parser().parse(sutra("  - { id: notes, kind: markdown, text: hi }\n").replace("kind: trade", "kind: position"), "deal.v1.sutra.yaml", "endur");
            assertThat(other.panels()).extracting(Panel::id).containsExactly("notes");                  // only the kind it names
        } finally {
            com.ash.rachana.TestGrammar.blocks().withdraw("source-swapone");
        }
        Sutra gone = com.ash.rachana.TestGrammar.parser().parse(sutra("  - { id: notes, kind: markdown, text: hi }\n"), "deal.v1.sutra.yaml", "endur");
        assertThat(gone.panels()).extracting(Panel::id).containsExactly("notes");
    }

    @Test
    void twoInstancesKeepTheirOwnLibrariesNothingIsShared() throws Exception {
        Files.writeString(dir.resolve("ticket.v1.block.yaml"), TICKET);
        BlockLibrary a = new BlockLibrary(), b = new BlockLibrary();
        assertThat(a.load(List.of(dir))).isEmpty();
        b.contribute("source-swapone", "trade", SECTION, "trade-swapone.v1.fragment.yaml");
        SutraParser pa = new SutraParser(com.ash.rachana.profile.Grammar.of(List.of(com.ash.rachana.profile.Entities.addTo(
                com.ash.rachana.profile.Profile.named("a"), List::of, a).build())));
        SutraParser pb = new SutraParser(com.ash.rachana.profile.Grammar.of(List.of(com.ash.rachana.profile.Entities.addTo(
                com.ash.rachana.profile.Profile.named("b"), List::of, b).build())));
        assertThat(pa.parse(sutra("  - { use: ticket@1 }\n"), "deal.v1.sutra.yaml", "x").panels()).extracting(Panel::id).containsExactly("ticket", "built");
        assertThatThrownBy(() -> pb.parse(sutra("  - { use: ticket@1 }\n"), "deal.v1.sutra.yaml", "x")).isInstanceOf(SutraException.class);
        assertThat(pb.parse(sutra("  - { id: notes, kind: markdown, text: hi }\n"), "deal.v1.sutra.yaml", "x").panels()).extracting(Panel::id)
                .containsExactly("notes", "swapone-fields");
        assertThat(pa.parse(sutra("  - { id: notes, kind: markdown, text: hi }\n"), "deal.v1.sutra.yaml", "x").panels()).extracting(Panel::id)
                .containsExactly("notes");
        assertThat(com.ash.rachana.TestGrammar.blocks().library()).isEmpty();           // and the tests' own host saw neither
        assertThatThrownBy(() -> new SutraParser().parse(sutra("  - { use: ticket@1 }\n").replaceAll("match:.*\n", ""), "deal.v1.sutra.yaml", "x"))
                .isInstanceOf(SutraException.class);                                        // Core alone has no blocks at all
    }
}
