# Office Hours Tuesday Feedback Items

### Differentiating between First Turn Prompts and Follow-up Turn Prompts

| Dimension | First Turn Prompt | Follow-up Prompts |
| ----- | ----- | ----- |
| Scope | Defines the task. Establishes ***what*** the model needs to do, not how. | Gives feedback to improve the quality or make corrections to the implementation. Prescribes ***how*** the model should fulfill the requirements, but never adds to the scope. Follow-up prompts should never introduce scope creep. |
| Perspective | Written from a **user** perspective. | Written from the perspective of a software developer doing PR review. |
| Implementation Details | Does not include implementation details. | Highly prescriptive about the implementation details. |

### **First Turn** Prompt Implementation Details

| Good | Bad |
| ----- | ----- |
| Generate reference documentation for tools. Make sure it includes tool parameters, types, required vs. optional, defaults, and descriptions automatically derived from the code. Docs need to stay in sync when tools are added or modified.  | Generate reference documentation for tools. Make sure it includes tool parameters, types, required vs. optional, defaults, and descriptions automatically derived from the tool’s pydantic annotations. Docs need to stay in sync when tools are added or modified. Make sure you add automod to pre-commit hooks. Generate tool documentation using Mintlify components. Scan the tools directory for tools. |

***Why is this important?***

Model A and Model B responses will have fewer similarities if you don’t spell out the implementation details in the first turn prompt. During training, models learn best from A/B pairs where one response is much better than the other.

Also, why shoot yourself in the foot by giving the model a cheatsheet telling it how to complete the task correctly? You’re more likely to run into situations where you can’t get to 3 turns because the model arrives at production ready code sooner. Don’t give the model the solution to the problem on the first turn.

### Naturalness Issues in Prompts

Both the first turn and follow-up prompts should never ***explicitly*** instruct the model to make its changes ‘production ready’ (or some variant of that).

We want to teach the model how to write high quality code changes to codebases, and succeed even in complex production settings. Importantly, we want the model to learn to do this ***by default*** without being explicitly told to do so. If you tack on an explicit instruction in your prompt to “make the code changes production ready”, during training the model may instead learn to behave this way only when it’s explicitly instructed to.

Furthermore, **you** are the one who is supposed to teach the model what production ready looks like in any given task. By instructing the model to simply “make the code changes production ready”, you are effectively asking the model to teach and steer itself, which isn’t valuable.

#### Generic / Boilerplate / Templated Steering

Here’s an example to illustrate, and to be clear, we don’t want this kind of follow-up steering:

**Turn 1 Prompt:** *Generate reference documentation for tools. Make sure it includes tool parameters, types, required vs. optional, defaults, and descriptions automatically derived from the code. Docs need to stay in sync when tools are added or modified.*

**Turn 2 Prompt:** *Go through your changes and check to make sure all edge cases are handled. Write comprehensive unit tests for coverage. Don’t just test the happy path. Cover edge cases too.*

**Turn 3 Prompt:** *Make sure the documentation generator script is DRY. Keep the code clean and simple and adhering to the code style the codebase follows.*

**Turn 4 Prompt:** *Add this feature to the README and commit all of your changes using a clear, informative, and concise commit message.*

This is mediocre. This kind of follow-up prompting follows a template. It takes no skill to do. It requires no real effort. It gives reviewers zero confidence that the person who submitted actually critiqued and understood the model responses. All of these steers are formulaic and generic. I could more or less take these exact same follow-up prompts and apply them to the next task I do. They fail to actually address anything concrete about what the model did poorly. They also fail to demonstrate that you are an expert. You should be the one to know what edge cases are appropriate to handle. You should be the one who understands when code is unnecessarily complex, and what simple would look like, if not in the prompts then in the rationale writing.

### Other Bad Prompts

#### Vague Prompts

**Turn 1 Prompt:** *Make a function that handles user data*

**Turn 1 Prompt:** *Add error handling and improve security.*

**Turn 1 Prompt:** *Build a login system.*

None of these prompts define clear, unambiguous scope that the model could reasonably fulfill.

#### Misaligned Prompts

**Codebase:** A codebase that deliberately showcases security vulnerabilities for educational content  
**Turn 1 Prompt:** *Fix the sql code so that users can’t perform malicious sql injections.*

These prompts fail to define scope that aligns with the codebase. As such, any code the model writes will, by definition, never be production ready, because a developer would never approve and merge that code into the codebase.

### Rationale Writing

The pros and cons in the CLI version of the project are required form fields. Do not skip these.

The overall preference justification needs to be written to clearly express the reasons why one model response is preferred over the other.

### Some Misconceptions We’re Seeing

- The model you’re talking to (Claude Code) has no concept of Model A or Model B. Please don’t make reference to “Model A” and “Model B” in your prompts to the model.  
- The model doesn’t know about the project instructions. Don’t talk to the model as if it knows that the goal is to arrive at production ready code. Don’t ever tell it explicitly to produce “production ready code”. Try to leave out language from your prompts that relate back to the goals from the project instructions in general.