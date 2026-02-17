### PUll-Request - 305

Calls to locally defined functions are not captured, for example:

`mod.py`:

```py
def add(a, b):
    def add_impl(a, b):
        return a + b
    return add_impl(a, b)
```

`main.py`:

```py
from mod import add
add(1, 2)
```

After running monkeytype run ./main.py, the resulting monkeytype.sqlite3 contains just the call to add.

---

**Contributor**

Fixes #305 by adding logic to tracing.get_func that goes up the stack trace and looks for the function in the locals.

Added a test for this, and also for locally defined class tracing which was already supported.

---

**Reviewer**

This looks reasonable as far as it goes. Thanks!

But what is the impact on stub generation of having these traces in the trace store? Is stub generation able to handle them reasonably? How about monkeytype apply?

---

**Contributor**

Stub generation fails on these traces and skips them. It has the same behavior as locally defined classes had before this PR, so I thought we can first add the tracing functionality to match classes, and later try to support stub generation.

I also think the tracing alone is useful, as I can see what the types were inside monkeytype.sqlite3 even if they fail to stub.

After running this code with monkeytype run:

```py
from tests.test_tracing import call_method_on_locally_defined_class, call_locally_defined_function
call_method_on_locally_defined_class(3)
call_locally_defined_function(3)
```

monkeytype stub tests.test_tracing outputs:

```
8 traces failed to decode; use -v for details
```

monkeytype -v stub tests.test_tracing outputs:

```
WARNING: Failed decoding trace: Module 'builtins' has no attribute 'frame'
WARNING: Failed decoding trace: Module 'builtins' has no attribute 'frame'
WARNING: Failed decoding trace: Module 'builtins' has no attribute 'frame'
WARNING: Failed decoding trace: Module 'builtins' has no attribute 'frame'
WARNING: Failed decoding trace: Module 'builtins' has no attribute 'frame'
WARNING: Failed decoding trace: Module 'tests.test_tracing' has no attribute 'call_locally_defined_function.<locals>'
WARNING: Failed decoding trace: Module 'tests.test_tracing' has no attribute 'call_method_on_locally_defined_class.<locals>'
WARNING: Failed decoding trace: Module 'tests.test_tracing' has no attribute 'call_method_on_locally_defined_class.<locals>'
```

---

**Reviewer**

Ok, that works for me!
