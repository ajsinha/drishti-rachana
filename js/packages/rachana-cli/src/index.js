/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * The command line: rachana new / dev / build / lint / preview / lib. Each command is a function over the compiler bundle
 * (the tools bundle, generated from the Java reference); no language logic is written here. `lib` hands over to the jar,
 * which holds the one library implementation (LibraryTool).
 */
import { lint, findSutras } from "./lint.js";
import { scaffold } from "./scaffold.js";
import { build } from "./build.js";
import { preview } from "./serve.js";
import { lib } from "./lib.js";

export const STATUS = "lint, new, build (painter step pending lane C), preview, lib";

const HELP = `rachana <command>
  new <dir> [--template <name>]                 a starter folder: a Sutra, sample data, rachana.yaml
  lint [paths] [--format text|json|sarif] [--strict]   compile and lint; exit 1 on errors
  build [--src <dir>] [--out site]              a static site: Sutras, data, runtime bundle, pages
  preview <file> [--data <dir>] [--port <n>]    one screen in the browser (dev <dir> serves a folder)
  lib build|sign|keygen|pack|verify|lock ...    libraries (needs a JVM: RACHANA_JAR or RACHANA_CLASSPATH)
`;

/** Splits argv into positional arguments and --flags (a flag takes a value unless it is a known switch). */
export function parseArgs(argv, switches = ["strict", "help"]) {
  const pos = [], flags = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith("--")) {
      const k = a.slice(2);
      if (switches.includes(k)) flags[k] = true;
      else if (i + 1 < argv.length) flags[k] = argv[++i];
      else throw new UsageError(`--${k} needs a value`);
    } else pos.push(a);
  }
  return { pos, flags };
}

import { UsageError } from "./errors.js";
export { UsageError };

/** Runs a command line; returns the exit status (0 done, 1 problems, 2 usage). */
export async function main(argv, io = { out: (s) => process.stdout.write(s + "\n"), err: (s) => process.stderr.write(s + "\n") }) {
  const [cmd, ...rest] = argv;
  try {
    if (!cmd || cmd === "help" || cmd === "--help") { io.out(HELP); return cmd ? 0 : 2; }
    if (cmd === "lib") return await lib(rest, io);
    const { pos, flags } = parseArgs(rest);
    switch (cmd) {
      case "new": return scaffold(pos[0], flags.template, io);
      case "lint": return await lint(pos.length ? pos : ["."], flags, io);
      case "build": return await build(flags.src ?? pos[0] ?? ".", flags.out ?? "site", io);
      case "preview": case "dev": return await preview(pos[0] ?? ".", flags, io);
      default: io.err(`rachana: unknown command '${cmd}'\n${HELP}`); return 2;
    }
  } catch (e) {
    if (e instanceof UsageError) { io.err(`rachana: ${e.message}`); return 2; }
    throw e;
  }
}

export { findSutras };
