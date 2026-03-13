#### PR - 327

fix the ClassStub class and build_module_stubs function to correctly generate valid stubs for nested classes.

fixes monkeytype stub generates invalid stub for nested classes. #114
fixes Support nested classes (ERROR: Failed decoding trace: Module '...' has no attribute '<function with nested class>') #36

---

#### PR - 114

Reporting a separate issue for the problem discovered in #113 .

The exact files to reproduce this issue are :

forward_ref.py:

```py
class AA:

    def use_aa( self, aa ):
        pass

    def use_bb( self, bb ):
        pass

    def use_cc( self, cc ):
        pass

    def use_dd( self, dd ):
        pass

    class CC:

        def use_aa( self, aa ):
            pass

        def use_bb( self, bb ):
            pass

        def use_cc( self, cc ):
            pass

        def use_dd( self, dd ):
            pass

        class DD:

            def use_aa( self, aa ):
                pass

            def use_bb( self, bb ):
                pass

            def use_cc( self, cc ):
                pass

            def use_dd( self, dd ):
                pass

class BB:
    pass
```

And use_fw_ref.py :

```py
from forward_ref import AA, BB

if __name__ == '__main__':
    aa = AA()
    bb = BB()
    cc = AA.CC()
    dd = AA.CC.DD()

    for inst in (aa, cc, dd):
        inst.use_aa( aa )
        inst.use_bb( bb )
        inst.use_cc( cc )
        inst.use_dd( dd )

```

Then using them :

```py
d:\work\pyann\monkeytype\toto>monkeytype stub forward_ref
class AA:
    def use_aa(self, aa: AA) -> None: ...
    def use_bb(self, bb: BB) -> None: ...
    def use_cc(self, cc: AA.CC) -> None: ...
    def use_dd(self, dd: AA.CC.DD) -> None: ...


class AA.CC:
    def use_aa(self, aa: AA) -> None: ...
    def use_bb(self, bb: BB) -> None: ...
    def use_cc(self, cc: AA.CC) -> None: ...
    def use_dd(self, dd: AA.CC.DD) -> None: ...


class AA.CC.DD:
    def use_aa(self, aa: AA) -> None: ...
    def use_bb(self, bb: BB) -> None: ...
    def use_cc(self, cc: AA.CC) -> None: ...
    def use_dd(self, dd: AA.CC.DD) -> None: ...
```

When calling mypy, I get the following :

```
d:\work\pyann\monkeytype\toto>mypy forward_ref.py forward_ref.pyi
forward_ref.pyi:7: error: invalid syntax
```

To fix it, I had to change the stub to :

```py
class AA:
    def use_aa(self, aa: AA) -> None: ...
    def use_bb(self, bb: BB) -> None: ...
    def use_cc(self, cc: AA.CC) -> None: ...
    def use_dd(self, dd: AA.CC.DD) -> None: ...

    class CC:
        def use_aa(self, aa: AA) -> None: ...
        def use_bb(self, bb: BB) -> None: ...
        def use_cc(self, cc: AA.CC) -> None: ...
        def use_dd(self, dd: AA.CC.DD) -> None: ...


        class DD:
            def use_aa(self, aa: AA) -> None: ...
            def use_bb(self, bb: BB) -> None: ...
            def use_cc(self, cc: AA.CC) -> None: ...
            def use_dd(self, dd: AA.CC.DD) -> None: ...

class BB:
```

    ...

---

#### PR - 113

While running monkeytype apply, I received :

d:\work\pyann\monkeytype\toto>monkeytype apply forward_ref
ERROR: Failed applying stub with retype:
error: d:\work\pyann\monkeytype\toto\forward_ref.py: invalid syntax (<unknown>, line 8)
I was actually trying to reproduce a bug-case for forward references with nested classes, which I encountered at work.

The exact files to reproduce this issue are :

forward_ref.py:

class AA:

    def use_aa( self, aa ):
        pass

    def use_bb( self, bb ):
        pass

    def use_cc( self, cc ):
        pass

    def use_dd( self, dd ):
        pass

    class CC:

        def use_aa( self, aa ):
            pass

        def use_bb( self, bb ):
            pass

        def use_cc( self, cc ):
            pass

        def use_dd( self, dd ):
            pass

        class DD:

            def use_aa( self, aa ):
                pass

            def use_bb( self, bb ):
                pass

            def use_cc( self, cc ):
                pass

            def use_dd( self, dd ):
                pass

class BB:
pass
And use_fw_ref.py :

from forward_ref import AA, BB

if **name** == '**main**':
aa = AA()
bb = BB()
cc = AA.CC()
dd = AA.CC.DD()

    for inst in (aa, cc, dd):
        inst.use_aa( aa )
        inst.use_bb( bb )
        inst.use_cc( cc )
        inst.use_dd( dd )

Then using them :

d:\work\pyann\monkeytype\toto>monkeytype run use_fw_ref.py
d:\work\pyann\monkeytype\toto>monkeytype list-modules
forward_ref
d:\work\pyann\monkeytype\toto>monkeytype apply forward_ref
ERROR: Failed applying stub with retype:
error: d:\work\pyann\monkeytype\toto\forward_ref.py: invalid syntax (<unknown>, line 8)
d:\work\pyann\monkeytype\toto>
There is no error generated if I generate the stub, so this is probably an issue with parsing, or even a forward reference which confuses internally monkeytype.

d:\work\pyann\monkeytype\toto>monkeytype stub forward_ref
class AA:
def use_aa(self, aa: AA) -> None: ...
def use_bb(self, bb: BB) -> None: ...
def use_cc(self, cc: AA.CC) -> None: ...
def use_dd(self, dd: AA.CC.DD) -> None: ...

class AA.CC:
def use_aa(self, aa: AA) -> None: ...
def use_bb(self, bb: BB) -> None: ...
def use_cc(self, cc: AA.CC) -> None: ...
def use_dd(self, dd: AA.CC.DD) -> None: ...

class AA.CC.DD:
def use_aa(self, aa: AA) -> None: ...
def use_bb(self, bb: BB) -> None: ...
def use_cc(self, cc: AA.CC) -> None: ...
def use_dd(self, dd: AA.CC.DD) -> None: ...

---

## Copilot Analysis & Initial Prompt Draft

### Scope Validation: PASS

This task is well-scoped. It is a focused bug fix for nested class stub generation (issues #114, #36, addressed by PR #327). The current code has a `# TODO: Handle nested classes` comment in `build_module_stubs()` in `monkeytype/stubs.py`, which explicitly marks this as unfinished work. The fix requires changes to the data model (`ClassStub`), the rendering logic, and the stub-building algorithm — meaningful enough to not be trivial, but contained enough to not be overly broad. Tests are expected for this.

### What the Gold-Standard Solution Does

1. **`monkeytype/stubs.py`**:
   - `ClassStub.__init__` gets a new optional `class_stubs` parameter (dict of name→ClassStub) to hold nested classes.
   - `ClassStub.render()` receives a `prefix` parameter and renders nested class stubs recursively with incremented indentation.
   - `ClassStub.__repr__` updated to include the new `class_stubs`.
   - `build_module_stubs` is refactored: a new `_build_module_stub()` helper is extracted that handles a single module. For nested classes, it walks the `class_path` segments in a while-loop, descending into or creating nested `ClassStub` objects as needed, then attaches the function stub at the correct depth. The `# TODO: Handle nested classes` comment is removed.

2. **`tests/test_stubs.py`**:
   - New `test_render_nested_class` test verifying 3-level deep rendering.
   - Updated `TestModuleStub.test_render` with nested class expectations.
   - New `test_build_module_stubs_with_nested_classes` for the full pipeline.

3. **`tests/util.py`**:
   - Added `Nested1` and `Nested2` nested classes inside `Dummy` for test support.

### Opinions & Key Evaluation Points

- The models **must** modify `ClassStub` to support child classes and recursive rendering with proper indentation.
- The models **must** fix `build_module_stubs` to walk the class path hierarchy instead of joining it with dots.
- The models **should** add test fixtures (nested classes in `tests/util.py`) and write tests for both rendering and the build pipeline.
- Watch for: models that only fix rendering but not the building logic, or that hack it with string replacement instead of proper data structures.
- The solution also applies code formatting (single→double quotes) across test files — models won't do this, and that's fine. Focus on logic.

### Drafted Initial Prompt (Turn 1)

```
Right now, monkeytype generates invalid stubs for nested classes. If you have a class like AA with a nested class CC inside it, the stub output comes out as "class AA.CC:" at the top level which is invalid python syntax and fails on mypy. The nested classes should be rendered inside their parent class with proper indentation. There's already a TODO comment about this in build_module_stubs in stubs.py. Fix the ClassStub class and the build_module_stubs function to correctly handle nested classes so that stubs are valid and properly indented, and add tests for this
```
