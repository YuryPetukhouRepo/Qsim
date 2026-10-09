package com.quantorium.qsim.algorithm;

import com.quantorium.qsim.circuit.QuantumCircuit;
import com.quantorium.qsim.core.StateVector;

import java.util.Random;

/**
 * Quantum teleportation: moves the state of the {@code source} qubit onto Bob's qubit using a
 * shared Bell pair and two classical bits. The source and Alice's qubits end up collapsed to
 * a basis state, so the state is moved, not cloned.
 */
public final class Teleportation {

    /** The two classical bits Alice sends to Bob. */
    public record Outcome(int sourceBit, int aliceBit) {
    }

    /**
     * Runs the protocol in place on {@code state}. Alice's qubit and Bob's qubit are expected
     * in |0>; the source qubit holds the state to teleport.
     */
    public Outcome teleport(StateVector state, TeleportationRegisters regs, Random random) {
        QuantumCircuit circuit = new QuantumCircuit(state);

        shareBellPair(circuit, regs);
        Outcome outcome = measureBell(state, circuit, regs, random);
        applyCorrection(circuit, regs, outcome);
        return outcome;
    }

    private void shareBellPair(QuantumCircuit circuit, TeleportationRegisters regs) {
        circuit.h(regs.alice()).cnot(regs.alice(), regs.bob());
    }

    /** Alice's Bell measurement: rotate the Bell basis to the computational basis, then measure. */
    private Outcome measureBell(StateVector state, QuantumCircuit circuit, TeleportationRegisters regs, Random random) {
        circuit.cnot(regs.source(), regs.alice()).h(regs.source());
        int sourceBit = state.measure(regs.source(), random);
        int aliceBit = state.measure(regs.alice(), random);
        return new Outcome(sourceBit, aliceBit);
    }

    /** Bob undoes the Pauli error: X if Alice's qubit read 1, then Z if the source qubit read 1. */
    private void applyCorrection(QuantumCircuit circuit, TeleportationRegisters regs, Outcome outcome) {
        if (outcome.aliceBit() == 1) {
            circuit.x(regs.bob());
        }
        if (outcome.sourceBit() == 1) {
            circuit.z(regs.bob());
        }
    }
}
