 706 passed (618 pre-existing + 88 validation). Zero regressions. Here's
   what changed:                                                     
                          
  ---
  Auto-validation on blur
                         
  pygame_menu/widgets/core/widget.py — base Widget._blur() now calls
  self.validate() when auto-validation is enabled and the widget has
  validators. The decision logic lives in _should_validate_on_blur():
  - If the widget's _validation_on_blur is True or False, that wins
  - If it's None (the default), the widget inherits the menu's     
  _validation_on_blur flag                                    
  - If there's no menu, it defaults to False
                                            
  New public API:
  - widget.set_validation_on_blur(True / False / None) — per-widget
  control; None means inherit from menu

  Subclass _blur() fixes

  pygame_menu/widgets/widget/textinput.py — TextInput._blur() now calls
  super()._blur() first, then runs its own cleanup (cursor hidden, mouse
  state reset, text unselected).

  pygame_menu/widgets/widget/rangeslider.py — RangeSlider._blur() now
  calls super()._blur() first, then resets _selected_mouse.

  Menu-level validation-on-blur setting

  pygame_menu/menu.py — New _validation_on_blur attribute (default False)
   and public setter:
  - menu.set_validation_on_blur(True) — all widgets with
  _validation_on_blur=None (inherit) will auto-validate on blur

  Tests (17 new, 88 total)

  - Default behavior: widgets don't auto-validate on blur
  - Per-widget enable/disable/inherit
  - Menu-level enable, widget override in both directions
  - No validators → no error on blur
  - Valid value on blur clears previous errors
  - TextInput._blur() fires validation AND still runs its own cleanup
  - RangeSlider._blur() fires validation AND still resets _selected_mouse
  - Selector and ToggleSwitch (inheriting base _blur()) work correctly
  - Sequential blur across multiple widgets
  - Drawing after blur-triggered validation doesn't crash