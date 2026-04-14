# Turn 2 — Classifications

## Prompt Summary

The prompt (coming from the user as a PR reviewer on top of Turn 1's Model A) asks for 5 specific fixes:

1. Render the first error message as a small text label below the widget (the invalid state currently only changes border color).
2. Make the error label adjust layout by growing the widget's reported height via `_rect_size_delta`, then invalidate the menu surface so `_update_widget_position()` re-layouts.
3. After invalid-to-valid transition, briefly show a success color (green `(40, 167, 69)`) on the border. Add `widget_validation_success_color` to the theme.
4. Add `super()._blur()` to `TextInput._blur()` and `RangeSlider._blur()`, add `_auto_validate_on_blur` flag on the widget, and call `self.validate()` in the base `_blur()` when the flag is True and the widget has validators.
5. `clear_validators()` must also reset visual state (call `_clear_validation_state()` or equivalent to restore original border and remove error surface).

**Both trajectories branch from Turn 1's Model A.**

---

## Model A — Pros

- **Error label properly adjusts layout with independent delta tracking.** Uses a dedicated `_validation_rect_delta` field to track how much height the error label added, then adds/subtracts it from `_rect_size_delta[1]` on build/teardown. This is safe against clobbering other delta contributors. The test `test_error_label_no_overlap_in_layout` actually verifies end-to-end that the bottom widget's rect.y moves down and `top.get_rect().bottom <= bottom.get_rect().top` — no other test in either model checks this critical layout invariant.
- **Border rect compensates for error label height.** In `_draw_border()`, the rect height is reduced by `_validation_rect_delta` so the validation border wraps only the widget body, not the error label below it. Without this, the red/green border would stretch to encompass the error text, which would look visually wrong.
- **Untouched-widget guard via `__touched__` attribute.** `_focus()` sets a `__touched__` attribute, and `_blur()` only auto-validates if touched. This prevents widgets that were never interacted with by the user from being flagged invalid on creation (when `_append_widget()` calls `select(False)`). A dedicated test (`test_auto_validate_untouched_widget_skipped`) verifies this.
- **Success border one-shot via `_force_render()` works predictably.** The "armed" flag mechanism in `_force_render()` ensures the green border shows for exactly one render cycle then auto-reverts. Test coverage confirms no false success flash on first validation of an already-valid widget (`test_success_border_skipped_on_clean_first_pass`).

## Model A — Cons

- **Modifying `_force_render()` is invasive.** `_force_render()` is a core rendering method called from many places across the codebase. Adding success-border side-effect logic here means any call to `_force_render()` (from theme changes, font updates, etc.) will also clear the success border. This makes the success flash very short-lived — potentially too short for the user to notice.
- **Fixed error font size (14px).** `_validation_error_font_size` defaults to 14 regardless of the widget's font size. On a widget with font size 40, the error label will look tiny; on a widget with font size 12, the 14px error text could be larger than the widget text itself. A proportional approach would adapt better.
- **`_focus()` adds `super()._focus()` to subclasses beyond what was asked.** The prompt specifically mentioned `_blur()` needs `super()` calls. Model A also adds `super()._focus()` to both `TextInput` and `RangeSlider` — this is needed for the `__touched__` mechanism but is a change the prompt didn't request, and could have unintended side-effects if the base `_focus()` gains more logic later.

---

## Model B — Pros

- **Proportional error font size.** Uses `max(int(self._font_size * 0.7), 8)` — the error text scales with the widget's own font size, so it looks proportionally correct on both small and large widgets. This is a better design than a fixed pixel value.
- **Success border cleared on `_blur()` — cleaner trigger.** The green border persists until focus moves away from the widget, which gives the user visible time to actually see the success indicator. The prompt said "the revert can happen on the next `_force_render()` or on next interaction" — clearing on blur is a natural "next interaction" trigger.
- **Uses existing `_selection_time > 0` as auto-validate guard.** Instead of introducing a new `__touched__` attribute, Model B checks `self._selection_time > 0` to determine if the widget was ever focused by the user. This leverages existing widget infrastructure without adding new state.

## Model B — Cons

- **`_rect_size_delta[1]` set to absolute values, not additive.** `_rebuild_error_surface()` sets `_rect_size_delta = (x, error_h)` and `_clear_validation_state()` sets it to `(x, 0)`. If any other feature or future code also contributes to `_rect_size_delta[1]`, this approach wipes it out. Model A's additive approach is defensively safer.
- **No end-to-end layout overlap test.** Model B has tests for error surface creation, height delta changes, and draw-without-crash, but doesn't actually verify that the next widget in the menu is pushed down when an error label appears. This is the entire point of the layout fix — without a test asserting `y_after > y_before`, there's no proof the layout integration works.
- **`_draw_validation_errors()` called from inside `_draw_border()`.** The error text rendering is invoked from within the invalid-border branch of `_draw_border()`, mixing border drawing with text rendering responsibilities. Model A keeps them separate — `_draw_validation_errors()` is called from `draw()` after `_draw_border()`. The separation of concerns is cleaner.

---

## Overall Preference Justification

Both models successfully address all five items from the prompt — error text rendering, layout adjustment, success color, `super()._blur()` calls, and `clear_validators()` cleanup. The key differentiator is layout correctness: Model A tracks the error label's height independently via `_validation_rect_delta` and adds/subtracts it from `_rect_size_delta[1]`, which is safer than Model B's absolute assignment that could clobber other delta contributors. Model A also compensates the border rect height so the validation border wraps only the widget body (not the error label), and includes a `test_error_label_no_overlap_in_layout` test that actually verifies widgets don't overlap — Model B lacks this critical integration test. On the other hand, Model B has a better proportional error font size (`0.7 * widget_font_size` vs. fixed 14px) and a cleaner auto-validate guard using existing `_selection_time` instead of introducing a new `__touched__` attribute. Model A's modification of `_force_render()` for the success one-shot is more invasive than Model B's blur-based revert. Overall, Model A's stronger layout handling and test coverage for the most important fix in this turn give it a slight edge.

---

## Axis Ratings

| Axis                          | Rating                          |
| ----------------------------- | ------------------------------- |
| Logic and correctness         | 3 - Model A Slightly Preferred  |
| Naming and clarity            | 5 - Model B Minimally Preferred |
| Organization and modularity   | 4 - Model A Minimally Preferred |
| Interface design              | 4 - Model A Minimally Preferred |
| Error handling and robustness | 3 - Model A Slightly Preferred  |
| Comments and documentation    | 4 - Model A Minimally Preferred |
| Review/production readiness   | 3 - Model A Slightly Preferred  |

**Overall Preference: 3 - Model A Slightly Preferred**

---

## Next Step / Follow-Up Prompt (Turn 3)

> Good progress, a few more things to tighten up:
>
> 1. **Error font size should be proportional, not fixed.** The error label font size is hardcoded at 14px. Change `_validation_error_font_size` to be computed as `max(self._font_size - 4, 10)` at render time (in `_build_validation_error_surface`), matching the prompt's original requirement of "font size - 4, minimum 10px". Remove the fixed `14` default. Add a `widget_validation_error_font_size_delta` attribute to the theme (default `-4`) so users can configure the offset.
> 2. **Unit tests for every validator primitive need boundary coverage.** Add tests for: `Required` with `0` and `False` (both are non-empty and should pass), `MinLength(0)` edge case (everything should pass), `MaxLength(0)` (only empty passes), `NumberRange` with string coercion (e.g., `"7"` should validate as numeric), `Email` with edge cases like `user+tag@sub.domain.co` (valid) and `@no-local.com` (invalid) and `missing@tld` (invalid). Also test short-circuit mode: two failing validators with fail-fast should return exactly 1 error; collect-all should return 2.
> 3. **The `_force_render()` side-effect for success border is fragile.** Any unrelated call to `_force_render()` (theme change, font update, resize) will clear the success border before the user sees it. Move the success-border revert into `_blur()` instead — when the widget loses focus, clear `_validation_show_success` and `_force_render()`. Remove the armed/one-shot logic from `_force_render()` entirely so it stays clean.
> 4. **Run the full test suite** (`python -m pytest test/test_validation.py -v`) and fix any failures. Then run `ruff check pygame_menu/validation.py pygame_menu/widgets/core/widget.py pygame_menu/menu.py` and fix any lint issues.
