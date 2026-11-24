package com.bolt7.factory;

import java.util.ArrayList;
import java.util.Random;

/**
 * 俯视角世界：一次性生成的 320×240 格大地图（5120×3840 像素），
 * 出生点在正中，越往外怪物越强。地图固定，可以来回跑，不存在"关卡"。
 *
 * 边界不再是看不见的墙：最外 3 圈是 VOID（断崖），渲染成深黑深渊 + 钢制护栏，
 * 走到那里视觉上就是"世界尽头"。地图面积是 v6 的 2.56 倍，跑一圈要好几分钟。
 */
public final class World {

    public final TileMap map;
    public final ArrayList<Node> nodes = new ArrayList<Node>();
    public final ArrayList<Battery> batteries = new ArrayList<Battery>();
    public final ArrayList<Loot> loots = new ArrayList<Loot>();
    public final ArrayList<Enemy> enemies = new ArrayList<Enemy>();
    public final ArrayList<Plasma> plasmas = new ArrayList<Plasma>();
    public final ArrayList<Bench> benches = new ArrayList<Bench>();
    /** Boss 巢穴：每区一座，击杀后 cleared=true（本局不再刷）。 */
    public final ArrayList<Site> sites = new ArrayList<Site>();

    public static final class Site {
        public final float x, y;
        public final int zone;
        public boolean cleared;

        Site(float x, float y, int zone) {
            this.x = x;
            this.y = y;
            this.zone = zone;
        }
    }

    public final float spawnX, spawnY;
    /** 整张地图的缩略图：一格一像素，用于小地图。 */
    public final Spr mini;

    public World(long seed) {
        Random r = new Random(seed);
        map = new TileMap(Const.MAP_W, Const.MAP_H);
        int sx = Const.MAP_W / 2, sy = Const.MAP_H / 2;
        spawnX = sx * Const.TILE + Const.TILE / 2f;
        spawnY = sy * Const.TILE + Const.TILE / 2f;

        // 最外圈断崖
        for (int y = 0; y < map.h; y++) {
            for (int x = 0; x < map.w; x++) {
                if (x < Const.EDGE || y < Const.EDGE
                        || x >= map.w - Const.EDGE || y >= map.h - Const.EDGE) {
                    map.tile[map.idx(x, y)] = TileMap.VOID;
                }
            }
        }
        clear(sx, sy, 4);

        genRocks(r, sx, sy);
        genRuins(r, sx, sy);
        genHazards(r, sx, sy);
        genBenches(r, sx, sy);
        genNodes(r, sx, sy);
        genBatteries(r, sx, sy);
        genEnemies(r, sx, sy);
        genSites();

        mini = Spr.of(map.w, map.h);
        for (int y = 0; y < map.h; y++) {
            for (int x = 0; x < map.w; x++) {
                int id = map.idx(x, y);
                int c = 0xFF1C2227;
                if (map.tile[id] == TileMap.VOID) c = 0xFF05070A;
                else if (map.haz[id] != 0) c = 0xFFB53524;
                else if (map.tile[id] == TileMap.ROCK) c = 0xFF454F56;
                else if (map.tile[id] == TileMap.WALL) c = 0xFF2B333A;
                mini.px[y * map.w + x] = 0xFF000000 | c;
            }
        }
    }

    // ------------------------------------------------------------ 生成

    private void clear(int cx, int cy, int rad) {
        for (int y = cy - rad; y <= cy + rad; y++) {
            for (int x = cx - rad; x <= cx + rad; x++) {
                if (!map.inside(x, y)) continue;
                map.tile[map.idx(x, y)] = TileMap.GROUND;
                map.haz[map.idx(x, y)] = 0;
            }
        }
    }

    private double tileDist(int x, int y, int cx, int cy) {
        return Math.hypot(x - cx, y - cy);
    }

    private void genRocks(Random r, int sx, int sy) {
        int n = Const.MAP_W * Const.MAP_H / 70;          // 面积缩放
        for (int i = 0; i < n; i++) {
            int x = Const.EDGE + 1 + r.nextInt(map.w - (Const.EDGE + 1) * 2);
            int y = Const.EDGE + 1 + r.nextInt(map.h - (Const.EDGE + 1) * 2);
            if (tileDist(x, y, sx, sy) < 7) continue;
            int k = 1 + r.nextInt(4);
            int w = 1 + r.nextInt(3), h = 1 + r.nextInt(3);
            for (int j = 0; j < k; j++) {
                int tx = x + r.nextInt(w), ty = y + r.nextInt(h);
                if (!map.inside(tx, ty) || tileDist(tx, ty, sx, sy) < 7) continue;
                if (map.isVoid(tx, ty)) continue;
                int id = map.idx(tx, ty);
                map.tile[id] = TileMap.ROCK;
                map.dark[id] = (byte) (r.nextInt(3));
            }
        }
    }

    /** 断墙：几段直线废墟，给地图一点结构感，也提供遮挡。 */
    private void genRuins(Random r, int sx, int sy) {
        int n = 60;
        for (int i = 0; i < n; i++) {
            int x = 6 + r.nextInt(map.w - 14);
            int y = 6 + r.nextInt(map.h - 14);
            if (tileDist(x, y, sx, sy) < 12) continue;
            int len = 4 + r.nextInt(8);
            boolean horiz = r.nextBoolean();
            for (int k = 0; k < len; k++) {
                int tx = horiz ? x + k : x;
                int ty = horiz ? y : y + k;
                if (!map.inside(tx, ty) || tileDist(tx, ty, sx, sy) < 10) continue;
                if (map.isVoid(tx, ty)) continue;
                if (r.nextInt(5) == 0) continue;   // 留缺口，避免围成死胡同
                int id = map.idx(tx, ty);
                map.tile[id] = TileMap.WALL;
                map.dark[id] = (byte) r.nextInt(3);
            }
        }
    }

    /**
     * 危险区是"电房"：规则矩形，彼此留出间距。
     * 原来用椭圆随机堆叠，叠出来的形状像红斑，边界读不清。
     */
    private void genHazards(Random r, int sx, int sy) {
        int target = 48;
        int placed = 0, guard = 0;
        while (placed < target && guard++ < 1200) {
            int w = 4 + r.nextInt(4);
            int h = 3 + r.nextInt(3);
            int x = 4 + r.nextInt(map.w - 8 - w);
            int y = 4 + r.nextInt(map.h - 8 - h);
            if (tileDist(x + w / 2, y + h / 2, sx, sy) < 11) continue;

            boolean clash = false;
            for (int ty = y - 2; ty <= y + h + 1 && !clash; ty++) {
                for (int tx = x - 2; tx <= x + w + 1; tx++) {
                    if (!map.inside(tx, ty)) continue;
                    if (map.haz[map.idx(tx, ty)] != 0) {
                        clash = true;
                        break;
                    }
                }
            }
            if (clash) continue;

            for (int ty = y; ty < y + h; ty++) {
                for (int tx = x; tx < x + w; tx++) {
                    int id = map.idx(tx, ty);
                    map.haz[id] = 1;
                    map.tile[id] = TileMap.GROUND;
                }
            }
            placed++;
        }
    }

    private boolean free(int tx, int ty) {
        if (!map.inside(tx, ty)) return false;
        int id = map.idx(tx, ty);
        return map.tile[id] == TileMap.GROUND && map.haz[id] == 0;
    }

    /** 合成台：出生点旁边必有一个（4 格），其余散布全图，彼此至少 18 格。 */
    private void genBenches(Random r, int sx, int sy) {
        // 出生点东南 4 格，保证开局就能找到
        for (int k = 0; k < 40; k++) {
            int tx = sx + 4 + (k % 3), ty = sy + 4 + (k / 3) % 3;
            if (free(tx, ty)) {
                benches.add(new Bench(tx * Const.TILE + 8f, ty * Const.TILE + 8f));
                break;
            }
        }
        int guard = 0;
        while (benches.size() < Const.BENCH_COUNT && guard++ < 3000) {
            int tx = Const.EDGE + 4 + r.nextInt(map.w - (Const.EDGE + 4) * 2);
            int ty = Const.EDGE + 4 + r.nextInt(map.h - (Const.EDGE + 4) * 2);
            if (!free(tx, ty) || tileDist(tx, ty, sx, sy) < 16) continue;
            boolean clash = false;
            for (int i = 0; i < benches.size(); i++) {
                Bench b = benches.get(i);
                if (tileDist(tx, ty, (int) (b.x / Const.TILE), (int) (b.y / Const.TILE)) < 18) {
                    clash = true;
                    break;
                }
            }
            if (clash) continue;
            benches.add(new Bench(tx * Const.TILE + 8f, ty * Const.TILE + 8f));
        }
    }

    private void genNodes(Random r, int sx, int sy) {
        int target = 170;
        int placed = 0, guard = 0;
        while (placed < target && guard++ < 8000) {
            int x = Const.EDGE + 1 + r.nextInt(map.w - (Const.EDGE + 1) * 2);
            int y = Const.EDGE + 1 + r.nextInt(map.h - (Const.EDGE + 1) * 2);
            if (!free(x, y) || tileDist(x, y, sx, sy) < 4) continue;
            nodes.add(new Node(x * Const.TILE + 8f, y * Const.TILE + 8f, Node.SCRAP));
            placed++;
        }
    }

    private void genBatteries(Random r, int sx, int sy) {
        int target = 200;
        int placed = 0, guard = 0;
        while (placed < target && guard++ < 9000) {
            int x = Const.EDGE + 1 + r.nextInt(map.w - (Const.EDGE + 1) * 2);
            int y = Const.EDGE + 1 + r.nextInt(map.h - (Const.EDGE + 1) * 2);
            if (!free(x, y) || tileDist(x, y, sx, sy) < 3) continue;
            boolean big = r.nextInt(5) == 0;
            batteries.add(new Battery(x * Const.TILE + 8f, y * Const.TILE + 8f, big));
            placed++;
        }
    }

    private void genEnemies(Random r, int sx, int sy) {
        int target = 70;
        int placed = 0, guard = 0;
        while (placed < target && guard++ < 6000) {
            int x = Const.EDGE + 1 + r.nextInt(map.w - (Const.EDGE + 1) * 2);
            int y = Const.EDGE + 1 + r.nextInt(map.h - (Const.EDGE + 1) * 2);
            double d = tileDist(x, y, sx, sy);
            if (!free(x, y) || d < 13) continue;
            Enemy en = new Enemy(x * Const.TILE + 8f, y * Const.TILE + 8f, pickType(r, d));
            en.zone = zoneOfTiles(d);
            enemies.add(en);
            placed++;
        }
    }

    // ------------------------------------------------------------ 区域与 Boss

    /** 以出生点为圆心的三条环带。 */
    public static int zoneOfTiles(double tileDist) {
        if (tileDist > Const.ZONE_R2) return 2;
        if (tileDist > Const.ZONE_R1) return 1;
        return 0;
    }

    public int zoneAt(float px, float py) {
        double d = Math.hypot((px - spawnX) / (double) Const.TILE,
                (py - spawnY) / (double) Const.TILE);
        return zoneOfTiles(d);
    }

    public static String zoneName(int z) {
        switch (z) {
            case 1:
                return "废墟";
            case 2:
                return "深渊";
            default:
                return "近郊";
        }
    }

    /**
     * 三座 Boss 巢穴：出生点三个方向，半径分别落在三个区里。
     * 巢穴附近清空一块空地并确保能走进去，否则会出现"Boss 卡在石头里"。
     */
    private void genSites() {
        float sx = Const.MAP_W / 2f, sy = Const.MAP_H / 2f;
        float[] ang = {0.55f, 2.65f, 4.55f};
        int[] rad = {36, 74, 104};
        for (int i = 0; i < ang.length; i++) {
            int z = zoneOfTiles(rad[i]);
            int tx = Math.round(sx + (float) Math.cos(ang[i]) * rad[i]);
            int ty = Math.round(sy + (float) Math.sin(ang[i]) * rad[i]);
            int[] ok = findOpen(tx, ty);
            if (ok == null) continue;
            clear(ok[0], ok[1], 3);
            Enemy boss = new Enemy(ok[0] * Const.TILE + 8f, ok[1] * Const.TILE + 8f,
                    Enemy.WARDEN, true);
            boss.zone = z;
            enemies.add(boss);
            sites.add(new Site(boss.x, boss.y, z));
        }
    }

    /** 从给定格向外螺旋找一块能站的地。 */
    private int[] findOpen(int cx, int cy) {
        for (int r = 0; r < 26; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = cx + dx, y = cy + dy;
                    if (free(x, y)) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    /** Boss 倒下：把最近的巢穴标记为已清。 */
    public void clearBossSite(Enemy en) {
        for (int i = 0; i < sites.size(); i++) {
            Site s = sites.get(i);
            if (Math.hypot(s.x - en.x, s.y - en.y) < 40f) {
                s.cleared = true;
                return;
            }
        }
    }

    /** 按离出生点的距离挑类型：越远越危险，但每种都会在中距离出现。 */
    public static int pickType(Random r, double dist) {
        int roll = r.nextInt(100);
        if (dist > 58 && roll < 12) return Enemy.WARDEN;
        if (dist > 34 && roll < 26) return Enemy.SHIELDER;
        if (dist > 28 && roll < 42) return Enemy.CHARGER;
        if (dist > 20 && roll < 56) return Enemy.LEAPER;
        if (dist > 16 && roll < 68) return Enemy.TURRET;
        if (roll < 78) return Enemy.SPITTER;
        if (roll < 88) return Enemy.BOMBER;
        return r.nextBoolean() ? Enemy.CRAWLER : Enemy.DRONE;
    }

    /** 在玩家周围补一块电池，保证长时间探索不会"资源枯竭"。 */
    public boolean spawnBatteryNear(float px, float py, Random r) {
        for (int tries = 0; tries < 24; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            float dist = 280f + r.nextFloat() * 320f;
            float x = px + (float) Math.cos(a) * dist;
            float y = py + (float) Math.sin(a) * dist;
            int tx = (int) (x / Const.TILE), ty = (int) (y / Const.TILE);
            if (!free(tx, ty)) continue;
            batteries.add(new Battery(x, y, r.nextInt(4) == 0));
            return true;
        }
        return false;
    }

    /** 在玩家视野外找一个空地生成怪物，用于持续补充。 */
    public boolean spawnEnemyNear(float px, float py, Random r, int maxTotal) {
        if (enemies.size() >= maxTotal) return false;
        for (int tries = 0; tries < 30; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            float dist = 260f + r.nextFloat() * 160f;
            float x = px + (float) Math.cos(a) * dist;
            float y = py + (float) Math.sin(a) * dist;
            int tx = (int) (x / Const.TILE), ty = (int) (y / Const.TILE);
            if (!free(tx, ty)) continue;
            double dFromSpawn = Math.hypot(tx - Const.MAP_W / 2, ty - Const.MAP_H / 2);
            enemies.add(new Enemy(x, y, pickType(r, dFromSpawn)));
            return true;
        }
        return false;
    }
}
