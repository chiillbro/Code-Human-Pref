# Code Human Preferences CLI Edition

# Code Human Preferences CLI Edition

[Claude Code CLI Binary Download](https://feedback.anthropic.com/claude_code?email_login=true) / [CLI Install Instructions](https://docs.google.com/document/d/1CQzW4R542zI9bJsato8aAWNM4kKKsOV-C4S_CZcw_lc/edit?usp=sharing) / [Submission Form](https://alignerrd-portal.vercel.app/human-preferences-feedback) / [Additional Tips](https://docs.google.com/document/d/1pTP7n0lKF4b4LGXxEEoKpY4YBqOOaJDVU0TBQZy5sAw/edit?usp=sharing) / [Rationale Writing Guide](https://docs.google.com/document/d/12SmZvQC6r5-q3iN0xEYIhXMBVitR1GR6xx5Am2R4bJ4/edit?usp=sharing)

### Before You Begin

**Please take the time to read these instructions in full, and refer back to them often, as needed. It is essential not to gloss over them, and that you understand and adhere to the specific constraints that are outlined.**

### Prerequisites Required

To participate in this project, you will need to have proficiency with Git, understand what production-ready code looks like, and have competence in your assigned programming language.

You should also have read the setup instructions for installing and tasking with the custom Claude Code CLI tool. It should be installed on your machine.

### Overall Aim

For this task, you will select a codebase that is a git repository, and ask the model to perform a single task in that codebase. Your goal is to, over multiple turns, iterate on the model’s solution for that task until it reaches a “production-ready” state (defined later in these instructions). In addition to iterating on the model’s solution, you should be iterating on the model’s workflow with it to ensure it is working like a real engineer \- meaning ensuring the model is reviewing the code it wrote, validating code against task requirements, committing regularly, etc.

Quick Setup Check:

Our current setup is the following, please ensure you’re using the following setup 

- Claude Code CLI version: `v2.1.45`  
- Interface code:: `cc_agentic_coding`

---

**UPDATE Dec 14th 2025 \- Proper usage of the N/A Rating**

- If two models produce equivalent, or near-equivalent, sets of changes, you should use middle-ratings for all applicable categories. Only use the N/A rating if the respective grading axis truly does not apply to what the model response produces in content on the relevant turn.

---

**UPDATE Dec 7th 2025 \- Common Things to Avoid**

- Make sure the `claude-hfi` binary is not included in the codebase itself. You should be moving the binary out of Downloads to the appropriate folder, and adding the executable to the PATH in your terminal rc file. ❗  
- Please refrain from including your name in the tar upload. As per the instructions, we want the name of the ***codebase***. We don’t want personally identifying information included (PII).  
- Make sure that follow-up turns do not introduce **scope creep**.

---

### Select a codebase

When using the Claude CLI tool, you may choose to source a codebase yourself or choose from one of the pre-approved [codebases](https://docs.google.com/spreadsheets/d/1L2W-YQ9jlDiMdzH3FQMiN3LZk5-QAEJrNOXelC5D-1c/edit?usp=sharing). If you choose to source a codebase on your own the chosen codebase **must** adhere to the following criteria:

- The codebase must be a git repo.  
- The codebase must be primarily written in your assigned programming language. If you are not assigned a language, stick to your area of expertise. We’re currently interested more in Python, Javascript, Typescript, and Rust.  
- The codebase must be high quality. It should not be a codebase that you vibe-coded.  
- We expect that the codebase likely has multiple contributors, and some present-day activity or recognition on Github. The code represents professional-level work.  
- The codebase must be open license.  
- The codebase must have clear, working instructions on how to ***build*** and ***run*** it. You have succeeded in completing these setup steps locally successfully.

Keep these additional points in mind too:

- The codebase should almost always have existing testing practices of some kind.  
- Aim for diversity when selecting codebases. Some should be libraries, some should be applications. Some should be simple and small, some should be large and complex (and everything in between)  
- Codebases should be a mix of libraries, applications, SDKs, etc  
- **You can use a codebase from a previous interaction with the model.** We encourage you to reuse codebases you have deep knowledge of. However, make sure your prompts are diverse. You may reuse the same codebase up to 10 times.  
- Codebases should often but not always include common developer productivity tools like linters and formatters.  
- Codebases may sometimes have [`SKILL.md`](http://SKILL.md) files, or [`CLAUDE.md`](http://CLAUDE.md) files. Take care to make sure you understand the contents of these files as Claude Code may behave in ways that don’t at first glance make sense, but do in lieu of that added context. If a codebase did not already have these files, do not go out of your way to introduce them.  
- Codebases may have [`CONTRIBUTING.md`](http://CONTRIBUTING.md) or other developer documentation that communicates opinions about contributing practices. It’s wise to understand the contents of these files too as they can inform how Claude Code should engage with common development patterns.  
- **Added December 8th:** The selected repo should generally AVOID making extensive usage of network ports or require active internet access, as these resources are ultimately shared between the environments.

### Prepare the Codebase

If you have a codebase that meets all criteria, but lacks clear setup instructions you can still use the codebase provided you add and commit those instructions. Test to make sure your own setup instructions work. These instructions should cover how to build and run the repo successfully. When you upload the tar of the codebase, make sure that commit is also included. See [below](#submission-steps) for details on submission steps.

In addition, when you begin your conversation with Claude Code, please make sure your current git worktree is clean. There should be no unstaged or staged changes active.

### Familiarize yourself with the codebase

Take time to understand the codebase’s purpose, features, and history. You should be comfortable ***reviewing*** any code changes made to the codebase and have the ability to ***ideate*** on your own plausible, useful, and realistic ideas for new features, refactors, or improvements.

### Start the Conversation with Claude Code

Launch Claude Code using either the vscode or tmux flag option. You will be prompted for an interface code. Enter the following code: `cc_agentic_coding`.

#### First Turn

Come up with a **well-scoped** prompt for your first turn. It’s important that you craft your first-turn prompt to meet these criteria:

- The scope should be appropriately sized for a single PR. Avoid prompts that would require very large changes that normally entail multiple PRs. Lean towards **atomic** change requests.  
- The scope should be specified from the perspective of the user. You want to avoid spelling out implementation details, or prescribing how the model should accomplish its task. Sometimes the user may be a developer. This is particularly common with refactor prompts. In these cases consider what technical details about the code need to be clarified only to define ***what*** the task is rather than ***how*** to do the task.  
- The scope should be reasonably challenging such that the model does not arrive at production quality code changes in 1 turn.  
- The first turn prompt should not instruct the model explicitly to “make the code changes PR ready”. We want to provide training data that can teach the model to do this by default unprompted, so including an explicit instruction defeats the point.

To illustrate, consider these examples:

| Good | Bad |
| ----- | ----- |
| Add resume logic to the [train.py](http://train.py) script so that long-running training runs can continue from previous saved checkpoints. **(Reasonably sized, atomic change request)** | Add checkpointing capabilities to model training runs, and provide a dashboard experience viewable with live streamed-in data feed. I should be able to compare different ablation runs easily, and ergonomically pause/resume experiments through the dashboard or cli. **(Too large, not atomic either)** |
| Add a boid simulation that runs smoothly in the browser with animating tails and configurable settings. Settings should have good defaults and ranges that allow users to easily experience the full range of interesting emergent behaviors, though make sure constraints are in place to avoid running into glitchiness or lag. **(Only defines what the scope is without prescribing how)** | Add a Boid simulation sandbox in javascript using a canvas element 800 by 600 px. Each boid should have position and velocity using cartesian coordinates. Make sure the sandbox is configurable with live feedback for flocking rules visual range, separation, cohesion, and alignment, defaulting to 75, 0.05, 0.005, and 0.05 respectively. Cap the number of boids to 100 operating at 60fps requestAnimationFrame. **(Specifies not just what the scope is, but also how to implement it effectively)** |
| We learned that POSIX filesystems have reliable guarantees in file move operation mtimes. The time doesn’t change between the reference to the prev and current inode, so we were able to use this to differentiate between move operations and really fast delete \+ create operations. However, some of the variable and class abstraction naming reflects the older understanding. Please update to reflect this new understanding. **(this is more the type of complexity we’re going for)** | We weren’t using underscores to denote private functions. Please update the class to do so. **(too easy)** |
| Implement async/await support for all existing callback-based API methods while maintaining backward compatibility. | Implement async/await support for all existing callback-based API methods while maintaining backward compatibility. Make sure all changes you make are production ready quality code. **(explicitly instructs model to make prod ready)** |

#### Successive Turns

All conversations with the model should require at least 3+ turns (3+ user prompts and 3+ model response pairs). Followup prompts after your first turn are treated differently. These prompts should adhere to the following criteria:

- The scope that was initially set in the first turn is kept the same. There should be **no scope creep**.  
- You are prompting the model akin to a PR reviewer providing comments and specific guidance. You are steering it towards making code changes approvable and **production ready**.  
- At the end of the conversation, the model has made changes within the repo that you are confident would genuinely be approved by codebase maintainers.  
- You are steering the model as an ***expert***. You are not passively steering the model and depending on it to be the expert.  
- In contrast to the first-turn prompt, successive turns should be **highly prescriptive** about ***what*** the model should improve on its implementation towards a state of production readiness (without being contrived). Be direct; you are the expert in this conversation.  
- Avoid following a script or a templated path that could be applied generically to any conversation you have with the model. Aim to steer the model in ways that are unique to the particular circumstances taking place. Consider this steer example: “*Make sure documentation is written for the new feature, and follows the style and practices of the codebase*”. This steer is generic and could apply to any conversation.

| Good | Bad |
| ----- | ----- |
| Please set a variable for the added conditional in ImpressionFactory to dry up the code. This is complex async queue logic that is difficult to simplify down further, but still not easiest to read. The variable keeps it self-documenting. As such you can remove the code comment over that line too since it’ll become redundant. You’ve added enough tests, but you included one vetting for exponential backoff that actually falsely passes despite not truly being something you implemented. I agree it’s in scope so do add it, but also make sure the test can’t falsely pass. **(This followup prompt steers prescriptively and demonstrates expertise. You aren’t just telling the model to be good, you’re teaching it what good looks like)** | Please review your code changes to make sure they are minimal. No unnecessary complexity. Make sure to follow best practices for production readiness. You added tests, but I only see a few, so you’ll want to make sure edge cases are captured too. Document the new feature. **(Consistently steering the model in this way can lead to removal from the project. You are here, because you bring coding expertise. This type of steering is something the LLM could do itself, and reflects either a lack of attention/effort or the prerequisite skills required to engage in this project meaningfully.)** |

### Definition of Production Ready

Production ready code should make these considerations **when relevant**:

* Implement the requested scope completely  
* Handle legitimate edge cases  
* Consider security implications  
* Avoid unnecessary comments, chain-of-thought, or explanations of the obvious  
* Match the codebase’s style  
* Use appropriate abstractions  
* Include comprehensive tests if the codebase has them

### Reviewing model outputs

Ensure the model:

* Follows software engineering best practices  
* Writes appropriate documentation  
* Uses git effectively  
* Reviews its own work  
* Clarifies requirements, edge cases, and expectations when necessary  
* Produces production-ready code

**Provide feedback exactly as you would on a regular PR.**

### Definition of done

**The interaction is complete when the code is production-ready to the best of your judgment.**

### Provide Your Ratings and Annotations

On each conversation turn, after both Model A and Model B responses complete you will be prompted to fill out a set of ratings and annotations, as well as a summary of what each model did and didn’t do well.

Start by writing the pros and cons for each model response. When in doubt, focus more on the negatives. Abstain from giving an overall preference rating until after you’ve given all individual ratings along the seven grading axes.

Each individual grading axis rating does not need to align with your overall preference rating, but the overall preference rating should reflect the combined individual ratings taken as a whole.

If two models produce equivalent, or near-equivalent, sets of changes, you should use middle-ratings for all applicable categories. Only use the N/A rating if the code changes truly do not apply to the task specified in the prompt.

In the overall preference justification writeup, please write a minimum of 3-5+ sentences clearly articulating the reason for your preference of model response.

### Submission Steps {#submission-steps}

When using the custom Claude Code CLI tool for tasking on Human Preferences Prod, there are additional items we need to collect. You must fill out the [submission form](https://alignerrd-portal.vercel.app/human-preferences-feedback) for each task submission.

#### Step 1 \- Select the CLI option

#### Step 2 \- Enter Initial Task Details

The easiest way to obtain the **Task ID** is to run `git status` in either of the model trajectory windows. You should see the result similar to below. The highlighted uuid is what you will enter into the **Task ID** field.  

The **Aligner Email** specifically refers to the personal email you use tied to your aligner account. This is your personal email, not the mailbox email. (Apologies for the confusion)

The **Starting Git Commit Hash** refers to the present commit hash at the HEAD of the git repo you intend to use for this task.

1. Verify first that you are on the main branch and the worktree is clean. Simply run `git status` at the root of the codebase. You should see “nothing to commit, working tree clean”. Ensure any staged or unstaged changes are removed.  
2. Now run `git rev-parse HEAD` to obtain the commit hash. Copy that value into the Starting Git Commit Hash form field.

#### Step 3 \- Record the Session Id

Launch the claude-hfi cli in `--vscode` or `--tmux`. We highly recommend choosing `--vscode` if you don’t have a strong preference or are unsure which option might be better. Right away, take note of the Session UUID as shown in the green rectangle. This will be important later when you are ready to complete your task.  

#### Step 4 \- Upload the Codebase

We require the codebase as it exists before the model makes any code changes. To do this:

1. First, remove unnecessary dependency folders such as `venv` or `node_modules`. This is mandatory, and it is likely that the upload will be too large to accept otherwise.  
2. Then, in a separate terminal one level up from the root of the codebase, run: `tar -cf <codebase-name>.tar ./<project-folder>`  
3. Upload the tar by clicking the upload button and selecting the tar file in the **Starting Codebase** field.

#### Step 5 \- Upload the Session

Complete each conversation turn and follow the feedback steps. Once you are satisfied that the code changes are mostly production ready and you have submitted the feedback for the final turn in the cli, upload the session.

Follow the steps as outlined, replacing the **\<uuid\>** placeholder with the **Session UUID** noted earlier.  

#### Step 6 \- Upload the Final Diff

1. After completing your conversation, obtain the complete diff of the accepted changes made to the codebase after all ratings have been made.  
   1. Use the command `git add -A` to ensure new files are included in the diff.  
   2. Then, `git diff <commit_hash> > ~/<uuid>_final.diff`*.* Make sure the `commit_hash` is replaced with the same commit hash you obtained at the start of the conversation when you executed `git rev-parse HEAD` earlier.

#### Step 7 \- Verify

You **must** verify that you have done each of the following correctly. Repeated mistakes adhering to these checks can result in removal from the project. Refrain from clicking them automatically.  

#### Step 8 \- Final Details

Record the time in minutes to complete the task. We’ve left a freeform comments field as well in case there are issues or concerns.  

#### Rubric Axes

You may have noticed that explainers for each axis are provided in the cli by pressing `?` on your keyboard. To help, we’ve extracted that information out below for convenience too. These aren’t exhaustive considerations, but especially if you struggle to identify code improvements to meet the 3+ turn minimum, you should look at these questions. It’s highly unlikely that there aren’t some lingering code quality issues.

##### Which code has better logic and correctness?

• Does the implementation match the intended behavior?  
• Are edge cases and error conditions properly handled?  
• Is the control flow clear and free of subtle bugs?  
• Are there any off-by-one errors, null pointer exceptions, or race conditions?  
• Is the algorithm/approach correct for the problem being solved?  
• Are boundary conditions (empty inputs, maximum values, etc.) handled correctly?

##### Which code has better naming and clarity?

• Do variable, function, and class names clearly express their purpose?  
• Is domain terminology used consistently throughout?  
• Are boolean names and conditions expressed positively when possible?  
• Do names avoid ambiguous abbreviations or insider knowledge?  
• Are assumptions about inputs, outputs, or behavior clearly documented?  
• Would a new developer understand what each component does from its name alone?  
• Are units clear in variable names (e.g., delaySeconds vs delay)?

##### Which code has better organization and modularity?

• Are functions/methods focused on a single responsibility?  
• Is there duplicate code that should be extracted into reusable functions?  
• Are source files reasonably sized (not thousands of lines)?  
• Are functions/methods concise and focused (not hundreds of lines)?  
• Is related functionality grouped together logically?  
• Are abstraction levels consistent (not mixing high and low-level operations)?  
• Is there proper separation of concerns (I/O separate from business logic)?  
• Does each class have high cohesion (all methods relate to its purpose)?  
• Is cyclomatic complexity reasonable (avoiding deeply nested code)?  
• Are there parallel implementations of the same functionality?

##### Which code has better interface design?

• Are APIs intuitive and hard to misuse?  
• Do function signatures minimize coupling (avoiding unnecessary parameters)?  
• Are return values and side effects predictable and well-documented?  
• Is mutability controlled and explicit?  
• Do functions have reasonable parameter counts (≤5, using objects for complex configs)?  
• Are return types consistent (avoiding different types based on conditions)?  
• Is it clear what each function does without reading its implementation?  
• Are required vs optional parameters clearly distinguished?  
• Do interfaces follow established patterns and conventions?

##### Which code has better error handling and robustness?

• Are specific exception types used with contextual error messages?  
• Is there a consistent error handling strategy (fail fast vs recovery)?  
• Is input validation performed early at system boundaries?  
• Are errors properly propagated rather than silently swallowed?  
• Is resource management handled properly (files closed, memory freed)?  
• Are there any bare except clauses that could hide bugs?  
• Do error messages provide enough context to debug issues?  
• Are partial failures handled gracefully?  
• Is defensive programming used appropriately (not excessively)?

##### Which code has better comments and documentation?

• Do comments explain WHY something is done, not WHAT is being done?  
• Are complex algorithms or business logic clearly explained?  
• Have comments been updated to match code changes?  
• Are there any AI-generated chain-of-thought comments that should be removed?  
• Are there placeholder comments saying code was removed/replaced?  
• Is there appropriate documentation for public APIs?  
• Are edge cases and non-obvious behavior documented?  
• Are there too many obvious comments that add noise?  
• Do comments provide value to future maintainers?

##### Which code is more ready for review/merge?

• Is there any debug code, print statements, or console.log calls?  
• Has all commented-out code been removed?  
• Is the code properly formatted according to project standards?  
• Are all temporary files, build artifacts, or test outputs removed?  
• Does the code follow the established conventions for the codebase?  
• Are commit messages clear and follow project guidelines?  
• Is version control hygiene maintained (no large binary files, etc.)?  
• Are all tests passing and coverage adequate?  
• Has the code been linted and does it pass static analysis?  
• Are there any hardcoded values that should be configurable?  
• Is sensitive information (passwords, keys) properly handled?

# Tips for success

### Important Reminders 

- Please ensure conversations are **always 3+ turns** in length. This means at least 3 user prompts, 3 model responses, and 3 ratings \+ comments. Only exceptionally high-quality 2-turn submissions may be accepted.  
- Come up with **original** prompts. We see a lot of “add a CLI flag” style prompts that were essentially seeded or too closely inspired from the examples provided in the official project instructions.  
- Make sure to write **thorough and detailed comments** with your ratings. This is not optional.  
- Please refrain from prompting the LLM adversarially. Prompts should always reflect genuine requests a contributor to the codebase would actually make.  
- We are more interested in steering towards PR-readiness, rather than perfectly exact PR-readiness. Don’t spend an entire turn trying to fix one comment.

### Common PR Readiness Steering Problems

Though there are many ways that a submission may not have been appropriately steered towards PR readiness, these are some common issues that have been noticed to occur.

- **The model never runs the lint, tests, and/or other checks** to verify changes are working as expected ***and*** that no existing functionality is breaking (i.e. regression checks).  
- **The model commits a bunch of unnecessary markdown/txt files.** These are usually summaries, explanations of changes, how-to guides, step by step breakdowns, etc. These files typically don’t align with the codebase’s documentation practices, and are often about helping the user in the conversation rather than helping the user arrive at PR ready code changes.  
- **The model creates custom impromptu test verification scripts that don’t properly integrate into the codebase’s test suites.** The conversation ends with those scripts wrongly included in the final commit.  
- **The model creates backup versions of files, but they are left in the final commit.** If you see the model do this, you should steer the model to delete them.

### Prompt Dos and Don’ts

#### Prompting

To properly prompt the model, you should adhere to the following:

* The prompt should be original.  
* Clearly define the scope of your prompt, do not be ambiguous.  
* The prompt should align with the project’s direction and goals.

##### DOs

**Initial Prompt**

- The initial prompt should define a clear scope that will be followed throughout the task.  
- The initial prompt should align with the project’s goals and direction.  
- The initial prompt should be realistic and achievable.

**Follow-Up Prompts**

- Follow-up prompts should steer the direction of the task towards PR readiness.  
- Follow-up prompts should stay within the scope of the initial prompt.  
- Follow-up prompts should encourage writing tests if the request is a feature.  
- Follow-up prompts should make sure that existing functionality is not broken and there are no regressions.

##### DONTs

**Initial Prompt**

- The initial prompt should not have many radically different feature requests that aren’t related to each other.  
- The initial prompt should not be too vague or open-ended.  
- The initial prompt should not request the model to use real-time, current information or data.  
- The initial prompt should not ask the model to use the Internet. It does not have access to the internet.

**Follow-Up Prompts**

- The follow-up prompts should not introduce new features that don’t fit within initial prompt scope.  
- The follow-up prompts should not “break the 4th wall” and complain about platform-related issues. These are not related to the model itself.

### Commenting/Rating Axis

#### DOs

- Comments should be written after every turn. (Turn is one model response to a human prompt)  
- Comments should accurately reflect the ratings and vice-versa.  
- Ratings should only be given when applicable (use N/A appropriately)  
- Make sure the multi-axis rating actually reflects the comparative scores, so they aren’t overly polarized to the point where one model’s response looks completely useless even though both had similar shortcomings.

#### DONTs

- Comments should not be vague or general, such as “The code was good overall”  
- Comments should not be missing
