# pygame-menu — Local Setup Guide

## 1. Project Overview

### What is pygame-menu?

**pygame-menu** (v4.3.4) is a Python library built on top of [pygame](https://www.pygame.org/) that provides a complete, feature-rich menu and GUI system for pygame applications. It allows developers to create interactive menus with buttons, text inputs, selectors, dropdowns, color pickers, toggle switches, progress bars, sliders, tables, frames, images, and more — all rendered natively through pygame surfaces.

### Who Uses It and How

- **Indie game developers** use it to build main menus, settings screens, level selectors, and in-game HUDs for their pygame-based games.
- **Educators and students** use it to rapidly prototype interactive Python applications with a GUI layer without leaving the pygame ecosystem.
- **Hobbyists and jam participants** use it to add polished menu systems to game-jam projects with minimal boilerplate.

Typical usage involves creating a `Menu` object, adding widgets via `menu.add.<widget_type>(...)`, applying a visual `Theme`, and running `menu.mainloop(surface)` inside a pygame event loop.

### Why It Is Valuable

- **Zero external GUI dependency** — works entirely within pygame, no Qt/Tk/wxWidgets required.
- **Rich widget library** — 20+ widget types covering most GUI needs (text input, dropdowns, sliders, tables, frames with scrolling, etc.).
- **Theming system** — 6 built-in themes and a fully customisable `Theme` class for uniform visual styling.
- **Comprehensive input support** — keyboard, mouse, joystick, and touchscreen out of the box.
- **Decorator system** — attach arbitrary visual decorations (arcs, circles, lines, images, text) to any widget or menu.
- **Nested menus & scroll areas** — deep submenu trees and scrollable content with configurable scrollbars.
- **Well-tested** — 31 test modules (~12,500 LOC of tests) covering every widget and core component.

### Codebase at a Glance

| Metric | Value |
|---|---|
| Package source (LOC) | ~34,000 |
| Test code (LOC) | ~12,500 |
| Test modules | 31 |
| Widget types | 21 |
| Selection effects | 5 |
| Built-in themes | 6 |
| Built-in fonts | 14 |
| Example apps | 6+ |

---

## 2. Setup Instructions

### Prerequisites

| Requirement | Version |
|---|---|
| Python | 3.7 or higher |
| pip | latest recommended |
| pygame | ≥ 1.9.3 (pygame 2.x recommended) |

### macOS

```bash
# 1. Verify Python 3 is installed
python3 --version

# 2. (Optional) Create and activate a virtual environment
python3 -m venv venv
source venv/bin/activate

# 3. Clone the repository (if not already present)
git clone https://github.com/ppizarror/pygame-menu.git
cd pygame-menu

# 4. Install runtime dependencies
pip install -r requirements.txt

# 5. Install the package in editable/development mode
pip install -e .

# 6. (Optional) Install test + docs extras
pip install -e ".[test,docs]"

# 7. Verify the installation
python3 -c "import pygame_menu; print(pygame_menu.__version__)"
# Expected output: 4.3.4

# 8. Run an example to confirm everything works
python3 -m pygame_menu.examples.simple
```

> **Note (macOS):** If you encounter issues with pygame on Apple Silicon, ensure you are using pygame ≥ 2.1.3 which has native ARM support, or run under Rosetta.

### Windows

```powershell
# 1. Verify Python 3 is installed
python --version

# 2. (Optional) Create and activate a virtual environment
python -m venv venv
venv\Scripts\activate

# 3. Clone the repository (if not already present)
git clone https://github.com/ppizarror/pygame-menu.git
cd pygame-menu

# 4. Install runtime dependencies
pip install -r requirements.txt

# 5. Install the package in editable/development mode
pip install -e .

# 6. (Optional) Install test + docs extras
pip install -e ".[test,docs]"

# 7. Verify the installation
python -c "import pygame_menu; print(pygame_menu.__version__)"
# Expected output: 4.3.4

# 8. Run an example to confirm everything works
python -m pygame_menu.examples.simple
```

> **Note (Windows):** On some Windows machines, use `python` instead of `python3`. Ensure your PATH includes the Python Scripts directory for pip-installed executables.

### Dependencies Reference

| Package | Purpose |
|---|---|
| `pygame>=1.9.3` | Core rendering and event loop |
| `pyperclip` | Clipboard copy/paste in text input widgets |
| `typing_extensions` | Backported type hints for Python < 3.10 |
| `nose2` *(test)* | Test runner |
| `codecov` *(test)* | Coverage reporting |
| `sphinx` *(docs)* | Documentation generation |
| `sphinx-autodoc-typehints` *(docs)* | Auto-document type hints |
| `sphinx-rtd-theme` *(docs)* | ReadTheDocs theme for Sphinx |

---

## 3. Testing & Linting

### Running the Full Test Suite

The project uses **unittest** as the testing framework and **nose2** as the test runner.

```bash
# Run all tests with nose2 (recommended)
nose2

# Or run with unittest discovery
python -m unittest discover -s test -p "test_*.py" -v

# Run a specific test module
python -m unittest test.test_menu -v

# Run a specific test class
python -m unittest test.test_widget_button.ButtonWidgetTest -v

# Run a specific test method
python -m unittest test.test_widget_button.ButtonWidgetTest.test_button -v
```

### Running Tests with Coverage

```bash
# Install coverage if not already present
pip install coverage

# Run tests with coverage measurement
coverage run -m nose2
coverage report -m

# Generate an HTML coverage report
coverage html
open htmlcov/index.html   # macOS
start htmlcov/index.html  # Windows
```

### Test Structure

| Directory / File | What It Covers |
|---|---|
| `test/_utils.py` | Shared test infrastructure (`BaseTest`, `MenuUtils`, `PygameEventUtils`) |
| `test/test_menu.py` | Core Menu class (creation, layout, events, navigation) |
| `test/test_themes.py` | Theme validation and predefined themes |
| `test/test_widget_*.py` | Individual widget tests (one file per widget type) |
| `test/test_base.py` | Base class attribute and ID management |
| `test/test_scrollarea.py` | Scroll area and scrollbar behavior |
| `test/test_decorator.py` | Decorator/visual effect system |
| `test/test_examples.py` | End-to-end example application tests |

### Linting & Formatting

The project does not ship a pre-configured linter/formatter in its repository. The recommended approach for development:

```bash
# Install common Python linters/formatters
pip install flake8 mypy

# Run flake8 for style checks
flake8 pygame_menu/ --max-line-length=120 --ignore=E501,W503

# Run mypy for type checking (the package includes py.typed marker)
mypy pygame_menu/

# (Optional) Format with autopep8 or black
pip install autopep8
autopep8 --in-place --recursive pygame_menu/
```

### Building Documentation

```bash
cd docs

# macOS / Linux
make html

# Windows
make.bat html

# Open the built documentation
open _build/html/index.html   # macOS
start _build\html\index.html  # Windows
```

### Building Distribution Packages

```bash
# Build source and wheel distributions
python build.py pip

# Upload to PyPI (requires twine + credentials)
python build.py twine
```
