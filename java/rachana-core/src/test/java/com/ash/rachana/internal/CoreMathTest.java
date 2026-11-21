/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.SplittableRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

/** CoreMath equals StrictMath (fdlibm) bit for bit, on edge values and on random inputs over every exponent range. */
class CoreMathTest {

    private static final double[] EDGES = {0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 2.0, 3.0, 10.0, 1000.0, 1e-300, -1e-300, 4.9e-324,
        -4.9e-324, 2.2250738585072014E-308, Double.MAX_VALUE, -Double.MAX_VALUE, Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY, Double.NaN, 709.782712893384, 709.79, -745.1332191019411, -745.14, -708.4, 0.6931471805599453,
        Math.PI, Math.PI / 2, Math.PI / 4, -Math.PI, 1e22, 1e300, 22.0, 56 * 0.6931471805599453, 0.41422, -0.2929, 1e-17,
        0.9087887256040952, 1.0000000000000002, 0.9999999999999999, 2.0e9, 1.0e15, 7.0e15, 9.007199254740993E15};

    private static final SplittableRandom RANDOM = new SplittableRandom(20261121L);

    private static double randomDouble() {
        switch (RANDOM.nextInt(4)) {
            case 0:
                return Double.longBitsToDouble(RANDOM.nextLong());
            case 1:
                return (RANDOM.nextDouble() - 0.5) * 2 * Math.pow(10, RANDOM.nextInt(-12, 6));
            case 2:
                return (RANDOM.nextDouble() - 0.5) * 1500;
            default:
                return RANDOM.nextInt(-2000, 2000) / 8.0;
        }
    }

    private static void same(String name, DoubleUnaryOperator mine, DoubleUnaryOperator strict) {
        for (double x : EDGES) {
            check(name, x, Double.NaN, mine.applyAsDouble(x), strict.applyAsDouble(x));
        }
        for (int i = 0; i < 400_000; i++) {
            double x = randomDouble();
            check(name, x, Double.NaN, mine.applyAsDouble(x), strict.applyAsDouble(x));
        }
    }

    private static void check(String name, double x, double y, double mine, double strict) {
        if (Double.doubleToRawLongBits(mine) != Double.doubleToRawLongBits(strict) && !(Double.isNaN(mine) && Double.isNaN(strict))) {
            fail(name + "(" + x + (Double.isNaN(y) ? "" : ", " + y) + "): CoreMath " + mine + ", StrictMath " + strict);
        }
    }

    @Test
    void unaryFunctionsMatchStrictMath() {
        same("exp", CoreMath::exp, StrictMath::exp);
        same("log", CoreMath::log, StrictMath::log);
        same("log10", CoreMath::log10, StrictMath::log10);
        same("log1p", CoreMath::log1p, StrictMath::log1p);
        same("expm1", CoreMath::expm1, StrictMath::expm1);
        same("tanh", CoreMath::tanh, StrictMath::tanh);
        same("sin", CoreMath::sin, StrictMath::sin);
        same("cos", CoreMath::cos, StrictMath::cos);
        same("tan", CoreMath::tan, StrictMath::tan);
        same("asin", CoreMath::asin, StrictMath::asin);
        same("acos", CoreMath::acos, StrictMath::acos);
        same("sinh", CoreMath::sinh, StrictMath::sinh);
        same("cosh", CoreMath::cosh, StrictMath::cosh);
        same("toRadians", CoreMath::toRadians, StrictMath::toRadians);
        same("toDegrees", CoreMath::toDegrees, StrictMath::toDegrees);
        same("atan", CoreMath::atan, StrictMath::atan);
        same("rint", CoreMath::rint, StrictMath::rint);
    }

    private static void sameBinary(String name, DoubleBinaryOperator mine, DoubleBinaryOperator strict) {
        for (double x : EDGES) {
            for (double y : EDGES) {
                check(name, x, y, mine.applyAsDouble(x, y), strict.applyAsDouble(x, y));
            }
        }
        for (int i = 0; i < 400_000; i++) {
            double x = randomDouble();
            double y = RANDOM.nextBoolean() ? randomDouble() : RANDOM.nextInt(-40, 40) / (RANDOM.nextBoolean() ? 1.0 : 4.0);
            check(name, x, y, mine.applyAsDouble(x, y), strict.applyAsDouble(x, y));
        }
    }

    @Test
    void binaryFunctionsMatchStrictMath() {
        sameBinary("pow", CoreMath::pow, StrictMath::pow);
        sameBinary("atan2", CoreMath::atan2, StrictMath::atan2);
        sameBinary("hypot", CoreMath::hypot, StrictMath::hypot);
    }

    @Test
    void castsAreJavasSaturatingCasts() {
        for (double x : EDGES) {
            assertEquals((long) x, CoreMath.toLong(x), "toLong " + x);
            assertEquals((int) x, CoreMath.toInt(x), "toInt " + x);
        }
        double[] more = {9.223372036854775807E18, -9.223372036854775808E18, 4503599627370496.0, -4503599627370497.0, -1.5, 1.5,
            -0.9, 4294967296.0, -4294967295.5, 2147483648.0, -2147483649.0};
        for (double x : more) {
            assertEquals((long) x, CoreMath.toLong(x), "toLong " + x);
            assertEquals((int) x, CoreMath.toInt(x), "toInt " + x);
        }
        for (int i = 0; i < 200_000; i++) {
            double x = randomDouble();
            assertEquals((long) x, CoreMath.toLong(x), "toLong " + x);
            assertEquals((int) x, CoreMath.toInt(x), "toInt " + x);
        }
    }
}
