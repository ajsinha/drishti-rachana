/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/** rachana-data: the Data Protocol's in-memory host, its reference HTTP server and the JVM client. */
module com.ash.rachana.data {
    requires transitive com.ash.rachana.core;
    requires jdk.httpserver;
    requires java.net.http;
    requires org.apache.arrow.vector;
    requires org.apache.arrow.memory.core;
    requires org.apache.arrow.memory.unsafe;
    exports com.ash.rachana.data;
}
