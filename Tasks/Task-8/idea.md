#### Contributor 

Fixes #159.

Earlier, we didn't pass in max_typed_dict_size recursively for all calls to get_type. This meant that [{"a": 1}] would produce the TypedDict class name even when max_typed_dict_size was set to 0. And since we didn't gather class stubs from nested generic types, the class definition stub wouldn't show up either.

Pass max_typed_dict_size in all calls of get_type.

Gather class stubs recursively using a TypeRewriter visitor for parameter types, return types, and yield types. Add rewrite_TypedDict method to TypeRewriter.

Special-case {} so that we don't return an empty TypedDict regardless of max_typed_dict_size.



#### Reviewer

Looks good, thank you for the fix! Maybe add an entry to CHANGES.rst for this also?

#### Reviewer

Typically it's preferable to use typing.Type (with a subscript clarifying which types are accepted) rather than type for type annotations. But it looks like our typing.pyi stub forces your hand here, so that's probably an orthogonal change.


#### Contributor 

Yeah, I'd have liked to use Type[...]. However, when I tried using just cls: Type[List] and left out the other possible container types, it typechecked even with mypy --strict. Not sure why it didn't catch the errors (probably because of the type annotations in typing.pyi).

I've left it as type as I'm not sure of the right type to use here.