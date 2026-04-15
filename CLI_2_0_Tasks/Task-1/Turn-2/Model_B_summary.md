 All 71 validation tests pass. Here's a summary of what changed to
  address each issue:                                                
                                                                         
  Issue 1: Error label now contributes to widget layout footprint

  - Added _validation_footprint_height and pre-rendered
  _validation_error_surfaces on the widget
  - Added get_layout_rect() that returns the body rect extended by the
  error footprint; get_rect() still returns the body rect by default so
  background/border/selection-effects don't get extended
  - Added include_validation_footprint kwarg on get_rect() (default
  False) for explicit layout callers
  - menu.py layout code (_build_widget_surface) switched to
  widget.get_layout_rect(render=True) so subsequent widgets get
  positioned below the error text
  - _set_validation_errors() sets self._menu._widgets_surface_need_update
   = True to trigger a re-layout when the error state actually changes
  (pygame_menu/widgets/core/widget.py:1504)

  Issue 2: Stricter email regex

  - New regex: local part is one or more [A-Za-z0-9_%+\-]+ runs joined by
   single dots; each domain label starts and ends with alphanumeric with
  optional hyphens in the middle; TLD is 2+ ASCII letters
  - Rejects user@-domain.com, user@domain-.com, user@a..b.com,
  a..b@example.com, leading/trailing dots anywhere
  - pygame_menu/validation.py:263

  Issue 3: Dead code removed

  - Replaced validate()'s unreachable elif with a new
  _set_validation_errors() helper that compares against the previous
  state (before overwriting) and only forces render + invalidates the
  menu surface when state actually changes
  (pygame_menu/widgets/core/widget.py:1504)

  Issue 4: clear_validators() clears visual state

  - clear_validators() now calls _set_validation_errors([]) when errors
  are present, which drops stored errors, the rendered error surfaces,
  and the footprint height, and invalidates the menu layout so the space
  is reclaimed (pygame_menu/widgets/core/widget.py:1459)
  - clear_validation_errors() and set_validation_show_errors() also go
  through the same helper so all paths behave consistently

  New tests (20 added, 71 total)

  - Email strictness: 7 tests covering leading/trailing hyphen,
  consecutive dots (domain + local), leading/trailing dots, valid
  hyphenated labels, classic valid forms
  - clear_validators visual cleanup: 3 tests
  - Layout footprint: get_layout_rect vs get_rect, next-widget pushdown,
  error resolution reclaiming space, surface-invalidation flag,
  multi-error footprint growth, hide-errors footprint, clear-errors
  footprint
  - State-change detection regression test for the removed dead code

  Final state: 689 tests pass (618 pre-existing + 71 validation). The
  only failures in the repo are 19 pre-existing sound tests failing due
  to no audio device in this environment, unrelated to these changes.