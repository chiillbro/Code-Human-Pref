 669 passed (618 existing + 51 new), 1 skipped, no failures. Here's a summary of everything
  that was implemented:                                                                   

  ---
  New file: pygame_menu/validation.py

  The core validation module with:

  - ValidationResult — holds valid bool, errors list, optional widget ref, first_error property,
   merge(), and truthiness via __bool__
  - Built-in validators (all subclass Validator):
    - Required — rejects empty strings, None, and unselected dropdowns (index -1)
    - MinLength(n) / MaxLength(n) — string length constraints
    - Pattern(regex) — regex fullmatch (accepts string or compiled re.Pattern)
    - MinValue(n) / MaxValue(n) — numeric range constraints
    - Email — basic email format check
    - Custom(func) — user-supplied callable
  - CrossValidator — takes 2+ widgets and a function that receives {widget_id: value} dict,
  returns ValidationResult
  - validate_widgets() — utility to validate a list of widgets

  Modified: pygame_menu/widgets/core/widget.py

  Widget-level validation API added to the Widget base class:

  - add_validator() / remove_validator() / clear_validators() / get_validators()
  - validate(stop_on_first=True) — runs validators sequentially, returns ValidationResult
  - is_valid() — quick boolean check
  - get_validation_errors() / clear_validation_errors()
  - set_validation_show_errors(bool) — toggle visual error display
  - Visual feedback: _draw_validation_border() draws a colored border, _draw_validation_errors()
   renders error text below the widget. Both integrate into the draw() pipeline and respect
  theme colors.

  Modified: pygame_menu/themes.py

  Three new theme properties:

  - widget_validation_error_color — error text color (default red)
  - widget_validation_error_border_color — error border color (default red)
  - widget_validation_error_font_size — error font size (0 = auto-derive from widget font)

  All are validated, color-formatted, and preserved through copy().

  Modified: pygame_menu/menu.py

  Menu-level form validation:

  - add_cross_validator() / remove_cross_validator()
  - validate_form(stop_on_first=True, recursive=False) — validates all widgets with validators +
   cross-validators, optionally recurses into submenus
  - is_form_valid(recursive=False) — quick boolean check

  Modified: pygame_menu/__init__.py

  Exports the new validation module.

  New file: test/test_validation.py

  51 tests covering all functionality: validator types, widget API, cross-validation, menu form
  validation, theme integration, visual drawing, edge cases, and error handling.