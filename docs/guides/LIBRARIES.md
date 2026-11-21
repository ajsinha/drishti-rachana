<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Libraries and `rachana lib`

A **library** is a versioned, signed folder of [typed fragments](LANGUAGE.md#7-fragments-and-fragments-as-functions) (and optionally
functions) that any Sutra can use. Screens become assembly: a Sutra names the library, uses its fragments and maps its own data onto
them. The normative rules are SPEC 2.5 "Libraries" ([spec/1/SPEC.md](../../spec/1/SPEC.md)); the libraries shipped with Rachana and
every fragment's parameters are in their generated pages: [core](../../libraries/core/README.md) and
[finance](../../libraries/finance/README.md) (index: [libraries/README.md](../../libraries/README.md)).

## 1. Using a library in a Sutra

```yaml
rachana: 1.2
sutra: orders
version: 1
libraries: [core@1]                 # each library and the major the Sutra is written against
title: { id: "'Orders'" }
data:
  orders: { rows: [ { ORDER_ID: A1, AMT_USD: 120 }, { ORDER_ID: A2, AMT_USD: 80 } ] }
panels:
  - use: core/data-table@1          # <library>/<fragment>@<major>; the major is required
    with:
      rows: $.orders
      columns: [ { label: Order, bind: "@.ORDER_ID" }, { label: Amount, bind: "@.AMT_USD", fmt: amount0, total: true } ]
```

`use: core@1/data-table@1` (the long form) works without a `libraries:` key. A library function is called as
`<library>.<name>(...)`. The compile result lists every library it was built from with its exact version and SHA-384.

## 2. Where libraries are found

A host finds libraries **on disk only, never on the network**, in this order of sources, all of which are read:

| Source | Used by |
|---|---|
| the libraries bundled with Rachana (`core`, `finance`) | every tool |
| `<project>/rachana_libs/` (library folders or `.rlib` files) | `lint`, `build`, `studio`, the server (`project.dir`) |
| `--libs <path>[,<path>]` | `rachana lint`, `build`, `studio`; `python -m drishti_rachana lint --libs` |
| `RACHANA_LIBS=<path>[:<path>]` | the same commands |
| `libraries:` in `rachana-server.yaml` | the [server](SERVER.md) |

Each path is a library folder, an `.rlib`, or a folder of them. A library name found twice with different contents is refused with
a problem (rename or remove one), never silently shadowed. A project's `rachana.lock` pins each library's exact version and the
SHA-384 of its canonical archive; a library not in the lock, or of another major, is `RCH-2111`; a hash or signature that does not
match is `RCH-2113`.

## 3. What a library is

```text
mylib/
  mylib.rachana-lib.yaml       the manifest: rachana, library, version (semver), title, summary, publisher, licence,
                               scope (public | private), profiles, depends, needs, categories; exports (generated)
  fragments/<name>.v<N>.fragment.yaml    one typed fragment per file and major
  functions/<file>.yaml        optional library functions
  contracts/<name>.json        optional published table contracts (Data Protocol schema form)
  examples/<name>.sutra.yaml   one example Sutra per fragment
  goldens/                     the ViewModel snapshot of each example
  MANIFEST.sha384              the SHA-384 of every file (generated)
  SIGNATURE                    optional Ed25519 signatures of MANIFEST.sha384
  README.md                    the library's page (generated)
```

Inside a library every use is `<library>/<fragment>@N` of itself or of a library it `depends` on. A typed fragment of a **public**
library reads only its parameters (`RCH-2117` for `$`, `ctl.`, `state.`, `source:`, `data:`, `url:`). Codes: `RCH-2110` not found,
`RCH-2112` invalid library.

The `.rlib` archive is a ZIP written deterministically (sorted entries, stored, a fixed time), so a folder and its archive have one
identity and one hash.

## 4. `rachana lib`

One implementation, the Java `LibraryTool`, reached from every launcher: `node js/packages/rachana-cli/bin/rachana.js lib ...` (with
`RACHANA_JAR` or `RACHANA_CLASSPATH` set; see [CLI and SDKs](CLI_AND_SDKS.md#the-java-launcher)) or
`java -cp <jars> com.ash.rachana.cli.Main lib ...`.

| Command | What it does |
|---|---|
| `lib build DIR` | regenerates the manifest's `exports` and `MANIFEST.sha384` from the files |
| `lib keygen` | prints a new Ed25519 key pair (public key, private seed) |
| `lib sign DIR PUBLIC-KEY PRIVATE-SEED` | adds a signature line over `MANIFEST.sha384` to `SIGNATURE` |
| `lib pack DIR [--out FOLDER] [--require-signed] [--publisher KEY]...` | writes `<name>-<version>.rlib` |
| `lib verify DIR-OR-RLIB [--require-signed] [--publisher KEY]...` | checks every rule a host checks when it loads the library |
| `lib lock DIR-OR-RLIB... [--lock rachana.lock]` | adds or updates each library's entry in the lock file |
| `lib gate DIR [--strict]` | the quality gates: LG1 library valid, LG2 frozen fragments unchanged, LG3 definition of done (docs, examples), LG4 level replay, LG5 interface append-only, LG8 budgets; exit 1 when one fails (`--strict` also fails on the LG3 notes, such as a missing second example) |
| `lib freeze DIR` | records the released baseline (`FROZEN.sha384`, `released/`) that LG2 and LG5 check later versions against; run `lib build` after |
| `lib contracts DIR-OR-RLIB [--json]` | the tables a host must serve to use the library |
| `lib check-host DIR-OR-RLIB --base URL [--table CONTRACT=TABLE]... [--json]` | checks each contract against a running host's table schemas, column by column |

Exit status: 0 done, 1 the library does not verify (or a host column breaks a fragment), 2 usage.

## 5. Building the bundled libraries

```bash
cd java && ./mvnw clean package -DskipTests && cd ..
python3 tools/libraries/build.py              # docs, build, test against goldens, gates, sign (development key), pack, verify
python3 tools/libraries/build.py --update-goldens core   # after an intended change of a fragment's output
```

`build.py` writes each library's `README.md` from its fragment files, so the library pages are never edited by hand. `--gallery`
also builds a gallery site of every fragment with Rachana itself. The same check runs in the Maven build (`LibrariesCheckTest`).

## 6. Your own library

Write the fragments and examples, then `rachana lib build`, `rachana lib gate`, sign and pack. Put the folder or the `.rlib` where your
hosts read libraries (section 2); nothing in Rachana is modified or rebuilt. A step-by-step run is in the tutorial
[Build and use a library](../tutorials/LIBRARY.md). In Studio, every fragment of every library found appears in the palette with a
parameter form ([Studio](STUDIO.md)).
