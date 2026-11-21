/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * Sutra libraries (docs/design/LIBRARIES.md, EL_EXPANSION X42): the library manifest, the {@link com.ash.rachana.lib.LibraryResolver}
 * hook and its shipped resolvers, the lock file, the deterministic {@code .rlib} archive with SHA-384 hashes, and the adapter that
 * serves a library's fragments to {@code use:}. Everything here is pure data handling with no network; signature checking is a hook
 * ({@link com.ash.rachana.lib.SignatureVerifier}) a host supplies. Language level 1.2.
 */
package com.ash.rachana.lib;
