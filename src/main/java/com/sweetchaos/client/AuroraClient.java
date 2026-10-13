package com.sweetchaos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sweetchaos.SweetChaosMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Efecto puramente visual (cliente): oscurece el cielo, dibuja una aurora boreal animada y estrellas fugaces.
 * No cambia la luz del mundo ni el ciclo dia/noche.
 */
@Mod.EventBusSubscriber(modid = SweetChaosMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuroraClient {

    private static final float SKY_R = 0.012f, SKY_G = 0.02f, SKY_B = 0.06f;
    private static final float DOME_RADIUS = 100f;

    private static boolean active;
    private static float intensity;
    private static float prevIntensity;

    private static final RandomSource RNG = RandomSource.create();
    private static final List<Meteor> METEORS = new ArrayList<>();
    private static float[][] stars;

    private AuroraClient() {}

    public static void setActive(boolean value) {
        active = value;
    }

    // ------------------------------------------------------------------ ticks

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;

        prevIntensity = intensity;
        float target = active ? 1f : 0f;
        intensity += Mth.clamp(target - intensity, -0.01f, 0.02f); // entra en ~2.5 s, sale en ~5 s

        if (mc.level == null || intensity <= 0f) {
            METEORS.clear();
            return;
        }
        for (Meteor m : METEORS) m.age++;
        METEORS.removeIf(m -> m.age >= m.life);
        if (intensity > 0.6f && RNG.nextInt(28) == 0) {
            METEORS.add(Meteor.random(RNG));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        intensity = 0f;
        prevIntensity = 0f;
        METEORS.clear();
    }

    // ------------------------------------------------------------------ fog (el horizonte tambien se oscurece)

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float a = Mth.lerp((float) event.getPartialTick(), prevIntensity, intensity);
        if (a <= 0.001f) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimensionType().hasSkyLight()) return;
        float k = a * 0.92f;
        event.setRed(Mth.lerp(k, event.getRed(), SKY_R));
        event.setGreen(Mth.lerp(k, event.getGreen(), SKY_G));
        event.setBlue(Mth.lerp(k, event.getBlue(), SKY_B));
    }

    // ------------------------------------------------------------------ render

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float partial = event.getPartialTick();
        float a = Mth.lerp(partial, prevIntensity, intensity);
        if (a <= 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !level.dimensionType().hasSkyLight()) return;

        Matrix4f m = event.getPoseStack().last().pose();
        float t = level.getGameTime() + partial;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // 1) cielo oscuro
        RenderSystem.defaultBlendFunc();
        drawDome(m, a * 0.92f);

        // 2) estrellas, aurora y estrellas fugaces (mezcla aditiva)
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        drawStars(m, a, t);
        drawAurora(m, a, t);
        drawMeteors(m, a, partial);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static BufferBuilder begin() {
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return buf;
    }

    private static void end(BufferBuilder buf) {
        BufferUploader.drawWithShader(buf.end());
    }

    private static void vtx(BufferBuilder b, Matrix4f m, float x, float y, float z, float r, float g, float bl, float a) {
        b.vertex(m, x, y, z).color(r, g, bl, a).endVertex();
    }

    private static float[] dir(float elev, float azim) {
        float ce = Mth.cos(elev);
        return new float[]{ce * Mth.sin(azim), Mth.sin(elev), -ce * Mth.cos(azim)};
    }

    // ---- cupula oscura

    private static void drawDome(Matrix4f m, float alpha) {
        final int stacks = 10, slices = 24;
        BufferBuilder b = begin();
        for (int i = 0; i < stacks; i++) {
            float e0 = Mth.PI * (-0.5f + (float) i / stacks);
            float e1 = Mth.PI * (-0.5f + (float) (i + 1) / stacks);
            for (int j = 0; j < slices; j++) {
                float a0 = Mth.TWO_PI * j / slices;
                float a1 = Mth.TWO_PI * (j + 1) / slices;
                float[] p00 = dir(e0, a0), p01 = dir(e0, a1), p11 = dir(e1, a1), p10 = dir(e1, a0);
                vtx(b, m, p00[0] * DOME_RADIUS, p00[1] * DOME_RADIUS, p00[2] * DOME_RADIUS, SKY_R, SKY_G, SKY_B, alpha);
                vtx(b, m, p01[0] * DOME_RADIUS, p01[1] * DOME_RADIUS, p01[2] * DOME_RADIUS, SKY_R, SKY_G, SKY_B, alpha);
                vtx(b, m, p11[0] * DOME_RADIUS, p11[1] * DOME_RADIUS, p11[2] * DOME_RADIUS, SKY_R, SKY_G, SKY_B, alpha);
                vtx(b, m, p10[0] * DOME_RADIUS, p10[1] * DOME_RADIUS, p10[2] * DOME_RADIUS, SKY_R, SKY_G, SKY_B, alpha);
            }
        }
        end(b);
    }

    // ---- estrellas fijas

    private static void initStars() {
        RandomSource r = RandomSource.create(4242L);
        stars = new float[280][];
        for (int i = 0; i < stars.length; i++) {
            float y = Mth.clamp(r.nextFloat() * 1.12f - 0.12f, -0.1f, 1f);
            float az = r.nextFloat() * Mth.TWO_PI;
            float rad = Mth.sqrt(1f - y * y);
            float x = rad * Mth.sin(az), z = -rad * Mth.cos(az);
            // u = d x up, v = d x u
            float ux = -z, uy = 0f, uz = x;
            float ul = Mth.sqrt(ux * ux + uz * uz);
            if (ul < 1e-3f) { ux = 1f; uz = 0f; ul = 1f; }
            ux /= ul; uz /= ul;
            float vx = y * uz - z * uy, vy = z * ux - x * uz, vz = x * uy - y * ux;
            float size = 0.25f + r.nextFloat() * 0.55f;
            stars[i] = new float[]{x, y, z, ux, uy, uz, vx, vy, vz, size, r.nextFloat() * Mth.TWO_PI};
        }
    }

    private static void drawStars(Matrix4f m, float a, float t) {
        if (stars == null) initStars();
        final float R = 95f;
        BufferBuilder b = begin();
        for (float[] s : stars) {
            float tw = 0.45f + 0.55f * (0.5f + 0.5f * Mth.sin(t * 0.08f + s[10]));
            float al = a * tw;
            float cx = s[0] * R, cy = s[1] * R, cz = s[2] * R;
            float k = s[9];
            float ux = s[3] * k, uy = s[4] * k, uz = s[5] * k;
            float vx = s[6] * k, vy = s[7] * k, vz = s[8] * k;
            vtx(b, m, cx - ux - vx, cy - uy - vy, cz - uz - vz, 0.9f, 0.95f, 1f, al);
            vtx(b, m, cx + ux - vx, cy + uy - vy, cz + uz - vz, 0.9f, 0.95f, 1f, al);
            vtx(b, m, cx + ux + vx, cy + uy + vy, cz + uz + vz, 0.9f, 0.95f, 1f, al);
            vtx(b, m, cx - ux + vx, cy - uy + vy, cz - uz + vz, 0.9f, 0.95f, 1f, al);
        }
        end(b);
    }

    // ---- aurora boreal: cortinas onduladas, verde abajo -> turquesa -> violeta arriba

    private static final float[] ROW_F = {0f, 0.12f, 0.45f, 1f};
    private static final float[] ROW_A = {0.10f, 0.90f, 0.38f, 0f};
    private static final float[][] ROW_C = {
            {0.30f, 1.00f, 0.50f},
            {0.20f, 1.00f, 0.45f},
            {0.20f, 0.80f, 0.80f},
            {0.60f, 0.30f, 0.90f}
    };

    private static void drawAurora(Matrix4f m, float a, float t) {
        final int layers = 3, cols = 72;
        final float span = 1.9f; // +-109 grados alrededor del norte
        BufferBuilder b = begin();
        for (int l = 0; l < layers; l++) {
            float radius = 96f - l * 4f;
            float offset = (l - 1) * 0.30f;
            float[][][] ring = new float[cols + 1][ROW_F.length][3]; // posiciones
            float[] bright = new float[cols + 1];
            for (int i = 0; i <= cols; i++) {
                float phi = -span + 2f * span * i / cols;
                float base = 0.22f + 0.07f * Mth.sin(phi * 2.3f + t * 0.008f + l);
                float height = 0.50f + 0.18f * Mth.sin(phi * 4f - t * 0.015f + l * 2.1f);
                float s = 0.5f + 0.5f * Mth.sin(phi * 6f + t * 0.02f + l * 1.9f);
                float edge = 1f - Mth.clamp((Math.abs(phi) - (span - 0.6f)) / 0.6f, 0f, 1f);
                bright[i] = (0.35f + 0.65f * s * s) * edge;
                for (int r = 0; r < ROW_F.length; r++) {
                    float[] d = dir(base + height * ROW_F[r], phi + offset);
                    ring[i][r][0] = d[0] * radius;
                    ring[i][r][1] = d[1] * radius;
                    ring[i][r][2] = d[2] * radius;
                }
            }
            for (int i = 0; i < cols; i++) {
                for (int r = 0; r < ROW_F.length - 1; r++) {
                    put(b, m, ring[i][r], ROW_C[r], ROW_A[r] * bright[i] * a * 0.8f);
                    put(b, m, ring[i][r + 1], ROW_C[r + 1], ROW_A[r + 1] * bright[i] * a * 0.8f);
                    put(b, m, ring[i + 1][r + 1], ROW_C[r + 1], ROW_A[r + 1] * bright[i + 1] * a * 0.8f);
                    put(b, m, ring[i + 1][r], ROW_C[r], ROW_A[r] * bright[i + 1] * a * 0.8f);
                }
            }
        }
        end(b);
    }

    private static void put(BufferBuilder b, Matrix4f m, float[] p, float[] c, float alpha) {
        vtx(b, m, p[0], p[1], p[2], c[0], c[1], c[2], alpha);
    }

    // ---- estrellas fugaces

    private static final class Meteor {
        final float px, py, pz, tx, ty, tz, speed;
        final int life;
        int age;

        Meteor(float px, float py, float pz, float tx, float ty, float tz, float speed, int life) {
            this.px = px; this.py = py; this.pz = pz;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.speed = speed; this.life = life;
        }

        static Meteor random(RandomSource r) {
            float[] p = dir(0.25f + r.nextFloat() * 1.0f, r.nextFloat() * Mth.TWO_PI);
            // tangente aleatoria perpendicular a p
            float rx = r.nextFloat() - 0.5f, ry = r.nextFloat() - 0.5f, rz = r.nextFloat() - 0.5f;
            float dot = rx * p[0] + ry * p[1] + rz * p[2];
            float tx = rx - dot * p[0], ty = ry - dot * p[1], tz = rz - dot * p[2];
            float len = Mth.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1e-4f) { tx = 1f; ty = 0f; tz = 0f; len = 1f; }
            return new Meteor(p[0], p[1], p[2], tx / len, ty / len, tz / len,
                    0.05f + r.nextFloat() * 0.04f, 12 + r.nextInt(14));
        }

        float[] at(float theta) {
            float c = Mth.cos(theta), s = Mth.sin(theta);
            return new float[]{px * c + tx * s, py * c + ty * s, pz * c + tz * s};
        }
    }

    private static void drawMeteors(Matrix4f m, float a, float partial) {
        if (METEORS.isEmpty()) return;
        final float R = 92f;
        BufferBuilder b = begin();
        for (Meteor mt : METEORS) {
            float age = mt.age + partial;
            float theta = mt.speed * age;
            float[] head = mt.at(theta);
            float[] tail = mt.at(Math.max(0f, theta - 0.22f));
            float hx = head[0] * R, hy = head[1] * R, hz = head[2] * R;
            float tx = tail[0] * R, ty = tail[1] * R, tz = tail[2] * R;
            // ancho perpendicular al trazo: cross(H, T-H)
            float dx = tx - hx, dy = ty - hy, dz = tz - hz;
            float wx = hy * dz - hz * dy, wy = hz * dx - hx * dz, wz = hx * dy - hy * dx;
            float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1e-4f) continue;
            float w = 0.28f / wl;
            wx *= w; wy *= w; wz *= w;
            float fade = Mth.sin(Mth.PI * Mth.clamp(age / mt.life, 0f, 1f)) * a;
            vtx(b, m, tx - wx, ty - wy, tz - wz, 0.5f, 0.7f, 1f, 0f);
            vtx(b, m, tx + wx, ty + wy, tz + wz, 0.5f, 0.7f, 1f, 0f);
            vtx(b, m, hx + wx, hy + wy, hz + wz, 1f, 1f, 1f, fade);
            vtx(b, m, hx - wx, hy - wy, hz - wz, 1f, 1f, 1f, fade);
        }
        end(b);
    }
}
