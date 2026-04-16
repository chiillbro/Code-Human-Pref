Hey there, I want you to implement something in this codebase, so let me explain the scenario first. Actually, in high throughput production environment, we often need to limit or reduce how much log output at specific levels to control I/O and storage costs. right now, there's no built in way to do this in pino, we are relying on some hacky ad-hoc wrappers. So, I want you to add a first class 'sampling' option to pino's logger options that lets us configure per level sampling rules. could you look into adding a native sampling configuration to the codebase? we generally need a few ways to control the flow:
- something probability-based(example, only emitting a certain percentage of logs
- some form of rate limiting
- emitting every Nth log

along with this, we have an issue with identical errors spamming the logs. it would be great to have a deduplication mechanism that suppresses repeated messages over a time window and maybe just emits a summary of how many were dropped when the window expires. A few things to keep in mind:

- the check needs to happen early so we are not paying serialization costs for logs that end up getting dropped
- if sampling is not configured, we absolutely cannot introduce any performance overhead to the existing hot path
- child loggers should probably inherit parent rules but be able to override them

take a look at the codebase, figure out the best way to design this configuration and go ahead with a implementation