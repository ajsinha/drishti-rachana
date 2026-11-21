#!/usr/bin/env node
/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
import { main } from "../src/index.js";
process.exitCode = await main(process.argv.slice(2));
