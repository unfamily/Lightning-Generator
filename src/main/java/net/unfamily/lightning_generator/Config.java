package net.unfamily.lightning_generator;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ========== lightning_generator ==========
    static {
        BUILDER.comment("Lightning Generator: production, energy buffer and auto-lightning time ranges.").push("lightning_generator");
    }

    public static final ForgeConfigSpec.IntValue LIGHTNING_GENERATOR_RF_PER_STRIKE = BUILDER
            .comment("RF produced per lightning strike (vanilla rod or high-power rod). Default: 10000")
            .defineInRange("000_rf_per_strike", 10000, 1, 1000000);
    public static final ForgeConfigSpec.IntValue LIGHTNING_GENERATOR_CAPACITY = BUILDER
            .comment("Energy buffer capacity in RF. Default: 1000000")
            .defineInRange("010_capacity", 1000000, 1000, 100000000);
    public static final ForgeConfigSpec.IntValue LIGHTNING_GENERATOR_MAX_EXTRACT = BUILDER
            .comment("Max RF extractable per tick from the front face. Default: 10000")
            .defineInRange("020_max_extract", 10000, 1, 1000000);
    public static final ForgeConfigSpec.IntValue LIGHTNING_GENERATOR_RAIN_INTERVAL_MIN = BUILDER
            .comment("Min ticks between auto-lightning (high-power rod) when raining. 300 = 15s. Default: 300")
            .defineInRange("030_rain_interval_min", 300, 20, 12000);
    public static final ForgeConfigSpec.IntValue LIGHTNING_GENERATOR_RAIN_INTERVAL_MAX = BUILDER
            .comment("Max ticks between auto-lightning when raining. 600 = 30s. Default: 600")
            .defineInRange("031_rain_interval_max", 600, 20, 12000);
    public static final ForgeConfigSpec.IntValue LIGHTNING_GENERATOR_THUNDER_INTERVAL_MIN = BUILDER
            .comment("Min ticks between auto-lightning in thunderstorm. 60 = 3s. Default: 60")
            .defineInRange("040_thunder_interval_min", 60, 10, 600);
    public static final ForgeConfigSpec.IntValue LIGHTNING_GENERATOR_THUNDER_INTERVAL_MAX = BUILDER
            .comment("Max ticks between auto-lightning in thunderstorm. 100 = 5s. Default: 100")
            .defineInRange("041_thunder_interval_max", 100, 10, 600);
    public static final ForgeConfigSpec.IntValue LIGHTNING_GENERATOR_DRAGON_RF = BUILDER
            .comment("RF produced when a Lightning Dragon (Ice and Fire) strikes the high-power lightning rod. Same tier as dragon forge. Default: 1000")
            .defineInRange("050_dragon_rf", 1000, 1, 1000000);

    static {
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();
}
