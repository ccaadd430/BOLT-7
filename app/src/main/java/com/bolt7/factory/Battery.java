package com.bolt7.factory;

/** 地上的能量电池：普通 +1，大电池 +3。攒够当前等级所需即可升级。 */
public final class Battery {
    public static final int W = 8;
    public static final int H = 12;

    public float x, y;   // 中心
    public final boolean big;
    public boolean taken;
    public float t;

    public Battery(float x, float y, boolean big) {
        this.x = x;
        this.y = y;
        this.big = big;
        this.t = (float) (Math.random() * 6.28);
    }

    public int value() { return big ? 5 : 2; }

    public void update(float dt) {
        t += dt;
    }

    public float bob() {
        return (float) Math.sin(t * 3.2) * 1.5f;
    }
}
