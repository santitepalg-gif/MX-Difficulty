package com.sweetchaos;

import net.minecraftforge.common.ForgeConfigSpec;

public class SweetChaosConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue RAIN_DAMAGE;
    public static final ForgeConfigSpec.IntValue RAIN_INTERVAL_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("rain");
        RAIN_DAMAGE = b.comment("Dano que hace la lluvia a un jugador expuesto (20 = 10 corazones).")
                .defineInRange("damage", 20.0D, 0.0D, 1000.0D);
        RAIN_INTERVAL_TICKS = b.comment("Cada cuantos ticks se aplica el dano (20 ticks = 1 segundo).")
                .defineInRange("intervalTicks", 20, 1, 24000);
        b.pop();
        SPEC = b.build();
    }
}
