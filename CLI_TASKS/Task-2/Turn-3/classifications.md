# Turn 3 — Classifications (Final Turn)

## Prompt (Source of Truth)

> that's a nice work I can say, just a couple of things to address, please don't set \_tooltip_delay_ms on the widget in \_configure_widget, because you are bypassing the public set_tooltip() api by doing widget.\_tooltip_delay_ms = self.\_theme.widget_tooltip_delay_ms which is a direct property mutating. instead always pass the theme delay through set_tooltip() when a tooltip is provided. next, please extract \_draw_tooltip into a helper or dedicated class, it is becoming somewhat tighter with long logic, pull the word wrap logic into a static method or utility function something like \_wrap_tooltip_text(font, text, max_width) -> List[str] and also pull the surface construction into a separate callable. finally, can you add a test for boundary flip positioning, currently, there is no test that verify the tooltip actually flips or clamps when the mouse is near the right edge or bottom edge. so, create a test to test this behavior

### Prompt Assessment

The prompt targets three specific improvements from Turn 2 Model B: (1) eliminate direct `_tooltip_delay_ms` mutation in `_configure_widget`, (2) extract `_draw_tooltip` into helpers, (3) add boundary-flip positioning tests. All three are concrete, well-scoped, and address real issues identified in Turn 2. No scope creep.

**What I'm evaluating**: Only the _new_ delta changes each model makes on top of Turn 2 Model B. Both models branch from that same baseline. Carried-over code (widget attributes, theme properties, cache key logic, mouseover tracking) is not re-judged.

---

## 1. Rationale Support (Pros & Cons)

### Model A Pros

- Correctly removes the direct `_tooltip_delay_ms` mutation: `_filter_widget_attributes` defaults `tooltip_delay_ms` to the theme value and `_configure_widget` only calls `set_tooltip()` when `kwargs['tooltip'] is not None`, routing the delay through the public API exactly as the prompt requested ("when a tooltip is provided").
- Extracts three clean helpers — `_wrap_tooltip_text` (static), `_build_tooltip_surface` (instance method with internal font loading), and `_position_tooltip` (static) — reducing `_draw_tooltip` from ~100 inline lines to a short orchestrator that calls cached helpers then blits.
- `_position_tooltip` uses tuple parameters (`mouse_pos`, `margin`, `box_size`, `surface_size`) which is a more Pythonic and self-documenting signature than individual scalars.
- Adds `test_tooltip_position_flip_clamp` covering 8 scenarios including normal placement, right/bottom/corner flips, extreme clamp cases, and a sweep across multiple cursor positions — all in one test method.

### Model A Cons

- Widgets without a tooltip kwarg keep the Widget constructor default of `_tooltip_delay_ms = 500` rather than inheriting the theme's delay, so a later `widget.set_tooltip('text')` call without an explicit delay silently uses 500ms instead of the theme value.
- The positioning test is a single monolithic method with 8 inline scenario blocks; if one assertion fails, the test name alone doesn't tell you _which_ scenario broke.
- `_build_tooltip_surface` is an instance method that internally accesses `self._theme` and manages font caching, coupling it to Menu state and making it harder to test in isolation.

### Model B Pros

- Also eliminates direct mutation — `_configure_widget` now always routes through `set_tooltip()`, passing the theme delay for all widgets. This ensures every widget inherits the theme `tooltip_delay_ms` consistently, so later programmatic `set_tooltip('text')` calls without an explicit delay automatically use the theme value.
- `_build_tooltip_surface` is a pure `@staticmethod` with all rendering parameters passed in explicitly, making it fully decoupled from Menu state and independently testable.
- Positioning clamp uses `max(0, min(tx, sw - box_w))` — a single expression that handles both underflow and overflow per axis, which is more concise and idiomatic than Model A's three sequential if-checks.
- Boundary-flip tests are split into 6 focused methods (`test_position_tooltip_normal`, `_flip_right_edge`, `_flip_bottom_edge`, `_flip_both_edges`, `_clamp_extreme_cases`, `_always_on_screen`), each with a descriptive name and docstring — a failing test immediately identifies the exact scenario.
- More comprehensive test file overall (423 lines vs 369).

### Model B Cons

- Calls `set_tooltip(text=None, delay_ms=theme_delay)` for _every_ widget, including those without tooltips, which is unnecessary overhead — the prompt said "when a tooltip is provided."
- `_position_tooltip` takes 8 individual scalar parameters instead of grouped tuples, making the signature verbose and harder to read at a glance compared to Model A's tuple-based approach.
- Imports `ColorType` at the top of `menu.py` but this is only used in the type signature of `_build_tooltip_surface` — a minor unused-style import in the Menu module's existing import block.

---

## 2. Axis Ratings & Preference

| Axis                          | Rating                          |
| ----------------------------- | ------------------------------- |
| Logic and correctness         | 3 - Tie                         |
| Naming and clarity            | 4 - Model A Minimally Preferred |
| Organization and modularity   | 6 - Model B Slightly Preferred  |
| Interface design              | 5 - Model B Minimally Preferred |
| Error handling and robustness | 3 - Tie                         |
| Comments and documentation    | 5 - Model B Minimally Preferred |
| Review/production readiness   | 6 - Model B Slightly Preferred  |

**Choose the final better answer:** **6 - Model B Slightly Preferred**

---

## 3. Overall Preference Justification

Both models address all three prompt items correctly — the direct `_tooltip_delay_ms` mutation is eliminated, `_draw_tooltip` is decomposed into extracted helpers, and boundary-flip positioning tests are added. The decisive difference is in test organization and modularity. Model B splits its positioning tests into 6 independently-named methods (`test_position_tooltip_flip_right_edge`, `_flip_bottom_edge`, etc.), each with a docstring, so a failure immediately identifies the broken scenario; Model A bundles all 8 scenarios into a single `test_tooltip_position_flip_clamp` method where diagnosing a failure requires reading through the body. Model B's `_build_tooltip_surface` as a pure static method is more decoupled and testable than Model A's instance method that internally reads theme state. Model B's positioning clamp (`max(0, min(tx, sw - box_w))`) is a single expression per axis versus Model A's three sequential if-checks — functionally identical but more concise. Model A does have a cleaner `_position_tooltip` interface using tuple params over Model B's 8 individual scalars, and more literally follows the prompt's "when a tooltip is provided" phrasing by skipping `set_tooltip()` for tooltip-less widgets. Overall, Model B's test granularity and helper decoupling make it the stronger choice for production readiness.

---

## 4. Task Completion Note

This is the 3rd turn, reaching the conversation threshold. The tooltip feature is now production-ready: the widget API (`set_tooltip`/`get_tooltip`/`get_mouseover_time`), 11 theme properties with full validation, word-wrapping, surface caching with smart invalidation, boundary-aware positioning with flip-and-clamp, widget manager kwargs integration, and comprehensive tests covering the full feature surface are all in place. Any remaining differences between the models are stylistic rather than functional. No follow-up prompt is needed.
