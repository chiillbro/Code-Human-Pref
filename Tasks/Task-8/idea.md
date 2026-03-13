#### Contributor

Fixes #159.

Earlier, we didn't pass in max_typed_dict_size recursively for all calls to get_type. This meant that [{"a": 1}] would produce the TypedDict class name even when max_typed_dict_size was set to 0. And since we didn't gather class stubs from nested generic types, the class definition stub wouldn't show up either.

Pass max_typed_dict_size in all calls of get_type.

Gather class stubs recursively using a TypeRewriter visitor for parameter types, return types, and yield types. Add rewrite_TypedDict method to TypeRewriter.

Special-case {} so that we don't return an empty TypedDict regardless of max_typed_dict_size.

````markdown
#### Issue 159

Given the following code:

```py
def foo(a):
    print(a)

foo([{"a": "b"}])
```

When it gets annotated by monkeytype the following will be the result:

```py
from monkeytype.encoding import DUMMY_NAME
from typing import List
def foo(a: List[DUMMY_NAME]) -> None:
    print(a)

foo([{"a": "b"}])
```

This raises ImportError as there's no DUMMY_NAME in monkeytype.encoding.

I'm using the latest monkeytype which is MonkeyType==19.11.2.
````

---

#### Reviewer

Looks good, thank you for the fix! Maybe add an entry to CHANGES.rst for this also?

---

in the monkeytype/stubs.py, commented for the lines 515 to 516 changes which are

```diff
515 +   def stubs(self) -> List[ClassStub]:
516 +       return self._stubs
517 +
518 +   def _rewrite_container(self, cls: type, container: type) -> type:

```

#### Reviewer

Typically it's preferable to use typing.Type (with a subscript clarifying which types are accepted) rather than type for type annotations. But it looks like our typing.pyi stub forces your hand here, so that's probably an orthogonal change.

#### Contributor

Yeah, I'd have liked to use Type[...]. However, when I tried using just cls: Type[List] and left out the other possible container types, it typechecked even with mypy --strict. Not sure why it didn't catch the errors (probably because of the type annotations in typing.pyi).

I've left it as type as I'm not sure of the right type to use here.

---

## Initial Prompt Draft

```
Hey, there's a bug related to Issue #159 in the TypedDict stub generation. When you trace something like `foo([{"a": "b"}])`, MonkeyType produces `List[DUMMY_NAME]` as the annotation which causes an ImportError because there's no `DUMMY_NAME` in `monkeytype.encoding`. The root cause is that `max_typed_dict_size` is not passed recursively in all `get_type()` calls inside `monkeytype/typing.py`, so nested dicts inside lists/tuples/sets don't respect the setting. Also, `ClassStub.stubs_from_typed_dict` in `stubs.py` only handles direct TypedDicts but doesn't discover or replace TypedDicts that are nested inside generic containers like `List[...]` or `Dict[str, ...]`. Fix the recursive propagation of `max_typed_dict_size` through all `get_type` calls, and refactor the stub generation so it can find and replace TypedDicts nested within generics. Also, empty dicts `{}` shouldn't generate an empty TypedDict regardless of `max_typed_dict_size` since that's unintuitive. Make sure to add proper tests covering the nested cases.
```

## My Opinions / Notes for Next Steps

**What the gold-standard solution does (key changes to look for):**

1. **`monkeytype/typing.py`:**
   - Makes `max_typed_dict_size` a **required** parameter on both `get_type()` and `get_dict_type()` (removes the `=None` default) — this forces every recursive call to explicitly pass it.
   - All recursive `get_type(e)` calls inside `list`, `set`, `tuple`, `defaultdict` branches now pass `max_typed_dict_size`.
   - Empty dict `{}` is special-cased to return `Dict[Any, Any]` instead of an empty TypedDict.
   - Adds `rewrite_TypedDict()` method to `TypeRewriter` so the visitor can now traverse into TypedDict annotations
   - Adds `is_typed_dict` check in `rewrite()` dispatch (before the generic check) so TypedDicts route to `rewrite_TypedDict`.

2. **`monkeytype/stubs.py`:**
   - Replaces `ClassStub.stubs_from_typed_dict()` static method with a whole new class `ReplaceTypedDictsWithStubs(TypeRewriter)` that uses the visitor pattern to walk generic types and discover/replace nested TypedDicts with forward refs.
   - `_rewrite_container` is overridden to pass a numbered `class_name_hint` suffix for disambiguation (e.g., `FooBar2TypedDict` for second element in Tuple).
   - `rewrite_TypedDict` replaces anonymous TypedDicts with forward refs and collects class stubs.
   - `FunctionDefinition.from_callable_and_traced_types` simplified to just call `ReplaceTypedDictsWithStubs.rewrite_and_get_stubs()` for arg_types, return_type, and yield_type — no more manual `is_anonymous_typed_dict` checks at top level.

3. **`monkeytype/typing.pyi`:** Updated type stubs to match new signatures.

4. **`tests/test_stubs.py`:** New `TestReplaceTypedDictsWithStubs` class with parametrized tests for: non-TypedDict passthrough, nested in List/Set/Dict/Tuple, genuine (non-anonymous) TypedDict passthrough, nested TypedDict-in-TypedDict, Tuple with two separate TypedDicts. Plus `test_render_return_typed_dict` and `test_render_typed_dict_in_list`.

5. **`tests/test_typing.py`:** Updated all `get_type` calls to pass `max_typed_dict_size` explicitly, new tests for nested dicts within generics, dict with other max_sizes, new `TestTypeRewriter` class testing `rewrite_TypedDict`.

6. **`CHANGES.rst`:** Changelog entry.

**What I'll watch for in model responses:**

- Did the model propagate `max_typed_dict_size` recursively? (The core bug fix)
- Did the model use the `TypeRewriter` visitor pattern or some ad-hoc approach?
- Did the model handle the empty dict edge case?
- Did the model add comprehensive tests for the nested cases?
- Did the model touch `typing.pyi`?
- Did the model add a CHANGES.rst entry?
- Any unnecessary files (summary.md, etc.)?
