package com.bolt7.factory;

/** 可采集的资源点：目前只有废料堆，敲三下掉废铁，偶尔掉装备。 */
public final class Node {
    public static final int SCRAP = 0;

    public final float x, y;      // 中心
    public final int kind;
    public int hp = 3;
    public float flash;
    public boolean taken;
    public float t;

    public Node(float x, float y, int kind) {
        this.x = x;
        this.y = y;
        this.kind = kind;
        this.t = (float) (Math.random() * 6.28);
    }

    public void update(float dt) {
        t += dt;
        if (flash > 0f) flash -= dt;
    }
}
