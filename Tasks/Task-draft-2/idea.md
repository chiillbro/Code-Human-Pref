#### Issue - Proper support for multiline labels: 407

Contributor:

Is your feature request related to a problem? Please describe.
Currently real multiline labels are not supported. If one activates word wrapping, several label widgets are created instead, one per line. Thus, it is not possible, for example, to change the text of the label programmatically with word-wrapping, unless you remove every label and re-add them.

Describe the solution you'd like
What I want is a widget to display multiline messages. In particular, I want to reimplement the following message display as a pygame_menu widget:


As you can see, this displays multiline messages of variable length, doing word-wrapping and animating the text. This widget can speed up the animation when the enter key is pressed, as well as changing to the next page of text when the enter key is pressed at the end.

If the number of lines exceed the available lines, it should mark it somehow (with an arrow maybe) and allow the user to move the lines upwards / replace existing text to put the new ones (this is currently not implemented).

Describe alternatives you've considered
I was trying to implement this as my own widget, when I noticed that the existing Label object partially implements multiline support, but only at creation time. I think that it would be better to implement multiline support in the Label widget directly. The other features could probably be implemented in a Label subclass (or as a composite Widget with a label as one of its members) if they are not deemed general enough.

This is my first try to implement multiline (in a custom widget):

```py
from typing import List, Optional

import pygame
from pygame.rect import Rect
from pygame_menu._types import EventVectorType
from pygame_menu.utils import make_surface
from pygame_menu.widgets.core.widget import Widget


class TextDisplay(Widget):
    """
    Label widget.

    .. note::

        Label accepts all transformations.

    :param title: Label title/text
    :param label_id: Label ID
    :param onselect: Function when selecting the label widget
    """

    def __init__(
        self,
        title: str,
        text_display_id: str = '',
        wordwrap: bool = True,
        n_lines: Optional[int] = None,
        leading: Optional[int] = None,
    ) -> None:
        super().__init__(
            title=title,
            onselect=None,
            widget_id=text_display_id,
        )
        self._wordwrap = wordwrap
        self._n_lines = n_lines
        self._leading = leading

    def _draw(self, surface: pygame.Surface) -> None:
        # The minimal width of any surface is 1px, so the background will be a
        # line
        if self._title == '':
            return
        assert self._surface
        surface.blit(self._surface, self._rect.topleft)

    def _apply_font(self) -> None:
        return

    def _wordwrap_line(
        self,
        line: str,
        font: pygame.font.Font,
        max_width: int,
        tab_size: int,
    ) -> List[str]:

        final_lines = []
        words = line.split(" ")

        while True:
            split_line = False

            for i, _ in enumerate(words):

                current_line = " ".join(words[:i + 1])
                current_line = current_line.replace("\t", " " * tab_size)
                current_line_size = font.size(current_line)
                if current_line_size[0] > max_width:
                    split_line = True
                    break

            if split_line:
                if i == 0:
                    i += 1
                final_lines.append(" ".join(words[:i]))
                words = words[i:]
            else:
                final_lines.append(current_line)
                break

        return final_lines

    def _get_leading(self) -> int:
        assert self._font

        return (
            self._font.get_linesize()
            if self._leading is None
            else self._leading
        )

    def _get_n_lines(self) -> int:
        assert self._font

        if self._n_lines is None:
            text_size = self._font.get_ascent() + self._font.get_descent()
            leading = self._get_leading()
            offset = leading - text_size

            available_height = self._rect.height

            return (available_height + offset) // (text_size + offset)

        else:
            return self._n_lines

    def _render(self) -> Optional[bool]:
        if not self._render_hash_changed(
            self._title,
            self._font_color,
            self._visible,
        ):
            return None

        # Render surface
        if self._font is None or self._menu is None:
            self._surface = make_surface(
                0,
                0,
                alpha=True,
            )
            return None

        lines = self._title.split("\n")
        if self._wordwrap:
            lines = sum(
                (
                    self._wordwrap_line(
                        line,
                        font=self._font,
                        max_width=self._menu.get_width(inner=True),
                        tab_size=self._tab_size,
                    )
                    for line in lines
                ),
                [],
            )

        self._surface = make_surface(
            max(self._font.size(line)[0] for line in lines),
            len(lines) * self._get_leading(),
            alpha=True,
        )

        for n_line, line in enumerate(lines):
            line_surface = self._render_string(line, self._font_color)
            self._surface.blit(
                line_surface,
                Rect(
                    0,
                    n_line * self._get_leading(),
                    self._rect.width,
                    self._rect.height,
                ),
            )

        self._apply_transforms()
        self._rect.width, self._rect.height = self._surface.get_size()

        self.force_menu_surface_update()
        return True

    def update(self, events: EventVectorType) -> bool:
        self.apply_update_callbacks(events)
        for event in events:
            if self._check_mouseover(event):
                break
        return False
```
However I did not found a nice way to find the right width (this is the one used by the multiline implementation of Label and it is WRONG).


---

Reviewer:
What are you refering to get the right width? max(self._font.size(line)[0] for line in lines), do not work?

Contributor:
I meant that using self._menu.get_width(inner=True) overestimates the available space.

Reviewer:
You can also create a new PR :). A new widget requires a manager (for menu.add) and lots of new tests.

Contributor:
I think that at least multiline support could be added to the existing label widget


Reviewer:
Got it! You can take a look at this example:

**pygame-menu/pygame_menu/widgets/widget/textinput.py**

```py
 posx2 = max(self._get_max_container_width() - self._input_underline_size * 1.75 - 
             self._padding[1] - self._padding[3], 
             current_rect.width) 
 delta_ch = posx2 - self._title_size - self._selection_effect.get_width() 
 char = math.ceil(delta_ch / self._input_underline_size) 
 for i in range(10):  # Find the best guess for 
     fw = self._font_render_string( 
         self._input_underline * int(char), color).get_width() 
     char += 1 
     if fw >= delta_ch: 
         break 
```

Textinput also checks the available space of the menu (within its column). Maybe you could use some of these functions.
I this case, _get_max_container_width() returns the max width of the column the widget resides.


Contributor:
Tried it, it still uses menu.get_width(inner=True) in my case, which is wrong.

Reviewer: Hi. I've implemented this feature. See #413 and let me know what you think 😄

Usage: label = menu.add.label('lorem ipsum dolor sit amet this was very important nice a test is required', wordwrap=True)

Today I uploaded v4.3.0 to PyPI which incorporates multiline labels. I'll close this issue by now. If anyone has more suggestions, please create a new issue 😄



---

#### PR - Added multilabel support, Proper support for multiline labels - 407.

Reviewer: Added multilabel support, Proper support for multiline labels #407.

Contributor:

I think _get_nlines is not used right now, is it? I planned to use that to allow the user to fix the maximum number of lines. In case that the text needs more lines, the remaining test should not be shown, but the information of the start position of the remaining text should be stored, so that a subclass can use that information to show the next page of text.


Reviewer:
Ok I'll fix that. Just copied the code you provided me, I had the same doubt

@vnmabus I've added two new methods to lines. get_lines and get_overflow_lines. The first returns a list of currently displayed lines on the widget, and the latter return the lines not shown because of overflow.

Let me know what you think.

Example (see tests):

```py
s = 'lorem ipsum dolor sit amet this was very important nice a test is required ' \
    'lorem ipsum dolor sit amet this was very important nice a test is required'
label = menu.add.label(s, wordwrap=True, max_nlines=3)  # Maximum number of lines

self.assertEqual(len(label.get_lines()), 3) # The widget needs 4 lines, but maximum is 3
self.assertEqual(label.get_height(), 131)
self.assertEqual(label.get_overflow_lines(), ['important nice a test is required']) # The overflowed text
self.assertEqual(' '.join(label.get_lines() + label.get_overflow_lines()), s) # The sum of lines and overflow should be the same as s
```


Contributor:

It seems that we are going in the right direction, but there are still issues. The size of the label with wordwrap is still a bit larger than the available size. For example, if you allow scrollbars, a horizontal scrollbar will be added.

I think that being able to choose the alignment of the text inside the label would be useful (and easy to implement). Currently the text is aligned to the left, but allowing it to be centered (not justified) or right-aligned could be easily done adjusting the x-position where each subsurface will be blitted.

I still have to try if the current implementation allows me to implement the subclass I wanted. I will try when I have time.


Reviewer:

@vnmabus can you provide a MWE to test The size of the label with wordwrap is still a bit larger than the available size. For example, if you allow scrollbars, a horizontal scrollbar will be added. I'll try to code the alignment in my free time, that might happen in the next two weeks.


Hi! I'll merge this and continue the work on text alignment. greetings!

