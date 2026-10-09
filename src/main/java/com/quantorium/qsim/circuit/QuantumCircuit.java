package com.quantorium.qsim.circuit;

import com.quantorium.qsim.core.StateVector;
import com.quantorium.qsim.gates.Gates;

/**
 * Fluent builder over a StateVector, so circuits read like:
 *   StateVector result = new QuantumCircuit(2)
 *       .h(0)
 *       .cnot(0, 1)
 *       .state();
 */
public final class QuantumCircuit {

    private final StateVector state;

    public QuantumCircuit(int numQubits) {
        this.state = new StateVector(numQubits);
    }

    public QuantumCircuit(StateVector state) {
        this.state = state;
    }

    public QuantumCircuit h(int qubit) {
        state.applySingleQubitGate(Gates.H, qubit);
        return this;
    }

    public QuantumCircuit x(int qubit) {
        state.applySingleQubitGate(Gates.X, qubit);
        return this;
    }

    public QuantumCircuit y(int qubit) {
        state.applySingleQubitGate(Gates.Y, qubit);
        return this;
    }

    public QuantumCircuit z(int qubit) {
        state.applySingleQubitGate(Gates.Z, qubit);
        return this;
    }

    public QuantumCircuit s(int qubit) {
        state.applySingleQubitGate(Gates.S, qubit);
        return this;
    }

    public QuantumCircuit t(int qubit) {
        state.applySingleQubitGate(Gates.T, qubit);
        return this;
    }

    public QuantumCircuit rx(int qubit, double theta) {
        state.applySingleQubitGate(Gates.rx(theta), qubit);
        return this;
    }

    public QuantumCircuit ry(int qubit, double theta) {
        state.applySingleQubitGate(Gates.ry(theta), qubit);
        return this;
    }

    public QuantumCircuit rz(int qubit, double theta) {
        state.applySingleQubitGate(Gates.rz(theta), qubit);
        return this;
    }

    public QuantumCircuit cnot(int control, int target) {
        state.applyCNOT(control, target);
        return this;
    }

    public QuantumCircuit toffoli(int control1, int control2, int target) {
        state.applyToffoli(control1, control2, target);
        return this;
    }

    public StateVector state() {
        return state;
    }
}
