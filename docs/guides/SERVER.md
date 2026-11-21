<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# The Rachana server

The standalone server (`java/rachana-server`) serves a project folder of Sutras as pages, the [Data Protocol](DATA_PROTOCOL.md) over
tables read from files, JDBC databases and REST endpoints and kept [live](LIVE_TABLES.md), sign-in, access rules, and the guarded edit
path with approvals. It is one JVM process configured by one YAML file.

## 1. Run it

```bash
cd java && ./mvnw clean package -DskipTests && cd ..        # after python3 tools/js/build_js.py: the jar carries the runtime bundle
java -jar java/rachana-server/target/rachana-server-<version>-all.jar check --config examples/server-demo/rachana-server.yaml
java -jar java/rachana-server/target/rachana-server-<version>-all.jar serve --config examples/server-demo/rachana-server.yaml
java -jar java/rachana-server/target/rachana-server-<version>-all.jar hash-password      # type a password, get its hash for users.yaml
```

`check` reads the whole file, prints the resolved settings and every problem at once, and starts nothing (exit 0 when the file is
valid). `serve --port 0` takes any free port. The same commands from Node: `rachana serve --config <file> [--port n] [--check]` with
`--jar` or `RACHANA_SERVER_JAR`. In the IDE: the launchers **Rachana server serve (server-demo)**, **check** and **hash-password**
([java/README.md](../../java/README.md#build-and-run-in-the-ide)).

The example [examples/server-demo](../../examples/server-demo/README.md) serves on `http://127.0.0.1:8090/` (user `dev`, password
`dev-only-pass`, development only).

## 2. The configuration file

Every key is shown, with its default and meaning, in
[rachana-server.example.yaml](../../java/rachana-server/rachana-server.example.yaml); that file is the one reference. The sections:

| Section | What it sets |
|---|---|
| `server` | `bind` (loopback by default), `port`, `origins`, `cookie-secure`, `behind-tls-proxy`, session lifetimes, `max-body` |
| `project` | `dir`: `sutras/` (any depth), `rachana_libs/`, `static/`, and `*.live.yaml` declarations (also in `live/`); `watch` reloads on change |
| `libraries` | more library folders or `.rlib` files ([Libraries](LIBRARIES.md)) |
| `assets.runtime` | serve another runtime bundle than the jar's |
| `drivers` | JDBC driver jars (none is bundled) |
| `tables` | each a Data Protocol table: `file` (`.csv`, `.json`, `.jsonl`/`.ndjson`, `.arrow`), `jdbc` + `query` + `params`, or `rest` + `path`; with `key`, `refresh`, `max-rows`, `where`, `columns`, `timeout`, `write` |
| `auth` | `mode`: `none` (loopback only), `users` (a users file of PBKDF2 hashes and roles) or `header` (a trusted reverse proxy's headers) |
| `access` | `default` (`signed-in` or `closed`), per table `read`, `unmask`, `edit {roles, columns}`, `approve`; per Sutra the roles that may open it |
| `edits` | `journal` (the outbox), `off` (tables whose edits are switched off), `hold` (tables whose changes wait for an approver) |

**Paths are explicit**: absolute, or from `${config}` (the configuration file's folder) or `${project}` (`project.dir`). A bare relative
path is refused. **Secrets are never written in the file**: a database password is `password-env` or `password-file`, a secret
header is `header-env`.

## 3. What it serves

| Address | What |
|---|---|
| `/` | the index of the Sutras the caller may open |
| `/v/<name>` | a Sutra's page: `<rachana-view>` over the Data Protocol, live |
| `/sutras/<name>.sutra.yaml` | the Sutra's text |
| `/data/v1/...` | the Data Protocol: tables, rows, query, changes, writes |
| `/static/...` | the project's `static/` folder |
| `/signin`, `/signout` | sign-in (users mode) |
| `/healthz`, `/metrics`, `/problems` | health, counters, and the Sutras and declarations set aside with their problems |

Tables are read with the reader's access applied: rows and columns they may not read are never sent, masked columns arrive masked.

## 4. Security

- Loopback by default. Binding another address needs `auth.mode: users` or `header`; `users` on a non-loopback address also needs
  `behind-tls-proxy: true` (a TLS proxy in front) and should set `cookie-secure: true`.
- A session ends after `session-idle` unused and `session-max` after sign-in.
- Every write needs the page's CSRF token (`X-Rachana-CSRF`, given in the page) and an allowed `Origin`.
- JDBC connections are read-only by default and rolled back after each read; queries take `:name` parameters through prepared
  statements.
- The log is one JSON line per event (requests, tables, writes, refusals), the audit trail of the edit path.

## 5. Writes back to the source

A table with `write:` takes the changes of its `*.live.yaml` declaration's `edit:` ([Live tables](LIVE_TABLES.md#4-editing)):

| Source | `write:` | How each change applies once |
|---|---|---|
| `.csv`, `.json`, `.jsonl`, `.ndjson` file | `{ journal: "${config}/data/<t>.applied.jsonl" }` | the file is replaced atomically and the change id recorded in the journal |
| JDBC | `{ table: <db table>, journal-table: <db table> }` | the update and the change id are written in one transaction |

An `.arrow` file is never written to. The tutorial [Edit a live table with approvals](../tutorials/LIVE_EDITS.md) runs it end to end.
