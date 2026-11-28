# drishti-rachana

The browser and Node runtime: the compiler built from the Java reference (TeaVM, no hand-written logic), the facade, <rachana-view>, the Data Protocol client (HTTP or the in-process bridge)

**EXPERIMENTAL at Rachana 1.0:** the facade (`init`, `evaluate`, `check`, `checked`, `grammar`, `view`, `screen`…), `<rachana-view>`'s attributes and events, `DataClient` and `StaticHost` may change in a minor release; the frozen contract is the JSON they answer (the ViewModel schemas, the Data Protocol). How to use it: [docs/IN_YOUR_APP.md](../../../docs/IN_YOUR_APP.md#javascript-the-facade-and-rachana-view); the design: [docs/DESIGN.md](../../../docs/DESIGN.md#compiler-and-runtimes-design).
