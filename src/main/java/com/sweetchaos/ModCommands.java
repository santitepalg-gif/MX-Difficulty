package com.sweetchaos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.sweetchaos.network.AuroraPacket;
import com.sweetchaos.network.ModNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

public class ModCommands {

    /** Estado de la aurora en el servidor (se envia a los clientes). */
    public static boolean auroraActive = false;

    private enum Flag {
        ALEBRIJE("toggle_alebrije_SweetCalamity", "Alebrije Tiger en sweet_lands"),
        KRAMPUS("toggle_krampus_SweetCalamity", "Krampus en cloying_fields (sin loot)"),
        RAIN("toggle_rain_SweetCalamity", "Lluvia con dano"),
        ICY("toggle_icy_SweetCalamity", "Mobs de Sweet Calamity solo vulnerables a icy_sweetness");

        final String command;
        final String label;

        Flag(String command, String label) {
            this.command = command;
            this.label = label;
        }

        boolean get(DifficultyState s) {
            return switch (this) {
                case ALEBRIJE -> s.alebrije;
                case KRAMPUS -> s.krampus;
                case RAIN -> s.rain;
                case ICY -> s.icyOnly;
            };
        }

        void set(DifficultyState s, boolean v) {
            switch (this) {
                case ALEBRIJE -> s.alebrije = v;
                case KRAMPUS -> s.krampus = v;
                case RAIN -> s.rain = v;
                case ICY -> s.icyOnly = v;
            }
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        for (Flag f : Flag.values()) {
            d.register(Commands.literal(f.command)
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> toggle(ctx, f)));
        }

        d.register(Commands.literal("toggle_status_SweetCalamity")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    DifficultyState s = DifficultyState.get(ctx.getSource().getServer());
                    for (Flag f : Flag.values()) {
                        boolean on = f.get(s);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                (on ? "[ON]  " : "[OFF] ") + f.label + "  (/" + f.command + ")"), false);
                    }
                    return 1;
                }));

        d.register(Commands.literal("aurora")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("start").executes(ctx -> aurora(ctx, true)))
                .then(Commands.literal("stop").executes(ctx -> aurora(ctx, false))));
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, Flag f) {
        DifficultyState s = DifficultyState.get(ctx.getSource().getServer());
        boolean now = !f.get(s);
        f.set(s, now);
        s.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal(
                f.label + ": " + (now ? "ACTIVADO" : "DESACTIVADO")), true);
        return 1;
    }

    private static int aurora(CommandContext<CommandSourceStack> ctx, boolean start) {
        auroraActive = start;
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new AuroraPacket(start));
        ctx.getSource().sendSuccess(() -> Component.literal(
                start ? "Aurora boreal iniciada." : "Aurora boreal detenida."), true);
        return 1;
    }
}
