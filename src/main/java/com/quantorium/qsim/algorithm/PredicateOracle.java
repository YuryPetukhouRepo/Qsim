package com.quantorium.qsim.algorithm;

import com.quantorium.qsim.core.StateVector;

import java.util.function.IntPredicate;

/**
 * Simulator shortcut oracle: negates the amplitude of every state whose input bits
 * satisfy the predicate. The ancilla is ignored.
 */
public record PredicateOracle(IntPredicate isMarked) implements Oracle {

    @Override
    public void apply(StateVector state, GroverRegisters registers) {
        state.flipPhaseWhere(basisState -> isMarked.test(basisState & registers.inputMask()));
    }
}
