/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { createServer } from "node:http";
import { existsSync, readFileSync, statSync } from "node:fs";
import { extname, join, resolve, sep, dirname } from "node:path";
import { distDir } from "./bundle.js";
import { UsageError } from "./errors.js";
import { findSutras } from "./lint.js";
import { esc, nameOf, painterFile, tableFiles, viewPage } from "./site.js";

const TYPES = { ".html": "text/html", ".js": "text/javascript", ".json": "application/json", ".csv": "text/csv", ".yaml": "application/yaml", ".jsonl": "application/x-ndjson" };

function within(root, rel) {
  const p = resolve(root, "." + sep + rel);
  return p === resolve(root) || p.startsWith(resolve(root) + sep) ? p : null;
}

/** The handler of the preview server: pure (path to [status, type, body]) so it is testable without a socket. */
export function handler(sutras, dataDir) {
  return (url) => {
    const path = decodeURIComponent(new URL(url, "http://x").pathname);
    if (path === "/") return [200, TYPES[".html"], `<!doctype html><meta charset=utf-8><title>Sutras</title><h1>Sutras</h1><ul>${[...sutras.keys()].map((n) => `<li><a href="/page/${esc(n)}">${esc(n)}</a></li>`).join("")}</ul>`];
    let m;
    if ((m = path.match(/^\/page\/([^/]+)$/)) && sutras.has(m[1])) return [200, TYPES[".html"], viewPage(m[1], `/sutras/${m[1]}.sutra.yaml`, "/data", "/rachana/rachana-view.js")];
    if ((m = path.match(/^\/sutras\/([^/]+)\.sutra\.yaml$/)) && sutras.has(m[1])) return [200, TYPES[".yaml"], readFileSync(sutras.get(m[1]), "utf8")];
    if (path === "/data/index.json") return [200, TYPES[".json"], JSON.stringify({ protocol: "rdp", v: 1, mode: "static-files", tables: tableFiles(dataDir) })];
    if (path.startsWith("/data/") && dataDir) { const f = within(dataDir, path.slice(6)); if (f && existsSync(f) && statSync(f).isFile()) return [200, TYPES[extname(f)] ?? "application/octet-stream", readFileSync(f)]; }
    if (path === "/rachana/rachana-view.js" && painterFile()) return [200, TYPES[".js"], readFileSync(painterFile())];
    if (path === "/rachana/rachana.runtime.js") { const f = join(distDir(), "rachana.runtime.js"); if (existsSync(f)) return [200, TYPES[".js"], readFileSync(f)]; }
    return [404, "text/plain", "not found"];
  };
}

/** rachana preview <file|folder> [--data <dir>] [--port <n>]: serves one Sutra, or a folder's Sutras (dev). Prints the URL; runs until stopped. */
export async function preview(target, flags, io) {
  if (!existsSync(target)) throw new UsageError(`no such file or folder: ${target}`);
  const files = findSutras([target]);
  if (!files.length) throw new UsageError(`no *.sutra.yaml in ${target}`);
  const root = statSync(target).isDirectory() ? target : dirname(target);
  const dataDir = flags.data ?? (existsSync(join(root, "data")) ? join(root, "data") : existsSync(join(root, "..", "data")) ? join(root, "..", "data") : null);
  const handle = handler(new Map(files.map((f) => [nameOf(f), f])), dataDir);
  const server = createServer((req, res) => {
    try { const [s, t, b] = handle(req.url); res.writeHead(s, { "content-type": t, "cache-control": "no-store" }); res.end(b); }
    catch (e) { res.writeHead(500); res.end(String(e.message)); }
  });
  const port = Number(flags.port ?? 0);
  await new Promise((ok) => server.listen(port, "127.0.0.1", ok));
  const url = `http://127.0.0.1:${server.address().port}/${files.length === 1 ? "page/" + nameOf(files[0]) : ""}`;
  io.out(`serving ${url}  (Ctrl+C to stop)`);
  if (!painterFile()) io.err("warning: rachana-view.js is not in dist (lane C, branch g2/data): the page does not paint yet");
  if (flags["test-once"]) { server.close(); return 0; }
  await new Promise((ok) => server.on("close", ok));
  return 0;
}
