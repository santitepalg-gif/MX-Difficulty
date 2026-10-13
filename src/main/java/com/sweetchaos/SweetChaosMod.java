package com.sweetchaos;

import com.sweetchaos.network.ModNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SweetChaosMod.MODID)
public class SweetChaosMod {
    public static final String MODID = "sweet_chaos_difficulty";

    public SweetChaosMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SweetChaosConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
