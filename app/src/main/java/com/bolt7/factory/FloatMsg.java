package com.bolt7.factory;

/** 顶部居中的提示条（拾取、升级、背包满），避免在世界坐标里飘字挡住战斗。 */
public final class FloatMsg {
    public final String text;
    public final int color;
    public float t;
    public final float life = 1.8f;

    public FloatMsg(String text, int color) {
        this.text = text;
        this.color = color;
    }

    public boolean update(float dt) {
        t += dt;
        return t < life;
    }

    public int alpha() {
        float q = t / life;
        if (q < 0.12f) return (int) (255 * q / 0.12f);
        return (int) Math.max(0f, 255f * (1f - (q - 0.12f) / 0.88f));
    }
}
