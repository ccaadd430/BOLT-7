package com.bolt7.factory;

/** 玩家：俯视角八向移动，武器扇形挥击，冲刺带短暂无敌。 */
public final class Player {

    public static final float HW = 5f;   // 碰撞半宽
    public static final float HH = 5f;   // 碰撞半高

    public float x, y;                   // 中心
    public float vx, vy;
    public float faceX = 0f, faceY = 1f;

    public int hp;
    public float invuln;
    public float hurtFlash;

    public float atkT, atkCd;            // 挥击动画 / 冷却
    public boolean atkHitDone;
    public float atkDir = 1f;            // 每次挥击换向，避免动作重复感
    public float atkAng;                 // 挥击起始角（弧度），开挥时锁定
    /** 输入缓冲：冷却中按下的这一下先记着，冷却一到立刻出刀。 */
    public float atkBuf;

    public float dashT, dashCd;
    public float dashDirX, dashDirY;

    /** 招架：攻击命中敌人后进入，剩余时间 > 0 时受伤只吃 10%。 */
    public float guard;
    public float guardFlash;

    public boolean dead;
    public float walkT;
    public float spawnProtect;

    public Player(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void update(float dt, Input in, TileMap map, Game g) {
        if (dead) return;
        if (invuln > 0f) invuln -= dt;
        if (spawnProtect > 0f) spawnProtect -= dt;
        if (hurtFlash > 0f) hurtFlash -= dt;
        if (guard > 0f) guard = Math.max(0f, guard - dt);
        if (guardFlash > 0f) guardFlash -= dt;
        if (atkCd > 0f) atkCd -= dt;
        if (dashCd > 0f) dashCd -= dt;
        if (atkT > 0f) {
            atkT -= dt;
            if (atkT <= 0f) atkT = 0f;
        }

        // ---- 挥击：输入缓冲 + 辅助瞄准 + 按武器类型出招 ----
        if (in.attackPressed) atkBuf = Const.ATK_BUFFER;
        if (atkBuf > 0f && atkCd <= 0f) {
            swing(g);
            atkBuf = 0f;
        } else if (atkBuf > 0f) {
            atkBuf = Math.max(0f, atkBuf - dt);
        }

        if (in.dashPressed && dashCd <= 0f) {
            dashCd = g.dashCd();
            dashT = Const.DASH_T;
            float len = (float) Math.hypot(faceX, faceY);
            dashDirX = len > 0.01f ? faceX / len : 0f;
            dashDirY = len > 0.01f ? faceY / len : 1f;
            invuln = Math.max(invuln, Const.DASH_T + 0.06f);
            g.onDash();
        }

        float dirX = in.moveX, dirY = in.moveY;
        float len = (float) Math.hypot(dirX, dirY);
        float mag = Math.min(1f, len);
        if (len > 0.01f) {
            dirX /= len;
            dirY /= len;
            // 挥砍期间锁朝向：不然身体会立刻扭回摇杆方向，读起来像"刀在背后"
            if (atkT <= 0f) {
                faceX = dirX;
                faceY = dirY;
            }
        }

        float speed = g.runSpeed();
        if (dashT > 0f) {
            dashT -= dt;
            vx = dashDirX * Const.DASH_V;
            vy = dashDirY * Const.DASH_V;
            if (dashT <= 0f) {
                vx *= 0.35f;
                vy *= 0.35f;
            }
        } else if (len > 0.01f) {
            // 推杆幅度真的生效：轻推走 55%，推满 100%。手机上精细走位靠这个。
            float k2 = 0.55f + 0.45f * mag;
            float tx = dirX * speed * k2, ty = dirY * speed * k2;
            float k = Math.min(1f, Const.ACCEL * dt / Math.max(1f, speed));
            vx += (tx - vx) * k;
            vy += (ty - vy) * k;
        } else {
            float f = (float) Math.pow(0.002f, dt);
            vx *= f;
            vy *= f;
            if (Math.abs(vx) < 3f) vx = 0f;
            if (Math.abs(vy) < 3f) vy = 0f;
        }

        move(map, vx * dt, vy * dt);
        walkT += (float) Math.hypot(vx, vy) * dt * 0.16f;

        // 危险区：站上去就掉血，冲刺期间免疫
        if (map.hazardAtPx(x, y) && invuln <= 0f && dashT <= 0f) {
            hurt(1, x, y - 10f, g);
        }
    }

    /** 出刀：锁定朝向（含辅助瞄准）、按武器类型给冷却/动画/前冲。 */
    private void swing(Game g) {
        float ang = g.aimAngle();
        atkAng = ang;
        faceX = (float) Math.cos(ang);
        faceY = (float) Math.sin(ang);
        atkCd = g.atkCd();
        atkT = g.atkAnim();
        atkHitDone = false;
        atkDir = -atkDir;
        // 只有在推摇杆时才前冲：原地挥砍会自己滑走，是上一版最难受的地方
        if (Math.hypot(g.input.moveX, g.input.moveY) > 0.01f) {
            float lunge = Weapon.lunge(g.weaponType());
            vx += faceX * lunge;
            vy += faceY * lunge;
        }
        g.onSwing();
    }

    private void move(TileMap map, float dx, float dy) {
        float steps = Math.max(1f, (float) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 2f));
        float sx = dx / steps, sy = dy / steps;
        for (int i = 0; i < (int) steps; i++) {
            if (sx != 0f && !map.boxHits(x + sx, y, HW, HH)) x += sx;
            if (sy != 0f && !map.boxHits(x, y + sy, HW, HH)) y += sy;
        }
        float minX = 2 * Const.TILE + HW + 1f, minY = 2 * Const.TILE + HH + 1f;
        float maxX = (Const.MAP_W - 2) * Const.TILE - HW - 1f;
        float maxY = (Const.MAP_H - 2) * Const.TILE - HH - 1f;
        if (x < minX) x = minX;
        if (y < minY) y = minY;
        if (x > maxX) x = maxX;
        if (y > maxY) y = maxY;
    }

    /** 受到伤害；from 用于击退方向。招架期间只吃 10%，但至少留 1 点。 */
    public void hurt(int dmg, float fromX, float fromY, Game g) {
        if (invuln > 0f || dead || spawnProtect > 0f) return;
        // 幸运词条：庇护护盾 / 闪避先结算，吃掉了就一点血都不掉
        if (g.absorbHit()) return;
        boolean blocked = guard > 0f;
        int real = blocked ? Math.max(1, Math.round(dmg * Const.GUARD_KEEP)) : dmg;
        hp -= real;
        invuln = Const.INVULN;
        if (blocked) {
            guardFlash = Const.GUARD_FLASH;
        } else {
            hurtFlash = 0.25f;
        }
        float dx = x - fromX, dy = y - fromY;
        float d = (float) Math.hypot(dx, dy);
        if (d < 0.01f) {
            dx = 0f;
            dy = 1f;
            d = 1f;
        }
        vx = dx / d * (blocked ? 60f : 150f);
        vy = dy / d * (blocked ? 60f : 150f);
        g.onPlayerHurt(real, blocked, x, y);
        if (hp <= 0) {
            hp = 0;
            g.killPlayer();
        }
    }
}
