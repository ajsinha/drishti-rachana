/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * The browser and Node runtime: the compiler built from the Java reference (TeaVM, no hand-written logic: dist/rachana.runtime.js and
 * dist/rachana.tools.js, built by tools/js/build_js.py), the Data Protocol client (data_client.js: transport only), a Sutra bound to its
 * tables (screen.js: the bundle's screen functions) and <rachana-view> (rachana_view.js: import it in a page that loaded Drishti Elements).
 */
export { DataClient, DataError, BridgeChannel, pageRows, arrowPage, ndjsonPage, liveTables } from './data_client.js';
export { StaticHost, installStaticHost, parseCsv } from './static_files_host.js';
export { Screen, loadRuntime } from './screen.js';
export const STATUS = "bundles built; data client and <rachana-view> (B2, B3)";
