All 44 tests pass.                   
                                                        
  Summary of fixes                     

  #: 1            
  Change: Left mods/ai_difficulty.yaml in place.
    paths.mods_folder resolves to mods/ (verified),
    matching the other ai_*.yaml files there. Moving it
    to mods/tuxemon/ would break loading — let me know
  if
     you still want it moved.
  ────────────────────────────────────────
  #: 2
  Change: PositionScore gained raw_type_matchup;
    evaluate() stores the unweighted
    best_matchup_against() result there; should_switch()

    now reads current.raw_type_matchup directly instead
    of dividing by the weight.
  ────────────────────────────────────────
  #: 3
  Change: AIManager now caches a PositionEvaluator per
    character in self.position_evaluators via    get_position_evaluator();
    choose_replacement_monster() reuses it and
  clear_ai()
     clears the cache.
  ────────────────────────────────────────
  #: 4
  Change: try_switch() now wraps opponent_bench in
    list(...).
  ────────────────────────────────────────
  #: 5
  Change: Added test_same_type_matchup_is_neutral (fire
    vs fire → total 0) and
    test_should_switch_false_when_all_candidates_worse
    (bad position but bench has no better matchup → no
    switch). Updated existing should_switch tests to
    populate raw_type_matchup.