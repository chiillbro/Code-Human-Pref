## What changed and why

### 1. Removed `QPixmap` and `QFont` from the `PySide6.QtGui` import

The entire `from PySide6.QtGui import QPixmap, QFont` line was deleted. Neither name appears anywhere in the file's actual code — `QPixmap` was never used at all, and the font work done in `_setup_ui` uses the plain `font()` instance method on `QLabel` objects, which returns a `QFont` without any import being needed. Keeping dead imports is misleading because it implies those types are needed for something, and it adds to the cognitive surface area of the module.

### 2. Extracted `_populate_year_event_tree` and replaced both duplicate bodies

`_on_all_years_events_loaded` and `_on_filtered_races_loaded` were line-for-line identical: both called `self.schedule_tree.clear()`, both looped over events extracting the same four fields (`year`, `event_name`, `country`, `date`) in the same order, both constructed `QTreeWidgetItem` with those four strings, both called `setData(0, Qt.UserRole, event)` and `addTopLevelItem`, and both ended with the same `resizeColumnToContents(0)` / `resizeColumnToContents(1)` pair. The new `_populate_year_event_tree(events)` method holds that logic once with a clear docstring explaining which two callers it serves. Each slot is now a one-liner that delegates straight to the helper. The `populate_schedule` method (which builds the Round/Event/Country/Start Date layout for the normal single-year view) was deliberately left alone — it is a structurally different path that stamps a `year` key onto each event dict before storing it, and uses a different first-column field (`round_number` rather than `year`), so it is not a duplicate of the helper.
