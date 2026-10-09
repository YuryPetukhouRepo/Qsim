package com.quantorium.qsim.gates;

import org.apache.commons.math3.complex.Complex;

/**
 * Standard single-qubit gate matrices, as 2x2 Complex arrays:
 * {{m00, m01}, {m10, m11}}.
 *
 * Two-qubit gates like CNOT are handled directly in StateVector via
 * applyControlledGate rather than as explicit 4x4 matrices, since the
 * bit-indexed application is both simpler and faster than full matrix math.
 */
public final class Gates {

    private Gates() {
    }

    private static final double INV_SQRT2 = 1.0 / Math.sqrt(2.0);

    public static final Complex[][] I = {
            {Complex.ONE, Complex.ZERO},
            {Complex.ZERO, Complex.ONE}
    };

    public static final Complex[][] X = {
            {Complex.ZERO, Complex.ONE},
            {Complex.ONE, Complex.ZERO}
    };

    public static final Complex[][] Y = {
            {Complex.ZERO, new Complex(0, -1)},
            {new Complex(0, 1), Complex.ZERO}
    };

    public static final Complex[][] Z = {
            {Complex.ONE, Complex.ZERO},
            {Complex.ZERO, new Complex(-1, 0)}
    };

    public static final Complex[][] H = {
            {new Complex(INV_SQRT2, 0), new Complex(INV_SQRT2, 0)},
            {new Complex(INV_SQRT2, 0), new Complex(-INV_SQRT2, 0)}
    };

    public static final Complex[][] S = {
            {Complex.ONE, Complex.ZERO},
            {Complex.ZERO, new Complex(0, 1)}
    };

    public static final Complex[][] T = {
            {Complex.ONE, Complex.ZERO},
            {Complex.ZERO, fromPolar(1.0, Math.PI / 4)}
    };

    public static Complex[][] rx(double theta) {
        Complex cos = new Complex(Math.cos(theta / 2), 0);
        Complex isin = new Complex(0, -Math.sin(theta / 2));
        return new Complex[][]{
                {cos, isin},
                {isin, cos}
        };
    }

    public static Complex[][] ry(double theta) {
        Complex cos = new Complex(Math.cos(theta / 2), 0);
        Complex sin = new Complex(Math.sin(theta / 2), 0);
        return new Complex[][]{
                {cos, sin.multiply(-1)},
                {sin, cos}
        };
    }

    public static Complex[][] rz(double theta) {
        Complex neg = fromPolar(1.0, -theta / 2);
        Complex pos = fromPolar(1.0, theta / 2);
        return new Complex[][]{
                {neg, Complex.ZERO},
                {Complex.ZERO, pos}
        };
    }

    public static Complex fromPolar(double r, double theta) {
        return new Complex(r * Math.cos(theta), r * Math.sin(theta));
    }
}
