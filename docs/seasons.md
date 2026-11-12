# Seasons and weather

While Home is on the screen, something falls across it, chosen by the month and by the weather the top bar shows
(Open-Meteo, the same reading as the temperature):

| When | What falls | On the tiles |
|---|---|---|
| December to February | gentle snow | it settles on the tops of the tiles, the shelf's pill, the Now Playing card and Kyivstar's channel logos, a cap that grows |
| March to May | cherry blossom | a petal rests on an edge for a moment |
| June to August | fireflies, rising and twinkling | nothing: they fly free |
| September to November | autumn leaves, turning as they fall | a leaf rests on an edge for a moment |
| It snows outside | thick snow | caps grow faster |
| It rains outside | rain, slanted | drops burst on the top edges, beads run down the tiles, the Continue pill, the card and the channel logos, hang from the lower edge and drip |
| A thunderstorm | heavier rain, flashes of light | as rain |

It keeps falling while you move around Home (owner, 12.11: the rain must not go at a key). It never shows over
another app, a menu, search, the full-screen player or while reordering, and not with animations off or reduced
motion; the screensaver or a dark screen ends it.

**Turn it off**: hold OK on Settings, Launcher settings, Motion, «Погода й пори року».

## Cost

It is one view over Home drawn about 30 times a second while Home is on the screen, nothing allocated per frame. Rain,
snow and splashes go out in a few batched calls (one per depth layer), not one per drop: per-drop calls made issuing
the frame the slow part on the XGIMI (moving along the dock: 40 ms median frame, 147 of 257 frames slow to issue). On the XGIMI, whose GPU redraws the whole screen every frame, that
keeps Home drawing all the time it is up; the switch in Launcher settings turns it off where that is too much.

## Code

`Season` picks the scene (tested in `SeasonTest`); `SeasonView` draws it and reads where the tiles, the pill, the card
and the channel logos are through `MainActivity.seasonBoxes`; `MainActivity.seasonCheck` starts and stops it.
