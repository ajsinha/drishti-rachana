/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, relative } from "node:path";
import { tools } from "./bundle.js";
import { UsageError } from "./errors.js";
import { projectLibraries, bundleFiles } from "../../rachana-studio/server/library_files.js";

export const DEFAULT_LEVEL = "1.1";   // the newest built language level; --level 1.2 for typed fragments and libraries
export const LEVELS = ["1.0", "1.1", "1.2"];
export const SUFFIX = ".sutra.yaml";

/** Every *.sutra.yaml under the paths (files as given; folders searched, dot folders and node_modules skipped). */
export function findSutras(paths) {
  const out = [];
  const walk = (dir) => {
    for (const n of readdirSync(dir).sort()) {
      if (n.startsWith(".") || n === "node_modules") continue;
      const p = join(dir, n);
      if (statSync(p).isDirectory()) walk(p); else if (n.endsWith(SUFFIX)) out.push(p);
    }
  };
  for (const p of paths) {
    if (!existsSync(p)) throw new UsageError(`no such file or folder: ${p}`);
    if (statSync(p).isDirectory()) walk(p); else out.push(p);
  }
  return out;
}

/** Per file: { file, ok, problems } from the bundle's check (the problem objects of SPEC section 7, identical to the JVM and Python). */
export async function lintFiles(files, profiles = "core", level = DEFAULT_LEVEL, libFiles = null) {
  const r = await tools(profiles);
  return files.map((file) => {
    const req = { sutra: readFileSync(file, "utf8"), file, since: level };
    if (libFiles && Object.keys(libFiles).length) req.files = libFiles;
    const res = JSON.parse(r.check(JSON.stringify(req)));
    return { file, ok: res.ok, problems: res.problems };
  });
}

/** The `files` map of the libraries for a lint or a build: the project's `rachana_libs`, each --libs path, RACHANA_LIBS and the bundled
 * libraries. A name found twice with different contents is a refusal, reported through `onProblem`. */
export function libraryFiles(root, flags, onProblem = () => {}) {
  const extra = flags.libs ? String(flags.libs).split(",").filter(Boolean) : [];
  const { libraries, problems } = projectLibraries(root, extra);
  problems.forEach(onProblem);
  return { files: bundleFiles(libraries), problems };
}

export function sarif(results) {
  return {
    version: "2.1.0", $schema: "https://json.schemastore.org/sarif-2.1.0.json",
    runs: [{ tool: { driver: { name: "rachana", informationUri: "https://github.com/ajsinha/drishti-rachana" } },
      results: results.flatMap((r) => r.problems.map((p) => ({
        ruleId: p.code, level: p.severity === "error" ? "error" : p.severity === "warning" ? "warning" : "note",
        message: { text: p.message },
        locations: [{ physicalLocation: { artifactLocation: { uri: r.file },
          region: { startLine: p.range?.start?.line ?? 1, startColumn: p.range?.start?.column ?? 1 } } }] }))) }],
  };
}

export async function lint(paths, flags, io) {
  const fmt = flags.format ?? "text";
  if (!["text", "json", "sarif"].includes(fmt)) throw new UsageError("--format is text, json or sarif");
  const level = flags.level ?? DEFAULT_LEVEL;
  if (!LEVELS.includes(level)) throw new UsageError("--level is 1.0, 1.1 or 1.2");
  const lib = libraryFiles(paths[0] && existsSync(paths[0]) && statSync(paths[0]).isDirectory() ? paths[0] : ".", flags, (m) => io.err(`rachana: ${m}`));
  const results = await lintFiles(findSutras(paths), flags.profiles ?? "core", level, lib.files);
  const failing = results.flatMap((r) => r.problems).filter((p) => p.severity === "error" || (flags.strict && p.severity === "warning")).length + lib.problems.length;
  if (fmt === "json") io.out(JSON.stringify(results, null, 2));
  else if (fmt === "sarif") io.out(JSON.stringify(sarif(results), null, 2));
  else {
    for (const r of results) for (const p of r.problems) {
      const s = p.range?.start ?? {};
      io.out(`${relative(".", r.file)}:${s.line ?? 1}:${s.column ?? 1}: ${p.severity} ${p.code}: ${(p.message ?? "").split("\n")[0]}`);
    }
    io.out(`${results.length} file(s), ${failing} failing problem(s)`);
  }
  return failing ? 1 : 0;
}
