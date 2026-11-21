/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * Finds the libraries a Studio session can offer and reads each as the file map the compiler bundle takes (`files`:
 * `rachana_libs/<library>/<path>` to text, FORMAT 5.7). A library is an unpacked folder (a `*.rachana-lib.yaml` manifest at its
 * top) or a `.rlib` archive. Nothing is parsed here beyond the manifest's name and version; the bundle checks the library, and the
 * browser reads the typed parameters of its fragments. Signatures are not checked by Studio (the host's trust list is the CLI's).
 */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import { inflateRawSync } from "node:zlib";

const MAX_FILE = 2 * 1024 * 1024;
const MANIFEST = /\.rachana-lib\.yaml$/;

/** The entries of a ZIP archive as {name: Buffer}: stored and deflated entries, the central directory read from the end. */
export function readZip(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 65535); i--) if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) throw new Error("not a ZIP archive");
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const out = {};
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error("damaged ZIP central directory");
    const method = buf.readUInt16LE(p + 10), csize = buf.readUInt32LE(p + 20), usize = buf.readUInt32LE(p + 24);
    const nlen = buf.readUInt16LE(p + 28), xlen = buf.readUInt16LE(p + 30), clen = buf.readUInt16LE(p + 32), off = buf.readUInt32LE(p + 42);
    const name = buf.toString("utf8", p + 46, p + 46 + nlen);
    p += 46 + nlen + xlen + clen;
    if (name.endsWith("/")) continue;
    if (name.startsWith("/") || name.split("/").includes("..")) throw new Error(`unsafe entry name ${name}`);
    if (usize > MAX_FILE) continue;
    const data = off + 30 + buf.readUInt16LE(off + 26) + buf.readUInt16LE(off + 28);
    const body = buf.subarray(data, data + csize);
    out[name] = method === 0 ? Buffer.from(body) : method === 8 ? inflateRawSync(body) : (() => { throw new Error(`unsupported ZIP method ${method}`); })();
  }
  return out;
}

function walk(dir, rel, into) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    if (e.name.startsWith(".")) continue;
    const p = join(dir, e.name), r = rel ? `${rel}/${e.name}` : e.name;
    if (e.isDirectory()) walk(p, r, into);
    else if (e.isFile() && statSync(p).size <= MAX_FILE) into[r] = readFileSync(p, "utf8");
  }
}

const top = (text, key) => (text.match(new RegExp(`^${key}:\\s*"?([^"\\s#]+)"?`, "m")) ?? [])[1];

function describe(files, source) {
  const mf = Object.keys(files).find((n) => !n.includes("/") && MANIFEST.test(n));
  if (!mf) throw new Error(`${source}: no *.rachana-lib.yaml at the top`);
  const name = top(files[mf], "library"), version = top(files[mf], "version");
  if (!name) throw new Error(`${source}: the manifest names no library`);
  return { name, version: version ?? "", source, files };
}

/** One library from a folder or an archive; throws with the reason when it is neither. */
export function readLibrary(path) {
  const p = resolve(path);
  if (statSync(p).isDirectory()) { const files = {}; walk(p, "", files); return describe(files, p); }
  const files = {};
  for (const [n, b] of Object.entries(readZip(readFileSync(p)))) files[n] = b.toString("utf8");
  return describe(files, p);
}

/** Every library a path offers: itself when it is one, else each child folder or .rlib that is one. */
export function findLibraries(path, problems = []) {
  const p = resolve(path), out = [];
  if (!existsSync(p)) { problems.push(`no such library path: ${path}`); return out; }
  const one = (q) => { try { out.push(readLibrary(q)); } catch (e) { problems.push(String(e.message)); } };
  if (statSync(p).isFile()) { one(p); return out; }
  if (readdirSync(p).some((n) => MANIFEST.test(n))) { one(p); return out; }
  for (const n of readdirSync(p).sort()) {
    if (n.startsWith(".")) continue;
    const q = join(p, n), st = statSync(q);
    if (st.isDirectory() ? readdirSync(q).some((x) => MANIFEST.test(x)) : n.endsWith(".rlib")) one(q);
  }
  return out;
}

/** The libraries of a project: `<root>/rachana_libs`, then each of `extra` (paths), then RACHANA_LIBS (path-delimited). The first of a name wins. */
export function projectLibraries(root, extra = [], env = process.env) {
  const problems = [], seen = new Map();
  const paths = [join(root, "rachana_libs"), ...extra, ...(env.RACHANA_LIBS ? env.RACHANA_LIBS.split(process.platform === "win32" ? ";" : ":").filter(Boolean) : [])];
  for (const p of paths) {
    if (p === paths[0] && !existsSync(p)) continue;
    for (const l of findLibraries(p, problems)) if (!seen.has(l.name)) seen.set(l.name, l);
  }
  return { libraries: [...seen.values()], problems };
}

/** The `files` map of the compiler bundle for these libraries. */
export function bundleFiles(libraries) {
  const files = {};
  for (const l of libraries) for (const [n, t] of Object.entries(l.files)) files[`rachana_libs/${l.name}/${n}`] = t;
  return files;
}

