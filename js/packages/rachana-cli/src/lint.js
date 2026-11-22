/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { basename, join, relative } from "node:path";
import { tools } from "./bundle.js";
import { UsageError } from "./errors.js";
import { projectLibraries, bundleFiles } from "../../rachana-studio/server/library_files.js";

/** The language levels and the default of the tools, from the bundle (Core's Grammar.DEFAULT_LEVEL: the one place the default is written). */
export async function languageLevels(profiles = "core") {
  return JSON.parse((await tools(profiles)).levels());
}
/** The answer to --level, which no command takes: a Sutra declares its own level (SPEC 2.1). */
export const LEVEL_FLAG = "--level is gone: a Sutra declares its language level in its rachana: key (rachana: 1 is 1.2)";
export const SUFFIX = ".sutra.yaml";
export const LIVE_SUFFIX = ".live.yaml";
/** The profile keys of a live table's declaration that the Drishti profile reads (LIVE_TABLES 2); none in core. */
export const DRISHTI_LIVE_KEYS = ["kind", "query", "links", "edit.roles", "edit.save", "edit.fields.editable-by"];

/**
 * Every *.sutra.yaml under the paths (files as given; folders searched, dot folders and node_modules skipped); `suffixes` names other
 * file kinds to find instead (lint also takes live tables' *.live.yaml).
 */
export function findSutras(paths, suffixes = [SUFFIX]) {
  const out = [];
  const walk = (dir) => {
    for (const n of readdirSync(dir).sort()) {
      if (n.startsWith(".") || n === "node_modules") continue;
      const p = join(dir, n);
      if (statSync(p).isDirectory()) walk(p); else if (suffixes.some((x) => n.endsWith(x))) out.push(p);
    }
  };
  for (const p of paths) {
    if (!existsSync(p)) throw new UsageError(`no such file or folder: ${p}`);
    if (statSync(p).isDirectory()) walk(p); else out.push(p);
  }
  return out;
}

/**
 * Per file: { file, ok, problems } from the bundle's check (the problem objects of SPEC section 7, identical to the JVM and Python); a
 * live table's declaration (*.live.yaml) by the tools bundle's declaration checker (LIVE_TABLES 2).
 */
export async function lintFiles(files, profiles = "core", level = null, libFiles = null) {
  const r = await tools(profiles);
  level = level ?? JSON.parse(r.levels()).default;
  const profileKeys = profiles.split(",").includes("drishti") ? DRISHTI_LIVE_KEYS : [];
  return files.map((file) => {
    const text = readFileSync(file, "utf8");
    let res;
    if (file.endsWith(LIVE_SUFFIX)) {
      res = JSON.parse(r.live(JSON.stringify({ declaration: text, file: basename(file), profileKeys })));
    } else {
      const req = { sutra: text, file, since: level };
      if (libFiles && Object.keys(libFiles).length) req.files = libFiles;
      res = JSON.parse(r.check(JSON.stringify(req)));
    }
    if (res.error) throw new Error(`${file}: ${res.error}`);
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
    runs: [{ tool: { driver: { name: "rachana", informationUri: "https://github.com/drishti-platform/drishti-rachana" } },
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
  if (flags.level !== undefined) throw new UsageError(LEVEL_FLAG);
  const lib = libraryFiles(paths[0] && existsSync(paths[0]) && statSync(paths[0]).isDirectory() ? paths[0] : ".", flags, (m) => io.err(`rachana: ${m}`));
  const results = await lintFiles(findSutras(paths, [SUFFIX, LIVE_SUFFIX]), flags.profiles ?? "core", null, lib.files);
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
