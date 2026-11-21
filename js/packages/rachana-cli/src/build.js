/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { lintFiles, findSutras } from "./lint.js";
import { distDir } from "./bundle.js";
import { UsageError } from "./errors.js";
import { esc, nameOf, painterFile, tableFiles, viewPage } from "./site.js";

/**
 * rachana build: a static site (static mode, docs/MODES.md 1). Lints first (exit 1 on errors, nothing written), then writes
 *   out/index.html, out/<sutra>.html, out/sutras/<sutra>.sutra.yaml, out/data/<table files> + out/data/index.json,
 *   out/rachana/rachana.runtime.js, out/rachana/rachana-view.js (when the painter exists), out/site.json.
 * TODO(lane C merge): the painter (<rachana-view>, js/packages/rachana/dist/rachana-view.js) lives on g2/data. Until it is in
 * dist, the pages reference it and the build warns; nothing here paints.
 */
export async function build(src, out, io) {
  const sutraDir = existsSync(join(src, "sutras")) ? join(src, "sutras") : src;
  const dataDir = existsSync(join(src, "data")) ? join(src, "data") : null;
  const files = findSutras([sutraDir]);
  if (!files.length) throw new UsageError(`no *.sutra.yaml under ${sutraDir}`);
  const results = await lintFiles(files);
  const errors = results.flatMap((r) => r.problems.map((p) => ({ file: r.file, ...p }))).filter((p) => p.severity === "error");
  if (errors.length) {
    for (const p of errors) io.err(`${p.file}:${p.range?.start?.line ?? 1}: ${p.code}: ${p.message.split("\n")[0]}`);
    io.err(`build stopped: ${errors.length} error(s)`);
    return 1;
  }
  mkdirSync(join(out, "sutras"), { recursive: true });
  mkdirSync(join(out, "rachana"), { recursive: true });
  const painter = painterFile();
  const script = painter ? "rachana/rachana-view.js" : "rachana/rachana-view.js";   // same URL; the file is missing without the painter
  if (painter) cpSync(painter, join(out, script)); else io.err("warning: rachana-view.js is not in dist (lane C, branch g2/data): pages are written but nothing paints yet");
  const runtime = join(distDir(), "rachana.runtime.js");
  if (existsSync(runtime)) cpSync(runtime, join(out, "rachana", "rachana.runtime.js"));
  const tables = tableFiles(dataDir);
  if (dataDir) {
    mkdirSync(join(out, "data"), { recursive: true });
    for (const t of tables) cpSync(join(dataDir, t.file), join(out, "data", t.file));
    writeFileSync(join(out, "data", "index.json"), JSON.stringify({ protocol: "rdp", v: 1, mode: "static-files", tables }, null, 2));
  }
  const names = files.map(nameOf);
  files.forEach((f, i) => {
    writeFileSync(join(out, "sutras", names[i] + ".sutra.yaml"), readFileSync(f));
    writeFileSync(join(out, names[i] + ".html"), viewPage(names[i], `sutras/${names[i]}.sutra.yaml`, "data", script));
  });
  writeFileSync(join(out, "index.html"), `<!doctype html><meta charset=utf-8><title>Sutras</title><h1>Sutras</h1><ul>${names.map((n) => `<li><a href="${esc(n)}.html">${esc(n)}</a></li>`).join("")}</ul>\n`);
  writeFileSync(join(out, "site.json"), JSON.stringify({ sutras: names, tables, painter: Boolean(painter) }, null, 2));
  io.out(`built ${out}: ${names.length} Sutra(s), ${tables.length} table file(s)${painter ? "" : " (painter missing)"}`);
  return 0;
}
