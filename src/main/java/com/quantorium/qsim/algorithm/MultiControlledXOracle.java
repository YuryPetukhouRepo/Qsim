package com.quantorium.qsim.algorithm;

import com.quantorium.qsim.core.StateVector;
import com.quantorium.qsim.gates.Gates;

/**
 * Gate-level oracle for a single marked value. Flips the ancilla when the input
 * register equals the marked value; with the ancilla in |->, this becomes a phase
 * flip on the marked state (phase kickback).
 */
public record MultiControlledXOracle(int marked) implements Oracle {

    public MultiControlledXOracle {
        if (marked < 0) {
            throw new IllegalArgumentException("marked must be >= 0");
        }
    }

    @Override
    public void apply(StateVector state, GroverRegisters registers) {
        if (marked >= registers.searchSpace()) {
            throw new IllegalArgumentException("marked value out of range: " + marked);
        }
        flipZeroBitsOfMarked(state, registers);
        state.applyMultiControlledGate(Gates.X, registers.inputMask(), registers.ancilla());
        flipZeroBitsOfMarked(state, registers);
    }

    private void flipZeroBitsOfMarked(StateVector state, GroverRegisters registers) {
        for (int qubit = 0; qubit < registers.inputQubits(); qubit++) {
            boolean bitIsZero = (marked & (1 << qubit)) == 0;
            if (bitIsZero) {
                state.applySingleQubitGate(Gates.X, qubit);
            }
        }
    }
}
