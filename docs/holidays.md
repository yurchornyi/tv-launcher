# Holidays

On a holiday Home dresses its top row of app tiles: something hangs from each tile's top edge all day, and when you
come back to Home (at most every half hour) a character walks along the tops of the tiles from the left, stops at
the focused tile and does its thing there, then walks on. Owner, 13.11: minimal, but animated, someone must walk.

| Day | On the tiles | Who walks, and what happens at the focused tile | What falls |
|---|---|---|---|
| Saint Nicholas, 6 December | gifts | Saint Nicholas with his staff and bag leaves a gift on each tile, a big glowing one on the focused tile | snow |
| Christmas, 24 to 26 December | baubles that swing as he passes | a caroler with a turning star: it spins faster and sings, notes float up | snow, caps on the tiles |
| New Year, 31 December and 1 January | a warm garland | a snowman with a sparkler sets a rocket on the tile, counts 3, 2, 1 and the salute goes up from between the tiles, its sparks settle on them as glitter, then the new year's number. At midnight on the 31st the count ends at 00:00 exactly | only if it snows outside |
| Valentine's Day, 14 February | strings of hearts, hearts rising | Cupid flies over, shoots the focused tile: it glows pink and hearts burst out | nothing |
| Easter, Sunday and Monday, Orthodox and Western dates | Easter eggs, jumping as the bunny lands | a bunny hops from tile to tile; at the focused one an egg cracks, a chick climbs out and follows it | blossom |
| Independence Day, 24 August | blue and yellow flags | a stork walks, wheat grows between the tiles, the stork flies off | nothing |
| Halloween, 31 October | jack-o'-lanterns flickering, a spider on its thread | a ghost floats over; at the focused tile it says boo, the pumpkin flares and bats fly out | leaves |

Easter comes on both calendars (owner, 13.11): the Orthodox date (the Julian computus, which Ukraine's churches keep)
and the Western one; in years they fall together it is one day.

It shows whenever the seasons scene could (Home on the screen, no menu, search or player, motion on), and on a
holiday it takes the seasons scene's place, rain or not. **Turn it off**: Launcher settings, Motion, «Свята».
**See them now**: «Показати свята» there closes the settings and plays all seven in turn, each with its character;
from a computer, `adb shell am start -n com.gathin.tvhome/.MainActivity --ez holiday_demo true`.

## Cost

It is drawn by the seasons view in the same frames (about 30 a second), nothing allocated per frame. What repeats on
every tile (hearts, flags, eggs, wheat, pumpkins) is drawn once into small bitmaps at the tiles' size; the one
character is a few circles, ovals and rectangles. Sizes follow the tiles, so it looks the same on every screen.

## Code

`Holiday` picks the day (tested in `HolidayTest`); `HolidayScene` draws it over the top row of tiles that
`MainActivity.seasonBoxes` reports, stopping at the focused one (`SeasonView.Tiles.focused`);
`MainActivity.startSeason` starts it with the seasons scene and decides when a character walks.
