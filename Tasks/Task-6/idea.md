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
