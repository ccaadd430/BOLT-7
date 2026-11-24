package com.bolt7.factory;

/**
 * 敌方弹丸：直线飞行，撞地形消失，冲刺可以打掉。
 * v9：任何武器的挥击也能把它切掉；带"反弹"词条时会被打回去变成我方弹丸（friendly）。
 */
public final class Plasma {
    public static final float R = 2.6f;

    public float x, y, vx, vy;
    public float t;
    public boolean alive = true;
    public final boolean acid;
    /** 反弹后变成我方弹丸：只打怪，不打玩家。 */
    public boolean friendly;
    /** 反弹伤害，出手时写入。 */
    public int dmg = 1;

    public Plasma(float x, float y, float vx, float vy) {
        this(x, y, vx, vy, false);
    }

    public Plasma(float x, float y, float vx, float vy, boolean acid) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.acid = acid;
    }

    public void update(float dt, TileMap map) {
        if (!alive) return;
        t += dt;
        x += vx * dt;
        y += vy * dt;
        if (t > (friendly ? 2.6f : 3.2f)) {
            alive = false;
            return;
        }
        if (map.solid((int) Math.floor(x / Const.TILE), (int) Math.floor(y / Const.TILE))) {
            alive = false;
        }
    }
}
