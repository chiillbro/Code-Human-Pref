 All 45 tests pass (26 original + 19 new). Here's what changed:
                                                                     
  ---                     
  1. Inspect: distinct, min, max fields
                                       
  cli.py _cmd_inspect — the schema builder now computes three additional
  fields per column:

  ┌──────────┬─────────────────────────┬──────────────────────────────┐
  │  Field   │         Meaning         │            Scope             │
  ├──────────┼─────────────────────────┼──────────────────────────────┤
  │ distinct │ Count of unique         │ All columns                  │
  │          │ non-null values         │                              │
  ├──────────┼─────────────────────────┼──────────────────────────────┤
  │ min      │ Minimum value           │ Numeric columns only (null   │
  │          │                         │ for strings)                 │
  ├──────────┼─────────────────────────┼──────────────────────────────┤
  │ max      │ Maximum value           │ Numeric columns only (null   │
  │          │                         │ for strings)                 │
  └──────────┴─────────────────────────┴──────────────────────────────┘

  All existing fields (column, type, nulls, total, samples) are unchanged.
   Both text and JSON output include the new fields — text table gained
  Dist, Min, Max columns; the — glyph renders for non-numeric min/max.

  2. Group having clause

  ops.py Group — new optional having parameter that filters aggregated
  rows, reusing the existing Expression parser:

  - CLI: --group 'dept:avg(salary) as avg_sal,count() having avg_sal >
  80000' — the having keyword splits the agg string from the filter
  expression
  - Pipeline file: {"op": "group", "by": "dept", "aggs": "sum(salary) as
  total", "having": "total > 200000"}
  - Fail-fast: At construction time, the having expression is compiled
  with columns= set to the output column names (group keys + aggregate
  aliases). Referencing an unknown alias raises ColumnError immediately —
  before any data is processed — with the bad name and available aliases
  listed
  - Describe/explain: includes the having clause, e.g. group dept:avg(*)
  having avg_sal > 100000

  New tests (19)

  Class: InspectDistinctMinMaxTest    
  Tests: 9                            
  What they verify: distinct count for numeric/string/unique columns;
    min/max correct for ints and null for strings; backward compat of all
    fields; text header includes new column headers
  ────────────────────────────────────────
  Class: GroupHavingTest
  Tests: 10
  What they verify: having filters aggregated rows; keeps matching groups;

    can reference group keys; no-having returns all; removes all when none

    match; from_spec pipeline support; unknown alias fails at construction

    with ColumnError; error shows available aliases; syntax error raises
    ExpressionError; describe includes having
