package com.quantorium.qsim.algorithm;

/**
 * Qubit roles in quantum teleportation: the {@code source} holds the state to send,
 * {@code alice} and {@code bob} hold the two halves of the shared Bell pair.
 */
public record TeleportationRegisters(int source, int alice, int bob) {

    public static final TeleportationRegisters DEFAULT = new TeleportationRegisters(0, 1, 2);

    public TeleportationRegisters {
        if (source == alice || source == bob || alice == bob) {
            throw new IllegalArgumentException("source, alice and bob must be distinct qubits");
        }
    }

    public int totalQubits() {
        return Math.max(source, Math.max(alice, bob)) + 1;
    }
}
