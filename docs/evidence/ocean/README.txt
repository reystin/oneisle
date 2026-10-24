Phase 9.3 — v3 ocean floor and deep-ocean band evidence
========================================================

Generated with generation_version = 3, island size 4000, islet density 0.25
(locate) or 0.0/0.35 (radial/islet tests), presets NATURAL, ROUND, LONG,
CRESCENT, ARCHIPELAGO, seeds 101/202/303.

Files
-----
ocean_band_summary.csv       per config: samples in [0.4*Wo, 1.0*Wo], violations,
                             max/min continentalness (15 configs, 120,856 samples)
ocean_band_mismatches.csv    header only: zero samples at or above the deep threshold
ocean_radial_profiles_v3.csv 23,184-row radial profile (ray, offshore distance, c, y)
ocean_profile_summary_v3.csv global min/max, band max, seamount max, thresholds
ocean_height_6000_v3.png     6000x6000 height render (ROUND 4000 seed 101, islets 0.25)
ocean_islets_v3.csv          islet land samples, peak c, clearance, max 1-block delta
ocean_monument_band.csv      headless monument placement: chunk, centre c, 29-block
                             survey max c, generated has_structure tag (15 configs)
ocean_monument_locate.csv    real 26.3 server: /locate result + biome tag queries
                             at the located monument (10 configs)
pack_metadata.txt            min_format/max_format 121 and clean datapack load log

Headless regeneration
---------------------
JAVA_HOME="$HOME/Library/Application Support/PrismLauncher/java/java-runtime-epsilon" \
  ./gradlew test --tests 'com.hack337.omniisle.test.Phase9OceanTest' \
  -Domniisle.evidence=true

The tests write the CSV/PNG files above only when -Domniisle.evidence=true.

Real-server locate method
-------------------------
For each preset x seed: export the datapack with WorldPackBuilder, start the 26.3
dedicated server (Fabric, runServer) with a fresh world in run/locate, let the pack
auto-load, then over RCON:

  execute positioned <origin> -60 <origin> run locate structure minecraft:monument

The origin is placed 1000 blocks from the configured monument chunk toward (0,0),
inside the 100-chunk locate radius. The located monument area is forceloaded, then:

  execute if biome <x> <y> <z> #minecraft:is_deep_ocean   (y = 62, 50, 40, 30)
  execute if biome <x> 50 <z> #minecraft:is_ocean

All 10 configs returned the pack chunk (511-1000 blocks away) and passed every
deep-ocean probe. The vanilla structure id is minecraft:monument; ocean_monument
is the biome-tag name, not a structure id.
