re-factor: migrated classes from xml to json

---

## Drafted Initial Prompt

```
I want to refactor the class definitions data layer, specifically migrate `data/classes.xml` to a JSON format. Create a new `data/classes.json` file with the same four classes (innkeeper, warrior, ranger, spy) and their stats/skills, then add a new `src/services/load_from_json_manager.py` module with a `load_classes()` function that reads from JSON, normalizes optional fields like constitution and move to defaults when missing, and converts skill name strings into Skill objects. Update `main.py` and `tests/tools.py` to use the new JSON loader for classes instead of the XML one, and remove the old `load_classes` function and its `CLASSES_DATA_PATH` from the XML manager. Make sure the data contract stays exactly the same so Character.init_data and the stats_up method keep working.
```

## My Opinions / Notes

**Why this task is good:**

- Well-scoped refactor: one data entity (classes) from XML → JSON. Clear boundaries.
- Touches multiple files (data file, new loader module, call sites, removal of old code) so models have real work to do.
- JSON is inherently cleaner than the verbose XML, and there's a subtle mapping issue (XML uses `strength`/`defense`/`resistance` but the codebase expects `str`/`def`/`res` in the dict) — good opportunity to see if models handle this correctly.

**What to watch for in model responses:**

1. **Stats key naming**: Do they realize `Character.stats_up()` uses `str`, `def`, `res` (not `strength`, `defense`, `resistance`)? The XML loader's `load_stats_up()` was doing this translation. The JSON should use the short keys directly.
2. **`def` and `str` as dict keys in TypedDict**: These are Python reserved/builtin words. If they try class-based TypedDict syntax, it'll fail. The gold solution uses functional-form TypedDict.
3. **Reuse of `get_skill_data`**: Do they import from the XML manager or duplicate the logic?
4. **Do they remove `CLASSES_DATA_PATH` and `load_classes` from the XML manager?** Half-done migration is messy.
5. **Do they update both `main.py` AND `tests/tools.py`?** Missing the test tools file would break the test suite.
6. **Tests**: The solution doesn't include dedicated tests, but this is a natural follow-up prompt target for Turn 2.

**Follow-up prompt ideas (if needed):**

- Turn 2: Ask for unit tests for the new `load_classes()` — test defaults normalization, skill conversion, file-not-found error handling.
- Turn 3: Any remaining cleanup — unused imports, missing docstrings, edge cases like malformed JSON.
