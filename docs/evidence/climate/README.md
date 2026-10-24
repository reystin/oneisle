# Phase 9.5 — theme climate gradient and biome-family borders

Every number below comes from `IslandClimate`, the shared code used by
`OmniBiomeSource` (server) and `BiomePreview` (editor). Samples use seed 202, climate
scale 720, island half-span 2000, continentalness 0.10 (inland), y 64 and
generation_version 3. The family-fraction grid is 128 × 128 samples over a 4000-block
island at gradient strengths 0.0 (off), 0.5 (the default, on) and 1.0 (max). The PNG is
a 6 × 3 montage of radius-1500 disks for the six themes in those three states.
Regenerate with:

```
./gradlew test --tests "*Phase9ClimateTest" -Domniisle.evidence=true
```

## Gradient formula

Each theme owns a centre temperature, a temperature span and a noise scale. For
gradient coordinate `u ∈ [-1, 1]` (N/S uses z, E/W uses x, RADIAL uses distance from the
centre) and strength `s`:

```
temperature = centre(theme) + u · 0.5 · span(theme) · clamp01(s) + fbm_noise · noise(theme)
```

The peak latitudinal offset is therefore `0.5 · span · s`: at the default strength 0.5
the gradient covers half of the theme's span, at strength 1 it covers the whole span.
`IslandClimate.gradientAmplitude(theme, s)` exposes the peak offset and
`temperatureSpan(theme)` the span. The old plan's ±0.75 amplitude was not kept: for ALL
it would push `centre ± 0.75` outside `[0, 1]` for most of the map, clamping a large
fraction of the island to the two extremes; the measured formula caps the peak at
±0.45.

| theme | centre | span | noise | amplitude at 0.5 |
|---|---|---|---|---|
| ALL | 0.50 | 0.90 | 0.17 | 0.225 |
| STANDARD | 0.50 | 0.90 | 0.17 | 0.225 |
| MYSTIC | 0.52 | 0.44 | 0.12 | 0.110 |
| NORDIC | 0.22 | 0.32 | 0.08 | 0.080 |
| DESERT | 0.82 | 0.28 | 0.08 | 0.070 |
| TROPICAL | 0.78 | 0.28 | 0.08 | 0.070 |

ALL and STANDARD both expose the full snowy→desert palette, so they deliberately share
the full span; Nordic, Desert and Tropical are narrower. The 9.1 audit sentence "the
theme temperature span is not used" was too broad: it observed the ALL/STANDARD alias
and missed the narrow themes. The 9.5 refactor only re-expresses the existing constants
as spans (`0.5 · span · s` equals the old `maxSpread · s` for every theme), so
v1/v2/v3 keep their exact temperatures.

`climate_gradient_summary_v3.csv` measures the gradient on/off differential at `u = ±1`
(128 x positions per theme/strength), which cancels the identical noise term, and
compares it with `2 · gradientAmplitude`. The test asserts equality at 1e-9; the CSV
rounds to three decimals.

## Family-border jitter

`selectLandFamily` adds `±0.04` temperature and `±0.032` humidity value noise on a
96-block cell for `generation_version ≥ 2` (v1 has no jitter; the 9.5 refactor preserves
the existing doubles for every version, so old worlds are unchanged). The temperature
amplitude is 8 % of the 0.5 neutral temperature centre of the default palette.

A straight border is traced as a chain of family changes across adjacent 10-block
scanlines; the chain is a border only where the two neighbouring samples are land. A
chord is straight when every intermediate chain point stays within 4 blocks of the
chord line. Chords are capped at 46 points (45 intervals = 450 blocks), so any truly
straight run longer than 400 blocks contains a chord this metric would find. The caveat
is the 10-block sampling resolution: a reported length is exact to one sample step.

## Results

Smallest and largest gradient offsets: `climate_gradient_summary_v3.csv` reports
`measured_north_south_delta = 2 · amplitude` with `delta_error = 0.000` for all 12
theme/strength rows (strength 0 and 0.5). At strength 0.5 the south−north difference is
0.450 (ALL, STANDARD), 0.220 (MYSTIC), 0.160 (NORDIC) and 0.140 (DESERT, TROPICAL)
temperature units, exactly twice the formula amplitude.

Family fractions show the gradient doing different work per theme (percent of the
16 384 sampled land points):

| theme | span | snowy_plains + taiga, off → on → max | desert + badlands, off → on → max | dominant families at max |
|---|---|---|---|---|
| ALL | 0.90 | 0.0 → 2.5 → 21.5 | 0.0 → 0.0 → 15.3 | badlands 13.2, snowy_plains 11.8, meadow 10.5, taiga 9.7 |
| STANDARD | 0.90 | 0.0 → 2.5 → 21.5 | 0.0 → 0.0 → 15.3 | identical to ALL |
| NORDIC | 0.32 | 91.4 → 71.9 → 61.0 | 0.0 → 0.0 → 0.0 | snowy_plains 38.7, birch_forest 23.2, taiga 22.3, meadow 15.1 |
| DESERT | 0.28 | 0.0 → 0.0 → 0.0 | 57.0 → 48.3 → 41.5 | badlands 35.0, savanna 29.0, mangrove_swamp 17.7, jungle 9.6 |
| TROPICAL | 0.28 | 0.0 → 0.0 → 0.0 | 16.8 → 27.9 → 32.0 | savanna 30.5, badlands 27.6, mangrove_swamp 17.8, jungle 9.2 |
| MYSTIC | 0.44 | 0.0 → 0.2 → 1.9 | 0.0 → 0.0 → 0.0 | plains 21.1, birch_forest 15.7, swamp 14.4, forest 12.5 |

- At the default strength 0.5 NORDIC stays entirely in its snowy/taiga/birch palette,
  DESERT in its hot palette, and ALL gains its first snow (0.6 %) and jungle/savanna.
- At strength 1.0 ALL/STANDARD span the full snowy→badlands palette; NORDIC and the hot
  themes stay predominantly in their own families. A small temperate fraction appears at
  the coldest edge of DESERT (1.5 %) and TROPICAL (9.7 %): the span endpoint (0.68 /
  0.64) plus the ±0.04 jitter crosses the `temp < 0.68` branch. Strength 1.0 is a stress
  test; the editor default is 0.5.
- The 0.74 % fixed `mushroom_fields` district (the magenta disk in every tile) is
  independent of climate and appears in all rows.
- ALL and STANDARD fractions are identical in every column, as expected from the shared
  full-temperature palette.

Family-border straightness (`climate_straight_borders_v3.csv`, 4 measurements per
theme: OFF/NORTH_SOUTH × horizontal/vertical scanlines):

| theme | longest horizontal | longest vertical |
|---|---|---|
| ALL | 200 | 141 |
| STANDARD | 200 | 141 |
| NORDIC | 200 | 180 |
| DESERT | 200 | 160 |
| TROPICAL | 200 | 230 |
| MYSTIC | 210 | 141 |

The longest straight family border across all 24 measurements is **230 blocks**
(TROPICAL, gradient off, vertical at z = 1100 from x = −1000 to −770), below the
400-block requirement. The v2 sampler measures **130 blocks** in both orientations
(`Phase6ClimateTest` step 6.3, seed 9999, c = 0.25), so the existing ±0.04 jitter is
sufficient and its amplitude does not need to grow.

`theme_families_6000.png` is the visual counterpart: columns are ALL, STANDARD, NORDIC,
DESERT, TROPICAL, MYSTIC; rows are gradient off, on (0.5) and max (1.0). Each 200 × 200
tile samples a 3000-block disk.
