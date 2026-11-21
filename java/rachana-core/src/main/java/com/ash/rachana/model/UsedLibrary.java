/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

/**
 * A library a compiled Sutra was built from (LIBRARIES.md 9.3, 12): its name, exact version and the SHA-384 of its canonical archive, so
 * a compile result says exactly which library bytes it stands on (a dependency of a declared library is listed too).
 *
 * @param name the library's name ({@code finance})
 * @param version its exact version ({@code 1.4.2})
 * @param sha384 the lower-case hex SHA-384 of its canonical {@code .rlib}
 */
public record UsedLibrary(String name, String version, String sha384) { }
