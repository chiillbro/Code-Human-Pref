All 45 tests pass.                   
                                                        
  Changes:                             
  - AI.__init__() now takes position_evaluator: 
  PositionEvaluator as a required arg and no longer
  loads the difficulty config or builds its own    
  evaluator. PositionEvaluator import moved under
  TYPE_CHECKING.                                 
  - AIManager.process_ai_turn() passes
  self.get_position_evaluator(character) into AI(...),
  so get_position_evaluator() is the single construction
   path — both the replacement strategy and the         
  per-monster AI instances share the exact same cached
  evaluator per NPC.                                  
  - Added test_ai_manager_caches_position_evaluator:
  same character → same instance, different character →
  different instance, clear_ai() → new instance on next
  call.