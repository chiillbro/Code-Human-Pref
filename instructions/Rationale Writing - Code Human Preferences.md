# Principles to Good Rationale Writing

### Introduction

The following document is meant to illustrate best practices in the rationale writing portions of the Code Human Preference project.

First off, what is a ***rationale***? A rationale is the explanation you provide of your reasoning for why you made a particular rating choice during a task. A good rationale accomplishes a few things:

1. It allows a reviewer going over your work to understand your rating decisions without performing the entire task themselves.  
2. It gives a reviewer and the project sponsor confidence that the individual tasker (you) made a real human effort.  
3. It is how you can tacitly advertise your particular experience and expertise.

Rationale writing is not an afterthought. It is not soulless formal documentation. It is actually an expression of your authenticity. Rationales should never be written with the assistance of any AI tool. An imperfect but honest rationale is better than a “perfect” one written by an LLM.

***“What if my English is not great? I like to make sure my writeups are professional.”***

For the most part, we don’t really care how ‘professional’ or ‘polished’ your rationale writing comes across. We don’t want you to be intentionally casual, and writing should obviously be understandable, but what matters to us is the substance. Write in complete sentences. We’re judging that your writing reflects thoughtfulness and genuine engagement with the task. Even using an LLM to clean up your writing is not permitted. The spellchecker that comes native in your browser or note-taking app of preference is allowed and encouraged. **Grammarly is not allowed.**

### Pros and Cons

You can think of the pros and cons as a list of noteworthy observations you make about Model A and Model B. Here are some best practices:

1. Evaluate each model on its own, independent of the other. When writing pros for Model A, focus on what makes Model A good, not on how it compares to Model B. Save comparative assessment for the overall preference justification.  
2. Build out your lists of pros and cons on an ongoing basis as you critique both of the model’s code changes.  
3. Feel free to write in bullet form. This is the most natural way to do it, but each point should be understandable, not an unfinished or half-formed thought fragment. Enough detail should be provided that the point becomes self-evidently a pro or a con.  
4. Try to only note observations that affect how you would rate along any of the seven grading axes and/or overall preference rating.  
5. Observations should be based on examinations of the **code changes**, and sometimes also the **model’s behavior**.

We’ve put together some examples to illustrate. Note these are often simplifications.

| Bad | Good |
| ----- | ----- |
| Pro: Model A's error handling is better than Model B's. (Comparative judgment) | Pro: In the new api route handler, Model A returns errors in the res object, and doesn’t itself throw exceptions. (Independent judgment) |
| Con: The model didn’t do what I asked. I specifically asked it to create a soft body blob creature spawned into the sandbox. When I load the app the blob creature is absent. (Not a direct observation about the code changes or the model’s behavior.) | Con: Model B creates the soft body blob but spawns it at coordinates (0, 0\) in `initScene()`, which is unfortunately outside where the canvas is visible. The `Composites.softBody()` implementation itself is completely correct. (Notice how proper scrutiny into the actual code changes puts things into perspective. A major issue at first glance can shift to a minor one upon closer inspection.) |
| Con: The application crashes (Not a direct observation about the code changes or the model’s behavior. Still an important thing to note, but should be grounded in what’s fundamentally observable in the code.) | Con: The application crashes. `page.tsx` has broken CSR now, because a div is introduced inside a p tag. (Observation is grounded in the code changes the model specifically makes. Suppose the other model’s changes didn’t cause the app to crash. One might’ve been inclined to rate the other model much better. However, upon further investigation, it becomes clear that the magnitude of the issue is due to something more trivial.) |
| Pro: Includes 73 new test cases. (Why is this a pro? We need a bit more detail.) | Con: Includes 73 new test cases, but most are unnecessary input validation checks. These include tests asserting `processPayment(null)`, `processPayment(undefined)`, and `processPayment(“”)` all throw. (Interesting. With attention to detail, we see that the pro actually flips to a con.) |
| Con: Model A took over 15 minutes to complete its response. (This does not relate to any of the grading axes being rated) |  |

### Overall Preference Justifications

The overall preference justification is exactly as it sounds. It should justify why one model response is better than the other. Here are some best practices:

1. Write in a self-contained format. Assume the person reading it has no access to the pros and cons, or any other writing you’ve done. The overall preference justification should speak for itself and articulate complete arguments. This means that you will have to resurface points made in the pros and cons.  
2. Compare Model A to Model B. Observations about ***both*** models should be made. Do not simply mention the way one model is strong and use that as sufficient evidence that it is therefore stronger than the other model.  
3. Substantiate claims that cannot be reasonably taken for granted otherwise.  
4. Generally speaking, critique the two models on factors that are relevant to the multi-axis ratings.  
5. Most of the time, aim to write 5-7 sentences. Conciseness is an advanced skill in rationale writing, and we don’t expect that. When in doubt, err on the side of writing more if it helps to make your argumentation complete. However, we don’t need you to write exhaustively. Hone in on the most salient points worth making.  
6. We appreciate when you refer to Model A and Model B using the exact terms ‘**Model A**’ and ‘**Model B**’.  
7. Writing rationales directly in the Claude Code CLI can feel suffocating, and lead some people to want to move on quickly or write less than needed. We would rather you slow down. If it helps, we encourage you to write in your note taking app of choice, and then copy/paste it into the form field in the CLI.

| Bad | Good |
| ----- | ----- |
| Model A is preferred because it implemented unit tests. The better comments of model B aren't enough to overcome the tests that model A implemented. (How do we know that you checked that Model B didn’t write unit tests too? And which comments about Model B are better? As opposed to what?) | Model A is preferable, because it includes unit tests, whereas Model B only runs the build and concludes its QA there.  |
| Model B is better than Model A, because it has better modularity and extensibility, and better documentation than Model A. Model B’s code changes are also clearer and easier to review. The code is also more robust to edge cases and handles errors more gracefully. (These claims need to be substantiated.) | Model B is better than Model A. Model B extracts the phone plan form validation logic into a separate \`validateInput()\` function, whereas Model A inlines even the non-business logic (which is redundant) across multiple event handlers. Model B includes JSDoc comments describing parameter types and return values for all of the new fetch functions that prop up the plan purchase user journey, whereas Model A has no such documentation. Model B wraps the \`getQuote\` and \`addPromoCode\` calls in try/catch and handles error cases well with distinct error messages that make use of the logger in the bff. Model B even understands how the different upstream error results in successful posts ought to result in different user feedback. Model A does add try/catch, but doesn't even attempt to parse the error object when there's an upstream failure captured in the 200 response. Yes, Model A can do this, because the api endpoints are all available in the swagger docs, and they do detail the error scenarios. Model A doesn’t seem to realize it should make use of the logger at all. (The substantiations replace the original vague claims entirely and speak for themselves) |
| Both Model A and Model B are about the same, but Model B is slightly better, because it actually ran the linter, which makes sure the code style aligns with the established practices. (this person probably didn’t actually look at the diff of code changes for that turn) | Model A is better, the biggest reason being that its code changes are easier to review, otherwise the implementations between Model A and Model B are similar. Both Model A and Model B run the linter, but Model B used the wrong command and used `npx` instead, so a different ruleset was used with autofixing applied. This results in undesirable extra code changes. Model B also includes unnecessary code comments that document the way the code changed from the previous turn, but this doesn’t matter from the pov of a contributing dev. (this person actually looked at the diff of code changes) |

