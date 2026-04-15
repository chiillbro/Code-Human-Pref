677 passed (618 existing + 59 new validation tests), 0 failures. Here's what
  was fixed:                                                                  
                                                                                
  Fix 1 — Error labels now part of widget layout footprint

  - Added _validation_error_height field tracking the pixel height of error text
  - Added _compute_validation_error_height() to measure the rendered error text
  height
  - Added _update_validation_error_height() which adjusts _rect_size_delta by
  the difference between old/new error height and calls
  force_menu_surface_update() to trigger menu relayout
  - _draw_validation_errors() now draws within the expanded rect (positions at
  rect.bottom - error_height + gap) instead of blindly at rect.bottom + 2
  - Extracted _ensure_validation_error_font() to avoid duplicating font creation
   logic

  Fix 2 — Stricter email regex

  The new pattern rejects:
  - Domain labels starting/ending with hyphens (user@-domain.com,
  user@domain-.com)                                              
  - Consecutive dots in domain (user@domain..com) or local part
  (a..b@example.com)                                           
  - Dots at start/end of local part (.user@example.com, user.@example.com)
  - Domain starting with dot (user@.domain.com)                           
                                               
  Fix 3 — Removed dead code in Widget.validate()

  The elif self._validation_errors != errors branch was unreachable because it
  ran after self._validation_errors = errors, making the comparison always
  equal. Replaced with tracking had_errors before the assignment, and
  re-rendering when errors existed before (to clear them on a now-valid run).

  Fix 4 — clear_validators() now clears visual error state

  clear_validators() now calls clear_validation_errors(), which clears the error
   list, resets _validation_error_height, restores _rect_size_delta, invalidates
   the menu surface, and forces a re-render.

  New tests (8 added, 59 total)

  - test_validation_errors_increase_widget_rect_height — rect grows when errors
  appear
  - test_validation_errors_shrink_back_on_clear — rect shrinks on
  clear_validation_errors()
  - test_validation_errors_shrink_back_on_valid — rect shrinks when widget
  becomes valid
  - test_clear_validators_clears_error_state — clear_validators() removes errors
   and height
  - test_validation_error_height_hidden_when_show_disabled — no height change
  when show is off
  - test_multiple_errors_increase_height_more — two error lines taller than one
  - test_validation_error_height_updates_menu_surface — menu surface flagged for
   update
  - test_draw_after_validation_no_overlap — second widget top >= first widget
  bottom after render