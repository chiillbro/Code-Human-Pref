# F1 Race Replay — Local Setup Guide

## Table of Contents

- [F1 Race Replay — Local Setup Guide](#f1-race-replay--local-setup-guide)
  - [Table of Contents](#table-of-contents)
  - [What Is This Project?](#what-is-this-project)
    - [Key capabilities](#key-capabilities)
  - [Who Is This For?](#who-is-this-for)
  - [How It Works](#how-it-works)
  - [Prerequisites](#prerequisites)
    - [System-level dependencies](#system-level-dependencies)
  - [Setup on macOS](#setup-on-macos)
    - [Notes for macOS](#notes-for-macos)
  - [Setup on Windows](#setup-on-windows)
    - [Notes for Windows](#notes-for-windows)
  - [Running the Application](#running-the-application)
    - [GUI Mode (default)](#gui-mode-default)
    - [CLI Mode](#cli-mode)
    - [Direct Command Line](#direct-command-line)
    - [Replay Controls](#replay-controls)
    - [Running the Insights Menu Standalone](#running-the-insights-menu-standalone)
    - [Running the Telemetry Stream Viewer](#running-the-telemetry-stream-viewer)
    - [Running the Example Pit Wall Window](#running-the-example-pit-wall-window)
  - [Running Tests](#running-tests)
  - [Project Architecture Overview](#project-architecture-overview)
    - [Key data flow](#key-data-flow)
  - [Potential Refactors, Bug Fixes, and Feature Ideas](#potential-refactors-bug-fixes-and-feature-ideas)
    - [Bug Fixes](#bug-fixes)
    - [Refactors](#refactors)
    - [New Feature Ideas (small-session scope)](#new-feature-ideas-small-session-scope)
  - [Troubleshooting](#troubleshooting)
  - [Contributing](#contributing)

---

## What Is This Project?

**F1 Race Replay** is a Python desktop application that lets you visualize and replay Formula 1 race sessions using real telemetry data sourced from the [FastF1](https://github.com/theOehrly/Fast-F1) library (which wraps the official F1 API). It renders an interactive 2D track map with live driver positions, a leaderboard, weather info, tyre strategy data, and playback controls — essentially a personal "pit wall" experience for data-loving F1 fans.

### Key capabilities

- **Race Replay**: Watch any race from 2018 onwards unfold frame-by-frame on a 2D track map with live leaderboard, weather panel, DRS zones, and safety car / flag indicators.
- **Qualifying Replay**: Load a qualifying session and inspect each driver's fastest lap telemetry (speed, gear, throttle, brake) plotted over distance, with comparison overlays against the pole-sitter.
- **Sprint & Sprint Qualifying**: Full support for sprint race weekends.
- **Live Telemetry Stream**: A TCP socket server (port 9999) broadcasts telemetry JSON in real-time so external tools and custom "Pit Wall" windows can consume it.
- **Insights Menu**: A floating Qt window that launches alongside the replay, providing one-click access to analysis tools like a telemetry stream viewer and a driver live telemetry chart.
- **Bayesian Tyre Degradation Model**: A state-space model estimates tyre health in real-time during the replay, accounting for fuel load, track abrasion, compound type, and weather conditions.
- **Custom Insight Windows**: Developers can subclass `PitWallWindow` to create their own data-driven widgets that automatically connect to the telemetry stream.

---

## Who Is This For?

| Audience | Why they'd use it |
|---|---|
| **F1 fans** | Relive races with full telemetry overlays — see DRS usage, pit stops, safety cars, tyre strategy, and driver battles. |
| **Data analysts / students** | Explore a rich dataset (speed, gear, throttle, brake, weather, tyre life) for any race since 2018. |
| **Python developers** | Contribute features to an active open-source project that blends data engineering, real-time rendering (Arcade), desktop UI (PySide6/Qt), and statistical modelling (Bayesian Kalman filter). |
| **Hobbyist sim-racers** | Compare real-world F1 telemetry to simulator data or simply study racing lines and braking points. |

---

## How It Works

1. **Data fetching**: FastF1 downloads session telemetry from the F1 API and caches it locally (`.fastf1-cache/`).
2. **Processing**: The app resamples every driver's telemetry onto a common 25 FPS timeline, extracts weather data, builds frames with position/speed/gear/tyre info, and serialises the result to a pickle file (`computed_data/`) for fast subsequent loads.
3. **Rendering**: The Arcade game engine draws the track outline, driver dots, HUD (leaderboard, weather, lap counter, progress bar, controls), and DRS zones at 60 FPS.
4. **Telemetry streaming**: A background TCP server broadcasts each frame as JSON so that PySide6-based insight windows (running in separate processes) can render live charts.

---

## Prerequisites

| Requirement | Version |
|---|---|
| **Python** | 3.11 or newer |
| **pip** | Latest (comes with Python) |
| **Git** | Any recent version |
| **OS** | macOS 12+ / Windows 10+ (Linux should also work but is untested by the maintainer) |

### System-level dependencies

- **macOS**: Xcode Command Line Tools (`xcode-select --install`) may be needed for compiling some Python packages.
- **Windows**: A working C++ build toolchain is occasionally needed for `scipy` or `numpy` wheels. Usually pre-built wheels are available and this is not an issue.

---

## Setup on macOS

```bash
# 1. Clone the repository
git clone https://github.com/IAmTomShaw/f1-race-replay.git
cd f1-race-replay

# 2. Create a virtual environment
python3 -m venv venv
source venv/bin/activate

# 3. Upgrade pip (recommended)
pip install --upgrade pip

# 4. Install dependencies
pip install -r requirements.txt

# 5. (Optional) Create FastF1 cache folder manually if it isn't created on first run
mkdir -p .fastf1-cache

# 6. Run the application (GUI mode — default)
python main.py
```

### Notes for macOS

- On **Apple Silicon** (M1/M2/M3), all dependencies have native arm64 wheels. No Rosetta required.
- If you encounter `ModuleNotFoundError: No module named 'PySide6'`, ensure you activated the virtual environment (`source venv/bin/activate`).
- The first run for a given race will download telemetry from the F1 API and process it — this can take 1–5 minutes depending on your connection. Subsequent runs for the same session load from the local cache instantly.

---

## Setup on Windows

```powershell
# 1. Clone the repository
git clone https://github.com/IAmTomShaw/f1-race-replay.git
cd f1-race-replay

# 2. Create a virtual environment
python -m venv venv
.\venv\Scripts\activate

# 3. Upgrade pip (recommended)
pip install --upgrade pip

# 4. Install dependencies
pip install -r requirements.txt

# 5. (Optional) Create FastF1 cache folder manually if it isn't created on first run
mkdir .fastf1-cache

# 6. Run the application (GUI mode — default)
python main.py
```

### Notes for Windows

- Use **PowerShell** or **Windows Terminal** for best results. The classic `cmd.exe` also works.
- If `python` is not recognised, try `py` instead, or ensure Python is added to your PATH during installation.
- PySide6 and Arcade both require a functioning OpenGL driver. If you're on a VM or RDP session without GPU, you may see rendering errors.

---

## Running the Application

### GUI Mode (default)

```bash
python main.py
```

Opens a PySide6 window where you select the year, race weekend, and session type (Race, Qualifying, Sprint, etc.). Clicking a session launches the replay in a new Arcade window.

### CLI Mode

```bash
python main.py --cli
```

An interactive terminal prompt (powered by `questionary` + `rich`) that walks you through year → round → session selection.

### Direct Command Line

```bash
# Race replay for 2024 Round 1
python main.py --viewer --year 2024 --round 1

# Qualifying replay
python main.py --viewer --year 2024 --round 1 --qualifying

# Sprint race
python main.py --viewer --year 2024 --round 1 --sprint

# Sprint qualifying
python main.py --viewer --year 2024 --round 1 --sprint-qualifying

# Without HUD
python main.py --viewer --year 2024 --round 1 --no-hud

# Force re-download of telemetry data (skip cache)
python main.py --viewer --year 2024 --round 1 --refresh-data

# List all rounds for a year
python main.py --list-rounds --year 2024

# List sprint rounds for a year
python main.py --list-sprints --year 2024
```

### Replay Controls

| Key | Action |
|---|---|
| `SPACE` | Pause / resume |
| `←` / `→` | Rewind / fast-forward (hold for continuous seek) |
| `↑` / `↓` | Increase / decrease playback speed |
| `1`–`4` | Set speed to 0.5× / 1× / 2× / 4× |
| `R` | Restart replay |
| `D` | Toggle DRS zones |
| `B` | Toggle progress bar |
| `L` | Toggle driver name labels on track |
| `H` | Toggle help popup |
| `I` | Toggle session info banner |
| `ESC` | Close window |
| Click driver | Select driver (shows detailed telemetry side-panel) |
| Shift+click | Multi-select drivers |

### Running the Insights Menu Standalone

```bash
python -m src.gui.insights_menu
```

### Running the Telemetry Stream Viewer

```bash
python -m src.insights.telemetry_stream_viewer
```

### Running the Example Pit Wall Window

```bash
python -m src.insights.example_pit_wall_window
```

---

## Running Tests

The project does **not** currently include a test suite (no `tests/` directory or test configuration for the `f1-race-replay` codebase). This is one of the key improvement areas identified below.

If you want to run basic validation manually:

```bash
# Verify all imports resolve correctly
python -c "from src.f1_data import get_race_telemetry, enable_cache, load_session; print('Imports OK')"

# Verify the GUI window initialises (will open briefly)
python -c "from PySide6.QtWidgets import QApplication; import sys; app = QApplication(sys.argv); print('PySide6 OK')"

# Verify Arcade works
python -c "import arcade; print(f'Arcade {arcade.__version__} OK')"
```

To add automated tests (recommended), see the feature suggestions below.

---

## Project Architecture Overview

```
f1-race-replay/
├── main.py                         # Entry point: arg parsing, GUI/CLI dispatch
├── requirements.txt                # Python dependencies
│
├── src/
│   ├── f1_data.py                  # Core data layer: FastF1 fetch, resample, frame building, caching
│   ├── run_session.py              # Glue: launches Arcade window + Insights menu as subprocesses
│   ├── ui_components.py            # All reusable Arcade UI widgets (2300+ lines)
│   ├── bayesian_tyre_model.py      # Bayesian Kalman-filter tyre degradation model
│   ├── tyre_degradation_integration.py  # Adapter between the model and the UI
│   │
│   ├── interfaces/
│   │   ├── race_replay.py          # Arcade Window subclass for race replay
│   │   └── qualifying.py           # Arcade Window subclass for qualifying replay
│   │
│   ├── cli/
│   │   └── race_selection.py       # Interactive CLI menu (questionary + rich)
│   │
│   ├── gui/
│   │   ├── race_selection.py       # PySide6 main window for picking sessions
│   │   ├── settings_dialog.py      # Settings dialog (cache path config)
│   │   ├── insights_menu.py        # Floating insights launcher menu
│   │   ├── pit_wall_window.py      # Base class for custom telemetry insight windows
│   │   └── pit_wall_window_template.py  # Copy-paste template for new insights
│   │
│   ├── insights/
│   │   ├── example_pit_wall_window.py    # Demo insight window
│   │   ├── driver_telemetry_window.py    # Live per-driver speed/gear/throttle/brake charts
│   │   └── telemetry_stream_viewer.py    # Raw JSON telemetry log viewer
│   │
│   ├── services/
│   │   └── stream.py               # TCP telemetry server + Qt client
│   │
│   └── lib/
│       ├── settings.py             # Singleton JSON settings manager
│       ├── time.py                 # Time parsing / formatting utilities
│       └── tyres.py                # Tyre compound string ↔ int mapping
│
├── docs/
│   ├── PitWallWindow.md            # Developer guide for custom insights
│   └── InsightsMenu.md             # Guide for adding menu buttons
│
├── images/
│   ├── controls/                   # Playback control icon PNGs
│   ├── tyres/                      # Tyre compound icon PNGs
│   └── weather/                    # Weather icon PNGs
│
├── resources/                      # Background image, preview screenshots
├── .fastf1-cache/                  # FastF1 API cache (auto-created)
└── computed_data/                  # Pre-processed pickle files (auto-created)
```

### Key data flow

```
FastF1 API  →  f1_data.py (fetch + resample at 25 FPS)  →  pickle cache
                         ↓
              race_replay.py / qualifying.py  (Arcade rendering loop)
                         ↓
              TelemetryStreamServer (TCP :9999)  →  Insight windows (PySide6)
```

---

## Potential Refactors, Bug Fixes, and Feature Ideas

Below is a categorised list of actionable items, each scoped for a **small session** (1–4 hours). They are ordered roughly by impact and difficulty within each category.

### Bug Fixes

| # | Issue | Where | Details |
|---|---|---|---|
| 1 | **`total_dist_so_far` never updated** | `f1_data.py` `_process_single_driver()` line ~95 | `total_dist_so_far` is initialised to `0.0` but never incremented after each lap. This means the `race_d_lap` (race distance) is incorrect for every lap after the first — it just uses per-lap distance instead of cumulative distance. Fix: update `total_dist_so_far += d_lap.max()` at the end of each lap loop. |
| 2 | **`throttle_all`/`brake_all` not included in batch `all_arrays`** | `f1_data.py` `_process_single_driver()` ~line 108 | These two arrays are concatenated and sorted separately from the main vectorised batch, creating inconsistent code and a risk of them being accidentally omitted from sorting. They should be added to `all_arrays`. |
| 3 | **Leaderboard inaccuracy on lap 1 / pit stops** | `race_replay.py`, `ui_components.py` | Documented known issue. Position is computed from cumulative `progress_m` which relies on projected track distance. Drivers in the pits temporarily appear further ahead/behind owing to their x,y being off the racing line. A fix would exclude pit-in/out frames from position calculation or use lap number + relative distance within the lap. |
| 4 | **Gap calculation uses hardcoded 200 km/h reference** | `ui_components.py` `DriverInfoComponent` | `time = dist / 55.56` uses a constant speed. This produces inaccurate gap estimates on slow/fast circuits. Consider using the actual leader speed or a configurable reference speed. |
| 5 | **`_draw_speed_comp` uses `and` instead of `in` for dict check** | `ui_components.py` ~line 1837 | `if 'speed+' and 'speed-' in self._control_textures:` evaluates as `if 'speed+' and ('speed-' in ...)` which always truths the first condition. Should be `if 'speed+' in self._control_textures and 'speed-' in self._control_textures:`. |
| 6 | **`'gap_text' in locals()` anti-pattern** | `ui_components.py` `LeaderboardComponent.draw()` | Uses `'gap_text' in locals()` to check if variable is defined — fragile. Better to initialise `gap_text = ""` before the if-else block. |
| 7 | **`selected_drivers` attribute not initialised** | `race_replay.py` | `selected_drivers` is first referenced via `getattr(self, "selected_drivers", [])` in `on_draw`. It should be explicitly initialised in `__init__` for clarity. |
| 8 | **`parse_time_string` prints debug output** | `lib/time.py` | Several `print('1parse_time_string output: None')` debug statements are left in production code. They should be removed or converted to logging. |
| 9 | **Multiprocessing + FastF1 session pickling** | `f1_data.py` | `_process_single_driver` receives the full `session` object via `pool.map`. FastF1 `Session` objects are large and may not pickle cleanly across all platforms. Consider passing only the minimal data each worker needs. |

### Refactors

| # | Area | Details |
|---|---|---|
| 1 | **Split `ui_components.py` (2300+ lines)** | This monolithic file contains 15+ classes. Split into separate files under `src/ui/` (e.g., `leaderboard.py`, `weather.py`, `controls.py`, `progress_bar.py`, `driver_info.py`). |
| 2 | **Extract duplicated `update_scaling` / `world_to_screen` / `_interpolate_points`** | Both `race_replay.py` and `qualifying.py` duplicate these methods verbatim. Extract into a shared mixin or utility module (e.g., `src/lib/track_geometry.py`). |
| 3 | **Replace `sys.argv` parsing with `argparse`** | `main.py` uses manual `sys.argv.index(...)` parsing. Switching to `argparse` would add help text, validation, and proper error handling for free. |
| 4 | **Replace `sys.argv` checks inside data functions** | `f1_data.py` checks `"--refresh-data" not in sys.argv` deep inside data processing functions. This tightly couples data logic to CLI args. Instead, pass a `refresh` parameter from the caller. |
| 5 | **Add Python `logging` module** | The codebase uses `print()` throughout. Adopting `logging` with configurable levels would make debugging easier and silence noise in production. |
| 6 | **Type hints and dataclasses** | Many functions pass around dicts with implicit schemas (frames, driver data, weather). Defining typed `dataclass`es or `TypedDict`s would improve maintainability and enable mypy checking. |
| 7 | **Consistent code style** | Mixed indentation (2-space in some files, 4-space in others, e.g., `stream.py` uses 2-space). Run a formatter like `black` or `ruff format` project-wide. |
| 8 | **`SettingsManager` singleton is not used for `computed_data` path** | `f1_data.py` hardcodes `"computed_data/"` path instead of using `settings.computed_data_location`. |
| 9 | **`_format_wind_direction` duplicated** | Present in both `ui_components.py` (module-level) and `race_replay.py` (method). Keep one. |

### New Feature Ideas (small-session scope)

| # | Feature | Difficulty | Details |
|---|---|---|---|
| 1 | **Add a `pytest` test suite** | Easy | Write unit tests for `lib/time.py`, `lib/tyres.py`, `lib/settings.py`, and `f1_data.py` helper functions. Add a `tests/` directory and a `pytest.ini` or `pyproject.toml` config. This immediately improves project quality and contributor confidence. |
| 2 | **Pit stop markers on the progress bar** | Easy | Detect pit stop events (when a driver's tyre compound changes between frames) and add pit stop markers to `RaceProgressBarComponent`. The infrastructure already supports custom event types. |
| 3 | **Lap time chart insight window** | Medium | Create a new `PitWallWindow` subclass that plots lap times over the race for all (or selected) drivers — a common F1 analysis chart. The telemetry stream already provides lap + time data. |
| 4 | **Position change chart** | Medium | A `PitWallWindow` subclass showing position vs. lap for each driver (spaghetti chart). Very popular in F1 analytics. |
| 5 | **Tyre strategy timeline** | Medium | Horizontal bar chart in a new insight window showing each driver's tyre stints (compound + lap range), similar to the F1 TV broadcast graphic. |
| 6 | **Export telemetry to CSV** | Easy | Add a button or CLI flag to export the processed frame data to CSV for use in Jupyter notebooks, R, or Excel. |
| 7 | **Dark/light theme for Qt windows** | Easy | Add a theme toggle in `SettingsDialog` and apply a Qt stylesheet to all PySide6 windows. |
| 8 | **Practice session support** | Hard | Extend `f1_data.py` to handle FP1/FP2/FP3 sessions. The data fetching already works via FastF1 — the challenge is designing a useful replay UI for practice (possibly showing driver runs/stints rather than a continuous replay). |
| 9 | **Keyboard shortcut overlay for qualifying** | Easy | The qualifying window defines a custom controls popup list but doesn't render the on-screen legend labels consistently with the race window. Wire up `LegendComponent` with qualifying-specific controls. |
| 10 | **Add `--year` auto-detection** | Easy | Default `--year` to the current calendar year dynamically instead of hardcoding `2025`. Use `datetime.date.today().year`. |
| 11 | **Configurable telemetry port** | Easy | The telemetry stream port is hardcoded to `9999`. Add a setting or `--port` CLI argument. |
| 12 | **Progress bar click-to-seek in qualifying** | Easy | The race replay window supports clicking the progress bar to seek. The qualifying window doesn't have a progress bar at all — adding one would improve UX. |
| 13 | **Overtake counter insight** | Medium | Detect position changes between consecutive frames and count overtakes per driver. Display in a simple `PitWallWindow`. |
| 14 | **Circuit mini-map in driver info panel** | Medium | Show a small track outline in the driver info box with the selected driver's current position highlighted. |
| 15 | **Multi-window sync** | Hard | When the user pauses/seeks in the main Arcade window, all connected insight windows should reflect the paused state instantly (currently there's a ~1s delay because the broadcast is only in `on_update`). Move broadcast to after every state change. |

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `ModuleNotFoundError: No module named 'fastf1'` | Make sure you activated the virtual environment and ran `pip install -r requirements.txt`. |
| `ModuleNotFoundError: No module named 'PySide6'` | Same as above. On some Linux distros you may also need `sudo apt install libegl1`. |
| First run takes a very long time | FastF1 is downloading session data from the F1 API. Subsequent runs for the same session will use the local cache. |
| `OSError: [Errno 48] Address already in use` (port 9999) | Another instance of the app (or another process) is using port 9999. Close it, or wait for it to release the port. |
| Arcade window is blank / crashes | Ensure your machine has a working OpenGL driver. This is common on VMs or headless servers. |
| `pickle.UnpicklingError` when loading cached data | The pickle format may have changed between versions. Delete the `computed_data/` folder and re-run. |
| Replay looks laggy | Try reducing the window size, or press `↓` to lower the playback speed. Rendering many UI elements at high resolution can be demanding. |

---

## Contributing

1. Fork the repository and create a feature branch.
2. Follow the existing code style (or apply `black` formatting).
3. Make sure all existing functionality still works after your changes.
4. Submit a pull request with a clear description and screenshots/GIFs if the change is visual.

See [roadmap.md](./roadmap.md) for the project vision and [contributors.md](./contributors.md) for existing contributors.

---

*This guide was written based on an exhaustive review of the f1-race-replay codebase as of February 2026.*
