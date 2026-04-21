 All 44 tests pass and the CLI works end-to-end. Here's what was built:
                                                                                          
  Project structure

  schedctl                    # Entry point (executable)
  schedmax/
    __init__.py
    cli.py                   # Argument parsing & dispatch
    store.py                 # JSON file storage (.schedmax/data.json)
    commands.py              # Command implementations
    validators.py            # Input validation
  tests/
    test_validators.py       # 27 unit tests for validation logic
    test_commands.py         # 17 integration tests for commands

  Commands

  ┌───────────────────────────────────────────────┬────────────────────────────────────┐
  │                    Command                    │            Description             │
  ├───────────────────────────────────────────────┼────────────────────────────────────┤
  │ schedctl init                                 │ Creates .schedmax/data.json store  │
  ├───────────────────────────────────────────────┼────────────────────────────────────┤
  │ schedctl resource add <name> -a 'mon          │ Add a resource with availability   │
  │ 09:00-17:00' [-d "desc"]                      │ slots (repeatable -a)              │
  ├───────────────────────────────────────────────┼────────────────────────────────────┤
  │ schedctl resource list                        │ List all resources and their       │
  │                                               │ availability                       │
  ├───────────────────────────────────────────────┼────────────────────────────────────┤
  │ schedctl book <resource> -t "title" -b        │                                    │
  │ "person" -s "YYYY-MM-DD HH:MM" -e "YYYY-MM-DD │ Create a booking                   │
  │  HH:MM"                                       │                                    │
  ├───────────────────────────────────────────────┼────────────────────────────────────┤
  │ schedctl cancel <id>                          │ Cancel a booking by ID             │
  ├───────────────────────────────────────────────┼────────────────────────────────────┤
  │ schedctl list [-r resource] [-d YYYY-MM-DD]   │ List bookings (upcoming by         │
  │ [--all]                                       │ default)                           │
  └───────────────────────────────────────────────┴────────────────────────────────────┘

  Validations enforced

  - Resource names: alphanumeric + hyphens/underscores, unique, max 64 chars
  - Availability slots: no overlaps on the same day for a resource
  - Bookings: must be in the future, end after start, same calendar day, within resource
  availability, no overlap with existing bookings

  No external dependencies — pure Python standard library (argparse, json, uuid, datetime,
  re, pathlib).