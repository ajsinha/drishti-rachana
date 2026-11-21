/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** The view package's generated files are what {@link ViewCodeGenerator} writes today (regenerate with its {@code main}). */
class ViewCodeGeneratedTest {

    @Test
    void theRecordWritersAreFresh() throws Exception {
        assertThat(Files.readString(ViewCodeGenerator.SOURCE.resolve("ViewRecords.java"), StandardCharsets.UTF_8))
                .as("ViewRecords.java is stale: run ViewCodeGenerator.main").isEqualTo(ViewCodeGenerator.records());
    }

    @Test
    void theSchemaTextIsFresh() throws Exception {
        assertThat(Files.readString(ViewCodeGenerator.SOURCE.resolve("ViewModelSchemaText.java"), StandardCharsets.UTF_8))
                .as("ViewModelSchemaText.java is stale: run ViewCodeGenerator.main").isEqualTo(ViewCodeGenerator.schemaSource());
        assertThat(ViewModelSchema.build()).isEqualTo(ViewCodeGenerator.schema());
    }
}
