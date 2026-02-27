"""Pytest configuration and fixtures for ui_components tests."""
import sys
from unittest.mock import MagicMock

# Create a comprehensive arcade mock before any imports
mock_arcade = MagicMock()
mock_arcade.color = MagicMock()
mock_arcade.color.WHITE = (255, 255, 255)
mock_arcade.color.BLACK = (0, 0, 0)
mock_arcade.color.GRAY = (128, 128, 128)
mock_arcade.color.LIGHT_GRAY = (192, 192, 192)
mock_arcade.color.RED = (255, 0, 0)
mock_arcade.color.GREEN = (0, 255, 0)
mock_arcade.color.BLUE = (0, 0, 255)
mock_arcade.color.YELLOW = (255, 255, 0)
mock_arcade.color.ORANGE = (255, 165, 0)
mock_arcade.color.CYAN = (0, 255, 255)
mock_arcade.color.BROWN = (139, 69, 19)
mock_arcade.color.DARK_GRAY = (64, 64, 64)
mock_arcade.color.DIM_GRAY = (105, 105, 105)

mock_arcade.load_texture = MagicMock(return_value=MagicMock())
mock_arcade.Text = MagicMock(return_value=MagicMock(content_width=100))
mock_arcade.draw_rect_filled = MagicMock()
mock_arcade.draw_rect_outline = MagicMock()
mock_arcade.draw_line = MagicMock()
mock_arcade.draw_line_strip = MagicMock()
mock_arcade.draw_circle_filled = MagicMock()
mock_arcade.draw_circle_outline = MagicMock()
mock_arcade.draw_texture_rect = MagicMock()
mock_arcade.XYWH = MagicMock(return_value=MagicMock())
mock_arcade.key = MagicMock()
mock_arcade.key.MOD_SHIFT = 1

sys.modules['arcade'] = mock_arcade
sys.modules['arcade.color'] = mock_arcade.color
sys.modules['arcade.key'] = mock_arcade.key
