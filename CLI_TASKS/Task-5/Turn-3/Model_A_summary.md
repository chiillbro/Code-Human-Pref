All 45 tests pass. Changes:         
                                                        
  - tuxemon/ai/ai.py — AI.__init__() now takes          
  position_evaluator: PositionEvaluator as a parameter
  and assigns it directly. Removed the duplicated
  difficulty-config loading and local PositionEvaluator
  construction.
  - tuxemon/ai/manager.py — process_ai_turn() passes
  self.get_position_evaluator(character) into AI(...),
  so every AI instance for a given character shares the
  same cached evaluator with
  choose_replacement_monster().
  - tests/tuxemon/test_ai_position_evaluator.py — added
  test_manager_caches_position_evaluator: two calls for
  the same character return the identical instance
  (config loader called once), a different character
  gets a distinct instance, and clear_ai() invalidates
  the cache so a subsequent call builds a fresh one.

  Single construction path now lives in
  AIManager.get_position_evaluator().