package com.quantorium.qsim.core;

import com.quantorium.qsim.gates.Gates;
import org.apache.commons.math3.complex.Complex;

import java.util.Random;
import java.util.function.IntPredicate;

/**
 * Dense statevector representation of an n-qubit quantum register.
 *
 * <p>Convention: qubit 0 is the least-significant bit of the basis-state index.
 * E.g. for 2 qubits, amplitudes[0b10] is the amplitude of |q1=1, q0=0>.
 * This is the little-endian convention used by Qiskit. Textbooks (e.g. Nielsen and Chuang)
 * usually write kets with qubit 0 leftmost, so |q0 q1> there corresponds to the reversed bit
 * string here. Printed bit strings (see {@link #toString()}) list the highest qubit first.
 */
public final class StateVector {

    private final int numQubits;
    private final Complex[] amplitudes;

    public StateVector(int numQubits) {
        if (numQubits < 1) {
            throw new IllegalArgumentException("numQubits must be >= 1");
        }
        this.numQubits = numQubits;
        int dim = 1 << numQubits;
        this.amplitudes = new Complex[dim];
        this.amplitudes[0] = Complex.ONE;
        for (int i = 1; i < dim; i++) {
            this.amplitudes[i] = Complex.ZERO;
        }
    }

    public StateVector(int numQubits, Complex[] amplitudes) {
        this.numQubits = numQubits;
        this.amplitudes = amplitudes;
    }

    public int numQubits() {
        return numQubits;
    }

    public int dimension() {
        return amplitudes.length;
    }

    public Complex amplitude(int basisState) {
        return amplitudes[basisState];
    }

    /**
     * Applies a single-qubit gate (2x2 unitary) to the given target qubit.
     * Mutates this StateVector in place and returns this, for fluent chaining.
     */
    public StateVector applySingleQubitGate(Complex[][] matrix, int target) {
        validateTarget(target);
        int targetBit = 1 << target;

        for (int state = 0; state < amplitudes.length; state++) {
            boolean targetIsZero = (state & targetBit) == 0;
            if (targetIsZero) {
                int zeroIndex = state;
                int oneIndex = state | targetBit;
                applyMatrixToPair(matrix, zeroIndex, oneIndex);
            }
        }
        return this;
    }

    /**
     * Applies a controlled single-qubit gate: the 2x2 matrix is applied to the
     * target qubit's amplitude pair only when the control qubit is |1>.
     */
    public StateVector applyControlledGate(Complex[][] matrix, int control, int target) {
        validateTarget(control);
        return applyMultiControlledGate(matrix, 1 << control, target);
    }

    /**
     * Applies a multi-controlled single-qubit gate: the 2x2 matrix is applied to the
     * target qubit's amplitude pair only when every qubit set in controlMask is |1>.
     */
    public StateVector applyMultiControlledGate(Complex[][] matrix, int controlMask, int target) {
        validateTarget(target);
        validateControlMask(controlMask);
        int targetBit = 1 << target;
        if ((controlMask & targetBit) != 0) {
            throw new IllegalArgumentException("control and target must differ");
        }

        for (int state = 0; state < amplitudes.length; state++) {
            boolean controlsAreOne = (state & controlMask) == controlMask;
            boolean targetIsZero = (state & targetBit) == 0;
            if (controlsAreOne && targetIsZero) {
                int zeroIndex = state;
                int oneIndex = state | targetBit;
                applyMatrixToPair(matrix, zeroIndex, oneIndex);
            }
        }
        return this;
    }

    /**
     * Negates the amplitude of every basis state accepted by the predicate (a diagonal
     * phase flip, |x> -> -|x>). Probabilities are unchanged.
     */
    public StateVector flipPhaseWhere(IntPredicate basisState) {
        for (int state = 0; state < amplitudes.length; state++) {
            if (basisState.test(state)) {
                amplitudes[state] = amplitudes[state].negate();
            }
        }
        return this;
    }

    /**
     * Applies a 2x2 unitary to the amplitude pair (amplitudes[zeroIndex], amplitudes[oneIndex])
     * that differ only in the target qubit's value — the standard single-qubit gate update:
     *   [newAmp0]   [m00 m01] [amp0]
     *   [newAmp1] = [m10 m11] [amp1]
     */
    private void applyMatrixToPair(Complex[][] matrix, int zeroIndex, int oneIndex) {
        Complex amp0 = amplitudes[zeroIndex];
        Complex amp1 = amplitudes[oneIndex];

        amplitudes[zeroIndex] = matrix[0][0].multiply(amp0).add(matrix[0][1].multiply(amp1));
        amplitudes[oneIndex]  = matrix[1][0].multiply(amp0).add(matrix[1][1].multiply(amp1));
    }

    /** Convenience: CNOT is a controlled-X. */
    public StateVector applyCNOT(int control, int target) {
        return applyControlledGate(Gates.X, control, target);
    }

    /** Convenience: Toffoli (CCNOT) flips the target only when both controls are |1>. */
    public StateVector applyToffoli(int control1, int control2, int target) {
        validateTarget(control1);
        validateTarget(control2);
        if (control1 == control2) {
            throw new IllegalArgumentException("controls must differ");
        }
        int controlMask = (1 << control1) | (1 << control2);
        return applyMultiControlledGate(Gates.X, controlMask, target);
    }

    /** Probability of measuring the given basis state, per the Born rule. */
    public double probability(int basisState) {
        return amplitudes[basisState].abs() * amplitudes[basisState].abs();
    }

    /**
     * Samples a single measurement of all qubits using the Born rule.
     * Does NOT collapse the state — this is a read-only sampler for
     * statistics-gathering over many shots on a freshly prepared state.
     */
    public int sampleMeasurement(Random random) {
        double r = random.nextDouble();
        double cumulative = 0.0;
        for (int i = 0; i < amplitudes.length; i++) {
            cumulative += probability(i);
            if (r < cumulative) {
                return i;
            }
        }
        return amplitudes.length - 1; // floating-point fallback
    }

    /** Probability that measuring the given qubit yields {@code value} (0 or 1); other qubits are summed out. */
    public double marginalProbability(int qubit, int value) {
        validateTarget(qubit);
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("value must be 0 or 1: " + value);
        }
        int qubitBit = 1 << qubit;
        double sum = 0.0;
        for (int state = 0; state < amplitudes.length; state++) {
            boolean bitMatches = ((state & qubitBit) != 0) == (value == 1);
            if (bitMatches) {
                sum += probability(state);
            }
        }
        return sum;
    }

    /**
     * Measures a single qubit in the computational basis using the Born rule and collapses
     * the state: amplitudes inconsistent with the outcome are zeroed and the rest renormalized.
     * Mutates this StateVector in place and returns the measured bit.
     */
    public int measure(int qubit, Random random) {
        int outcome = random.nextDouble() < marginalProbability(qubit, 1) ? 1 : 0;
        collapse(qubit, outcome);
        return outcome;
    }

    private void collapse(int qubit, int outcome) {
        int qubitBit = 1 << qubit;
        double norm = Math.sqrt(marginalProbability(qubit, outcome));

        for (int state = 0; state < amplitudes.length; state++) {
            boolean bitMatches = ((state & qubitBit) != 0) == (outcome == 1);
            amplitudes[state] = bitMatches ? amplitudes[state].divide(norm) : Complex.ZERO;
        }
    }

    /** Checks that total probability sums to ~1, useful in tests/debugging. */
    public double totalProbability() {
        double sum = 0.0;
        for (Complex a : amplitudes) {
            sum += a.abs() * a.abs();
        }
        return sum;
    }

    public StateVector copy() {
        Complex[] copyArr = new Complex[amplitudes.length];
        System.arraycopy(amplitudes, 0, copyArr, 0, amplitudes.length);
        return new StateVector(numQubits, copyArr);
    }

    private void validateTarget(int index) {
        if (index < 0 || index >= numQubits) {
            throw new IllegalArgumentException("qubit index out of range: " + index);
        }
    }

    private void validateControlMask(int controlMask) {
        if (controlMask <= 0 || controlMask >= amplitudes.length) {
            throw new IllegalArgumentException("invalid control mask: " + controlMask);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int dim = amplitudes.length;
        for (int i = 0; i < dim; i++) {
            Complex a = amplitudes[i];
            if (a.abs() > 1e-9) {
                if (!sb.isEmpty()) {
                    sb.append(" + ");
                }
                sb.append(String.format("(%.3f%+.3fi)|%s>",
                        a.getReal(), a.getImaginary(),
                        Integer.toBinaryString(i | (1 << numQubits)).substring(1)));
            }
        }
        return sb.toString();
    }
}
