that's a nice work I can say, just a couple of things to address, please don't set _tooltip_delay_ms on the widget in _configure_widget, because you are bypassing the public
  set_tooltip() api by doing widget._tooltip_delay_ms = self._theme.widget_tooltip_delay_ms which is a direct property mutating. instead always pass the theme delay through
  set_tooltip() when a tooltip is provided. next, please extract _draw_tooltip into a helper or dedicated class, it is becoming somewhat tighter with long logic, pull the word wrap
  logic into a static meethod or utility function something like _wrap_tooltip_text(font, text, max_width) -> List[str] and also pull the surface construction into a separate callable.
  finally, can you add a test for boundary flip positioning, currently, there is no test that verify the tooltip actually flips or clamps when the mouse is near the right edge or bottom
  edge. so, create a test to test this behavior