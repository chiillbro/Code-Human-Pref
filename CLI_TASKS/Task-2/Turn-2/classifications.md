# Turn 2 — Classifications

## Prompt (Source of Truth)

> hey good work there, few things to address, firstly, you're rebuilding the tooltip surface every frame in _draw_tooltip that's a font.render() call, a Surface create, a fill multiple blit's and draw.rect call happening 30 to 60 times per second for a normal and static text box, so please cache the rendered tooltip surface on the widget or menu level and only trigger it again when the tooltip text changes. there's no word wrapping logic implemented, please add this which will break text at word boundaries to fit in the limited widget_tooltip_max_width theme property (please have a default with some reasonable). next, right now, the horizontal overflow case just clamps to sw - box_w where the tooltip right now can sit on top of the cursor. flip it to the left of the cursor (like you do vertically) when it would overflow the right edge and clamp to 0 as a fallback so it never goes from the screen on any edge. and lastly, right now _filter_widget_attributes defaults tooltip to '' empty string, so set_tooltip(text='', ...) is getting called for every single widget even when no tooltip is wanted. change the default to None and skip the set_tooltip call in _configure_widget when it is None

### Prompt Assessment

The prompt addresses exactly 4 concrete issues from Turn 1's Model B output — no scope creep, no generic steering. It's prescriptive about *what* to fix and *how* (e.g., "cache on the widget," "flip to the left of the cursor," "default to None"). Good follow-up prompt.

**What I'm evaluating**: Only the *new* delta changes each model makes on top of Turn 1 Model B. Both models branch from that same baseline. Carried-over code from Turn 1 Model B is not re-judged.

---

## 1. Rationale Support (Pros & Cons)

### Model A Pros

- Implements surface caching correctly via `_tooltip_surface` and `_tooltip_surface_key` on the widget, with a comprehensive cache key tuple covering all theme attributes that affect the rendered box.
- Word-wrapping is implemented properly — splits on `\n` first, then wraps each paragraph at word boundaries using `font.size()` against `max_text_w`. Single words wider than the column pass through without breaking mid-word.
- Horizontal overflow now flips to the left of the cursor (`mx - ox - box_w`) before falling back to `tx = 0`, which is exactly what the prompt asked for.
- Changes the `_filter_widget_attributes` default to `None` and adds a conditional in `_configure_widget` that skips `set_tooltip` when both `tooltip` and `tooltip_delay_ms` are `None`. Also correctly inherits the theme delay when only text is provided.
- `set_tooltip()` invalidates the cache unconditionally (`_tooltip_surface = None`), ensuring any text change triggers a rebuild on the next draw.

### Model A Cons

- The `_configure_widget` guard is `if kwargs['tooltip'] is not None or kwargs['tooltip_delay_ms'] is not None` — this means passing *only* `tooltip_delay_ms=200` without a tooltip string still triggers `set_tooltip(text=None, delay_ms=200)`, which sets `_tooltip = ''` and wastefully invalidates the cache. It should only call `set_tooltip` when there's actual tooltip text.
- `_filter_widget_attributes` has a 5-line block comment explaining the None/delay logic that's verbose for what's essentially one conditional. The codebase style doesn't use comments this long for straightforward attribute handling.
- The `test_widget_tooltip_kwargs` test asserts `btn3._tooltip_delay_ms` is `500` (Widget default) rather than `750` (theme default), because `set_tooltip` is now skipped for `tooltip=None`. This is correct behavior after the fix but means widgets without tooltips won't automatically pick up the theme delay if a tooltip is set later via `set_tooltip('text')` without an explicit delay.
- `test_tooltip_surface_cache` tests that `set_tooltip` with the same text still invalidates the cache — because Model A does unconditional `_tooltip_surface = None` in `set_tooltip()` regardless of whether the text actually changed. This is a minor inefficiency the test exposes but doesn't flag.
- No test covering the case where a single word is wider than `max_text_w`, to verify it doesn't get stuck in an infinite loop or dropped.
- The `_draw_tooltip` method is now ~100 lines in `menu.py`, still inline rather than extracted. The prompt didn't ask for extraction, but it's getting unwieldy.

### Model B Pros

- Surface caching uses the same `_tooltip_surface` / `_tooltip_surface_key` pattern, and the cache key tuple is thorough — covers text, font, antialias, colors, border, padding, and max_width.
- `set_tooltip()` only invalidates the cache when the text actually changed (`if new_text != self._tooltip`), avoiding unnecessary rebuilds when `set_tooltip` is called with the same text.
- Word-wrapping logic handles the same cases as Model A — `\n` paragraph splits, word-boundary wrapping with `font.size()`, single-word passthrough. Adds `max(1, ...)` guards on `box_w` and `box_h` to prevent zero-size surfaces.
- Boundary positioning has a more thorough clamp — after flipping horizontally, it checks `if tx < 0: tx = 0`, *and then* `if tx + box_w > sw: tx = max(0, sw - box_w)`. Same pattern vertically. This handles the edge case where the tooltip is wider than the screen itself.
- `_configure_widget` sets `widget._tooltip_delay_ms = self._theme.widget_tooltip_delay_ms` for *every* widget before the conditional, so all widgets inherit the theme delay regardless of whether a tooltip is set. This means calling `widget.set_tooltip('text')` later without a delay will use the correct theme default.
- Tests include a cache invalidation test that verifies `set_tooltip` with the *same* text reuses the cache (testing the optimization), plus a test confirming theme attribute changes trigger a rebuild.

### Model B Cons

- The `_configure_widget` line `widget._tooltip_delay_ms = self._theme.widget_tooltip_delay_ms` directly mutates a private attribute instead of going through `set_tooltip()`. This bypasses the public API and creates two paths for setting the delay — one via `set_tooltip(delay_ms=...)` and one via direct assignment.
- The `_filter_widget_attributes` default is `None` (fixing the prompt's issue), but the assertion `tooltip is None or isinstance(tooltip, str)` allows empty strings to pass through. If someone passes `tooltip=''`, it'll call `set_tooltip(text='')` which sets `_tooltip = ''` — functionally harmless but doesn't reject clearly useless input.
- The `_draw_tooltip` method is also ~100 lines inline in `menu.py`, same as Model A.
- `test_tooltip_word_wrap` doesn't test explicit `\n` line breaks combined with word-wrapping — only tests long single-paragraph text and the `max_width=0` case.
- The word-wrap loop ends with `lines.append(cur)` outside the inner for-loop, which means an empty paragraph (`\n\n`) would still append a `''` line — this is fine behavior but is untested.
- No test for the boundary-flip positioning logic (e.g., tooltip near the right/bottom edge flipping to the other side).

---

## 2. Axis Ratings & Preference

| Axis                          | Rating |
|-------------------------------|--------|
| Logic and correctness         | 5 - Model B Minimally Preferred |
| Naming and clarity            | 5 - Model B Minimally Preferred |
| Organization and modularity   | 5 - Model B Minimally Preferred |
| Interface design              | 6 - Model B Slightly Preferred |
| Error handling and robustness | 6 - Model B Slightly Preferred |
| Comments and documentation    | 4 - Model A Minimally Preferred |
| Review/production readiness   | 6 - Model B Slightly Preferred |

**Choose the final better answer:** **6 - Model B Slightly Preferred**

---

## 3. Overall Preference Justification

Model B edges ahead on a few details that matter for correctness and robustness. Model B's `set_tooltip()` only invalidates the cache when text actually changed, while Model A unconditionally nulls the cache every call — a minor performance difference but it shows Model B is thinking about cache semantics more carefully. Model B's boundary positioning is more defensive: after the flip it re-checks both underflow *and* overflow on each axis, handling the edge case where the tooltip is wider/taller than the screen, whereas Model A only checks the flip direction and then clamps to 0 without a final overflow guard. Model B also ensures every widget inherits the theme `tooltip_delay_ms` unconditionally (even widgets without tooltips), so adding a tooltip later via the public API doesn't silently fall back to the hardcoded Widget constructor default of 500ms — Model A's skip-when-None approach leaves widgets that never passed a tooltip kwarg stuck on the Widget default rather than the theme value. On the downside, Model B sets the delay via direct attribute mutation instead of going through the public API, which isn't ideal, and Model A has somewhat better inline docstring comments in `_draw_tooltip`. Overall both responses address all four prompt items correctly with good test coverage, but Model B's cache invalidation logic and positioning fallbacks are a bit more production-solid.

---

## 4. Next Step / Follow-Up Prompt (Turn 3)

> Couple more things to tighten up:
> 
> 1. **Don't set `_tooltip_delay_ms` directly on the widget in `_configure_widget`** — you're bypassing the public `set_tooltip()` API by doing `widget._tooltip_delay_ms = self._theme.widget_tooltip_delay_ms` as a direct attribute mutation. Instead, always pass the theme delay through `set_tooltip()` when a tooltip text is provided. For widgets *without* a tooltip, the delay should just flow naturally from the theme when a tooltip is eventually added — you can do this by having `set_tooltip()` accept a `None` delay_ms that means "use whatever the menu's theme says" at draw time, rather than baking the value into the widget at configure time.
> 
> 2. **Extract `_draw_tooltip` into a helper or dedicated class** — it's now ~100 lines of surface building, word-wrapping, and positioning logic living inline inside `Menu`, a class that's already huge. Pull the word-wrap logic into a static method or utility function (something like `_wrap_tooltip_text(font, text, max_width) -> List[str]`), and pull the surface construction into a separate callable as well. The positioning + blit can stay in `_draw_tooltip` since it needs the surface and mouse state.
> 
> 3. **Add a test for boundary-flip positioning** — you have no test that verifies the tooltip actually flips or clamps when the mouse is near the right or bottom edge. Create a test that sets the mouse position near the screen edge (you can monkeypatch `pygame.mouse.get_pos`) and asserts the tooltip is blitted within surface bounds.
