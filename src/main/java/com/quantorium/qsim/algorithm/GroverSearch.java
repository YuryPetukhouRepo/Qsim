package com.quantorium.qsim.algorithm;

import com.quantorium.qsim.core.StateVector;
import com.quantorium.qsim.gates.Gates;

public final class GroverSearch {

    public StateVector run(GroverRegisters regs, Oracle oracle, int iterations) {
        StateVector state = new StateVector(regs.totalQubits());

        // ancilla -> |->
        state.applySingleQubitGate(Gates.X, regs.ancilla());
        state.applySingleQubitGate(Gates.H, regs.ancilla());

        applyHadamardToInputs(state, regs);

        for (int i = 0; i < iterations; i++) {
            oracle.apply(state, regs);
            diffuse(state, regs);
        }
        return state;
    }

    /** Optimal iteration count floor(pi/4 * sqrt(N/M)) for M marked states out of N. */
    public static int optimalIterations(int searchSpace, int markedCount) {
        if (markedCount < 1 || markedCount > searchSpace) {
            throw new IllegalArgumentException("markedCount must be in [1, searchSpace]");
        }
        return (int) Math.floor(Math.PI / 4 * Math.sqrt((double) searchSpace / markedCount));
    }

    /**
     * Inversion about the mean on the input qubits only: H^n, phase flip of every
     * state except |0...0>, H^n. Equals the diffuser up to a global phase of -1.
     */
    private void diffuse(StateVector state, GroverRegisters regs) {
        applyHadamardToInputs(state, regs);
        state.flipPhaseWhere(basisState -> (basisState & regs.inputMask()) != 0);
        applyHadamardToInputs(state, regs);
    }

    private void applyHadamardToInputs(StateVector state, GroverRegisters regs) {
        for (int qubit = 0; qubit < regs.inputQubits(); qubit++) {
            state.applySingleQubitGate(Gates.H, qubit);
        }
    }
}
