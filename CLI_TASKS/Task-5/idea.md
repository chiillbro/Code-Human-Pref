# Task-3: Advanced Battle AI with Minimax Decision Tree and Dynamic Strategy Switching

**Task ID:** Task-03  
**Type:** Complex Refactor + Substantial New Feature

---

## Core Request (Turn 1)

### Summary

Refactor the existing trainer AI decision-making system to replace the current single-pass weighted scoring with a proper Minimax decision tree (with alpha-beta pruning) that looks ahead N turns to evaluate move choices. Layer on top of this a **dynamic strategy controller** that detects the current battle phase (e.g., "setup", "sweep", "stall", "desperation") and adjusts AI behavior accordingly. The AI must also implement **monster switching logic** — evaluating when it's better to swap in a different monster rather than attack with the current one, considering type matchups and HP thresholds.

### Detailed Requirements

#### 1. Battle State Evaluator (`tuxemon/ai/battle_evaluator.py` — new file)

Create a `BattleStateEvaluator` class that produces a numeric score for any given battle state:

- **Inputs:** Both sides' active monsters (HP, stats, status effects, types), both sides' bench monsters (party members not in battle), turn count, weather (if Task-1 is implemented, otherwise ignore).
- **Scoring heuristic:** Weighted sum of:
  - Active monster HP ratio (current / max) for both sides.
  - Type advantage of active attacker vs. defender (using `ElementTypesHandler`).
  - Status effects on each side's active monster (negative statuses on opponent = positive score, on self = negative).
  - Bench depth: number of healthy bench monsters remaining (more = better).
  - Level differential.
  - Fainted monster count on each side.
- The evaluator must be stateless (pure function of the battle snapshot) to support Minimax tree evaluation.

#### 2. Minimax Engine with Alpha-Beta Pruning (`tuxemon/ai/minimax.py` — new file)

Implement a `MinimaxEngine` class:

- **Search depth:** Configurable (default depth=2, meaning AI looks 2 full turns ahead — its move + opponent's response).
- **Move generation:** For the current turn, enumerate all legal actions: each available technique the active monster can use + the option to switch to each healthy bench monster.
- **Opponent modeling:** Assume the opponent plays optimally (picks the action that maximizes their own score — standard Minimax assumption). For wild monsters, assume random move selection instead.
- **Alpha-beta pruning:** Implement standard alpha-beta cutoffs to prune branches and reduce the search tree.
- **Terminal states:** A position is terminal if either side has no remaining healthy monsters. Terminal evaluation returns +∞ for AI win, −∞ for AI loss.
- **Return value:** The engine returns the best `Action` (a technique target pair or a switch command) and the evaluation score.

#### 3. Dynamic Strategy Controller (`tuxemon/ai/strategy_controller.py` — new file)

Create a `StrategyController` that classifies the current battle phase and adjusts Minimax weights:

- **Battle phases:**
  - `SETUP`: First 2 turns, or when the AI's monster has stat-boosting moves. Prefer stat-boost moves over attacks.
  - `SWEEP`: AI's active monster has a significant stat or type advantage. Prefer high-damage attacks.
  - `STALL`: AI's active monster is at a type disadvantage but has healing/status moves. Prefer status/healing.
  - `DESPERATION`: AI has only one monster left with <25% HP. Prefer highest-damage moves, ignore setup. Consider risky but high-reward moves.
  - `SWITCH`: AI's active monster is at a severe type disadvantage and there's a better matchup on the bench. Trigger a switch.
- **Phase detection:** The controller evaluates the battle state each turn and classifies the phase. It then provides modified scoring weights to the `BattleStateEvaluator`.
- **Weight profiles:** Each phase has a different weight profile (e.g., SETUP phase gives 2x weight to status moves, SWEEP gives 2x to damage, STALL gives 2x to healing/status).

#### 4. Monster Switching Logic (`tuxemon/ai/switch_evaluator.py` — new file)

Create a `SwitchEvaluator` class:

- For each bench monster, compute a "switch-in score" based on:
  - Type advantage vs. opponent's active monster.
  - HP ratio of the bench monster.
  - Whether the bench monster resists the opponent's last used move type.
  - Speed comparison (can it outspeed the opponent?).
- The switch option is added to the Minimax move generation as an alternative action. The engine naturally evaluates it alongside attack moves.
- **HP threshold switching:** If the active monster is below a configurable HP threshold (default 20%) and there's a bench monster with >50% HP and neutral-or-better matchup, the AI should strongly prefer switching.

#### 5. Integration with Existing AI System

- The existing `TrainerAIDecisionStrategy` in `tuxemon/ai/decision_strategy.py` must be refactored to delegate to the `MinimaxEngine` for trainer battles. The current weighted-scoring logic becomes a fallback for when Minimax depth=0 is configured (backward compatibility).
- The `WildAIDecisionStrategy` remains simple (random or weighted selection) but uses the same `BattleStateEvaluator` for any scoring it needs.
- The `AIManager` in `tuxemon/ai/manager.py` must be updated to instantiate the new components and pass the strategy controller's weights to the evaluator.
- AI difficulty levels (defined in YAML: `mods/tuxemon/ai_difficulty.yaml` — new file) control the Minimax search depth and the probability of the AI making a "mistake" (selecting a sub-optimal move). Easy=depth 1 with 30% mistake rate, Medium=depth 2 with 10% mistake rate, Hard=depth 3 with 0% mistake rate.

#### 6. AI Difficulty Configuration (`mods/tuxemon/ai_difficulty.yaml` — new file)

```yaml
difficulties:
  easy:
    search_depth: 1
    mistake_rate: 0.3
    description: "AI looks 1 turn ahead, makes mistakes 30% of the time"
  medium:
    search_depth: 2
    mistake_rate: 0.1
    description: "AI looks 2 turns ahead, makes mistakes 10% of the time"
  hard:
    search_depth: 3
    mistake_rate: 0.0
    description: "AI looks 3 turns ahead, plays optimally"
```

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws to Critique

1. **Minimax is too slow without pruning:** The model will likely implement basic Minimax but skip or incorrectly implement alpha-beta pruning, making depth=3 unplayably slow with 4+ moves × 4+ bench monsters per side. Demand correct alpha-beta with measurable node count reduction.

2. **BattleStateEvaluator couples to live game objects:** The model will likely pass actual `Monster` objects into the evaluator rather than creating lightweight snapshot/state tuples. This makes the Minimax tree clone the entire game state at each node. Demand a lightweight `BattleSnapshot` dataclass that the evaluator operates on.

3. **Strategy phase detection is too simplistic:** The model will likely use only HP thresholds for phase detection, ignoring move pool analysis (does the monster even have stat-boost moves for SETUP phase?). Demand move pool inspection for phase classification.

4. **Switch logic doesn't account for switching cost:** The model may not penalize the "lost turn" cost of switching (the opponent gets a free attack). The switch-in score must discount for the damage the opponent will deal during the switch turn. Demand this discount.

5. **Depth=0 fallback doesn't match old behavior exactly:** The model may break backward compatibility when refactoring `TrainerAIDecisionStrategy`. Demand that depth=0 produces identical behavior to the pre-refactor code path.

6. **Mistake rate implementation is naive:** The model will likely implement "mistakes" as a flat random chance to pick a random move, rather than picking the 2nd or 3rd best move (which is more realistic). Demand a "softmax over top-K moves" approach for mistakes.

7. **Missing timeout/depth limit for degenerate cases:** If both sides only have healing moves, the Minimax tree could loop forever. Demand a turn horizon limit that caps evaluation at N turns regardless of terminal state.

### Turn 3 — Tests & Polish

1. **Unit tests for BattleStateEvaluator:** Test scoring with known monster stats — verify type advantage, HP ratio, status effects all contribute correctly.
2. **Unit tests for MinimaxEngine:** With a small game tree (2 moves, depth 1), verify it picks the optimal move. Test alpha-beta pruning reduces node count vs. naive Minimax.
3. **Unit tests for StrategyController:** Test each phase detection scenario — SETUP with stat-boost available, SWEEP with type advantage, STALL with healing moves, DESPERATION at low HP, SWITCH with better matchup on bench.
4. **Unit tests for SwitchEvaluator:** Test type advantage scoring, HP threshold triggers, speed comparisons.
5. **Performance test:** Verify depth=3 with alpha-beta completes in under 500ms for a battle with 6 moves and 3 bench monsters per side (mock the evaluator for speed).
6. **Integration test:** Run a full AI turn decision from battle state to chosen action. Verify the chosen action matches expected optimal play for a known scenario.
7. **Backward compatibility test:** With depth=0 config, verify the AI produces the same decisions as the old system for a set of fixed battle states.
8. **Linting and type annotations pass `tox -e lint` and `tox -e type`.**

---

## Why It Fits the Constraint

**~500-600+ lines of new core code:**

- `battle_evaluator.py` (BattleSnapshot dataclass, scoring heuristic with 6+ factors): ~120 lines
- `minimax.py` (Minimax with alpha-beta, move generation, terminal evaluation): ~180 lines
- `strategy_controller.py` (5 phase detectors, weight profiles, move pool analysis): ~120 lines
- `switch_evaluator.py` (switch scoring, HP threshold logic, turn-cost discounting): ~80 lines
- `ai_difficulty.yaml` config: ~20 lines
- Modifications to `decision_strategy.py`, `manager.py`, `ai.py`, `technique_tracker.py`, `opponent_evaluator.py`: ~80+ lines

**Why it's naturally difficult:**

- Minimax with alpha-beta pruning is a well-known but subtle algorithm — getting the pruning bounds correct is non-trivial.
- The evaluator must be stateless and operate on snapshots, not live objects — this architectural constraint is frequently violated.
- Phase detection that inspects move pools is more complex than simple threshold checks.
- Switch-cost discounting requires simulating the opponent's next move during the switching turn — a recursive dependency.
- Backward compatibility with the existing AI while refactoring the decision strategy requires careful code organization.

**Why it won't be flawless in one turn:**

- Alpha-beta pruning is a notorious source of subtle bugs (incorrect bound initialization, failing to propagate cutoffs).
- The model will almost certainly use live Monster objects in the tree instead of snapshots.
- Phase detection based on move pool analysis is easy to oversimplify.
- The "mistake" mechanism and difficulty integration add cross-cutting complexity.

---

## Files Modified (Actual Implementation)

| #   | File Path                                      | Change Type                                                                                      | Lines |
| --- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------ | ----- |
| 1   | `tuxemon/ai/battle_evaluator.py`               | **New file** — `MonsterSnapshot`, `BattleSnapshot`, `EvalWeights`, `BattleStateEvaluator`        | 244   |
| 2   | `tuxemon/ai/minimax.py`                        | **New file** — `MinimaxEngine` with alpha-beta pruning, action simulation, `apply_mistake_rate`  | 512   |
| 3   | `tuxemon/ai/strategy_controller.py`            | **New file** — `StrategyController`, `BattlePhase` enum, per-phase weight profiles               | 248   |
| 4   | `tuxemon/ai/switch_evaluator.py`               | **New file** — `SwitchEvaluator`, type-aware switch scoring with turn-cost discounting           | 244   |
| 5   | `mods/tuxemon/ai_difficulty.yaml`              | **New file** — Difficulty presets (easy/medium/hard) with depth + mistake_rate                   | 20    |
| 6   | `tuxemon/ai/decision_strategy.py`              | **Modified** (+222) — Added `AdvancedTrainerAIDecisionStrategy` class                            | 520   |
| 7   | `tuxemon/ai/ai.py`                             | **Modified** (+108/-9) — Added `AIDifficultyConfig`, `get_ai_difficulty()`, `_create_strategy()` | 368   |
| 8   | `tuxemon/ai/manager.py`                        | **Modified** (+74) — Added `"smart"` switch strategy with `SwitchEvaluator`                      | 185   |
| 9   | `tests/tuxemon/test_ai_battle_evaluator.py`    | **New file** — Unit tests for evaluator and snapshots                                            | 247   |
| 10  | `tests/tuxemon/test_ai_minimax.py`             | **New file** — Unit tests for minimax engine, simulation, mistake rate                           | 333   |
| 11  | `tests/tuxemon/test_ai_strategy_controller.py` | **New file** — Unit tests for phase detection and weight profiles                                | 202   |
| 12  | `tests/tuxemon/test_ai_switch_evaluator.py`    | **New file** — Unit tests for switch scoring and force-switch logic                              | 208   |

**Totals:** 1,268 lines new code + 395 lines of modifications to existing files + 990 lines of tests = **2,653 lines total**

> **Note:** `tuxemon/ai/opponent_evaluator.py` and `tuxemon/ai/technique_tracker.py` were **not** modified. The original spec anticipated changes to these files, but the implementation instead extracts move-pool data (healing/status/stat-boost classification) directly in `MonsterSnapshot.from_monster()` and handles type advantage scoring internally in `BattleStateEvaluator._type_advantage_score()`, avoiding coupling to the existing evaluator/tracker interfaces.

---

## PR Overview (Implementation Summary)

### Architecture

The implementation adds a **Minimax-powered decision engine** layered on top of the existing AI system, fully backward-compatible with the original `TrainerAIDecisionStrategy`.

**Data Flow:**

```
AI.__init__() → _create_strategy()
  → AIConfigLoader.get_ai_difficulty() → loads ai_difficulty.yaml
  → if depth > 0: creates BattleStateEvaluator + MinimaxEngine + StrategyController + SwitchEvaluator
  → returns AdvancedTrainerAIDecisionStrategy (or falls back to TrainerAIDecisionStrategy at depth=0)

AdvancedTrainerAIDecisionStrategy.make_decision():
  1. Check healing items (same as legacy)
  2. Build BattleSnapshot from live combat session state
  3. StrategyController.detect_phase() → classify as SETUP/SWEEP/STALL/DESPERATION/SWITCH
  4. Get per-phase EvalWeights and set on evaluator
  5. MinimaxEngine.find_best_action() → alpha-beta search returning AIAction
  6. If mistake_rate > 0: apply_mistake_rate() via softmax-over-top-K
  7. _execute_action() → translate AIAction to game action (technique or swap)
```

### Key Design Decisions

1. **Frozen dataclass snapshots** — `MonsterSnapshot` and `BattleSnapshot` are immutable, enabling safe Minimax tree expansion without cloning live game objects.

2. **Heuristic damage estimation** — The minimax tree uses a stat-based damage approximation rather than the full `formula.py` pipeline, since the latter requires live Monster/Technique objects.

3. **Switch cost discounting** — Switching incurs a simulated free attack from the opponent, discouraging frivolous switches.

4. **Wild battle random opponent** — For wild encounters, the opponent is modeled as random (averaging scores over all actions) rather than optimal minimax.

5. **Technique.create("swap")** — Monster switches use the same `swap` technique pattern as the player-side combat menus, ensuring proper action queue processing.

6. **Move pool inspection** — `MonsterSnapshot.from_monster()` classifies the move pool (has_healing, has_status, has_stat_boost) at snapshot creation time for use by the strategy controller.

7. **Graceful degradation** — If `ai_difficulty.yaml` is missing or malformed, the system falls back to medium defaults (depth=2, mistake_rate=0.1). At depth=0, it uses the legacy `TrainerAIDecisionStrategy` identically.

### Backward Compatibility

- `TrainerAIDecisionStrategy` and `WildAIDecisionStrategy` remain untouched.
- `AI._create_strategy()` selects the strategy based on difficulty config; depth=0 returns the legacy strategy.
- Existing YAML configs (`ai_opponent.yaml`, `ai_trainers.yaml`, `ai_items.yaml`, `ai_techniques.yaml`) are unmodified.
- `AIManager.choose_replacement_monster()` only uses `SwitchEvaluator` when strategy is explicitly `"smart"`; all other strategies behave identically.

---

## Reviewer Difficulty Assessment & Trimmed Scope

### Why the Original Task Is Too Broad

The original task spec above produces ~2,653 lines across 12 files (4 new source files, 3 modified files, 1 YAML config, 4 test files). Key concerns:

1. **Minimax alone is 512 lines.** Alpha-beta pruning, move generation, opponent modeling, action simulation, terminal evaluation, and mistake-rate softmax — this is essentially a standalone algorithm project.
2. **StrategyController adds another 248 lines** with 5 battle phases, weight profiles, and move-pool-based phase detection.
3. **Each model response would be 800-1000+ lines of code.** Over 3 turns (6 model responses), that's 5,000+ lines of model output in context, plus prompts, summaries, and analysis text. This WILL hit context limits.
4. **The scope is not "single PR" atomic.** Minimax engine + strategy controller + switch evaluator + battle evaluator + difficulty config + integration refactors would realistically be 3-4 separate PRs in any real project.

### Trimmed Scope (Recommended)

**Keep the core, drop the algorithmic overkill.** Focus on:

1. **BattleStateEvaluator** (`tuxemon/ai/battle_evaluator.py` — new) — `MonsterSnapshot` and `BattleSnapshot` frozen dataclasses + weighted scoring evaluator with HP ratio, type advantage, bench depth, status effects, level differential, fainted penalty. (~150 lines)
2. **SwitchEvaluator** (`tuxemon/ai/switch_evaluator.py` — new) — Type-aware switch-in scoring (type advantage vs opponent, HP ratio, speed comparison, resistance to opponent's last-used move type, HP-threshold-based force-switching, turn-cost discounting for free attack). (~120 lines)
3. **Integration** — Update `AIManager.choose_replacement_monster()` in `manager.py` to support a new `"smart"` strategy using `SwitchEvaluator`. Update `ai.py` to load difficulty config from YAML. (~60-80 lines)
4. **AI Difficulty Config** (`mods/tuxemon/ai_difficulty.yaml` — new) — Easy/medium/hard presets controlling switching aggressiveness thresholds. (~20 lines)
5. **Unit Tests** — Tests for evaluator snapshots, scoring math, and switch scoring logic. (~200-250 lines)

**Total: ~550-620 lines** — achievable in 3 turns without context issues.

**Dropped:**

- Minimax engine with alpha-beta pruning (512 lines, too algorithmically complex)
- StrategyController with 5 battle phases (248 lines, adds too much scope)
- AdvancedTrainerAIDecisionStrategy (222-line refactor of decision_strategy.py)

### Why This Trimmed Scope Still Works for 3 Turns

- **Turn 1** will likely have models coupling snapshots to live `Monster` objects, mishandling `ElementTypesHandler` for type scoring, or botching the integration into `AIManager`.
- **Turn 2** can address snapshot immutability issues, switch-cost discounting being missing/wrong, and type advantage calculation bugs.
- **Turn 3** addresses test coverage, edge cases (empty bench, all fainted, same-type matchups), and linting/type-checking.

The trimmed scope still makes models diverge meaningfully (evaluator architecture, type multiplier handling, HP threshold logic, integration approach) while staying within context budget.

---

## Drafted Turn 1 Prompt

> The Tuxemon battle AI currently picks replacement monsters using simple criteria like highest level or healthiest, but it completely ignores what type of monster the opponent has on the field. A good AI opponent should consider type matchups when choosing which monster to send in — for example, it should prefer swapping to a water-type when the opponent has a fire-type active. It should also be able to evaluate how favorable the current battle position is (considering HP ratios, type advantages, bench depth, status effects) to make smarter decisions about when switching actually makes sense versus staying in. Add an AI difficulty config in YAML format (easy/medium/hard) that controls switching aggressiveness thresholds. Write unit tests for the new evaluation and switching logic.

### Notes on This Prompt

- **User perspective:** Describes _what_ the AI should do (consider type matchups, evaluate battle state, know when to switch) without prescribing architecture (no mention of "dataclasses", "frozen snapshots", "evaluator classes").
- **No implementation cheatsheet:** Doesn't mention `ElementTypesHandler`, `MonsterSnapshot`, or any specific class names. Models must discover the repo's type system on their own.
- **No AI-steering:** Doesn't say "make it production ready" or "write clean code."
- **Room for divergence:** Both models must independently figure out how to represent battle state, score type advantages, and integrate with the existing `AIManager.choose_replacement_monster()` system.
- **Naturally needs 3 turns:** The evaluator/snapshot architecture, type advantage integration, and switch-cost discounting are subtle enough that models will get things wrong in Turn 1.
