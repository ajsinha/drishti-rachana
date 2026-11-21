/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import test, { after } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, readFileSync, existsSync, mkdirSync, rmSync, symlinkSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";
import { deflateRawSync } from "node:zlib";
import { fragmentsOf, tablesOf, formSpec, withOf, proposeMapping, insertUse, editorProblems } from "../src/rachana_studio_model.js";
import { createStudio, listen, within, tableInfo } from "../server/studio_server.js";
import { readZip, readLibrary, findLibraries, projectLibraries, bundleFiles } from "../server/library_files.js";
import { distDir } from "../../rachana-cli/src/bundle.js";

const yaml = createRequire(import.meta.url)("../../../vendor/js-yaml/4.1.0/js-yaml.min.js");
const hasBundle = existsSync(join(distDir(), "rachana.tools.js"));
const made = [];
const tmp = () => { const d = mkdtempSync(join(tmpdir(), "rachana-studio-")); made.push(d); return d; };
after(() => { for (const d of made) rmSync(d, { recursive: true, force: true }); });   // a test run leaves no temporary folder (G2 gate F16)

const FRAG = `rachana: 1
fragment: ranked-bars
version: 1
doc: { summary: Bars., category: charts }
params:
  rows: { type: table, required: true, doc: Rows, columns: { label: { type: text }, value: { type: number }, note: { type: text, optional: true } } }
  title: { type: text, default: Ranking, doc: Title }
  fmt: { type: format, default: amount0, doc: Format }
  heat: { type: boolean, default: false, doc: Heat }
  tone: { type: enum, options: [accent, ok], default: accent, doc: Tone }
  by: { type: id, doc: A column }
  columns: { type: columns, doc: Columns }
panels: []
`;
const LIB = { name: "core", version: "1.0.0", files: { "core.rachana-lib.yaml": "library: core\nversion: 1.0.0\n", "fragments/ranked-bars.v1.fragment.yaml": FRAG,
  "fragments/ranked-bars.v2.fragment.yaml": FRAG.replace("Bars.", "Bars two."), "fragments/old.v1.fragment.yaml": "rachana: 1\nfragment: old\nversion: 1\nparams:\n  x: text\n" } };
const PROJECT = [{ name: "sales", columns: [{ name: "region", type: "text" }, { name: "revenue", type: "number" }, { name: "units", type: "number" }] }];
const SUTRA = "rachana: 1.2\nsutra: demo\nversion: 1\ntitle: { id: \"'Demo'\" }\ndata:\n  orders: { rows: [ { A: x, B: 1 } ] }\n  other: { table: sales }\npanels:\n  - { id: a, kind: metric, label: A, value: 1 }\n\n# notes\n";

test("fragmentsOf: the highest major, typed form only, with the reason for a skip", () => {
  const { fragments, skipped } = fragmentsOf(LIB, yaml);
  assert.equal(fragments.length, 1);
  assert.equal(fragments[0].major, 2);
  assert.equal(fragments[0].summary, "Bars two.");
  assert.match(skipped[0], /old\.v1.*not in the typed form/);
});

test("tablesOf: written data entries (columns from rows or the data file) and the project's other files", () => {
  const t = tablesOf(SUTRA, PROJECT, yaml);
  assert.deepEqual(t.map((x) => [x.name, x.declared]), [["orders", true], ["other", true]]);
  assert.deepEqual(t[0].columns.map((c) => [c.name, c.type]), [["A", "text"], ["B", "number"]]);
  assert.equal(t[1].columns.length, 3);
  const t2 = tablesOf("rachana: 1\n", PROJECT, yaml);
  assert.deepEqual(t2.map((x) => [x.name, x.declared]), [["sales", false]]);
  assert.deepEqual(tablesOf("::: not yaml [", PROJECT, yaml).map((x) => x.name), ["sales"]);
});

test("proposeMapping: the same name first, then the first unused column of the type; optional ones only by name", () => {
  const want = { label: { type: "text" }, value: { type: "number" }, note: { type: "text", optional: true } };
  assert.deepEqual(proposeMapping(want, PROJECT[0].columns), { label: "region", value: "revenue" });
  assert.deepEqual(proposeMapping({ value: { type: "number" }, other: { type: "number" } }, PROJECT[0].columns), { value: "revenue", other: "units" });
});

test("formSpec: a table parameter is a table choice plus a column mapping; names of columns offer the table's columns", () => {
  const frag = fragmentsOf(LIB, yaml).fragments[0], tables = tablesOf("rachana: 1\n", PROJECT, yaml);
  const { schema, values } = formSpec(frag, tables);
  assert.deepEqual(schema.required, ["rows"]);
  assert.deepEqual(schema.properties.rows.properties.from.enum, ["sales"]);
  const cm = schema.properties.rows.properties.columns;
  assert.equal(cm.type, "columnmap");
  assert.deepEqual(cm.options.targets.map((t) => [t.name, t.type, t.required]), [["label", "string", true], ["value", "number", true], ["note", "string", false]]);
  assert.deepEqual(cm.options.columns.map((c) => c.name), ["region", "revenue", "units"]);
  assert.deepEqual(values.rows, { from: "sales", columns: { label: "region", value: "revenue" } });
  assert.deepEqual(schema.properties.by.enum, ["region", "revenue", "units"]);
  assert.equal(schema.properties.tone.widget, "select");
  assert.equal(values.title, "Ranking");
  assert.equal(values.columns.length, 3);
  assert.deepEqual(values.columns[1], { label: "Revenue", bind: "revenue", fmt: "amount0", total: true });
});

test("withOf: defaults left out, a mapping only where a name differs, columns as @.name, numbers as numbers", () => {
  const frag = fragmentsOf(LIB, yaml).fragments[0];
  const w = withOf(frag, { rows: { from: "sales", columns: { label: "region", value: "revenue", note: "" } }, title: "Ranking", fmt: "amount0", heat: false, tone: "ok", by: "", columns: [{ label: "R", bind: "region" }] });
  assert.deepEqual(w, { rows: { from: "$.sales", columns: { label: "region", value: "revenue" } }, tone: "ok", columns: [{ label: "R", bind: "@.region" }] });
  assert.deepEqual(withOf(frag, { rows: { from: "sales", columns: { label: "label", value: "value" } } }), { rows: "$.sales" });
  assert.equal(withOf({ params: { n: { type: "number" } } }, { n: "12" }).n, 12);
  assert.equal(withOf({ params: { n: { type: "number" } } }, { n: "sum($.a, 'b')" }).n, "sum($.a, 'b')");
});

test("insertUse: appended to panels, comments kept, the data file declared, the library added", () => {
  const frag = fragmentsOf(LIB, yaml).fragments[0], values = { rows: { from: "sales", columns: { label: "region", value: "revenue" } }, title: "Rev $ by region" };
  const tables = tablesOf(SUTRA.replace("  other: { table: sales }\n", ""), PROJECT, yaml);
  const r = insertUse(SUTRA.replace("  other: { table: sales }\n", ""), frag, values, tables, yaml);
  assert.ok(!r.error, r.error);
  const lines = r.text.split("\n");
  assert.equal(lines[3], "libraries: [core@2]");                       // after version:
  assert.ok(r.text.includes("  orders: { rows: [ { A: x, B: 1 } ] }\n  sales: { table: sales }\n"));   // declared in the data block
  assert.ok(r.text.includes("  - { id: a, kind: metric, label: A, value: 1 }\n  - use: core/ranked-bars@2\n    with:\n      rows: { from: $.sales, columns: { label: region, value: revenue } }\n      title: Rev $ by region\n"));
  assert.ok(r.text.endsWith("\n\n# notes\n"), "the trailing blank line and comment stay after the block");
  assert.ok(yaml.load(r.text).panels.length === 2, "the result is YAML with the new panel");
});

test("insertUse: libraries list and block forms, a missing panels:, inline forms refused", () => {
  const frag = fragmentsOf(LIB, yaml).fragments[0], values = { rows: { from: "other", columns: { label: "region", value: "revenue" } } }, tables = tablesOf(SUTRA, PROJECT, yaml);
  const a = insertUse(SUTRA.replace("version: 1\n", "version: 1\nlibraries: [other@1]\n"), frag, values, tables, yaml).text;
  assert.ok(a.includes("libraries: [other@1, core@2]"));
  const b = insertUse(SUTRA.replace("version: 1\n", "version: 1\nlibraries:\n  - other@1\n"), frag, values, tables, yaml).text;
  assert.ok(b.includes("libraries:\n  - other@1\n  - core@2\n"));
  const c = insertUse(SUTRA.replace("version: 1\n", "version: 1\nlibraries: [core@2]\n"), frag, values, tables, yaml).text;
  assert.equal(c.match(/core@2/g).length, 1, "the library is not listed twice");
  const d = insertUse("rachana: 1\nsutra: demo\nversion: 1\n", frag, values, [{ name: "other", columns: [], declared: true }], yaml).text;
  assert.ok(d.endsWith("panels:\n  - use: core/ranked-bars@2\n    with:\n      rows: { from: $.other, columns: { label: region, value: revenue } }\n"));
  assert.match(insertUse("panels: [ ]\n", frag, values, tables, yaml).error, /inline/);
});

test("editorProblems: lines, columns, severities and codes", () => {
  const p = editorProblems({ problems: [{ code: "RCH-1", severity: "error", message: "bad\nmore", range: { start: { line: 4, column: 3 } } }, { code: "RCH-2", severity: "warning", message: "w" }] });
  assert.deepEqual(p, [{ line: 4, from: 2, severity: "error", message: "RCH-1: bad" }, { line: 1, severity: "warning", message: "RCH-2: w" }]);
});

function zip(entries) {                                       // a small ZIP writer for the reader's test: deflated and stored entries
  const parts = [], central = [];
  let off = 0;
  for (const [name, data, method] of entries) {
    const n = Buffer.from(name), body = method === 0 ? Buffer.from(data) : deflateRawSync(Buffer.from(data));
    const lh = Buffer.alloc(30); lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(method, 8); lh.writeUInt32LE(body.length, 18); lh.writeUInt32LE(Buffer.byteLength(data), 22); lh.writeUInt16LE(n.length, 26);
    const ch = Buffer.alloc(46); ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(method, 10); ch.writeUInt32LE(body.length, 20); ch.writeUInt32LE(Buffer.byteLength(data), 24); ch.writeUInt16LE(n.length, 28); ch.writeUInt32LE(off, 42);
    parts.push(lh, n, body); central.push(ch, n); off += 30 + n.length + body.length;
  }
  const cd = Buffer.concat(central), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 10); end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(off, 16);
  return Buffer.concat([...parts, cd, end]);
}

test("libraries: a folder, an .rlib and a folder of both are found; a bad one is a problem, not a crash", () => {
  const d = tmp();
  mkdirSync(join(d, "libs", "alpha", "fragments"), { recursive: true });
  writeFileSync(join(d, "libs", "alpha", "alpha.rachana-lib.yaml"), "rachana: 1\nlibrary: alpha\nversion: 1.2.0\n");
  writeFileSync(join(d, "libs", "alpha", "fragments", "x.v1.fragment.yaml"), "x");
  writeFileSync(join(d, "libs", "beta-2.0.0.rlib"), zip([["beta.rachana-lib.yaml", "library: beta\nversion: 2.0.0\n", 8], ["fragments/y.v1.fragment.yaml", "y", 0], ["fragments/", "", 0]]));
  writeFileSync(join(d, "libs", "junk.rlib"), "not a zip at all, no end record here");
  const problems = [], found = findLibraries(join(d, "libs"), problems);
  assert.deepEqual(found.map((l) => `${l.name} ${l.version}`), ["alpha 1.2.0", "beta 2.0.0"]);
  assert.equal(problems.length, 1);
  assert.deepEqual(Object.keys(bundleFiles([found[1]])).sort(), ["rachana_libs/beta/beta.rachana-lib.yaml", "rachana_libs/beta/fragments/y.v1.fragment.yaml"]);
  assert.equal(readLibrary(join(d, "libs", "alpha")).files["fragments/x.v1.fragment.yaml"], "x");
  assert.throws(() => readZip(zip([["../evil", "x", 0]])), /unsafe/);
  const p = projectLibraries(d, [join(d, "libs")], {});
  assert.deepEqual(p.libraries.map((l) => l.name).sort(), ["alpha", "beta", "core", "finance"], "the bundled libraries (core, finance) are sources among the others");
  assert.equal(p.problems.length, 1, "only the junk archive");
  // another folder through RACHANA_LIBS, nothing rebuilt
  mkdirSync(join(d, "more", "gamma"), { recursive: true });
  writeFileSync(join(d, "more", "gamma", "gamma.rachana-lib.yaml"), "rachana: 1\nlibrary: gamma\nversion: 1.0.0\n");
  assert.ok(projectLibraries(d, [join(d, "libs")], { RACHANA_LIBS: join(d, "more") }).libraries.some((l) => l.name === "gamma"));
  // a same-name library with other contents is refused, never shadowing the bundled one
  mkdirSync(join(d, "shadow", "core"), { recursive: true });
  writeFileSync(join(d, "shadow", "core", "core.rachana-lib.yaml"), "rachana: 1\nlibrary: core\nversion: 9.9.9\n");
  const c = projectLibraries(d, [join(d, "shadow")], {});
  assert.ok(!c.libraries.some((l) => l.name === "core"));
  assert.match(c.problems.join("\n"), /library core is in .* and in .* different contents: refused/);
  rmSync(d, { recursive: true });
});

test("within: stays inside the folder, symbolic links resolved", () => {
  const d = tmp(), out = tmp();
  mkdirSync(join(d, "sub"));
  symlinkSync(out, join(d, "link"));
  assert.ok(within(d, "sub/a.sutra.yaml"));
  assert.equal(within(d, "../x.sutra.yaml"), null);
  assert.ok(within(d, "/etc/x.sutra.yaml").startsWith(d), "an absolute path is read as relative to the folder");
  assert.equal(within(d, "link/a.sutra.yaml"), null);
  assert.equal(within(d, "a\0b"), null);
  rmSync(d, { recursive: true }); rmSync(out, { recursive: true });
});

test("tableInfo: columns and their types from csv, jsonl and json", () => {
  const d = tmp();
  writeFileSync(join(d, "a.csv"), "k,n\nx,1\ny,2\n");
  writeFileSync(join(d, "b.jsonl"), '{"k":"x","n":1}\n{"k":"y","n":null}\n');
  writeFileSync(join(d, "c.json"), '[{"k":"x"}]');
  const t = tableInfo(d);
  assert.deepEqual(t.map((x) => [x.name, x.rows, x.columns.map((c) => c.type).join()]), [["a", 2, "text,number"], ["b", 2, "text,number"], ["c", 1, "text"]]);
  rmSync(d, { recursive: true });
});

test("the server: the project, the file, the save; Host, header, path and size rules", async () => {
  const d = tmp(), out = tmp();
  mkdirSync(join(d, "data")); mkdirSync(join(d, "rachana_libs", "demolib", "fragments"), { recursive: true });
  writeFileSync(join(d, "data", "sales.csv"), "region,revenue\nN,1\n");
  writeFileSync(join(d, "a.sutra.yaml"), SUTRA);
  writeFileSync(join(d, "rachana_libs", "demolib", "demolib.rachana-lib.yaml"), "library: demolib\nversion: 1.0.0\n");
  writeFileSync(join(d, "rachana_libs", "demolib", "fragments", "x.v1.fragment.yaml"), FRAG);
  writeFileSync(join(d, "rachana_libs", "demolib", "fragments", "e.sutra.yaml"), "an example inside a library is not a project Sutra");
  const studio = createStudio(d, { env: {} }), { server, url } = await listen(studio);
  const H = { "content-type": "application/json", "x-rachana-studio": "1" }, post = (p, b, h = H) => fetch(url + p, { method: "POST", headers: h, body: typeof b === "string" ? b : JSON.stringify(b) });
  try {
    const proj = await (await fetch(url + "api/project")).json();
    assert.deepEqual(proj.sutras, ["a.sutra.yaml"]);
    assert.equal(proj.level, undefined, "the level is the runtime's default, written in Core only");
    assert.equal(proj.tables[0].columns[1].type, "number");
    assert.equal(proj.libraries[0].name, "demolib");
    assert.equal((await fetch(url + "api/file?path=a.sutra.yaml")).status, 200);
    assert.equal((await fetch(url + "api/file?path=../" + out.split("/").pop() + "/x.sutra.yaml")).status, 404);
    assert.match(await (await fetch(url)).text(), /Rachana Studio/);
    assert.equal((await fetch(url + "studio/rachana_studio.js")).status, 200);
    assert.equal((await fetch(url + "vendor/js-yaml/4.1.0/js-yaml.min.js")).status, 200);
    assert.equal((await fetch(url + "data/sales.csv")).status, 200);
    // the writes
    assert.equal((await post("api/save", { path: "a.sutra.yaml", text: "new text\n" })).status, 200);
    assert.equal(readFileSync(join(d, "a.sutra.yaml"), "utf8"), "new text\n");
    assert.equal((await post("api/save", { path: "sub/b.sutra.yaml", text: "b" })).status, 200);
    assert.ok(existsSync(join(d, "sub", "b.sutra.yaml")));
    assert.equal((await post("api/save", { path: "../escape.sutra.yaml", text: "x" })).status, 403);
    assert.equal((await post("api/save", { path: "a.txt", text: "x" })).status, 400);
    assert.equal((await post("api/save", { path: ".hidden/a.sutra.yaml", text: "x" })).status, 400);
    assert.equal((await post("api/save", { path: "a.sutra.yaml" })).status, 400);
    assert.equal((await post("api/save", "{not json")).status, 400);
    assert.equal((await post("api/save", { path: "a.sutra.yaml", text: "x" }, { "content-type": "application/json" })).status, 403, "no Studio header");
    assert.equal((await post("api/save", { path: "a.sutra.yaml", text: "x".repeat(2.5 * 1024 * 1024) })).status, 413);
    assert.equal(readFileSync(join(d, "a.sutra.yaml"), "utf8"), "new text\n");
    assert.ok(!existsSync(join(d, "..", "escape.sutra.yaml")));
    assert.deepEqual(readdirNames(d).filter((n) => n.endsWith(".tmp")), [], "no temporary file is left");
    // Host check (DNS rebinding): only this machine and port
    const bad = await new Promise((ok) => { import("node:http").then(({ request }) => { const r = request(url + "api/project", { headers: { host: "evil.example" } }, (res) => ok(res.statusCode)); r.end(); }); });
    assert.equal(bad, 403);
    if (hasBundle) {
      const ok = await (await post("api/check", { sutra: "rachana: 1\nsutra: demo\nversion: 1\ntitle: { id: \"'T'\" }\npanels:\n  - { id: a, kind: metric, label: A, value: 1 }\n", file: "a.sutra.yaml" })).json();
      assert.equal(ok.ok, true);
      const no = await (await post("api/check", { sutra: "rachana: 1\nsutra: demo\nversion: 1\npanels:\n  - { id: a, kind: metric, label: A, value: \"1 +\" }\n" })).json();
      assert.equal(no.ok, false);
      assert.ok(no.problems[0].range.start.line >= 1);
    }
  } finally { server.close(); rmSync(d, { recursive: true }); rmSync(out, { recursive: true }); }
});

import { readdirSync } from "node:fs";
function readdirNames(d) { return readdirSync(d); }
