All 44 tests pass. Summary of follow-up changes:
                                                        
  1. YAML location — left at mods/ai_difficulty.yaml.   
  paths.mods_folder resolves to mods/ (verified at
  runtime), and the existing ai_opponent.yaml /
  ai_items.yaml / ai_trainers.yaml / ai_techniques.yaml
  all live there and load via the same code path. Happy
  to move it if you still prefer mods/tuxemon/.
  2. should_switch() raw matchup — PositionScore gained
  raw_type_matchup, populated directly in evaluate().
  should_switch() now reads current.raw_type_matchup
  instead of dividing the weighted breakdown value.
  (position_evaluator.py:37, :99–101, :120, :155)
  3. Evaluator caching — AIManager now has
  position_evaluators: dict[NPC, PositionEvaluator] and
  a get_position_evaluator(character) helper that builds
   once per NPC and is cleared in clear_ai().
  choose_replacement_monster() uses the cached instance.
   (manager.py:25, :27–40, :65, :118)
  4. opponent_bench materialised — wrapped in list() in
  try_switch(). (decision_strategy.py:188)
  5. New tests — added
  test_evaluate_type_matchup_same_type_is_neutral (fire
  vs fire → offensive == defensive → total 0) and
  test_should_switch_rejects_worse_bench_candidates
  (position below threshold but all bench options have
  worse matchup → no switch). Updated existing
  should_switch tests to use raw_type_matchup.