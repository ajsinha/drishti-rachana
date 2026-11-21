/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { createServer } from "node:http";
import { existsSync, readFileSync, statSync } from "node:fs";
import { extname, join, resolve, sep, dirname } from "node:path";
import { UsageError } from "./errors.js";
import { findSutras, libraryFiles } from "./lint.js";
import { esc, nameOf, neededLibraryPaths, siteAssets, elementsStubs, STUB_CSS, tableFiles, viewPage } from "./site.js";

const TYPES = { ".html": "text/html", ".css": "text/css", ".js": "text/javascript", ".json": "application/json", ".csv": "text/csv", ".yaml": "application/yaml", ".jsonl": "application/x-ndjson" };

function within(root, rel) {
  const p = resolve(root, "." + sep + rel);
  return p === resolve(root) || p.startsWith(resolve(root) + sep) ? p : null;
}

/** The handler of the preview server: pure (path to [status, type, body]) so it is testable without a socket. */
export function handler(sutras, dataDir, libFiles = {}) {
  const assets = new Map(siteAssets().map(([p, f]) => ["/" + p, f]));
  const stubs = new Set(elementsStubs().map((p) => "/" + p));
  return (url) => {
    const path = decodeURIComponent(new URL(url, "http://x").pathname);
    if (path === "/") return [200, TYPES[".html"], `<!doctype html><meta charset=utf-8><title>Sutras</title><h1>Sutras</h1><ul>${[...sutras.keys()].map((n) => `<li><a href="/page/${esc(n)}">${esc(n)}</a></li>`).join("")}</ul>`];
    let m;
    if ((m = path.match(/^\/page\/([^/]+)$/)) && sutras.has(m[1])) {
      const needed = neededLibraryPaths(readFileSync(sutras.get(m[1]), "utf8"), libFiles);   // the libraries the page uses, as `rachana build` lists them
      return [200, TYPES[".html"], viewPage(m[1], `/sutras/${m[1]}.sutra.yaml`, "/data", "/", needed.length ? `/libraries/${m[1]}.libraries.json` : "")];
    }
    if ((m = path.match(/^\/libraries\/([^/]+)\.libraries\.json$/)) && sutras.has(m[1])) {
      return [200, TYPES[".json"], JSON.stringify({ files: neededLibraryPaths(readFileSync(sutras.get(m[1]), "utf8"), libFiles) })];
    }
    const lp = path.replace(/^\/page\//, "/");      // the manifest names files relative to the page (/page/<name>), as a built site does
    if (lp.startsWith("/rachana_libs/") && Object.hasOwn(libFiles, lp.slice(1))) return [200, TYPES[extname(lp)] ?? "application/octet-stream", Buffer.from(libFiles[lp.slice(1)])];
    if ((m = path.match(/^\/sutras\/([^/]+)\.sutra\.yaml$/)) && sutras.has(m[1])) return [200, TYPES[".yaml"], readFileSync(sutras.get(m[1]), "utf8")];
    if (path === "/data/index.json") return [200, TYPES[".json"], JSON.stringify({ protocol: "rdp", v: 1, mode: "static-files", tables: tableFiles(dataDir) })];
    if (path.startsWith("/data/") && dataDir) { const f = within(dataDir, path.slice(6)); if (f && existsSync(f) && statSync(f).isFile()) return [200, TYPES[extname(f)] ?? "application/octet-stream", readFileSync(f)]; }
    if (stubs.has(path)) return [200, TYPES[".css"], STUB_CSS];
    if (assets.has(path)) return [200, TYPES[extname(path)] ?? "application/octet-stream", readFileSync(assets.get(path))];
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
  const lib = libraryFiles(root, flags, (m) => io.err(`rachana: ${m}`));
  if (lib.problems.length) return 1;
  const handle = handler(new Map(files.map((f) => [nameOf(f), f])), dataDir, lib.files);
  const server = createServer((req, res) => {
    try { const [s, t, b] = handle(req.url); res.writeHead(s, { "content-type": t, "cache-control": "no-store" }); res.end(b); }
    catch (e) { res.writeHead(500); res.end(String(e.message)); }
  });
  const port = Number(flags.port ?? 0);
  await new Promise((ok) => server.listen(port, "127.0.0.1", ok));
  const url = `http://127.0.0.1:${server.address().port}/${files.length === 1 ? "page/" + nameOf(files[0]) : ""}`;
  io.out(`serving ${url}  (Ctrl+C to stop)`);
  if (flags["test-once"]) { server.close(); return 0; }
  await new Promise((ok) => server.on("close", ok));
  return 0;
}
