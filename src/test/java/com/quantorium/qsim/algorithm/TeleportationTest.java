package com.quantorium.qsim.algorithm;

import com.quantorium.qsim.circuit.QuantumCircuit;
import com.quantorium.qsim.core.StateVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportationTest {

    private static final double EPSILON = 1e-9;
    private static final TeleportationRegisters REGS = TeleportationRegisters.DEFAULT;

    private final Teleportation teleportation = new Teleportation();

    /** Source qubit prepared as Rz(phi) Ry(theta)|0>, a generic single-qubit state. */
    private static StateVector stateToTeleport(double theta, double phi) {
        return new QuantumCircuit(REGS.totalQubits())
                .ry(REGS.source(), theta)
                .rz(REGS.source(), phi)
                .state();
    }

    /** Undoes the preparation on Bob's qubit; if it holds the teleported state it returns to |0>. */
    private static void undoPreparationOnBob(StateVector state, double theta, double phi) {
        new QuantumCircuit(state)
                .rz(REGS.bob(), -phi)
                .ry(REGS.bob(), -theta);
    }

    @ParameterizedTest
    @CsvSource({"0.0, 0.0", "3.141592653589793, 0.0", "1.5707963267948966, 0.0",
            "1.5707963267948966, 1.5707963267948966", "0.7, 2.1", "2.4, -1.3"})
    void bobReceivesTheTeleportedStateForEveryMeasurementOutcome(double theta, double phi) {
        for (long seed = 0; seed < 50; seed++) {
            StateVector state = stateToTeleport(theta, phi);

            teleportation.teleport(state, REGS, new Random(seed));

            undoPreparationOnBob(state, theta, phi);
            assertEquals(1.0, state.marginalProbability(REGS.bob(), 0), EPSILON, "seed " + seed);
            assertEquals(1.0, state.totalProbability(), EPSILON);
        }
    }

    @Test
    void allFourClassicalOutcomesOccurAndEachIsCorrected() {
        double theta = 0.9;
        double phi = 0.4;
        Set<Teleportation.Outcome> seen = new HashSet<>();
        Random random = new Random(2024);

        for (int shot = 0; shot < 200; shot++) {
            StateVector state = stateToTeleport(theta, phi);
            seen.add(teleportation.teleport(state, REGS, random));

            undoPreparationOnBob(state, theta, phi);
            assertEquals(1.0, state.marginalProbability(REGS.bob(), 0), EPSILON);
        }

        assertEquals(4, seen.size());
    }

    @Test
    void outcomesAreRoughlyUniform() {
        Random random = new Random(42);
        int shots = 4000;
        int[] counts = new int[4];

        for (int i = 0; i < shots; i++) {
            StateVector state = stateToTeleport(1.1, 0.6);
            Teleportation.Outcome outcome = teleportation.teleport(state, REGS, random);
            counts[outcome.sourceBit() * 2 + outcome.aliceBit()]++;
        }

        for (int count : counts) {
            assertEquals(0.25, (double) count / shots, 0.03);
        }
    }

    @Test
    void sourceAndAliceQubitsEndInBasisStatesSoTheStateIsMovedNotCopied() {
        StateVector state = stateToTeleport(1.1, 0.6);

        Teleportation.Outcome outcome = teleportation.teleport(state, REGS, new Random(7));

        assertEquals(1.0, state.marginalProbability(REGS.source(), outcome.sourceBit()), EPSILON);
        assertEquals(1.0, state.marginalProbability(REGS.alice(), outcome.aliceBit()), EPSILON);
    }

    @Test
    void worksWithPermutedAndNonAdjacentQubits() {
        TeleportationRegisters regs = new TeleportationRegisters(3, 0, 2);
        double theta = 2.0;
        double phi = -0.8;

        for (long seed = 0; seed < 30; seed++) {
            StateVector state = new QuantumCircuit(regs.totalQubits())
                    .ry(regs.source(), theta)
                    .rz(regs.source(), phi)
                    .state();

            teleportation.teleport(state, regs, new Random(seed));

            new QuantumCircuit(state).rz(regs.bob(), -phi).ry(regs.bob(), -theta);
            assertEquals(1.0, state.marginalProbability(regs.bob(), 0), EPSILON);
        }
    }

    @Test
    void registersMustBeDistinct() {
        assertThrows(IllegalArgumentException.class, () -> new TeleportationRegisters(0, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new TeleportationRegisters(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TeleportationRegisters(2, 1, 2));
    }

    @Test
    void measureCollapsesAndRenormalizes() {
        for (long seed = 0; seed < 20; seed++) {
            StateVector state = new QuantumCircuit(2).h(0).cnot(0, 1).state();

            int first = state.measure(0, new Random(seed));

            assertEquals(1.0, state.totalProbability(), EPSILON);
            assertEquals(1.0, state.probability(first == 1 ? 0b11 : 0b00), EPSILON);
            assertEquals(first, state.measure(1, new Random(seed + 1000)));
        }
    }

    @Test
    void measureFollowsBornRule() {
        Random random = new Random(1);
        int shots = 4000;
        int ones = 0;

        for (int i = 0; i < shots; i++) {
            StateVector state = new QuantumCircuit(1).ry(0, 2 * Math.asin(Math.sqrt(0.3))).state();
            ones += state.measure(0, random);
        }

        assertTrue(Math.abs((double) ones / shots - 0.3) < 0.03);
    }

    @Test
    void marginalProbabilitySumsOutOtherQubits() {
        StateVector state = new QuantumCircuit(2).h(0).cnot(0, 1).state();

        assertEquals(0.5, state.marginalProbability(0, 1), EPSILON);
        assertEquals(0.5, state.marginalProbability(1, 0), EPSILON);
        assertThrows(IllegalArgumentException.class, () -> state.marginalProbability(0, 2));
        assertThrows(IllegalArgumentException.class, () -> state.marginalProbability(2, 0));
    }
}
