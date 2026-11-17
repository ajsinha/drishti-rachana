<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Rachana for AI: safe generative UI

Status: **planning.** All of this is planned (generation of Sutras by AI is phase R2; see [ROADMAP](ROADMAP.md)).

## The idea

A chat assistant can answer with a real dashboard instead of a wall of text, **without generating code that runs**. The
model writes a **Sutra**, which is declarative data. The compiler validates it. Nothing is executed: a Sutra can only
describe what to show, so a wrong or hostile answer cannot do more than show the wrong thing, and the compiler rejects
anything outside the language.

## Flow 1: a chatbot that draws dashboards

```mermaid
sequenceDiagram
  actor U as User
  participant Chat as Chat app
  participant LLM as LLM
  participant Tools as Data tools (MCP)
  participant RC as Rachana compiler
  participant View as Views (browser)
  U->>Chat: "Show revenue by region for last quarter"
  Chat->>LLM: question + tool list + Sutra schema and examples
  LLM->>Tools: call query_sales(quarter)
  Tools-->>LLM: rows
  LLM->>Chat: Sutra (YAML) + reference to the data
  Chat->>RC: validate(Sutra, table schemas)
  alt invalid
    RC-->>Chat: errors with line and column
    Chat->>LLM: "fix these errors"
    LLM->>Chat: corrected Sutra
    Chat->>RC: validate again
  end
  RC-->>Chat: ViewModel
  Chat->>View: draw ViewModel
  View-->>U: dashboard in the chat
```

Points that make it safe and reliable:

- **Data comes from tools**, over MCP, with the permissions the user already has. The model never gets a free query channel the host did not offer.
- **The model writes only the Sutra.** The data is bound by the compiler, not pasted into the model's output.
- **Errors are specific** (key, line, column), so the model can correct itself. The number of retries is capped by the chat app.
- **The schema and the grammar are given to the model** as context, so it writes valid Sutras first time more often.

## Flow 2: Rachana as an MCP server

Planned. A Rachana MCP server lets **any MCP-capable assistant** render dashboards, without that assistant's builder
integrating Rachana.

| Kind | Name | What it does |
|---|---|---|
| Tool | `validate_sutra` | Check a Sutra (and optionally table schemas); return errors with locations, or ok |
| Tool | `render_dashboard` | Compile a Sutra against supplied tables; return the ViewModel and a way to display it |
| Tool | `examples` | Return example Sutras for a stated purpose |
| Resource | schema | The Sutra JSON Schema |
| Resource | grammar | The Rachana-EL EBNF grammar |
| Resource | gallery | Reviewed example dashboards |

```mermaid
sequenceDiagram
  actor U as User
  participant A as Any MCP assistant
  participant R as Rachana MCP server
  participant D as Other MCP data servers
  U->>A: "Chart our tickets by priority"
  A->>R: read resources: schema, grammar, gallery
  A->>D: call tools to fetch tickets
  D-->>A: rows
  A->>A: write Sutra
  A->>R: validate_sutra(Sutra)
  R-->>A: errors or ok
  A->>R: render_dashboard(Sutra, tables)
  R-->>A: ViewModel + display payload
  A-->>U: dashboard
```

## Limits to state plainly

- A dashboard is only as good as the data the tools return; Rachana checks the structure of the Sutra, not the truth of the data.
- The model can still choose a misleading chart. The compiler guarantees validity and safety, not judgement.
- How the host application displays a ViewModel (inline, a link, an image) depends on the host; this is not designed yet.
