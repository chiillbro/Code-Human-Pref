# Turn 2 — Classifications

## Prompt Source of Truth

Five specific fixes requested:

1. Move YAML to `mods/tuxemon/ai_difficulty.yaml`
2. Stop reverse-engineering matchup in `should_switch()` — store `raw_type_matchup` on `PositionScore`
3. Cache the `PositionEvaluator` in `AIManager` instead of rebuilding per call
4. Wrap `opponent_bench` in `list()` in `try_switch()`
5. Add test for same-type matchups + test for bench-all-worse edge case

**Note on item #1:** Both models pushed back on this, claiming `paths.mods_folder` resolves to `mods/` not `mods/tuxemon/`. Checking the actual codebase (`paths.py` line 33: `mods_folder = (LIBDIR.parent / "mods").resolve()`) and the `mods/` directory listing confirms the existing AI YAML files (`ai_items.yaml`, `ai_opponent.yaml`, etc.) all live at `mods/`, not `mods/tuxemon/`. Both models are correct — the YAML file is already in the right place.

---

## Model A — Pros

- Addresses items 2–5 correctly: `raw_type_matchup` stored on `PositionScore` and read directly in `should_switch()`, evaluator cached via `get_position_evaluator()` on `AIManager`, `opponent_bench` wrapped in `list()`, both requested tests added.
- Correctly pushed back on item #1, explaining that `paths.mods_folder` resolves to `mods/` and the file is already in the right place. The summary's claim is verified against the codebase.

## Model A — Cons

- Existing test constructors still pass `breakdown={"type_matchup": ...}` values that `should_switch()` no longer reads — not wrong, but unnecessary data in the test fixtures.

## Model B — Pros

- Addresses items 2–5 identically to Model A — `raw_type_matchup` field, evaluator caching, `list()` wrap, both tests added.
- Correctly pushed back on item #1, same as Model A — verified claim.
- Cleans up existing test `PositionScore` constructors to use `breakdown={}` instead of keeping the now-irrelevant `{"type_matchup": ...}` entries. Since `should_switch()` no longer reads from `breakdown`, this makes the test data more honest about what's actually being tested.

## Model B — Cons

- No significant issues — all requested changes are addressed or correctly reasoned about.

---

## Overall Preference Justification

Both models are extremely close this turn — they produce functionally identical changes for items 2 through 5, and both correctly pushed back on item 1, explaining that `paths.mods_folder` resolves to `mods/` and the YAML is already in the right directory (verified against the codebase). The `position_evaluator.py` changes are line-for-line the same: same `raw_type_matchup` field on `PositionScore`, same storage during `evaluate()`, same simplified `should_switch()`. The `manager.py` caching and `decision_strategy.py` `list()` fix are also identical. The only real difference is in the tests: Model B replaces the now-unused `breakdown` dict values with empty dicts in existing test constructors, which is slightly cleaner since `should_switch()` doesn't read `breakdown` anymore. Model A keeps the old values, which isn't wrong but is unnecessary noise. Both summaries accurately reflect what the diffs contain — no hallucinations detected.

---

## Axis Ratings

| Axis                          | Rating                          |
| ----------------------------- | ------------------------------- |
| Logic and Correctness         | 5 - Model B Minimally Preferred |
| Naming and Clarity            | 5 - Model B Minimally Preferred |
| Organization and Modularity   | 5 - Model B Minimally Preferred |
| Interface Design              | 5 - Model B Minimally Preferred |
| Error Handling and Robustness | 5 - Model B Minimally Preferred |
| Comments and Documentation    | 5 - Model B Minimally Preferred |
| Review / Production Readiness | 4 - Model A Minimally Preferred |

**Overall: 5 - Model B Minimally Preferred**

---

## Next Step / Follow-Up Prompt (Turn 3)

> now that the evaluator caching is in `AIManager.get_position_evaluator()`, there are two places where difficulty config loading happens: once in `AIManager.get_position_evaluator()` and once in `AI.__init__()` (which creates `self.position_evaluator`). these two code paths could produce different evaluator instances for the same character. consolidate: have `AI.__init__()` get its evaluator from the manager instead of constructing its own, or remove the one in `AI.__init__()` and always go through the manager. don't leave two separate construction paths.
>
> also, add a unit test for `get_position_evaluator()` on `AIManager` that verifies caching — calling it twice for the same character should return the same instance, and `clear_ai()` should invalidate the cache.
