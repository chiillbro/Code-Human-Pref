 Here's what was built and tested:                
                                                                                  
  Project structure                   

  pyproject.toml                                                                  
  src/vijaybase/                                                                  
  ├── __init__.py          # package version                                      
  ├── __main__.py          # python -m vijaybase
  ├── cli.py               # argparse CLI with all subcommands
  ├── config.py            # vijaybase.yaml config load/save
  ├── conflict.py          # fork detection logic
  ├── diff.py              # schema diff engine (12 diff op types)
  ├── errors.py            # custom exception hierarchy
  ├── history.py           # _vijaybase_history table management
  ├── introspect.py        # read live SQLite schema (columns, PKs, indexes, FKs,
  CHECKs)
  ├── migration.py         # migration file I/O, chain walking, SQL execution
  ├── schema.py            # YAML schema parsing into dataclass models
  └── sql_generator.py     # diff ops → SQL (including table recreation)

  Features verified end-to-end

  ┌───────────────────────────────────────────────────────────────────┬────────┐
  │                              Feature                              │ Status │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Schema YAML — columns (type, nullable, default, unique),          │ Tested │
  │ composite PKs, indexes, FKs (on_delete), CHECK constraints        │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Diff engine — detect added/removed/altered tables, columns,       │ Tested │
  │ indexes, FKs, constraints                                         │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Migration generation — versioned YAML files with up + down SQL,   │ Tested │
  │ parent chain linking                                              │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ _vijaybase_history table — tracks migration ID, timestamp,        │ Tested │
  │ direction (up/down), checksum                                     │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Conflict detection — forks from same parent blocked on apply,     │ Tested │
  │ clear error message                                               │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ merge subcommand — re-diffs current DB vs desired YAML, creates   │ Tested │
  │ merge migration with merge_parent                                 │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Dry-run mode — apply --dry-run and rollback --dry-run print SQL   │ Tested │
  │ without executing                                                 │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ CLI subcommands — init, generate, apply [--dry-run] [--target],   │ Tested │
  │ rollback [--dry-run] [--steps], status, validate, merge           │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Transaction-wrapped applies — each migration runs inside          │ Tested │
  │ BEGIN/COMMIT with ROLLBACK on error                               │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Checksum verification — SHA-256 of SQL content, validate detects  │ Tested │
  │ tampering                                                         │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Table recreation — correct SQLite pattern (create temp new, copy, │ Tested │
  │  drop old, rename) preserving FKs in other tables                 │        │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ No ORM — pure sqlite3 stdlib + pyyaml                             │ Yes    │
  ├───────────────────────────────────────────────────────────────────┼────────┤
  │ Idempotency — re-running generate after full apply reports "No    │ Tested │
  │ schema changes detected"                                          │        │
  └───────────────────────────────────────────────────────────────────┴────────┘

  Install with pip install -e ., then use vijaybase from any project directory.
