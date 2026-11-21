/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { spawnSync } from "node:child_process";
import { UsageError } from "./errors.js";

/**
 * rachana serve --config <rachana-server.yaml> [--port <n>] [--jar <file>]: the standalone server (java/rachana-server), which is a JVM
 * process; this launcher adds nothing. The jar is --jar or RACHANA_SERVER_JAR (rachana-server-<version>-all.jar, built by
 * `./mvnw -pl rachana-server -am package` in java/); RACHANA_JAVA names the java binary (default: java).
 */
export function serverCommand(flags, env = process.env) {
  if (!flags.config) throw new UsageError("serve needs --config <rachana-server.yaml> (the one configuration file)");
  const jar = flags.jar ?? env.RACHANA_SERVER_JAR;
  if (!jar) return null;
  const args = ["-jar", jar, flags.check ? "check" : "serve", "--config", flags.config];
  if (flags.port != null) args.push("--port", String(flags.port));
  return [env.RACHANA_JAVA ?? "java", args];
}

export async function serve(flags, io) {
  const cmd = serverCommand(flags);
  if (!cmd) {
    io.err("rachana serve needs a JVM and the server jar: --jar <file> or RACHANA_SERVER_JAR (java/rachana-server: ./mvnw -pl rachana-server -am package).");
    return 2;
  }
  const r = spawnSync(cmd[0], cmd[1], { stdio: "inherit" });
  if (r.error) { io.err(`rachana serve: ${r.error.message}`); return 2; }
  return r.status ?? 1;
}
