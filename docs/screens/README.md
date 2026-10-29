# Editor screenshots

## 1.0.0 — captured with the dev harness

The 1.0.0 editor is captured by the in-mod development harness
(`client/dev/UiScreenshotHarness`, enabled by `client/OmniIsleClient` only when
`-Domniisle.uiScreenshot=<dir>` is set). It opens the editor with a default
`IslandDraft`, switches language, window size, GUI scale and tab, and grabs the
main render target.

```sh
export JAVA_HOME="/Users/reystin/Library/Application Support/PrismLauncher/java/java-runtime-epsilon/jre.bundle/Contents/Home"
./gradlew runClient --console=plain -Domniisle.uiScreenshot="$PWD/docs/screens/1.0.0/after"
```

- `after/<lang>/<window>_scale<g>/<tab>.png` — 2 languages × 4 window sizes ×
  4 requested GUI scales × 6 tabs.
- `after/<lang>/1280x720_scale1/preview_{land,height,biomes,shore}.png` — the four
  canvas preview modes.
- `after/manifest.csv` — `path,lang,tab,scale,requested,actual_logical,fps`.
  `actual_logical` is authoritative: Minecraft clamps the requested GUI scale so
  the logical size never drops below 320×240.
- `after/render_cost.csv` — `tab,scale,actual_logical,samples,median_ms,p95_ms`
  at 1920×1080, GUI scale 2, 300 frames after warm-up.
- `after/harness_done.txt` or `after/harness_error.txt`.

Smoke runs use `-Domniisle.uiScreenshot.smoke=1` (four screenshots) and
`-Domniisle.uiScreenshot.lang=ru_ru` to restrict the language.

### Measured environment constraints

- macOS enforces a 320×240-point minimum window, so the smallest capture is a
  640×480 framebuffer. A 640×320 framebuffer cannot be created; the 640×320
  matrix row in the design plan was replaced by 640×480.
- Minecraft clamps GUI scale 2–4 requests to keep at least 320×240 logical
  pixels. As a result **MINIMAL** (below 320×150) is unreachable in a real
  client and is covered by `Phase11UiLayoutTest` only; **MEDIUM** also only
  occurs at sizes between the four captured windows and is covered by the same
  test. **MINI** is captured at 640×480 scale 2 (320×240).
- Retina framebuffer scale is compensated with `SDL_GetWindowDisplayScale`, so
  requested sizes are framebuffer pixels.

## Before

`before/editor_0.8.4_old_ui.png` is the 0.8.4 editor as supplied by the user
before the 1.0.0 redesign (vanilla widgets, clipped resolution label). The
harness was written during the redesign and was not available to capture the old
UI itself.

## Earlier phases

The 0.7.0–0.8.4 phases had no screenshot harness; `UI_CHECKLIST.md` records
which interactions were covered by unit tests instead.
