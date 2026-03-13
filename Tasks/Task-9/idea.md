#### Contributor

Fix for #111 and #203

````markdown
#### Issue 111

#### Person 1

With MonkeyType 18.8.0 and Python 3.6 .

I ran into this on a complex example, but it's actually quite easy to reproduce.

```py
d:\work\pyann\monkeytype\toto>type a.py
class A:
    def __init__(self):
        self.titi = 'A_titi'

    def copy_from(self, other):
        self.titi = other.titi

d:\work\pyann\monkeytype\toto>type b.py
from a import A

class B(A):
    def __init__(self):
        super().__init__()
        self.titi = 'B_titi'

d:\work\pyann\monkeytype\toto>type main.py
from a import A
from b import B

if __name__ == '__main__':
    a = A()
    print( a.titi )
    b = B()
    a.copy_from( b )
    print( a.titi )

d:\work\pyann\monkeytype\toto>python main.py
A_titi
B_titi

d:\work\pyann\monkeytype\toto>monkeytype run main.py
A_titi
B_titi

d:\work\pyann\monkeytype\toto>monkeytype list-modules
a
b

d:\work\pyann\monkeytype\toto>monkeytype apply a

d:\work\pyann\monkeytype\toto>monkeytype apply b
Traceback (most recent call last):
  File "c:\program files (x86)\python36-32\lib\runpy.py", line 193, in _run_module_as_main
    "__main__", mod_spec)
  File "c:\program files (x86)\python36-32\lib\runpy.py", line 85, in _run_code
    exec(code, run_globals)
  File "C:\Program Files (x86)\Python36-32\Scripts\monkeytype.exe\__main__.py", line 9, in <module>
  File "c:\program files (x86)\python36-32\lib\site-packages\monkeytype\cli.py", line 377, in entry_point_main
    sys.exit(main(sys.argv[1:], sys.stdout, sys.stderr))
  File "c:\program files (x86)\python36-32\lib\site-packages\monkeytype\cli.py", line 362, in main
    handler(args, stdout, stderr)
  File "c:\program files (x86)\python36-32\lib\site-packages\monkeytype\cli.py", line 130, in apply_stub_handler
    stub = get_stub(args, stdout, stderr)
  File "c:\program files (x86)\python36-32\lib\site-packages\monkeytype\cli.py", line 105, in get_stub
    traces.append(thunk.to_trace())
  File "c:\program files (x86)\python36-32\lib\site-packages\monkeytype\encoding.py", line 183, in to_trace
    function = get_func_in_module(self.module, self.qualname)
  File "c:\program files (x86)\python36-32\lib\site-packages\monkeytype\util.py", line 38, in get_func_in_module
    func = get_name_in_module(module, qualname)
  File "c:\program files (x86)\python36-32\lib\site-packages\monkeytype\util.py", line 75, in get_name_in_module
    obj = importlib.import_module(module)
  File "c:\program files (x86)\python36-32\lib\importlib\__init__.py", line 126, in import_module
    return _bootstrap._gcd_import(name[level:], package, level)
  File "<frozen importlib._bootstrap>", line 978, in _gcd_import
  File "<frozen importlib._bootstrap>", line 961, in _find_and_load
  File "<frozen importlib._bootstrap>", line 950, in _find_and_load_unlocked
  File "<frozen importlib._bootstrap>", line 655, in _load_unlocked
  File "<frozen importlib._bootstrap_external>", line 678, in exec_module
  File "<frozen importlib._bootstrap>", line 205, in _call_with_frames_removed
  File "d:\work\pyann\monkeytype\toto\b.py", line 2, in <module>
    from a import A
  File "d:\work\pyann\monkeytype\toto\a.py", line 2, in <module>
    from b import B
ImportError: cannot import name 'B'
```
````

Type information can not be applied to b.py because a circular import has been created in a.py :

```py
d:\work\pyann\monkeytype\toto>type a.py
from b import B
class A:
    def __init__(self) -> None:
        self.titi = 'A_titi'

    def copy_from(self, other: B) -> None:
        self.titi = other.titi

```

I am not sure that there is a way around this. The structure of the modules is so that they don't have circular imports without type information but adding it creates the cycle.

In this precise case, the cycle is easy to break. Just by recording a call to A.copy_from() with an A instance as parameter, monkeytype figured out that type A is actually expected and did not use B anymore.

In my complex use case at work, the circular dependencies created are much more complex and can not be solved without deep involvement and probably some refactoring.

#### Reviewer 1

Thanks for the report! I'm not sure what the right thing to do here is. I'm going to leave this open until we figure out the best course of action going forwards

#### Reviewer 2

It would probably make sense for us to just always add all new imports inside an if TYPE_CHECKING block, right? By definition they are typecheck-only imports. That would solve this problem.

It probably also provides a reasonable workaround for you in the meantime? E.g. in your example case, if you just changed a.py to make the import conditional before trying to apply to b.py, the problem would go away.

#### Person 1

So, I tried carljm recommendation and this is not sufficient (at least with python 3.6) : with the conditional import, the type B is no longer recognised when I run my program. I need to put the B annotation in a string to make it work. The final version is :

```py
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from b import B

class A:
    def __init__(self) -> None:
        self.titi = 'A_titi'

    def copy_from(self, other: 'B') -> None:
        self.titi = other.titi
```

The one thing you or we can do is improve the documentation : when reading it, I was under the impression that usingmonkeytype applywas a 5 minutes job. Actually, it requires thorough code review. Pointing to the TYPE_CHECKING trick and string-style type annotation is also important.

For example that I ran at work, after thorough analysis, I only had two easy to resolve problems :

- circular references
- forward references
  All circular refences were actually of the type of this example : monkeytype recorded a method being called with several different child classes. Just like in my example, if I had also a class B2(A) and class B3(A). Then a.py was importing B, B1 and B2 creating the circular dependancy.

Just switching the method type annotation to A was the easy solution. This also raises the question of whether it is a good idea to be precise (method was called with instances of B, B1, B2 so let's annotate that) or to be more generic, like figuring out that B, B1 and B2 have a common ancestor and this is certainly the one needed here. In my case, the second option was the correct one.

The forward references were also easy to solve, I'll open a separate issue for them...

```

```

#### Reveiwer

Hi, thanks for the contribution! The code looks OK, but I think we need some tests for this new functionality, and an update to the docs including the new flag. Also, looks like you need to run black over the new files, that step of CI is failing.

#### Contributor

Added a few test cases and updated the docs @carljm, can you please take a look again?

#### Reveiwer

his looks great! Thanks for adding the tests and docs.

I think this is basically merge-ready, but it looks like it is failing CI on the flake8 linter check -- a lot of blank lines were added with whitespace in them, which the linter doesn't like.

Sorry for the inconvenience that GitHub won't run CI automatically when you push, since you're a first-time contributor I have to trigger it to run. But all the checks that the CI does should also be runnable locally via tox.

````

---

## Phase 1 Analysis

### Scope Validation: ✅ GOOD TASK

This is a well-scoped feature request that fixes two real GitHub issues (#111 circular imports and #203 forward references). It's not too broad (single flag, single feature) and not trivial (needs a new CST transformer module, CLI changes, tests, and docs). A mid-level engineer could reasonably implement this in a focused PR.

### My Opinions on What the Gold Standard Solution Does

The solution.diff modifies/creates the following:

1. **`monkeytype/cli.py`** — Adds `--pep_563` flag to the `apply` subparser. Modifies `apply_stub_using_libcst()` to accept a `confine_new_imports_in_type_checking_block` parameter. When set, it uses `use_future_annotations=True` in `ApplyTypeAnnotationsVisitor.store_stub_in_context()` to insert `from __future__ import annotations`. A new helper `get_newly_imported_items()` diffs stub imports vs source imports to find which are newly added. Then a new `MoveImportsToTypeCheckingBlockVisitor` transforms the module to move those new imports inside an `if TYPE_CHECKING:` block.

2. **`monkeytype/type_checking_imports_transformer.py`** (NEW FILE) — Contains `MoveImportsToTypeCheckingBlockVisitor` (a `ContextAwareTransformer` from libcst) which: adds `from typing import TYPE_CHECKING`, removes the newly-added imports from the top level, and re-inserts them inside an `if TYPE_CHECKING:` block placed right after the last import. Also contains `RemoveImportsTransformer` (a `CSTTransformer`) that handles removing specific `import` and `from ... import` statements by name.

3. **`tests/test_cli.py`** — Two new tests: `test_apply_stub_using_libcst__confine_new_imports_in_type_checking_block()` and `test_get_newly_imported_items()`.

4. **`tests/test_type_checking_imports_transformer.py`** (NEW FILE) — Comprehensive tests for the new transformer: simple case, existing TYPE_CHECKING block, typing imports, and a complex mix scenario.

5. **`CHANGES.rst`** & **`doc/generation.rst`** — Changelog entry and documentation for the new flag.

### Drafted Initial Prompt (Turn 1)

```
There's a known problem with the `apply` command, when monkeytype applies type annotations it can introduce circular imports in the target module. For example, if module A has a method that accepts instances of class B (defined in module B which inherits from A), applying the annotations adds `from b import B` to a.py creating a circular dependency. I want to add a `--pep_563` flag to the `apply` command that solves this by adding `from __future__ import annotations` to the file and confining all the newly added imports inside an `if TYPE_CHECKING:` block so they're only available at type-checking time and don't cause runtime circular imports. This should involve changes to the CLI argument parsing in cli.py, a new libcst transformer to handle moving imports into the TYPE_CHECKING block, tests for the new functionality, and update the docs and changelog accordingly
```

### Notes for Prompt Refinement

- The prompt gives clear scope (the --pep_563 flag), the why (circular imports), and the expected approach (future annotations + TYPE_CHECKING block) without dictating exact function names or class structures.
- It mentions tests, docs, and changelog which sets the expectation for production-readiness from turn 1.
- It's concise enough for a turn 1 prompt while giving a mid-level engineer level of detail.
- You can adjust the tone to match your natural style — e.g., adding "so basically" or making it slightly more conversational if you want.
````
