/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/** rachana-live: live tables (docs/design/LIVE_TABLES.md), the Data Protocol provider that keeps tables current. JVM only. */
module com.ash.rachana.live {
    requires transitive com.ash.rachana.data;
    exports com.ash.rachana.live;
    exports com.ash.rachana.live.spi;
}
