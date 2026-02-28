# RPG Tactical Fantasy Game — Local Setup Guide

> **Version**: 1.0.4 | **License**: GPL-3.0 | **Python**: 3.13+ | **Package Manager**: [uv](https://docs.astral.sh/uv/)

---

## Table of Contents

1. [What Is This Project?](#1-what-is-this-project)
2. [Who Is It For?](#2-who-is-it-for)
3. [Architecture Overview](#3-architecture-overview)
4. [Local Setup — macOS](#4-local-setup--macos)
5. [Local Setup — Windows](#5-local-setup--windows)
6. [Running the Game](#6-running-the-game)
7. [Running the Tests](#7-running-the-tests)
8. [Project Structure Walkthrough](#8-project-structure-walkthrough)
9. [Potential Refactors, Bug Fixes & Feature Ideas](#9-potential-refactors-bug-fixes--feature-ideas)
10. [Contributing Guidelines](#10-contributing-guidelines)

---

## 1. What Is This Project?

**RPG Tactical Fantasy Game** is an open-source, turn-based tactical RPG built entirely in Python using the **pygame-ce** (Community Edition) library. Inspired by games like *Fire Emblem* and *Final Fantasy Tactics*, the game features:

- **Turn-based grid combat** on 2D tile maps (22×14 grid, 48px tiles)
- **4 playable levels** with escalating difficulty
- **6 unique playable characters** (e.g., Jist the innkeeper, Braern the elf ranger, Thokdrum the dwarf warrior) with different races, classes, stats, and equipment
- **AI-controlled enemies** with multiple strategies (static, passive, semi-active, active)
- **Rich item system**: weapons (swords, bows, daggers, axes, spells), shields, armor, potions, consumables, keys, books, spellbooks
- **RPG mechanics**: XP and leveling, alteration effects (poison, stun, buffs), skills (multi-attack, ally boost), equipment durability, parrying
- **Missions & objectives**: kill missions, position-based objectives, turn limits, optional side-objectives
- **Interactive world**: shops for buying/selling, chests (with lock-picking), buildings to visit, fountains for healing, NPCs with dialog, portals, breakable walls
- **Save/Load system** with 3 save slots, persistent across sessions
- **Multi-language support**: English, French, Spanish, Chinese (Simplified)
- **Sound effects and a soundtrack** ("Village Consort" by Kevin MacLeod)

The game data (characters, foes, items, maps) is configured entirely via **XML** and **Tiled TMX** map files, making it highly moddable without touching any Python code.

---

## 2. Who Is It For?

| Audience | How They Can Use It |
|----------|-------------------|
| **Players** | Download a release (Windows 64-bit exe available) or run from source to enjoy a classic tactical RPG |
| **Python learners** | Study a real-world pygame project with clear entity hierarchies, scene management, XML parsing, and AI logic |
| **Game dev hobbyists** | Fork and extend with new levels, characters, items, or mechanics — data-driven design makes modding approachable |
| **Open-source contributors** | Fix bugs, implement TODOs, add translations, contribute art/sound, improve balancing (see Section 9) |
| **CS students** | Explore patterns like Entity-Component hierarchy, state machines, factory loading, scene management, and test design |

---

## 3. Architecture Overview

```
main.py                          ← Entry point: pygame init, main loop, event delegation
├── src/
│   ├── constants.py             ← Global constants (colors, sizes, defaults)
│   ├── scenes/
│   │   ├── scene.py             ← Abstract base Scene class + QuitActionKind enum
│   │   ├── start_scene.py       ← Main menu (New/Load/Options/Exit)
│   │   ├── level_loading_scene.py ← Fade-in/out transition screen
│   │   └── level_scene.py       ← Core gameplay (2348 lines) — combat, AI, menus, interactions
│   ├── services/
│   │   ├── scene_manager.py     ← Orchestrates scene transitions
│   │   ├── menu_creator_manager.py ← Factory for all UI menus/popups
│   │   ├── load_from_xml_manager.py ← Parses XML game data (characters, foes, items, etc.)
│   │   ├── load_from_tmx_manager.py ← Parses Tiled .tmx map files
│   │   ├── save_state_manager.py ← Serializes game state to XML
│   │   ├── options_manager.py   ← Persists options to JSON
│   │   ├── menus.py             ← Enum definitions for all menu action types
│   │   ├── language.py          ← Localization loader
│   │   └── global_foes.py       ← Foe-to-mission registry
│   ├── game_entities/           ← 29 entity classes (see hierarchy below)
│   │   ├── entity.py            ← Base: name, position, sprite
│   │   ├── destroyable.py       ← Adds HP, defense, damage
│   │   ├── movable.py           ← Movement, AI, XP, inventory
│   │   ├── character.py         ← Allies: race, class, equipment
│   │   ├── player.py            ← Player-controlled characters
│   │   ├── foe.py               ← Enemies: keywords, loot, scaling
│   │   └── ... (item.py, weapon.py, shield.py, shop.py, chest.py, etc.)
│   └── gui/
│       ├── animation.py         ← Frame-based animation engine
│       ├── fade_in_out_animation.py ← Loading screen fade effect
│       ├── constant_sprites.py  ← Preloaded sprite cache
│       ├── fonts.py             ← Font loader
│       ├── sidebar.py           ← Bottom HUD (turn info, missions, entity details)
│       ├── position.py          ← Hashable Vector2 position
│       └── tools.py             ← FPS display, distance calc, gauge colors
├── data/                        ← Game data (XML) + localization (per-language folders)
├── maps/                        ← Tiled .tmx map files per level
├── imgs/                        ← Sprites and tilesets
├── fonts/                       ← Font files
├── sound_fx/                    ← Music and sound effects
├── tests/                       ← unittest test suite (15 test files)
└── saves/                       ← Auto-created: save slots + options.json
```

### Entity Inheritance Hierarchy

```
Entity
├── Obstacle
├── Objective
├── Portal
├── Door
├── Chest
├── Building → Shop
├── Fountain
├── Destroyable → Breakable
│               → Movable → Character → Player
│                         → Foe
├── Item → Consumable → Potion
│        → Equipment → Weapon
│                    → Shield
│        → Book → Spellbook
│        → Key
│        → Gold
├── Alteration
├── Effect
├── Skill
├── Mission
```

---

## 4. Local Setup — macOS

### Prerequisites

| Tool | Minimum Version | Install Command |
|------|----------------|----------------|
| **Python** | 3.13+ | `brew install python@3.13` or download from [python.org](https://www.python.org/downloads/) |
| **uv** | Latest | `curl -LsSf https://astral.sh/uv/install.sh \| sh` or `brew install uv` |
| **Git** | Any | `xcode-select --install` (comes with Xcode CLI tools) |
| **Git LFS** | Any | `brew install git-lfs` |

### Step-by-Step

```bash
# 1. Clone the repository
git clone https://github.com/Grimmys/rpg_tactical_fantasy_game.git
cd rpg_tactical_fantasy_game

# 2. Install Git LFS and pull binary assets (images, sounds, fonts)
git lfs install
git lfs pull

# 3. Verify Python version (must be 3.13+)
python3 --version

# 4. Install uv if not already installed
#    Option A: via curl
curl -LsSf https://astral.sh/uv/install.sh | sh
#    Option B: via Homebrew
brew install uv

# 5. Sync dependencies (uv reads pyproject.toml and uv.lock automatically)
uv sync

# 6. Run the game
uv run main.py
```

### macOS-Specific Notes

- **SDL2** (required by pygame-ce) is bundled with the pygame-ce wheel — no separate install needed.
- If you see font rendering issues, ensure Git LFS pulled the `.ttf` files properly: `git lfs ls-files` should list them.
- On Apple Silicon (M1/M2/M3), `uv` and `pygame-ce` have native ARM wheels — no Rosetta needed.

---

## 5. Local Setup — Windows

### Prerequisites

| Tool | Minimum Version | Install Method |
|------|----------------|---------------|
| **Python** | 3.13+ | Download from [python.org](https://www.python.org/downloads/) — **check "Add Python to PATH"** during install |
| **uv** | Latest | `powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 \| iex"` |
| **Git** | Any | Download from [git-scm.com](https://git-scm.com/download/win) |
| **Git LFS** | Any | Download from [git-lfs.com](https://git-lfs.com/) or comes bundled with Git for Windows |

### Step-by-Step (PowerShell or Command Prompt)

```powershell
# 1. Clone the repository
git clone https://github.com/Grimmys/rpg_tactical_fantasy_game.git
cd rpg_tactical_fantasy_game

# 2. Install Git LFS and pull binary assets
git lfs install
git lfs pull

# 3. Verify Python version
python --version
# Should output: Python 3.13.x

# 4. Install uv (if not already installed)
powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"

# 5. Sync dependencies
uv sync

# 6. Run the game
uv run main.py
```

### Windows-Specific Notes

- The game auto-handles **High DPI** on Windows using `windll.user32.SetProcessDPIAware()`.
- A **prebuilt 64-bit Windows executable** is available on the [Releases page](https://github.com/Grimmys/rpg_tactical_fantasy_game/releases) — no Python needed.
- If you encounter issues with `lxml`, install the C build tools: `pip install lxml` may require Visual C++ Build Tools, but `uv` should fetch a prebuilt wheel.

---

## 6. Running the Game

```bash
# Standard run with uv (recommended — manages virtualenv and deps automatically)
uv run main.py

# Alternative: if you're using a manual virtual environment
python -m venv .venv
source .venv/bin/activate    # macOS/Linux
# .venv\Scripts\activate     # Windows
pip install lxml pygame-ce pygame-popup==0.11.2 pytest pytmx
python main.py
```

### Game Controls

| Input | Action |
|-------|--------|
| **Left click** (on player) | Select player, choose move tile, select action |
| **Left click** (on empty tile) | Open/close main menu |
| **Left click** (on entity) | View entity info |
| **Right click** | Deselect player / cancel last action |
| **Right click** (on entity) | Show entity's possible movements |
| **Esc** | Close top-layer menu |

### Gameplay Flow

1. **Start Screen** → New Game, Load Game, Options (speed, screen size, language), or Exit
2. **Level Loading** → Chapter title with fade-in/out animation
3. **Level Gameplay** → Turn-based: Player Phase → Ally Phase → Foe Phase
4. **Victory/Defeat** → Mission objectives determine outcome → Next level or back to menu

---

## 7. Running the Tests

> ⚠️ **Warning**: Tests may modify game configuration (e.g., save files could be deleted). Only run tests in a development environment.

```bash
# Run the full test suite
uv run pytest tests/ -v

# Run a specific test file
uv run pytest tests/test_character.py -v

# Run a specific test method
uv run pytest tests/test_character.py::TestCharacter::test_init_character -v

# Run with unittest runner instead (alternative)
uv run python -m pytest tests/ -v
```

### Test Suite Overview

The project has **15 test files** covering core game entity logic:

| Test File | What It Tests |
|-----------|--------------|
| `test_entity.py` | Entity creation, display, string formatting, position collision |
| `test_destroyable.py` | Damage calculation (physical/spiritual), healing, overkill |
| `test_movable.py` | Movement system, turn states, XP/leveling, inventory, alterations |
| `test_character.py` | Character init, attack calc, parry, equipment, equip/unequip, stat growth |
| `test_weapon.py` | Hit calculation, durability, strong-against multiplier, charge mechanic |
| `test_equipment.py` | Equipment creation, display modes, grey toggle |
| `test_item.py` | Item creation, string representation, XML save |
| `test_alteration.py` | Alteration init, duration tracking, increment, finished check |
| `test_chest.py` | Chest content determination (weighted random), opening behavior |
| `test_building.py` | Building creation, interaction (talks/gold/item), interaction removal |
| `test_shop.py` | Buy/sell mechanics, stock management, gold balance |
| `test_mission.py` | Mission state updates for all mission types |
| `test_start_screen.py` | Start screen initialization |
| `test_save_and_load.py` | Save/load round-trip for game state |
| `test_level_utilitarian.py` | Level utility: tile availability, pathfinding, entity queries |

### Test Infrastructure

- **`tests/tools.py`**: `minimal_setup_for_game()` — initializes pygame (headless), loads fonts, sprites, races, classes
- **`tests/random_data_library.py`**: Factory functions for generating random test data (`random_character_entity()`, `random_weapon()`, `random_foe_entity()`, etc.)

---

## 8. Project Structure Walkthrough

### Data Layer (`data/`)

All game content is defined in XML files — highly moddable:

| File | Content |
|------|---------|
| `characters.xml` | 6 characters with stats, sprites, equipment, dialog, race/class |
| `foes.xml` | Enemy types: stats, AI strategy, loot tables, level scaling |
| `items.xml` | Full item catalog: weapons, shields, armor, potions, keys, books |
| `classes.xml` | 4 classes: innkeeper, warrior, ranger, spy (with stat growth rates + skills) |
| `races.xml` | 5 races: human, elf, dwarf, centaur, gnome (constitution, movement, skills) |
| `skills.xml` | Skill definitions (multi-attack, lock-picking, covered, brutal hitter, etc.) |
| `alterations.xml` | Status effects (poison, burn, stun, defense boost, etc.) |
| `fountains.xml` | Fountain effect definitions |

### Localization (`data/{en,fr,es,zh_cn}/`)

Each language folder contains:
- `text.py` — All UI strings as Python variables/functions
- `fonts_description.py` — Font definitions (Chinese uses different fonts)
- `maps/` — Localized map metadata (level names, mission descriptions)

### Maps (`maps/level_{0..3}/`)

Maps are created with the [Tiled Map Editor](https://www.mapeditor.org/) and stored as `.tmx` XMLfiles. Each level folder contains:
- `map.tmx` — Ground tiles, obstacles, entity placements
- `dialog_*.txt` — Event dialog text
- `house_dialog_*.txt` — Building interaction text

---

## 9. Potential Refactors, Bug Fixes & Feature Ideas

Below is a curated list of improvements suitable for contributors of all levels, organized by category. Each item can be tackled in a small, focused coding session.

---

### 🐛 Bug Fixes

#### B1. `INTERACTION_OPACITY` set to 500 (should be max 255)
- **File**: `src/gui/constant_sprites.py`
- **Issue**: Alpha opacity is defined as `500`, but pygame alpha values are clamped to 0–255. This is either a bug (intended value was likely 128 or 200) or misleading dead code.
- **Fix**: Change to a valid alpha value (e.g., `200`).

#### B2. Hardcoded English in `Fountain.drink()`
- **File**: `src/game_entities/fountain.py` (around line 80)
- **Issue**: Strings like `"The fountain is empty..."` and `"{self.times} remaining uses"` are not localized.
- **Fix**: Move these strings to the language `text.py` files and use the localization system.

#### B3. `Movable.get_item()` returns `False` instead of `None`
- **File**: `src/game_entities/movable.py` (around line 399)
- **Issue**: Method returns `bool` when it should return `Optional[Item]`. A TODO in the code confirms this.
- **Fix**: Return `None` instead of `False`, update callers.

#### B4. `Movable.unequip()` returns `-1` instead of `None`
- **File**: `src/game_entities/movable.py` (around line 429)
- **Issue**: Magic return values. Should use `Optional` return type.
- **Fix**: Return `None` instead of `-1`, update callers.

#### B5. Sound determination based on entity name strings
- **File**: `src/game_entities/movable.py` (around lines 282-293)
- **Issue**: `if self.name == "chrisemon"` / `"skeleton"` / `"necrophage"` to pick a walking sound. Fragile — breaks if entity names change.
- **Fix**: Use a race or species attribute, or a `sound_profile` field in the entity data.

---

### 🔧 Refactoring Opportunities

#### R1. Split `LevelScene` (2348 lines) into focused managers
- **File**: `src/scenes/level_scene.py`
- **Issue**: Massive god class handling rendering, input, AI, combat, menus, items, missions, shops, saving.
- **Approach**: Extract into:
  - `CombatManager` — duel logic, damage calculation, XP
  - `InteractionManager` — chest/door/portal/fountain/building/trade handlers
  - `TurnManager` — turn cycle, AI action processing
  - `LevelRenderer` — display logic
- **Difficulty**: Medium-Hard (but each extraction can be its own PR)

#### R2. Move popup creation from `level_scene.py` to `menu_creator_manager.py`
- **Files**: `src/scenes/level_scene.py`, `src/services/menu_creator_manager.py`
- **Issue**: 5 TODOs in `level_scene.py` note inline popup creation that belongs in the menu factory.
- **Difficulty**: Easy-Medium

#### R3. Replace wildcard imports (`from language import *`)
- **Files**: ~20+ files throughout `src/`
- **Issue**: `from src.services.language import *` pollutes namespaces, makes IDE support worse, and makes it hard to trace string origins.
- **Fix**: Use explicit imports like `from src.services.language import STR_NEW_TURN, TRANSLATIONS` or a namespace `import src.services.language as lang`.
- **Difficulty**: Easy but tedious

#### R4. Replace `dict[str, any]` with typed structures
- **Files**: `character.py`, `foe.py`, `load_from_xml_manager.py`, `level_scene.py`
- **Issue**: Data dicts like `races_data`, `classes_data`, `events`, `interaction` use untyped dictionaries.
- **Fix**: Create `dataclasses` or `TypedDict` definitions for `RaceData`, `ClassData`, `InteractionData`, `EventData`, etc.
- **Difficulty**: Medium

#### R5. Refactor `load_entities_from_save()` if/elif chain
- **File**: `src/services/load_from_xml_manager.py` (around line 234)
- **Issue**: Long sequence of if/elif for different entity types. A TODO already notes this.
- **Fix**: Use a dictionary mapping entity type strings to loader functions.
- **Difficulty**: Easy

#### R6. Consolidate victory/defeat booleans into an enum
- **File**: `src/scenes/level_scene.py` (around line 242)
- **Issue**: Two booleans (`victory`, `defeat`) that are mutually exclusive — classic boolean trap.
- **Fix**: Replace with `GameResult` enum: `IN_PROGRESS`, `VICTORY`, `DEFEAT`.
- **Difficulty**: Easy

#### R7. Centralize sound management
- **Files**: `src/game_entities/movable.py`, `src/scenes/level_scene.py`
- **Issue**: Sound effects loaded individually across multiple entity classes and the level scene with duplicated `pygame.mixer.Sound()` calls.
- **Fix**: Create a `SoundManager` service that preloads and provides all sounds.
- **Difficulty**: Medium

#### R8. Refactor parry logic into `Shield` class
- **File**: `src/game_entities/character.py` (around line 172)
- **Issue**: Parry calculation lives in `Character` but logically belongs to the `Shield` entity.
- **Fix**: Move parry logic to `Shield.attempt_parry()` method.
- **Difficulty**: Easy

---

### 🚀 New Feature Ideas

#### F1. Implement `load_breakables()` from TMX maps
- **File**: `src/services/load_from_tmx_manager.py` (line 385)
- **Issue**: Stub method that returns empty list. Breakable wall entities exist (`breakable.py`) but are never loaded from maps.
- **Goal**: Parse breakable tiles from TMX data layers and instantiate `Breakable` objects.
- **Difficulty**: Easy-Medium

#### F2. Implement `load_portals()` from TMX maps
- **File**: `src/services/load_from_tmx_manager.py` (line 392)
- **Issue**: Same as above — stub method. `Portal` class exists but is dead code.
- **Goal**: Parse paired portal tiles from TMX and instantiate `Portal` objects.
- **Difficulty**: Easy-Medium

#### F3. Implement mission items rewards
- **Files**: `load_from_tmx_manager.py` (line 114), `level_scene.py` (line 469)
- **Issue**: Two connected TODOs — parsing of item rewards for missions isn't implemented, and distributing optional objective rewards to players isn't either.
- **Difficulty**: Medium

#### F4. Add keyboard shortcuts for common actions
- **File**: `src/scenes/level_scene.py`
- **Current state**: Only `Esc` key is handled.
- **Ideas**: `I` for inventory, `E` for equipment, `S` for status, `Space` to end turn, `Tab` to cycle players.
- **Difficulty**: Easy

#### F5. Add a "confirm quit" dialog
- **File**: `src/scenes/level_scene.py`
- **Issue**: Quitting mid-level from the main menu goes straight back to start screen without confirmation.
- **Difficulty**: Easy

#### F6. Add difficulty settings
- **Files**: `src/services/options_manager.py`, `src/game_entities/foe.py`
- **Ideas**: Enemy stat multiplier (0.75x / 1x / 1.5x), starting gold adjustment, XP modifier.
- **Difficulty**: Medium

#### F7. Mission progress tracking in sidebar
- **File**: `src/gui/sidebar.py`
- **Issue**: Sidebar shows mission name and status, but not progress (e.g., "2/3 enemies killed").
- **Difficulty**: Medium

#### F8. Auto-save feature
- **Files**: `src/services/save_state_manager.py`, `src/scenes/level_scene.py`
- **Issue**: Currently only manual save at level start. No mid-level auto-save.
- **Difficulty**: Medium

#### F9. Complete French and Spanish translations
- **Files**: `data/fr/text.py`, `data/es/text.py`, `data/characters.xml` (dialog tags)
- **Issue**: Some dialogs in `characters.xml` lack `<fr>` and `<es>` translations. Some UI strings may also be missing.
- **Difficulty**: Easy (language skill dependent)

#### F10. Add unit tests for untested modules
- **Currently untested**:
  - `LevelScene` (the entire gameplay!)
  - `SceneManager`, `LevelLoadingScene`
  - All GUI modules (`sidebar.py`, `animation.py`, etc.)
  - `menu_creator_manager.py`
  - `load_from_tmx_manager.py`
  - `save_state_manager.py`
  - `options_manager.py`
  - `Effect`, `Fountain`, `Door`, `Portal`, `Consumable`, `Skill`
  - `Foe` loot/pathfinding logic
- **Difficulty**: Easy-Medium per test file

#### F11. Add type annotations where missing
- **Files**: `src/services/load_from_xml_manager.py` (many functions have `:return:` only in docstrings), various other modules
- **Difficulty**: Easy

---

## 10. Contributing Guidelines

1. **Fork** the repository on GitHub
2. **Create a feature branch**: `git checkout -b feature/my-improvement`
3. **Enable Git LFS** before adding binary assets:
   ```bash
   git lfs install
   git lfs track "*.png" "*.wav" "*.ttf"
   git add .gitattributes
   ```
4. **Run the test suite** before submitting:
   ```bash
   uv run pytest tests/ -v
   ```
5. **Follow existing code style**: PEP 8, docstrings on classes and public methods, type annotations
6. **Submit a Pull Request** with a clear description of what was changed and why
7. **Report bugs or ideas**: Open an issue on GitHub or email grimmys.programming@gmail.com
8. **Join the community**: [Discord server](https://discord.gg/CwFdXNs9Wt)

### Key Technologies

| Library | Purpose | Docs |
|---------|---------|------|
| `pygame-ce` | Game engine (rendering, input, audio) | [pygame-ce.org](https://pyga.me/) |
| `pygamepopup` | UI popup/menu system | [PyPI](https://pypi.org/project/pygame-popup/) |
| `lxml` | XML parsing for game data and saves | [lxml.de](https://lxml.de/) |
| `pytmx` | Tiled TMX map file parsing | [PyPI](https://pypi.org/project/pytmx/) |
| `pytest` | Test runner | [docs.pytest.org](https://docs.pytest.org/) |
| `uv` | Python package/project manager | [docs.astral.sh/uv](https://docs.astral.sh/uv/) |

---

*Generated on 28 February 2026. Based on commit history of the `master` branch of [Grimmys/rpg_tactical_fantasy_game](https://github.com/Grimmys/rpg_tactical_fantasy_game).*
