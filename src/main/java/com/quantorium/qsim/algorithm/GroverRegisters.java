package com.quantorium.qsim.algorithm;

public record GroverRegisters(int inputQubits) {
    public int ancilla()       { return inputQubits; }          // top qubit
    public int totalQubits()   { return inputQubits + 1; }
    public int inputMask()     { return (1 << inputQubits) - 1; }
    public int searchSpace()   { return 1 << inputQubits; }     // N
}
