<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Tutorial: edit a live table with approvals

You will run the Rachana server over a CSV file, declare the table live and editable, let one user change a quantity, and have a
second user approve the change before it is written back to the file. Guides: [Server](../guides/SERVER.md),
[Live tables, editing and approvals](../guides/LIVE_TABLES.md), [Data Protocol](../guides/DATA_PROTOCOL.md).

## Prepare your environment

JDK 21, Python 3 and `curl`. From the repository root, build the runtime bundle first, then the jars (the server jar carries the bundle):

```bash
python3 tools/js/build_js.py
cd java && ./mvnw clean package -DskipTests && cd ..
export SERVER_JAR="$PWD/$(ls java/rachana-server/target/rachana-server-*-all.jar)"
```

In the IDE: **Rachana JS runtime bundle**, then **Rachana build all** ([java/README.md](../../java/README.md#build-and-run-in-the-ide)).
Work in a new folder `orders-demo`; every path below is inside it.

## Step 1: the table

`project/data/orders.csv`:

```text
ID,V,QTY,BY_USER
A-1,1,10,system
A-2,1,25,system
```

`V` is the row's version and `BY_USER` who changed it last; the server maintains both when it writes a change.

## Step 2: two users

```text
$ java -jar "$SERVER_JAR" hash-password
tutorial-pass                      (typed)
pbkdf2-sha256$210000$l3bJ...87JQ
```

`users.yaml`, with that hash for both (a development password; never reuse it):

```yaml
users:
  alice: { password: "pbkdf2-sha256$210000$...", roles: [sales] }
  carol: { password: "pbkdf2-sha256$210000$...", roles: [sales, checker] }
```

## Step 3: the server configuration

`rachana-server.yaml`:

```yaml
rachana-server: 1
server: { bind: 127.0.0.1, port: 8095 }
project: { dir: "${config}/project", watch: true }
tables:
  orders:
    file: "${project}/data/orders.csv"
    key: ID
    refresh: 1s                                               # read again every second; changes go to readers as patches
    write: { journal: "${config}/data/orders.applied.jsonl" } # each applied change id, recorded once
auth: { mode: users, users: "${config}/users.yaml" }
access:
  default: signed-in
  tables:
    orders: { read: [sales], edit: { roles: [sales], columns: [QTY] }, approve: [checker] }
edits:
  journal: "${config}/data/outbox.jsonl"                      # the outbox: a change is journalled before it is acknowledged
  hold: [orders]                                              # four eyes: every change waits for an approver
```

## Step 4: declare the table live and editable

`project/orders.live.yaml`:

```yaml
rachana: 1
live-table: orders
title: Orders
snapshot: orders
stream: orders
key: ID
version: V
changed-by: BY_USER
tray: true
columns:
  - { name: ID, label: Order }
  - { name: V, type: integer }
  - { name: QTY, type: integer, label: Quantity }
  - { name: BY_USER, label: Changed by }
edit:
  fields:
    - { id: qty, bind: QTY, type: number, min: 0 }
```

## Step 5: the screen

`project/sutras/orders.sutra.yaml`: the core library's live grid and the tray of pending changes.

```yaml
rachana: 1.2
sutra: orders
version: 1
libraries: [core@1]
title: { id: "'Orders'" }
data:
  orders:  { table: orders, key: ID, live: true }
  pending: { table: orders.changes, key: change, live: true }
panels:
  - use: core/live-table@1
    with:
      title: Orders
      rows: $.orders
      order: V
      edit: cells
      columns:
        - { label: Order, bind: "@.ID" }
        - { label: Quantity, bind: "@.QTY", fmt: amount0 }
        - { label: Changed by, bind: "@.BY_USER" }
  - use: core/held-changes@1
    with:
      changes: $.pending
```

Check both files: `cd project && rachana lint` answers `2 file(s), 0 failing problem(s)` (the `rachana` alias of
[Your first dashboard](FIRST_DASHBOARD.md#prepare-your-environment)).

## Step 6: start the server

```text
$ java -jar "$SERVER_JAR" check --config "$PWD/rachana-server.yaml"
config /.../orders-demo/rachana-server.yaml
project /.../orders-demo/project (level 1.2, watched)
table orders: file /.../orders-demo/project/data/orders.csv, every 1000 ms
auth users /.../orders-demo/users.yaml
$ java -jar "$SERVER_JAR" serve --config "$PWD/rachana-server.yaml"
rachana-server listening on http://127.0.0.1:8095/
```

The log is one JSON line per event; `{"event":"table","table":"orders","file":"orders.live.yaml","snapshot":"orders"}` says the
declaration was taken.

## Step 7: change a quantity (alice)

**In the UI:** open `http://127.0.0.1:8095/`, sign in as `alice`, open **orders**. Double-click the Quantity of `A-1`, type `12`, press
Enter. The grid still shows 10: the change is held. **Pending changes** lists it: `held`, `A-1: QTY = 12`, `alice`, "waiting for an
approver".

**From the command line:** sign in with a cookie jar, read the page's CSRF token, and send the change:

```bash
B=http://127.0.0.1:8095
signin() {   # user: a session cookie in <user>.jar
  F=$(curl -s -c $1.jar -b $1.jar $B/signin)
  C=$(echo "$F" | grep -oE 'name="?csrf"? value="[^"]+"' | sed 's/.*value="//;s/"//')
  curl -s -o /dev/null -c $1.jar -b $1.jar -H "Origin: $B" --data-urlencode "csrf=$C" --data-urlencode "next=/" \
       --data-urlencode "user=$1" --data-urlencode "password=tutorial-pass" $B/signin
}
token() { curl -s -b $1.jar $B/ | grep -oE 'name="?csrf"? (content|value)="[^"]+"' | head -1 | sed 's/.*="//;s/"//'; }
write() { curl -s -b $1.jar -H "Origin: $B" -H "X-Rachana-CSRF: $(token $1)" -H "Content-Type: application/json" -d "$3" $B/data/v1/tables/$2/write; echo; }
signin alice; signin carol
write alice orders '{"id":"c1","key":["A-1"],"basedOn":1,"set":{"QTY":12}}'
```

```json
{"change":"c1","state":"held","message":"waiting for an approver"}
```

The change names the row's key, the version it was made on (`basedOn`) and the columns it sets; `id` is chosen by the client, so the
same change sent twice is taken once.

## Step 8: approve it (carol)

**In the UI:** in another browser (or a private window) sign in as `carol` and open **orders**. **Pending changes** shows alice's held
change. Double-click its State, pick **released** (or **rejected**). Within a second the grid shows `A-1` at 12, changed by alice.
Alice cannot release her own change: an approver never decides their own.

**From the command line:**

```bash
curl -s -b carol.jar -H "Origin: $B" -H "X-Rachana-CSRF: $(token carol)" -H "Content-Type: application/json" \
     -d '{"columns":["change","state","by","summary"]}' $B/data/v1/tables/orders.changes/query; echo
write alice orders.changes '{"id":"d1","key":["c1"],"basedOn":"held","set":{"state":"released"}}'
write carol orders.changes '{"id":"d2","key":["c1"],"basedOn":"held","set":{"state":"released"}}'
```

```text
{"table":"orders.changes",...,"columns":["change","state","by","summary"],"values":[["c1","held","alice","A-1: QTY = 12"]],"total":1}
{"error":{"code":"rdp.write.forbidden","message":"you may not decide the changes of orders","table":"orders.changes"}}
{"change":"d2","state":"applied","message":"c1 released: released by carol"}
```

## Step 9: see it everywhere

```text
$ cat project/data/orders.csv
ID,V,QTY,BY_USER
A-1,2,12,alice
A-2,1,25,system
$ cat data/orders.applied.jsonl
{"change":"c1","state":"writing","at":"..."}
{"change":"c1","state":"applied","at":"..."}
$ curl -s -b alice.jar "$B/data/v1/tables/orders/rows?shape=objects"
{"table":"orders","version":{"epoch":"e1","seq":1},"rows":[{"ID":"A-1","V":2,"QTY":12,"BY_USER":"alice"},...],"total":2}
```

The server log holds the audit trail: `write-submitted`, `write-held`, `write-refused` (alice's attempt), `write-released`,
`write-sent`.

## Step 10: what the guards refuse

```bash
write alice orders '{"id":"c3","key":["A-1"],"basedOn":1,"set":{"QTY":99}}'    # made on an old version
write alice orders '{"id":"c4","key":["A-2"],"basedOn":1,"set":{"QTY":-5}}'    # breaks the field's rule
curl -s -b alice.jar -H "Origin: $B" -H "Content-Type: application/json" \
     -d '{"id":"c6","key":["A-2"],"basedOn":1,"set":{"QTY":31}}' $B/data/v1/tables/orders/write; echo   # no CSRF token
```

```text
{"error":{"code":"rdp.write.conflict","message":"the row changed since version 1: it is at version 2, changed by alice","table":"orders","current":{"version":2,"by":"alice"}}}
{"error":{"code":"rdp.write.invalid","message":"the change breaks 1 rule: qty is at least 0","table":"orders","fields":[{"column":"QTY","rule":"value","message":"qty is at least 0"}]}}
{"error":{"code":"rdp.write.forbidden","message":"the CSRF token is missing or wrong"}}
```

Stop the server with Ctrl+C. To switch a table's edits off, list it under `edits.off`; to take changes without approval, remove it
from `edits.hold` (both read at start). A database table works the same way with `write: { table, journal-table }`
([Server](../guides/SERVER.md#5-writes-back-to-the-source)).
