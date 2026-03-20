# Task-4: Widget Validation Framework with Visual Feedback

## Task ID
Task-04

## Type
Substantial New Feature

## Core Request (Turn 1)

### Summary
Implement a comprehensive **widget validation framework** with visual feedback for pygame-menu. Currently, there is no built-in way to validate form inputs — if a user enters an invalid email in a `TextInput`, selects an incompatible combination of options, or leaves a required field empty, the application has no standardized mechanism to check, report, or visually indicate the problem. This task requires building a composable validator system, a validation runner that evaluates validators against widget values, per-widget error state rendering (colored borders, error message labels), cross-widget validation support, and a form-level `validate_all()` API.

### Detailed Requirements

1. **Validator Primitives (`pygame_menu/validators.py` — new file):**
   - Create an abstract `Validator` base class with:
     - `validate(value: Any) -> ValidationResult` — the core method.
     - `message: str` — the error message to display on failure.
   - Create a `ValidationResult` dataclass: `is_valid: bool`, `error_message: str | None`, `validator_name: str`.
   - Implement the following concrete validators:
     - `Required()` — fails if value is `None`, empty string, or empty list.
     - `MinLength(min_len: int)` — fails if `len(value) < min_len`.
     - `MaxLength(max_len: int)` — fails if `len(value) > max_len`.
     - `Pattern(regex: str, message: str)` — fails if value doesn't match the regex pattern.
     - `Email()` — validates basic email format (must use a reasonable regex, not a trivial one).
     - `NumberRange(min_val: float | None, max_val: float | None)` — fails if numeric value is outside range.
     - `Custom(fn: Callable[[Any], bool], message: str)` — wraps an arbitrary predicate function.
     - `OneOf(choices: list)` — fails if value is not in the allowed choices list.
   - All validators must be composable: a widget can have multiple validators, and they are evaluated in order. Validation stops at the first failure (short-circuit) by default, but a `validate_all=True` mode should collect all errors.

2. **Widget Validation Integration (`pygame_menu/widgets/core/widget.py`):**
   - Add methods to the base `Widget` class:
     - `add_validator(validator: Validator) -> Widget` — adds a validator; returns `self` for chaining.
     - `remove_validator(validator: Validator)`.
     - `clear_validators()`.
     - `validate() -> list[ValidationResult]` — runs all validators against `self.get_value()` and returns the results.
     - `is_valid() -> bool` — convenience method; returns `True` if `validate()` produces no errors.
     - `set_required(message: str = "This field is required") -> Widget` — shorthand for adding `Required()`.
   - Add internal state:
     - `_validation_errors: list[ValidationResult]` — stores the latest validation results.
     - `_show_validation: bool` — whether to visually display the validation state.
   - Validation must be triggered:
     - Explicitly via `widget.validate()`.
     - Automatically on blur (when the widget loses focus) if `auto_validate_on_blur=True` (default `True`).
     - Automatically on value change if `auto_validate_on_change=True` (default `False`).

3. **Visual Error Feedback (`pygame_menu/widgets/core/widget.py`):**
   - When `_validation_errors` is non-empty and `_show_validation` is `True`:
     - Change the widget's border color to an error color (default: red `(220, 53, 69)`).
     - Change the widget's border width to `2` (or the theme's error border width).
     - Render the first error message as a small label directly below the widget, in the error color, using a smaller font size (e.g., theme font size - 4, minimum 10px).
   - When the widget is valid after validation has been shown:
     - Change the border color to a success color (default: green `(40, 167, 69)`) briefly, then revert to the theme default.
     - Remove the error message label.
   - The error label must be part of the widget's layout — it must push subsequent widgets downward, not overlap them.

4. **Cross-Widget Validation (`pygame_menu/validators.py`):**
   - Create a `CrossWidgetValidator` class that validates relationships between multiple widgets:
     - `CrossWidgetValidator(widgets: list[Widget], fn: Callable[[list[Any]], bool], message: str, target_widget: Widget)` — `fn` receives the list of values from `widgets`, and if it returns `False`, the error is attached to `target_widget`.
   - Example use case: "Confirm password must match password" — the cross-validator references both the password and confirm-password widgets.

5. **Menu Form Validation (`pygame_menu/menu.py`):**
   - Add `Menu.validate() -> dict[str, list[ValidationResult]]` — validates all widgets that have validators. Returns a dict mapping widget IDs to their validation results. Only includes widgets that have at least one error.
   - Add `Menu.is_valid() -> bool` — returns `True` if all widgets pass validation.
   - Add `Menu.set_on_validate(callback: Callable[[dict], None])` — registers a callback that fires after `Menu.validate()` is called, receiving the results dict.
   - Add `Menu.reset_validation()` — clears all validation errors and visual feedback from all widgets.
   - Cross-widget validators must be evaluated in `Menu.validate()` after individual widget validators.

6. **Theme Integration (`pygame_menu/themes.py`):**
   - Add theme attributes:
     - `widget_validation_error_color`: default `(220, 53, 69)`.
     - `widget_validation_success_color`: default `(40, 167, 69)`.
     - `widget_validation_error_border_width`: default `2`.
     - `widget_validation_error_font_size_delta`: default `-4` (relative to widget font size).
     - `widget_validation_error_font_color`: default `(220, 53, 69)`.
     - `widget_validation_auto_validate_on_blur`: default `True`.

7. **WidgetManager Convenience (`pygame_menu/_widgetmanager.py`):**
   - Extend `_filter_widget_attributes()` to accept `validators` (list of Validator instances) and `required` (bool) kwargs, so users can write: `menu.add.text_input("Email:", validators=[Email()], required=True)`.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws to Critique

1. **Error label overlaps next widget:** The model will likely render the error label by drawing text at a fixed offset below the widget, but it won't increase the widget's reported height or adjust the menu's layout, causing the error text to overlap the next widget. Demand that the widget's bounding rect grows to include the error label, and that `Menu._update_widget_position()` is called to re-layout.
2. **`validate()` called during rendering causes loop:** If auto-validate-on-change triggers validation, which changes the border, which triggers a re-render, which queries the widget state, the model may introduce an infinite update loop. Demand that validation state changes are batched and applied outside the render cycle.
3. **`get_value()` not implemented on all widgets:** The base `Widget.get_value()` raises `ValueError` for widgets that don't have a meaningful value (e.g., `Button`, `Image`). The model will call `validate()` on these and crash. Demand a guard that only evaluates validators on widgets that have a `get_value()` implementation.
4. **Cross-widget validator evaluated before individual validators:** If Widget A has a `Required()` validator and is empty, and the cross-validator references Widget A's value, it will receive `""` as input. The model will likely not enforce the ordering (individual first, then cross). Demand explicit ordering.
5. **Email regex too simplistic:** The model will use something like `r'.*@.*'` or `r'.+@.+\..+'` which accepts clearly invalid emails. Demand a reasonably robust regex (RFC 5322 simplified) without being overly strict.
6. **Success color stays forever:** The model will set the border to green on valid but never revert it back to the theme default. Demand a timed revert (e.g., 1.5 seconds) or revert on next interaction.
7. **`clear_validators()` doesn't clear visual state:** After clearing validators, the error border and message may persist visually. Demand that `clear_validators()` also resets the visual state.

### Turn 3 — Tests, Linting & Polish

1. **Unit tests for every validator:** `Required` (None, "", [], "valid"), `MinLength` / `MaxLength`, `Pattern`, `Email` (valid/invalid addresses), `NumberRange` (boundary values, None min/max), `Custom`, `OneOf`.
2. **Unit tests for short-circuit vs. collect-all mode.**
3. **Unit tests for `ValidationResult` data class.**
4. **Integration test for auto-validate-on-blur:** Simulate focus → type invalid → blur → assert error border.
5. **Integration test for cross-widget validation:** Two TextInputs, cross-validator checking equality, validate → assert error on target widget.
6. **Integration test for `Menu.validate()`:** Multiple widgets with validators, verify returned dict structure.
7. **Integration test for `Menu.reset_validation()`:** Verify all visual state cleared.
8. **Test `required=True` kwarg via `menu.add.text_input(..., required=True)`.**
9. **Full linting pass and type annotations.**

---

## Why It Fits the Constraint

- **~580–650 lines of new core code:** `validators.py` (~250 lines: base class, ValidationResult, 8 concrete validators, CrossWidgetValidator, composability logic), `widget.py` modifications (~150 lines: add_validator, remove_validator, validate, is_valid, set_required, auto-validate on blur/change hooks, visual error state, error label rendering, _validation_errors management), `menu.py` modifications (~80 lines: validate, is_valid, set_on_validate, reset_validation, cross-widget evaluation, layout re-trigger), `themes.py` (~40 lines: 6 new validation theme attributes + validation), `_widgetmanager.py` (~30 lines: validators/required kwargs), `__init__.py` (~10 lines: exports).
- **High difficulty:** The visual error label that adjusts layout is the crux — it requires understanding how `Menu._update_widget_position()` works and how widget heights feed into the layout engine. Auto-validation on blur requires hooking into the focus/unfocus lifecycle. Cross-widget validation adds another layer of ordering complexity. The model must also handle widgets that don't support `get_value()`.
- **Natural Turn 2 material:** Layout overlap, render loops, `get_value()` crashes on non-input widgets, email regex quality, and visual state cleanup are all classic first-implementation failures.

---

## Potential Files Modified

| # | File Path | Change Type |
|---|---|---|
| 1 | `pygame_menu/validators.py` | **New file** — Validator base, 8 concrete validators, CrossWidgetValidator, ValidationResult |
| 2 | `pygame_menu/widgets/core/widget.py` | Modify — validation methods, visual error rendering, auto-validate hooks |
| 3 | `pygame_menu/menu.py` | Modify — Menu.validate(), is_valid(), cross-widget eval, layout re-trigger |
| 4 | `pygame_menu/themes.py` | Modify — validation theme attributes |
| 5 | `pygame_menu/_widgetmanager.py` | Modify — validators/required kwargs |
| 6 | `pygame_menu/__init__.py` | Modify — export validators module |

---

## PR Overview (Turn 1 Implementation)

### Summary
Implemented a comprehensive Widget Validation Framework with Visual Feedback for pygame-menu. The framework provides composable validators, per-widget validation state with visual error rendering, cross-widget validation, form-level validation API, theme integration, and WidgetManager convenience kwargs.

### Files Changed

| # | File | Change Type | Lines | Description |
|---|------|------------|-------|-------------|
| 1 | `pygame_menu/validators.py` | **New** | ~265 | Validator ABC, ValidationResult dataclass, 8 concrete validators (Required, MinLength, MaxLength, Pattern, Email, NumberRange, Custom, OneOf), CrossWidgetValidator, `run_validators()` with short-circuit/collect-all modes |
| 2 | `pygame_menu/widgets/core/widget.py` | Modified | ~165 added | Validation state attrs (`_validators`, `_validation_errors`, `_show_validation`, `_auto_validate_on_blur/change`, `_original_border_*`), methods (`add_validator`, `remove_validator`, `clear_validators`, `validate`, `is_valid`, `set_required`, `reset_validation`, `get_validation_errors`, `_has_value`, `_apply_validation_visual`, `_reset_validation_visual`, `_build_error_surface`), auto-validate-on-blur in `_blur()`, auto-validate-on-change in `change()`, error label drawing in `draw()`, `_rect_size_delta` expansion for error label layout |
| 3 | `pygame_menu/menu.py` | Modified | ~70 added | `_cross_validators` and `_on_validate` init attrs, `add_cross_validator()`, `validate()`, `is_valid()`, `set_on_validate()`, `reset_validation()` — cross-widget validators evaluated after individual validators |
| 4 | `pygame_menu/themes.py` | Modified | ~30 added | 6 validation theme attributes with type annotations, `__init__` kwargs, `validate()` assertions: `widget_validation_error_color`, `widget_validation_success_color`, `widget_validation_error_border_width`, `widget_validation_error_font_size_delta`, `widget_validation_error_font_color`, `widget_validation_auto_validate_on_blur` |
| 5 | `pygame_menu/_widgetmanager.py` | Modified | ~20 added | Pop `validators` and `required` kwargs in `_filter_widget_attributes()`, apply validators and auto-validate-on-blur from theme in `_configure_widget()` |
| 6 | `pygame_menu/__init__.py` | Modified | ~2 changed | Added `validators` to conditional imports and `__all__` |
| 7 | `pygame_menu/widgets/widget/textinput.py` | Modified | ~1 added | `super()._blur()` call so validation hooks fire on TextInput blur |
| 8 | `pygame_menu/widgets/widget/rangeslider.py` | Modified | ~1 added | `super()._blur()` call so validation hooks fire on RangeSlider blur |
| 9 | `test/test_validators.py` | **New** | ~460 | 85 tests covering all validators, short-circuit/collect-all, ValidationResult, widget validation API, visual feedback (border colors, error surface, rect expansion), auto-validate-on-blur/change, CrossWidgetValidator, Menu form validation (validate, is_valid, reset_validation, set_on_validate, cross-validators), WidgetManager kwargs, theme attributes |

### Key Design Decisions

1. **Guard for non-input widgets**: `widget.validate()` catches `ValueError` from `get_value()` and silently returns no errors, preventing crashes on Button/Image/Label widgets.

2. **Error label layout via `_rect_size_delta`**: Rather than modifying the complex `get_rect()` method, the error label height is added to `_rect_size_delta[1]`, which is already factored into the widget's reported height. This causes `_update_widget_position()` to push subsequent widgets down naturally.

3. **`super()._blur()` in subclasses**: TextInput and RangeSlider override `_blur()` without calling super. Added `super()._blur()` calls so validation hooks fire correctly.

4. **Email regex (RFC 5322 simplified)**: Uses a multi-part compiled regex that validates local part (allows `.`, `+`, special chars), `@`, and requires at least two domain labels with proper character restrictions.

5. **Cross-widget validation ordering**: `Menu.validate()` runs individual widget validators first, then cross-widget validators, ensuring individual errors are present before cross-widget checks access values.

6. **Theme-driven defaults**: `widget_validation_auto_validate_on_blur` is applied from the theme in `_configure_widget()`, giving applications consistent validation behavior via theming.

### Test Results

- **85 new tests** — all passing
- **738 existing tests** — all passing (1 skipped, 0 failures)
- **Ruff lint** — clean, no errors
