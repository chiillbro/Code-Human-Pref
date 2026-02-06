Code Human Preference Tips

Updated December 11th, 2025

The purpose of this document is to provide some tips and clarifications to help boost the quality of your work contributing to the Code Human Preference Prod project. Thank you to everyone for your hard work on this project so far!

Important Reminders

Please ensure conversations are always 2+ turns in length. This means at least 2 user prompts, 2 model responses, and 2 ratings + comments.

Come up with original prompts. We see a lot of "add a CLI flag" style prompts that were essentially seeded or too closely inspired from the examples provided in the official project instructions.

Make sure to write thorough and detailed comments with your ratings. This is not optional.

Please refrain from prompting the LLM adversarially. Prompts should always reflect genuine requests a contributor to the codebase would actually make.

Al use of any kind in this project is strictly prohibited apart from using it mildly to introspect and understand a new codebase if that saves you time.

On another note, we also noticed several submissions ended prematurely while there were still obvious issues in the final solution worth steering the model to self-correct. Please take care to ensure conversations do not end while any of the following issues are still present:

The model never runs the lint, tests, and/or other checks to verify changes are working as expected and that no existing functionality is breaking (i.e. regression checks).

The model commits a bunch of unnecessary markdown/txt files. These are usually summaries, explanations of changes, how-to guides, step by step breakdowns, etc. These files typically don't align with the codebase's documentation practices, and are often about helping the user in the conversation rather than helping the user arrive at PR ready code changes.

The model creates custom impromptu test verification scripts that don't properly integrate into the codebase's test suites. The conversation ends with those scripts wrongly included in the final commit.

The model creates backup versions of files, but they are left in the final commit. If you see the model do this, you should steer the model to delete them.

If you spot other common failure patterns across multiple tasks / conversations feel free to voice them in the discord chat!

Rubric Axes

On each conversation turn you must rate the model responses against a grading rubric.

These aren't exhaustive considerations, but especially if you struggle to identify code improvements to meet the 2+ turn minimum, you should look at these questions for inspiration. It's highly unlikely that there aren't some lingering code quality issues.

Which code has better logic and correctness?

Does the implementation match the intended behavior?

Are edge cases and error conditions properly handled?

Is the control flow clear and free of subtle bugs?

Are there any off-by-one errors, null pointer exceptions, or race conditions?

Is the algorithm/approach correct for the problem being solved?

Are boundary conditions (empty inputs, maximum values, etc.) handled correctly?

Which code has better naming and clarity?

Do variable, function, and class names clearly express their purpose?

Is domain terminology used consistently throughout?

Are boolean names and conditions expressed positively when possible?

Do names avoid ambiguous abbreviations or insider knowledge?

Are assumptions about inputs, outputs, or behavior clearly documented?

Would a new developer understand what each component does from its name alone?

Are units clear in variable names (e.g., delay Seconds vs delay)?

Which code has better organization and modularity?

Are functions/methods focused on a single responsibility?

Is there duplicate code that should be extracted into reusable functions?

Are source files reasonably sized (not thousands of lines)?

Are functions/methods concise and focused (not hundreds of lines)?

Is related functionality grouped together logically?

Are abstraction levels consistent (not mixing high and low-level operations)?

Is there proper separation of concerns (I/O separate from business logic)?

Does each class have high cohesion (all methods relate to its purpose)?

Is cyclomatic complexity reasonable (avoiding deeply nested code)?

Are there parallel implementations of the same functionality?

Which code has better interface design?

Are APIs intuitive and hard to misuse?

Do function signatures minimize coupling (avoiding unnecessary parameters)?

Are return values and side effects predictable and well-documented?

Is mutability controlled and explicit?

Do functions have reasonable parameter counts (≤5, using objects for complex configs)?

Are return types consistent (avoiding different types based on conditions)?

Is it clear what each function does without reading its implementation?

Are required vs optional parameters clearly distinguished?

Do interfaces follow established patterns and conventions?

Which code has better error handling and robustness?

Are specific exception types used with contextual error messages?

Is there a consistent error handling strategy (fail fast vs recovery)?

Is input validation performed early at system boundaries?

Are errors properly propagated rather than silently swallowed?

Is resource management handled properly (files closed, memory freed)?

Are there any bare except clauses that could hide bugs?

Do error messages provide enough context to debug issues?

Are partial failures handled gracefully?

Is defensive programming used appropriately (not excessively)?

Which code has better comments and documentation?

Do comments explain WHY something is done, not WHAT is being done?

Are complex algorithms or business logic clearly explained?

Have comments been updated to match code changes?

Are there any Al-generated chain-of-thought comments that should be removed?

Are there placeholder comments saying code was removed/replaced?

Is there appropriate documentation for public APIs?

Are edge cases and non-obvious behavior documented?

Are there too many obvious comments that add noise?

Do comments provide value to future maintainers?

Which code is more ready for review/merge?

Is there any debug code, print statements, or console.log calls?

Has all commented-out code been removed?

Is the code properly formatted according to project standards?

Are all temporary files, build artifacts, or test outputs removed?

Does the code follow the established conventions for the codebase?

Are commit messages clear and follow project guidelines?

Is version control hygiene maintained (no large binary files, etc.)?

Are all tests passing and coverage adequate?

Has the code been linted and does it pass static analysis?

Are there any hardcoded values that should be configurable?

Is sensitive information (passwords, keys) properly handled?

Prompt Dos and Don'ts

Prompting

To properly prompt the model, you should adhere to the following:

The prompt should be original. Do not use Al or copy the examples from the instructions.

Clearly define the scope of your prompt, do not be ambiguous.

The prompt should align with the project's direction and goals.

DOs

Initial Prompt

The initial prompt should define a clear scope that will be followed throughout the task.

The initial prompt should align with the project's goals and direction.

The initial prompt should be realistic and achievable.

Follow-Up Prompts

Follow-up prompts should steer the direction of the task towards PR readiness.

Follow-up prompts should stay within the scope of the initial prompt.

Follow-up prompts should encourage writing tests if the request is a feature.

Follow-up prompts should make sure that existing functionality is not broken and there are no regressions.

DONTS

Initial Prompt

The initial prompt should not have many radically different feature requests that aren't related to each other.

The initial prompt should not be too vague or open-ended.

The initial prompt should not request the model to use real-time, current information or data.

The initial prompt should not ask the model to use the Internet. It does not have access to the internet.

Follow-Up Prompts

The follow-up prompts should not introduce new features that don't fit within initial prompt scope.

The follow-up prompts should not "break the 4th wall" and complain about platform-related issues. These are not related to the model itself.

Examples of Prompts

Good

cmgh4kukk000207231c447kqq

Repo https://github.com/marqbritt/Syqlorix/

Turn 1:

I would like to extend the CLI command for initializing an empty project. I would like to provide the ability to initialize the project with different templates. Evaluate the project and come up with the best approach. I want the template command invoked via this flag -t/--template so the command would look something like 'syqlorix init my_app --template minimal' or 'syqlorix init my_app -t api'. I want you to start with two templates minimal and possibly tailwind.. Look at the demo files and see how you can leverage those to help create this new functionality. I want you to deliver something that is PR ready.

Turn 2:

Great start, I noticed that you didn't leverage the demo files to help create the templates also putting all templates in a single templates.py doesn't seem very easily maintainable so please come up with a better design for that. The API template has a questionable approach as well - using DOM manipulation rather than HTTP response. The minimal template has styles which is not exactly minimal and the styles should be omitted for it to be truly minimal.

Turn 3:

I noticed you removed some of the existing templates from the templates.py file, please include the api and components templates as well. I also noticed that you still include both setup.py file and pyproject.toml manifest. Please resolve these issues.

Turn 4:

Great work, I noticed you included a setup.cfg file - is this file necessary? Also the test files being test_cli_templates.py and test_tempaltes.py can be consolidated or more properly integrated, having these two file names is a bit confusing. Lastly I noticed there is no error handling for template rendering, maybe adding template validation tests that actually try to import/execute the generated files. We're very close so let's get this PR ready!

cmgh4lty4000107072g17qmx7

Turn 1:

Add a --show flag which when provided a file conversion shows a sample of the pdf being converted to markdown. It should output some text, less than 100 characters from the pdf and then show it as markdown while the conversion process is running. For example python3 --show usage.py

cmgh4gkva9ow407923ojjixz2

Turn 1:

as it stands, the args that get passed in for --target-dir and --target-file when this program gets run are not validated, which is obviously a bad thing that can lead to all sorts of problems down the road. I need you to validate the args for --target-dir and --target-file to make sure they are what is expected.

cmgh6iz62ab1z0715nrvtepwr

Turn 1:

Add a logging middleware to the FastAPI-MCP library that logs all incoming MCP tool calls and their responses. \nThe middleware should: - Log the tool name being called - Log the input parameters - Log the response (or error if one occurs) - Use Python's standard logging module - Be configurable via a parameter when initializing FastApiMCP Example usage: ```python mcp = FastApiMCP(app, enable_logging=True)

cmgh6inwnpdeu0772jqbse7tn

Turn 1:

Add timeout support for MCP tool calls in the FastAPI-MCP library. When a tool takes too long to execute, it should timeout gracefully.

Bad

cmgh4badiay110742bra4i70d

Turn1:

Add a non-interactive dry-run validation mode to the ollmcp CLI. In mcp_client_for_ollama/client.py's top-level Typer command, introduce a --dry-run boolean flag. When --dry-run is set, resolve servers exactly as the current CLI does (--mcp-server, --mcp-server-url, --servers-json, --auto-discovery), attempt connections using the existing ServerConnector, print a concise per-server summary in one pass (server name, type: script | sse | streamable_http | config, connected: true/false, tool_count if connected, and an explicit reason if skipped that echoes the exact URL or path), and then exit without launching the interactive chat loop. Return specific exit codes: 0 when at least one server connected successfully; 2 when one or more URL servers are unreachable; 3 when a provided config file or --mcp-server path is invalid; 4 when no servers are discovered/resolved; 1 for any unexpected error. Add an optional --http-timeout float (default 0.6s) that applies only to dry-run HTTP/SSE connectivity checks. Do not duplicate connection logic; reuse ServerConnector and helper utilities. Keep edits minimal and consistent with existing style (Typer option grouping and Rich output). Update CLI help so the new flags appear under General Options, and add a short README Usage snippet describing dry-run and the exit codes. Add deterministic unit tests in tests/test_dry_run.py: cover a mix of reachable and unreachable URLs (mock mcp_client_for_ollama.utils.connection.check_url_connectivity), an invalid --mcp-server path (exit 3), a missing auto-discovery file scenario, the "no servers resolved" case (exit 4), and a success path that reports tool counts (mock ClientSession.list_tools). Use small, meaningful commits and ensure tests pass locally with uv venv && source .venv/bin/activate && uv pip install. && pytest -q.

Notes: Example is too complex and contains too many separate features, this is unrealistic and overly verbose.

cmgh4d4i0a9gr0715nj4aw4a5

Turn 1:

refactor the logging and configuration system to make the output more human readable and pretty like a json file and show the code changes in files and example of the output

Turn 2:

add a command line feature called letscheck that analyzes the current python project and reports potential issues with a very detailed, human readable format

Turn 3:

add a new tool called Smart-log-analyzer that scans all the logs, classifies them (info, warning, error, critical) and explain each issue clearly with cause and fix with best solution up to 2 solutions, and generate a professional json report with summaries and Al-based suggestions then merge it with the existing letscheck report into one final report file

Notes: Example is vague and confusing and doesn't have clear direction. The follow-up prompts do not relate to each other and the overall task lacks a well defined scope.

cmgh4hjei9yfk0716oc2wcbtb

Turn 1:

Add support for configurable temperature and max_tokens parameters in OllamaModel. These should be optional arguments in the constructor, stored in params, and passed correctly to the Ollama HTTP API request payload. Write unit tests that verify different values are respected.

Turn 2:

We want to extend the SDK with support for Cohere. Create a new model class CohereModel inside src/strands/models/cohere.py, following the same structure as OpenAlModel and AnthropicModel. The model should support both synchronous (call) and asynchronous (invoke_async) usage, using Cohere's Chat API.

Notes: The follow up prompt deviates significantly from the original prompt intent and does not stay within scope.

Commenting/Rating Axis

DOs

Comments should be written after every turn. (Turn is one model response to a human prompt)

Comments should accurately reflect the ratings and vice-versa.

Ratings should only be given when applicable (use N/A appropriately)

Make sure the multi-axis rating actually reflects the comparative scores, so they aren't overly polarized to the point where one model's response looks completely useless even though both had similar shortcomings.

DONTS

Comments should not be vague or general, such as "The code was good overall"

Comments should not be missing

Examples of Comments

Good

cmgh6iz62ab1z0715nrvtepwr

Model A provides runnable test code that verifies initialization. Model B provides a summary report claiming everything works. Model A is better because it gives executable verification that users can run, while Model B only documents claims without providing the code to prove them. Executable tests beat status reports.

Bad

cmgh4d4i0a9gr0715nj4aw4a5

the two responses are similar up to 80% but most of the time answer b is more specific, accurate, detailed but makes more space and execution time and sometimes with less visualization and human readability
