"""Tests for SessionInfoBannerComponent."""
import pytest
from dataclasses import dataclass


@dataclass
class MockWindow:
    """Mock arcade window for testing."""
    width: int = 1280
    height: int = 720


@pytest.fixture
def session_info_class():
    """Import SessionInfo with arcade mocked."""
    from src.ui_components import SessionInfo
    return SessionInfo


@pytest.fixture
def banner_component_class():
    """Import SessionInfoBannerComponent with arcade mocked."""
    from src.ui_components import SessionInfoBannerComponent
    return SessionInfoBannerComponent


@pytest.fixture
def base_component_class():
    """Import BaseComponent with arcade mocked."""
    from src.ui_components import BaseComponent
    return BaseComponent


class TestSessionInfo:
    def test_creates_with_required_fields(self, session_info_class):
        SessionInfo = session_info_class
        info = SessionInfo(
            event_name="Monaco Grand Prix",
            session_type="R",
            year=2024,
            round_number=8,
        )
        assert info.event_name == "Monaco Grand Prix"
        assert info.session_type == "R"
        assert info.year == 2024
        assert info.round_number == 8

    def test_creates_with_optional_fields(self, session_info_class):
        SessionInfo = session_info_class
        info = SessionInfo(
            event_name="Monaco Grand Prix",
            session_type="R",
            year=2024,
            round_number=8,
            country="Monaco",
            circuit_name="Circuit de Monaco",
        )
        assert info.country == "Monaco"
        assert info.circuit_name == "Circuit de Monaco"

    def test_session_type_display_race(self, session_info_class):
        SessionInfo = session_info_class
        info = SessionInfo("Test", "R", 2024, 1)
        assert info.session_type_display == "Race"

    def test_session_type_display_sprint(self, session_info_class):
        SessionInfo = session_info_class
        info = SessionInfo("Test", "S", 2024, 1)
        assert info.session_type_display == "Sprint"

    def test_session_type_display_qualifying(self, session_info_class):
        SessionInfo = session_info_class
        info = SessionInfo("Test", "Q", 2024, 1)
        assert info.session_type_display == "Qualifying"

    def test_session_type_display_sprint_qualifying(self, session_info_class):
        SessionInfo = session_info_class
        info = SessionInfo("Test", "SQ", 2024, 1)
        assert info.session_type_display == "Sprint Qualifying"

    def test_session_type_display_unknown_returns_raw(self, session_info_class):
        SessionInfo = session_info_class
        info = SessionInfo("Test", "XYZ", 2024, 1)
        assert info.session_type_display == "XYZ"


class TestSessionInfoBannerComponent:
    def test_creates_with_session_info(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo(
            event_name="Monaco Grand Prix",
            session_type="R",
            year=2024,
            round_number=8,
        )
        banner = SessionInfoBannerComponent(session_info=info)
        assert banner.session_info == info
        assert banner.visible is True

    def test_creates_hidden_when_no_session_info(self, banner_component_class):
        SessionInfoBannerComponent = banner_component_class
        
        banner = SessionInfoBannerComponent(session_info=None)
        assert banner.session_info is None
        assert banner.visible is False

    def test_toggle_visibility(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo("Test", "R", 2024, 1)
        banner = SessionInfoBannerComponent(session_info=info)
        
        assert banner.visible is True
        result = banner.toggle_visibility()
        assert result is False
        assert banner.visible is False
        
        result = banner.toggle_visibility()
        assert result is True
        assert banner.visible is True

    def test_set_session_info(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        banner = SessionInfoBannerComponent(session_info=None)
        assert banner.visible is False
        
        info = SessionInfo("Monaco GP", "R", 2024, 8)
        banner.set_session_info(info)
        
        assert banner.session_info == info
        assert banner.visible is True

    def test_set_session_info_to_none_hides_banner(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo("Monaco GP", "R", 2024, 8)
        banner = SessionInfoBannerComponent(session_info=info)
        assert banner.visible is True
        
        banner.set_session_info(None)
        assert banner.session_info is None
        assert banner.visible is False

    def test_format_title_line(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo(
            event_name="Monaco Grand Prix",
            session_type="R",
            year=2024,
            round_number=8,
        )
        banner = SessionInfoBannerComponent(session_info=info)
        
        title = banner._format_title_line()
        assert "Monaco Grand Prix" in title
        assert "Race" in title

    def test_format_subtitle_line(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo(
            event_name="Monaco Grand Prix",
            session_type="R",
            year=2024,
            round_number=8,
            country="Monaco",
        )
        banner = SessionInfoBannerComponent(session_info=info)
        
        subtitle = banner._format_subtitle_line()
        assert "2024" in subtitle
        assert "Round 8" in subtitle

    def test_format_subtitle_with_country(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo(
            event_name="Monaco Grand Prix",
            session_type="R",
            year=2024,
            round_number=8,
            country="Monaco",
        )
        banner = SessionInfoBannerComponent(session_info=info)
        
        subtitle = banner._format_subtitle_line()
        assert "Monaco" in subtitle

    def test_on_resize_updates_position(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo("Test", "R", 2024, 1)
        banner = SessionInfoBannerComponent(session_info=info)
        
        window = MockWindow(width=1920, height=1080)
        banner.on_resize(window)
        
        # Banner should be centered horizontally
        assert banner._center_x == window.width // 2

    def test_default_position_is_top_center(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo("Test", "R", 2024, 1)
        banner = SessionInfoBannerComponent(session_info=info, top_margin=20)
        
        assert banner.top_margin == 20
        assert banner._center_x is None  # Will be calculated on first draw/resize

    def test_custom_styling(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo("Test", "R", 2024, 1)
        banner = SessionInfoBannerComponent(
            session_info=info,
            title_font_size=24,
            subtitle_font_size=14,
            padding=15,
        )
        
        assert banner.title_font_size == 24
        assert banner.subtitle_font_size == 14
        assert banner.padding == 15


class TestSessionInfoBannerComponentIntegration:
    """Integration tests that verify component behavior in context."""

    def test_component_follows_base_component_interface(self, session_info_class, banner_component_class, base_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        BaseComponent = base_component_class
        
        info = SessionInfo("Test", "R", 2024, 1)
        banner = SessionInfoBannerComponent(session_info=info)
        
        assert isinstance(banner, BaseComponent)
        assert hasattr(banner, 'draw')
        assert hasattr(banner, 'on_resize')
        assert hasattr(banner, 'on_mouse_press')

    def test_draw_does_not_crash_with_none_session_info(self, banner_component_class):
        SessionInfoBannerComponent = banner_component_class
        
        banner = SessionInfoBannerComponent(session_info=None)
        window = MockWindow()
        
        # Should not raise
        banner.draw(window)

    def test_draw_does_not_crash_when_hidden(self, session_info_class, banner_component_class):
        SessionInfo = session_info_class
        SessionInfoBannerComponent = banner_component_class
        
        info = SessionInfo("Test", "R", 2024, 1)
        banner = SessionInfoBannerComponent(session_info=info)
        banner.visible = False
        
        window = MockWindow()
        # Should not raise
        banner.draw(window)


class TestSessionInfoFromFastF1:
    """Tests for SessionInfo.from_fastf1_session factory method."""
    
    def test_creates_from_mock_session(self, session_info_class):
        from unittest.mock import MagicMock
        from datetime import datetime
        
        SessionInfo = session_info_class
        
        mock_event_date = MagicMock()
        mock_event_date.year = 2024
        
        mock_session = MagicMock()
        mock_session.event = {
            'EventName': 'Monaco Grand Prix',
            'RoundNumber': 8,
            'EventDate': mock_event_date,
            'Country': 'Monaco',
            'Location': 'Circuit de Monaco',
        }
        
        info = SessionInfo.from_fastf1_session(mock_session, 'R')
        
        assert info.event_name == 'Monaco Grand Prix'
        assert info.session_type == 'R'
        assert info.year == 2024
        assert info.round_number == 8
        assert info.country == 'Monaco'
        assert info.circuit_name == 'Circuit de Monaco'

    def test_handles_missing_event_fields(self, session_info_class):
        from unittest.mock import MagicMock
        
        SessionInfo = session_info_class
        
        mock_session = MagicMock()
        mock_session.event = {}
        
        info = SessionInfo.from_fastf1_session(mock_session, 'S')
        
        assert info.event_name == 'Unknown Event'
        assert info.session_type == 'S'
        assert info.round_number == 0
        assert info.country is None

    def test_handles_none_event_date(self, session_info_class):
        from unittest.mock import MagicMock
        
        SessionInfo = session_info_class
        
        mock_session = MagicMock()
        mock_session.event = {
            'EventName': 'Test GP',
            'RoundNumber': 1,
            'EventDate': None,
        }
        
        info = SessionInfo.from_fastf1_session(mock_session, 'Q')
        
        assert info.year == 2024  # Default fallback
