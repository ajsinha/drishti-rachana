/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * The server behind `rachana studio <folder>`: the Studio page and its files, the preview's assets and data (the preview server's
 * handler), and a small local API over one project folder:
 *
 *   GET  /api/project            the Sutras, the data tables (with columns), the libraries found, the language level
 *   GET  /api/file?path=<rel>    a Sutra of the project, as text
 *   POST /api/check              {sutra, file} -> the compiler bundle's check at level 1.2, libraries in place
 *   POST /api/save               {path, text} -> writes a *.sutra.yaml inside the project folder
 *
 * Local only: it listens on 127.0.0.1, answers only a Host of this machine and this port (DNS rebinding), takes a write only with the
 * Studio's own header (a page of another origin cannot send it without a preflight, which is never answered) and only for a path that
 * stays inside the project folder (symbolic links resolved) and ends in .sutra.yaml. Nothing is written anywhere else.
 */
import { createServer } from "node:http";
import { existsSync, mkdirSync, readFileSync, readdirSync, realpathSync, renameSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, extname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { handler } from "../../rachana-cli/src/serve.js";
import { findSutras, SUFFIX } from "../../rachana-cli/src/lint.js";
import { tools } from "../../rachana-cli/src/bundle.js";
import { parseCsv } from "../../rachana/src/static_files_host.js";
import { bundleFiles, projectLibraries } from "./library_files.js";

const HERE = dirname(fileURLToPath(import.meta.url));
const SRC = join(HERE, "..", "src");
const YAML_JS = join(HERE, "..", "..", "..", "vendor", "js-yaml", "4.1.0", "js-yaml.min.js");
export const STUDIO_HEADER = "x-rachana-studio";
const MAX_BODY = 2 * 1024 * 1024;
const TYPES = { ".html": "text/html; charset=utf-8", ".css": "text/css", ".js": "text/javascript", ".json": "application/json" };
const STUDIO_FILES = new Map([["studio/rachana_studio.js", "rachana_studio.js"], ["studio/rachana_studio_model.js", "rachana_studio_model.js"], ["studio/rachana_studio.css", "rachana_studio.css"]]);

/** The path inside root, symbolic links resolved, or null when it leaves root. */
export function within(root, rel) {
  if (typeof rel !== "string" || rel.includes("\0")) return null;
  const base = realpathSync(root), p = resolve(base, "." + sep + rel);
  if (p !== base && !p.startsWith(base + sep)) return null;
  let probe = p;
  while (!existsSync(probe) && probe !== base) probe = dirname(probe);
  const real = realpathSync(probe);
  return real === base || real.startsWith(base + sep) ? p : null;
}

/** Name, type and row count of each data file of a folder: the type is number when the first 50 values of the column are all numbers. */
export function tableInfo(dir) {
  if (!dir || !existsSync(dir)) return [];
  return readdirSync(dir).filter((n) => /\.(csv|jsonl|json)$/.test(n)).sort().map((file) => {
    const ext = extname(file).slice(1), text = readFileSync(join(dir, file), "utf8");
    let rows = [];
    try { rows = ext === "csv" ? parseCsv(text) : ext === "jsonl" ? text.split("\n").filter((l) => l.trim()).map((l) => JSON.parse(l)) : (() => { const v = JSON.parse(text); return Array.isArray(v) ? v : v.rows ?? []; })(); } catch { rows = []; }
    const sample = rows.slice(0, 50), names = [...new Set(sample.flatMap((r) => Object.keys(r)))];
    const columns = names.map((name) => { const vs = sample.map((r) => r[name]).filter((v) => v !== null && v !== undefined); return { name, type: vs.length && vs.every((v) => typeof v === "number") ? "number" : "text" }; });
    return { name: file.slice(0, -(ext.length + 1)), file, rows: rows.length, columns };
  });
}

const json = (s, v) => [s, TYPES[".json"], JSON.stringify(v)];

/** A Studio over one project folder. `libs` are extra library paths (folders or .rlib); `dataDir` the data folder (default: root/data). */
export function createStudio(root, { libs = [], dataDir = null, env = process.env } = {}) {
  root = realpathSync(resolve(root));
  const data = dataDir ?? (existsSync(join(root, "data")) ? join(root, "data") : null);
  const preview = handler(new Map(), data);
  let found = null;
  const libraries = () => (found ??= projectLibraries(root, libs, env));
  const fileOf = (rel) => { const f = within(root, rel); return f && existsSync(f) && statSync(f).isFile() ? f : null; };
  const relOf = (f) => relative(root, f).split(sep).join("/");

  /** [status, type, body] for a request that needs no body. */
  function get(url) {
    const u = new URL(url, "http://x"), path = decodeURIComponent(u.pathname);
    if (path === "/" || path === "/index.html") return [200, TYPES[".html"], readFileSync(join(SRC, "studio.html"))];
    if (STUDIO_FILES.has(path.slice(1))) return [200, TYPES[extname(path)], readFileSync(join(SRC, STUDIO_FILES.get(path.slice(1))))];
    if (path === "/vendor/js-yaml/4.1.0/js-yaml.min.js") return [200, TYPES[".js"], readFileSync(YAML_JS)];
    if (path === "/api/project") {
      const { libraries: ls, problems } = libraries();
      return json(200, { name: root.split(sep).pop(), sutras: findSutras([root]).map(relOf).filter((r) => !r.split("/").includes("rachana_libs")), tables: tableInfo(data),
        libraries: ls.map((l) => ({ name: l.name, version: l.version, source: l.source, files: l.files })), problems });
    }
    if (path === "/api/file") {
      const f = fileOf(u.searchParams.get("path"));
      return f && f.endsWith(SUFFIX) ? [200, "text/plain; charset=utf-8", readFileSync(f, "utf8")] : [404, "text/plain", "no such Sutra in the project"];
    }
    return preview(url);
  }

  async function post(url, body) {
    const path = new URL(url, "http://x").pathname;
    let req;
    try { req = JSON.parse(body); } catch { return json(400, { error: "the body is not JSON" }); }
    if (path === "/api/check") {
      if (typeof req.sutra !== "string") return json(400, { error: "sutra is text" });
      const t = await tools();
      return [200, TYPES[".json"], t.check(JSON.stringify({ sutra: req.sutra, file: String(req.file ?? "case.sutra.yaml"), since: JSON.parse(t.levels()).default, files: bundleFiles(libraries().libraries) }))];
    }
    if (path === "/api/save") {
      if (typeof req.path !== "string" || typeof req.text !== "string") return json(400, { error: "path and text are needed" });
      if (!req.path.endsWith(SUFFIX)) return json(400, { error: `only a *${SUFFIX} file can be saved` });
      const f = within(root, req.path);
      if (!f) return json(403, { error: "the path is outside the project folder" });
      if (relOf(f).split("/").some((s) => s.startsWith("."))) return json(400, { error: "a hidden file or folder is not written" });
      mkdirSync(dirname(f), { recursive: true });
      if (!within(root, req.path)) return json(403, { error: "the path is outside the project folder" });
      const tmp = `${f}.studio-${process.pid}.tmp`;
      try { writeFileSync(tmp, req.text, "utf8"); renameSync(tmp, f); } catch (e) { rmSync(tmp, { force: true }); throw e; }
      return json(200, { saved: relOf(f), bytes: Buffer.byteLength(req.text) });
    }
    return [404, "text/plain", "not found"];
  }

  /** The request handler: Host check, then GET (pure) or POST (the Studio's header, a body of at most 2 MB). */
  function serve(port) {
    return (req, res) => {
      const send = ([s, t, b]) => { res.writeHead(s, { "content-type": t, "cache-control": "no-store", "x-content-type-options": "nosniff" }); res.end(b); };
      const host = (req.headers.host ?? "").toLowerCase();
      if (host !== `127.0.0.1:${port}` && host !== `localhost:${port}`) return send([403, "text/plain", "this Studio answers only on this machine"]);
      try {
        if (req.method === "GET") return send(get(req.url));
        if (req.method !== "POST") return send([405, "text/plain", "method not allowed"]);
        if (req.headers[STUDIO_HEADER] !== "1") return send([403, "text/plain", "missing the Studio header"]);
        const chunks = []; let n = 0, over = false;
        req.on("data", (c) => { n += c.length; if (n > MAX_BODY) over = true; else chunks.push(c); });
        req.on("end", () => { if (over) return send([413, "text/plain", "too large"]); post(req.url, Buffer.concat(chunks).toString("utf8")).then(send, (e) => send([500, "text/plain", String(e.message)])); });
      } catch (e) { send([500, "text/plain", String(e.message)]); }
    };
  }

  return { root, get, post, serve, libraries };
}

/** Starts the Studio; resolves with {server, url}. */
export async function listen(studio, port = 0) {
  const server = createServer();
  await new Promise((ok) => server.listen(port, "127.0.0.1", ok));
  server.on("request", studio.serve(server.address().port));
  return { server, url: `http://127.0.0.1:${server.address().port}/` };
}

