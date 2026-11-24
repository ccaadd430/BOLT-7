package com.bolt7.factory;

/**
 * 俯视角格子地图：地形阻挡 + 危险区标记。
 * 世界尺寸固定，最外圈是 VOID（断崖），画成深黑深渊 + 内沿钢制护栏，
 * 让"走到头"看起来是世界尽头，而不是一堵莫名其妙的墙。
 */
public final class TileMap {

    public static final byte GROUND = 0;
    public static final byte ROCK = 1;
    public static final byte WALL = 2;
    public static final byte VOID = 3;

    public final int w, h;
    public final byte[] tile;
    public final byte[] haz;    // 0 无，1 电弧区
    public final byte[] dark;   // 岩石视觉变体

    public TileMap(int w, int h) {
        this.w = w;
        this.h = h;
        tile = new byte[w * h];
        haz = new byte[w * h];
        dark = new byte[w * h];
    }

    public int idx(int tx, int ty) {
        return ty * w + tx;
    }

    public boolean inside(int tx, int ty) {
        return tx >= 0 && ty >= 0 && tx < w && ty < h;
    }

    public boolean solid(int tx, int ty) {
        if (!inside(tx, ty)) return true;
        return tile[ty * w + tx] != GROUND;
    }

    /** 断崖：地图最外圈，solid 但不可见为墙。 */
    public boolean isVoid(int tx, int ty) {
        return !inside(tx, ty) || tile[ty * w + tx] == VOID;
    }

    public boolean hazard(int tx, int ty) {
        return inside(tx, ty) && haz[ty * w + tx] != 0;
    }

    public boolean hazardAtPx(float x, float y) {
        return hazard((int) Math.floor(x / Const.TILE), (int) Math.floor(y / Const.TILE));
    }

    /** 轴对齐盒子是否撞到实心格；hw/hh 为半宽半高。 */
    public boolean boxHits(float cx, float cy, float hw, float hh) {
        int x0 = (int) Math.floor((cx - hw) / Const.TILE);
        int x1 = (int) Math.floor((cx + hw) / Const.TILE);
        int y0 = (int) Math.floor((cy - hh) / Const.TILE);
        int y1 = (int) Math.floor((cy + hh) / Const.TILE);
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (solid(tx, ty)) return true;
            }
        }
        return false;
    }

    /** 该格四周是否有实心格（渲染用，用来画岩石的接缝）。 */
    public boolean solidAround(int tx, int ty) {
        return solid(tx - 1, ty) || solid(tx + 1, ty)
                || solid(tx, ty - 1) || solid(tx, ty + 1);
    }
}
