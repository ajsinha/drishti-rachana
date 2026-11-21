<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Rachana. Proprietary: see LICENSE. -->
# server-demo

The standalone server's example (`java/rachana-server`): one Sutra, two tables from files (`data/sales.csv`, `data/regions.json`), sign-in
with a users file. **Synthetic data**: fictional regions, products and figures.

| File | What |
|---|---|
| `rachana-server.yaml` | the one configuration file: port 8090, loopback, tables, `auth.mode: users` |
| `users.yaml` | one user, `dev`, password `dev-only-pass`. **Development only**: written here so the example runs; it protects nothing; never reuse it |
| `project/sutras/overview.sutra.yaml` | the page |
| `project/data/` | the two tables |

```bash
java -jar java/rachana-server/target/rachana-server-<version>-all.jar check --config examples/server-demo/rachana-server.yaml
java -jar java/rachana-server/target/rachana-server-<version>-all.jar serve --config examples/server-demo/rachana-server.yaml   # http://127.0.0.1:8090/ , user dev
node js/packages/rachana-cli/bin/rachana.js studio examples/server-demo/project/sutras --data examples/server-demo/project/data --port 4500
```

In the IDE: the launchers `Rachana server serve (server-demo)`, `check`, `hash-password` (type a password in the Run window, press Enter) and
`Rachana Studio (server-demo)`; see `java/README.md#build-and-run-in-the-ide`.
