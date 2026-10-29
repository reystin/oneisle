package com.reystin.oneisle.continent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Optional v3 shoreline settings. A null value selects the legacy v1/v2 ribbon. */
public record ShoreSettings(double density, double variety, String style, boolean rocky) {
    public static final ShoreSettings DEFAULT = new ShoreSettings(0.30, 0.6, "natural", true);
    public static final ShoreSettings DISABLED = new ShoreSettings(0.0, 0.6, "natural", false);

    public static final Codec<ShoreSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.DOUBLE.optionalFieldOf("density", 0.30).forGetter(ShoreSettings::density),
        Codec.DOUBLE.optionalFieldOf("variety", 0.6).forGetter(ShoreSettings::variety),
        Codec.STRING.optionalFieldOf("style", "natural").forGetter(ShoreSettings::style),
        Codec.BOOL.optionalFieldOf("rocky", true).forGetter(ShoreSettings::rocky)
    ).apply(instance, ShoreSettings::new));

    public ShoreSettings {
        density = Math.max(0.0, Math.min(1.0, density));
        variety = Math.max(0.0, Math.min(1.0, variety));
        style = style == null ? "natural" : style;
    }

    public boolean enabled() {
        return density > 0.0;
    }
}
