package com.hack337.omniisle.continent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldOptions;

public class IslandShapeConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(IslandShapeConfig.class);

    public static final int CURRENT_GENERATION_VERSION = 2;
    public static final int LEGACY_GENERATION_VERSION = 1;
    public static final int MAX_SUPPORTED_GENERATION_VERSION = 2;

    public static volatile boolean unknownGenerationVersionDetected = false;
    public static volatile int lastUnknownGenerationVersion = 0;

    public record HeightLayer(int bitsPerPixel, String data) {
        public static final Codec<HeightLayer> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.INT.optionalFieldOf("bits_per_pixel", 2).forGetter(HeightLayer::bitsPerPixel),
                Codec.STRING.fieldOf("data").forGetter(HeightLayer::data)
            ).apply(instance, HeightLayer::new)
        );

        public byte[] unpack(int totalPixels) {
            byte[] result = new byte[totalPixels];
            java.util.Arrays.fill(result, (byte) 1);
            if (data == null || data.isEmpty()) return result;
            try {
                byte[] packed = java.util.Base64.getDecoder().decode(data);
                for (int i = 0; i < totalPixels && (i >>> 2) < packed.length; i++) {
                    int shift = (i & 3) * 2;
                    result[i] = (byte) (((packed[i >>> 2] & 0xFF) >>> shift) & 3);
                }
            } catch (Exception ignored) {}
            return result;
        }
    }

    public record SpawnPos(int x, int z) {
        public static final Codec<SpawnPos> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(SpawnPos::x),
                Codec.INT.fieldOf("z").forGetter(SpawnPos::z)
            ).apply(instance, SpawnPos::new)
        );
    }

    public static final Codec<IslandShapeConfig> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.INT.optionalFieldOf("generation_version", 1).forGetter(d -> d.generationVersion),
            Codec.DOUBLE.optionalFieldOf("islet_density", 0.25).forGetter(d -> d.isletDensity),
            HeightLayer.CODEC.optionalFieldOf("height_layer").forGetter(d -> java.util.Optional.ofNullable(d.heightLayer)),
            SpawnPos.CODEC.optionalFieldOf("spawn").forGetter(d -> java.util.Optional.ofNullable(d.spawn)),
            IslandShapeConfig.Entry.CODEC.listOf().fieldOf("entries").orElseGet(ArrayList::new).forGetter(d -> d.entries),
            IslandMask.CODEC.optionalFieldOf("mask").forGetter(d -> java.util.Optional.ofNullable(d.mask)),
            Codec.LONG.optionalFieldOf("biome_seed", 0L).forGetter(d -> d.biomeSeed)
        ).apply(instance, IslandShapeConfig::new)
    );

    public int generationVersion;
    public double isletDensity = 0.25;
    public HeightLayer heightLayer;
    public SpawnPos spawn;
    public byte[] unpackedHeight;
    public ArrayList<IslandShapeConfig.Entry> entries;
    public IslandMask mask;
    public long biomeSeed;

    public IslandShapeConfig(int generationVersion, double isletDensity, java.util.Optional<HeightLayer> heightLayer,
                             java.util.Optional<SpawnPos> spawn,
                             List<IslandShapeConfig.Entry> entries, java.util.Optional<IslandMask> mask, long biomeSeed) {
        if (generationVersion > MAX_SUPPORTED_GENERATION_VERSION) {
            LOGGER.error("OmniIsle: Unknown generation_version {}, capping to {}", generationVersion, MAX_SUPPORTED_GENERATION_VERSION);
            this.generationVersion = MAX_SUPPORTED_GENERATION_VERSION;
            unknownGenerationVersionDetected = true;
            lastUnknownGenerationVersion = generationVersion;
        } else {
            this.generationVersion = Math.max(1, generationVersion);
        }
        this.isletDensity = Math.max(0.0, Math.min(1.0, isletDensity));
        this.heightLayer = heightLayer.orElse(null);
        this.spawn = spawn.orElse(null);
        this.entries = new ArrayList<>(entries);
        this.mask = mask.orElse(null);
        this.biomeSeed = biomeSeed;

        int totalPx = this.mask != null ? this.mask.width * this.mask.height : 192 * 192;
        if (this.heightLayer != null && this.heightLayer.data() != null && !this.heightLayer.data().isEmpty()) {
            this.unpackedHeight = this.heightLayer.unpack(totalPx);
        } else {
            this.unpackedHeight = null;
        }
    }

    public IslandShapeConfig(int generationVersion, double isletDensity, java.util.Optional<HeightLayer> heightLayer,
                             List<IslandShapeConfig.Entry> entries, java.util.Optional<IslandMask> mask, long biomeSeed) {
        this(generationVersion, isletDensity, heightLayer, java.util.Optional.empty(), entries, mask, biomeSeed);
    }

    public IslandShapeConfig(int generationVersion, double isletDensity, List<IslandShapeConfig.Entry> entries,
                             java.util.Optional<IslandMask> mask, long biomeSeed) {
        this(generationVersion, isletDensity, java.util.Optional.empty(), entries, mask, biomeSeed);
    }

    public IslandShapeConfig(int generationVersion, List<IslandShapeConfig.Entry> entries, java.util.Optional<IslandMask> mask, long biomeSeed) {
        this(generationVersion, 0.25, java.util.Optional.empty(), entries, mask, biomeSeed);
    }

    public IslandShapeConfig(List<IslandShapeConfig.Entry> entries, java.util.Optional<IslandMask> mask, long biomeSeed) {
        this(1, entries, mask, biomeSeed);
    }

    public java.util.Optional<SpawnPos> spawn() { return java.util.Optional.ofNullable(spawn); }
    public java.util.Optional<IslandMask> mask() { return java.util.Optional.ofNullable(mask); }

    public List<ShapeLayerSettings> createSettings() {
        return this.entries.stream().map(ShapeLayerSettings::new).toList();
    }

    public static class Entry {
        public static final Codec<IslandShapeConfig.Entry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.DOUBLE.fieldOf("center_x").forGetter(e -> e.center_x),
                Codec.DOUBLE.fieldOf("center_z").forGetter(e -> e.center_z),
                Codec.DOUBLE.fieldOf("x_divisor").forGetter(e -> e.x_divisor),
                Codec.DOUBLE.fieldOf("z_divisor").forGetter(e -> e.z_divisor),
                Codec.FLOAT.fieldOf("rotation_degrees").forGetter(e -> e.rotation_degrees),
                Codec.FLOAT.fieldOf("multiplier").orElse(1.0F).forGetter(e -> e.multiplier),
                Codec.BOOL.fieldOf("overlap").forGetter(e -> e.overlap),
                IslandShapeConfig.Entry.Noise.CODEC.fieldOf("noise").forGetter(e -> e.noise)
            ).apply(instance, IslandShapeConfig.Entry::new)
        );

        public double center_x;
        public double center_z;
        public double x_divisor;
        public double z_divisor;
        public float rotation_degrees;
        public float multiplier;
        public boolean overlap;
        public IslandShapeConfig.Entry.Noise noise;

        public Entry(
            double center_x,
            double center_z,
            double x_divisor,
            double z_divisor,
            float rotation_degrees,
            float multiplier,
            boolean overlap,
            IslandShapeConfig.Entry.Noise noise
        ) {
            this.center_x = center_x;
            this.center_z = center_z;
            this.x_divisor = x_divisor;
            this.z_divisor = z_divisor;
            this.rotation_degrees = rotation_degrees;
            this.multiplier = multiplier;
            this.overlap = overlap;
            this.noise = noise;
        }

        public static class Noise {
            private static final Codec<IslandShapeConfig.Entry.Noise> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                    Codec.STRING.fieldOf("seed").forGetter(d -> d.seed),
                    Codec.INT.fieldOf("first_octave").forGetter(d -> d.first_octave),
                    Codec.DOUBLE.listOf().fieldOf("amplitudes").forGetter(d -> d.amplitudes),
                    Codec.DOUBLE.fieldOf("scale").forGetter(d -> d.scale)
                ).apply(instance, IslandShapeConfig.Entry.Noise::new)
            );

            private static final RandomSource SEED_RANDOM = RandomSource.create();

            public String seed;
            public int first_octave;
            public List<Double> amplitudes;
            public double scale;

            public Noise(String seed, int amplitudes_first_octave, List<Double> amplitudes, double scale) {
                this.seed = seed;
                this.first_octave = amplitudes_first_octave;
                this.amplitudes = amplitudes;
                this.scale = scale;
            }

            public long getSeed() {
                try {
                    return Long.parseLong(this.seed);
                } catch (NumberFormatException ignored) {
                    SEED_RANDOM.setSeed(this.seed.hashCode());
                    return SEED_RANDOM.nextLong();
                }
            }
        }
    }
}
