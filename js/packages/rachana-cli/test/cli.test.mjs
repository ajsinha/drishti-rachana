/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import test, { after } from "node:test";
import assert from "node:assert/strict";
import { cpSync, mkdtempSync, writeFileSync, readFileSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { main } from "../src/index.js";
import { handler } from "../src/serve.js";
import { ELEMENTS_VERSION } from "../src/site.js";
import { libraryFiles } from "../src/lint.js";
import { libCommand } from "../src/lib.js";
import { serverCommand } from "../src/server.js";
import { distDir } from "../src/bundle.js";

const GOOD = "rachana: 1\nsutra: ok\nversion: 1\ntitle: { id: \"'Ok'\" }\npanels:\n  - { id: m, kind: metric, label: Revenue, value: 1 }\n";
const BAD = "rachana: 1\nsutra: bad\nversion: 1\npanels:\n  - { id: m, kind: metric, label: Revenue, value: \"1 +\" }\n";
const hasBundle = existsSync(join(distDir(), "rachana.tools.js"));
const made = [];
const tmp = () => { const d = mkdtempSync(join(tmpdir(), "rachana-cli-")); made.push(d); return d; };
after(() => { for (const d of made) rmSync(d, { recursive: true, force: true }); });   // a test run leaves no temporary folder (G2 gate F16)
const io = () => { const o = { out: [], err: [] }; return { o, io: { out: (s) => o.out.push(s), err: (s) => o.err.push(s) } }; };

test("usage: no command and unknown command exit 2", async () => {
  assert.equal(await main([], io().io), 2);
  assert.equal(await main(["nope"], io().io), 2);
  assert.equal(await main(["lint", "--format"], io().io), 2);
  assert.equal(await main(["lint", "--format", "xml", "."], io().io), 2);
});

test("new scaffolds a project and refuses a non-empty folder", async () => {
  const d = tmp(); const p = join(d, "p");
  assert.equal(await main(["new", p], io().io), 0);
  for (const f of ["rachana.yaml", "sutras/overview.sutra.yaml", "data/sales.csv"]) assert.ok(existsSync(join(p, f)), f);
  assert.equal(await main(["new", p], io().io), 2);
  assert.equal(await main(["new", join(d, "q"), "--template", "zzz"], io().io), 2);
  rmSync(d, { recursive: true });
});

test("lint: exit codes and formats", { skip: !hasBundle && "bundle not built" }, async () => {
  const d = tmp();
  writeFileSync(join(d, "a.sutra.yaml"), GOOD);
  assert.equal(await main(["lint", d], io().io), 0);
  writeFileSync(join(d, "b.sutra.yaml"), BAD);
  let { o, io: x } = io();
  assert.equal(await main(["lint", d], x), 1);
  assert.ok(o.out.some((l) => l.includes("b.sutra.yaml") && l.includes("error RCH-")));
  ({ o, io: x } = io());
  await main(["lint", "--format", "json", d], x);
  const res = JSON.parse(o.out[0]);
  assert.equal(res.find((r) => r.file.endsWith("b.sutra.yaml")).ok, false);
  ({ o, io: x } = io());
  await main(["lint", "--format", "sarif", join(d, "b.sutra.yaml")], x);
  assert.equal(JSON.parse(o.out[0]).version, "2.1.0");
  assert.equal(await main(["lint", join(d, "missing")], io().io), 2);
  rmSync(d, { recursive: true });
});

test("lint checks live tables' declarations with the tools bundle", { skip: !hasBundle && "bundle not built" }, async () => {
  const d = tmp();
  writeFileSync(join(d, "orders.live.yaml"), "rachana: 1\nlive-table: orders\nsnapshot: s\nkey: id\ncolumns: [ { name: id } ]\n");
  assert.equal(await main(["lint", d], io().io), 0);
  writeFileSync(join(d, "bad.live.yaml"), "rachana: 1\nlive-table: bad\nsnapshot: s\nkey: nope\ncolumns: [ { name: id } ]\nkind: trade\n");
  let { o, io: x } = io();
  assert.equal(await main(["lint", d], x), 1);
  assert.ok(o.out.some((l) => l.includes("bad.live.yaml") && l.includes("error RCH-")), o.out.join("\n"));
  ({ o, io: x } = io());
  await main(["lint", "--format", "json", "--profiles", "core,drishti", join(d, "bad.live.yaml")], x);
  const codes = JSON.parse(o.out[0])[0].problems.map((p) => p.code);
  assert.ok(!codes.includes("RCH-2060"), "the Drishti profile reads kind: " + codes);
  rmSync(d, { recursive: true });
});

test("build writes the site and stops on a lint error", { skip: !hasBundle && "bundle not built" }, async () => {
  const d = tmp(); const p = join(d, "p"), out = join(d, "site");
  await main(["new", p], io().io);
  assert.equal(await main(["build", "--src", p, "--out", out], io().io), 0);
  const site = JSON.parse(readFileSync(join(out, "site.json"), "utf8"));
  assert.deepEqual(site.sutras, ["overview"]);
  assert.equal(site.tables[0].rows, 3);
  assert.ok(readFileSync(join(out, "overview.html"), "utf8").includes("<rachana-view"));
  assert.equal(JSON.parse(readFileSync(join(out, "data", "index.json"), "utf8")).protocol, "rdp");
  writeFileSync(join(p, "sutras", "bad.sutra.yaml"), BAD);
  const out2 = join(d, "site2");
  assert.equal(await main(["build", "--src", p, "--out", out2], io().io), 1);
  assert.ok(!existsSync(out2), "nothing is written when lint fails");
  rmSync(d, { recursive: true });
});

test("preview handler serves pages, Sutra text and data; refuses path escape", { skip: !hasBundle && "bundle not built" }, () => {
  const d = tmp(); mkdirSync(join(d, "data"));
  writeFileSync(join(d, "a.sutra.yaml"), GOOD); writeFileSync(join(d, "data", "t.csv"), "a\n1\n"); writeFileSync(join(d, "secret.txt"), "x");
  const h = handler(new Map([["a", join(d, "a.sutra.yaml")]]), join(d, "data"));
  assert.equal(h("/")[0], 200);
  assert.ok(h("/page/a")[2].includes("/sutras/a.sutra.yaml"));
  assert.equal(h("/sutras/a.sutra.yaml")[2], GOOD);
  assert.equal(h("/data/t.csv")[2].toString(), "a\n1\n");
  assert.equal(JSON.parse(h("/data/index.json")[2]).tables[0].rows, 1);
  assert.equal(h("/data/..%2Fsecret.txt")[0], 404);
  assert.equal(h("/page/zzz")[0], 404);
  for (const u of ["/rachana/src/rachana_view.js", "/rachana/rachana.runtime.js", `/vendor/drishti-elements/${ELEMENTS_VERSION}/drishti-elements.js`, `/vendor/drishti-elements/${ELEMENTS_VERSION}/drishti-elements.css`]) assert.equal(h(u)[0], 200, u);
  rmSync(d, { recursive: true });
});

test("lib hands over to the one Java implementation", async () => {
  assert.equal(libCommand(["verify", "x"], {}), null);
  assert.deepEqual(libCommand(["verify", "x"], { RACHANA_JAR: "r.jar" }), ["java", ["-jar", "r.jar", "lib", "verify", "x"]]);
  assert.equal(libCommand(["pack", "d"], { RACHANA_CLASSPATH: "a:b", RACHANA_JAVA: "/j/java" })[1][2], "com.ash.rachana.cli.Main");
  const { o, io: x } = io();
  const saved = { ...process.env }; delete process.env.RACHANA_JAR; delete process.env.RACHANA_CLASSPATH;
  assert.equal(await main(["lib", "verify", "x"], x), 2);
  Object.assign(process.env, saved);
  assert.ok(o.err[0].includes("RACHANA_JAR"));
});

test("serve hands over to the server jar (no jar: exit 2 naming RACHANA_SERVER_JAR; no config: usage)", async () => {
  assert.equal(serverCommand({ config: "c.yaml" }, {}), null);
  assert.deepEqual(serverCommand({ config: "c.yaml", port: "0" }, { RACHANA_SERVER_JAR: "s.jar" }),
    ["java", ["-jar", "s.jar", "serve", "--config", "c.yaml", "--port", "0"]]);
  assert.deepEqual(serverCommand({ config: "c.yaml", jar: "x.jar", check: true }, { RACHANA_JAVA: "/j/java" }), ["/j/java", ["-jar", "x.jar", "check", "--config", "c.yaml"]]);
  const { o, io: x } = io();
  const saved = process.env.RACHANA_SERVER_JAR; delete process.env.RACHANA_SERVER_JAR;
  assert.equal(await main(["serve", "--config", "c.yaml"], x), 2);
  if (saved !== undefined) process.env.RACHANA_SERVER_JAR = saved;
  assert.ok(o.err[0].includes("RACHANA_SERVER_JAR"));
  assert.equal(await main(["serve"], io().io), 2);
});

const jv = resolve(import.meta.dirname, "../../../../java");
const cp = ["rachana-cli", "rachana-tools", "rachana-core", "rachana-api"].map((m) => join(jv, m, "target", "classes")).join(":");
test("lib verify runs the jar's LibraryTool (needs the Java classes built)", { skip: !existsSync(join(jv, "rachana-cli", "target", "classes")) && "java not built" }, async () => {
  process.env.RACHANA_CLASSPATH = cp;
  const d = tmp();
  try { assert.equal(await main(["lib", "verify", join(d, "missing")], io().io), 1); }
  finally { delete process.env.RACHANA_CLASSPATH; rmSync(d, { recursive: true }); }
});

const examples = resolve(import.meta.dirname, "../../../../examples");
test("lint takes --level; the examples lint clean at 1.1 and 1.2", { skip: !hasBundle && "bundle not built" }, async () => {
  for (const lv of ["1.1", "1.2"]) assert.equal(await main(["lint", "--level", lv, join(examples, "static-sales"), join(examples, "live-orders")], io().io), 0, lv);
  assert.equal(await main(["lint", "--level", "9.9", examples], io().io), 2);
});

test("build static-sales: the real <rachana-view>, Elements pack and runtime are in the site", { skip: !hasBundle && "bundle not built" }, async () => {
  const d = tmp(), out = join(d, "site"), { o, io: x } = io();
  assert.equal(await main(["build", "--src", join(examples, "static-sales"), "--out", out], x), 0);
  assert.ok(!o.err.length, o.err.join("\n"));
  for (const f of ["rachana/src/rachana_view.js", "rachana/src/screen.js", "rachana/src/data_client.js", "rachana/rachana.runtime.js",
    `vendor/drishti-elements/${ELEMENTS_VERSION}/drishti-elements.js`, `vendor/drishti-elements/${ELEMENTS_VERSION}/drishti-elements.css`, "overview.html", "data/index.json"]) assert.ok(existsSync(join(out, f)), f);
  const page = readFileSync(join(out, "overview.html"), "utf8");
  assert.ok(page.includes('./rachana/src/rachana_view.js') && page.includes("<rachana-view") && page.includes('runtime="rachana/rachana.runtime.js"'));
  rmSync(d, { recursive: true });
});

test("build: a page that uses a core fragment gets the library files and a manifest (so the browser can load them)", { skip: !hasBundle && "bundle not built" }, async () => {
  const d = tmp(), src = join(d, "proj"), out = join(d, "site");
  cpSync(join(examples, "static-sales"), src, { recursive: true });
  writeFileSync(join(src, "ranked.sutra.yaml"), "rachana: 1\nsutra: ranked\nversion: 1\nlibraries: [core@1]\ntitle: { id: \"'R'\" }\ndata:\n  sales: { table: sales, key: [region, product, month] }\npanels:\n  - use: core/ranked-bars@1\n    with:\n      rows: { from: $.sales, columns: { label: region, value: revenue } }\n");
  const { o, io: x } = io();
  assert.equal(await main(["build", "--src", src, "--out", out, "--level", "1.2"], x), 0, o.err.join("\n"));
  const m = JSON.parse(readFileSync(join(out, "libraries/ranked.libraries.json"), "utf8"));
  assert.ok(m.files.includes("rachana_libs/core/fragments/ranked-bars.v1.fragment.yaml"), m.files.slice(0, 3).join());
  for (const f of m.files) assert.ok(existsSync(join(out, f)), f);
  assert.ok(readFileSync(join(out, "ranked.html"), "utf8").includes('libraries="libraries/ranked.libraries.json"'));
  assert.ok(!existsSync(join(out, "libraries/overview.libraries.json")), "a page without use: gets no manifest");
  assert.ok(!readFileSync(join(out, "overview.html"), "utf8").includes("libraries="));
  rmSync(d, { recursive: true });
});

test("preview resolves libraries as build does: bundled, --libs and <project>/rachana_libs", { skip: !hasBundle && "bundle not built" }, () => {
  const d = tmp(), src = join(d, "proj");
  cpSync(join(examples, "static-sales"), src, { recursive: true });
  const sutra = "rachana: 1\nsutra: ranked\nversion: 1\nlibraries: [core@1]\ntitle: { id: \"'R'\" }\npanels:\n  - use: core/note@1\n    with: { text: hi }\n";
  writeFileSync(join(src, "ranked.sutra.yaml"), sutra);
  const lib = libraryFiles(src, {});
  assert.deepEqual(lib.problems, []);
  const h = handler(new Map([["ranked", join(src, "ranked.sutra.yaml")]]), null, "1.2", lib.files);
  assert.ok(h("/page/ranked")[2].includes('libraries="/libraries/ranked.libraries.json"'));
  const m = JSON.parse(h("/libraries/ranked.libraries.json")[2]);
  assert.ok(m.files.some((f) => f.startsWith("rachana_libs/core/fragments/")));
  for (const f of m.files) { assert.equal(h("/page/" + f)[0], 200, f); assert.equal(h("/" + f)[0], 200, f); }
  assert.equal(h("/rachana_libs/nope/x")[0], 404);
  // a project library beside the bundled one
  mkdirSync(join(src, "rachana_libs", "mine", "fragments"), { recursive: true });
  const ml = libraryFiles(src, {});
  assert.ok(Object.keys(ml.files).length >= Object.keys(lib.files).length);
  rmSync(d, { recursive: true });
});

test("studio: usage errors, and a start that prints its address and the libraries found", async () => {
  assert.equal(await main(["studio", join(tmpdir(), "no-such-folder-xyz")], io().io), 2);
  const d = tmp();
  mkdirSync(join(d, "rachana_libs", "demo"), { recursive: true });
  writeFileSync(join(d, "rachana_libs", "demo", "demo.rachana-lib.yaml"), "library: demo\nversion: 0.1.0\n");
  writeFileSync(join(d, "a.sutra.yaml"), GOOD);
  const { o, io: x } = io();
  assert.equal(await main(["studio", d, "--test-once"], x), 0);
  assert.match(o.out[0], /^studio http:\/\/127\.0\.0\.1:\d+\/ .*demo 0\.1\.0/);
  rmSync(d, { recursive: true });
});
