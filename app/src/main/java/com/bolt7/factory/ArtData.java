package com.bolt7.factory;

/**
 * 全部美术资源：字符画 + 程序化纹理。没有外部图片文件。
 * 俯视角，角色与怪物都画成"从上斜看"的 3/4 视角，并自带深色描边。
 */
public final class ArtData {
    private ArtData() {}

    public static final Spr PLAYER_DOWN;
    public static final Spr PLAYER_UP;
    public static final Spr PLAYER_SIDE;
    public static final Spr PLAYER_SIDE_L;

    public static final Spr CRAWLER;
    public static final Spr DRONE;
    public static final Spr TURRET;
    public static final Spr CHARGER;
    public static final Spr WARDEN;
    public static final Spr BOMBER;
    public static final Spr SHIELDER;
    public static final Spr SPITTER;
    public static final Spr LEAPER;

    public static final Spr SCRAP;
    public static final Spr SCRAP_EMPTY;
    public static final Spr BATTERY;
    public static final Spr LOOT;

    public static final Spr GROUND;

    static {
        PLAYER_DOWN = Spr.parse(
                "....KKKKKK....",
                "..KKLLLLLLKK..",
                ".KLLLLLLLLLLK.",
                ".KLLccccccLLK.",
                ".KLLcWWWWcLLK.",
                ".KLLccccccLLK.",
                ".KLLLLLLLLLLK.",
                "..KKLLLLLLKK..",
                ".KKDyyDDyyDKK.",
                "KDDDwwwwwwDDDK",
                "KDDwwLLLLwwDDK",
                "KDDwwLKKLwwDDK",
                "KDDwwLLLLwwDDK",
                ".KKDDwwwwDDKK.",
                "..KKDDDDDDKK..",
                "...KKDDDDKK...");

        PLAYER_UP = Spr.parse(
                "....KKKKKK....",
                "..KKLLLLLLKK..",
                ".KLLLLLLLLLLK.",
                ".KLLDDDDDDLLK.",
                ".KLLDMMMMDLLK.",
                ".KLLDMooMDLLK.",
                ".KLLLLLLLLLLK.",
                "..KKLLLLLLKK..",
                ".KKDyyDDyyDKK.",
                "KDDDwwwwwwDDDK",
                "KDDwwLLLLwwDDK",
                "KDDwwLDDLwwDDK",
                "KDDwwLLLLwwDDK",
                ".KKDDwwwwDDKK.",
                "..KKDDDDDDKK..",
                "...KKDDDDKK...");

        PLAYER_SIDE = Spr.parse(
                "....KKKKKK....",
                "..KKLLLLLLKK..",
                ".KLLLLLLLLLLK.",
                ".KLLLLLLLcccK.",
                ".KLLLLLLLcWcK.",
                ".KLLLLLLLcccK.",
                ".KLLLLLLLLLLK.",
                "..KKLLLLLLKK..",
                ".KKDyyDDyyDKK.",
                "KDDDwwwwwwDDDK",
                "KDDwwLLLLLwwDK",
                "KDDwwLKKLLwwDK",
                "KDDwwLLLLLwwDK",
                ".KKDDwwwwwDKK.",
                "..KKDDDDDDKK..",
                "...KKDDDDKK...");
        PLAYER_SIDE_L = PLAYER_SIDE.mirrorX();

        CRAWLER = Spr.parse(
                "................",
                "..K..........K..",
                "..KK...KK...KK..",
                "...KK.KKKK.KK...",
                "....KKrrrrKK....",
                "...KrrrrrrrrK...",
                "..KrrOOOOrrrrK..",
                "..KrOOKKKKOOrK..",
                "..KrOOKWWKOOrK..",
                "..KrOOKKKKOOrK..",
                "..KrrOOOOrrrrK..",
                "...KrrrrrrrrK...",
                "....KKrrrrKK....",
                "...KK.KKKK.KK...",
                "..KK...KK...KK..",
                "..K..........K..");

        DRONE = Spr.parse(
                "................",
                "..KK........KK..",
                ".KwwK......KwwK.",
                ".KwwK..KK..KwwK.",
                "..KK..KppK..KK..",
                "......KppK......",
                "....KKppppKK....",
                "...KpppppppPK...",
                "..KpppWWWWppPK..",
                "..KppWWccWWppK..",
                "..KpppWWWWppPK..",
                "...KppppppppK...",
                "....KKppppKK....",
                "......KKKK......",
                "................",
                "................");

        TURRET = Spr.parse(
                "................",
                "....KKKKKKKK....",
                "...KyyyyyyyyK...",
                "..KyyssssssyyK..",
                "..KyssssssssyK..",
                "..KysKKKKKKsyK..",
                "..KysKBBBBKsyK..",
                "..KysKBWWBssyK..",
                "..KysKBBBBKsyK..",
                "..KysKKKKKKsyK..",
                "..KyssssssssyK..",
                "..KyyssssssyyK..",
                "...KyyyyyyyyK...",
                "....KKKKKKKK....",
                "................",
                "................");

        CHARGER = Spr.parse(
                "................",
                "..K..........K..",
                "..KK...KK...KK..",
                "...KK.KrrK.KK...",
                "....KKrrrrKK....",
                "...KrrrrrrrrK...",
                "..KrRRRRRRRRrK..",
                "..KrRWWWWWWRrK..",
                "..KrRKKKKKKRrK..",
                "..KrRWWWWWWRrK..",
                "..KrRRRRRRRRrK..",
                "...KrrrrrrrrK...",
                "....KKrrrrKK....",
                "...KK.KKKK.KK...",
                "..KK...KK...KK..",
                "..K..........K..");

        WARDEN = Spr.parse(
                "................",
                "...KK......KK...",
                "..KMMK....KMMK..",
                "..KMMKKKKKKMMK..",
                "...KMMMMMMMMK...",
                "..KMMwwwwwwMMK..",
                ".KMMwKKKKKKwMMK.",
                ".KMMwKrrrrKwMMK.",
                ".KMMwKrWWrKwMMK.",
                ".KMMwKrrrrKwMMK.",
                ".KMMwKKKKKKwMMK.",
                ".KMMwwwwwwwwMMK.",
                "..KMMwwwwwwMMK..",
                "...KMMMMMMMMK...",
                "..KKKK....KKKK..",
                "................");

        BOMBER = Spr.parse(
                "................",
                ".......KK.......",
                "......KmmK......",
                "..KK..KmmK..KK..",
                ".KmmK.KmmK.KmmK.",
                ".KmmmKKmmKKmmmK.",
                "..KmmmmmmmmmmK..",
                "...KmmKKKKmmK...",
                "..KmmKWWWWKmmK..",
                "..KmmKWKKWKmmK..",
                "..KmmKWWWWKmmK..",
                "...KmmKKKKmmK...",
                "..KmmmmmmmmmmK..",
                ".KmmK.KmmK.KmmK.",
                ".KK....KK....KK.",
                "................");

        SHIELDER = Spr.parse(
                "................",
                "...KKKKKKKKKK...",
                "..KMMMMMMMMMMK..",
                "..KMwwwwwwwwMK..",
                "..KMwKKKKKKwMK..",
                "..KMwKccccKwMK..",
                "..KMwKccccKwMK..",
                "..KMwKKKKKKwMK..",
                "..KMwwwwwwwwMK..",
                "..KMMMMMMMMMMK..",
                "...KMMMMMMMMK...",
                "..KKMMwwwwMMKK..",
                ".KMMK.MMMM.KMMK.",
                ".KMK..KMMK..KMK.",
                "..K...KKKK...K..",
                "................");

        SPITTER = Spr.parse(
                "................",
                "....KK....KK....",
                "...KgGK..KgGK...",
                "....KgGKKGgK....",
                ".....KggggK.....",
                "....KgGGGGgK....",
                "...KgGcGGcGgK...",
                "..KgGGGGGGGGgK..",
                "..KgGGKKKKGGgK..",
                "..KgGKWWWWKGgK..",
                "..KgGGKKKKGGgK..",
                "...KgGGGGGGgK...",
                "....KggggggK....",
                "..KK.KggggK.KK..",
                ".KgGK.KggK.KgGK.",
                "..KK...KK...KK..");

        LEAPER = Spr.parse(
                "................",
                "..KK........KK..",
                "..KyK......KyK..",
                "...KOOKKKKOOK...",
                "....KOOKKOOK....",
                "...KOOyKKyOOK...",
                "..KOOOKWWKOOOK..",
                "..KOOOKWWKOOOK..",
                "..KOOOyKKyOOOK..",
                "...KOOOOOOOOK...",
                "...KOOKKKKOOK...",
                "..KOOK....KOOK..",
                "..KK........KK..",
                "................",
                "................",
                "................");

        SCRAP = Spr.parse(
                "................",
                "................",
                "....KKKK........",
                "...KSSSSKK......",
                "..KSSaaSSSK.....",
                "..KSannnaSSKK...",
                ".KSSannnaSSSSK..",
                ".KSaannnaaaSSK..",
                ".KSSaaaaaaSSSK..",
                "..KSSSSSSSSSK...",
                "..KKaSSSSaaKK...",
                "...KKaSSSKK.....",
                "....KKKKKK......",
                "................",
                "................",
                "................");

        SCRAP_EMPTY = Spr.parse(
                "................",
                "................",
                "................",
                "................",
                "................",
                "................",
                "....KKKKKK......",
                "...KNNNNNNK.....",
                "...KNDDDDNK.....",
                "...KNNNNNNK.....",
                "....KKKKKK......",
                "................",
                "................",
                "................",
                "................",
                "................");

        BATTERY = Spr.parse(
                "..KKKK..",
                ".KWWWWK.",
                ".KGGGGK.",
                "KGGGGGGK",
                "KGgggggK",
                "KGgGGGgK",
                "KGgGcGgK",
                "KGgGGGgK",
                "KGgggggK",
                "KGGGGGGK",
                ".KGGGGK.",
                ".KggggK.",
                "..KKKK..",
                "........");

        LOOT = Spr.parse(
                "............",
                "..KKKKKKKK..",
                ".KNNNNNNNNK.",
                ".KNaaaaaaNK.",
                ".KNaKKKKNaNK",
                ".KNaKccKNaNK",
                ".KNaKccKNaNK",
                ".KNaKKKKNaNK",
                ".KNaaaaaaNK.",
                ".KNNNNNNNNK.",
                "..KKKKKKKK..",
                "............");

        GROUND = makeGround();
    }

    /**
     * 128x128 可平铺地面：低频值噪声 + 细颗粒 + 稀疏裂缝。
     *
     * 刻意不加规则接缝——每 32 像素一条网格线会把地面变成"办公室地砖"。
     * 地形结构交给岩石、断墙和排水格栅，地面本身只要"有起伏的暗色调"。
     */
    private static Spr makeGround() {
        int n = 128;
        Spr s = Spr.of(n, n);
        java.util.Random r = new java.util.Random(0xB0177L);

        // 两层低频噪声（16 / 32 网格），双线性插值，可平铺
        float[] low = smoothNoise(r, 16, n, 0.62f);
        float[] mid = smoothNoise(r, 32, n, 0.38f);

        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int i = y * n + x;
                float t = low[i] + mid[i];
                // 量化成三档，保持像素块感；阈值抖动让边缘不是等高线
                float j = (r.nextInt(100) - 50) * 0.0022f;
                int c = t + j < 0.30f ? Pal.GROUND_C
                        : t + j < 0.62f ? Pal.GROUND_A : Pal.GROUND_B;
                s.px[i] = c;
            }
        }

        // 细颗粒：每 1/260 个像素一颗，深浅各半
        for (int i = 0; i < 260; i++) {
            int x = r.nextInt(n), y = r.nextInt(n);
            s.px[y * n + x] = r.nextBoolean() ? Pal.GROUND_DUST : 0xFF1C2228;
        }

        // 几片油渍，压在噪声之上
        for (int i = 0; i < 2; i++) {
            float cx = r.nextInt(n), cy = r.nextInt(n);
            float rad = 10 + r.nextFloat() * 13;
            for (int y = (int) (cy - rad); y <= cy + rad; y++) {
                for (int x = (int) (cx - rad); x <= cx + rad; x++) {
                    int yy = (y + n) % n, xx = (x + n) % n;
                    float dx = x - cx, dy = y - cy;
                    if (dx * dx + dy * dy < rad * rad) {
                        float d = (float) Math.sqrt(dx * dx + dy * dy) / rad;
                        if (d < 0.45f || r.nextInt(100) < 30) {
                            s.px[yy * n + xx] = Pal.GROUND_OIL;
                        }
                    }
                }
            }
        }

        // 稀疏裂缝：少量、短、带分叉
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(n), y = r.nextInt(n);
            int len = 3 + r.nextInt(7);
            boolean horiz = r.nextBoolean();
            for (int k = 0; k < len; k++) {
                if (horiz) x = (x + 1) % n;
                else y = (y + 1) % n;
                s.px[y * n + x] = Pal.GROUND_CRACK;
                if (r.nextInt(3) == 0) {
                    int xx = (x + (r.nextBoolean() ? 1 : -1) + n) % n;
                    s.px[y * n + xx] = Pal.GROUND_CRACK;
                }
            }
        }
        return s;
    }

    /** 可平铺的值噪声：g×g 网格双线性放大到 n×n，返回 0..amp 的浮点场。 */
    private static float[] smoothNoise(java.util.Random r, int g, int n, float amp) {
        float[] grid = new float[g * g];
        for (int i = 0; i < grid.length; i++) grid[i] = r.nextFloat();
        float[] out = new float[n * n];
        for (int y = 0; y < n; y++) {
            float fy = y / (float) n * g;
            int y0 = (int) fy % g, y1 = (y0 + 1) % g;
            float ty = fy - (float) Math.floor(fy);
            float sy = ty * ty * (3f - 2f * ty);       // smoothstep，斑块边缘更软
            for (int x = 0; x < n; x++) {
                float fx = x / (float) n * g;
                int x0 = (int) fx % g, x1 = (x0 + 1) % g;
                float tx = fx - (float) Math.floor(fx);
                float sx = tx * tx * (3f - 2f * tx);
                float a = grid[y0 * g + x0] * (1 - sx) + grid[y0 * g + x1] * sx;
                float b = grid[y1 * g + x0] * (1 - sx) + grid[y1 * g + x1] * sx;
                out[y * n + x] = (a * (1 - sy) + b * sy) * amp;
            }
        }
        return out;
    }
}
