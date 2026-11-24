package com.bolt7.factory;

import java.util.Random;

/**
 * 九种怪物，行为一眼可分：
 *   爬虫   巡逻 → 贴身撞击
 *   无人机 悬浮 → 绕圈骚扰
 *   炮台   不动 → 蓄力后射单发
 *   冲锋兽 蓄力（闪红）→ 直线冲撞
 *   守卫   高血量精英 → 三连散射，必掉装备
 *   自爆虫 冲到脸上 → 引信闪烁 → 范围爆炸（自己也炸没）
 *   盾卫   缓慢推进，正面挡 75% 伤害，必须绕到侧后
 *   喷吐者 保持中距 → 两连酸液弹
 *   跳虫   蹲伏蓄力 → 大跳扑击，落地有冲击波
 */
public final class Enemy {

    public static final int CRAWLER = 0;
    public static final int DRONE = 1;
    public static final int TURRET = 2;
    public static final int CHARGER = 3;
    public static final int WARDEN = 4;
    public static final int BOMBER = 5;
    public static final int SHIELDER = 6;
    public static final int SPITTER = 7;
    public static final int LEAPER = 8;
    public static final int TYPES = 9;

    public static final String[] TYPE_NAME = {
            "爬虫", "无人机", "炮台", "冲锋兽", "守卫", "自爆虫", "盾卫", "喷吐者", "跳虫"
    };

    public static final float R = 6f;

    public final int type;
    /** Boss：体型放大、血量按区放大、有独立血条与阶段技能。 */
    public final boolean boss;
    public float x, y;
    public float vx, vy;
    public int hp, maxHp;
    public float flash;
    public float hitCd;
    public float stun;            // 被玩家打出的硬直
    public int zone;              // 所在区域 0/1/2，影响血量与伤害
    public boolean alive = true;
    public float t;
    public float aimX = 1f, aimY = 0f;
    /** 盾卫：刚挡住一次攻击时闪一下，给玩家"打正面没用"的即时反馈。 */
    public float blockFlash;

    private float think;      // 行为计时
    private int mode;         // 0 巡逻 / 1 追击 / 2 蓄力 / 3 冲刺 / 4 冷却
    private float wanderA;
    private float fireCd;

    public Enemy(float x, float y, int type) {
        this(x, y, type, false);
    }

    public Enemy(float x, float y, int type, boolean boss) {
        this.x = x;
        this.y = y;
        this.type = Math.max(0, Math.min(TYPES - 1, type));
        this.boss = boss;
        switch (this.type) {
            case DRONE: maxHp = 4; break;
            case TURRET: maxHp = 6; break;
            case CHARGER: maxHp = 9; break;
            case WARDEN: maxHp = 18; break;
            case BOMBER: maxHp = 5; break;
            case SHIELDER: maxHp = 12; break;
            case SPITTER: maxHp = 5; break;
            case LEAPER: maxHp = 7; break;
            default: maxHp = 4; break;
        }
        if (boss) {
            // Boss 统一用守卫的外形与行为，血量按体型放大，避免再写 9 套 AI
            maxHp = Math.round(maxHp * Const.BOSS_HP_MUL);
        }
        hp = maxHp;
        t = (float) (Math.random() * 6.28);
        wanderA = (float) (Math.random() * 6.28);
        fireCd = 0.8f + (float) Math.random();
        think = 0.6f + (float) Math.random() * 0.6f;
    }

    /** 碰撞半径：Boss 大一圈，攻击距离和接触判定都跟着变。 */
    public float radius() {
        return boss ? Const.BOSS_R : R;
    }

    public boolean elite() {
        return type == WARDEN;
    }

    public int color() {
        switch (type) {
            case DRONE: return Pal.E_DRONE;
            case TURRET: return Pal.E_TURRET;
            case CHARGER: return Pal.E_CHARGER;
            case WARDEN: return Pal.E_WARDEN;
            case BOMBER: return Pal.E_BOMBER;
            case SHIELDER: return Pal.E_SHIELDER;
            case SPITTER: return Pal.E_SPITTER;
            case LEAPER: return Pal.E_LEAPER;
            default: return Pal.E_CRAWLER;
        }
    }

    public float dropChance() {
        switch (type) {
            case WARDEN: return 1.0f;
            case SHIELDER: return 0.75f;
            case CHARGER: return 0.55f;
            case LEAPER: return 0.55f;
            case TURRET: return 0.5f;
            case SPITTER: return 0.45f;
            case BOMBER: return 0.35f;
            default: return 0.3f;
        }
    }

    public float speed() {
        switch (type) {
            case DRONE: return 54f;
            case TURRET: return 0f;
            case CHARGER: return 34f;
            case WARDEN: return 30f;
            case BOMBER: return 56f;
            case SHIELDER: return 26f;
            case SPITTER: return 40f;
            case LEAPER: return 46f;
            default: return 48f;
        }
    }

    /** 接触伤害：1 级只有 0.6 倍（新手期不疼），随等级线性抬高。 */
    public int contactDamage(int level) {
        int base;
        switch (type) {
            case WARDEN:
            case CHARGER:
            case BOMBER:
                base = 6;
                break;
            case SHIELDER:
            case LEAPER:
                base = 5;
                break;
            case SPITTER:
                base = 3;
                break;
            default:
                base = 4;
        }
        float mul = Const.DMG_BASE + Const.DMG_PER_LV * Math.max(1, level);
        if (boss) base *= 1.7f;
        return Math.max(1, Math.round(base * mul));
    }

    /** 生成时按玩家等级放大血量；区域越外越硬（v8）。 */
    public void scaleTo(int level) {
        scaleTo(level, 0);
    }

    public void scaleTo(int level, int zone) {
        int z = Math.max(0, Math.min(Const.ZONE_HP.length - 1, zone));
        float f = (1f + Math.max(0, level - 1) * Const.ENEMY_HP_PER_LV) * Const.ZONE_HP[z];
        maxHp = Math.max(1, Math.round(maxHp * f));
        hp = maxHp;
    }

    public void update(float dt, TileMap map, Player p, Game g, Random r) {
        if (!alive) return;
        t += dt;
        if (flash > 0f) flash -= dt;
        if (blockFlash > 0f) blockFlash -= dt;
        if (hitCd > 0f) hitCd -= dt;
        // 硬直：被刀打停的这段时间不动也不出手，是"打得动"的即时反馈
        if (stun > 0f) {
            stun -= dt;
            vx *= 0.55f;
            vy *= 0.55f;
            return;
        }

        float dx = p.x - x, dy = p.y - y;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 0.01f) dist = 0.01f;
        float nx = dx / dist, ny = dy / dist;
        if (p.dead) {
            vx *= 0.9f;
            vy *= 0.9f;
        } else if (boss) {
            aimX = nx;
            aimY = ny;
            bossAI(dt, p, g, r, dist, nx, ny);
        } else if (type == TURRET) {
            aimX = nx;
            aimY = ny;
            turret(dt, p, g, r, dist);
        } else if (type == WARDEN) {
            aimX = nx;
            aimY = ny;
            warden(dt, p, g, r, dist, nx, ny);
        } else if (type == CHARGER) {
            charger(dt, p, g, nx, ny, dist);
        } else if (type == DRONE) {
            drone(dt, p, nx, ny, dist);
        } else if (type == BOMBER) {
            bomber(dt, p, g, nx, ny, dist);
        } else if (type == SHIELDER) {
            shielder(dt, p, nx, ny, dist);
        } else if (type == SPITTER) {
            spitter(dt, p, g, r, nx, ny, dist);
        } else if (type == LEAPER) {
            leaper(dt, p, g, nx, ny, dist);
        } else {
            crawler(dt, p, nx, ny, dist);
        }

        if (type != TURRET) {
            move(map, vx * dt, vy * dt);
        }
    }

    private void crawler(float dt, Player p, float nx, float ny, float dist) {
        if (!p.dead && dist < 190f) {
            mode = 1;
            float wobble = (float) Math.sin(t * 7f) * 0.35f;
            vx = (nx - ny * wobble) * speed();
            vy = (ny + nx * wobble) * speed();
        } else {
            mode = 0;
            wanderA += (float) Math.sin(t * 1.7f) * dt * 2f;
            vx = (float) Math.cos(wanderA) * speed() * 0.45f;
            vy = (float) Math.sin(wanderA) * speed() * 0.45f;
        }
    }

    private void drone(float dt, Player p, float nx, float ny, float dist) {
        if (!p.dead && dist < 210f) {
            float want = 44f;
            float push = dist > want ? 1f : -0.75f;
            float orbit = 0.85f;
            vx = (nx * push - ny * orbit) * speed();
            vy = (ny * push + nx * orbit) * speed();
        } else {
            float a = (float) (t * 1.3);
            x += (float) Math.cos(a) * 6f * dt;
            vx = 0f;
            vy = 0f;
        }
    }

    private void turret(float dt, Player p, Game g, Random r, float dist) {
        if (p.dead) return;
        fireCd -= dt;
        if (dist < 200f && fireCd <= 0f) {
            mode = 2;
            if (fireCd < -0.45f) {
                g.fireEnemyPlasma(x, y - 2f, aimX * 150f, aimY * 150f);
                fireCd = 1.7f + r.nextFloat() * 0.7f;
                mode = 0;
            }
        }
    }

    private void warden(float dt, Player p, Game g, Random r, float dist, float nx, float ny) {
        if (p.dead) return;
        vx = nx * speed();
        vy = ny * speed();
        fireCd -= dt;
        if (dist < 240f && fireCd <= 0f) {
            fireCd = 2.4f;
            for (int i = -1; i <= 1; i++) {
                float a = (float) Math.atan2(ny, nx) + i * 0.34f;
                g.fireEnemyPlasma(x, y - 2f, (float) Math.cos(a) * 130f, (float) Math.sin(a) * 130f);
            }
        }
    }

    /** 贴上来自爆：引信期间抖、闪、外圈收窄，给玩家跑开的时间。 */
    private void bomber(float dt, Player p, Game g, float nx, float ny, float dist) {
        if (p.dead) {
            vx *= 0.9f;
            vy *= 0.9f;
            return;
        }
        if (mode == 2) {
            vx = 0f;
            vy = 0f;
            think -= dt;
            if (think <= 0f) {
                alive = false;
                hp = 0;
                g.enemyExplode(this);
            }
            return;
        }
        vx = nx * speed();
        vy = ny * speed();
        if (dist < 30f) {
            mode = 2;
            think = Const.BOMB_FUSE;
            g.onEnemyArm();
        }
    }

    /**
     * Boss：体型大、走得慢、扇形弹幕 + 近身冲击波。
     * 血量掉到一半后弹幕从 3 发变 5 发，掉到 1/4 再补一圈环形，作为"阶段"。
     */
    private void bossAI(float dt, Player p, Game g, Random r, float dist, float nx, float ny) {
        if (p.dead) return;
        float ratio = maxHp <= 0 ? 1f : hp / (float) maxHp;
        float stop = 26f + radius();
        if (dist > stop) {
            vx = nx * speed();
            vy = ny * speed();
        } else {
            vx = 0f;
            vy = 0f;
        }
        fireCd -= dt;
        think -= dt;
        if (dist < 330f && fireCd <= 0f) {
            fireCd = (ratio < 0.5f ? 1.7f : 2.3f) + r.nextFloat() * 0.5f;
            int n = ratio < 0.5f ? 5 : 3;
            float a0 = (float) Math.atan2(ny, nx);
            for (int i = 0; i < n; i++) {
                float a = a0 + (i - (n - 1) / 2f) * 0.26f;
                g.fireEnemyPlasma(x, y - 4f, (float) Math.cos(a) * 132f, (float) Math.sin(a) * 132f);
            }
            if (ratio < 0.32f) {
                for (int i = 0; i < 10; i++) {
                    float a = (float) (Math.PI * 2 * i / 10.0) + t;
                    g.fireEnemyPlasma(x, y - 4f, (float) Math.cos(a) * 104f, (float) Math.sin(a) * 104f);
                }
            }
        }
        // 贴身冲击波：逼玩家保持距离，不能站着撸
        if (dist < 42f && think <= 0f) {
            think = 3.2f;
            g.enemyShock(x, y);
        }
    }

    /** 盾卫：正面朝向玩家慢慢压上来，正面挨打只吃 25%。 */
    private void shielder(float dt, Player p, float nx, float ny, float dist) {
        if (p.dead) {
            vx *= 0.9f;
            vy *= 0.9f;
            return;
        }
        aimX = nx;
        aimY = ny;
        float stop = 18f;
        if (dist > stop) {
            vx = nx * speed();
            vy = ny * speed();
        } else {
            vx = 0f;
            vy = 0f;
        }
    }

    private void spitter(float dt, Player p, Game g, Random r, float nx, float ny, float dist) {
        if (p.dead) return;
        float want = 96f;
        float push = dist > want + 20f ? 1f : dist < want - 20f ? -1f : 0f;
        vx = nx * push * speed();
        vy = ny * push * speed();
        fireCd -= dt;
        if (dist < 210f && fireCd <= 0f) {
            fireCd = 2.0f + r.nextFloat() * 0.6f;
            float a0 = (float) Math.atan2(ny, nx);
            for (int i = -1; i <= 1; i += 2) {
                float a = a0 + i * 0.13f;
                g.fireAcid(x, y - 2f, (float) Math.cos(a) * 112f, (float) Math.sin(a) * 112f);
            }
        }
    }

    /** 跳虫：蹲伏 → 扑过来 → 落地冲击波。 */
    private void leaper(float dt, Player p, Game g, float nx, float ny, float dist) {
        think -= dt;
        if (p.dead) {
            mode = 0;
            vx *= 0.9f;
            vy *= 0.9f;
            return;
        }
        switch (mode) {
            case 2:      // 蹲伏
                vx = 0f;
                vy = 0f;
                if (think <= 0f) {
                    mode = 3;
                    think = 0.34f;
                    aimX = nx;
                    aimY = ny;
                    g.onLeap();
                }
                break;
            case 3:      // 空中
                vx = aimX * 300f;
                vy = aimY * 300f;
                if (think <= 0f) {
                    mode = 4;
                    think = 0.55f;
                    g.enemyShock(x, y);
                }
                break;
            case 4:
                vx *= 0.8f;
                vy *= 0.8f;
                if (think <= 0f) mode = 0;
                break;
            default:
                vx = nx * speed();
                vy = ny * speed();
                if (dist < 118f) {
                    mode = 2;
                    think = 0.45f;
                }
                break;
        }
    }

    private void charger(float dt, Player p, Game g, float nx, float ny, float dist) {
        think -= dt;
        if (p.dead) {
            mode = 0;
            vx *= 0.9f;
            vy *= 0.9f;
            return;
        }
        switch (mode) {
            case 0:
                vx = nx * speed();
                vy = ny * speed();
                if (dist < 150f && think <= 0f) {
                    mode = 2;
                    think = 0.55f;
                    aimX = nx;
                    aimY = ny;
                }
                break;
            case 2:
                vx = 0f;
                vy = 0f;
                aimX = nx;
                aimY = ny;
                if (think <= 0f) {
                    mode = 3;
                    think = 0.36f;
                    g.onChargerDash();
                }
                break;
            case 3:
                vx = aimX * 235f;
                vy = aimY * 235f;
                if (think <= 0f) {
                    mode = 4;
                    think = 0.7f;
                }
                break;
            default:
                vx *= 0.86f;
                vy *= 0.86f;
                if (think <= 0f) {
                    mode = 0;
                    think = 0.4f;
                }
                break;
        }
    }

    /** 击退也要走碰撞，否则会被打进岩石里卡死。 */
    private void knock(TileMap map, float dx, float dy) {
        float r = radius();
        float steps = Math.max(1f, (float) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 2f));
        for (int i = 0; i < (int) steps; i++) {
            if (!map.boxHits(x + dx / steps, y, r, r)) x += dx / steps;
            if (!map.boxHits(x, y + dy / steps, r, r)) y += dy / steps;
        }
    }

    private void move(TileMap map, float dx, float dy) {
        float r = radius();
        float steps = Math.max(1f, (float) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 2f));
        float sx = dx / steps, sy = dy / steps;
        for (int i = 0; i < (int) steps; i++) {
            if (sx != 0f && !map.boxHits(x + sx, y, r, r)) x += sx;
            if (sy != 0f && !map.boxHits(x, y + sy, r, r)) y += sy;
        }
    }

    /**
     * 受伤。盾卫正面挡 75%——攻击方向与我朝向的夹角在 ±52° 内算"正面"。
     *
     * @return true 表示这一击致死
     */
    public boolean damage(int dmg, float fromX, float fromY, TileMap map) {
        int real = dmg;
        if (type == SHIELDER) {
            float ax = fromX - x, ay = fromY - y;
            float al = (float) Math.hypot(ax, ay);
            if (al > 0.01f) {
                float dot = (ax / al) * aimX + (ay / al) * aimY;
                if (dot > Const.SHIELD_ARC) {
                    real = Math.max(1, Math.round(dmg * Const.SHIELD_BLOCK));
                    blockFlash = 0.18f;
                }
            }
        }
        hp -= real;
        flash = 0.16f;
        float dx = x - fromX, dy = y - fromY;
        float d = (float) Math.hypot(dx, dy);
        if (d < 0.01f) {
            dx = 0f;
            dy = 1f;
            d = 1f;
        }
        float kb;
        if (boss) kb = 2f;
        else if (type == WARDEN) kb = 10f;
        else if (type == TURRET || type == SHIELDER) kb = 3f;
        else kb = 30f;
        knock(map, dx / d * kb, dy / d * kb);
        if (hp <= 0) {
            hp = 0;
            alive = false;
            return true;
        }
        return false;
    }

    /** 实际造成的伤害（给飘字用，避免显示被盾挡掉的原值）。 */
    public int lastDamage(int dmg) {
        if (type != SHIELDER) return dmg;
        return Math.max(1, Math.round(dmg * Const.SHIELD_BLOCK));
    }

    public boolean windingUp() {
        return mode == 2 && type != BOMBER;
    }

    /** 跳虫蹲伏蓄力中（渲染压扁）。 */
    public boolean crouching() {
        return type == LEAPER && mode == 2;
    }

    /** 跳虫在空中（渲染投影缩小）。 */
    public boolean airborne() {
        return type == LEAPER && mode == 3;
    }

    /** 自爆虫引信是否已点燃。 */
    public boolean armed() {
        return type == BOMBER && mode == 2;
    }

    /** 引信剩余比例 0..1（画收窄的红圈）。 */
    public float fuseQ() {
        return Game.clamp(think / Const.BOMB_FUSE, 0f, 1f);
    }

    /** 盾卫是否正在挡（渲染盾弧用）。 */
    public boolean shielded() {
        return type == SHIELDER;
    }

    public float shieldAngle() {
        return (float) Math.toDegrees(Math.atan2(aimY, aimX));
    }
}
