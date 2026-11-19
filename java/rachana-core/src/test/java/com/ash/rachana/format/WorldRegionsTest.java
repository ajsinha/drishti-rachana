/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.format;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldRegionsTest {

    private final WorldRegions regions = WorldRegions.load();

    @Test
    void aCountryHasOneSubRegionAndOneContinent() {
        assertThat(regions.subRegionOf("fra")).contains("SR-WESTERN-EUROPE");
        assertThat(regions.continentOf("FRA")).contains("CT-EUR");
        assertThat(regions.regionOf("KEN", "continent")).contains("CT-AFR");
        assertThat(regions.regionOf("KEN", "subregion")).contains("SR-EASTERN-AFRICA");
        assertThat(regions.regionOf("KEN", "planet")).isEmpty();
        assertThat(regions.subRegionOf("ZZZ")).isEmpty();
    }

    @Test
    void namesAndMembersComeFromTheSameTable() {
        assertThat(regions.name("CT-AME")).contains("Americas");
        assertThat(regions.name("SR-CARIBBEAN")).contains("Caribbean");
        assertThat(regions.countriesOf("SR-WESTERN-EUROPE")).contains("FRA", "DEU", "NLD");
        assertThat(regions.countriesOf("CT-EUR")).contains("FRA", "POL", "GBR").doesNotContain("KEN");
    }

    @Test
    void everyCountryIsInExactlyOneSubRegion() {
        long total = regions.subRegionCodes().stream().mapToLong(c -> regions.countriesOf(c).size()).sum();
        long distinct = regions.subRegionCodes().stream().flatMap(c -> regions.countriesOf(c).stream()).distinct().count();
        assertThat(distinct).isEqualTo(total).isGreaterThan(240);
        assertThat(regions.continentCodes()).hasSize(6);
    }
}
