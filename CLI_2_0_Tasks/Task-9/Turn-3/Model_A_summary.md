 Done. Summary of changes:               
                                                
  ---                                          
  1. inspect schema extended — tablesmith/cli.py

  Three new fields per column, original fields
  untouched:

  field: distinct                  
  meaning: count of unique non-null values
    (unhashable values compared by str())
  non-numeric: always present      
  ────────────────────────────────────────
  field: min
  meaning: minimum value
  non-numeric: null
  ────────────────────────────────────────
  field: max
  meaning: maximum value
  non-numeric: null

  Text output gains aligned Distinct, Min, Max
  columns; JSON keeps
  column/type/nulls/total/samples and adds the
  three new keys, so anything parsing the old JSON
   keeps working.

  Column  Type  Nulls  Total  Distinct    Min
  Max  Sample
  ──────  ────  ─────  ─────  ────────  ─────
  ──────  ──────────────────────────────
  age     int       1      6         5     23
   41  34, 29, 41, 23, 38
  dept    str       0      6         3
       "eng", "eng", "sales", ...

  2. having clause on group — tablesmith/ops.py

  - CLI: --group 'dept:avg(salary) as a,count()
  having a > 100000'
  - Pipeline file: {"op":"group", "by":"dept",
  "agg":[...], "having":"a > 100000"}
  - Reuses Expression and passes columns =
  group_keys ∪ aggregate_aliases, so an unknown
  alias is rejected at construction time with
  did-you-mean + available list:
  error (step #1 [group]): unknown column 'avgsal'
    hint: did you mean 'avg_sal'?
    hint: available columns: avg_sal, dept
  - tablesmith validate catches bad having
  references without running the pipeline.
  - Backward compatible: omitting having behaves
  exactly as before.

  Tests — tests/test_hardening.py (51 pass)

  New class: InspectDistinctMinMaxTest (10)
  Covers: distinct ignores nulls, distinct on
    str/int/bool, min/max for int + float,
    min/max=null for str/bool/all-null, text
  header
     has new cols
  ────────────────────────────────────────
  New class: GroupHavingTest (8)
  Covers: filters on alias, default alias, count,
    group key, compound expr, no-having backward
    compat, from_spec, end-to-end CLI
  ────────────────────────────────────────
  New class: GroupHavingErrorTest (7)
  Covers: unknown alias raises at construction,
    shows available + suggestion, from_spec,
  syntax
     error, validate subcommand catches it, CLI
    step context
