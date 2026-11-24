package com.bolt7.factory;

/**
 * 像素图：一串字符画解码成 ARGB 数组。所有精灵共用一张全局调色板，
 * 好处是画的时候只需要记字符含义，配色统一，且换色只改一处。
 */
public final class Spr {

    public final int w, h;
    public final int[] px;

    private Spr(int w, int h, int[] px) {
        this.w = w;
        this.h = h;
        this.px = px;
    }

    public static Spr of(int w, int h) {
        return new Spr(w, h, new int[w * h]);
    }

    public static Spr parse(String... rows) {
        int h = rows.length;
        int w = rows[0].length();
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            if (r.length() != w) {
                throw new IllegalArgumentException(
                        "字符画第 " + y + " 行宽度 " + r.length() + "，应为 " + w + "：" + r);
            }
            for (int x = 0; x < w; x++) {
                px[y * w + x] = colorOf(r.charAt(x));
            }
        }
        return new Spr(w, h, px);
    }

    public Spr mirrorX() {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = px[y * w + (w - 1 - x)];
            }
        }
        return new Spr(w, h, out);
    }

    /** 向 color 混合 amt（0..1），用于受击闪白。 */
    public Spr tint(int color, float amt) {
        int[] out = new int[px.length];
        float a = Math.max(0f, Math.min(1f, amt));
        int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int pa = (p >>> 24);
            if (pa == 0) {
                out[i] = 0;
                continue;
            }
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            r = (int) (r + (cr - r) * a);
            g = (int) (g + (cg - g) * a);
            b = (int) (b + (cb - b) * a);
            out[i] = (pa << 24) | (r << 16) | (g << 8) | b;
        }
        return new Spr(w, h, out);
    }

    /** 全局调色板：一字符一色，'.' 透明。 */
    public static int colorOf(char c) {
        switch (c) {
            case '.': return 0x00000000;
            // 冷灰钢件
            case 'K': return 0xFF0C1013;
            case 'D': return 0xFF232A31;
            case 'M': return 0xFF3A444D;
            case 'L': return 0xFF5C6B77;
            case 'w': return 0xFF8C9AA6;
            case 'W': return 0xFFDCE6EE;
            // 暖色 / 危险
            case 'R': return 0xFF7E2119;
            case 'r': return 0xFFE8452F;
            case 'O': return 0xFFF08A2C;
            case 'o': return 0xFFB0762E;
            case 'y': return 0xFFF2C14E;
            // 冷色 / 资源
            case 'g': return 0xFF3F7A28;
            case 'G': return 0xFF7CE04A;
            case 'c': return 0xFF46D8E6;
            case 'b': return 0xFF3E7BD6;
            case 'B': return 0xFF2F6B7A;
            case 'p': return 0xFF9A6BE0;
            case 'P': return 0xFF5B3C8C;
            // 材质
            case 'n': return 0xFF6B543C;
            case 'N': return 0xFF3E3128;
            case 'a': return 0xFFA98A5F;
            case 's': return 0xFF8A8578;
            case 'S': return 0xFFBDB7A8;
            case 'm': return 0xFFE24FB0;
            default: throw new IllegalArgumentException("未知像素字符: " + c);
        }
    }
}
