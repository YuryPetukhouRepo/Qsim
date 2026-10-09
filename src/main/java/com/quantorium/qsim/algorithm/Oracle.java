package com.quantorium.qsim.algorithm;

import com.quantorium.qsim.core.StateVector;

@FunctionalInterface
public interface Oracle {
    void apply(StateVector state, GroverRegisters registers);
}
