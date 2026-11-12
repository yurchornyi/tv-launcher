# Seasons and weather

While Home sits still (four seconds after the last key), something falls across it, chosen by the month and by the
weather the top bar shows (Open-Meteo, the same reading as the temperature):

| When | What falls | On the tiles |
|---|---|---|
| December to February | gentle snow | it settles on the tiles' tops and on the shelf's pill, a cap that grows |
| March to May | cherry blossom | a petal rests on an edge for a moment |
| June to August | fireflies, rising and twinkling | nothing: they fly free |
| September to November | autumn leaves, turning as they fall | a leaf rests on an edge for a moment |
| It snows outside | thick snow | caps grow faster |
| It rains outside | rain, slanted | drops burst on the top edges, beads run down the faces and the Continue pill, hang from the lower edge and drip |
| A thunderstorm | heavier rain, flashes of light | as rain |

The first key takes it away (a quick fade) and does what it always did. After three minutes with no key it stops by
itself until the next one, so a TV left on Home does not keep its GPU busy. It never shows over another app, a menu,
search, the full-screen player or while reordering, and not with animations off or reduced motion.

**Turn it off**: hold OK on Settings, Launcher settings, Motion, «Погода й пори року».

## Cost

It is one view over Home drawn about 30 times a second, only while it plays: a few dozen circles, lines and small
paths a frame, nothing allocated per frame. On the XGIMI, whose GPU redraws the whole screen every frame, that is the
price of any animation on Home; the scene stops at the first key so moving between tiles is never slower.

## Code

`Season` picks the scene (tested in `SeasonTest`); `SeasonView` draws it and reads where the tiles and the pill are
through `MainActivity.seasonBoxes`; `MainActivity.seasonCheck` starts and stops it.
