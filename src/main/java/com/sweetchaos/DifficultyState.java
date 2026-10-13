package com.sweetchaos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Interruptores de dificultad, guardados en el mundo (sobreviven a reinicios). Todos empiezan apagados. */
public class DifficultyState extends SavedData {
    private static final String NAME = "sweet_chaos_difficulty";

    public boolean alebrije; // Alebrije Tiger aparece en sweet_lands
    public boolean krampus;  // Krampus aparece en cloying_fields y no suelta loot
    public boolean rain;     // la lluvia hace dano
    public boolean icyOnly;  // los mobs de Sweet Calamity (y Krampus) solo reciben dano de icy_sweetness

    public static DifficultyState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DifficultyState::load, DifficultyState::new, NAME);
    }

    public static DifficultyState load(CompoundTag tag) {
        DifficultyState s = new DifficultyState();
        s.alebrije = tag.getBoolean("alebrije");
        s.krampus = tag.getBoolean("krampus");
        s.rain = tag.getBoolean("rain");
        s.icyOnly = tag.getBoolean("icyOnly");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("alebrije", alebrije);
        tag.putBoolean("krampus", krampus);
        tag.putBoolean("rain", rain);
        tag.putBoolean("icyOnly", icyOnly);
        return tag;
    }
}
