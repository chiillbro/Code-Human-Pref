Core Request:
Add a form validation framework to pygame-menu. Right now, there's no built-in way to validate user input across multiple TextInput widgets and show inline error messages. Implement a FormValidator class that can be attached to a Menu. Developers register validation rules for specific TextInput widgets — required(), min_length(n), max_length(n), regex(pattern, message), and custom(callable, message). When form.validate() is called (or on each value change if live_validation=True is set), the validator checks all registered rules and, for any failures, renders a small red error label directly below the failing widget. The error label should use a configurable font size and color from theme properties (widget_error_font_color, widget_error_font_size). validate() returns a dict mapping widget IDs to error message lists, or an empty dict if all pass.
Add a menu.add.validated_text_input(...) convenience method that accepts a validators=[...] list alongside the normal TextInput parameters. When validation fails, the widget's border should change to red (using the existing border system). Write tests for each rule type, live validation, the error label rendering, and the validate() return format.

The 70/30 Execution Strategy:

Turn 1 (70%): Create pygame_menu/form_validation.py with the FormValidator class, rule classes (RequiredRule, MinLengthRule, MaxLengthRule, RegexRule, CustomRule), error label rendering logic that inserts a Label widget below the failing TextInput. Add validate() method that checks all rules and returns the results dict. Add theme error styling properties to themes.py. Integrate into Menu class with menu.get_form_validator().

Turn 2 (30%): Add live_validation=True mode that hooks into TextInput's onchange callback to validate on each keystroke. Add menu.add.validated_text_input(...) convenience method in \_widgetmanager.py. Implement border color change on validation failure using the widget's existing set_border() API. Handle edge cases: widget removal clears its rules, error labels are removed when validation passes, multiple rules per widget show combined errors. Write comprehensive tests.

Why it fits the constraint:
form_validation.py will be ~150 lines (FormValidator, 5 rule classes, error label management). Theme additions add ~20 lines. Menu integration adds ~30 lines. WidgetManager convenience method adds ~40 lines. TextInput onchange hook for live validation adds ~25 lines. Tests add ~120+ lines. Total: ~385-420 lines across 6 files. Difficulty is appropriate because error labels must be dynamically inserted/removed from the menu's widget list without breaking layout, live validation must debounce rapid keystrokes, and Turn 3 will naturally address layout shifts when error labels appear/disappear and interaction with scroll areas.

---

## Reviewer Draft & Opinions

### Drafted Turn 1 Prompt

> pygame-menu currently has no built-in way to validate user input across widgets and display errors inline. Add a per-widget validation system that lets developers attach validation rules — at minimum: required, min/max length, regex pattern matching, and numeric range — to any widget. When `validate()` is called on a widget or on the menu as a whole, every rule should be checked and a structured result returned (a dict mapping widget IDs to their error message lists, empty dict if everything passes). Widgets that fail validation should visually indicate the error — at a minimum via a colored border and an error message rendered near the widget. The error styling (border color/width, error text color, font size) should be configurable through the existing Theme system. Write tests for the rule logic, widget-level validation, menu-level validation, and the draw integration.

### Opinions & Reasoning

**Why this prompt works:**

- It describes _what_ the feature should do without prescribing _how_ to implement it. There's room for models to differ on:
  - Where to put the validation code (new module vs. inline in existing files)
  - How rules are represented (classes vs. tuples vs. callables)
  - How error labels are rendered (inserting Label widgets vs. drawing directly on the surface)
  - How the validator is attached to widgets (kwargs, method call, decorator, etc.)
  - How menu-level validation aggregates results (recursive or not)
- It doesn't say "production ready" or steer the model toward any quality standard.
- The scope is atomic — a single coherent feature addition that belongs in one PR.
- It's challenging enough that Turn 1 alone won't produce production-ready code. Models will likely miss edge cases like: what happens when `get_value()` raises (e.g., on buttons), error font caching, theme validation assertions for the new properties, interaction between validation borders and user-set borders, and properly hooking `_draw_validation_error` into the draw pipeline.

**Things I'll be watching for in model responses:**

1. **Integration quality:** Does the solution actually plug into the existing `Widget` base class and drawing pipeline (`draw()` → `_draw_border()` → etc.) properly, or is it a disconnected utility?
2. **Theme integration:** Does it add new properties to `Theme.__init__`, `Theme.validate()`, and the type annotations? The solution.diff does this correctly across declarations, init, validate assertions, and `_format_color_opacity`.
3. **Test quality:** Are tests using the repo's `BaseTest` / `MenuUtils` / `surface` testing infrastructure from `test/_utils.py`, or rolling their own setup? Do they cover edge cases like validators on non-input widgets (buttons)?
4. **Codebase style adherence:** Does the model follow the existing docstring style (`:param:` / `:return:` RST style), license headers, type hints with string-quoted forward refs, and assertion-based validation?
5. **Error rendering:** The gold solution renders error text by lazily building/caching a font on the menu object and blitting directly. A model might try to insert Label widgets into the menu, which would be far more complex and fragile.
6. **kwarg plumbing:** Does it add `validator` as a configurable kwarg through `_widgetmanager.py`'s `_filter_widget_attributes`? This is the pattern every other widget property follows.

**Potential follow-up areas for Turns 2+:**

- Missing theme validation assertions
- Error font caching concerns
- Border override logic when validation errors exist vs. user-configured borders
- Tests not covering `validate_widgets(recursive=True)` submenu path
- Missing `__all__` or public API exports
- Robustness of `validate()` when `get_value()` raises
