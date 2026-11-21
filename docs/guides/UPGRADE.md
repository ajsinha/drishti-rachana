<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Upgrading Sutras: `rachana upgrade`

Every Sutra and fragment declares its language level in its `rachana:` key, and every tool reads it at that level, so a level-1.0
file keeps its meaning on a 1.2 host without any change. `rachana upgrade` is for when you want a file to use the newer level: it
rewrites a level-1.0 Sutra or fragment for a later level **with the same meaning**, and tells you about every place where the meaning
could differ.

A Sutra uses fragments of its own level only: a level-1.2 Sutra that uses a fragment still at 1.0 (or without the key, which is 1.0)
is refused with `RCH-2008` naming both levels, never read at either level. Upgrade a Sutra and the fragments it uses together (one
run over their folders does).

## 1. Run it

```bash
rachana upgrade [paths] [--to 1.2] [--write] [--diff] [--report <file.json>] [--rules]
```

| Option | Meaning |
|---|---|
| `paths` | Sutras (`*.sutra.yaml`), fragments (`*.fragment.yaml`, `*.block.yaml`) or folders searched for them (dot folders and `node_modules` skipped) |
| `--to` | the target level (default: the newest) |
| *(none)* | a dry run: each file's changes and flags are printed, nothing is written |
| `--diff` | also print a unified diff per file |
| `--write` | write the files |
| `--report <file.json>` | write every change and flag as JSON |
| `--rules` | list the rules |

Exit status: 0 done; 1 a file was refused or holds a blocking flag (nothing is written for it); 2 usage. It needs a JVM, as `lib` does
([the Java launcher](CLI_AND_SDKS.md#the-java-launcher)); `python -m drishti_rachana upgrade` and
`java -cp <jars> com.ash.rachana.cli.Main upgrade` run the same implementation (`java/rachana-tools` `Upgrade`).

```text
$ rachana upgrade prices.sutra.yaml --diff
prices.sutra.yaml: sutra 1.0 -> 1.2, 2 change(s), 0 flag(s)
  change declare /rachana (line 1): 1  ->  1.2
  change span /panels/0/value (line 9): range($.prices, 'close')  ->  span($.prices, 'close')
--- a/prices.sutra.yaml
+++ b/prices.sutra.yaml
@@ -1,4 +1,4 @@
-rachana: 1
+rachana: 1.2
...
1 file(s): 1 to upgrade (dry run: --write writes them), 0 refused or blocked
```

## 2. What it changes

Only the words a rule rewrites change: comments, layout and quoting are kept (the tool reads the file again to make sure). It is
idempotent: a file already at the target level is left untouched.

| Rule | Kind | What |
|---|---|---|
| `declare` | rewrite | the file declares the target level (a fragment without the key gains it as its first key) |
| `span` | rewrite | `range(list, field)`, the 1.0 aggregate, becomes `span(list, field)` |
| `named` | rewrite | a named argument takes the function catalogue's name |
| `exact` | flag, review | arithmetic that 1.2 computes exactly (decimals) |
| `number-text` | flag, review | number-like text that 1.2 does not read as a number |
| `standardize` | flag, review | `standardize` returns a number |
| `aggregate-not-a-list` | flag, review | an aggregate over something that is not a list |
| `text-code-points` | flag, info | texts ordered and counted by code point (beyond U+FFFF) |
| `regex-code` | flag, info | a refused regular expression met at evaluation is `RCH-2102` |
| `keyword` | flag, **blocking** | a name that is a word of the 1.1 grammar (such as a bare `lambda`): the file is not written; rename it first |

`rachana upgrade --rules` prints the full list, including the differences that need no change. Read each **review** flag: it marks a
place where the 1.2 answer can differ from the 1.0 answer for some data.

## 3. How it is proven

The rule list is checked against the specification's documented differences between the levels; each rule has verified cases
(`compat/upgrade/cases.yaml`), each bound at both levels on one host. A whole customer corpus (hundreds of Sutras and fragments) is
upgraded in the build and compared with its recorded ViewModels byte for byte (`UpgradeCorpusTest`).
