#### Contributor

**Summary**
This PR adds a comprehensive pytest unit test suite to establish testing infrastructure for the project.

**Changes**
pytest.ini - Test configuration with discovery patterns
tests/conftest.py - Shared fixtures for time strings, tyre compounds, and wind directions
tests/test_time.py - ~50 parametrized tests for format_time() and parse_time_string()
tests/test_tyres.py - ~40 parametrized tests for tyre compound conversions
tests/test_ui_helpers.py - ~40 parametrized tests for _format_wind_direction()
requirements.txt - Added pytest and pytest-cov dependencies

**Total: 100+ test cases**
How to Run

```bash
python -m pytest tests/ -v                     # Run all tests
python -m pytest tests/ --cov=src/lib          # With coverage
```


**NOTE:** this is a open PR and no reviewer commented yet, so while you are analyzing the solution, please try to also analyze for correctness and you may make changes such that it will be our gold standard