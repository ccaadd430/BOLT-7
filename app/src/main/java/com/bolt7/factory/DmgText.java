package com.bolt7.factory;

/**
 * 世界坐标里的伤害数字：受击时在头顶弹一下并向上飘。
 *
 * 之前只有血格减少、没有数字，玩家很难判断"这一下到底疼不疼"，
 * 也看不出招架到底挡掉了多少——所以伤害必须可见。
 */
public final class DmgText {

    public float x, y;
    public final String text;
    public final int color;
    public final float size;
    public final float life;
    public float t;

    public DmgText(float x, float y, String text, int color, float size, float life) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.size = size;
        this.life = life;
    }

    /** @return false 表示已消失 */
    public boolean update(float dt) {
        t += dt;
        if (t >= life) return false;
        // 先快后慢地往上飘，像被打出来的
        float k = 1f - t / life;
        y -= (34f * k + 8f) * dt;
        return true;
    }

    public int alpha() {
        float q = t / life;
        if (q < 0.7f) return 255;
        return (int) Math.max(0f, 255f * (1f - (q - 0.7f) / 0.3f));
    }
}
