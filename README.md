# Qsim

A small, readable **state-vector quantum computer simulator in Java**, built to make the mechanics of quantum
algorithms visible in code. It implements the standard gate set, projective measurement with state collapse, and three
canonical protocols: **Grover's search**, **quantum teleportation**, and the **Toffoli gate** as the bridge between
classical reversible logic and quantum circuits.

The goal is not raw speed. Every operation is a short loop over the amplitudes that you can read next to the
equation it implements, and every algorithm is verified against its closed-form theory, not just "it runs".

- [Quick start](#quick-start)
- [Concepts and conventions](#concepts-and-conventions)
- [Gates](#gates)
- [Measurement](#measurement)
- [Algorithms](#algorithms)
  - [Toffoli gate](#toffoli-gate-ccnot)
  - [Grover's search](#grovers-search)
  - [Quantum teleportation](#quantum-teleportation)
- [Architecture](#architecture)
- [Testing strategy](#testing-strategy)
- [Limitations](#limitations)
- [Further reading](#further-reading)
- [License](#license)

## Quick start

Requirements: JDK 25 (the Gradle build requests a Java 25 toolchain). The only runtime dependency is
Apache Commons Math (for `Complex`).

```bash
git clone <repo-url>
cd Qsim
./gradlew test
```

### Build a Bell state

```java
StateVector bell = new QuantumCircuit(2)
        .h(0)          // |00> -> (|00> + |10>)/sqrt(2)
        .cnot(0, 1)    // -> (|00> + |11>)/sqrt(2)
        .state();

bell.probability(0b00);   // 0.5
bell.probability(0b11);   // 0.5
bell.probability(0b01);   // 0.0
```

The two qubits are now **entangled**: neither has a state of its own, yet measuring one fixes the other.

### Search with Grover

```java
GroverRegisters regs = new GroverRegisters(3);                 // 3 input qubits + 1 ancilla, N = 8
int iterations = GroverSearch.optimalIterations(regs.searchSpace(), 1);

StateVector state = new GroverSearch().run(regs, new MultiControlledXOracle(0b101), iterations);

int found = state.sampleMeasurement(new Random()) & regs.inputMask();   // 5 with probability ~0.945
```

### Teleport a state

```java
TeleportationRegisters regs = TeleportationRegisters.DEFAULT;   // source = 0, alice = 1, bob = 2
StateVector state = new QuantumCircuit(3).ry(0, 0.7).rz(0, 2.1).state();   // an arbitrary state on qubit 0

Teleportation.Outcome bits = new Teleportation().teleport(state, regs, new Random());
// Bob's qubit (2) now holds the state that qubit 0 had; `bits` are the two classical bits Alice "sent".
```

## Concepts and conventions

**State vector.** An *n*-qubit register is a vector of $2^n$ complex amplitudes
$|\psi\rangle = \sum_x a_x |x\rangle$ with $\sum_x |a_x|^2 = 1$. This simulator stores that vector densely, so memory and
time per gate both scale as $O(2^n)$. That exponential cost is precisely why quantum hardware is interesting, and why
classical simulation is limited to a few tens of qubits.

**Qubit ordering.** Qubit 0 is the *least-significant bit* of the basis-state index, the same little-endian convention
Qiskit uses. For 2 qubits, `amplitude(0b10)` is the amplitude of $|q_1 = 1,\ q_0 = 0\rangle$. Printed bit strings
(`StateVector.toString()`) list the highest qubit on the left.

**Gates are unitary.** Every gate is a unitary matrix $U$ ($U^\dagger U = I$), so it preserves total probability and is
reversible. `StateVector.totalProbability()` stays at 1 after any sequence of gates, and the test suite asserts this.

**Applying a gate without building a matrix.** A single-qubit gate on qubit $t$ never needs the full $2^n \times 2^n$
matrix $I \otimes \dots \otimes U \otimes \dots \otimes I$. The basis states split into pairs that differ only in bit $t$,
and the gate acts on each pair independently:

$$
\begin{pmatrix} a_0' \\ a_1' \end{pmatrix} =
\begin{pmatrix} u_{00} & u_{01} \\ u_{10} & u_{11} \end{pmatrix}
\begin{pmatrix} a_0 \\ a_1 \end{pmatrix}
$$

That is the whole of `applySingleQubitGate`. A controlled gate does the same, but only for pairs where the control bit
is 1, and a multi-controlled gate requires all control bits to be 1.

## Gates

| Gate | Matrix / action | API |
|---|---|---|
| Pauli-X | bit flip, $\|0\rangle \leftrightarrow \|1\rangle$ | `x(q)` |
| Pauli-Y | bit and phase flip | `y(q)` |
| Pauli-Z | phase flip, $\|1\rangle \to -\|1\rangle$ | `z(q)` |
| Hadamard | $\|0\rangle \to (\|0\rangle+\|1\rangle)/\sqrt2$, $\|1\rangle \to (\|0\rangle-\|1\rangle)/\sqrt2$ | `h(q)` |
| S, T | phase gates, $\sqrt Z$ and $\sqrt S$ | `s(q)`, `t(q)` |
| Rx, Ry, Rz | rotations of the Bloch vector by $\theta$ | `rx`, `ry`, `rz` |
| CNOT | flips target when control is 1 | `cnot(c, t)` |
| Toffoli (CCNOT) | flips target when both controls are 1 | `toffoli(c1, c2, t)` |
| Controlled-$U$ | any 2x2 unitary under one control | `StateVector.applyControlledGate` |
| Multi-controlled-$U$ | any 2x2 unitary under a set of controls | `StateVector.applyMultiControlledGate` |
| Phase flip | $\|x\rangle \to -\|x\rangle$ for chosen basis states | `StateVector.flipPhaseWhere` |

Two things worth internalizing:

- **Hadamard converts between "which value" and "which sign".** It maps the computational basis $\{|0\rangle, |1\rangle\}$
  to the sign basis $\{|+\rangle, |-\rangle\}$. A phase flip is invisible when you measure in the computational basis, but
  after a Hadamard it becomes a bit flip. Both Grover's diffuser and the teleportation measurement are built on this.
- **Single-qubit rotations reach every pure single-qubit state.** Up to an irrelevant global phase,
  $R_z(\varphi) R_y(\theta)|0\rangle$ is a generic point on the Bloch sphere. The tests use this to teleport "arbitrary" states.

## Measurement

Measurement is where quantum mechanics stops being linear algebra, so the simulator exposes it in two distinct forms.

| Method | Collapses the state? | Use |
|---|---|---|
| `probability(x)` / `marginalProbability(q, v)` | no | inspect the Born-rule probabilities directly (impossible on real hardware) |
| `sampleMeasurement(random)` | no | draw one shot of all qubits from a prepared state, for statistics over many shots |
| `measure(q, random)` | **yes** | measure one qubit; zero the inconsistent amplitudes and renormalize |

By the **Born rule**, outcome $x$ has probability $|a_x|^2$. After `measure`, the state is projected onto the observed
outcome and renormalized by $1/\sqrt{p}$, so measuring the same qubit again always returns the same bit. Collapse is what
makes teleportation work and what makes it irreversible.

## Algorithms

### Toffoli gate (CCNOT)

$$|c_1, c_2, t\rangle \;\longmapsto\; |c_1, c_2,\ t \oplus (c_1 \wedge c_2)\rangle$$

Three qubits in and three out: the target flips only if **both** controls are 1, and the controls pass through unchanged.
As a matrix it is the $8\times 8$ identity with two basis states swapped, so it is a permutation, hence unitary and its
own inverse.

Why it matters:

- A classical AND gate destroys information (three input patterns give output 0), so it cannot be a quantum gate.
  Toffoli keeps both inputs, which makes it reversible. With the target in $|0\rangle$ it **computes AND into the
  target**; with the target in $|1\rangle$ it computes NAND; with both controls fixed to 1 it is NOT.
- NAND is universal for classical logic, so Toffoli alone can implement any classical computation reversibly. This is
  the link between classical reversible computing and quantum circuits.
- Together with the Hadamard gate, Toffoli is computationally universal for quantum computation (Shi 2003, Aharonov 2003).
- On superpositions it acts by linearity. With the controls in $|+\rangle|+\rangle$ it evaluates AND on all four inputs
  at once and leaves the result entangled with the controls.

### Grover's search

**Problem.** Find a marked item $w$ among $N = 2^n$ unordered items, given only a black-box test $f(x)$. Classically this
takes $\Theta(N)$ queries. Grover's algorithm needs $\Theta(\sqrt N)$, a quadratic speedup that is provably optimal for
unstructured search (Bennett, Bernstein, Brassard, Vazirani 1997).

**The two reflections.** Start from the uniform superposition $|s\rangle = H^{\otimes n}|0\ldots0\rangle$. Each iteration
applies two reflections:

1. **Oracle** $O_f|x\rangle = (-1)^{f(x)}|x\rangle$ flips the sign of the marked state(s). It changes only phases, so by
   itself it changes no measurement probabilities.
2. **Diffuser** $D = 2|s\rangle\langle s| - I$, "inversion about the mean": every amplitude is mapped to
   $a_x \mapsto 2\bar a - a_x$. The Hadamards turn the earlier phase flip into an amplitude change. It is built as
   $H^{\otimes n}\,(2|0\rangle\langle 0| - I)\,H^{\otimes n}$.

**Geometric picture.** Let $|w\rangle$ be the uniform superposition of the $M$ marked states and $|r\rangle$ that of the
rest. Then $|s\rangle = \sin\theta\,|w\rangle + \cos\theta\,|r\rangle$ with $\sin\theta = \sqrt{M/N}$. The oracle reflects
across $|r\rangle$, the diffuser reflects across $|s\rangle$, and two reflections compose to a **rotation by $2\theta$**
toward $|w\rangle$. After $k$ iterations:

$$P_{\text{success}}(k) = \sin^2\!\big((2k+1)\theta\big), \qquad k_{\text{opt}} \approx \frac{\pi}{4}\sqrt{\frac{N}{M}}$$

Two consequences that surprise people:

- **You can overshoot.** Past $k_{\text{opt}}$ the state rotates *past* $|w\rangle$ and the success probability falls again.
  Grover is not "run it longer for a better answer", and you need to know (or estimate) $M$.
- **N = 4 with one marked item succeeds with probability exactly 1 after a single iteration** ($\theta = 30°$, so
  $3\theta = 90°$). Qsim's tests check this.

**Ancilla and phase kickback.** The gate-level oracle `MultiControlledXOracle` flips an ancilla qubit when the input
register equals the marked value. With the ancilla prepared in $|-\rangle = (|0\rangle - |1\rangle)/\sqrt2$, flipping it
returns the same state with a factor of $-1$:

$$X|-\rangle = -|-\rangle \;\Longrightarrow\; U_f\,|x\rangle|-\rangle = (-1)^{f(x)}|x\rangle|-\rangle$$

The sign "kicks back" onto the input register while the ancilla is left untouched. This is how a classical-style
function evaluation becomes a phase oracle on real hardware. The marked value is matched by sandwiching a multi-controlled
X between X gates on the zero bits. The diffuser acts on the input qubits only, never on the ancilla.

`PredicateOracle` is the simulator shortcut: it negates the amplitudes of any set of marked states directly from an
`IntPredicate` and supports $M > 1$. A test asserts the two oracles produce identical results.

```java
new GroverSearch().run(regs, new PredicateOracle(x -> x == 3 || x == 12), iterations);   // M = 2 of N = 16
```

### Quantum teleportation

**The statement.** Alice holds one qubit in an unknown state $|\psi\rangle = \alpha|0\rangle + \beta|1\rangle$. She and Bob
share a Bell pair. By measuring her two qubits and sending **two classical bits**, she lets Bob reconstruct $|\psi\rangle$
on his half of the pair.

What it is *not*:

- **Not cloning.** The no-cloning theorem forbids copying an unknown state. Alice's measurement destroys her copy, so the
  state is *moved*. Qsim's tests assert that her qubits end in a definite basis state.
- **Not faster than light.** Before Bob receives the two bits, his qubit is maximally mixed no matter what Alice sent.
  The classical channel is essential.
- **Not a measurement of the state.** $\alpha$ and $\beta$ are never read out, so nobody has to know them.

**The protocol** (qubit 0 = state to send, 1 = Alice's half, 2 = Bob's half):

1. Share a Bell pair: H on qubit 1, CNOT(1 → 2).
2. Alice's Bell measurement: CNOT(0 → 1), then H on qubit 0, then measure qubits 0 and 1 to get bits $m_0$ and $m_1$.
3. Bob corrects: X on qubit 2 if $m_1 = 1$, then Z on qubit 2 if $m_0 = 1$.

**Why it works.** After step 2's gates (before measuring), the three-qubit state regroups as

$$\tfrac12\Big[\,|00\rangle|\psi\rangle + |01\rangle X|\psi\rangle + |10\rangle Z|\psi\rangle + |11\rangle ZX|\psi\rangle\,\Big]$$

up to global phases. The CNOT and Hadamard rotate the Bell basis onto the computational basis, so measuring $(m_0, m_1)$
reveals which of four Pauli errors ($I, X, Z, ZX$) Bob's qubit carries, and tells Alice nothing about
$\alpha$ and $\beta$: all four outcomes occur with probability $\tfrac14$ regardless of $|\psi\rangle$. The two bits say
which Pauli to undo.

**How it is verified.** To test "Bob holds exactly $|\psi\rangle$", the suite applies the inverse of the preparation
($R_y(-\theta)R_z(-\varphi)$) to Bob's qubit and requires it to read $|0\rangle$ with probability 1. This also catches phase
errors such as a missing Z correction, which a direct probability comparison would miss (for $|+\rangle$ versus $|-\rangle$
the measurement statistics are identical).

## Architecture

```
com.quantorium.qsim
├── core        StateVector: amplitudes, gate application, measurement
├── gates       Gates: constant matrices and rotation factories
├── circuit     QuantumCircuit: fluent builder over a StateVector
└── algorithm   GroverSearch, Oracle (+ implementations), Teleportation, register records
```

- **`core`** knows nothing about circuits or algorithms. All gate application reduces to two primitives: pairwise 2x2
  updates (optionally under a control mask) and diagonal phase flips.
- **`algorithm`** depends on `core` and `circuit`, never the reverse. Oracles are a small port (`Oracle`), so the
  Grover loop is independent of how the marked set is encoded.
- **Register records** (`GroverRegisters`, `TeleportationRegisters`) put qubit layout ("the ancilla is the top qubit")
  in one place instead of scattering index arithmetic.
- `StateVector` mutates in place and returns `this` for chaining; use `copy()` to keep a snapshot.

## Testing strategy

The tests check **physical invariants and closed-form results**, not just example outputs:

| Property | Where |
|---|---|
| Unitarity: total probability stays 1 | every algorithm test |
| Self-inverse gates (Toffoli applied twice is the identity) | `ToffoliGateTest` |
| Full truth table of Toffoli over all 8 basis inputs, plus superposition and non-adjacent qubits | `ToffoliGateTest` |
| Grover success probability equals $\sin^2((2k+1)\theta)$ for several $n$, marked values and $k$, including overshoot | `GroverSearchTest` |
| Gate-level (ancilla) oracle and predicate oracle agree | `GroverSearchTest` |
| Teleportation succeeds for $|0\rangle$, $|1\rangle$, $|+\rangle$, complex-phase and generic states, across random measurement outcomes | `TeleportationTest` |
| The four teleportation outcomes are uniform; Alice's qubits end collapsed | `TeleportationTest` |
| Measurement collapses, renormalizes, and follows the Born rule statistically | `TeleportationTest` |

Statistical tests use fixed seeds with explicit tolerances, so they are reproducible.

```bash
./gradlew test
```

## Limitations

This is a teaching and verification tool, not a production simulator.

- **Exponential memory.** Amplitudes are stored as one `Complex` object each, so practical limits are a few tens of qubits
  at most, depending on heap size.
- **Ideal quantum computer only.** No noise, decoherence, or error models, and no density matrices, so mixed states are
  not represented.
- **Measurement results are Java values.** There is no classical register or circuit-level classical control; the
  teleportation corrections are ordinary `if` statements on the returned bits.
- **No circuit IR.** Gates execute immediately on a state vector, so there is no optimizer, transpiler or circuit
  drawer.
- **Grover's speedup is not visible.** The simulator evaluates $f$ classically and costs $O(2^n)$ per gate, so the
  algorithm's $\sqrt N$ *query* advantage does not translate into faster wall-clock time here. The point is to see
  the amplitudes rotate.

## Further reading

- M. Nielsen, I. Chuang, *Quantum Computation and Quantum Information*, the standard reference for everything above.
- L. Grover, *A fast quantum mechanical algorithm for database search* (1996).
- C. Bennett et al., *Teleporting an unknown quantum state via dual classical and Einstein-Podolsky-Rosen channels* (1993).
- C. Bennett, E. Bernstein, G. Brassard, U. Vazirani, *Strengths and weaknesses of quantum computing* (1997).
- Y. Shi, *Both Toffoli and controlled-NOT need little help to do universal quantum computing* (2003);
  D. Aharonov, *A simple proof that Toffoli and Hadamard are quantum universal* (2003).

## License

Released under the [MIT License](LICENSE).
