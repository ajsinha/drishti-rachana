/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import java.nio.charset.StandardCharsets;
import org.teavm.interop.Address;
import org.teavm.interop.Export;

/**
 * TeaVM entry for the WebAssembly GC build run by a host with no JavaScript (Python under wasmtime). The boundary is numbers and
 * the module's linear memory only: the host writes UTF-8 input at an address from {@code teavm.malloc}, calls {@code call(op, ptr,
 * len)}, reads {@code resultLength()} and copies the UTF-8 result out with {@code copyResult(ptr)}. No JavaScript interop.
 */
public final class WasmMain {
    private WasmMain() {
    }

    private static byte[] result = new byte[0];
    private static int number;

    public static void main(String[] args) {
    }

    private static String read(int ptr, int len) {
        byte[] b = new byte[len];
        Address a = Address.fromInt(ptr);
        for (int i = 0; i < len; i++) {
            b[i] = a.add(i).getByte();
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    /** op 1 runAll, 2 compileSutra, 3 load, 4 evalLoop (input "reps\nexpr"), 5 evalOne ("row\nexpr"). Returns the result's byte length. */
    @Export(name = "call")
    public static int call(int op, int ptr, int len) {
        String in = read(ptr, len);
        String out;
        switch (op) {
            case 1: out = Harness.runAll(in); break;
            case 2: out = Integer.toString(Bench.compileSutra(in)); break;
            case 3: out = Integer.toString(Bench.load(in)); break;
            case 4: {
                int nl = in.indexOf('\n');
                out = Integer.toString(Bench.evalLoop(in.substring(nl + 1), Integer.parseInt(in.substring(0, nl))));
                break;
            }
            case 5: {
                int nl = in.indexOf('\n');
                out = Bench.evalOne(in.substring(nl + 1), Integer.parseInt(in.substring(0, nl)));
                break;
            }
            default: out = "";
        }
        result = out.getBytes(StandardCharsets.UTF_8);
        return result.length;
    }

    @Export(name = "copyResult")
    public static void copyResult(int ptr) {
        Address a = Address.fromInt(ptr);
        for (int i = 0; i < result.length; i++) {
            a.add(i).putByte(result[i]);
        }
    }
}
