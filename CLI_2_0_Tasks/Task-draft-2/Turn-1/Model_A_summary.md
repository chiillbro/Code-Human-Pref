● All passing. Let me mark the task as complete.
                                                        
● Implementation is complete. Here's a summary of what was built:
                                                        
  Files Created                        

  - lib/context.js — Core module. Creates an AsyncLocalStorage
  instance, exposes createContext(userMixin) which returns a
  controller object and a composedMixin function that integrates
  with pino's existing mixin system.
  - test/context.test.js — 30 comprehensive tests covering all
  functionality.

  Files Modified

  - lib/symbols.js — Added contextSym symbol.
  - pino.js — Imports createContext, wires up context
  initialization when context: true is passed in options.
  Composes with user-provided mixin seamlessly.
  - pino.d.ts — Added ContextController interface, context?:
  boolean to LoggerOptions, and context?: ContextController to
  LoggerExtras.
  - test/types/pino.tst.ts — Added type-level tests for the
  context API.

  API

  const logger = pino({ context: true })

  // Scoped context — fields auto-appear in every log line
  logger.context.run({ requestId: 'abc', userId: 42 }, () => {
    logger.info('request started')  // includes requestId +
  userId

    // Nested scopes — inner overrides outer
    logger.context.run({ userId: 99 }, () => {
      logger.info('inner scope')    // userId=99, requestId=abc
    })
  })

  // Read/write current scope
  logger.context.get()               // all fields
  logger.context.get('requestId')    // single field
  logger.context.set({ traceId: 'xyz' })

  // Escape hatch (like AsyncLocalStorage.enterWith)
  logger.context.enterWith({ background: true })

  // Express middleware helper
  app.use(logger.context.middleware((req) => ({
    requestId: req.headers['x-request-id']
  })))

  Key Design Properties

  - Zero overhead when disabled — context defaults to false; no
  mixin, no ALS instance
  - Integrates via mixin system — no hot-path changes to pino
  internals
  - Composes with user mixin — both work together, user mixin
  wins on field conflicts
  - Child loggers inherit context — through prototype chain, no
  extra wiring
  - Throws at creation time if AsyncLocalStorage is unavailable

  Test Results

  - 30/30 context tests pass
  - 13/13 mixin tests pass (no regression)
  - 112/112 basic tests pass (no regression)
  - 15/15 hooks+formatters tests pass (no regression)
  - TypeScript type checks pass
