package com.bolt7.factory;

/** 怪物掉落的装备箱，走近自动拾取。 */
public final class Loot {
    public static final int SIZE = 12;

    public final Item item;
    public float x, y;          // 中心
    public float t;
    public boolean taken;

    public Loot(float x, float y, Item item) {
        this.x = x;
        this.y = y;
        this.item = item;
        this.t = (float) (Math.random() * 6.28);
    }

    public void update(float dt) {
        t += dt;
    }

    public float bob() {
        return (float) Math.sin(t * 3.4) * 1.8f;
    }
}
