package com.sweetchaos;

import com.sweetchaos.network.AuroraPacket;
import com.sweetchaos.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = SweetChaosMod.MODID)
public class ServerEvents {

    private static final ResourceLocation SWEET_LANDS = new ResourceLocation("sweet_calamity", "sweet_lands");
    private static final ResourceLocation CLOYING_FIELDS = new ResourceLocation("sweet_calamity", "cloying_fields");
    private static final ResourceLocation ALEBRIJE = new ResourceLocation("alebrije_tiger", "alebrije_tiger");
    private static final ResourceLocation KRAMPUS = new ResourceLocation("born_in_chaos_v1", "krampus");
    private static final ResourceLocation ICY_SWEETNESS = new ResourceLocation("born_in_chaos_v1", "icy_sweetness");

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    // ---------------------------------------------------------------- aurora sync

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (ModCommands.auroraActive && event.getEntity() instanceof ServerPlayer sp) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new AuroraPacket(true));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ModCommands.auroraActive = false;
    }

    // ---------------------------------------------------------------- spawns

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id == null) return;
        boolean isAlebrije = id.equals(ALEBRIJE);
        boolean isKrampus = id.equals(KRAMPUS);
        if (!isAlebrije && !isKrampus) return;

        ServerLevel level = event.getLevel().getLevel();
        DifficultyState state = DifficultyState.get(level.getServer());

        if (isAlebrije && !state.alebrije && level.dimension().location().equals(SWEET_LANDS)) {
            event.setResult(Event.Result.DENY);
        }
        if (isKrampus && !state.krampus) {
            BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
            if (level.getBiome(pos).is(CLOYING_FIELDS)) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    // ---------------------------------------------------------------- krampus: sin loot

    private static boolean isKrampus(LivingEntity e) {
        return KRAMPUS.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    @SubscribeEvent
    public static void onKrampusDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !isKrampus(e)) return;
        if (DifficultyState.get(e.level().getServer()).krampus) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKrampusXp(LivingExperienceDropEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !isKrampus(e)) return;
        if (DifficultyState.get(e.level().getServer()).krampus) {
            event.setDroppedExperience(0);
        }
    }

    // ---------------------------------------------------------------- solo icy_sweetness hace dano

    private static boolean isSweetMob(LivingEntity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && (id.getNamespace().equals("sweet_calamity") || id.equals(KRAMPUS));
    }

    private static boolean damageAllowed(DamageSource src) {
        // /kill y el vacio siempre funcionan, para no volver imposible la administracion.
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        Entity attacker = src.getEntity();
        if (attacker instanceof LivingEntity living) {
            return ICY_SWEETNESS.equals(ForgeRegistries.ITEMS.getKey(living.getMainHandItem().getItem()));
        }
        return false;
    }

    private static boolean shouldBlock(LivingEntity target, DamageSource src) {
        if (target.level().isClientSide) return false;
        if (!isSweetMob(target)) return false;
        if (!DifficultyState.get(target.level().getServer()).icyOnly) return false;
        return !damageAllowed(src);
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (shouldBlock(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (shouldBlock(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- lluvia con dano

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % SweetChaosConfig.RAIN_INTERVAL_TICKS.get() != 0) return;
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return;

        ServerLevel level = player.serverLevel();
        if (!level.isRaining()) return;
        if (!DifficultyState.get(level.getServer()).rain) return;

        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        if (level.isRainingAt(head)) {
            // "magic" ignora la armadura, asi el dano es el configurado (20 por defecto).
            player.hurt(level.damageSources().magic(), SweetChaosConfig.RAIN_DAMAGE.get().floatValue());
        }
    }
}
