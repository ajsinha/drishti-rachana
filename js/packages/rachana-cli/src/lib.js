/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { spawnSync } from "node:child_process";

/**
 * rachana lib ...: the library commands are the Java LibraryTool's, reached through com.ash.rachana.cli.Main; this launcher
 * adds nothing. RACHANA_JAR is a runnable (shaded) jar; RACHANA_CLASSPATH is a class path of the module jars (the target/classes folders
 * or jars). RACHANA_JAVA names the java binary (default: java).
 */
export function libCommand(args, env = process.env) {
  const java = env.RACHANA_JAVA ?? "java";
  if (env.RACHANA_JAR) return [java, ["-jar", env.RACHANA_JAR, "lib", ...args]];
  if (env.RACHANA_CLASSPATH) return [java, ["-cp", env.RACHANA_CLASSPATH, "com.ash.rachana.cli.Main", "lib", ...args]];
  return null;
}

export async function lib(args, io) {
  const cmd = libCommand(args);
  if (!cmd) {
    io.err("rachana lib needs a JVM and the rachana-cli jar: set RACHANA_JAR (a runnable jar) or RACHANA_CLASSPATH (the module jars). One implementation: java/rachana-tools LibraryTool.");
    return 2;
  }
  const r = spawnSync(cmd[0], cmd[1], { stdio: "inherit" });
  if (r.error) { io.err(`rachana lib: ${r.error.message}`); return 2; }
  return r.status ?? 1;
}
