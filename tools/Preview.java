import com.bolt7.factory.*;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 用 Java2D + 同一份 Renderer 生成预览图。
 * 因为渲染代码是共享的，预览图 = 真机画面（裁切差异另出一张 wide_crop）。
 */
public final class Preview {

    static final Renderer R = new Renderer();

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "preview");
        dir.mkdirs();

        shot(title(), false, new File(dir, "title.png"));
        shot(gameplay(), false, new File(dir, "gameplay.png"));
        shot(bag(), false, new File(dir, "bag.png"));
        shot(paused(), false, new File(dir, "paused.png"));
        shot(dead(), false, new File(dir, "dead.png"));
        shot(hit(), false, new File(dir, "hit.png"));
        shot(gameplay(), true, new File(dir, "wide_crop.png"));
        shot(dead(), true, new File(dir, "dead_crop.png"));
        shotPhone(gameplay(), new File(dir, "phone_2400x1080.png"));
        shotPhone(title(), new File(dir, "phone_title.png"));
        shot(swing(), false, new File(dir, "swing.png"));
        shot(layout(), false, new File(dir, "layout.png"));
        shotPhone(swing(), new File(dir, "phone_swing.png"));
        shotPhone(layout(), new File(dir, "phone_layout.png"));
        shotPhone(paused(), new File(dir, "phone_pause.png"));
        shotPhone(bag(), new File(dir, "phone_bag.png"));
        shotPhone(paused(), new File(dir, "phone_paused.png"));
        shotPhone(dead(), new File(dir, "phone_dead.png"));
        shotPhone(bench(), new File(dir, "phone_bench.png"));
        shot(bench(), false, new File(dir, "bench.png"));
        shot(codexWeapons(), false, new File(dir, "codex.png"));
        shotPhone(codexWeapons(), new File(dir, "phone_codex_weapons.png"));
        shotPhone(codexGear(), new File(dir, "phone_codex_gear.png"));
        shotPhone(fixedJoy(), new File(dir, "phone_joyfixed.png"));
        shotPhone(arsenal(), new File(dir, "phone_arsenal.png"));
        shotPhone(idle(), new File(dir, "phone_idle.png"));
        shotPhone(spawn(), new File(dir, "phone_spawn.png"));
        shotPhone(edge(), new File(dir, "phone_edge.png"));
        shot(levelup(), false, new File(dir, "levelup.png"));
        shotPhone(levelup(), new File(dir, "phone_levelup.png"));
        shotPhone(specialLevelup(), new File(dir, "phone_special.png"));
        shotPhone(deflect(), new File(dir, "phone_deflect.png"));
        shotPhone(bossfight(), new File(dir, "phone_boss.png"));
        weaponSheet(new File(dir, "weapons.png"));
        itemSheet(new File(dir, "items.png"));
        bestiarySheet(new File(dir, "bestiary.png"));
        System.out.println("preview -> " + dir.getAbsolutePath());
    }

    // ------------------------------------------------------------ 场景

    static Game base() {
        Game g = new Game(null);
        g.rnd.setSeed(20261123L);
        g.startRun();
        return g;
    }

    static Game title() {
        Game g = new Game(null);
        g.rnd.setSeed(7);
        g.bestLevel = 17;
        g.bestKills = 214;
        g.time = 3.1f;
        g.ui.layout(g);
        return g;
    }

    static Game gameplay() {
        Game g = base();
        Player p = g.player;
        p.invuln = 999f;
        TileMap map = g.world.map;

        // 找一块危险区，把周边布置成"地形/危险/资源/怪"同框的画面
        int hx = -1, hy = -1;
        outer:
        for (int ty = 8; ty < map.h - 8; ty++) {
            for (int tx = 8; tx < map.w - 8; tx++) {
                if (!map.hazard(tx, ty)) continue;
                int cnt = 0;
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dx = -4; dx <= 4; dx++) {
                        if (map.hazard(tx + dx, ty + dy)) cnt++;
                    }
                }
                if (cnt >= 18 && cnt <= 40) {
                    hx = tx;
                    hy = ty;
                    break outer;
                }
            }
        }
        if (hx < 0) {
            hx = Const.MAP_W / 2 + 14;
            hy = Const.MAP_H / 2;
        }
        for (int dy = -8; dy <= 8; dy++) {
            for (int dx = -13; dx <= 13; dx++) {
                int tx = hx + dx, ty = hy + dy;
                if (!map.inside(tx, ty)) continue;
                map.tile[map.idx(tx, ty)] = TileMap.GROUND;
            }
        }
        int[][] rocks = {
                {-12, -6}, {-11, -6}, {-10, -6}, {-12, -5}, {-11, -5},
                {10, -7}, {11, -7}, {10, -6}, {11, -6}, {12, -6},
                {-8, 7}, {-7, 7}, {-8, 8}, {-7, 8}, {-6, 8},
                {9, 6}, {10, 6}, {9, 7}, {10, 7}, {11, 7},
                {4, -8}, {5, -8}, {4, -7},
        };
        for (int i = 0; i < rocks.length; i++) {
            int tx = hx + rocks[i][0], ty = hy + rocks[i][1];
            if (!map.inside(tx, ty)) continue;
            map.tile[map.idx(tx, ty)] = TileMap.ROCK;
            map.dark[map.idx(tx, ty)] = (byte) (i % 3);
        }
        for (int k = 0; k < 4; k++) {
            map.tile[map.idx(hx - 12, hy - 2 + k)] = TileMap.WALL;
            map.tile[map.idx(hx + 12, hy + 1 + k)] = TileMap.WALL;
        }

        p.x = (hx - 4) * Const.TILE + 8f;
        p.y = hy * Const.TILE + 10f;
        p.faceX = 1f;
        p.faceY = 0.2f;
        g.level = 9;
        g.gear[Item.ATK] = new Item(Item.ATK, Item.PURPLE);
        g.gear[Item.HP] = new Item(Item.HP, Item.BLUE);
        g.gear[Item.SPD] = new Item(Item.SPD, Item.WHITE);
        p.hp = g.maxHp();
        g.energy = 12;
        g.bag.scrap = 5;
        g.bag.add(new Item(Item.DASH, Item.ORANGE));
        g.bag.add(new Item(Item.ATK, Item.BLUE));
        g.bag.add(new Item(Item.HP, Item.PURPLE));

        g.world.enemies.clear();
        g.world.enemies.add(new Enemy(p.x + 74, p.y - 6, Enemy.CRAWLER));
        g.world.enemies.add(new Enemy(p.x + 96, p.y + 34, Enemy.CHARGER));
        g.world.enemies.add(new Enemy(p.x + 40, p.y - 60, Enemy.DRONE));
        g.world.enemies.add(new Enemy(p.x + 118, p.y - 34, Enemy.TURRET));
        g.world.enemies.add(new Enemy(p.x + 140, p.y - 56, Enemy.BOMBER));
        g.world.enemies.add(new Enemy(p.x + 92, p.y + 8, Enemy.SHIELDER));
        g.world.enemies.add(new Enemy(p.x + 26, p.y - 78, Enemy.SPITTER));
        g.world.enemies.add(new Enemy(p.x + 128, p.y + 48, Enemy.LEAPER));
        g.world.enemies.get(1).damage(4, p.x, p.y, map);

        g.world.batteries.clear();
        g.world.batteries.add(new Battery(p.x + 52, p.y + 10, false));
        g.world.batteries.add(new Battery(p.x + 62, p.y - 30, true));
        g.world.nodes.clear();
        g.world.nodes.add(new Node(p.x - 34, p.y + 26, Node.SCRAP));
        g.world.nodes.add(new Node(p.x + 26, p.y + 46, Node.SCRAP));
        g.world.loots.clear();
        g.world.loots.add(new Loot(p.x + 30, p.y - 12, new Item(Item.SPD, Item.ORANGE)));

        Input in = new Input();
        for (int i = 0; i < 90; i++) {
            g.update(1f / 60f, in);
            p.invuln = 999f;
            p.hp = g.maxHp();
            g.energy = 12;
        }
        p.atkT = 0.13f;
        p.faceX = 1f;
        p.faceY = 0.15f;
        g.camX = Game.clamp(p.x - Const.VW * 0.45f, 0, Const.MAP_W * Const.TILE - Const.VW);
        g.camY = Game.clamp(p.y - Const.VH * 0.5f, 0, Const.MAP_H * Const.TILE - Const.VH);
        g.hintT = 6f;
        return g;
    }

    static Game bag() {
        Game g = gameplay();
        g.openBag();
        g.selBag = 0;
        g.ui.layout(g);
        return g;
    }

    static Game paused() {
        Game g = gameplay();
        g.pause();
        return g;
    }

    /** 招架中的受击瞬间：护盾 + 伤害数字同框。 */
    static Game hit() {
        Game g = gameplay();
        Player p = g.player;
        p.guard = Const.GUARD_T * 0.8f;
        p.guardFlash = Const.GUARD_FLASH * 0.7f;
        g.popDamage(p.x + 6, p.y - 30, "格挡 -1", Pal.GUARD, 10, 0.9f);
        Enemy en = g.world.enemies.get(0);
        g.popDamage(en.x, en.y - 16, "-5", 0xFFF2F6FA, 7, 0.5f);
        return g;
    }

    /** 挥砍到一半：刀光扫过去、拖尾还在。 */
    static Game swing() {
        Game g = gameplay();
        Player p = g.player;
        p.atkAng = (float) Math.atan2(p.faceY, p.faceX);
        p.atkT = Const.ATK_ANIM * 0.45f;
        p.atkDir = 1f;
        return g;
    }

    /** 升级三选一。 */
    static Game levelup() {
        Game g = gameplay();
        g.levelUp();
        return g;
    }

    /** 幸运词条：三张特殊卡同框（卡池只在 10 级后出现）。 */
    static Game specialLevelup() {
        Game g = gameplay();
        g.level = 14;
        g.levelUp();
        g.offers[0] = Perk.REFLECT;
        g.offers[1] = Perk.DODGE;
        g.offers[2] = Perk.AEGIS;
        return g;
    }

    /** 幸运词条实战：庇护护盾就绪 + 反弹弹丸。 */
    static Game deflect() {
        Game g = gameplay();
        g.perks[Perk.AEGIS] = 2;
        g.aegisT = g.aegisCd();
        g.perks[Perk.REFLECT] = 1;
        g.perks[Perk.DODGE] = 2;
        Player p = g.player;
        p.invuln = 999f;
        p.faceX = 1f;
        p.faceY = 0f;
        for (int i = 0; i < 2; i++) {
            Plasma pl = new Plasma(p.x + 26f + i * 16f, p.y - 6f + i * 12f, 240f, -20f);
            pl.friendly = true;
            pl.dmg = 9;
            g.world.plasmas.add(pl);
            g.world.plasmas.add(new Plasma(p.x + 58f, p.y + 26f - i * 10f, -170f, -30f, i == 1));
        }
        Enemy e = new Enemy(p.x + 84f, p.y - 18f, Enemy.CRAWLER);
        e.maxHp = e.hp = 24;
        g.world.enemies.add(e);
        g.ui.layout(g);
        return g;
    }

    /** Boss 巢穴：站在第一区守卫面前。 */
    static Game bossfight() {
        Game g = base();
        Player p = g.player;
        p.invuln = 999f;
        World.Site site = null;
        for (int i = 0; i < g.world.sites.size(); i++) {
            if (g.world.sites.get(i).zone == 0) {
                site = g.world.sites.get(i);
                break;
            }
        }
        if (site != null) {
            p.x = site.x - 42f;
            p.y = site.y + 26f;
            p.faceX = 1f;
            p.faceY = 0f;
            for (int i = 0; i < g.world.enemies.size(); i++) {
                Enemy en = g.world.enemies.get(i);
                if (en.boss && Math.hypot(en.x - site.x, en.y - site.y) < 40f) {
                    g.bossFocus = en;
                    g.bossBarT = 1.2f;
                    break;
                }
            }
        }
        g.camX = Game.clamp(p.x - Const.VW * 0.42f, 0, Const.MAP_W * Const.TILE - Const.VW);
        g.camY = Game.clamp(p.y - Const.VH * 0.5f, 0, Const.MAP_H * Const.TILE - Const.VH);
        g.ui.layout(g);
        return g;
    }

    /** 四种武器对比表：同一场景、同一帧，只换武器类型。 */
    static void weaponSheet(File out) throws Exception {
        int cell = 240, pad = 14, cols = 4;
        int W = pad * 2 + cell * cols, H = pad * 2 + cell + 76;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        PreviewGC gg = new PreviewGC(g2);
        gg.clear(0xFF0A0E12);
        gg.text("BOLT-7  v8 四种武器", pad, 12, 0xFFE8EEF4, 20, true, 0);
        gg.text("同一品阶，只换类型：形状 / 冷却 / 扇角 / 距离都不同", pad, 40, 0xFF7E8C97, 11, false, 0);

        for (int t = 0; t < 4; t++) {
            Game g = base();
            g.world.enemies.clear();
            g.gear[Item.ATK] = new Item(Item.ATK, Item.PURPLE, t);
            Player p = g.player;
            p.invuln = 999f;
            p.faceX = 1f;
            p.faceY = 0f;
            p.atkAng = 0f;
            p.atkDir = 1f;
            p.atkT = g.atkAnim() * 0.45f;
            g.camX = Game.clamp(p.x - Const.VW * 0.30f, 0, Const.MAP_W * Const.TILE - Const.VW);
            g.camY = Game.clamp(p.y - Const.VH * 0.5f, 0, Const.MAP_H * Const.TILE - Const.VH);
            g.ui.layout(g);

            final int S = 2;
            BufferedImage frame = new BufferedImage(Const.VW * S, Const.VH * S,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D fg = frame.createGraphics();
            fg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            fg.scale(S, S);
            R.draw(new PreviewGC(fg), g);
            fg.dispose();

            float px = p.x - g.camX, py = p.y - g.camY;
            int sx = Math.max(0, Math.min(Const.VW - 130, Math.round(px) - 58));
            int sy = Math.max(0, Math.min(Const.VH - 130, Math.round(py) - 66));
            int x = pad + t * cell, y = 62;
            g2.drawImage(frame.getSubimage(sx * S, sy * S, 130 * S, 130 * S),
                    x, y, cell - 10, cell - 10, null);
            g2.setColor(new java.awt.Color(0xFF2A343D));
            g2.drawRect(x, y, cell - 10, cell - 10);
            gg.text(Weapon.NAME[t], x + 6, y + cell - 34, 0xFFFFFFFF, 13, true, 0);
            gg.text(String.format(java.util.Locale.US, "%.2fs · %.0fpx · %.0f°",
                            game_cd(t), Weapon.range(t), Weapon.arc(t) * 57.2958f * 2f),
                    x + 6, y + cell - 18, 0xFF8A99A6, 10, false, 0);
            gg.text(Weapon.TAG[t], x + cell - 14, y + cell - 18, 0xFF7FD4FF, 9, false, 2);
        }
        g2.dispose();
        ImageIO.write(img, "png", out);
    }

    static float game_cd(int t) {
        return Weapon.cd(t);
    }

    /**
     * 全部装备总览：20 把武器 + 15 件护具，一页看完。
     * 卡片上的图标直接调 Renderer 里那套画法，和游戏里（合成台 / 图鉴）长得一模一样。
     */
    static void itemSheet(File out) throws Exception {
        int cw = 236, ch = 76, gap = 8, pad = 22;
        int gridW = cw * 4 + gap * 3;
        int secH = 30;
        int W = pad * 2 + gridW;
        int H = pad + 54 + 20 + secH + 5 * (ch + gap) + 26 + secH + 5 * (ch + gap) + pad;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        PreviewGC g = new PreviewGC(g2);
        g.clear(0xFF0A0E12);

        g.text("BOLT-7  v10 全部装备", pad, 12, 0xFFE8EEF4, 22, true, 0);
        g.text("20 把武器（4 类型 × 5 品阶）+ 15 件护具（3 部位 × 5 品阶）　品阶只改数值，类型只改手感",
                pad, 40, 0xFF7E8C97, 11, false, 0);

        // 品阶图例
        float lx = pad;
        for (int t = 0; t < Item.TIERS; t++) {
            g.rect(lx, 62, 12, 12, Item.TIER_COLOR[t]);
            String label = Item.TIER_NAME[t];
            g.text(label, lx + 18, 63, Item.TIER_COLOR[t], 11, true, 0);
            lx += 26 + g.textWidth(label, 11, true);
        }

        int y = 92;
        g.text("武 器", pad, y, 0xFF46D8E6, 15, true, 0);
        g.text("冷却 / 距离 / 扇角 决定怎么打；伤害随品阶走", pad + 54, y + 5, 0xFF66788A, 10, false, 0);
        y += secH;

        for (int t = 0; t < Weapon.TYPES; t++) {
            for (int tier = 0; tier < Item.TIERS; tier++) {
                int x = pad + t * (cw + gap), cy = y + tier * (ch + gap);
                Item it = new Item(Item.ATK, tier, t);
                g.rect(x, cy, cw, ch, 0xFF141A20);
                g.frame(x, cy, cw, ch, it.color(), 1f);
                g.rect(x, cy, 3, ch, it.color());
                Renderer.drawWeaponIcon(g, t, tier, x + 36, cy + ch / 2f, 40f);
                g.text(it.name(), x + 66, cy + 10, it.color(), 15, true, 0);
                g.text(String.format(java.util.Locale.US, "伤 %d · %.2fs · %.0fpx · %.0f°",
                                it.value(), Weapon.cd(t), Weapon.range(t),
                                Weapon.arc(t) * 57.2958f * 2f),
                        x + 66, cy + 32, 0xFF8A99A6, 11, false, 0);
                g.text(Weapon.NAME[t] + " · " + Weapon.TAG[t], x + 66, cy + 52, 0xFF5E8FA8, 10, false, 0);
            }
        }
        y += 5 * (ch + gap) + 26;

        g.text("护 具", pad, y, 0xFF46D8E6, 15, true, 0);
        g.text("部位决定加什么，品阶决定加多少；三件都是纯数值，不挑手感", pad + 54, y + 5, 0xFF66788A, 10, false, 0);
        y += secH;

        int gw = (gridW - gap * 2) / 3;
        for (int s = 1; s < Item.SLOTS; s++) {
            for (int tier = 0; tier < Item.TIERS; tier++) {
                int x = pad + (s - 1) * (gw + gap), cy = y + tier * (ch + gap);
                Item it = new Item(s, tier, 0);
                g.rect(x, cy, gw, ch, 0xFF141A20);
                g.frame(x, cy, gw, ch, it.color(), 1f);
                g.rect(x, cy, 3, ch, it.color());
                Renderer.drawItemIcon(g, s, tier, x + 38, cy + ch / 2f, 42f);
                g.text(it.name(), x + 70, cy + 12, it.color(), 15, true, 0);
                g.text(it.effect(), x + 70, cy + 36, 0xFF8A99A6, 12, false, 0);
                g.text(Item.SLOT_NAME[s] + " · " + Item.TIER_NAME[tier], x + 70, cy + 55,
                        0xFF5E8FA8, 10, false, 0);
            }
        }

        g2.dispose();
        ImageIO.write(img, "png", out);
    }

    /** 按键布局模式：可拖动的键都描了框。 */
    static Game layout() {
        Game g = gameplay();
        g.enterLayout();
        g.btnFixed = false;
        g.setOffset(Game.B_ATTACK, -26f, -8f);
        g.setOffset(Game.B_DASH, -10f, -4f);
        g.ui.layout(g);
        return g;
    }

    /** 合成台面板：8 张产物卡、废铁、合成键。 */
    static Game bench() {
        Game g = gameplay();
        Bench b = g.world.benches.get(0);
        g.player.x = b.x;
        g.player.y = b.y + 18f;
        g.camX = Game.clamp(g.player.x - Const.VW * 0.45f, 0, Const.MAP_W * Const.TILE - Const.VW);
        g.camY = Game.clamp(g.player.y - Const.VH * 0.5f, 0, Const.MAP_H * Const.TILE - Const.VH);
        g.bag.scrap = 7;
        g.openBench();
        g.benchPick = Weapon.SPEAR;
        g.ui.layout(g);
        return g;
    }

    /** 图鉴第一页：20 把武器。 */
    static Game codexWeapons() {
        Game g = bench();
        g.openCodex();
        g.codexPage = 0;
        g.ui.layout(g);
        return g;
    }

    /** 图鉴第二页：15 件装备。 */
    static Game codexGear() {
        Game g = bench();
        g.openCodex();
        g.codexPage = 1;
        g.ui.layout(g);
        return g;
    }

    /** 固定摇杆：底盘常驻左下，推杆在中间。 */
    static Game fixedJoy() {
        Game g = gameplay();
        g.joyFixed = true;
        g.input.joyActive = true;
        Ui.Btn jb = g.ui.byId(Game.B_JOY);
        if (jb != null) {
            g.input.joyX = jb.cx();
            g.input.joyY = jb.cy();
        }
        g.input.joyDX = 12f;
        g.input.joyDY = -9f;
        g.input.moveX = 0.7f;
        g.input.moveY = -0.4f;
        g.ui.layout(g);
        return g;
    }

    /** 传说武器 + 高等级长刀光：验证武器外观与挥砍随距离变长。 */
    static Game arsenal() {
        Game g = gameplay();
        g.gear[Item.ATK] = new Item(Item.ATK, Item.RED);
        g.level = 40;
        Player p = g.player;
        p.atkAng = (float) Math.atan2(p.faceY, p.faceX);
        p.atkT = Const.ATK_ANIM * 0.45f;
        p.atkDir = 1f;
        p.hp = g.maxHp();
        g.ui.layout(g);
        return g;
    }

    /** 站立不动：检查手持武器在待机姿势下的样子。 */
    static Game idle() {
        Game g = gameplay();
        g.player.atkT = 0f;
        g.player.faceX = 0f;
        g.player.faceY = 1f;
        g.ui.layout(g);
        return g;
    }

    /** 刚开局：出生点、合成台、信标同框，检查新玩家第一眼看到什么。 */
    static Game spawn() {
        Game g = base();
        g.player.spawnProtect = 999f;
        g.hintT = 8f;
        g.camX = Game.clamp(g.player.x - Const.VW * 0.5f, 0, Const.MAP_W * Const.TILE - Const.VW);
        g.camY = Game.clamp(g.player.y - Const.VH * 0.5f, 0, Const.MAP_H * Const.TILE - Const.VH);
        g.ui.layout(g);
        return g;
    }

    /** 地图边缘：断崖 + 护栏，检查"世界尽头"的观感。 */
    static Game edge() {
        Game g = base();
        g.player.spawnProtect = 999f;
        g.player.x = Const.EDGE * Const.TILE + 60f;
        g.player.y = Const.MAP_H * Const.TILE * 0.5f;
        g.camX = 0f;
        g.camY = Game.clamp(g.player.y - Const.VH * 0.5f, 0, Const.MAP_H * Const.TILE - Const.VH);
        g.world.enemies.clear();
        g.ui.layout(g);
        return g;
    }

    static Game dead() {
        Game g = gameplay();
        g.level = 9;
        g.killPlayer();
        g.stateT = 2f;
        g.ui.layout(g);
        return g;
    }

    /** 图鉴：9 种怪物 3×3 摆开，带名字，用来核对外形是否互相可分。 */
    static void bestiarySheet(File out) throws Exception {
        int cw = 300, ch = 190, pad = 16, cols = 3, rows = 3;
        int W = pad * 2 + cw * cols, H = pad * 2 + ch * rows + 60;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        PreviewGC g = new PreviewGC(g2);

        g.clear(0xFF0A0E12);
        g.text("BOLT-7  怪物图鉴", pad, 14, 0xFFE8EEF4, 22, true, 0);
        g.text("9 种 · 行为与外形各不相同", pad, 42, 0xFF7E8C97, 12, false, 0);

        Spr[] sp = {ArtData.CRAWLER, ArtData.DRONE, ArtData.TURRET, ArtData.CHARGER,
                ArtData.WARDEN, ArtData.BOMBER, ArtData.SHIELDER, ArtData.SPITTER, ArtData.LEAPER};
        int[] col = {Pal.E_CRAWLER, Pal.E_DRONE, Pal.E_TURRET, Pal.E_CHARGER, Pal.E_WARDEN,
                Pal.E_BOMBER, Pal.E_SHIELDER, Pal.E_SPITTER, Pal.E_LEAPER};
        String[] desc = {"巡逻贴身", "悬浮绕圈", "固定点射", "蓄力冲撞", "三连散射",
                "贴脸自爆", "正面减伤", "中距酸液", "跳跃冲击"};

        for (int i = 0; i < sp.length; i++) {
            int c = i % cols, r = i / cols;
            float x = pad + c * cw, y = 70 + r * ch;
            g.rect(x + 3, y + 3, cw - 8, ch - 10, 0xFF141A20);
            g.frame(x + 3, y + 3, cw - 8, ch - 10, 0xFF2A343D, 1f);
            g.circle(x + 74, y + 96, 42, 0x22000000);
            g.sprite(sp[i], x + 74 - sp[i].w * 2.6f, y + 96 - sp[i].h * 2.6f, 5.2f);
            g.text(Enemy.TYPE_NAME[i], x + 150, y + 34, col[i], 22, true, 0);
            g.text(desc[i], x + 150, y + 72, 0xFF8A99A6, 14, false, 0);
            g.rect(x + 150, y + 100, 26, 3, col[i]);
        }
        g2.dispose();
        ImageIO.write(img, "png", out);
    }

    // ------------------------------------------------------------ 输出

    static void shot(Game game, boolean wideCrop, File out) throws Exception {
        // 与 GameView.computeLayout 完全一致的可见带：[cropTop, VH-cropBot]
        if (wideCrop) {
            game.uiTop = Const.cropTop(2400, 1080);
            game.uiBottom = Const.VH - (Const.cropTotal(2400, 1080) - game.uiTop);
        }
        game.ui.layout(game);

        // 与 AndroidGC 一致的 3 倍超采样：1080p 屏上真实就是这个像素密度
        final int S = 3;
        BufferedImage img = new BufferedImage(Const.VW * S, Const.VH * S, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(S, S);
        R.draw(new PreviewGC(g2), game);
        g2.dispose();

        int y0 = wideCrop ? Math.round(game.uiTop) * S : 0;
        int hh = Math.round(game.uiBottom - game.uiTop) * S;
        BufferedImage crop = img.getSubimage(0, y0, Const.VW * S, hh);
        ImageIO.write(crop, "png", out);
    }

    /** 按真机 2400×1080（20:9）出图：5 倍超采样，再按可见带裁成 2400×1080。 */
    static void shotPhone(Game game, File out) throws Exception {
        final int S = 5;
        float crop = Const.cropTotal(2400, 1080);
        float cropTop = Const.cropTop(2400, 1080);
        float cropBot = crop - cropTop;
        game.uiTop = cropTop;
        game.uiBottom = Const.VH - cropBot;
        game.ui.layout(game);

        BufferedImage img = new BufferedImage(Const.VW * S, Const.VH * S, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.scale(S, S);
        R.draw(new PreviewGC(g2), game);
        g2.dispose();

        int y0 = Math.round(cropTop * S);
        int hh = Math.round((Const.VH - crop) * S);
        BufferedImage cropImg = img.getSubimage(0, y0, Const.VW * S, hh);
        BufferedImage outImg = new BufferedImage(2400, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D o = outImg.createGraphics();
        o.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        o.drawImage(cropImg, 0, 0, 2400, 1080, null);
        o.dispose();
        ImageIO.write(outImg, "png", out);
    }
}
