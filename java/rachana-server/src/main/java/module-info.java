/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/** rachana-server: the standalone server (COMPILER_AND_RUNTIMES 10, MODES 4). A host of the public API; nothing depends on it. */
module com.ash.rachana.server {
    requires com.ash.rachana.live;
    requires com.ash.tools.csv;                 // Drishti Tools' CSV reader and writer (owner decision O6); an automatic module named by its jar
    requires jdk.httpserver;
    requires java.net.http;
    requires java.sql;
    uses java.sql.Driver;
    exports com.ash.rachana.server;
}
