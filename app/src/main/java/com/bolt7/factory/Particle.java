package com.bolt7.factory;

/** 像素粒子：火花、爆炸、尘土。俯视角，没有重力，只有阻尼。 */
public final class Particle {
    public float x, y, vx, vy;
    public float life, maxLife;
    public int color;
    public int size;

    public Particle(float x, float y, float vx, float vy, float life, int color, int size) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.color = color;
        this.size = size;
    }

    /** @return false 表示已消失 */
    public boolean update(float dt) {
        life -= dt;
        if (life <= 0f) return false;
        x += vx * dt;
        y += vy * dt;
        float d = (float) Math.pow(0.06f, dt);   // 强阻尼，像在空气里而不是真空
        vx *= d;
        vy *= d;
        return true;
    }

    public float alpha() {
        float a = Math.max(0f, life / maxLife);
        return a * a;
    }
}
