# Phase 14 evidence — OmniIsle 1.0.1

Everything in this directory is captured output from test runs on 2026-10-28. No numbers are
hand-written estimates.

## Captured runs

| File | What it is |
| :--- | :--- |
| `test_run.log` | `./gradlew test --tests "*Phase14*" --console=plain` (Gradle console output) |
| `phase14_stdout.txt` | JUnit XML `<testsuite>` headers and every `PHASE14` stdout line from that run |
| `full_test_run.log` | `./gradlew test --console=plain` for the whole suite |
| `junit_summary.txt` | Aggregation of `build/test-results/test/TEST-*.xml` from that run |
| `build.log` | `./gradlew build --console=plain` after the full test run |

## Phase 14 suite (10 tests)

```text
Phase14ClimateTransitionTest  tests=3 skipped=0 failures=0 errors=0
Phase14ShoreInteriorTest      tests=3 skipped=0 failures=0 errors=0
Phase14VillagePackTest        tests=4 skipped=0 failures=0 errors=0
```

## Captured `PHASE14` stdout

```text
PHASE14 climate MINESHIELD_4_MAP families={0=1620, 1=353, 2=3164, 3=3433, 4=671, 5=353, 6=148, 7=496, 8=59, 9=5, 10=47, 11=1335, 12=147, 13=576, 16=133, 17=207} warmCore=2105 coldCore=1973 mountainCore=0 adjacent=0 switches=39 unbanded=0 banded=39 bands={2=1590, 4=287, 5=160}
PHASE14 climate NATURAL families={0=1266, 1=619, 2=5030, 3=1746, 4=683, 5=1659, 6=367, 7=482, 8=2, 10=79, 11=1855, 12=26, 13=483, 16=21, 17=223} warmCore=2443 coldCore=1885 mountainCore=0 adjacent=0 switches=100 unbanded=0 banded=100 bands={2=4316, 4=558, 5=1629}
PHASE14 fjord none=198 minBay=1.0
PHASE14 shore NATURAL d in [0,40] coastal=2597 shore=136 interior=21 beyondBand=0 shallow=0 highTier=0 inlandC=0
PHASE14 shore FJORDS d in [0,40] coastal=5198 shore=150 interior=22 beyondBand=0 shallow=0 highTier=0 inlandC=0
PHASE14 shore ARCHIPELAGO d in [0,40] coastal=7272 shore=224 interior=38 beyondBand=0 shallow=0 highTier=0 inlandC=0
PHASE14 openCoast sand=76/201
PHASE14 naturalCoast sand=302
```

Interpretation:

* `adjacent=0` — no 8-neighbour pair mixes a warm-core family {10,11,12,13} with a cold-core
  family {0,1} without the `tier >= 1.5 && c > 0.50` mountain exemption.
* `unbanded=0` — every warm↔cold switch along sampled rows/columns is separated by an
  intermediate family {2,4,5,11} or a mountain gap on both presets.
* `beyondBand`, `shallow`, `highTier`, `inlandC` are zero: no shore column outside
  `d ∈ [0,40]`, no beach without open water (`ring.minDistance <= -55`), none above
  `heightTier 1.10` or above `continentalness 0.20`.
* The synthetic 20-block channel returns `none=198` of 198 points, `minBay=1.0`.

## Shore distributions (rewritten by the same test run)

* `../../shore/shore_acceptance_summary.txt` (Phase 9, 30 islands / 61,440 points):
  sand `0.115576171875`, stony `0.032486979…`, none `0.8519368489583333`, `run_failures,0`.
* `../../phase11/after/shore_runs.csv` (five presets, 2-block raster, 8-connected components):
  17–48 components per preset, median arc 36–70 blocks, `tiny_le3` 4+1+0+5+0 = 10 of 144
  components. The zero-speck target remains **PARTIAL**.

## Whole suite and build

```text
JUnit aggregation over build/test-results/test/TEST-*.xml:
suites=47 tests=193 failures=0 errors=0 skipped=2
BUILD SUCCESSFUL (./gradlew test)
BUILD SUCCESSFUL (./gradlew build)
```

The two skips are the assumption-gated helpers `GoldenV1Test.regenerateGoldenIfRequested` and
`Phase9HarnessPacksTest.exportHarnessPacks`, the same two skipped in the 1.0.0 baseline; they are
not Phase 14 failures.

## Not verified

* In-game checks were **not performed**: interior beach ribbons in a live world, village type
  inspection, warm→cold walking transects, and snow caps next to deserts.
* No performance measurement was made for the ring gates. The change adds no SDF calls (the
  eight ring samples already existed) but this is unmeasured.
* Existing v3 worlds are not repaired; a new world is required to see the changes.
