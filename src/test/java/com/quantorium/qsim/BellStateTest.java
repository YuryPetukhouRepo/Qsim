package com.quantorium.qsim;

import com.quantorium.qsim.circuit.QuantumCircuit;
import com.quantorium.qsim.core.StateVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BellStateTest {

    private static final double EPSILON = 1e-9;

    @Test
    void hadamardOnSingleQubitGivesEqualSuperposition() {
        StateVector state = new QuantumCircuit(1).h(0).state();

        assertEquals(0.5, state.probability(0), EPSILON);
        assertEquals(0.5, state.probability(1), EPSILON);
    }

    @Test
    void hCnotProducesBellState() {
        // |Phi+> = (|00> + |11>) / sqrt(2)
        StateVector state = new QuantumCircuit(2)
                .h(0)
                .cnot(0, 1)
                .state();

        assertEquals(0.5, state.probability(0b00), EPSILON);
        assertEquals(0.5, state.probability(0b11), EPSILON);
        assertEquals(0.0, state.probability(0b01), EPSILON);
        assertEquals(0.0, state.probability(0b10), EPSILON);

        assertEquals(1.0, state.totalProbability(), EPSILON);
    }

    @Test
    void xGateFlipsBasisState() {
        StateVector state = new QuantumCircuit(1).x(0).state();

        assertEquals(1.0, state.probability(1), EPSILON);
        assertEquals(0.0, state.probability(0), EPSILON);
    }

    @Test
    void doubleHadamardIsIdentity() {
        // H*H = I, so |0> -> H -> H -> |0> again
        StateVector state = new QuantumCircuit(1).h(0).h(0).state();

        assertEquals(1.0, state.probability(0), EPSILON);
        assertEquals(0.0, state.probability(1), EPSILON);
    }

    @Test
    void ghzStateAcrossThreeQubits() {
        // (|000> + |111>) / sqrt(2)
        StateVector state = new QuantumCircuit(3)
                .h(0)
                .cnot(0, 1)
                .cnot(1, 2)
                .state();

        assertEquals(0.5, state.probability(0b000), EPSILON);
        assertEquals(0.5, state.probability(0b111), EPSILON);
        assertEquals(1.0, state.totalProbability(), EPSILON);
    }
}
