All refinements landed and verified.                  
                                                                     
  Summary of changes      

  - Per-resource timezones: Resource.timezone is now required;
  service.add_resource validates via ZoneInfo (rejecting names like
  Mars/Olympus_Mons). Bookings accept local wall-clock input, convert
  using the resource's zone, and persist in UTC ISO-8601. The DB retains a
   default_timezone used when resource add omits --timezone.
  - Cross-midnight support: AvailabilitySlot.wraps_midnight() treats end
  <= start as wrapping. containing_window enumerates concrete per-day
  windows (scanning anchor days from start-1 through end) so FRI
  22:00-02:00 covers both the Friday evening and Saturday early-morning
  portions, and bookings themselves may cross midnight. The MAX_BOOKING
  cap is now 7 days (was 24 h). Same-day-only restriction removed.
  - schedctl check: new command that parses the booking identically to
  book, runs the full validator, and prints the resource/tz, UTC times,
  the matching availability window (or its absence), and any conflicting
  booking ids. Exits 0 when acceptable, 1 when not. DB is not mutated
  (verified by test).
  - Hardened init: refuses to overwrite an existing db.json, refuses a
  partial .schedmax/ directory (listing what's inside so you can decide),
  validates the default tz before creating anything (so a bad tz leaves no
   dir behind), and storage.load rejects corrupt JSON and wrong-version
  DBs with clear errors.
  - Tests: 22 unittest cases in tests/test_schedmax.py covering init
  robustness (overwrite, partial dir, corrupt JSON, bad version), timezone
   validation (valid, invalid, default fallback, CLI no-residue on bad
  tz), resource-local semantics (UTC storage,
  same-wallclock-different-zone independence, UTC-instant overlap
  detection), cross-midnight window math (four positional variants incl.
  rejection past the tail), overnight booking happy/reject paths, and the
  check command (ok / overlap / outside / no-mutation / CLI exit code).
  Run with python -m unittest discover -s tests -v.

  Breaking change flagged: DB version bumped 1 → 2. Any v1 DB from the
  previous step will be rejected with a message telling you to remove
  .schedmax/ and re-init. Nothing committed yet — want me to commit this
  as a single "per-resource tz + cross-midnight + check + tests" change,
  or split it?