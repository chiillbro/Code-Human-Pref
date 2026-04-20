Everything works. Here's what was built:                  
                                                                                           
  Package structure:
  tablesmith/
  ├── pyproject.toml          # Installable package with `tablesmith` CLI entry point
  ├── README.md                                                                      
  └── tablesmith/
      ├── __init__.py
      ├── __main__.py          # python -m tablesmith support
      ├── cli.py               # Argument parsing and main entry point
      ├── errors.py            # Error classes with hints (no raw tracebacks)
      ├── expr.py              # Recursive descent parser for filter expressions
      ├── io.py                # CSV/JSON/JSONL/TSV reading and writing         
      ├── ops.py               # All operations: filter, select, rename, sort, group, join,
   etc.                                                                                    
      ├── pipeline.py          # JSON pipeline file loader and executor
      └── types.py             # Auto type inference (int, float, bool, null)

  Key features:
  - Formats — CSV, TSV, JSON, JSONL as both input and output, auto-detected from extension
  - Inline mode — chain --filter, --select, --rename, --sort, --group-by/--agg, --join,
  --head/--tail, --count, --describe
  - Pipeline mode — --pipeline transform.json for multi-step transforms defined in a JSON
  file
  - Filter expressions — comparisons (>, ==, !=), logical (and, or, not), string ops
  (contains, startswith, endswith), null checks, in lists, parentheses, backtick-quoted
  column names
  - Type inference — automatically casts strings to int/float/bool/null from the data
  - Joins — inner, left, right, outer against another file with --join-on col or --join-on
  left_col=right_col
  - Aggregations — sum, mean/avg, min, max, count, first, last
  - Error handling — friendly tablesmith: error: ... messages with hint: lines, no raw
  tracebacks
  - Pure stdlib — zero external dependencies
  - Install — pip install -e . gives you the tablesmith command, also works as python -m
  tablesmith