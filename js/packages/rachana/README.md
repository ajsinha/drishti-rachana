# drishti-rachana

The browser and Node runtime: the compiler built from the Java reference (TeaVM, no hand-written logic), the facade, <rachana-view>, the Data Protocol client (HTTP or the in-process bridge)

**EXPERIMENTAL at Rachana 1.0 (gate 2, O9):** the facade (`init`, `evaluate`, `check`, `checked`, `grammar`, `view`, `screen`…), `<rachana-view>`'s attributes and events, `DataClient` and `StaticHost` may change in a minor release; the frozen contract is the JSON they answer (the ViewModel schemas, the Data Protocol). How to use it: [docs/guides/CLI_AND_SDKS.md](../../../docs/guides/CLI_AND_SDKS.md); the design: [docs/design/COMPILER_AND_RUNTIMES.md](../../../docs/design/COMPILER_AND_RUNTIMES.md).
