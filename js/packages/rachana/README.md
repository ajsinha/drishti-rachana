# drishti-rachana

The browser and Node runtime: the compiler built from the Java reference (TeaVM, no hand-written logic), the facade, <rachana-view>, the Data Protocol client and the desktop bridge

**EXPERIMENTAL at Rachana 1.0 (gate 2, O9):** the facade (`init`, `evaluate`, `check`, `checked`, `grammar`, `view`, `screen`…), `<rachana-view>`'s attributes and events, `DataClient` and `StaticHost` may change in a minor release; the frozen contract is the JSON they answer (the ViewModel schemas, the Data Protocol). **Status (historical): planned** (A15 JavaScript build, B2 data client, B3 <rachana-view>). See [docs/plans/R0_R1_I8_PLAN.md](../../../docs/plans/R0_R1_I8_PLAN.md) and [docs/design/COMPILER_AND_RUNTIMES.md](../../../docs/design/COMPILER_AND_RUNTIMES.md).
