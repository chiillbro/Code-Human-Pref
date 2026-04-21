All staged. Here's a summary of what changed across all four areas:
                                                                     
  1. Per-resource timezones
  - Resource now has a timezone field (default "UTC", validated as IANA
  name via validate_timezone)                                          
  - resource add gains --timezone flag; global DB timezone removed from
  init                                                                 
  - Naive datetime inputs are interpreted in the resource's timezone;
  bookings stored in UTC                                             
  - schedctl list displays times converted back to each resource's local
  timezone

  2. Cross-midnight availability
  - AvailabilitySlot.validate() now allows end < start (only rejects start
   == end)                                                                
  - crosses_midnight property added to AvailabilitySlot
  - New _slot_window() function generates concrete time ranges: for
  cross-midnight slots, matches both the evening portion (slot weekday)
  and morning-after portion (next day)                                 
  - Same-day restriction removed — fits_within_availability works for any
  booking that fits within a single slot window, including overnight spans
  - resource list labels overnight slots with [overnight]                 

  3. schedctl check command
  - check_availability() in service returns a CheckResult with:
  within_availability, matching_slot, conflicts, and a computed available
  property                                                               
  - CLI output shows the matching availability window, lists any
  conflicting bookings with local times, and reports AVAILABLE / NOT 
  AVAILABLE with reasons                                             
  - Returns exit code 0 if available, 1 if not
                                              
  4. Tests & init robustness
  - 100 tests across 3 files: test_models.py (44 tests), test_service.py
  (39 tests), test_storage.py (17 tests)                                
  - Coverage includes: timezone validation, cross-midnight slot windows,
  overnight bookings with overlap detection, Sunday→Monday wraparound,  
  partial initialization recovery, corrupt DB detection, and --force  
  reinit                                                            
  - init_db now handles: dir-exists-but-no-db (recovers), corrupt db.json
  (clear error message suggesting --force), --force flag for intentional 
  overwrite                                                             
  - load() detects corrupt JSON and gives an actionable error message