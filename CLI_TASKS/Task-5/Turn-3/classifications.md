# Turn 3 — Classifications (Final Turn)

## Prompt Source of Truth

Two items requested:

1. Consolidate evaluator construction — `AI.__init__()` should get its evaluator from the manager instead of building its own. Remove the duplicate difficulty-config loading path.
2. Add a unit test for `get_position_evaluator()` on `AIManager` verifying caching (same character → same instance, `clear_ai()` → cache invalidated).

---

## Model A — Pros

- Correctly consolidates the construction path: `AI.__init__()` now takes `position_evaluator` as a parameter, the 9-line block of difficulty loading and local evaluator construction is removed, and `AIManager.process_ai_turn()` passes `self.get_position_evaluator(character)`.
- Caching test verifies `mock_get_difficulty.call_count == 1` after two calls for the same character — this confirms the cache actually prevents redundant YAML loading, not just object identity.

## Model A — Cons

- Leaves `PositionEvaluator` as a regular import in `ai.py` even though after this change it's only used as a type annotation on the constructor parameter. A minor cleanup miss.

## Model B — Pros

- Same consolidation as Model A — functionally identical `ai.py` and `manager.py` changes.
- Moves the `PositionEvaluator` import under `TYPE_CHECKING` in `ai.py` since it's now only used for the type hint on the constructor parameter. This is the correct pattern — avoids a circular import risk and aligns with how the codebase already handles similar imports.
- Test includes `assert isinstance(first, PositionEvaluator)` which validates the returned object is actually the right type, not just a mock artifact.

## Model B — Cons

- Test does not verify `mock_get_difficulty.call_count` — so it confirms object identity but doesn't verify the config loader was only called once. Minor gap since identity already implies caching, but Model A's approach is more thorough on that point.

---

## Overall Preference Justification

Both models address the prompt fully and produce nearly identical functional changes — the `ai.py` constructor refactor and `manager.py` injection are the same code. The meaningful difference is in import hygiene: Model B moves `PositionEvaluator` under `TYPE_CHECKING` since it's now only used for the type annotation on the constructor, which is the right pattern for this codebase and avoids potential circular import issues. Model A leaves it as a runtime import which still works but isn't as clean. On the testing side, Model A checks `call_count == 1` (proving the cache prevents redundant config loads) while Model B checks `isinstance` (proving the returned object is typed correctly) — both are valid, slightly different angles. Overall the import cleanup in Model B is a more meaningful code-quality improvement than Model A's extra assertion, so Model B gets a slight edge. At this point the task is effectively complete — the AI evaluates type matchups for switching, the position scoring works, difficulty config is loaded and cached centrally, and all 45 tests pass. Any remaining improvements would be polish-level and don't affect functionality.

---

## Axis Ratings

| Axis                          | Rating                          |
| ----------------------------- | ------------------------------- |
| Logic and Correctness         | 5 - Model B Minimally Preferred |
| Naming and Clarity            | 5 - Model B Minimally Preferred |
| Organization and Modularity   | 6 - Model B Slightly Preferred  |
| Interface Design              | 5 - Model B Minimally Preferred |
| Error Handling and Robustness | 5 - Model B Minimally Preferred |
| Comments and Documentation    | 5 - Model B Minimally Preferred |
| Review / Production Readiness | 5 - Model B Minimally Preferred |

**Overall: 5 - Model B Minimally Preferred**

---

## Task Status: COMPLETE

The feature is production-ready at this point. Across 3 turns, the implementation now includes:

- **`position_evaluator.py`** — `PositionEvaluator` class with type-matchup scoring via `simple_damage_multiplier()`, HP-ratio comparison, bench-depth weighting, status penalties, and `raw_type_matchup` on `PositionScore` for clean switch evaluation.
- **`decision_strategy.py`** — `try_switch()` on `TrainerAIDecisionStrategy` that evaluates the battle position and swaps when a bench monster offers a sufficiently better matchup.
- **`ai.py`** — `DifficultyEntry`/`AIDifficulty` dataclasses, `AIConfigLoader.get_ai_difficulty()`, `action_swap()` with swap validation, constructor takes evaluator from manager.
- **`manager.py`** — Cached `get_position_evaluator()` per NPC, `type_matchup` strategy in `choose_replacement_monster()`, single construction path.
- **`db.py`** — `ai_difficulty` field on `NpcCombatModel`.
- **`mods/ai_difficulty.yaml`** — Easy/medium/hard/default difficulty profiles.
- **45 passing tests** covering matchup scoring, position evaluation, switch logic, difficulty aggressiveness, same-type edge case, worse-bench edge case, and evaluator caching.
