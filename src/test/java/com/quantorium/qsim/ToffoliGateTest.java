package com.quantorium.qsim;

import com.quantorium.qsim.circuit.QuantumCircuit;
import com.quantorium.qsim.core.StateVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToffoliGateTest {

    private static final double EPSILON = 1e-9;

    /** Prepares the computational basis state |input> by applying X to every set bit. */
    private static QuantumCircuit basisState(int numQubits, int input) {
        QuantumCircuit circuit = new QuantumCircuit(numQubits);
        for (int qubit = 0; qubit < numQubits; qubit++) {
            if ((input & (1 << qubit)) != 0) {
                circuit.x(qubit);
            }
        }
        return circuit;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void truthTableFlipsTargetOnlyWhenBothControlsAreOne(int input) {
        // controls are qubits 0 and 1, target is qubit 2
        boolean bothControlsSet = (input & 0b011) == 0b011;
        int expected = bothControlsSet ? input ^ 0b100 : input;

        StateVector state = basisState(3, input).toffoli(0, 1, 2).state();

        assertEquals(1.0, state.probability(expected), EPSILON);
    }

    @Test
    void doesNotChangeControls() {
        StateVector state = basisState(3, 0b011).toffoli(0, 1, 2).state();

        assertEquals(1.0, state.probability(0b111), EPSILON);
    }

    @Test
    void isItsOwnInverse() {
        StateVector state = new QuantumCircuit(3)
                .h(0).h(1).ry(2, 0.7)
                .state();
        StateVector original = state.copy();

        state.applyToffoli(0, 1, 2).applyToffoli(0, 1, 2);

        for (int basis = 0; basis < state.dimension(); basis++) {
            assertEquals(original.amplitude(basis).getReal(), state.amplitude(basis).getReal(), EPSILON);
            assertEquals(original.amplitude(basis).getImaginary(), state.amplitude(basis).getImaginary(), EPSILON);
        }
    }

    @Test
    void computesAndIntoTargetWhenControlsAreInSuperposition() {
        // (|00> + |01> + |10> + |11>)/2 on the controls, target |0>:
        // target becomes c1 AND c2, so only |c1=1,c2=1,t=1> has the target set.
        StateVector state = new QuantumCircuit(3)
                .h(0).h(1)
                .toffoli(0, 1, 2)
                .state();

        assertEquals(0.25, state.probability(0b000), EPSILON);
        assertEquals(0.25, state.probability(0b001), EPSILON);
        assertEquals(0.25, state.probability(0b010), EPSILON);
        assertEquals(0.25, state.probability(0b111), EPSILON);
        assertEquals(0.0, state.probability(0b011), EPSILON);
        assertEquals(1.0, state.totalProbability(), EPSILON);
    }

    @Test
    void targetInSuperpositionIsUnchangedWhenControlsAreSet() {
        // X on target is a no-op on |+>, so Toffoli leaves |11>|+> untouched.
        StateVector state = new QuantumCircuit(3)
                .x(0).x(1).h(2)
                .toffoli(0, 1, 2)
                .state();

        assertEquals(0.5, state.probability(0b011), EPSILON);
        assertEquals(0.5, state.probability(0b111), EPSILON);
    }

    @Test
    void worksWithNonAdjacentAndPermutedQubits() {
        // controls are qubits 3 and 0, target is qubit 1, qubit 2 is a bystander
        StateVector state = basisState(4, 0b1001).toffoli(3, 0, 1).state();

        assertEquals(1.0, state.probability(0b1011), EPSILON);
    }

    @Test
    void bystanderQubitDoesNotAffectOutcome() {
        StateVector state = basisState(4, 0b0111).toffoli(0, 1, 2).state();

        assertEquals(1.0, state.probability(0b0011), EPSILON);
    }

    @Test
    void rejectsEqualControls() {
        StateVector state = new StateVector(3);

        assertThrows(IllegalArgumentException.class, () -> state.applyToffoli(1, 1, 2));
    }

    @Test
    void rejectsTargetEqualToControl() {
        StateVector state = new StateVector(3);

        assertThrows(IllegalArgumentException.class, () -> state.applyToffoli(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> state.applyToffoli(0, 1, 0));
    }

    @Test
    void rejectsQubitIndexOutOfRange() {
        StateVector state = new StateVector(3);

        assertThrows(IllegalArgumentException.class, () -> state.applyToffoli(0, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> state.applyToffoli(-1, 1, 2));
    }
}
