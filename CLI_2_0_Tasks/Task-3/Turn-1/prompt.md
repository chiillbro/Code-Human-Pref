Hey, I got an idea that needs to build from scratch which is to build a cli tool named vijaybase which is a database schema migration and versioning system targeting SQLite. so, the idea here is that users define their desired database schema in yaml files (it can be one or more files in a schema/ dir) and the tool diffs the current DB state against the desire state and then generates versioned migration files with forward (up) and rollback (down) SQL. so, particularly, I want the following things it needs to support

- I want schema definition in yaml something like tables with columns (name, type, nullable, default, unique), primary keys (single or composite), indexes, foreign keys (with on_delete actions) and CHECK constraints
- also, please implement diff and migration generation, compare current DB state vs desired yaml schema. detect added/removed/updated tables, columns, indexes, foreign keys and constraints. Each generated migration should be trackable (also should have link to its parent migration)
- A _vijaybase_history table inside the SQLite DB tracking migration ID, timestamp, direction (up/down) and a checksum of the migration file content
- there should be conflict detection also, for example when me and my friend branch off from the same migration and generate independent migrations, detect the fork and do not accept to apply until resolved, I think it will be better to include a merge subcommand
- also, there should be dry-run mode for apply and rollback (I think you know what I mean by dry-run, so I mean to print the SQL without executing)
- there should be cli subcommands like init, generate <description>, apply [--dry-run] [--target=ID], rollback [--dry-run] [--steps=N], status, validate, merge etc
- there should be robust error handling like wrapping migration applies in transactions
- please try not to use any ORM dependency, maybe you can think of just a src/vijaybase package with pyproject.toml

I trust you, build this for me to a complete working tool, let's go