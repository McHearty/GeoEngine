package com.omms.geoenginecore.test.fixtures;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Specialized assertions for GeoEngine scalar field quantities (TECHSPEC §8).
 *
 * <p>Provides tolerance-based comparisons, finiteness checks, and
 * grid-level invariants that are used throughout the test suite.
 */
public final class FieldAssert {

    private FieldAssert() {
        // utility
    }

    /**
     * Asserts all values are finite (not NaN or ±∞).
     */
    public static void assertFinite(String context, double... values) {
        for (int i = 0; i < values.length; i++) {
            assertFalse(Double.isNaN(values[i]), context + " index " + i + " is NaN");
            assertFalse(Double.isInfinite(values[i]), context + " index " + i + " is infinite");
        }
    }

    /**
     * Asserts all values in the grid are finite.
     */
    public static void assertFiniteGrid(String context, double[] grid) {
        for (int i = 0; i < grid.length; i++) {
            assertFalse(Double.isNaN(grid[i]), context + " grid index " + i + " is NaN");
            assertFalse(Double.isInfinite(grid[i]), context + " grid index " + i + " is infinite");
        }
    }

    /**
     * Asserts a value is within the closed range [lo, hi] (inclusive).
     */
    public static void assertInRange(String context, double value, double lo, double hi) {
        assertTrue(value >= lo && value <= hi,
                context + " = " + value + " not in [" + lo + ", " + hi + "]");
    }

    /**
     * Asserts a value is within the closed range [lo, hi] with tolerance.
     */
    public static void assertInRange(String context, double value, double lo, double hi, double tol) {
        assertTrue(value >= lo - tol && value <= hi + tol,
                context + " = " + value + " not in [" + (lo - tol) + ", " + (hi + tol) + "]");
    }

    /**
     * Asserts two values are equal within absolute tolerance.
     */
    public static void assertClose(String context, double actual, double expected, double tol) {
        assertEquals(expected, actual, tol, context);
    }

    /**
     * Asserts two values are equal within relative tolerance (for large magnitudes).
     */
    public static void assertCloseRelative(String context, double actual, double expected, double relTol) {
        double diff = Math.abs(actual - expected);
        double scale = Math.max(Math.abs(expected), 1.0);
        assertTrue(diff <= scale * relTol, context + ": |" + actual + " - " + expected + "| = " + diff
                + " exceeds " + scale * relTol);
    }

    /**
     * Asserts a double array is bit-identical to another.
     */
    public static void assertBitEqual(String context, double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length, context + " length mismatch");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(Double.doubleToLongBits(expected[i]), Double.doubleToLongBits(actual[i]),
                    context + " bit mismatch at index " + i);
        }
    }

    /**
     * Asserts a long array is bit-identical to another.
     */
    public static void assertBitEqual(String context, long[] expected, long[] actual) {
        assertEquals(expected.length, actual.length, context + " length mismatch");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], context + " bit mismatch at index " + i);
        }
    }

    /**
     * Asserts no value in the grid exceeds the given maximum.
     */
    public static void assertGridMax(String context, double[] grid, double max) {
        for (int i = 0; i < grid.length; i++) {
            assertTrue(grid[i] <= max, context + " grid index " + i + " = " + grid[i] + " exceeds max " + max);
        }
    }

    /**
     * Asserts no value in the grid is below the given minimum.
     */
    public static void assertGridMin(String context, double[] grid, double min) {
        for (int i = 0; i < grid.length; i++) {
            assertTrue(grid[i] >= min, context + " grid index " + i + " = " + grid[i] + " below min " + min);
        }
    }
}
