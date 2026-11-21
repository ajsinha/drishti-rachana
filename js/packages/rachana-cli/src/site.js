/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, basename } from "node:path";
import { distDir } from "./bundle.js";

/** Escapes text for an HTML attribute or element. */
export const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

/** The painter: <rachana-view> built on branch g2/data (lane C). Looked up in dist; absent until that branch is merged. */
export function painterFile() {
  const f = join(distDir(), "rachana-view.js");
  return existsSync(f) ? f : null;
}

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

/** The page of one Sutra: <rachana-view> over the Sutra file and the data folder. */
export function viewPage(name, src, data, script) {
  return `<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>${esc(name)}</title>
<script type=module src="${esc(script)}"></script>
<rachana-view src="${esc(src)}" data="${esc(data)}"></rachana-view>
`;
}

export const nameOf = (file) => basename(file).replace(/\.sutra\.yaml$/, "");
