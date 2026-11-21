/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, basename, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { distDir } from "./bundle.js";

/** Escapes text for an HTML attribute or element. */
export const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

const HERE = dirname(fileURLToPath(import.meta.url));
export const ELEMENTS_VERSION = "3.2.0-dev+a40ca7e";
const ELEMENTS_URL = `vendor/drishti-elements/${ELEMENTS_VERSION}`;

/** The real <rachana-view> and what it loads, as [site path, source file]: the element and its modules (js/packages/rachana/src),
 * the vendored Elements pack (js/vendor/drishti-elements/3.2.0-dev+a40ca7e) and the runtime bundle (dist, built by tools/js/build_js.py). */
export function siteAssets() {
  const src = join(HERE, "..", "..", "rachana", "src"), vendor = join(HERE, "..", "..", "..", "vendor", "drishti-elements", ELEMENTS_VERSION);
  const list = [];
  for (const n of readdirSync(src).filter((f) => f.endsWith(".js"))) list.push([`rachana/src/${n}`, join(src, n)]);
  const walk = (dir, rel) => { for (const e of readdirSync(dir, { withFileTypes: true })) e.isDirectory() ? walk(join(dir, e.name), `${rel}/${e.name}`) : list.push([`${rel}/${e.name}`, join(dir, e.name)]); };
  walk(vendor, ELEMENTS_URL);
  list.push(["rachana/rachana.runtime.js", join(distDir(), "rachana.runtime.js")]);
  for (const [, f] of list) if (!existsSync(f)) throw new Error(`${f} is missing` + (f.includes("dist") ? ": build it with python3 tools/js/build_js.py (or set RACHANA_DIST)" : ""));
  return list;
}

/** The per-library stylesheets the Elements loader asks for by address (css/drishti_*.css, elements/<library>/*.css) although the pack
 * carries them inside drishti-elements.css: their paths under the pack's folder, read from the pack's own script. The site holds
 * each as an empty file naming where its rules are, so a page makes no request that fails. */
export function elementsStubs() {
  const js = readFileSync(join(HERE, "..", "..", "..", "vendor", "drishti-elements", ELEMENTS_VERSION, "drishti-elements.js"), "utf8");
  return [...new Set(js.match(/"(?:css|elements)\/[\w./-]+\.css"/g) ?? [])].map((q) => `${ELEMENTS_URL}/${q.slice(1, -1)}`);
}
export const STUB_CSS = "/* the rules of this sheet are in the pack's drishti-elements.css */\n";

/** Row counts of the data files of a folder (csv, json, jsonl), for the manifest and the protocol index. */
export function tableFiles(dir) {
  if (!dir || !existsSync(dir)) return [];
  return readdirSync(dir).filter((n) => /\.(csv|json|jsonl|arrow)$/.test(n)).sort().map((n) => {
    const ext = n.split(".").pop(), text = ext === "arrow" ? "" : readFileSync(join(dir, n), "utf8");
    const rows = ext === "csv" ? Math.max(0, text.split(/\r?\n/).filter(Boolean).length - 1)
      : ext === "jsonl" ? text.split("\n").filter(Boolean).length : ext === "json" ? (JSON.parse(text).length ?? null) : null;
    return { name: n.slice(0, -(ext.length + 1)), file: n, rows };
  });
}

/** The page of one Sutra: Drishti Elements, then <rachana-view> over the Sutra file and the data folder. `base` is the path prefix
 * of the site root ("" for a page at the root, "/" for a server). */
export function viewPage(name, src, data, base = "", level = "", libraries = "") {
  const u = (p) => esc(base + p);
  return `<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>${esc(name)}</title>
<link rel=stylesheet href="${u(`${ELEMENTS_URL}/drishti-elements.css`)}">
<script src="${u(`${ELEMENTS_URL}/drishti-elements.js`)}"></script>
<script type=module>import "${esc(base || "./")}rachana/src/static_files_host.js"; import "${esc(base || "./")}rachana/src/rachana_view.js";</script>
<rachana-view src="${esc(src)}" data="${esc(data)}" runtime="${u("rachana/rachana.runtime.js")}"${level ? ` level="${esc(level)}"` : ""}${libraries ? ` libraries="${esc(libraries)}"` : ""}></rachana-view>
`;
}

export const nameOf = (file) => basename(file).replace(/\.sutra\.yaml$/, "");

/** The library files a Sutra's text needs, from the whole `files` map (`rachana_libs/<library>/<path>`): every file of each library the
 * text names (`libraries: [core@1]`, `- core@1`, or a `use: core/<fragment>`). A library is copied whole: its fragments name each other. */
export function neededLibraryPaths(text, files) {
  const names = new Set(Object.keys(files).map((p) => p.split("/")[1]).filter(Boolean));
  const used = new Set();
  for (const m of text.matchAll(/\buse:\s*["']?([\w.-]+)\//g)) used.add(m[1]);
  for (const m of text.matchAll(/(?:^|[\s\[,"'-])([\w.-]+)@\d+/gm)) used.add(m[1]);
  return Object.keys(files).filter((p) => p.startsWith("rachana_libs/") && used.has(p.split("/")[1]) && names.has(p.split("/")[1])).sort();
}
