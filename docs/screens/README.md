# Editor screenshots

Screenshots at GUI scales 1–4 were **not captured**. The agent's environment has no
automated Minecraft client-screenshot harness, and launching the graphical client
was not part of the automated evidence run.

What was verified instead:

- `EditorLayout` is a pure layout calculator; `Phase9EditorTest.layoutFitsGuiScales`
  asserts that the preview, all eight control rows, the six tabs, the biome list
  (non-compact) and the Done button fit at 1920×1080, 960×540, 640×360 and
  480×270 logical pixels (GUI scales 1–4 on a 1080p display).
- `UI_CHECKLIST.md` records which interactions are covered by unit tests and which
  remain unverified visually.

To produce the screenshots later: launch the dev client, open Create World →
OmniIsle editor, set GUI scale 1/2/3/4, capture the Shape, Climate and Tools tabs,
and store them here as `gui_scale_1_shape.png` … `gui_scale_4_tools.png`.
