package com.quantorium.qsim.algorithm;

import com.quantorium.qsim.core.StateVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroverSearchTest {

    private static final double EPSILON = 1e-9;

    private final GroverSearch grover = new GroverSearch();

    @Test
    void twoQubitsOneIterationFindsMarkedStateWithCertainty() {
        GroverRegisters regs = new GroverRegisters(2);

        StateVector state = grover.run(regs, new MultiControlledXOracle(0b10), 1);

        assertEquals(1.0, inputProbability(state, regs, 0b10), EPSILON);
    }

    @ParameterizedTest
    @CsvSource({"3, 5, 1", "3, 5, 2", "4, 9, 1", "4, 9, 3", "5, 17, 4"})
    void successProbabilityFollowsSinSquaredLaw(int n, int marked, int iterations) {
        GroverRegisters regs = new GroverRegisters(n);
        double theta = Math.asin(Math.sqrt(1.0 / regs.searchSpace()));
        double expected = Math.pow(Math.sin((2 * iterations + 1) * theta), 2);

        StateVector state = grover.run(regs, new MultiControlledXOracle(marked), iterations);

        assertEquals(expected, inputProbability(state, regs, marked), EPSILON);
        assertEquals(1.0, state.totalProbability(), EPSILON);
    }

    @Test
    void predicateOracleMatchesGateLevelOracle() {
        GroverRegisters regs = new GroverRegisters(4);
        int marked = 6;

        StateVector viaGates = grover.run(regs, new MultiControlledXOracle(marked), 3);
        StateVector viaPredicate = grover.run(regs, new PredicateOracle(x -> x == marked), 3);

        assertEquals(inputProbability(viaGates, regs, marked),
                inputProbability(viaPredicate, regs, marked), EPSILON);
    }

    @Test
    void multipleMarkedStatesFollowSinSquaredLaw() {
        GroverRegisters regs = new GroverRegisters(4);
        int iterations = GroverSearch.optimalIterations(regs.searchSpace(), 2);
        double theta = Math.asin(Math.sqrt(2.0 / regs.searchSpace()));

        StateVector state = grover.run(regs, new PredicateOracle(x -> x == 3 || x == 12), iterations);

        double success = inputProbability(state, regs, 3) + inputProbability(state, regs, 12);
        assertEquals(Math.pow(Math.sin((2 * iterations + 1) * theta), 2), success, EPSILON);
    }

    @Test
    void optimalIterationsForSingleMarkedState() {
        assertEquals(1, GroverSearch.optimalIterations(4, 1));
        assertEquals(3, GroverSearch.optimalIterations(16, 1));
    }

    /** Probability that the input register holds the given value (ancilla marginalised out). */
    private static double inputProbability(StateVector state, GroverRegisters regs, int value) {
        double   sum = 0.0;
        for (int basis = 0; basis < state.dimension(); basis++) {
            if ((basis & regs.inputMask()) == value) {
                sum += state.probability(basis);
            }
        }
        return sum;
    }
}
