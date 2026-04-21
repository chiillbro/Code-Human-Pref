 Foundation is in place and verified end-to-end.           
                                                                                           
  What's built:
  - schedmax/storage.py — JSON DB under .schedmax/db.json with atomic writes and git-style
  upward lookup.                                                                          
  - schedmax/models.py — Resource, AvailabilitySlot, Booking dataclasses, availability-spec
   parser, and helpers fits_within_availability / overlaps.                                
  - schedmax/service.py — add/list resources, create/cancel/list bookings; enforces
  uniqueness, availability fit, no-past, end>start, 1 min–24 h duration, no overlap;
  timezone from init via zoneinfo.                                                  
  - schedmax/cli.py — schedctl with init, resource add|list, book, cancel, list (supports
  --resource, --all).                                                                    
  - pyproject.toml wires the schedctl entry point; python -m schedmax also works.
                                                                                 
  Verified flows (against today = 2026-04-21): init, adding a resource with weekly
  availability, happy-path booking, and rejection of overlap, out-of-availability, past,
  unknown-resource, zero-duration, duplicate-resource, bad-weekday, and double-init.

  One design choice worth flagging: bookings are restricted to a single calendar day (so a
  slot like FRI 09:00-17:00 can't be straddled by a booking crossing midnight). This keeps
  availability reasoning simple for the foundation. If you want multi-day bookings later,
  we'd generalize fits_within_availability to walk each covered day. Files are staged but
  not committed — want me to commit?