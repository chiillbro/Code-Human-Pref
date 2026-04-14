# Turn 1 — Classifications

## Prompt Source of Truth

> hey there, the tuxemon battle ai currently chooses replacement monsters using simple criteria like highest level or healthiest, but it completely ignores what type of monster the opponent has on the field. as per me, a good ai opponent should consider type matchups when choosing which monster to send in, for example it should prefer swapping to a water type when the opponent has a fire-type active. it should also be able to analyze things like how favorable the current battle position is (considering HP rations, type advantages, bench depth, status effects) to make smarter and nice decisions about when switching actually makes sense versus staying in. also, add an ai difficulty config in yaml format (easy/medium/hard) that will control switching aggressive thresholds

---

## Model A — Pros

- Type matchup scoring via `type_matchup_score()` in `battle_analyzer.py` correctly uses `ElementTypesHandler.calculate_affinity_score()` to compute both offensive and defensive multipliers, which is the canonical way to compute type effectiveness in the Tuxemon codebase.
- The `evaluate_switch()` method in `TrainerAIDecisionStrategy` has a solid flow: checks bench availability, checks `swap_tracker.can_swap()`, evaluates battle position, compares matchup improvement against `min_switch_advantage` threshold — all the right gates in the right order.
- The `evaluate_battle_position()` function is clean and easy to follow — plain function with explicit breakdown dict, no over-engineering. The HP, type, bench, and status factors are all weighted through the difficulty config.

## Model A — Cons

- `action_swap()` does not validate whether the monster can actually swap (e.g., status effects like swap-out-lock). It unconditionally calls `swap_tracker.register()` and `enqueue_action()`. The codebase has `Technique.validate_monster()` and `swap_tracker.can_swap()` checks that should be consulted before enqueuing.
- The `type_matchup` strategy in `AIManager.choose_replacement_monster()` accesses `self.session.client.combat_session` — but looking at how `AIManager` is used, `self.session` is the game `Session`, and `session.client.combat_session` is not a guaranteed path. The existing `AI` class accesses `self.combat_session` differently (passed in through the constructor from the combat state). This integration path is fragile and may not work at runtime.
- `DifficultyEntry` field is named `difficulty` on `NpcCombatModel`, but the existing model has no `difficulty` field — and Model A references `character.combat.difficulty` in `ai.py` without verifying that this property actually resolves from the Pydantic model through the NPC entity. If the NPC entity doesn't forward `combat.difficulty`, this will just always be `None` and silently fall back to the dataclass default.

## Model B — Pros

- Uses `simple_damage_multiplier()` from `formula.py` for type matchup calculation, which is the function already used by the codebase's `TechniqueTracker` for technique scoring — consistent with established patterns rather than calling `ElementTypesHandler` directly.
- `action_swap()` includes `swap.validate_monster()` check before enqueuing, returning `False` if the monster can't swap (e.g., due to swap-out-lock status). This prevents the AI from queuing invalid actions.
- Includes unit tests covering matchup scoring, position evaluation, replacement selection, and switching thresholds — not required by the prompt, but a nice addition that validates the scoring math works as intended.
- `PositionEvaluator` is a proper class with the difficulty config stored as instance state, so `evaluate()`, `select_best_replacement()`, and `should_switch()` share config without passing it through every function call. The AI instance stores `self.position_evaluator` once and reuses it.
- YAML includes a `"default"` difficulty entry + the loader validates its presence with `raise ValueError` — provides a known fallback without silently using hardcoded values.

## Model B — Cons

- `should_switch()` extracts the current monster's matchup by doing `current.breakdown["type_matchup"] / self.config.type_matchup_weight` to reverse-engineer the raw matchup score from the weighted breakdown. This is fragile — if someone changes how `evaluate()` computes the type_matchup entry, this reverse division will silently produce wrong values.
- In `AIManager.choose_replacement_monster()`, a new `PositionEvaluator` is constructed on every call — loading the difficulty config, creating the evaluator, selecting the best replacement. This is wasteful and doesn't reuse the evaluator already stored on the `AI` instance.
- `try_switch()` calls `get_bench()` to get `opponent_bench` but doesn't convert the result to a list — `get_bench()` might return a generator/iterator, which could be consumed once and produce incorrect bench-depth scoring if iterated again. (Model A explicitly calls `list()` on all bench results.)
- The `MatchupScore` uses `offensive - defensive` as the total, while Model A uses `offensive / defensive`. Neither approach accounts for the fact that `simple_damage_multiplier` is already clamped by `multiplier_range` config — the subtraction approach can produce misleading scores when both multipliers are in similar ranges (e.g., 1.0 - 1.0 = 0.0 regardless of absolute magnitudes).

---

## Overall Preference Justification

Model B is the better response for this turn. The most significant difference is in swap robustness — Model B's `action_swap()` validates whether the monster can actually swap before enqueuing (using `Technique.validate_monster()`), whereas Model A unconditionally registers the swap, which would cause the AI to attempt illegal actions on swap-locked monsters. Model B also follows the codebase's existing patterns more closely by using `simple_damage_multiplier()` from `formula.py` for type matchup calculations — the same function already used by `TechniqueTracker` — rather than calling `ElementTypesHandler` directly as Model A does. Model B encapsulates the evaluation logic in a `PositionEvaluator` class with cached config, while Model A uses bare functions that need the config passed every time. Both models have fragile integration in `AIManager.choose_replacement_monster()`. Model B's weaknesses (fragile reverse-division in `should_switch()`, redundant evaluator construction in the manager) are architectural warts rather than correctness bugs, so they don't outweigh Model A's swap validation gap.

---

## Axis Ratings

| Axis                          | Rating                          |
| ----------------------------- | ------------------------------- |
| Logic and Correctness         | 6 - Model B Slightly Preferred  |
| Naming and Clarity            | 5 - Model B Minimally Preferred |
| Organization and Modularity   | 6 - Model B Slightly Preferred  |
| Interface Design              | 5 - Model B Minimally Preferred |
| Error Handling and Robustness | 7 - Model B Medium Preferred    |
| Comments and Documentation    | 5 - Model B Minimally Preferred |
| Review / Production Readiness | 6 - Model B Slightly Preferred  |

**Overall: 6 - Model B Slightly Preferred**

---

## Next Step / Follow-Up Prompt (Turn 2)

> A few things to address on the implementation:
>
> 1. The YAML config file is at `mods/ai_difficulty.yaml`, but `AIConfigLoader` resolves paths via `paths.mods_folder` which points to `mods/tuxemon/`. Move the YAML file to `mods/tuxemon/ai_difficulty.yaml` so it's actually found at runtime.
> 2. In `should_switch()`, you're recovering the raw matchup score by dividing `breakdown["type_matchup"]` by the weight. Don't reverse-engineer from the weighted breakdown — compute the raw matchup score directly by calling `best_matchup_against(monster, opponents)` during `evaluate()` and store it on `PositionScore` (add a `raw_type_matchup` field). Then `should_switch()` can just read that field.
> 3. In `AIManager.choose_replacement_monster()`, you're constructing a new `PositionEvaluator` and loading the difficulty config on every call. The `AI` instance already has `self.position_evaluator` — find a way to reuse it or at least cache the evaluator on the manager instead of rebuilding it each time.
> 4. `try_switch()` assigns `opponent_bench = combat_session.get_bench(opponent_player)` without wrapping it in `list()`. If `get_bench()` returns an iterator, it gets consumed once during `evaluate()` and the bench depth score would be wrong. Wrap it in `list()` like you did for `own_bench`.
> 5. The unit tests mock `simple_damage_multiplier` at the module level but don't test what happens when both monsters share the same type (e.g., fire vs fire where offensive == defensive). Add a test for same-type matchups and a test for the edge case where the bench is non-empty but all candidates have a worse matchup than the active monster (should_switch should return False even if position is below threshold).
