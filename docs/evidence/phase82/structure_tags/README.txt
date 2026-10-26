Structure tag excerpts from the 0.8.2 reference and weighted packs.

V3 fixed-structure rules:
- Each fixed set is either placed at one strict chunk (enabled=true) or
  exported with enabled=false at chunk 0,0. There is no category or
  high-continentalness fallback.
- Tags list only the enabled biome IDs of the accepted families plus the
  actual point biome; disabled sets emit no structure tag file.
- Broad tags remain for random-spread villages/trial chambers, strongholds,
  and the ocean/deep-dark gates where the tag itself is the placement gate.

Reference pack (/tmp/omniisle-082-ref, seed 2025, size 3500):
  desert_pyramids   enabled=false (no desert family on the map)
  jungle_temples    enabled=false (no jungle family)
  igloos            enabled=false (no candidate met c <= 0.30 / slope < 0.25)
  swamp_huts        enabled=true  chunk 65,-82, tag = swamp, mangrove_swamp
  woodland_mansions enabled=true  chunk 44,81,  tag = dark_forest
  pillager_outposts enabled=true  chunk 64,-11, broad enabled-land tag
  mineshafts        enabled=true  chunk 91,54,  broad enabled-land tag
  trail_ruins       enabled=true
  abandoned_camp    enabled=true
  ruined_portals    enabled=true
  shipwrecks        enabled=true
  ocean_monuments   enabled=true
  ocean_ruins       enabled=true
  buried_treasures  enabled=true
  ancient_cities    enabled=true
  strongholds       enabled=true (fixed multi)
  villages/trial_chambers: random spread (unchanged broad tags)

Weighted pack (/tmp/omniisle-082-weighted, cherry 45, all biomes enabled):
  swamp_huts, desert_pyramids, jungle_temples, igloos,
  woodland_mansions -> enabled=false after calibration found no strict-policy
  candidate, no tag emitted.

0.8.1 before excerpts (commit 6559169, same reference draft):
  before_desert_pyramid_tag.json        broad all-enabled-land tag (fallback)
  before_desert_pyramids_set.json       enabled at chunk 5,-60 (c=0.6148)
  before_swamp_hut_tag.json             broad all-enabled-land tag (fallback)
  before_swamp_huts_set.json            enabled at chunk -10,-41 (c=0.6188)
  before_081_pack.zip                   full 0.8.1 generated pack for comparison
See ../structure_placement_before_provenance.txt for how these were captured.

Files:
  reference_swamp_hut_tag.json          narrow tag (swamp, mangrove_swamp)
  reference_swamp_huts_set.json         enabled=true chunk 65,-82
  reference_woodland_mansion_tag.json   narrow tag (dark_forest)
  reference_desert_pyramids_set.json    disabled set, no fallback
  reference_jungle_temples_set.json     disabled set, no fallback
  reference_igloos_set.json             disabled set, no fallback
  reference_pillager_outpost_tag.json   broad enabled-land tag (by design)
  weighted_swamp_huts_set.json          disabled set after weighting
Full set summaries are in structure_sets_reference.txt and
structure_sets_weighted.txt.
