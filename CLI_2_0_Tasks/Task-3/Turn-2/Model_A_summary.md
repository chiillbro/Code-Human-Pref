All three features working correctly:
                                                                 
  1. CREATE order: users → orders → audit_log (respects FK       
  dependencies)
  2. DROP order (in down SQL): audit_log → orders → users (exact
  reverse)
  3. diff command: Clean human-readable output

  Here's a summary of what was changed:

  1. Column rename detection (src/vijaybase/diff.py)

  - Added RenameColumn diff operation type
  - Added _column_signature_equal() — compares
  type/nullable/default/unique without name
  - _diff_table() now detects renames: when a column disappears
  and a new one appears with the same signature, it emits
  RenameColumn instead of reconstruct. Uses ordinal position as a
   tiebreaker when multiple candidates match.
  - ReconstructTable now carries column_renames so data is
  preserved even when a rename coincides with a table
  reconstruction

  2. SQL generation for renames
  (src/vijaybase/migration_generator.py)

  - RenameColumn generates ALTER TABLE ... RENAME COLUMN old TO
  new (up) and the reverse (down)
  - _reconstruct_table_sql() now accepts column_renames and
  correctly maps old→new column names in the INSERT...SELECT data
   copy

  3. FK dependency ordering (src/vijaybase/diff.py)

  - The existing _toposort_for_create and _toposort_for_drop with
   Kahn's algorithm already handled transitive FK chains
  correctly. Verified with new tests for 3-level chains (users →
  orders → audit_log) that CREATE goes parent-first and DROP goes
   child-first.

  4. vijaybase diff subcommand
  (src/vijaybase/commands/diff_cmd.py, src/vijaybase/cli.py)

  - New diff command that prints detected operations in a clean
  format using + (add), - (drop), ~ (modify/rename) markers
  - For ReconstructTable, describes the specific changes inside
  (rename, add/drop columns, alter columns, PK/FK/constraint
  changes)

  Tests added: 15 new tests (104 total, all passing)

  - test_diff.py: 7 new (rename detection, 3-level FK create
  order, 3-level FK drop order)
  - test_migration_generator.py: 3 new (rename SQL up/down,
  reconstruct with renames)
  - test_cli.py: 3 new (diff shows changes, diff no changes, diff
   shows rename)
  - test_end_to_end.py: 2 new (rename preserves data + rollback,
  3-level FK create+drop)
