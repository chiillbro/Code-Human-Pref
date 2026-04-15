All 80 TextInput and RangeSlider tests pass. The super()._blur() additions are
   safe.                                                                      
                 
  Summary

  Auto-validation on blur:

  - Base Widget._blur() now triggers validate() when _should_validate_on_blur()
  returns True and the widget has at least one validator
  (pygame_menu/widgets/core/widget.py:3147). Docstring explicitly warns
  subclasses to call super()._blur().
  - New method _should_validate_on_blur() resolves precedence: explicit
  per-widget value wins; otherwise fall back to the menu's default; otherwise
  False when no menu is attached (pygame_menu/widgets/core/widget.py:1555).

  Per-widget setting (tri-state):

  - Widget.set_validation_on_blur(enabled) accepts True, False, or None
  (inherit). Widget.get_validation_on_blur() returns the raw per-widget setting.
   None means "use the menu's default"
  (pygame_menu/widgets/core/widget.py:1527).

  Menu-level setting:

  - Menu.set_validation_on_blur(enabled) / Menu.get_validation_on_blur()
  (pygame_menu/menu.py:3398). Defaults to False so existing behavior is
  unchanged — opt-in only. Menu widgets that leave their own value at None
  inherit this.

  Subclass fixes:

  - TextInput._blur() now calls super()._blur() after its own cleanup
  (pygame_menu/widgets/widget/textinput.py:1318). Adding super() last so
  auto-validation sees the final reset state (cursor hidden, selection cleared).
  - RangeSlider._blur() now calls super()._blur()
  (pygame_menu/widgets/widget/rangeslider.py:1088).

  New tests (18 added, 89 total in test_validation.py):

  - Default off behavior, per-widget enable/disable, passing value clears
  errors, tri-state None inheritance, type-rejection for invalid inputs
  - Menu-level affects inheriting widgets, explicit per-widget overrides menu
  default
  - Regression tests for both TextInput._blur() and RangeSlider._blur() calling
  super()._blur() — these would fail if either subclass dropped the super() call
  - No-validators is a no-op on blur
  - Detached widget (no menu) doesn't crash and respects explicit per-widget
  setting
  - Blur-triggered validation updates visual state (footprint grows)

  Full test results: 707 passed, 1 skipped (same pre-existing sound
  test-environment failures excluded). No regressions in existing behavior.