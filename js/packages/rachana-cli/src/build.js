/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { lintFiles, findSutras, libraryFiles, DEFAULT_LEVEL } from "./lint.js";
import { UsageError } from "./errors.js";
import { esc, nameOf, neededLibraryPaths, siteAssets, elementsStubs, STUB_CSS, tableFiles, viewPage } from "./site.js";

/**
 * rachana build: a static site (static mode, docs/MODES.md 1). Lints first (exit 1 on errors, nothing written), then writes
 *   out/index.html, out/<sutra>.html, out/sutras/<sutra>.sutra.yaml, out/data/<table files> + out/data/index.json,
 *   out/rachana/rachana.runtime.js, out/rachana/src/ (<rachana-view> and its modules), out/vendor/drishti-elements/3.2.0-dev+2b87fcd/, out/site.json; for a page that uses libraries (`use:`): out/rachana_libs/<library>/ (the files it needs, from the project, --libs, RACHANA_LIBS and the bundled libraries) and out/libraries/<page>.libraries.json.
 */
export async function build(src, out, io, flags = {}) {
  const level = flags.level ?? DEFAULT_LEVEL;
  const sutraDir = existsSync(join(src, "sutras")) ? join(src, "sutras") : src;
  const dataDir = existsSync(join(src, "data")) ? join(src, "data") : null;
  const files = findSutras([sutraDir]);
  if (!files.length) throw new UsageError(`no *.sutra.yaml under ${sutraDir}`);
  const assets = siteAssets();
  const lib = libraryFiles(src, flags, (m) => io.err(`rachana: ${m}`));
  if (lib.problems.length) return 1;
  const results = await lintFiles(files, "core", level, lib.files);
  const errors = results.flatMap((r) => r.problems.map((p) => ({ file: r.file, ...p }))).filter((p) => p.severity === "error");
  if (errors.length) {
    for (const p of errors) io.err(`${p.file}:${p.range?.start?.line ?? 1}: ${p.code}: ${p.message.split("\n")[0]}`);
    io.err(`build stopped: ${errors.length} error(s)`);
    return 1;
  }
  mkdirSync(join(out, "sutras"), { recursive: true });
  for (const [path, file] of assets) { mkdirSync(dirname(join(out, path)), { recursive: true }); cpSync(file, join(out, path)); }
  for (const path of elementsStubs()) { mkdirSync(dirname(join(out, path)), { recursive: true }); writeFileSync(join(out, path), STUB_CSS); }
  const tables = tableFiles(dataDir);
  if (dataDir) {
    mkdirSync(join(out, "data"), { recursive: true });
    for (const t of tables) cpSync(join(dataDir, t.file), join(out, "data", t.file));
    writeFileSync(join(out, "data", "index.json"), JSON.stringify({ protocol: "rdp", v: 1, mode: "static-files", tables }, null, 2));
  }
  const names = files.map(nameOf);
  files.forEach((f, i) => {
    writeFileSync(join(out, "sutras", names[i] + ".sutra.yaml"), readFileSync(f));
    const text = readFileSync(f, "utf8"), needed = neededLibraryPaths(text, lib.files);
    let manifest = "";
    if (needed.length) {                                    // the libraries the page uses are copied into the site, listed in its manifest
      for (const n of needed) { mkdirSync(dirname(join(out, n)), { recursive: true }); writeFileSync(join(out, n), lib.files[n]); }
      manifest = `libraries/${names[i]}.libraries.json`;
      mkdirSync(join(out, "libraries"), { recursive: true });
      writeFileSync(join(out, manifest), JSON.stringify({ files: needed }, null, 2));
    }
    writeFileSync(join(out, names[i] + ".html"), viewPage(names[i], `sutras/${names[i]}.sutra.yaml`, "data", "", level, manifest));
  });
  writeFileSync(join(out, "index.html"), `<!doctype html><meta charset=utf-8><title>Sutras</title><h1>Sutras</h1><ul>${names.map((n) => `<li><a href="${esc(n)}.html">${esc(n)}</a></li>`).join("")}</ul>\n`);
  writeFileSync(join(out, "site.json"), JSON.stringify({ sutras: names, tables, level }, null, 2));
  io.out(`built ${out}: ${names.length} Sutra(s), ${tables.length} table file(s)`);
  return 0;
}
