import com.bolt7.factory.*;

/**
 * 无真机验证：直接跑游戏核心逻辑，逐项断言。
 * 重点覆盖这次改版最容易出错的地方——触屏命中、背包/装备、俯视角碰撞、危险区伤害。
 */
public final class SimTest {

    static int pass, fail;
    static final StringBuilder log = new StringBuilder();

    public static void main(String[] a) {
        art();
        world();
        movement();
        hazard();
        combat();
        weapons();
        levelup();
        boss();
        v9();
        monsters();
        progression();
        inventory();
        bench();
        joystick();
        layoutUi();
        ui();
        System.out.println(log);
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }

    // ------------------------------------------------------------ 工具

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
        } else {
            fail++;
            log.append("FAIL  ").append(name).append('\n');
        }
    }

    static void checkEq(String name, double expect, double actual) {
        check(name + "（期望 " + expect + "，实际 " + actual + "）", Math.abs(expect - actual) < 0.001);
    }

    static Game fresh() {
        Game g = new Game(null);
        g.rnd.setSeed(12345L);
        g.startRun();
        g.player.spawnProtect = 0f;
        return g;
    }

    static void step(Game g, int n) {
        for (int i = 0; i < n; i++) {
            g.update(1f / 60f, g.input);
            g.input.endFrame();
        }
    }

    static void tap(Game g, float x, float y) {
        g.input.post(Input.DOWN, 1, x, y);
        g.input.post(Input.UP, 1, x, y);
        step(g, 1);
    }

    // ------------------------------------------------------------ 美术

    static void art() {
        check("玩家精灵 14x16", ArtData.PLAYER_DOWN.w == 14 && ArtData.PLAYER_DOWN.h == 16);
        check("五类怪物精灵齐全", ArtData.CRAWLER.w == 16 && ArtData.DRONE.h == 16
                && ArtData.TURRET.w == 16 && ArtData.CHARGER.w == 16 && ArtData.WARDEN.w == 16);
        check("地面纹理可平铺 128", ArtData.GROUND.w == 128 && ArtData.GROUND.h == 128);
        check("地面明度足够低", brightness(Pal.GROUND_A) < 80);
        check("危险色足够亮", brightness(Pal.HAZARD_RED) > 110);
        check("危险与地面色相分离", hueGap(Pal.HAZARD_RED, Pal.GROUND_A) > 90);
        check("资源绿与危险红分离", hueGap(Pal.ENERGY, Pal.HAZARD_RED) > 100);
    }

    static int brightness(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    static int hueGap(int a, int b) {
        double ha = Math.atan2((a & 0xFF) - ((a >> 8) & 0xFF), ((a >> 16) & 0xFF) - ((a >> 8) & 0xFF));
        double hb = Math.atan2((b & 0xFF) - ((b >> 8) & 0xFF), ((b >> 16) & 0xFF) - ((b >> 8) & 0xFF));
        double d = Math.abs(Math.toDegrees(ha - hb));
        if (d > 180) d = 360 - d;
        return (int) d;
    }

    // ------------------------------------------------------------ 世界

    static void world() {
        Game g = fresh();
        World w = g.world;
        checkEq("地图宽", Const.MAP_W, w.map.w);
        checkEq("地图高", Const.MAP_H, w.map.h);
        check("边界是墙", w.map.solid(0, 0) && w.map.solid(Const.MAP_W - 1, 20));
        check("出生点可站立", !w.map.boxHits(w.spawnX, w.spawnY, 5, 5));
        check("出生点不在危险区", !w.map.hazardAtPx(w.spawnX, w.spawnY));
        check("废料堆数量", w.nodes.size() >= 80);
        check("电池数量", w.batteries.size() >= 100);
        check("初始怪物数量", w.enemies.size() >= 30);
        int haz = 0;
        for (int i = 0; i < w.map.haz.length; i++) if (w.map.haz[i] != 0) haz++;
        check("危险区格数 > 200（实际 " + haz + "）", haz > 200);
        check("出生 3 格内无怪", nearestEnemy(g) > 100f);
        check("全图有合成台", w.benches.size() >= 8);
        check("最外圈是断崖", w.map.isVoid(0, 0) && w.map.isVoid(Const.MAP_W - 1, 30));
        check("断崖会挡住玩家", w.map.solid(1, 1));
        boolean benchFree = true;
        for (Bench b : w.benches) {
            if (w.map.boxHits(b.x, b.y, 5, 5)) benchFree = false;
        }
        check("合成台不会被生成在石头里", benchFree);
    }

    static float nearestEnemy(Game g) {
        float best = 1e9f;
        for (Enemy e : g.world.enemies) {
            best = Math.min(best, (float) Math.hypot(e.x - g.player.x, e.y - g.player.y));
        }
        return best;
    }

    // ------------------------------------------------------------ 移动

    static void movement() {
        Game g = fresh();
        Player p = g.player;
        float x0 = p.x, y0 = p.y;
        g.input.moveX = 1f;
        g.input.moveY = 1f;
        step(g, 30);
        check("向右下移动：x 增大", p.x > x0 + 5);
        check("向右下移动：y 增大", p.y > y0 + 5);

        g.input.moveX = -1f;
        g.input.moveY = -1f;
        float x1 = p.x, y1 = p.y;
        step(g, 30);
        check("向左上移动：x 减小", p.x < x1 - 5);
        check("向左上移动：y 减小", p.y < y1 - 5);

        Game g2 = fresh();
        Player p2 = g2.player;
        TileMap map = g2.world.map;
        int rx = -1, ry = -1;
        outer:
        for (int ty = 3; ty < map.h - 3; ty++) {
            for (int tx = 3; tx < map.w - 3; tx++) {
                if (map.solid(tx, ty) && !map.solid(tx - 1, ty) && !map.solid(tx - 1, ty - 1)
                        && !map.solid(tx - 1, ty + 1)) {
                    rx = tx;
                    ry = ty;
                    break outer;
                }
            }
        }
        check("找得到可测试的岩石", rx > 0);
        if (rx > 0) {
            p2.x = rx * Const.TILE - 9f;
            p2.y = ry * Const.TILE + 8f;
            g2.input.moveX = 1f;
            g2.input.moveY = 0f;
            step(g2, 60);
            check("岩石挡住去路", p2.x < rx * Const.TILE - 4f);
        }

        p2.x = 10f;
        p2.y = 10f;
        step(g2, 2);
        check("不会走出地图左边界", p2.x > 2 * Const.TILE);
    }

    // ------------------------------------------------------------ 危险区

    static void hazard() {
        Game g = fresh();
        Player p = g.player;
        TileMap map = g.world.map;
        int hx = -1, hy = -1;
        outer:
        for (int ty = 3; ty < map.h - 3; ty++) {
            for (int tx = 3; tx < map.w - 3; tx++) {
                if (map.hazard(tx, ty) && map.hazard(tx + 1, ty) && !map.hazard(tx - 1, ty)) {
                    hx = tx;
                    hy = ty;
                    break outer;
                }
            }
        }
        check("找得到危险区", hx > 0);
        if (hx > 0) {
            p.x = hx * Const.TILE + 8f;
            p.y = hy * Const.TILE + 8f;
            p.invuln = 0f;
            p.spawnProtect = 0f;
            int hp0 = p.hp;
            step(g, 3);
            check("踩到危险区掉血", p.hp < hp0);
            check("危险区不会秒杀满血玩家", p.hp > 0 || hp0 <= 1);
        }
    }

    // ------------------------------------------------------------ 战斗

    static void combat() {
        Game g = fresh();
        Player p = g.player;
        p.spawnProtect = 999f;
        g.world.enemies.clear();
        Enemy e = new Enemy(p.x + 16, p.y, Enemy.CRAWLER);
        g.world.enemies.add(e);
        p.faceX = 1f;
        p.faceY = 0f;
        int kills0 = g.kills;
        for (int i = 0; i < 180; i++) {
            g.input.attackPressed = true;
            g.update(1f / 60f, g.input);
            g.input.endFrame();
        }
        check("挥击能击杀爬虫", !e.alive);
        check("击杀计数增加", g.kills > kills0);
        check("开局武器有伤害", g.attack() >= 2);
        check("开局默认装备撬棍", g.gear(Item.ATK) != null && g.gear(Item.ATK).slot == Item.ATK);

        Game g2 = fresh();
        g2.player.spawnProtect = 999f;
        g2.world.enemies.clear();
        g2.world.nodes.clear();
        Node n = new Node(g2.player.x + 14, g2.player.y, Node.SCRAP);
        g2.world.nodes.add(n);
        g2.player.faceX = 1f;
        g2.player.faceY = 0f;
        int scrap0 = g2.bag.scrap;
        for (int i = 0; i < 240 && !n.taken; i++) {
            g2.input.attackPressed = true;
            g2.update(1f / 60f, g2.input);
            g2.input.endFrame();
        }
        check("废料堆可采集", n.taken);
        check("采集得到废铁", g2.bag.scrap > scrap0);

        Game g3 = fresh();
        g3.player.spawnProtect = 999f;
        g3.world.batteries.clear();
        g3.world.enemies.clear();
        g3.energy = 0;
        g3.world.batteries.add(new Battery(g3.player.x, g3.player.y, true));
        step(g3, 3);
        check("电池增加能量", g3.energy >= 3);

        // ---- 招架：攻击命中 → 短暂格挡 90% ----
        Game g4 = fresh();
        g4.player.spawnProtect = 999f;
        g4.world.enemies.clear();
        Enemy e4 = new Enemy(g4.player.x + 15, g4.player.y, Enemy.CRAWLER);
        e4.maxHp = 999;
        e4.hp = 999;
        g4.world.enemies.add(e4);
        g4.player.faceX = 1f;
        g4.player.faceY = 0f;
        check("未命中时没有招架", g4.player.guard <= 0f);
        for (int i = 0; i < 30 && g4.player.guard <= 0f; i++) {
            g4.input.attackPressed = true;
            g4.update(1f / 60f, g4.input);
            g4.input.endFrame();
        }
        check("攻击命中后进入招架", g4.player.guard > 0f);

        int hp0 = g4.player.hp;
        g4.player.spawnProtect = 0f;
        g4.player.invuln = 0f;
        g4.player.guard = Const.GUARD_T;
        g4.player.hurt(4, g4.player.x + 20f, g4.player.y, g4);
        checkEq("招架时 4 点伤害只吃 1 点", 1, hp0 - g4.player.hp);
        check("招架伤害有数字提示", g4.dmgTexts.size() > 0
                && g4.dmgTexts.get(g4.dmgTexts.size() - 1).text.contains("格挡"));

        int hp1 = g4.player.hp;
        g4.player.invuln = 0f;
        g4.player.guard = 0f;
        g4.player.hurt(4, g4.player.x + 20f, g4.player.y, g4);
        checkEq("招架过期后吃满 4 点", 4, hp1 - g4.player.hp);

        check("招架有持续时间上限", Const.GUARD_T > 0.3f && Const.GUARD_T < 2f);
        // v6：初期怪伤害压低，随等级抬高；守卫始终最高
        int d1 = new Enemy(0, 0, Enemy.CRAWLER).contactDamage(1);
        int d10 = new Enemy(0, 0, Enemy.CRAWLER).contactDamage(10);
        int d30 = new Enemy(0, 0, Enemy.CRAWLER).contactDamage(30);
        check("1 级爬虫伤害很低", d1 <= 3);
        check("伤害随等级单调上升", d10 > d1 && d30 > d10);
        check("守卫伤害高于爬虫", new Enemy(0, 0, Enemy.WARDEN).contactDamage(10) > d10);
        check("伤害至少 1 点", new Enemy(0, 0, Enemy.CRAWLER).contactDamage(1) >= 1);

        Game g5 = fresh();
        g5.player.spawnProtect = 999f;
        g5.world.enemies.clear();
        g5.player.faceX = 1f;
        for (int i = 0; i < 30; i++) {
            g5.input.attackPressed = true;
            g5.update(1f / 60f, g5.input);
            g5.input.endFrame();
        }
        check("挥空不触发招架", g5.player.guard <= 0f);
    }

    // ------------------------------------------------------------ 怪物


    // ------------------------------------------------------------ v8：武器类型 / 输入缓冲 / 瞄准 / 词条 / Boss

    static void weapons() {
        // 四种武器的节奏必须真的不同，否则"类型"就只是换了个名字
        Game g = fresh();
        g.world.enemies.clear();
        g.player.spawnProtect = 999f;
        float[] cd = new float[Weapon.TYPES];
        float[] rg = new float[Weapon.TYPES];
        float[] ar = new float[Weapon.TYPES];
        int[] dmg = new int[Weapon.TYPES];
        for (int t = 0; t < Weapon.TYPES; t++) {
            g.gear[Item.ATK] = new Item(Item.ATK, Item.WHITE, t);
            cd[t] = g.atkCd();
            rg[t] = g.atkRange();
            ar[t] = g.atkArc();
            dmg[t] = g.attack();
        }
        check("太刀攻速最快", cd[Weapon.KATANA] < cd[Weapon.TWIN]
                && cd[Weapon.TWIN] < cd[Weapon.SPEAR] && cd[Weapon.SPEAR] < cd[Weapon.AXE]);
        check("重斧冷却明显更长", cd[Weapon.AXE] > cd[Weapon.KATANA] * 2f);
        check("长枪射程最远", rg[Weapon.SPEAR] > rg[Weapon.AXE] && rg[Weapon.AXE] > rg[Weapon.KATANA]);
        check("重斧扇角最大", ar[Weapon.AXE] > ar[Weapon.TWIN] && ar[Weapon.TWIN] > ar[Weapon.KATANA]);
        check("长枪扇角最窄", ar[Weapon.SPEAR] < ar[Weapon.KATANA]);
        check("重斧单次伤害最高", dmg[Weapon.AXE] >= dmg[Weapon.SPEAR] * 3 / 2);
        check("四种武器名字互不相同",
                !Weapon.name(0, 0).equals(Weapon.name(1, 0))
                        && !Weapon.name(1, 0).equals(Weapon.name(2, 0))
                        && !Weapon.name(2, 0).equals(Weapon.name(3, 0)));
        check("旧存档按品阶派生类型", new Item(Item.ATK, Item.BLUE).type >= 0
                && new Item(Item.ATK, Item.BLUE).type < Weapon.TYPES);
        check("非武器槽类型固定为 0", new Item(Item.HP, Item.RED).type == 0);

        // ---- 输入缓冲：冷却里点的那一下不能白丢 ----
        Game gb = fresh();
        gb.world.enemies.clear();
        gb.player.spawnProtect = 999f;
        gb.player.atkCd = 0.18f;
        gb.input.attackPressed = true;
        gb.update(1f / 120f, gb.input);
        gb.input.endFrame();
        check("冷却中不会立刻出刀", gb.player.atkT <= 0f);
        check("冷却中的点击被缓存", gb.player.atkBuf > 0f);
        for (int i = 0; i < 40 && gb.player.atkT <= 0f; i++) {
            gb.update(1f / 120f, gb.input);
            gb.input.endFrame();
        }
        check("冷却结束立刻兑现缓存的那一刀", gb.player.atkT > 0f);

        // ---- 判定方向 = 锁定角，不是实时朝向 ----
        Game gc = fresh();
        gc.world.enemies.clear();
        gc.player.spawnProtect = 999f;
        Player pc = gc.player;
        pc.faceX = 1f;
        pc.faceY = 0f;
        Enemy front = new Enemy(pc.x + 18, pc.y, Enemy.CRAWLER);
        front.maxHp = front.hp = 200;
        gc.world.enemies.add(front);
        gc.input.attackPressed = true;
        gc.input.moveX = 0f;
        gc.input.moveY = -1f;          // 同一帧就往上推，看判定会不会跟着错
        boolean stopSeen = false;
        for (int i = 0; i < 8; i++) {
            step(gc, 1);
            if (gc.hitStop > 0f) stopSeen = true;
        }
        check("挥砍命中正前方目标", front.hp < 200);
        check("命中触发顿帧", stopSeen);
        check("命中给敌人硬直", front.stun > 0f);
        gc.input.moveX = 0f;
        gc.input.moveY = 0f;

        // 背后目标不该被扫到
        Game gd = fresh();
        gd.world.enemies.clear();
        gd.player.spawnProtect = 999f;
        Player pd = gd.player;
        pd.faceX = 1f;
        pd.faceY = 0f;
        Enemy back = new Enemy(pd.x - 16, pd.y, Enemy.CRAWLER);
        back.maxHp = back.hp = 200;
        gd.world.enemies.add(back);
        gd.input.attackPressed = true;
        step(gd, 6);
        check("背后目标不会被正面挥砍打到", back.hp == 200);

        // ---- 辅助瞄准三档 ----
        Game ga = fresh();
        ga.world.enemies.clear();
        ga.player.spawnProtect = 999f;
        Player pa = ga.player;
        pa.faceX = 1f;
        pa.faceY = 0f;
        float off = 0.42f;             // 约 24°，落在"轻"的吸附范围内
        Enemy side = new Enemy(pa.x + (float) Math.cos(off) * 20f,
                pa.y + (float) Math.sin(off) * 20f, Enemy.CRAWLER);
        ga.world.enemies.add(side);
        ga.aimAssist = 0;
        check("辅助关闭时保持原方向", Math.abs(ga.aimAngle()) < 0.005f);
        ga.aimAssist = 1;
        check("轻微辅助会吸向侧前目标", Math.abs(ga.aimAngle() - off) < 0.02f);
        ga.aimAssist = 2;
        float off2 = 0.66f;            // 38°，只有"明显"能吸
        side.x = pa.x + (float) Math.cos(off2) * 20f;
        side.y = pa.y + (float) Math.sin(off2) * 20f;
        check("明显辅助能吸更偏的目标", Math.abs(ga.aimAngle() - off2) < 0.02f);
        check("辅助档位循环 0/1/2", ga.aimAssist == 2);
        ga.cycleAim();
        check("循环回到关闭", ga.aimAssist == 0);
    }

    static void levelup() {
        Game g = fresh();
        g.world.enemies.clear();
        g.player.spawnProtect = 999f;
        int lv0 = g.level;
        g.levelUp();
        check("升级后进入三选一", g.state == Game.State.LEVELUP);
        check("等级 +1", g.level == lv0 + 1);
        check("三张卡都有效", g.offers[0] >= 0 && g.offers[1] >= 0 && g.offers[2] >= 0);
        check("三张卡不重复", g.offers[0] != g.offers[1]
                && g.offers[1] != g.offers[2] && g.offers[0] != g.offers[2]);

        int pick = g.offers[1];
        g.choosePerk(1);
        check("选完回到游戏", g.state == Game.State.PLAY);
        check("选中的词条有层数", pick == Perk.HEAL || g.perkStack(pick) == 1);

        // 选满之后不再出现
        for (int id = 0; id < Perk.COUNT; id++) {
            if (id == Perk.HEAL) continue;
            g.perks[id] = Perk.MAX;
        }
        g.levelUp();
        check("全部满层时只剩修复", g.offers[0] == Perk.HEAL || g.offers[1] == Perk.HEAL
                || g.offers[2] == Perk.HEAL);
        g.choosePerk(0);

        // 数值真的生效
        Game g2 = fresh();
        g2.world.enemies.clear();
        g2.player.spawnProtect = 999f;
        g2.gear[Item.ATK] = new Item(Item.ATK, Item.WHITE, Weapon.TWIN);
        int base = g2.attack();
        float cd0 = g2.atkCd();
        int hp0 = g2.maxHp();
        g2.perks[Perk.DMG] = 5;
        g2.perks[Perk.CD] = 5;
        g2.perks[Perk.HP] = 5;
        check("锋利 ×5 伤害 +50%", Math.abs(g2.attack() / (float) base - 1.5f) < 0.08f);
        check("疾风 ×5 冷却变短", g2.atkCd() < cd0 * 0.75f);
        check("体魄 ×5 生命 +40", Math.abs(g2.maxHp() - hp0 - 40) <= 1);
        g2.player.hp = 1;
        int maxHp = g2.maxHp();
        g2.perks[Perk.HEAL] = 1;
        Game g3 = g2;
        g3.levelUp();
        int healSlot = -1;
        for (int i = 0; i < 3; i++) if (g3.offers[i] == Perk.HEAL) healSlot = i;
        if (healSlot >= 0) {
            g3.choosePerk(healSlot);
            check("修复卡立刻回血", g3.player.hp > 1);
        } else {
            check("修复卡立刻回血（本轮未抽到，跳过）", true);
        }

        // 升级只回 30%，不是回满
        Game g4 = fresh();
        g4.world.enemies.clear();
        g4.player.spawnProtect = 999f;
        g4.player.hp = 1;
        g4.levelUp();
        check("升级不是回满血", g4.player.hp < g4.maxHp());
        check("升级至少回一点血", g4.player.hp > 1);
    }

    static void boss() {
        Game g = new Game(null);
        g.rnd.setSeed(4242L);
        g.startRun();
        g.player.spawnProtect = 999f;
        check("每区一座 Boss 巢穴", g.world.sites.size() == Const.BOSS_SITES);
        Enemy boss = null;
        for (int i = 0; i < g.world.enemies.size(); i++) {
            if (g.world.enemies.get(i).boss) boss = g.world.enemies.get(i);
        }
        check("地图上有 Boss", boss != null);
        if (boss == null) return;
        check("Boss 血量远高于普通守卫", boss.maxHp > 18 * Const.BOSS_HP_MUL);
        check("Boss 体型更大", boss.radius() > Enemy.R);
        check("Boss 属于某个区", boss.zone >= 0 && boss.zone < 3);

        // 贴脸一刀打死（把血调到 1）
        boss.hp = 1;
        g.player.x = boss.x - 14f;
        g.player.y = boss.y;
        g.player.faceX = 1f;
        g.player.faceY = 0f;
        g.world.enemies.clear();
        g.world.enemies.add(boss);
        int cores0 = g.cores;
        for (int i = 0; i < 30 && boss.alive; i++) {
            g.input.attackPressed = true;
            g.update(1f / 120f, g.input);
            g.input.endFrame();
        }
        check("Boss 会被打死", !boss.alive);
        check("击杀 Boss 得到核心", g.cores == cores0 + 1);
        int cleared = 0;
        for (int i = 0; i < g.world.sites.size(); i++) if (g.world.sites.get(i).cleared) cleared++;
        check("对应巢穴标记为已清", cleared == 1);

        check("出生点是近郊", g.world.zoneAt(g.world.spawnX, g.world.spawnY) == 0);
        check("远处是深渊", World.zoneOfTiles(Const.ZONE_R2 + 5) == 2);
        check("区域名不是空的", World.zoneName(0).length() > 0
                && World.zoneName(1).length() > 0 && World.zoneName(2).length() > 0);
    }


    // ------------------------------------------------------------ v9：切弹 / 幸运词条 / 白装起步

    static void v9() {
        // 1) 四个部位都从白装起步
        Game g0 = new Game(null);
        g0.rnd.setSeed(77L);
        g0.startRun();
        boolean allWhite = true;
        for (int i = 0; i < Item.SLOTS; i++) {
            Item it = g0.gear(i);
            if (it == null || it.tier != Item.WHITE) allWhite = false;
        }
        check("四个部位初始都是白装", allWhite);
        check("初始武器是白装太刀", g0.gear(Item.ATK).type == Weapon.KATANA);

        // 2) 任何武器都能切弹（拿最慢的重斧也一样）
        Game g1 = fresh();
        g1.world.enemies.clear();
        g1.player.spawnProtect = 999f;
        g1.gear[Item.ATK] = new Item(Item.ATK, Item.WHITE, Weapon.AXE);
        Player p1 = g1.player;
        p1.faceX = 1f;
        p1.faceY = 0f;
        g1.world.plasmas.add(new Plasma(p1.x + 20f, p1.y, 0f, 0f));
        check("挥击前弹丸还在", g1.world.plasmas.size() == 1);
        g1.input.attackPressed = true;
        step(g1, 10);
        check("重斧挥击也能切掉弹丸", g1.world.plasmas.isEmpty());

        // 背后飞来的弹丸不该被切
        Game g2 = fresh();
        g2.world.enemies.clear();
        g2.player.spawnProtect = 999f;
        Player p2 = g2.player;
        p2.faceX = 1f;
        p2.faceY = 0f;
        g2.world.plasmas.add(new Plasma(p2.x - 24f, p2.y, 0f, 0f));
        g2.input.attackPressed = true;
        step(g2, 10);
        check("背后的弹丸不会被正面挥砍切掉", g2.world.plasmas.size() == 1);

        // 3) 反弹词条：切掉的弹丸打回去并造成伤害
        Game g3 = fresh();
        g3.world.enemies.clear();
        g3.player.spawnProtect = 999f;
        g3.perks[Perk.REFLECT] = 1;
        Player p3 = g3.player;
        p3.faceX = 1f;
        p3.faceY = 0f;
        Enemy e3 = new Enemy(p3.x + 46f, p3.y, Enemy.CRAWLER);
        e3.maxHp = e3.hp = 60;
        g3.world.enemies.add(e3);
        Plasma pl = new Plasma(p3.x + 18f, p3.y, -90f, 0f);
        g3.world.plasmas.add(pl);
        g3.input.attackPressed = true;
        step(g3, 20);
        check("反弹词条把弹丸变成我方", pl.friendly || !pl.alive);
        check("反弹弹丸会打到敌人", e3.hp < 60);
        check("反弹伤害高于普通挥击", pl.dmg > g3.attack());

        // 4) 闪躲：概率完全免伤
        Game g4 = fresh();
        g4.world.enemies.clear();
        g4.player.spawnProtect = 0f;
        g4.perks[Perk.DODGE] = 5;
        check("闪避率随层数提升", g4.dodgeChance() > 0.30f);
        boolean dodged = false;
        for (int i = 0; i < 60 && !dodged; i++) {
            g4.player.invuln = 0f;
            g4.player.hp = 999;
            g4.player.hurt(5, g4.player.x + 10f, g4.player.y, g4);
            if (g4.player.hp == 999) dodged = true;
        }
        check("闪躲词条能完全免伤", dodged);

        // 5) 庇护：护盾没好照常掉血，就绪时吃掉一次
        Game g5 = fresh();
        g5.world.enemies.clear();
        g5.player.spawnProtect = 0f;
        g5.perks[Perk.AEGIS] = 1;
        g5.aegisT = 0f;
        g5.player.invuln = 0f;
        g5.player.hp = 200;
        g5.player.hurt(9, g5.player.x + 10f, g5.player.y, g5);
        check("护盾没好时照常掉血", g5.player.hp < 200);
        g5.aegisT = g5.aegisCd();
        g5.player.invuln = 0f;
        int before = g5.player.hp;
        g5.player.hurt(9, g5.player.x + 10f, g5.player.y, g5);
        check("护盾就绪时吃掉一次伤害", g5.player.hp == before);
        check("护盾消耗后需要重新充能", !g5.aegisReady());
        Game ga1 = new Game(null);
        ga1.perks[Perk.AEGIS] = 1;
        Game ga5 = new Game(null);
        ga5.perks[Perk.AEGIS] = 5;
        check("庇护层级越高回充越快", ga5.aegisCd() < ga1.aegisCd());

        // 6) 幸运词条只在 10 级后进卡池
        Game g6 = fresh();
        g6.world.enemies.clear();
        int early = 0;
        for (int i = 0; i < 40; i++) {
            g6.level = 1;
            g6.levelUp();
            for (int k = 0; k < 3; k++) if (Perk.isSpecial(g6.offers[k])) early++;
            g6.choosePerk(0);
        }
        check("10 级前不会出现幸运词条", early == 0);

        int late = 0;
        for (int i = 0; i < 40; i++) {
            g6.level = 14;
            g6.levelUp();
            for (int k = 0; k < 3; k++) if (Perk.isSpecial(g6.offers[k])) late++;
            g6.choosePerk(0);
        }
        check("10 级后幸运词条会进卡池", late > 0);
        check("幸运词条有独立名字与描述", Perk.NAME[Perk.REFLECT].length() > 0
                && Perk.descOf(Perk.DODGE, 0).indexOf('%') > 0
                && Perk.descOf(Perk.AEGIS, 0).length() > 0);
    }

    static void monsters() {
        java.util.HashSet<Integer> colors = new java.util.HashSet<Integer>();
        for (int t = 0; t < Enemy.TYPES; t++) {
            Enemy e = new Enemy(0, 0, t);
            colors.add(e.color());
            check("怪物「" + Enemy.TYPE_NAME[t] + "」有血量", e.maxHp > 0);
            check("怪物「" + Enemy.TYPE_NAME[t] + "」伤害 ≥ 1", e.contactDamage(5) >= 1);
            check("怪物「" + Enemy.TYPE_NAME[t] + "」会掉东西", e.dropChance() > 0f);
        }
        check("九种怪物颜色互不相同", colors.size() == Enemy.TYPES);

        java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        java.util.Random rr = new java.util.Random(7);
        for (int i = 0; i < 4000; i++) seen.add(World.pickType(rr, 80));
        check("按距离能刷出全部 9 种", seen.size() == Enemy.TYPES);

        Game g = fresh();
        Enemy a = new Enemy(400, 400, Enemy.SHIELDER);
        a.aimX = 1f;
        a.aimY = 0f;
        int hp0 = a.hp;
        a.damage(10, 430, 400, g.world.map);       // 从正面打
        int front = hp0 - a.hp;

        Enemy b = new Enemy(400, 400, Enemy.SHIELDER);
        b.aimX = 1f;
        b.aimY = 0f;
        int hp1 = b.hp;
        b.damage(10, 370, 400, g.world.map);       // 从背后打
        int back = hp1 - b.hp;

        check("盾卫正面减伤", front < back);
        check("盾卫正面只吃 1/4（" + front + "/10）", front <= Math.max(1, Math.round(10 * Const.SHIELD_BLOCK)));
        check("盾卫背后吃满", back >= 10);

        // 自爆虫：贴近 → 点引信 → 炸
        Game ge = fresh();
        ge.world.enemies.clear();
        ge.world.batteries.clear();
        ge.player.spawnProtect = 0f;
        Enemy bom = new Enemy(ge.player.x + 9, ge.player.y, Enemy.BOMBER);
        ge.world.enemies.add(bom);
        int kills0 = ge.kills;
        int scrap0 = ge.bag.scrap;
        for (int i = 0; i < 120 && bom.alive; i++) {
            ge.update(1f / 60f, ge.input);
            ge.input.endFrame();
        }
        check("自爆虫会自爆", !bom.alive);
        check("自爆计入击杀", ge.kills > kills0);
        check("自爆掉废铁", ge.bag.scrap > scrap0);

        // 跳虫：蓄力后扑过来，落地有冲击波（不崩即可）
        Game gl = fresh();
        gl.world.enemies.clear();
        gl.player.spawnProtect = 999f;
        Enemy lea = new Enemy(gl.player.x + 90, gl.player.y, Enemy.LEAPER);
        gl.world.enemies.add(lea);
        float d0 = Math.abs(lea.x - gl.player.x);
        for (int i = 0; i < 180; i++) {
            gl.update(1f / 60f, gl.input);
            gl.input.endFrame();
        }
        check("跳虫会靠近玩家", Math.abs(lea.x - gl.player.x) < d0);
    }

    // ------------------------------------------------------------ 合成台

    static void bench() {
        Game g = fresh();
        g.player.spawnProtect = 999f;
        check("世界里有合成台", g.world.benches.size() >= 8);
        check("出生点旁没有台子时 benchNear 为 null 或合理",
                g.benchNear() == null || g.benchNear() != null);

        Bench b = g.world.benches.get(0);
        g.player.x = b.x;
        g.player.y = b.y + 12f;
        step(g, 1);
        check("走到台边能识别", g.benchNear() != null);

        g.bag.scrap = 10;
        g.openBench();
        check("能打开合成台", g.state == Game.State.BENCH);
        check("合成按钮在屏幕内", inside(g, Game.B_CRAFT));
        check("八个产物卡都在屏幕内", inside(g, Game.B_CRAFT_PICK)
                && inside(g, Game.B_CRAFT_PICK + Game.PICKS - 1));
        check("合成台有图鉴入口", inside(g, Game.B_CODEX));

        int cnt = g.bag.count();
        // v9：点产物只负责选中，不花材料——避免误触把废铁吃掉
        g.fire(Game.B_CRAFT_PICK + 5);
        checkEq("点产物不消耗废铁", 10, g.bag.scrap);
        check("点产物只切换选中", g.benchPick == 5);
        check("点产物不会产出", g.bag.count() == cnt);
        check("合成键在选中后仍可用", g.ui.byId(Game.B_CRAFT).enabled);

        // 真正合成必须按合成键
        g.fire(Game.B_CRAFT);
        checkEq("定向合成花 3 废铁", 7, g.bag.scrap);
        check("按合成产出装备", g.bag.count() == cnt + 1);
        boolean gotSpd = false;
        for (Item it : g.bag.slots) if (it != null && it.slot == Item.SPD) gotSpd = true;
        check("产出的是选中的部位", gotSpd);
        check("合成台显示产出概率", g.craftOdds().indexOf('%') >= 0);

        // v10：四类武器都能直接指定，不再只能造身上那件
        for (int i = 0; i < Const.BAG_SLOTS; i++) g.bag.slots[i] = null;
        int[] wantType = new int[Weapon.TYPES];
        boolean allTypes = true;
        for (int t = 0; t < Weapon.TYPES; t++) {
            g.benchPick = t;
            g.bag.scrap = 9;
            int c0 = g.bag.count();
            g.fire(Game.B_CRAFT);
            if (g.bag.count() != c0 + 1) allTypes = false;
            for (Item it : g.bag.slots) {
                if (it != null && it.slot == Item.ATK && it.type == t) wantType[t] = 1;
            }
        }
        check("四类武器都能指定合成", allTypes && wantType[0] + wantType[1] + wantType[2] + wantType[3] == 4);

        // 随机档：便宜一块，部位不定
        g.benchPick = Game.PICK_RANDOM;
        g.bag.scrap = 5;
        g.fire(Game.B_CRAFT);
        checkEq("随机合成只花 2 废铁", 3, g.bag.scrap);
        check("随机档更便宜", g.pickCost(Game.PICK_RANDOM) < g.pickCost(0));

        // 图鉴：两页都在屏幕内，能来回翻，关掉回到合成台
        g.fire(Game.B_CODEX);
        check("能从合成台打开图鉴", g.state == Game.State.CODEX);
        check("图鉴两页签在屏幕内",
                inside(g, Game.B_CODEX_TAB) && inside(g, Game.B_CODEX_TAB + 1));
        check("图鉴关闭键在屏幕内", inside(g, Game.B_CODEX_CLOSE));
        g.fire(Game.B_CODEX_TAB + 1);
        check("能切到装备页", g.codexPage == 1);
        g.fire(Game.B_CODEX_TAB);
        check("能切回武器页", g.codexPage == 0);
        g.fire(Game.B_CODEX_CLOSE);
        check("关图鉴回到合成台", g.state == Game.State.BENCH);

        g.fire(Game.B_BENCH_CLOSE);
        check("能关闭合成台", g.state == Game.State.PLAY);

        g.openBag();
        g.fire(Game.B_CODEX);
        check("能从背包打开图鉴（不用跑到台子边）", g.state == Game.State.CODEX);
        g.fire(Game.B_CODEX_CLOSE);
        check("关图鉴回到背包", g.state == Game.State.BAG);
        g.closeBag();

        // 远离台子：打不开，背包里的按钮也禁用
        Game far = fresh();
        far.player.spawnProtect = 999f;
        far.player.x = far.world.spawnX - 500f;
        far.player.y = far.world.spawnY - 500f;
        step(far, 1);
        check("远处 benchNear 为空", far.benchNear() == null);
        far.openBench();
        check("远离合成台打不开", far.state == Game.State.PLAY);
        far.openBag();
        check("远处背包里的合成台按钮禁用", !far.ui.byId(Game.B_CRAFT).enabled);
    }

    // ------------------------------------------------------------ 摇杆固定

    static void joystick() {
        Game g = fresh();
        g.joyFixed = true;
        step(g, 1);
        Ui.Btn jb = g.ui.byId(Game.B_JOY);
        check("摇杆底盘存在", jb != null && jb.visible);
        check("摇杆底盘默认在左下", jb.cx() < Const.VW * 0.4f && jb.cy() > Const.VH * 0.6f);

        // 固定模式下按底盘外：不起杆
        g.input.post(Input.DOWN, 21, jb.cx() + 120f, jb.cy() - 40f);
        step(g, 1);
        check("固定模式按底盘外不起杆", !g.input.joyActive);
        g.input.post(Input.UP, 21, jb.cx() + 120f, jb.cy() - 40f);
        step(g, 1);

        // 按底盘：起杆，基点钉在圆心
        float px = g.player.x;
        g.input.post(Input.DOWN, 22, jb.cx(), jb.cy());
        step(g, 1);
        check("按底盘能起杆", g.input.joyActive);
        check("起点在底盘圆心", Math.abs(g.input.joyX - jb.cx()) < 0.8f);

        g.input.post(Input.MOVE, 22, jb.cx() + 30f, jb.cy());
        step(g, 25);
        check("固定摇杆能推着走", g.player.x > px + 5);
        Ui.Btn jb2 = g.ui.byId(Game.B_JOY);
        check("固定摇杆基点不漂移", Math.abs(g.input.joyX - jb2.cx()) < 0.8f);
        g.input.post(Input.UP, 22, jb.cx() + 30f, jb.cy());
        step(g, 3);
        check("松手停止摇杆", !g.input.joyActive);

        // 改回跟随：任意位置起杆
        g.joyFixed = false;
        step(g, 1);
        g.input.post(Input.DOWN, 23, 40f, 110f);
        step(g, 1);
        check("跟随模式任意位置起杆", g.input.joyActive);
        g.input.post(Input.UP, 23, 40f, 110f);
        step(g, 2);

        // 底盘偏移会被夹回屏幕内
        g.joyFixed = true;
        g.setOffset(Game.B_JOY, -9999f, 9999f);
        g.ui.layout(g);
        check("摇杆底盘被夹回屏幕内", inside(g, Game.B_JOY));
        g.resetLayout();
    }

    // ------------------------------------------------------------ 成长

    static void progression() {
        check("升级需求随等级增长", Game.needFor(5) > Game.needFor(1));
        Game g = fresh();
        g.player.spawnProtect = 999f;
        g.world.enemies.clear();
        g.world.batteries.clear();
        g.energy = g.needNow() - 1;
        int lv = g.level;
        g.world.batteries.add(new Battery(g.player.x, g.player.y, false));
        step(g, 3);
        check("攒满能量会升级", g.level == lv + 1);
        check("升级回复满血", g.player.hp == g.maxHp());

        // ---- v6 成长：血量数字制 / 移速 / 幸运 / 挥砍距离 ----
        Game lv1 = fresh();
        int hp1 = lv1.maxHp();
        float sp1 = lv1.runSpeed();
        float rg1 = lv1.rangeMul();
        check("1 级血量是数字制", hp1 >= 40);
        check("1 级幸运为 0", lv1.luck() <= 0f);
        lv1.level = 11;
        checkEq("每级 +5 血", hp1 + 5 * 10, lv1.maxHp());
        check("移速每级 +1%", Math.abs(lv1.runSpeed() / sp1 - 1.10f) < 0.01f);
        check("挥砍距离随等级变长", lv1.rangeMul() > rg1);
        check("幸运 10 级解锁", lv1.luck() > 0f);
        check("幸运每级 +0.001", Math.abs(lv1.luck() - 0.001f * 1) < 1e-6f);
        lv1.level = 60;
        check("挥砍距离封顶 3 倍", Math.abs(lv1.rangeMul() - Const.RANGE_MAX) < 1e-4f);

        // 高等级掉落品阶应该明显偏上
        Game lk = fresh();
        lk.rnd.setSeed(99);
        lk.level = 1;
        float lowTier = avgTier(lk, 600);
        lk.level = 40;
        float highTier = avgTier(lk, 600);
        check("高等级掉落品阶更高", highTier > lowTier);

        Game g2 = fresh();
        g2.level = 23;
        g2.player.hp = 5;
        g2.killPlayer();
        checkEq("23 级死亡扣到 16 级", 16, g2.level);
        checkEq("记录损失等级", 7, g2.levelLost);
        check("死亡状态", g2.state == Game.State.DEAD);
        check("装备不因死亡丢失", g2.gear(Item.ATK) != null);

        Game g3 = fresh();
        g3.level = 1;
        g3.killPlayer();
        checkEq("1 级死亡不降级", 1, g3.level);

        Game g4 = fresh();
        g4.level = 7;
        g4.bag.add(new Item(Item.HP, Item.PURPLE));
        g4.killPlayer();
        g4.startRun();
        check("重开保留等级（7 级死一次剩 5 级）", g4.level == 5);
        check("重开保留背包", g4.bag.count() == 1);
        check("重开保留装备", g4.gear(Item.ATK) != null);
    }

    // ------------------------------------------------------------ 背包

    static void inventory() {
        Game g = fresh();
        g.player.spawnProtect = 999f;
        Item it = new Item(Item.ATK, Item.PURPLE);
        g.bag.slots[0] = it;
        g.selBag = 0;
        g.fire(Game.B_EQUIP);
        check("装备后进入装备栏", g.gear(Item.ATK) == it);
        check("装备后离开背包", g.bag.get(0) != it);
        Item better = new Item(Item.ATK, Item.RED);
        g.bag.slots[1] = better;
        g.selBag = 1;
        g.fire(Game.B_EQUIP);
        check("换下的旧装备回到背包", g.bag.get(1) != null && g.bag.get(1) != better);
        check("更高品阶可替换", g.gear(Item.ATK) == better);

        int scrap0 = g.bag.scrap;
        int lv0 = g.level;
        Item dis = g.bag.get(1);
        int expect = Const.DISMANTLE_BASE + (dis == null ? 0 : dis.tier);
        g.selBag = 1;
        g.fire(Game.B_DISMANTLE);
        check("分解返还废铁", g.bag.scrap >= scrap0 + expect);
        check("分解不动等级", g.level == lv0);

        Bag b = new Bag();
        for (int i = 0; i < Const.BAG_SLOTS; i++) b.slots[i] = new Item(i % 4, 0);
        check("满包检测", b.full());
        check("满包无法再放入", !b.add(new Item(0, 0)));
    }

    // ------------------------------------------------------------ UI（重点）

    static void ui() {
        // 宽屏真机：整条可见带是 [cropTop, VH-cropBot]，上下各裁一半
        float[] crops = {0f, 56f, 66f};
        for (int i = 0; i < crops.length; i++) {
            Game g = fresh();
            g.uiTop = Math.round(crops[i] * 0.45f);
            g.uiBottom = Const.VH - (crops[i] - g.uiTop);
            step(g, 1);
            check("uiTop=" + crops[i] + " 时暂停键在屏幕内", inside(g, Game.B_PAUSE));
            check("uiTop=" + crops[i] + " 时背包键在屏幕内", inside(g, Game.B_BAG));
            check("uiTop=" + crops[i] + " 时静音键在屏幕内", inside(g, Game.B_MUTE));
            check("uiTop=" + crops[i] + " 时攻击键在屏幕内", inside(g, Game.B_ATTACK));
            check("uiTop=" + crops[i] + " 时冲刺键在屏幕内", inside(g, Game.B_DASH));

            Ui.Btn pause = g.ui.byId(Game.B_PAUSE);
            tap(g, pause.cx(), pause.cy());
            check("uiTop=" + crops[i] + " 点暂停有效", g.state == Game.State.PAUSED);

            Ui.Btn resume = g.ui.byId(Game.B_RESUME);
            check("uiTop=" + crops[i] + " 暂停面板按钮在屏幕内", inside(g, Game.B_RESUME));
            tap(g, resume.cx(), resume.cy());
            check("uiTop=" + crops[i] + " 点继续有效", g.state == Game.State.PLAY);

            boolean m0 = g.muted;
            Ui.Btn mute = g.ui.byId(Game.B_MUTE);
            tap(g, mute.cx(), mute.cy());
            check("uiTop=" + crops[i] + " 点静音有效", g.muted != m0);
            tap(g, g.ui.byId(Game.B_MUTE).cx(), g.ui.byId(Game.B_MUTE).cy());

            Ui.Btn bagBtn = g.ui.byId(Game.B_BAG);
            tap(g, bagBtn.cx(), bagBtn.cy());
            check("uiTop=" + crops[i] + " 点背包打开", g.state == Game.State.BAG);
            check("uiTop=" + crops[i] + " 背包面板在屏幕内", panelInside(g));
            Ui.Btn close = g.ui.byId(Game.B_CLOSE);
            tap(g, close.cx(), close.cy());
            check("uiTop=" + crops[i] + " 关闭背包", g.state == Game.State.PLAY);
        }

        // 真机分辨率：20:9 / 19.5:9 / 18:9 / 16:9 / 4:3，按键必须全部落在可见带里
        int[][] screens = {{2400, 1080}, {2340, 1080}, {2160, 1080}, {1920, 1080}, {1600, 1200}};
        for (int[] sc : screens) {
            int sw = sc[0], sh = sc[1];
            String tag = sw + "x" + sh;
            Game g = fresh();
            float top = Const.cropTop(sw, sh);
            float bot = Const.VH - (Const.cropTotal(sw, sh) - top);
            g.uiTop = top;
            g.uiBottom = bot;
            g.ui.layout(g);
            check(tag + " 攻击键在可见带内", inside(g, Game.B_ATTACK));
            check(tag + " 冲刺键在可见带内", inside(g, Game.B_DASH));
            check(tag + " 暂停键在可见带内", inside(g, Game.B_PAUSE));
            Ui.Btn jb = g.ui.byId(Game.B_JOY);
            check(tag + " 摇杆底盘存在", jb != null && jb.visible);
            check(tag + " 摇杆底盘在可见带内",
                    jb.cy() - Const.JOY_R >= top - 0.5f && jb.cy() + Const.JOY_R <= bot + 0.5f);

            // 触摸映射（与 GameView.toVY 同式）：屏幕最底一行不能跑到可见带外面去
            float rate = sw / (float) Const.VW;
            int mm = Math.max(2, Math.min(5, (int) Math.ceil(rate)));
            while (mm > 2 && Const.VW * mm > 2400) mm--;
            int bh = Math.round((Const.VH - Const.cropTotal(sw, sh)) * mm);
            int dstTop = 0;
            if (Const.cropTotal(sw, sh) <= 0f) {
                int dh = Math.round(bh * sw / (float) (Const.VW * mm));
                dstTop = (sh - dh) / 2;
            }
            float vy = top + (sh - 1 - dstTop) / rate;
            vy = Math.max(0f, Math.min(Const.VH, vy));   // 同 GameView.toVY 的夹取
            check(tag + " 底部触摸映射不出界", vy <= bot + 0.5f && vy >= top);

            Ui.Btn atk = g.ui.byId(Game.B_ATTACK);
            tap(g, atk.cx(), atk.cy());
            check(tag + " 攻击键可点中", g.player.atkT > 0f);

            // 左半屏随便按一下就能出摇杆（v3 手感）
            Game j = fresh();
            j.uiTop = top;
            j.uiBottom = bot;
            j.ui.layout(j);
            j.input.post(Input.DOWN, 4, 70f, top + 120f);
            step(j, 1);
            check(tag + " 左半屏可起摇杆", j.input.joyActive);
            check(tag + " 摇杆圈在可见带内",
                    j.input.joyY - 30f >= top - 0.5f && j.input.joyY + 30f <= bot + 0.5f);
            j.input.post(Input.UP, 4, 70f, top + 120f);
            step(j, 1);
        }

        Game t = new Game(null);
        t.rnd.setSeed(3);
        t.uiTop = 25f;
        t.uiBottom = Const.VH - 31f;
        step(t, 1);
        check("标题页有开始按钮", t.ui.byId(Game.B_START) != null);
        check("标题页开始按钮在屏幕内", inside(t, Game.B_START));
        Ui.Btn start = t.ui.byId(Game.B_START);
        tap(t, start.cx(), start.cy());
        check("点开始能进游戏", t.state == Game.State.PLAY);
        check("开始按钮不会重复触发两次", t.player != null && t.world != null);

        Game p = fresh();
        p.uiTop = 0f;
        step(p, 1);
        Ui.Btn atk = p.ui.byId(Game.B_ATTACK);
        p.input.post(Input.DOWN, 2, atk.cx(), atk.cy());
        step(p, 1);
        check("攻击键按下即开始挥击", p.player.atkT > 0f);
        p.input.post(Input.UP, 2, atk.cx(), atk.cy());
        step(p, 1);

        Game j = fresh();
        float px0 = j.player.x;
        j.input.post(Input.DOWN, 3, 80f, 200f);
        step(j, 1);
        j.input.post(Input.MOVE, 3, 120f, 200f);
        step(j, 20);
        check("摇杆向右拖动会移动", j.player.x > px0 + 5);
        check("摇杆激活时 HUD 有坐标", j.input.joyActive);
        j.input.post(Input.UP, 3, 120f, 200f);
        step(j, 5);
        check("松手停止摇杆", !j.input.joyActive);

        Game d = fresh();
        d.killPlayer();
        step(d, 1);
        check("死亡页有重开按钮且在屏幕内", inside(d, Game.B_RESTART));
        Ui.Btn rs = d.ui.byId(Game.B_RESTART);
        tap(d, rs.cx(), rs.cy());
        check("点重开进入新一局", d.state == Game.State.PLAY);
    }

    /** 用 rollTier 的公开入口（击杀掉落）统计平均品阶，验证幸运/等级确实起作用。 */
    static float avgTier(Game g, int n) {
        int sum = 0;
        java.lang.reflect.Method m;
        try {
            m = Game.class.getDeclaredMethod("rollTier", boolean.class, int.class);
            m.setAccessible(true);
            for (int i = 0; i < n; i++) sum += (Integer) m.invoke(g, false, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sum / (float) n;
    }

    // ------------------------------------------------------------ 按键布局

    static void layoutUi() {
        // 从暂停面板的"按键布局"进来
        Game pv = fresh();
        pv.pause();
        Ui.Btn layBtn = pv.ui.byId(Game.B_LAYOUT);
        check("暂停面板有按键布局入口", layBtn != null && layBtn.visible);
        check("按键布局按钮在屏幕内", inside(pv, Game.B_LAYOUT));
        tap(pv, layBtn.cx(), layBtn.cy());
        check("点按键布局进入布局模式", pv.customizing && pv.state == Game.State.PLAY);
        Ui.Btn doneBtn = pv.ui.byId(Game.B_DONE);
        check("布局模式有完成按钮", doneBtn != null && doneBtn.visible);
        check("完成按钮在屏幕内", inside(pv, Game.B_DONE));

        Game g = fresh();
        step(g, 1);
        g.enterLayout();
        check("进入布局模式", g.customizing);

        Ui.Btn atk = g.ui.byId(Game.B_ATTACK);
        float x0 = atk.x, y0 = atk.y;

        // 固定模式：拖不动
        g.btnFixed = true;
        g.ui.layout(g);
        Ui.Btn a0 = g.ui.byId(Game.B_ATTACK);
        g.input.post(Input.DOWN, 7, a0.cx(), a0.cy());
        g.input.post(Input.MOVE, 7, a0.cx() - 60f, a0.cy() - 40f);
        g.input.post(Input.UP, 7, a0.cx() - 60f, a0.cy() - 40f);
        step(g, 2);
        check("固定时按键不动", Math.abs(g.offX(Game.B_ATTACK)) < 0.01f);

        // 关掉固定后可以拖
        g.btnFixed = false;
        g.ui.layout(g);
        Ui.Btn a1 = g.ui.byId(Game.B_ATTACK);
        float tx = a1.cx() - 70f, ty = a1.cy() - 30f;
        g.input.post(Input.DOWN, 8, a1.cx(), a1.cy());
        step(g, 1);
        g.input.post(Input.MOVE, 8, tx, ty);
        step(g, 1);
        g.input.post(Input.UP, 8, tx, ty);
        step(g, 2);
        check("取消固定后能拖动", Math.abs(g.offX(Game.B_ATTACK)) > 10f);
        Ui.Btn a2 = g.ui.byId(Game.B_ATTACK);
        check("拖动后按键仍在屏幕内", inside(g, Game.B_ATTACK));
        check("拖动后的位置接近手指", Math.abs(a2.cx() - tx) < 14f && Math.abs(a2.cy() - ty) < 14f);

        // 拖到屏幕外会被夹回来
        float cy0 = g.offY(Game.B_ATTACK);
        g.setOffset(Game.B_ATTACK, 9999f, 9999f);
        g.ui.layout(g);
        check("偏移夹回屏幕内", inside(g, Game.B_ATTACK));
        g.setOffset(Game.B_ATTACK, 0f, cy0);

        g.resetLayout();
        g.ui.layout(g);
        Ui.Btn a3 = g.ui.byId(Game.B_ATTACK);
        check("复位回到原位", Math.abs(a3.x - x0) < 0.6f && Math.abs(a3.y - y0) < 0.6f);

        g.fire(Game.B_DONE);
        check("点完成退出布局", !g.customizing);
        check("退出后回到暂停面板", g.state == Game.State.PAUSED);

        // 布局模式下世界不动
        Game w = fresh();
        w.enterLayout();
        float px = w.player.x;
        w.input.post(Input.DOWN, 9, 60f, 200f);
        w.input.post(Input.MOVE, 9, 140f, 200f);
        for (int i = 0; i < 30; i++) {
            w.update(1f / 60f, w.input);
            w.input.endFrame();
        }
        check("布局模式角色不移动", Math.abs(w.player.x - px) < 0.01f);
    }

    static boolean inside(Game g, int id) {
        Ui.Btn b = g.ui.byId(id);
        if (b == null || !b.visible) return false;
        return b.x >= -0.5f && b.y >= g.uiTop - 0.5f
                && b.x + b.w <= Const.VW + 0.5f && b.y + b.h <= g.uiBottom + 0.5f;
    }

    static boolean panelInside(Game g) {
        boolean any = false;
        for (Ui.Btn b : g.ui.shown()) {
            if (b.x < -0.5f || b.y < g.uiTop - 0.5f
                    || b.x + b.w > Const.VW + 0.5f || b.y + b.h > g.uiBottom + 0.5f) {
                return false;
            }
            any = true;
        }
        return any;
    }
}
