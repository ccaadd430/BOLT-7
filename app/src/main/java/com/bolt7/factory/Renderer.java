package com.bolt7.factory;

/**
 * 渲染器。只依赖 GC 接口，因此同一份代码在 Android 与桌面预览里画出完全一致的画面。
 *
 * 图层顺序（也是可读性的保证）：
 *   地面纹理 → 危险区（最抢眼） → 岩石 → 资源/怪物/玩家 → 粒子 → HUD → 面板 → 按钮
 */
public final class Renderer {

    private static final int T = Const.TILE;
    private static final int[] ROCK_MID = {0xFF212A31, 0xFF1D262C, 0xFF252F36};
    private static final int[] ROCK_TOP = {0xFF44525D, 0xFF3E4C57, 0xFF485660};

    public void draw(GC g, Game game) {
        if (game.state == Game.State.TITLE || game.world == null || game.player == null) {
            drawTitle(g, game);
            drawButtons(g, game);
            return;
        }
        drawWorld(g, game);
        if (game.state != Game.State.DEAD) drawHud(g, game);
        if (game.state == Game.State.PLAY || game.state == Game.State.PAUSED) drawMinimap(g, game);
        drawToasts(g, game);
        if (game.customizing) drawLayoutOverlay(g, game);
        if (game.state == Game.State.PAUSED) drawPausePanel(g, game);
        if (game.state == Game.State.BAG) drawBagPanel(g, game);
        if (game.state == Game.State.BENCH) drawBenchPanel(g, game);
        if (game.state == Game.State.CODEX) drawCodex(g, game);
        if (game.state == Game.State.LEVELUP) drawLevelUpPanel(g, game);
        if (game.state == Game.State.DEAD) drawDeadPanel(g, game);
        drawButtons(g, game);
    }

    // ============================================================ 世界

    private void drawWorld(GC g, Game game) {
        float camX = game.camX + game.shakeX();
        float camY = game.camY + game.shakeY();
        TileMap map = game.world.map;

        g.clear(Pal.VOID);
        drawGround(g, camX, camY);
        drawGroundDetails(g, camX, camY);
        drawHazards(g, map, camX, camY, game.time);
        drawVoid(g, map, camX, camY, game.time);
        drawRocks(g, map, camX, camY);
        drawBeacon(g, game, camX, camY);
        drawBenches(g, game, camX, camY);
        drawNodes(g, game, camX, camY);
        drawLoot(g, game, camX, camY);
        drawBatteries(g, game, camX, camY);
        drawEnemies(g, game, camX, camY);
        drawPlayer(g, game, camX, camY);
        drawPlasmas(g, game, camX, camY);
        drawParticles(g, game, camX, camY);
        drawDmgTexts(g, game, camX, camY);
    }

    /** 伤害数字：世界坐标 → 屏幕，带黑色描边，亮地面暗地面都读得清。 */
    private void drawDmgTexts(GC g, Game game, float camX, float camY) {
        for (int i = 0; i < game.dmgTexts.size(); i++) {
            DmgText d = game.dmgTexts.get(i);
            int a = d.alpha();
            if (a <= 4) continue;
            float x = d.x - camX;
            float y = d.y - camY;
            if (x < -60 || x > Const.VW + 60 || y < -30 || y > Const.VH + 30) continue;
            // 只用描边，不加底板：大黑块会挡住角色和怪物
            int outline = withAlpha(0xF0000000, a);
            g.text(d.text, x - 1.2f, y, outline, d.size, true, 1);
            g.text(d.text, x + 1.2f, y, outline, d.size, true, 1);
            g.text(d.text, x, y - 1.2f, outline, d.size, true, 1);
            g.text(d.text, x, y + 1.2f, outline, d.size, true, 1);
            g.text(d.text, x, y, withAlpha(d.color, a), d.size, true, 1);
        }
    }

    private void drawGround(GC g, float camX, float camY) {
        Spr tex = ArtData.GROUND;
        int startX = (int) Math.floor(camX / tex.w) * tex.w;
        int startY = (int) Math.floor(camY / tex.h) * tex.h;
        for (int y = startY; y < camY + Const.VH + tex.h; y += tex.h) {
            for (int x = startX; x < camX + Const.VW + tex.w; x += tex.w) {
                g.sprite(tex, x - camX, y - camY);
            }
        }
    }

    private void drawHazards(GC g, TileMap map, float camX, float camY, float time) {
        int tx0 = (int) Math.floor(camX / T) - 1;
        int ty0 = (int) Math.floor(camY / T) - 1;
        int tx1 = (int) Math.floor((camX + Const.VW) / T) + 1;
        int ty1 = (int) Math.floor((camY + Const.VH) / T) + 1;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3.2f);

        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                if (!map.hazard(tx, ty)) continue;
                float x = tx * T - camX;
                float y = ty * T - camY;

                boolean up = !map.hazard(tx, ty - 1);
                boolean dn = !map.hazard(tx, ty + 1);
                boolean lf = !map.hazard(tx - 1, ty);
                boolean rt = !map.hazard(tx + 1, ty);

                // 外发光：只在整块区域的外沿，弱一点，别把画面烧糊
                int glow = withAlpha(Pal.HAZARD_GLOW, (int) (26 + 26 * pulse));
                if (up) g.rect(x - 2, y - 4, T + 4, 4, glow);
                if (dn) g.rect(x - 2, y + T, T + 4, 4, glow);
                if (lf) g.rect(x - 4, y - 2, 4, T + 4, glow);
                if (rt) g.rect(x + T, y - 2, 4, T + 4, glow);

                // 底板
                g.rect(x, y, T, T, Pal.HAZARD_DEEP);
                g.rect(x + 1, y + 1, T - 2, T - 2, 0xFF280C09);

                // 内部：四个电极节点，像通电地板，而不是满地横条纹
                int node = withAlpha(Pal.HAZARD_RED, (int) (52 + 40 * pulse));
                g.rect(x + 3, y + 3, 2, 2, node);
                g.rect(x + 11, y + 3, 2, 2, node);
                g.rect(x + 3, y + 11, 2, 2, node);
                g.rect(x + 11, y + 11, 2, 2, node);
                g.rect(x + 7, y + 7, 2, 2, withAlpha(Pal.HAZARD_ARC, 60));

                // 电弧：少量、慢、位置稳定，不闪成噪点
                int hash = (tx * 73856093) ^ (ty * 19349663);
                if ((hash & 7) == 0) {
                    float ph = time * 6f + (hash & 7);
                    float ox = (float) Math.sin(ph) * 1.5f;
                    g.rect(x + 6 + ox, y + 7, 2, 2, withAlpha(Pal.HAZARD_ARC, 170));
                    g.line(x + 4 + ox, y + 10, x + 8 + ox, y + 12, withAlpha(Pal.HAZARD_ARC, 90), 1f);
                }

                // 外沿：3px 亮红 + 1px 近白描边，形成硬边界
                int edge = Pal.HAZARD_RED;
                if (up) {
                    g.rect(x, y, T, 3, edge);
                    g.rect(x, y, T, 1, 0xFFFFB08A);
                }
                if (dn) {
                    g.rect(x, y + T - 3, T, 3, edge);
                    g.rect(x, y + T - 1, T, 1, 0xFF8A1F12);
                }
                if (lf) {
                    g.rect(x, y, 3, T, edge);
                    g.rect(x, y, 1, T, 0xFFFFB08A);
                }
                if (rt) {
                    g.rect(x + T - 3, y, 3, T, edge);
                    g.rect(x + T - 1, y, 1, T, 0xFF8A1F12);
                }
            }
        }
    }

    /** 地面装饰：格栅、瓦砾、锈斑。稀疏摆放，给"平地"一点可记忆的地标。 */
    private void drawGroundDetails(GC g, float camX, float camY) {
        int tx0 = (int) Math.floor(camX / T) - 1;
        int ty0 = (int) Math.floor(camY / T) - 1;
        int tx1 = (int) Math.floor((camX + Const.VW) / T) + 1;
        int ty1 = (int) Math.floor((camY + Const.VH) / T) + 1;
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                int h = (tx * 92821) ^ (ty * 68917);
                int kind = Math.abs(h) % 52;
                if (kind > 2) continue;   // 约 8% 的格子有装饰
                float x = tx * T - camX;
                float y = ty * T - camY;
                if (kind == 0) {
                    // 排水格栅
                    g.rect(x + 1, y + 6, 14, 5, 0xFF1A2024);
                    for (int k = 0; k < 4; k++) {
                        g.rect(x + 2 + k * 3, y + 7, 2, 1, 0xFF232A30);
                    }
                    g.rect(x + 1, y + 5, 14, 1, 0xFF2A3238);
                } else if (kind == 1) {
                    // 瓦砾堆
                    g.rect(x + 5, y + 8, 3, 2, 0xFF39434B);
                    g.rect(x + 8, y + 9, 2, 2, 0xFF2F383F);
                    g.rect(x + 6, y + 10, 4, 1, 0xFF20262B);
                } else {
                    // 锈斑
                    g.rect(x + 3, y + 4, 6, 4, 0x22100B06);
                    g.rect(x + 5, y + 3, 3, 1, 0x22100B06);
                }
            }
        }
    }

    /**
     * 断崖：地图最外圈。深黑深渊 + 内沿钢制护栏 + 稀疏警示灯。
     * 目的是让"走到头"有一个能理解的理由，而不是撞上空气墙。
     */
    private void drawVoid(GC g, TileMap map, float camX, float camY, float time) {
        int tx0 = (int) Math.floor(camX / T) - 1;
        int ty0 = (int) Math.floor(camY / T) - 1;
        int tx1 = (int) Math.floor((camX + Const.VW) / T) + 1;
        int ty1 = (int) Math.floor((camY + Const.VH) / T) + 1;

        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                if (!map.inside(tx, ty) || !map.isVoid(tx, ty)) continue;
                float x = tx * T - camX;
                float y = ty * T - camY;

                g.rect(x, y, T, T, 0xFF05070A);
                // 深渊里极淡的冷光，避免一整片纯黑像没渲染
                int hash = (tx * 73856093) ^ (ty * 19349663);
                if ((hash & 15) == 0) {
                    g.circle(x + 8, y + 8, 3, 0x141E2A34);
                }

                // 只有"贴着实地"的那一侧才画护栏，且必须画在朝实地的那条边上
                boolean below = map.inside(tx, ty + 1) && !map.isVoid(tx, ty + 1);
                boolean above = map.inside(tx, ty - 1) && !map.isVoid(tx, ty - 1);
                boolean right = map.inside(tx + 1, ty) && !map.isVoid(tx + 1, ty);
                boolean left = map.inside(tx - 1, ty) && !map.isVoid(tx - 1, ty);
                int steel = 0xFF4A5762;
                int dark = 0xFF1B242B;
                boolean blink = ((tx + ty + (int) (time * 2f)) & 7) == 0;

                if (below) {
                    g.rect(x, y + T - 5, T, 5, dark);
                    g.rect(x, y + T - 5, T, 2, steel);
                    g.rect(x, y + T - 1, T, 1, 0xFF090C10);
                    if (blink) g.rect(x + 6, y + T - 4, 3, 2, Pal.WARN_YELLOW);
                }
                if (above) {
                    g.rect(x, y, T, 5, dark);
                    g.rect(x, y, T, 2, steel);
                    if (blink) g.rect(x + 6, y + 3, 3, 2, Pal.WARN_YELLOW);
                }
                if (right) {
                    g.rect(x + T - 5, y, 5, T, dark);
                    g.rect(x + T - 5, y, 2, T, steel);
                    g.rect(x + T - 1, y, 1, T, 0xFF090C10);
                }
                if (left) {
                    g.rect(x, y, 5, T, dark);
                    g.rect(x, y, 2, T, steel);
                }
                // 立柱：每 3 格竖一根，制造"护栏"的纵深
                if ((right || left) && (ty & 3) == 0) {
                    float px = right ? x + T - 6 : x + 1;
                    g.rect(px, y + 1, 3, T - 2, 0xFF39434C);
                    g.rect(px, y + 1, 3, 1, steel);
                }
            }
        }
    }

    /** 合成台：深色钢台 + 琥珀色工作灯。靠近时台面亮一档并浮出提示。 */
    private void drawBenches(GC g, Game game, float camX, float camY) {
        for (int i = 0; i < game.world.benches.size(); i++) {
            Bench b = game.world.benches.get(i);
            float x = b.x - camX, y = b.y - camY;
            if (x < -40 || y < -40 || x > Const.VW + 40 || y > Const.VH + 40) continue;
            float near = game.player == null ? 999f
                    : (float) Math.hypot(b.x - game.player.x, b.y - game.player.y);
            boolean inUse = near < Const.BENCH_USE_R;
            boolean showLabel = near < 96f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(game.time * 2.4f + i);

            g.circle(x, y + 6, 14, 0x44000000);
            // 台腿
            g.rect(x - 9, y + 2, 3, 8, 0xFF1A2228);
            g.rect(x + 6, y + 2, 3, 8, 0xFF1A2228);
            // 台面
            g.rect(x - 11, y - 7, 22, 9, Pal.BENCH_DARK);
            g.rect(x - 11, y - 7, 22, 2, Pal.BENCH_STEEL);
            g.frame(x - 11, y - 7, 22, 9, 0xFF0A0E12, 1f);
            // 台面上的工件
            g.rect(x - 5, y - 5, 6, 2, 0xFF7A8791);
            g.rect(x + 1, y - 4, 3, 1, 0xFF7A8791);
            // 工作灯
            int lamp = inUse ? 0xFFFFFFFF : withAlpha(Pal.BENCH_GLOW, (int) (160 + 80 * pulse));
            g.rect(x - 8, y - 11, 2, 4, 0xFF39434C);
            g.circle(x - 7, y - 12, 2f, lamp);
            // 远处的光柱：保证在杂乱地面里也能一眼看到"那里有个设施"
            g.circle(x - 7, y - 12, 10f + 4f * pulse,
                    withAlpha(Pal.BENCH_GLOW, inUse ? 54 : (int) (18 + 16 * pulse)));
            g.rect(x - 7.5f, y - 40f, 1f, 28, withAlpha(Pal.BENCH_GLOW, inUse ? 46 : 26));
            g.circle(x - 7, y - 41f, 1.6f, withAlpha(Pal.BENCH_GLOW, 150));
            if (inUse) {
                g.ring(x, y + 2, 17, withAlpha(Pal.BENCH_GLOW, 130), 1.5f);
            }
            if (showLabel) {
                String tip = inUse ? "合成台 · 点它打开" : "合成台";
                float tw = g.textWidth(tip, 7, false);
                float ly = y - (inUse ? 44f : 34f);
                g.rect(x - tw / 2f - 4, ly, tw + 8, 11, 0xCC0C1116);
                g.text(tip, x, ly + 2, Pal.BENCH_GLOW, 7, false, 1);
            }
        }
    }

    private void drawRocks(GC g, TileMap map, float camX, float camY) {
        int tx0 = (int) Math.floor(camX / T);
        int ty0 = (int) Math.floor(camY / T);
        int tx1 = (int) Math.floor((camX + Const.VW) / T);
        int ty1 = (int) Math.floor((camY + Const.VH) / T);
        int[] mid = ROCK_MID;
        int[] top = ROCK_TOP;
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                if (!map.inside(tx, ty)) continue;
                int id = map.idx(tx, ty);
                byte t = map.tile[id];
                if (t == TileMap.GROUND || t == TileMap.VOID) continue;
                float x = tx * T - camX;
                float y = ty * T - camY;
                int v = map.dark[id] % 3;
                if (t == TileMap.WALL) {
                    // 锈蚀钢板墙：暖褐而非纯黑，避免在暗场景里变成"UI 黑块"
                    if (!map.solid(tx + 1, ty)) g.rect(x + T, y + 3, 3, T - 1, 0x55000000);
                    if (!map.solid(tx, ty + 1)) g.rect(x + 3, y + T, T - 3, 3, 0x55000000);
                    g.rect(x, y, T, T, Pal.WALL_DARK);
                    g.rect(x + 1, y + 2, T - 2, T - 3, Pal.WALL_MID);
                    if (!map.solid(tx, ty - 1)) {
                        g.rect(x, y, T, 3, Pal.WALL_HI);
                        g.rect(x + 2, y + 1, T - 4, 1, 0xFF6B5340);
                    } else {
                        g.rect(x, y, T, 1, 0xFF3B2D24);
                    }
                    if (!map.solid(tx, ty + 1)) g.rect(x + 1, y + T - 2, T - 2, 2, 0xFF120D0A);
                    if (!map.solid(tx - 1, ty)) g.rect(x, y + 2, 2, T - 3, 0xFF150F0C);
                    if (!map.solid(tx + 1, ty)) g.rect(x + T - 2, y + 2, 2, T - 3, 0xFF150F0C);
                    // 铆钉
                    if (((tx * 31 + ty * 17) & 3) == 0) {
                        g.rect(x + 4, y + 6, 2, 2, 0xFF5A4434);
                        g.rect(x + 10, y + 10, 2, 2, 0xFF3E2F24);
                    }
                } else {
                    // 岩石：投影 → 暗底 → 中间调 → 顶面高光，四边压黑
                    g.rect(x + 3, y + 4, T, T, 0x99000000);      // 实投影
                    g.rect(x, y, T, T, Pal.ROCK_DARK);
                    g.rect(x + 1, y + 1, T - 2, T - 2, mid[v]);
                    // 每块都有顶部内高光，成片时才不会糊成一团死黑
                    g.rect(x + 2, y + 2, T - 4, 2, 0xFF3A4751);
                    g.rect(x + 2, y + 4, 5, 1, 0xFF333F48);
                    if (!map.solid(tx, ty - 1)) {
                        g.rect(x + 1, y + 1, T - 2, 3, top[v]);
                        g.rect(x + 3, y + 1, T - 8, 1, 0xFF6C7D8A);
                    }
                    if (!map.solid(tx, ty + 1)) g.rect(x + 1, y + T - 3, T - 2, 3, 0xFF0C1114);
                    if (!map.solid(tx - 1, ty)) g.rect(x + 1, y + 3, 2, T - 6, 0xFF3E4B55);
                    if (!map.solid(tx + 1, ty)) g.rect(x + T - 2, y + 3, 1, T - 6, 0xFF0D1317);
                    // 岩石裂纹
                    if (v == 1 && ((tx * 7 + ty * 13) & 7) == 0) {
                        g.rect(x + 5, y + 6, 5, 1, 0xFF1A2228);
                        g.rect(x + 9, y + 7, 2, 3, 0xFF1A2228);
                    }
                }
            }
        }
    }

    /** 出生点的信标：给这片荒野一个可回望的地标。 */
    private void drawBeacon(GC g, Game game, float camX, float camY) {
        float x = game.world.spawnX - camX;
        float y = game.world.spawnY - camY;
        if (x < -40 || y < -40 || x > Const.VW + 40 || y > Const.VH + 40) return;
        float p = 0.5f + 0.5f * (float) Math.sin(game.time * 2.2f);
        g.circle(x, y + 4, 18, withAlpha(Pal.PLAYER_VISOR, (int) (12 + 10 * p)));
        g.ring(x, y + 4, 13, withAlpha(Pal.PLAYER_VISOR, 40), 1f);
        g.line(x - 8, y + 6, x, y - 9, 0xFF39434C, 2f);
        g.line(x + 8, y + 6, x, y - 9, 0xFF39434C, 2f);
        g.line(x - 9, y + 7, x + 9, y + 7, 0xFF39434C, 2f);
        g.circle(x, y - 10, 2.6f, Pal.PLAYER_VISOR);
        g.circle(x, y - 10, 7, withAlpha(Pal.PLAYER_VISOR, (int) (20 + 30 * p)));
    }

    private void drawNodes(GC g, Game game, float camX, float camY) {
        for (int i = 0; i < game.world.nodes.size(); i++) {
            Node n = game.world.nodes.get(i);
            float x = n.x - camX, y = n.y - camY;
            if (x < -24 || y < -24 || x > Const.VW + 24 || y > Const.VH + 24) continue;
            g.circle(x, y + 5, 6, 0x33000000);
            if (n.taken) {
                g.sprite(ArtData.SCRAP_EMPTY, x - 8, y - 11);
            } else {
                Spr s = ArtData.SCRAP;
                if (n.flash > 0f) s = s.tint(Pal.WHITE, 0.6f);
                g.sprite(s, x - 8, y - 11 + (float) Math.sin(n.t * 1.6f) * 0.5f);
            }
        }
    }

    private void drawBatteries(GC g, Game game, float camX, float camY) {
        for (int i = 0; i < game.world.batteries.size(); i++) {
            Battery b = game.world.batteries.get(i);
            if (b.taken) continue;
            float x = b.x - camX, y = b.y - camY;
            if (x < -20 || y < -20 || x > Const.VW + 20 || y > Const.VH + 20) continue;
            float pulse = 0.5f + 0.5f * (float) Math.sin(game.time * 4f + b.t);
            g.circle(x, y, b.big ? 11 : 8, withAlpha(Pal.ENERGY, (int) (30 + 30 * pulse)));
            g.circle(x, y + 5, 5, 0x33000000);
            g.sprite(ArtData.BATTERY, x - 4, y - 8 + b.bob());
        }
    }

    private void drawLoot(GC g, Game game, float camX, float camY) {
        for (int i = 0; i < game.world.loots.size(); i++) {
            Loot l = game.world.loots.get(i);
            if (l.taken) continue;
            float x = l.x - camX, y = l.y - camY;
            if (x < -20 || y < -20 || x > Const.VW + 20 || y > Const.VH + 20) continue;
            float pulse = 0.5f + 0.5f * (float) Math.sin(game.time * 3.5f + l.t);
            float bob = l.bob();
            g.circle(x, y, 10, withAlpha(l.item.color(), (int) (26 + 34 * pulse)));
            g.circle(x, y + 5, 5, 0x33000000);
            g.frame(x - 7, y - 8 + bob, 14, 14, withAlpha(l.item.color(), 170), 1f);
            drawItemIcon(g, l.item.slot, l.item.tier, x, y - 1 + bob, 13f);
        }
    }

    private void drawEnemies(GC g, Game game, float camX, float camY) {
        for (int i = 0; i < game.world.enemies.size(); i++) {
            Enemy e = game.world.enemies.get(i);
            if (!e.alive) continue;
            float x = e.x - camX, y = e.y - camY;
            if (x < -30 || y < -30 || x > Const.VW + 30 || y > Const.VH + 30) continue;

            g.circle(x, y + 5, e.boss ? 16 : e.elite() ? 8 : 6, 0x40000000);
            if (e.boss) {
                // Boss 底下一圈缓慢旋转的红色警戒环，远远就能认出"这里不是普通怪"
                float pr = 0.5f + 0.5f * (float) Math.sin(game.time * 2.4f);
                g.ring(x, y + 4, 30f + pr * 2f, withAlpha(0xFFE8453F, 60), 1.5f);
                g.ring(x, y + 4, 26f, withAlpha(0xFFFFD166, 34), 1f);
            }
            if (e.type == Enemy.TURRET) {
                // 炮管单独画，跟着玩家转
                g.line(x, y - 3, x + e.aimX * 12f, y - 3 + e.aimY * 12f, 0xFF1A1F24, 4f);
                g.line(x, y - 3, x + e.aimX * 11f, y - 3 + e.aimY * 11f, 0xFF5E6B75, 2f);
                g.circle(x + e.aimX * 11f, y - 3 + e.aimY * 11f, 1.6f, Pal.E_TURRET);
            }
            if (e.windingUp()) {
                float p = 0.5f + 0.5f * (float) Math.sin(game.time * 26f);
                g.ring(x, y - 2, 11, withAlpha(Pal.HAZARD_RED, (int) (120 + 90 * p)), 2f);
            }
            // 自爆虫：引信点燃后红圈从 30 收到 8，配合闪烁告诉玩家"快跑"
            if (e.armed()) {
                float q = e.fuseQ();
                float r = 8f + 24f * q;
                float blink = 0.5f + 0.5f * (float) Math.sin(game.time * 44f);
                g.ring(x, y + 2, r, withAlpha(Pal.HAZARD_RED, (int) (120 + 110 * blink)), 2f);
                g.circle(x, y + 2, Const.BOMB_RADIUS, withAlpha(Pal.HAZARD_RED, 18));
                if (blink > 0.6f) g.circle(x, y - 4, 12, 0x33FFFFFF);
            }
            // 跳虫：蹲伏时地面出现落点预告，腾空时影子缩小
            if (e.crouching()) {
                g.ring(x, y + 5, 10, withAlpha(Pal.E_LEAPER, 150), 1.5f);
                g.arc(x, y + 5, 10, 200, 140, withAlpha(Pal.WARN_YELLOW, 90), 1f);
            }
            if (e.airborne()) {
                g.circle(x - e.aimX * 4f, y + 6, 7, 0x30000000);
            }
            Spr s = spriteFor(e.type);
            if (e.flash > 0f) s = s.tint(Pal.WHITE, 0.75f);
            if (e.boss) {
                float sc = 1.9f;
                g.sprite(s, x - s.w * sc / 2f, y - 12f - s.h * (sc - 1f) * 0.5f, sc);
                // 头顶两角，读起来是"守卫长"而不是普通守卫
                g.line(x - 5f, y - 21f, x - 8f, y - 27f, 0xFFFFD166, 1.6f);
                g.line(x + 5f, y - 21f, x + 8f, y - 27f, 0xFFFFD166, 1.6f);
            } else {
                g.sprite(s, x - s.w / 2f, y - 12);
            }

            // 盾卫：正面一块弧形钢盾，画在身体之上，挡住时闪白 —— 一眼就知道要绕后
            if (e.shielded()) {
                float a = e.shieldAngle();
                int sc = e.blockFlash > 0f ? Pal.WHITE : Pal.E_SHIELDER;
                g.arc(x, y - 1, 13f, a - 60f, 120f, withAlpha(0xFF0A0E12, 235), 7f);
                g.arc(x, y - 1, 13f, a - 58f, 116f, sc, 4f);
                g.arc(x, y - 1, 12.5f, a - 50f, 100f, withAlpha(0xFFFFFFFF, 70), 1f);
                // 盾面铆钉
                for (int k = -1; k <= 1; k++) {
                    double ra = Math.toRadians(a + k * 26f);
                    g.rect(x + (float) Math.cos(ra) * 13f - 1f,
                            y - 1 + (float) Math.sin(ra) * 13f - 1f, 2f, 2f, 0xFF24313B);
                }
            }

            if (e.hp < e.maxHp && !e.boss) {
                float w = e.elite() ? 20f : 14f;
                float hx = x - w / 2f, hy = y - (e.elite() ? 24f : 21f);
                g.rect(hx - 1, hy - 1, w + 2, 4, 0xAA000000);
                g.rect(hx, hy, w * e.hp / (float) e.maxHp, 2,
                        e.elite() ? Pal.E_WARDEN_EYE : e.color());
            }
        }
    }

    private Spr spriteFor(int type) {
        switch (type) {
            case Enemy.DRONE: return ArtData.DRONE;
            case Enemy.TURRET: return ArtData.TURRET;
            case Enemy.CHARGER: return ArtData.CHARGER;
            case Enemy.WARDEN: return ArtData.WARDEN;
            case Enemy.BOMBER: return ArtData.BOMBER;
            case Enemy.SHIELDER: return ArtData.SHIELDER;
            case Enemy.SPITTER: return ArtData.SPITTER;
            case Enemy.LEAPER: return ArtData.LEAPER;
            default: return ArtData.CRAWLER;
        }
    }

    private void drawPlayer(GC g, Game game, float camX, float camY) {
        Player p = game.player;
        float x = p.x - camX, y = p.y - camY;

        // 冲刺残影
        if (p.dashT > 0f) {
            for (int i = 1; i <= 3; i++) {
                float bx = x - p.dashDirX * i * 7f;
                float by = y - p.dashDirY * i * 7f;
                g.circle(bx, by - 3, 5 - i, withAlpha(Pal.CYAN, 70 - i * 18));
            }
        }

        g.circle(x, y + 4, 12, withAlpha(Pal.PLAYER_VISOR, 26));
        g.circle(x, y + 4, 8, withAlpha(Pal.PLAYER_VISOR, 44));
        g.circle(x, y + 5, 6, 0x55000000);
        float lunge = 0f;
        if (p.atkT > 0f) {
            float q = 1f - p.atkT / Math.max(0.01f, game.atkAnim());
            lunge = (float) Math.sin(Math.min(1f, q) * Math.PI) * 3f;
        }
        boolean blink = p.hurtFlash > 0f && ((int) (p.hurtFlash * 26f) % 2 == 0);
        // 朝上时武器先画（被身体挡住一半），其它方向后画，形成"握在手里"的层次
        boolean weaponBehind = p.faceY < -0.25f;
        float wx = x + p.faceX * lunge, wy = y + p.faceY * lunge;
        if (weaponBehind) drawHandWeapon(g, game, p, wx, wy);
        if (!blink) {
            Spr s = playerSprite(p);
            if (p.hurtFlash > 0f) s = s.tint(0xFFFF6A50, 0.62f);
            float bob = (float) Math.sin(p.walkT * 6.28f) * 0.8f;
            g.sprite(s, x - s.w / 2f + p.faceX * lunge, y - 12 + bob + p.faceY * lunge);
            // 朝向指示：面朝方向的一颗冷光点
            g.circle(x + p.faceX * 7f, y - 4 + p.faceY * 5f, 1.6f, Pal.PLAYER_VISOR);
        }
        if (!weaponBehind) drawHandWeapon(g, game, p, wx, wy);

        // 招架护盾：六边形能量罩（不是圆环——圆环看起来像水波，六边形才像"盾"）
        if (p.guard > 0f) {
            float q = Game.clamp(p.guard / Const.GUARD_T, 0f, 1f);
            float r = 15f;
            int a = (int) (55 + 130 * q);
            float cy = y - 3;
            for (int k = 0; k < 6; k++) {
                double a1 = Math.PI * 2 * k / 6 - Math.PI / 2;
                double a2 = Math.PI * 2 * (k + 1) / 6 - Math.PI / 2;
                g.line(x + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r,
                        x + (float) Math.cos(a2) * r, cy + (float) Math.sin(a2) * r,
                        withAlpha(Pal.GUARD, a), 1.5f);
            }
            // 剩余时间：护盾外圈从满到空
            g.arc(x, cy, r + 4f, -90f, 360f * q, withAlpha(Pal.GUARD, Math.min(255, a + 50)), 1.5f);
            if (p.guardFlash > 0f) {
                float f = p.guardFlash / Const.GUARD_FLASH;
                g.ring(x, cy, r + 9f * (1f - f), withAlpha(Pal.WHITE, (int) (200 * f)), 2f);
            }
        }

        // 庇护：外圈一道蓝色进度环，充满后变亮——不用看 HUD 也知道盾好了没
        if (game.perks[Perk.AEGIS] > 0) {
            float prog = game.aegisProgress();
            boolean ready = game.aegisReady();
            float r = 19f;
            int col = ready ? 0xFF9CC2FF : 0xFF45596E;
            g.arc(x, y - 3, r, -90f, Math.max(4f, 360f * prog),
                    withAlpha(col, ready ? 170 : 110), ready ? 1.6f : 1.1f);
            if (ready) {
                // 就绪时环变亮，再补四个小刻度；不要圆点，圆点会看成"眼睛"
                for (int k = 0; k < 4; k++) {
                    double a2 = Math.PI / 2 * k + Math.PI / 4;
                    float px2 = x + (float) Math.cos(a2) * r;
                    float py2 = (y - 3) + (float) Math.sin(a2) * r;
                    g.rect(px2 - 1f, py2 - 1f, 2f, 2f, 0xFFDCEBFF);
                }
            }
            if (game.aegisFlash > 0f) {
                float f = Game.clamp(game.aegisFlash / 0.7f, 0f, 1f);
                g.ring(x, y - 3, 19f + 11f * (1f - f), withAlpha(0xFFD6E6FF, (int) (210 * f)), 2f);
            }
        }

        // 闪避成功：一道很快的绿色残影环，告诉玩家"这一下不是白挨的"
        if (game.dodgeFlash > 0f) {
            float f = Game.clamp(game.dodgeFlash / 0.45f, 0f, 1f);
            g.ring(x, y - 3, 13f + 9f * (1f - f), withAlpha(0xFF6FE3A6, (int) (180 * f)), 1.6f);
        }

        // 挥砍：一道从起手扫到收势的刀光，带拖尾和刀锋线
        if (p.atkT > 0f) {
            float q = Game.clamp(1f - p.atkT / Math.max(0.01f, game.atkAnim()), 0f, 1f);
            float ease = q * q * (3f - 2f * q);            // smoothstep，起手慢、中间快
            float range = game.atkRange();
            int wtype = game.weaponType();
            float base = (float) Math.toDegrees(p.atkAng);
            float half = game.atkArc() * 57.2958f;         // 判定角 = 刀光角，不再有"擦到不掉血"
            float dir = p.atkDir >= 0f ? 1f : -1f;
            float start = base - half * dir;
            float cur = start + half * 2f * dir * ease;
            float span = cur - start;
            float cy = y - 2f;
            float heft = wtype == Weapon.AXE ? 1.6f : wtype == Weapon.KATANA ? 0.8f : 1f;

            // 拖尾：整段一次画完，再叠两段更亮的"刀锋附近"。
            // 之前按段画，段与段的圆头端点会在接缝处堆出一个个亮点，像一串珠子。
            float rm = game.rangeMul();
            float th = (2.0f + 1.2f * rm) * heft;
            float a0 = start;
            float sweep = cur - a0;
            g.arc(x, cy, range - 1f, a0, sweep, withAlpha(0xFFEAF7FF, 62), th);
            g.arc(x, cy, range - 1f, cur, -sweep * 0.55f, withAlpha(0xFFEAF7FF, 132), th * 0.92f);
            g.arc(x, cy, range - 1f, cur, -sweep * 0.24f, withAlpha(0xFFFFFFFF, 205), th * 0.72f);
            // 内侧薄雾：很淡、很细，只用来补体积感，不能抢刀锋的读数
            float fade = 0.45f + 0.55f * (1f - q);
            g.arc(x, cy, range * 0.52f, start, span, withAlpha(Pal.CYAN, (int) (16 * fade)), 1f + rm * 0.4f);

            // 刀锋：从身前 7px 一直拉到攻击距离
            double cr = Math.toRadians(cur);
            float fx = (float) Math.cos(cr), fy = (float) Math.sin(cr);
            // 刀锋：一条细亮线 + 一圈很淡的冷光。线宽不能跟着距离猛涨，
            // 否则远距离看起来像一根激光棒，而不是"刀扫过去的边"。
            g.line(x + fx * 7f, cy + fy * 7f, x + fx * range, cy + fy * range,
                    withAlpha(Pal.CYAN, 70), (2.2f + 0.5f * rm) * heft);
            g.line(x + fx * 7f, cy + fy * 7f, x + fx * range, cy + fy * range,
                    withAlpha(0xFFFFFFFF, 240), (1.1f + 0.2f * rm) * heft);

            // 长枪：额外一条"刺出去"的直线，读起来是突刺而不是横扫
            if (wtype == Weapon.SPEAR) {
                g.line(x + fx * 8f, cy + fy * 8f, x + fx * (range + 5f), cy + fy * (range + 5f),
                        withAlpha(0xFFFFFFFF, 170), 1.2f);
            }

            // 外圈余辉
            if (span > 1f) {
                g.arc(x, cy, range + 1f, start, span,
                        withAlpha(Pal.CYAN, (int) (20 + 80f * (1f - q))), 1f);
            }
        }
    }

    /**
     * 手持武器。待机时斜挎在身侧，挥砍时跟着刀光一起扫。
     * v8 起几何形状由**类型**决定（换类型一眼看出来），颜色/光效由品阶决定：
     *   0 太刀（细长弯刃） 1 重斧（大斧头） 2 长枪（长杆 + 枪尖） 3 双刃（主副两把）
     */
    private void drawHandWeapon(GC g, Game game, Player p, float x, float y) {
        Item w = game.gear(Item.ATK);
        int tier = w == null ? 0 : w.tier;
        int wtype = w == null ? Weapon.KATANA : w.type;
        boolean swinging = p.atkT > 0f;
        float ang;
        float reach;
        if (swinging) {
            float q = Game.clamp(1f - p.atkT / Math.max(0.01f, game.atkAnim()), 0f, 1f);
            float ease = q * q * (3f - 2f * q);
            float base = (float) Math.toDegrees(p.atkAng);
            float half = game.atkArc() * 57.2958f;
            float dir = p.atkDir >= 0f ? 1f : -1f;
            ang = (float) Math.toRadians(base - half * dir + half * 2f * dir * ease);
            reach = game.atkRange() - 3f;
        } else {
            ang = (float) Math.atan2(p.faceY, p.faceX) + 0.95f;
            reach = 11f + tier * 1.4f + (wtype == Weapon.SPEAR ? 6f : wtype == Weapon.AXE ? 3f : 0f);
        }
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        // 手在身体右前侧，而不是正中间——否则握柄会横穿胸口
        float rx = -p.faceY, ry = p.faceX;
        float hx = x + p.faceX * 3f + rx * 4f;
        float hy = y - 5f + p.faceY * 3f + ry * 3f;
        float ex = hx + ca * reach, ey = hy + sa * reach;
        float gr = Math.min(reach * 0.34f, 9f);
        float bx = hx + ca * gr, by = hy + sa * gr;

        int dark = 0xFF0C1013;
        int steel = 0xFF9AA8B4;
        int grip = 0xFF6B543C;
        int tint = Item.TIER_COLOR[tier];
        boolean glow = tier >= Item.ORANGE;

        // 握柄 + 手
        float gripW = wtype == Weapon.AXE ? 4.0f : wtype == Weapon.SPEAR ? 3.2f : 3.4f;
        g.line(hx - ca * 2f, hy - sa * 2f, bx, by, dark, gripW);
        g.line(hx - ca * 2f, hy - sa * 2f, bx, by, grip, gripW * 0.52f);
        g.circle(hx, hy, 1.5f, 0xFFB6C2CC);

        switch (wtype) {
            case Weapon.AXE: {
                // 重斧：短粗柄 + 一片梯形斧刃（四条边围出块面，不是两根线交叉成箭头）
                float hx2 = bx + ca * (reach * 0.40f), hy2 = by + sa * (reach * 0.40f);
                g.line(bx, by, hx2, hy2, dark, 5.0f);
                g.line(bx, by, hx2, hy2, steel, 2.6f);
                float hw = 5.0f + tier * 0.4f;          // 斧刃半宽
                float hl = reach * 0.42f;               // 斧刃长度
                float x1 = hx2 + sa * hw, y1 = hy2 - ca * hw;
                float x2 = hx2 - sa * hw, y2 = hy2 + ca * hw;
                float x3 = x2 + ca * hl - sa * hw * 1.3f, y3 = y2 + sa * hl + ca * hw * 1.3f;
                float x4 = x1 + ca * hl - sa * hw * 1.3f, y4 = y1 + sa * hl + ca * hw * 1.3f;
                // 块面：先深色描边，再钢色填充边
                g.line((x1 + x2) / 2f, (y1 + y2) / 2f, (x4 + x3) / 2f, (y4 + y3) / 2f,
                        0xFF454F59, hw * 1.5f);
                g.line(x1, y1, x4, y4, dark, 3.2f);
                g.line(x2, y2, x3, y3, dark, 3.2f);
                g.line(x1, y1, x2, y2, dark, 3.0f);
                g.line(x1, y1, x4, y4, steel, 1.8f);
                g.line(x2, y2, x3, y3, 0xFF6C7A86, 1.8f);
                g.line(x1, y1, x2, y2, steel, 1.4f);
                // 斧刃外沿：一条比刀更粗更钝的亮线
                g.line(x4, y4, x3, y3, steel, 2.6f);
                if (glow) g.line(x4, y4, x3, y3, withAlpha(tint, 150), 1.4f);
                break;
            }
            case Weapon.SPEAR: {
                // 长枪：一根细长杆 + 菱形枪尖
                g.line(bx, by, ex - ca * 7f, ey - sa * 7f, dark, 3.0f);
                g.line(bx, by, ex - ca * 7f, ey - sa * 7f, steel, 1.5f);
                // 枪尖：两个三角
                g.line(ex - ca * 8f - sa * 2.6f, ey - sa * 8f + ca * 2.6f, ex, ey, dark, 3.2f);
                g.line(ex - ca * 8f + sa * 2.6f, ey - sa * 8f - ca * 2.6f, ex, ey, dark, 3.2f);
                g.line(ex - ca * 8f - sa * 2.2f, ey - sa * 8f + ca * 2.2f, ex, ey, steel, 2.0f);
                g.line(ex - ca * 8f + sa * 2.2f, ey - sa * 8f - ca * 2.2f, ex, ey, steel, 2.0f);
                if (glow) g.line(ex - ca * 8f, ey - sa * 8f, ex, ey, withAlpha(tint, 170), 1.2f);
                break;
            }
            case Weapon.TWIN: {
                // 双刃：主手长刃 + 副手短刃反手交叉，两把刀一眼分得开
                taper(g, bx, by, ex, ey, dark, 4.0f, 1.0f);
                taper(g, bx, by, ex, ey, steel, 2.4f, 0.7f);
                if (glow) taper(g, bx, by, ex, ey, withAlpha(tint, 130), 1.4f, 0.5f);
                float a2 = ang - 0.62f;
                float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                float len2 = reach * 0.60f;
                float ox = hx - sa * 3f, oy = hy + ca * 3f;
                taper(g, ox, oy, ox + c2 * len2, oy + s2 * len2, dark, 3.6f, 0.9f);
                taper(g, ox, oy, ox + c2 * len2, oy + s2 * len2, 0xFF8A99A5, 2.1f, 0.6f);
                break;
            }
            default: {
                // 太刀：细长弯刃，刀背略亮一条；高阶再叠冷光
                float midx = bx + ca * (reach * 0.5f) - sa * 2.2f;
                float midy = by + sa * (reach * 0.5f) + ca * 2.2f;
                taper(g, bx, by, midx, midy, dark, 3.8f, 2.8f);
                taper(g, midx, midy, ex, ey, dark, 2.8f, 0.7f);
                taper(g, bx, by, midx, midy, steel, 2.4f, 1.8f);
                taper(g, midx, midy, ex, ey, steel, 1.8f, 0.5f);
                // 刀背高光：一条细白线，让"刃"有正反面的区别
                g.line(bx - sa * 1.4f, by + ca * 1.4f, ex - sa * 0.4f, ey + ca * 0.4f,
                        withAlpha(0xFFFFFFFF, 90), 0.7f);
                // 护手：一小横，读起来像刀而不是铁条
                float gx2 = bx + ca * 2.4f, gy2 = by + sa * 2.4f;
                g.line(gx2 - sa * 3.4f, gy2 + ca * 3.4f, gx2 + sa * 3.4f, gy2 - ca * 3.4f, dark, 2.4f);
                g.line(gx2 - sa * 3f, gy2 + ca * 3f, gx2 + sa * 3f, gy2 - ca * 3f, steel, 1.2f);
                if (glow) taper(g, bx, by, ex, ey, withAlpha(tint, 120), 1.6f, 0.5f);
                break;
            }
        }

        if (swinging && glow) {
            // 挥到哪，刀尖拖一小段冷光（只有高阶武器才发光）
            g.circle(ex, ey, 1.8f, withAlpha(tint, 160));
        }
    }

    /** 从宽到窄画一条刃：分 6 段收细，尖端才像"刀尖"而不是圆头棍子。 */
    private void taper(GC g, float x0, float y0, float x1, float y1,
                       int color, float w0, float w1) {
        final int n = 6;
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n;
            float ax = x0 + (x1 - x0) * t0, ay = y0 + (y1 - y0) * t0;
            float bx = x0 + (x1 - x0) * t1, by = y0 + (y1 - y0) * t1;
            float w = w0 + (w1 - w0) * t1;
            if (w < 0.5f) w = 0.5f;
            g.line(ax, ay, bx, by, color, w);
        }
    }

    private Spr playerSprite(Player p) {
        float fx = p.faceX, fy = p.faceY;
        if (Math.abs(fx) > Math.abs(fy) * 1.1f) {
            return fx > 0 ? ArtData.PLAYER_SIDE : ArtData.PLAYER_SIDE_L;
        }
        return fy < 0 ? ArtData.PLAYER_UP : ArtData.PLAYER_DOWN;
    }

    private void drawPlasmas(GC g, Game game, float camX, float camY) {
        for (int i = 0; i < game.world.plasmas.size(); i++) {
            Plasma p = game.world.plasmas.get(i);
            float x = p.x - camX, y = p.y - camY;
            if (p.friendly) {
                // 反弹回来的弹丸：冷白 + 青，加一小段拖尾，飞行方向一眼能读
                g.line(x - p.vx * 0.022f, y - p.vy * 0.022f, x, y, 0x447FD4FF, 2.4f);
                g.circle(x, y, 5f, 0x557FD4FF);
                g.circle(x, y, 2.8f, 0xFFCFE9FF);
                g.circle(x, y, 1.2f, 0xFFFFFFFF);
            } else if (p.acid) {
                g.circle(x, y, 5f, 0x4A4FD1A5);
                g.circle(x, y, 2.6f, 0xFF8FF0C8);
                g.circle(x, y, 1.2f, 0xFFE8FFF4);
            } else {
                g.circle(x, y, 4.5f, 0x55FF7A3C);
                g.circle(x, y, 2.4f, 0xFFFFB070);
                g.circle(x, y, 1.2f, 0xFFFFFFFF);
            }
        }
    }

    private void drawParticles(GC g, Game game, float camX, float camY) {
        for (int i = 0; i < game.particles.size(); i++) {
            Particle p = game.particles.get(i);
            float a = p.alpha();
            if (a <= 0.02f) continue;
            g.rect(p.x - camX, p.y - camY, p.size, p.size, withAlpha(p.color, (int) (a * 220)));
        }
    }

    // ============================================================ HUD

    private void drawHud(GC g, Game game) {
        float top = game.uiTop;

        // ---- 左上状态卡：LV / 能量 / 数字血量 / 废铁 / 幸运 ----
        float cx0 = 6f, cy0 = top + 5f, cw = 154f, ch = 48f;
        g.rect(cx0 + 1, cy0 + 2, cw, ch, 0x55000000);
        g.rect(cx0, cy0, cw, ch, 0xC40C1116);
        g.frame(cx0, cy0, cw, ch, 0x5525323B, 1f);

        g.text("LV " + game.level, cx0 + 6, cy0 + 3, Pal.UI_TEXT, 10, true, 0);

        float ex = cx0 + 44, ey = cy0 + 8, ew = 64, eh = 4;
        g.rect(ex, ey, ew, eh, 0xFF0A0E12);
        float ratio = Game.clamp(game.energy / (float) game.needNow(), 0f, 1f);
        g.rect(ex, ey, ew * ratio, eh, Pal.ENERGY);
        if (ratio > 0f) g.rect(ex, ey, ew * ratio, 1, 0xAFFFFFFF);
        g.frame(ex - 1, ey - 1, ew + 2, eh + 2, 0x662B3742, 1f);
        g.text(game.energy + "/" + game.needNow(), cx0 + cw - 5, cy0 + 3,
                Pal.UI_TEXT_DIM, 7, false, 2);

        // 生命：数字制。血条 + 居中数字，比 14 颗心直观得多
        float hx = cx0 + 6, hy = cy0 + 18, hw = cw - 12f, hh = 9f;
        int maxHp = game.maxHp();
        float hr = Game.clamp(game.player.hp / (float) maxHp, 0f, 1f);
        g.rect(hx, hy, hw, hh, 0xFF1A0E11);
        g.rect(hx, hy, hw * hr, hh, hr > 0.3f ? 0xFFE8453F : 0xFFF0544A);
        if (hr > 0f) g.rect(hx, hy, hw * hr, 1, 0x55FFFFFF);
        g.frame(hx, hy, hw, hh, 0x663B2228, 1f);
        String hpTxt = game.player.hp + "/" + maxHp;
        g.text(hpTxt, hx + hw / 2f, hy + 1, 0xFFFFFFFF, 7, true, 1);

        // 第三行：废铁 / 幸运 / 格挡
        g.text("废铁 " + game.bag.scrap, cx0 + 6, cy0 + 32, Pal.SCRAP, 7, false, 0);

        boolean luckOn = game.level >= Const.LUCK_LV;
        int luckCol = luckOn ? 0xFFF2C14E : 0xFF4A5762;
        g.text("幸运 " + game.luckText(), cx0 + 48, cy0 + 32, luckCol, 7, luckOn, 0);

        boolean guardOn = game.player.guard > 0f;
        float gx = cx0 + cw - 62f;
        if (guardOn) {
            float q = Game.clamp(game.player.guard / Math.max(0.01f, game.guardTime()), 0f, 1f);
            shield(g, gx, cy0 + 33, Pal.GUARD);
            g.rect(gx + 12, cy0 + 36, 26, 2, 0x66000000);
            g.rect(gx + 12, cy0 + 36, 26 * q, 2, Pal.GUARD);
        } else {
            shield(g, gx, cy0 + 33, 0xFF39454F);
            g.rect(gx + 12, cy0 + 36, 26, 2, 0x33222C34);
        }

        // 庇护：第三行最右侧一个小盾 + 充能条，和招架盾分开显示
        if (game.perks[Perk.AEGIS] > 0) {
            boolean ready = game.aegisReady();
            float ax = cx0 + cw - 20f;
            shield(g, ax, cy0 + 33, ready ? 0xFF8FB4FF : 0xFF39454F);
            g.rect(ax, cy0 + 43, 10, 2, 0x66000000);
            g.rect(ax, cy0 + 43, 10f * game.aegisProgress(), 2,
                    ready ? 0xFFBFD8FF : 0xFF4E6C8A);
        }

        // 武器条：类型 + 节奏 + 距离。换武器后这三个数会立刻变，玩家能"读"出手感
        Item wp = game.gear(Item.ATK);
        if (wp != null) {
            float wy = cy0 + ch + 3f;
            String s = Weapon.NAME[wp.type] + "  " + String.format(java.util.Locale.US,
                    "%.2fs · %.0fpx", game.atkCd(), game.atkRange());
            float sw = g.textWidth(s, 7, false) + 10f;
            g.rect(cx0, wy, sw, 11f, 0xB20C1116);
            g.rect(cx0, wy, 2f, 11f, wp.color());
            g.text(s, cx0 + 5, wy + 1.5f, 0xFFB9C6D1, 7, false, 0);
        }

        // Boss 血条（v9）：挪到屏幕底部中间。
        // 之前放在状态卡下方，正好压在战斗视野中央，打 Boss 时最要紧的那块看不见。
        // 现在贴着可见区底边，宽度收窄，上方只留一行小字，既不挡人也不挡弹。
        Enemy boss = game.bossFocus;
        if (boss != null && boss.alive && game.bossBarT > 0f) {
            int a = (int) (255 * Game.clamp(game.bossBarT / 0.6f, 0f, 1f));
            float bw = Math.min(196f, Const.VW * 0.42f);
            float bx = (Const.VW - bw) / 2f;
            float by = game.uiBottom - 15f;
            g.rect(bx - 8, by - 16, bw + 16, 24, withAlpha(0x94050A0E, a));
            g.rect(bx, by, bw, 5f, withAlpha(0xFF2A1216, a));
            float br = Game.clamp(boss.hp / (float) Math.max(1, boss.maxHp), 0f, 1f);
            g.rect(bx, by, bw * br, 5f, withAlpha(0xFFE8453F, a));
            if (br > 0f) g.rect(bx, by, bw * br, 1.4f, withAlpha(0x99FFD9D0, a));
            // 名字在左、数字在右，字号压到 6.5，尽量少占地方
            g.text("守卫 · " + World.zoneName(boss.zone), bx, by - 12f,
                    withAlpha(0xFFFFE0A8, a), 6.5f, true, 0);
            g.text(boss.hp + "/" + boss.maxHp, bx + bw, by - 12f,
                    withAlpha(0xFFC7D3DD, a), 6.5f, false, 2);
        }

        // 开局操作提示：放在画面上三分之一，不压摇杆和攻击键
        boolean bossBarUp = game.bossFocus != null && game.bossFocus.alive && game.bossBarT > 0f;
        if (game.hintT > 0f && game.state == Game.State.PLAY && !game.customizing && !bossBarUp) {
            int a = (int) (210 * Math.min(1f, game.hintT / 1.5f));
            String hint = "左侧拖动移动　右下攻击 / 冲刺　·　橙色合成台可造装备";
            float w = g.textWidth(hint, 8, false);
            float y = game.uiBottom - 21f;
            g.rect(Const.VW / 2f - w / 2f - 7, y - 3, w + 14, 14, withAlpha(0x9905080A, a));
            g.text(hint, Const.VW / 2f, y, withAlpha(Pal.UI_TEXT, a), 8, false, 1);
        }

        // 摇杆
        if (game.state == Game.State.PLAY) {
            drawJoystick(g, game);
        }

        // 冲刺冷却
        if (game.player.dashCd > 0f) {
            Ui.Btn dash = game.ui.byId(Game.B_DASH);
            if (dash != null) {
                float r = dash.w / 2f;
                float p = 1f - game.player.dashCd / game.dashCd();
                g.arc(dash.cx(), dash.cy(), r + 3f, -90f, 360f * Game.clamp(p, 0f, 1f), Pal.CYAN, 2f);
            }
        }
    }

    /**
     * 装备图标：用几何图形画出四类装备，不再用"武/甲/速/冲"四个字。
     * 颜色跟随品阶，所以背包里一眼能看出哪件更好。
     */
    public static void drawItemIcon(GC g, int slot, int tier, float cx, float cy, float size) {
        float s = size * 0.5f;
        int tierCol = Item.TIER_COLOR[tier];
        int steel = 0xFFB6C2CC;
        int steelDark = 0xFF39434C;
        int dark = 0xFF0C1013;

        if (tier >= Item.ORANGE) {
            g.circle(cx, cy, s * 1.05f, withAlpha(tierCol, tier == Item.RED ? 46 : 26));
        }

        switch (slot) {
            case Item.ATK: {
                float d = s * 0.62f;
                // 刃
                g.line(cx - d, cy + d, cx + d, cy - d, dark, s * 0.44f);
                g.line(cx - d, cy + d, cx + d, cy - d, steel, s * 0.22f);
                g.line(cx + d * 0.05f, cy - d * 0.05f, cx + d, cy - d, tierCol, s * 0.16f);
                // 护手与柄
                g.line(cx + d * 0.05f, cy - d * 0.45f, cx - d * 0.45f, cy + d * 0.05f, steelDark, s * 0.2f);
                g.line(cx - d * 0.4f, cy + d * 0.4f, cx - d, cy + d, 0xFF6B543C, s * 0.26f);
                break;
            }
            case Item.HP:
                // 盾形：上宽下尖，竖着一道品阶色中脊 —— 小尺寸下也能一眼认出不是"方块"
                for (int i = 0; i < 6; i++) {
                    float t = i / 5f;
                    float w = s * (1.34f - 0.82f * t);
                    float yy = cy - s * 0.66f + s * 1.32f * t;
                    g.rect(cx - w / 2f, yy, w, s * 0.19f, i == 0 ? steel : steelDark);
                }
                g.rect(cx - s * 0.42f, cy - s * 0.5f, s * 0.84f, s * 0.72f, steel);
                g.rect(cx - s * 0.08f, cy - s * 0.5f, s * 0.16f, s * 0.86f, tierCol);
                g.rect(cx - s * 0.66f, cy - s * 0.6f, s * 0.24f, s * 0.34f, steelDark);
                g.rect(cx + s * 0.42f, cy - s * 0.6f, s * 0.24f, s * 0.34f, steelDark);
                break;
            case Item.SPD:
                // 伺服齿轮
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i;
                    g.rect(cx + (float) Math.cos(a) * s * 0.62f - s * 0.11f,
                            cy + (float) Math.sin(a) * s * 0.62f - s * 0.11f,
                            s * 0.22f, s * 0.22f, tierCol);
                }
                g.circle(cx, cy, s * 0.6f, dark);
                g.circle(cx, cy, s * 0.5f, steel);
                g.circle(cx, cy, s * 0.2f, dark);
                g.circle(cx, cy, s * 0.1f, tierCol);
                break;
            default:
                // 线圈：闪电
                g.line(cx + s * 0.34f, cy - s * 0.72f, cx - s * 0.22f, cy + s * 0.02f, dark, s * 0.36f);
                g.line(cx - s * 0.22f, cy + s * 0.02f, cx + s * 0.32f, cy + s * 0.02f, dark, s * 0.36f);
                g.line(cx + s * 0.32f, cy + s * 0.02f, cx - s * 0.34f, cy + s * 0.8f, dark, s * 0.36f);
                g.line(cx + s * 0.34f, cy - s * 0.72f, cx - s * 0.22f, cy + s * 0.02f, tierCol, s * 0.18f);
                g.line(cx - s * 0.22f, cy + s * 0.02f, cx + s * 0.32f, cy + s * 0.02f, tierCol, s * 0.18f);
                g.line(cx + s * 0.32f, cy + s * 0.02f, cx - s * 0.34f, cy + s * 0.8f, tierCol, s * 0.18f);
                break;
        }
    }

    /** 实心小盾：HUD 上的招架状态标记。 */
    private void shield(GC g, float x, float y, int color) {
        g.rect(x + 1, y, 8, 2, color);
        g.rect(x, y + 2, 10, 3, color);
        g.rect(x + 2, y + 5, 6, 2, color);
        g.rect(x + 4, y + 7, 2, 1, color);
    }

    /** 右上小地图：整张地图压成 56 宽，红点是危险区，绿点是出生点，琥珀点是合成台。 */
    private void drawMinimap(GC g, Game game) {
        float w = 56;
        float s = w / Const.MAP_W;
        float h = Const.MAP_H * s;
        float x = Const.VW - w - 6f, y = game.uiTop + 54f;
        g.rect(x - 1, y - 1, w + 2, h + 2, 0xCC000000);
        g.sprite(game.world.mini, x, y, s);
        g.frame(x, y, w, h, 0xFF33414D, 1f);

        for (int i = 0; i < game.world.benches.size(); i++) {
            Bench b = game.world.benches.get(i);
            if (!b.seen) continue;
            g.rect(x + b.x / Const.TILE * s - 1f, y + b.y / Const.TILE * s - 1f, 2f, 2f, Pal.BENCH_GLOW);
        }

        float px = x + game.player.x / Const.TILE * s;
        float py = y + game.player.y / Const.TILE * s;
        g.rect(px - 1, py - 1, 3, 3, Pal.UI_ACCENT);
        float sx = x + game.world.spawnX / Const.TILE * s;
        float sy = y + game.world.spawnY / Const.TILE * s;
        g.rect(sx - 1, sy - 1, 2, 2, Pal.ENERGY);
    }

    /** 布局模式：压暗画面，给每个可拖的键描一圈虚线框。 */
    private void drawLayoutOverlay(GC g, Game game) {
        g.rect(0, 0, Const.VW, Const.VH, 0x99060A0E);
        for (int i = 0; i < game.ui.shown().size(); i++) {
            Ui.Btn b = game.ui.shown().get(i);
            if (!Game.movable(b.id)) continue;
            int c = game.btnFixed ? 0x66FF8A5C : Pal.UI_ACCENT;
            // 只画四角括号，不画整圈——几个键挨在一起时整圈会糊成一坨
            float k = 6f;
            g.line(b.x - 3, b.y - 3, b.x - 3 + k, b.y - 3, withAlpha(c, 255), 2f);
            g.line(b.x - 3, b.y - 3, b.x - 3, b.y - 3 + k, withAlpha(c, 255), 2f);
            g.line(b.x + b.w + 3, b.y + b.h + 3, b.x + b.w + 3 - k, b.y + b.h + 3, withAlpha(c, 255), 2f);
            g.line(b.x + b.w + 3, b.y + b.h + 3, b.x + b.w + 3, b.y + b.h + 3 - k, withAlpha(c, 255), 2f);
            g.circle(b.cx(), b.cy(), 2f, withAlpha(c, 180));
        }

        String tip = game.btnFixed
                ? "按键已锁定，点「按键锁:开」解锁后才能拖动"
                : "按住按钮 / 摇杆底盘，拖到顺手的位置";
        float tw = g.textWidth(tip, 8, false);
        float ty = game.uiBottom - 54f;
        g.rect(Const.VW / 2f - tw / 2f - 8, ty - 4, tw + 16, 15, 0xCC0C1116);
        g.text(tip, Const.VW / 2f, ty, Pal.UI_TEXT, 8, false, 1);
        g.text("攻击 / 冲刺 / 暂停 / 背包 / 声音 / 摇杆 都能挪", Const.VW / 2f, ty + 14,
                0x88788A96, 7, false, 1);
    }

    /**
     * 摇杆。
     * 固定模式：底盘常驻在玩家摆好的位置，只有按底盘才动 —— 适合大屏、拇指有固定落点的人。
     * 跟随模式：底盘画得很淡，按下左半屏任意位置即出杆（v3 手感）。
     */
    private void drawJoystick(GC g, Game game) {
        Ui.Btn base = game.ui.byId(Game.B_JOY);
        if (base == null) return;
        boolean active = game.input.joyActive;

        if (game.joyFixed) {
            float bx = base.cx(), by = base.cy();
            g.circle(bx, by, Const.JOY_R, active ? 0x44000000 : 0x2A000000);
            g.ring(bx, by, Const.JOY_R, active ? 0x99E8EEF4 : 0x55E8EEF4, 1.5f);
            g.ring(bx, by, Const.JOY_R - 6, 0x22E8EEF4, 1f);
            // 四向刻度，固定底盘才需要"对齐感"
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i;
                float cx = bx + (float) Math.cos(a) * (Const.JOY_R - 2f);
                float cy = by + (float) Math.sin(a) * (Const.JOY_R - 2f);
                g.rect(cx - 1f, cy - 1f, 2f, 2f, withAlpha(0xFFE8EEF4, active ? 150 : 80));
            }
            float kx = active ? bx + game.input.joyDX : bx;
            float ky = active ? by + game.input.joyDY : by;
            g.circle(kx, ky, 13, 0xCC1D262E);
            g.ring(kx, ky, 13, active ? 0xFF46D8E6 : 0xAA46D8E6, 1.5f);
            g.circle(kx, ky, 4, 0xCC46D8E6);
            return;
        }

        if (!active) {
            float bx = base.cx(), by = base.cy();
            int a = game.hintT > 0f ? 0x24FFFFFF : 0x12FFFFFF;
            g.ring(bx, by, Const.JOY_R, a, 1f);
            g.circle(bx, by, 9, 0x0EFFFFFF);
            return;
        }
        float bx = game.input.joyX, by = game.input.joyY;
        g.circle(bx, by, 30, 0x28000000);
        g.ring(bx, by, 30, 0x66E8EEF4, 1.5f);
        g.ring(bx, by, 22, 0x22E8EEF4, 1f);
        g.circle(bx + game.input.joyDX, by + game.input.joyDY, 13, 0xCC1D262E);
        g.ring(bx + game.input.joyDX, by + game.input.joyDY, 13, 0xAA46D8E6, 1.5f);
        g.circle(bx + game.input.joyDX, by + game.input.joyDY, 4, 0xAA46D8E6);
    }

    // ============================================================ 面板

    private void drawPausePanel(GC g, Game game) {
        g.rect(0, 0, Const.VW, Const.VH, 0xA6000000);
        Ui.Btn resume = game.ui.byId(Game.B_RESUME);
        if (resume == null) return;
        float x = resume.x - 22f;
        float w = resume.w + 44f;
        float h = Math.min(186f, game.uiBottom - game.uiTop - 12f);
        float y = resume.y - 40f;
        panel(g, x, y, w, h);
        g.text("暂停", x + 22, y + 8, Pal.UI_TEXT, 15, true, 0);
        String info = "LV " + game.level + "　击杀 " + game.kills + "　核心 " + game.cores + "/" + Const.BOSS_SITES;
        g.text(info, x + w - 22, y + 10, Pal.UI_TEXT_DIM, 8, false, 2);
        String stats = Weapon.NAME[game.weaponType()] + " " + game.attack() + "伤"
                + "　生命 " + game.maxHp()
                + "　挥砍 ×" + String.format(java.util.Locale.US, "%.2f", game.rangeMul())
                + "　幸运 " + game.luckText();
        g.text(stats, x + 22, y + 26, 0xFF7E8C97, 7, false, 0);
        g.rect(x + 22, y + 37, w - 44, 1, Pal.UI_LINE_SOFT);
    }

    /** 升级三选一：三张卡，颜色就是词条颜色，点了立刻生效。 */
    private void drawLevelUpPanel(GC g, Game game) {
        g.rect(0, 0, Const.VW, Const.VH, 0xBE03060A);
        Ui.Btn c0 = game.ui.byId(Game.B_PERK);
        Ui.Btn c2 = game.ui.byId(Game.B_PERK + 2);
        if (c0 == null || c2 == null) return;
        float x = c0.x - 12f;
        float y = c0.y - 36f;
        float pw = c2.x + c2.w + 12f - x;
        float ph = c0.h + 36f + 14f;
        panel(g, x, y, pw, ph);

        g.text("等级提升", x + 14, y + 7, 0xFFFFD166, 13, true, 0);
        g.text("LV " + game.level + " · 选一个词条", x + 76, y + 10, Pal.UI_TEXT_DIM, 8, false, 0);
        g.text("世界已暂停", x + pw - 14, y + 10, 0xFF6B7883, 7, false, 2);

        for (int i = 0; i < 3; i++) {
            Ui.Btn b = game.ui.byId(Game.B_PERK + i);
            if (b == null) continue;
            int id = game.offers[i];
            if (id < 0) continue;
            int col = Perk.color(id);
            g.rect(b.x + 1, b.y + 2, b.w, b.h, 0x66000000);
            g.rect(b.x, b.y, b.w, b.h, b.down ? 0xFF232B33 : 0xE6161D24);
            g.frame(b.x, b.y, b.w, b.h, col, b.down ? 2f : 1f);

            // 顶部色条 + 菱形图标
            g.rect(b.x, b.y, b.w, 2f, col);
            if (Perk.isSpecial(id)) {
                g.rect(b.x + 4, b.y + 5, 30, 11, 0x33FFD166);
                g.frame(b.x + 4, b.y + 5, 30, 11, 0xFFFFD166, 1f);
                g.text("幸运", b.x + 19, b.y + 6.5f, 0xFFFFD166, 6.5f, true, 1);
            }
            float ix = b.cx(), iy = b.y + 22f;
            g.circle(ix, iy, 10f, withAlpha(col, 34));
            g.line(ix, iy - 6f, ix + 6f, iy, col, 2f);
            g.line(ix + 6f, iy, ix, iy + 6f, col, 2f);
            g.line(ix, iy + 6f, ix - 6f, iy, col, 2f);
            g.line(ix - 6f, iy, ix, iy - 6f, col, 2f);

            g.text(Perk.NAME[id], b.cx(), b.y + 36f, 0xFFFFFFFF, 10, true, 1);

            // 描述最多两行（按 " " 断开）
            String d = Perk.descOf(id, game.perkStack(id));
            int sp = d.indexOf(' ');
            if (sp > 0 && g.textWidth(d, 8, false) > b.w - 8f) {
                g.text(d.substring(0, sp), b.cx(), b.y + 52f, 0xFFB9C6D1, 8, false, 1);
                g.text(d.substring(sp + 1), b.cx(), b.y + 63f, 0xFFB9C6D1, 8, false, 1);
            } else {
                g.text(d, b.cx(), b.y + 55f, 0xFFB9C6D1, 8, false, 1);
            }

            String note = Perk.note(id);
            if (note.length() > 0) {
                g.text(note, b.cx(), b.y + 80f, 0xFF7E8C97, 7, false, 1);
            }

            int stack = game.perkStack(id);
            // 层数条：五格，点亮几格就是叠了几层——一眼知道还能不能再叠
            if (id != Perk.HEAL) {
                float pw2 = 9f, gap2 = 4f;
                float total = Perk.MAX * pw2 + (Perk.MAX - 1) * gap2;
                float px2 = b.cx() - total / 2f;
                float py2 = b.y + b.h - 34f;
                for (int k = 0; k < Perk.MAX; k++) {
                    boolean on = k < stack;
                    g.rect(px2 + k * (pw2 + gap2), py2, pw2, 4f,
                            on ? col : 0xFF3E4A55);
                }
            }
            String foot = id == Perk.HEAL ? "立即生效" : "已选 " + stack + " / " + Perk.MAX;
            g.text(foot, b.cx(), b.y + b.h - 16f, 0xFF77848F, 7, false, 1);
        }
    }

    private void drawBagPanel(GC g, Game game) {
        g.rect(0, 0, Const.VW, Const.VH, 0xA6000000);
        Ui.Btn first = game.ui.byId(Game.B_SLOT);
        if (first == null) return;

        float px = first.x - 12f;
        float py = first.y - 30f;
        float pw = Const.VW - px * 2f;
        Ui.Btn bottom = game.ui.byId(Game.B_DISMANTLE);
        float ph = bottom != null ? bottom.y + bottom.h + 8f - py : 220f;
        panel(g, px, py, pw, ph);

        g.text("装备与背包", px + 14, py + 6, Pal.UI_TEXT, 12, true, 0);
        g.text("背包 " + game.bag.count() + "/" + Const.BAG_SLOTS
                        + "　　废铁 " + game.bag.scrap,
                px + pw - 44, py + 9, Pal.UI_TEXT_DIM, 8, false, 2);

        // 装备槽
        for (int i = 0; i < Item.SLOTS; i++) {
            Ui.Btn b = game.ui.byId(Game.B_SLOT + i);
            if (b == null) continue;
            Item it = game.gear(i);
            g.rect(b.x, b.y, b.w, b.h, it == null ? 0xFF171D23 : 0xFF1B232A);
            if (b.down) g.rect(b.x, b.y, b.w, b.h, 0x18FFFFFF);
            g.frame(b.x, b.y, b.w, b.h, it == null ? Pal.UI_LINE_SOFT : it.color(), 1f);
            g.rect(b.x, b.y, 3, b.h, it == null ? Pal.UI_LINE_SOFT : it.color());
            if (it != null) {
                drawItemIcon(g, i, it.tier, b.x + b.w - 16f, b.cy(), 20f);
            }
            // 第一行：槽位名 + 数值；第二行：装备名。分两行放，避免文字叠在一起
            g.text(Item.SLOT_NAME[i], b.x + 8, b.y + 3, Pal.UI_TEXT_DIM, 8, false, 0);
            if (it != null) {
                g.text(it.effect(), b.x + b.w - 30f, b.y + 4, Pal.UI_TEXT_DIM, 7, false, 2);
            }
            g.text(it == null ? "空" : it.name(), b.x + 8, b.y + 16,
                    it == null ? 0xFF55636E : it.color(), 9, true, 0);
        }

        // 背包格
        for (int i = 0; i < Const.BAG_SLOTS; i++) {
            Ui.Btn b = game.ui.byId(Game.B_ITEM + i);
            if (b == null) continue;
            Item it = game.bag.get(i);
            if (it == null) {
                g.rect(b.x, b.y, b.w, b.h, 0xFF141A20);
                g.frame(b.x, b.y, b.w, b.h, 0xFF1F2A33, 1f);
                continue;
            }
            g.rect(b.x, b.y, b.w, b.h, 0xFF1B232A);
            if (b.down) g.rect(b.x, b.y, b.w, b.h, 0x22FFFFFF);
            g.frame(b.x, b.y, b.w, b.h, it.color(), b.selected ? 2f : 1f);
            g.rect(b.x, b.y + b.h - 3, b.w, 2, it.color());
            drawItemIcon(g, it.slot, it.tier, b.cx(), b.cy() - 1, Math.min(b.w, b.h) - 9f);
            // 品阶用右下角的小点表示，不再写 "T3"
            for (int k = 0; k <= it.tier && k < 5; k++) {
                g.rect(b.x + b.w - 4f - k * 3f, b.y + b.h - 7f, 2f, 2f, it.color());
            }
            if (b.selected) {
                g.frame(b.x - 2, b.y - 2, b.w + 4, b.h + 4, Pal.UI_ACCENT, 1f);
            }
        }

        // 选中详情
        Item sel = game.selectedItem();
        Ui.Btn eq = game.ui.byId(Game.B_EQUIP);
        if (eq != null) {
            String line = sel == null ? "点一件背包里的装备查看" : sel.name() + "　" + sel.effect();
            int col = sel == null ? Pal.UI_TEXT_DIM : sel.color();
            g.text(line, eq.x, eq.y - 15, col, 9, sel != null, 0);
        }
    }

    /**
     * 合成台面板（v10）：8 张"产物卡"，按哪张造哪件。
     *
     * 卡片上写的是"这类东西的名字"（太刀 / 重斧 / … / 随机），不是身上那件，
     * 底下的产出行再把这个类型从白到红的名字整条列出来 —— 一眼就能看出
     * "我要的长枪最顶级叫相位长枪"，而不是"只能升级当前装备"。
     */
    private void drawBenchPanel(GC g, Game game) {
        g.rect(0, 0, Const.VW, Const.VH, 0xA6000000);
        float px = game.panelX, py = game.panelY, pw = game.panelW, ph = game.panelH;
        if (pw <= 0f) return;
        panel(g, px, py, pw, ph);

        g.text("合 成 台", px + 14, py + 8, Pal.BENCH_GLOW, 13, true, 0);
        Ui.Btn codex = game.ui.byId(Game.B_CODEX);
        float scrapX = codex != null ? codex.x - 8f : px + pw - 92f;
        g.text("废铁 " + game.bag.scrap, scrapX, py + 11, Pal.SCRAP, 8, false, 2);
        g.text("选一件要造的东西　品阶随机（等级与幸运抬高档位）",
                px + 14, py + 27, Pal.UI_TEXT_DIM, 7, false, 0);

        for (int i = 0; i < Game.PICKS; i++) {
            Ui.Btn b = game.ui.byId(Game.B_CRAFT_PICK + i);
            if (b == null) continue;
            boolean sel = game.benchPick == i;
            g.rect(b.x, b.y, b.w, b.h, sel ? 0xFF24313B : 0xFF171E24);
            if (b.down) g.rect(b.x, b.y, b.w, b.h, 0x18FFFFFF);
            g.frame(b.x, b.y, b.w, b.h, sel ? Pal.BENCH_GLOW : Pal.UI_LINE_SOFT, sel ? 1.6f : 1f);
            g.rect(b.x, b.y, 3, b.h, sel ? Pal.BENCH_GLOW : Pal.UI_LINE_SOFT);

            float ix = b.x + 15f, iy = b.cy() - 1f;
            if (i < 4) {
                drawWeaponIcon(g, i, sel ? Item.BLUE : Item.WHITE, ix, iy, Math.min(20f, b.h - 8f));
            } else if (i == Game.PICK_RANDOM) {
                g.circle(ix, iy, 8f, 0xFF0F151A);
                g.ring(ix, iy, 8f, sel ? Pal.BENCH_GLOW : 0xFF3A4650, 1f);
                g.text("?", ix, iy - 8f, sel ? Pal.BENCH_GLOW : 0xFF9AA8B4, 12, true, 1);
            } else {
                drawItemIcon(g, i - 3, sel ? Item.BLUE : Item.WHITE, ix, iy, Math.min(20f, b.h - 8f));
            }

            g.text(Game.PICK_NAME[i], b.x + 26f, b.y + 4f,
                    sel ? Pal.UI_TEXT : 0xFFB9C6D0, 9, true, 0);
            g.text(pickStat(i), b.x + 26f, b.y + 17f, 0xFF7E8C97, 6.5f, false, 0);
        }

        Ui.Btn craft = game.ui.byId(Game.B_CRAFT);
        if (craft != null) {
            int pick = Math.max(0, Math.min(Game.PICKS - 1, game.benchPick));
            float iy = craft.y - 30f;
            int slot = pick < 4 ? Item.ATK : pick - 3;
            if (pick == Game.PICK_RANDOM) {
                g.text("产出", px + 14, iy, Pal.UI_TEXT_DIM, 7, false, 0);
                g.text("部位与类型全随机　省 1 废铁", px + 42, iy, Pal.UI_TEXT, 7, false, 0);
            } else {
                Item lo = new Item(slot, Item.WHITE, pick < 4 ? pick : 0);
                Item hi = new Item(slot, Item.RED, pick < 4 ? pick : 0);
                float x = px + 14f;
                g.text("产出", x, iy, Pal.UI_TEXT_DIM, 7, false, 0);
                x += g.textWidth("产出", 7, false) + 5f;
                g.text(lo.name(), x, iy, lo.color(), 7, false, 0);
                x += g.textWidth(lo.name(), 7, false) + 3f;
                g.text("▸", x, iy, 0xFF55636E, 7, false, 0);
                x += g.textWidth("▸", 7, false) + 3f;
                g.text(hi.name(), x, iy, hi.color(), 7, false, 0);
                x += g.textWidth(hi.name(), 7, false) + 10f;
                // 只报"顶级那件"的关键数字：冷却/距离卡片上已经有了，不再重复一遍
                String top = slot == Item.ATK ? "顶级 伤" + hi.value() : "顶级 " + hi.effect();
                g.text(top, x, iy, 0xFF6C7A86, 6.5f, false, 0);
            }
            g.text("品阶概率　" + game.craftOdds(), px + 14, iy + 13, 0xFF8A99A6, 7, false, 0);
        }
    }

    /** 合成台卡片下面那行小字：武器的节奏 / 装备的作用。 */
    private static String pickStat(int pick) {
        if (pick < Weapon.TYPES) {
            return String.format(java.util.Locale.US, "%.2fs", Weapon.cd(pick));
        }
        switch (pick) {
            case Item.HP + 3: return "生命上限";
            case Item.SPD + 3: return "移动速度";
            case Item.DASH + 3: return "冲刺冷却";
            default: return "全部随机";
        }
    }

    /**
     * 装备图鉴：把全部 35 件东西摊开摆给人看。
     *
     * 第一页 20 把武器（4 类型 × 5 品阶），第二页 15 件装备（3 部位 × 5 品阶）。
     * 行 = 品阶（白→红，行首标色），列 = 类型/部位 —— 这样"同一品阶四把刀谁快谁慢"
     * 和"同一把刀升到红装多少伤害"两件事都能横竖扫一遍看出来。
     */
    private void drawCodex(GC g, Game game) {
        g.rect(0, 0, Const.VW, Const.VH, 0xB4000000);
        float px = game.panelX, py = game.panelY, pw = game.panelW, ph = game.panelH;
        if (pw <= 0f) return;
        panel(g, px, py, pw, ph);

        boolean weapons = game.codexPage == 0;
        g.text("装 备 图 鉴", px + 14, py + 8, Pal.BENCH_GLOW, 13, true, 0);
        g.text(weapons ? "4 类型 × 5 品阶　名字随品阶变，数值只由品阶定"
                        : "3 部位 × 5 品阶　部位决定加什么，品阶决定加多少",
                px + 100, py + 12, Pal.UI_TEXT_DIM, 6.5f, false, 0);

        float top = py + 50f;
        float bottom = py + ph - 6f;
        float labelW = 34f;
        float x0 = px + 8f + labelW;
        int cols = weapons ? Weapon.TYPES : Item.SLOTS - 1;
        float colW = (px + pw - 8f - x0) / cols;
        float headH = 15f;
        float rowH = (bottom - top - headH) / (float) Item.TIERS;
        float iconS = Math.min(19f, rowH - 7f);

        for (int c = 0; c < cols; c++) {
            float x = x0 + c * colW;
            String nm = weapons ? Weapon.NAME[c] : Item.SLOT_NAME[c + 1];
            g.text(nm, x + 4, top, Pal.UI_ACCENT, 9, true, 0);
            String tag = weapons ? Weapon.TAG[c] : gearTag(c + 1);
            g.text(tag, x + 8 + g.textWidth(nm, 9, true), top + 2,
                    0xFF66788A, 6.5f, false, 0);
        }

        final String[] tierName = {"普通", "精良", "稀有", "史诗", "传说"};
        for (int t = 0; t < Item.TIERS; t++) {
            float y = top + headH + t * rowH;
            g.text(tierName[t], px + 8, y + rowH / 2f - 4, Item.TIER_COLOR[t], 6.5f, false, 0);
            g.rect(px + 8, y - 0.5f, px + pw - 16, 0.5f, 0xFF1C252D);
        }

        for (int c = 0; c < cols; c++) {
            int slot = weapons ? Item.ATK : c + 1;
            for (int t = 0; t < Item.TIERS; t++) {
                float x = x0 + c * colW;
                float y = top + headH + t * rowH;
                Item it = new Item(slot, t, weapons ? c : 0);
                g.rect(x, y + 1, colW - 4, rowH - 3, 0xFF151C22);
                g.rect(x, y + 1, 2, rowH - 3, it.color());
                if (weapons) {
                    drawWeaponIcon(g, c, t, x + 13f, y + rowH / 2f, iconS);
                } else {
                    drawItemIcon(g, slot, t, x + 13f, y + rowH / 2f, iconS);
                }
                g.text(it.name(), x + 25f, y + rowH / 2f - 9f, it.color(), 8, true, 0);
                g.text(codexStat(it), x + 25f, y + rowH / 2f + 1f, 0xFF8A99A6, 6.5f, false, 0);
            }
        }
    }

    /** 图鉴里的数值行：武器要能横向比节奏，装备只写加什么。 */
    private static String codexStat(Item it) {
        if (it.slot != Item.ATK) return it.effect();
        return String.format(java.util.Locale.US, "伤%d · %.2fs · %.0fpx",
                it.value(), Weapon.cd(it.type), Weapon.range(it.type));
    }

    private static String gearTag(int slot) {
        switch (slot) {
            case Item.HP: return "硬抗一下";
            case Item.SPD: return "跑得更快";
            default: return "冲刺更勤";
        }
    }

    /**
     * 武器小图标：四类武器四种轮廓。
     *
     * 背包里 ATK 一直画的是一把剑，四类武器看着一模一样；合成台和图鉴要靠形状区分，
     * 所以这里按 Weapon 类型各画一套：弯刃 / 厚斧 / 长枪 / 交叉双刃。
     */
    public static void drawWeaponIcon(GC g, int type, int tier, float cx, float cy, float size) {
        float s = Math.max(6f, size) * 0.5f;
        int steel = 0xFFBFCBD6, dark = 0xFF0C1013, grip = 0xFF6B543C;
        int tint = Item.TIER_COLOR[Math.max(0, Math.min(Item.TIERS - 1, tier))];
        if (tier >= Item.ORANGE) {
            g.circle(cx, cy, s * 1.05f, withAlpha(tint, tier == Item.RED ? 42 : 24));
        }
        float ux = 0.7071f, uy = -0.7071f;   // 主轴：左下 → 右上
        float qx = 0.7071f, qy = 0.7071f;    // 法线：左上 → 右下

        switch (Weapon.clampType(type)) {
            case Weapon.AXE: {
                // 短柄 + 向一侧张开的楔形厚刃（三层粗线由窄到宽，小尺寸也像个斧头）
                g.line(cx - ux * s * 0.92f, cy - uy * s * 0.92f,
                        cx + ux * s * 0.22f, cy + uy * s * 0.22f, dark, s * 0.42f);
                g.line(cx - ux * s * 0.92f, cy - uy * s * 0.92f,
                        cx + ux * s * 0.22f, cy + uy * s * 0.22f, grip, s * 0.20f);
                for (int k = 0; k < 3; k++) {
                    float t = k / 2f;
                    float off = s * (0.30f - 0.26f * t);
                    float len = s * (0.42f + 0.30f * t);
                    float hx = cx + ux * off, hy = cy + uy * off;
                    g.line(hx - qx * s * 0.10f, hy - qy * s * 0.10f,
                            hx + qx * len, hy + qy * len,
                            k == 2 ? steel : 0xFF73828F, s * 0.20f);
                }
                // 刃口：把三层的外沿连成一条，颜色跟品阶走
                g.line(cx + ux * s * 0.30f + qx * s * 0.44f, cy + uy * s * 0.30f + qy * s * 0.44f,
                        cx + ux * s * 0.04f + qx * s * 0.74f, cy + uy * s * 0.04f + qy * s * 0.74f,
                        withAlpha(tint, 220), s * 0.09f);
                break;
            }
            case Weapon.SPEAR: {
                // 一根长杆 + 枪尖前的护环 + 菱形枪尖（不做护环会被看成一支箭）
                float x0 = cx - ux * s * 0.95f, y0 = cy - uy * s * 0.95f;
                float x1 = cx + ux * s * 0.58f, y1 = cy + uy * s * 0.58f;
                g.line(x0, y0, x1, y1, dark, s * 0.24f);
                g.line(x0, y0, x1, y1, 0xFF93A1AD, s * 0.12f);
                g.line(x0, y0, x0 + ux * s * 0.16f, y0 + uy * s * 0.16f, grip, s * 0.18f);
                g.line(x1 - qx * s * 0.17f, y1 - qy * s * 0.17f,
                        x1 + qx * s * 0.17f, y1 + qy * s * 0.17f, dark, s * 0.16f);
                g.line(x1 - qx * s * 0.14f, y1 - qy * s * 0.14f,
                        x1 + qx * s * 0.14f, y1 + qy * s * 0.14f, steel, s * 0.08f);
                float tipx = cx + ux * s * 1.02f, tipy = cy + uy * s * 1.02f;
                g.line(x1 - qx * s * 0.24f, y1 - qy * s * 0.24f, tipx, tipy, dark, s * 0.22f);
                g.line(x1 + qx * s * 0.24f, y1 + qy * s * 0.24f, tipx, tipy, dark, s * 0.22f);
                g.line(x1 - qx * s * 0.19f, y1 - qy * s * 0.19f, tipx, tipy, steel, s * 0.12f);
                g.line(x1 + qx * s * 0.19f, y1 + qy * s * 0.19f, tipx, tipy, steel, s * 0.12f);
                g.circle(tipx, tipy, s * 0.07f, tint);
                break;
            }
            case Weapon.TWIN: {
                // 交叉双刃：两条对角线 + 左下/左上两个握把，中间一个握把点
                g.line(cx - ux * s * 0.88f, cy - uy * s * 0.88f,
                        cx + ux * s * 0.88f, cy + uy * s * 0.88f, dark, s * 0.26f);
                g.line(cx - ux * s * 0.88f, cy - uy * s * 0.88f,
                        cx + ux * s * 0.88f, cy + uy * s * 0.88f, steel, s * 0.12f);
                g.line(cx - qx * s * 0.80f, cy - qy * s * 0.80f,
                        cx + qx * s * 0.80f, cy + qy * s * 0.80f, dark, s * 0.22f);
                g.line(cx - qx * s * 0.80f, cy - qy * s * 0.80f,
                        cx + qx * s * 0.80f, cy + qy * s * 0.80f, 0xFF9AA8B4, s * 0.10f);
                g.line(cx - ux * s * 1.04f, cy - uy * s * 1.04f,
                        cx - ux * s * 0.74f, cy - uy * s * 0.74f, grip, s * 0.16f);
                g.line(cx - qx * s * 1.02f, cy - qy * s * 1.02f,
                        cx - qx * s * 0.72f, cy - qy * s * 0.72f, grip, s * 0.16f);
                g.circle(cx, cy, s * 0.12f, grip);
                g.circle(cx, cy, s * 0.06f, tint);
                break;
            }
            default: {
                // 太刀：细长微弯的刃（刀尖上翘）+ 明显横向护手 + 柄
                float gx = cx - ux * s * 0.20f, gy = cy - uy * s * 0.20f;
                g.line(cx - ux * s * 0.86f, cy - uy * s * 0.86f, gx, gy, dark, s * 0.34f);
                g.line(cx - ux * s * 0.86f, cy - uy * s * 0.86f, gx, gy, grip, s * 0.18f);
                g.line(gx - qx * s * 0.34f, gy - qy * s * 0.34f,
                        gx + qx * s * 0.34f, gy + qy * s * 0.34f, dark, s * 0.22f);
                g.line(gx - qx * s * 0.30f, gy - qy * s * 0.30f,
                        gx + qx * s * 0.30f, gy + qy * s * 0.30f, steel, s * 0.10f);
                float mx = cx + ux * s * 0.42f - qx * s * 0.06f;
                float my = cy + uy * s * 0.42f - qy * s * 0.06f;
                float tipx = cx + ux * s * 1.00f - qx * s * 0.20f;
                float tipy = cy + uy * s * 1.00f - qy * s * 0.20f;
                g.line(gx, gy, mx, my, dark, s * 0.30f);
                g.line(mx, my, tipx, tipy, dark, s * 0.20f);
                g.line(gx, gy, mx, my, steel, s * 0.18f);
                g.line(mx, my, tipx, tipy, steel, s * 0.12f);
                g.line(gx + qx * s * 0.09f, gy + qy * s * 0.09f, tipx, tipy,
                        withAlpha(tint, 200), s * 0.05f);
                break;
            }
        }
    }

    private void drawDeadPanel(GC g, Game game) {
        g.rect(0, 0, Const.VW, Const.VH, 0xB4000000);
        float w = Const.DEAD_W;
        float h = Math.min(Const.DEAD_H, game.uiBottom - game.uiTop - 16f);
        float x = (Const.VW - w) / 2f;
        float y = game.uiTop + (game.uiBottom - game.uiTop - h) / 2f;
        panel(g, x, y, w, h);

        g.text("信 号 中 断", Const.VW / 2f, y + 10, Pal.HAZARD_RED, 16, true, 1);
        g.text("BOLT-7 的机体停止运转", Const.VW / 2f, y + 32, Pal.UI_TEXT_DIM, 8, false, 1);
        g.rect(x + 20, y + 46, w - 40, 1, Pal.UI_LINE_SOFT);

        // 等级行：满级触底时不要显示"1 → 1 损失 0"
        boolean floor = game.levelLost <= 0;
        String lvText = floor ? "LV " + game.level + "（已触底）"
                : game.levelBefore + " → " + game.level;
        int lvColor = floor ? Pal.UI_TEXT_DIM : Pal.WARN_YELLOW;
        g.text("等级", x + 24, y + 56, Pal.UI_TEXT_DIM, 9, false, 0);
        g.text(lvText, x + w - 24, y + 55, lvColor, 10, true, 2);

        g.text("损失", x + 24, y + 72, Pal.UI_TEXT_DIM, 9, false, 0);
        if (floor) {
            g.text("不扣除", x + w - 24, y + 72, 0xFF5D6B76, 9, false, 2);
        } else {
            g.text("-" + game.levelLost + " 级", x + w - 24, y + 72, Pal.HAZARD_RED, 10, true, 2);
        }

        g.text("本次击杀", x + 24, y + 88, Pal.UI_TEXT_DIM, 9, false, 0);
        g.text(String.valueOf(game.kills), x + w - 24, y + 88, Pal.UI_TEXT, 10, true, 2);

        g.text("装备与背包已保留", Const.VW / 2f, y + 106, Pal.UI_OK, 8, false, 1);
    }

    // ============================================================ 标题

    private void drawTitle(GC g, Game game) {
        g.clear(0xFF0A0E12);
        // 背景：斜向警示条，暗示"危险工厂"
        for (int i = -3; i < 14; i++) {
            float x = i * 52f;
            g.line(x, Const.VH, x + 90, 0, 0x14E8452F, 26f);
        }
        long seed = 0x9E3779B9L;
        for (int i = 0; i < 26; i++) {
            long h = seed * (i + 7) * 1103515245L + 12345L;
            float x = Math.abs(h % 452) + 14;
            float y = Math.abs((h >> 12) % 230) + game.uiTop + 10;
            float tw = 0.5f + 0.5f * (float) Math.sin(game.time * 2f + i);
            g.rect(x, y, 2, 2, withAlpha(0xFF46D8E6, (int) (40 + 70 * tw)));
        }

        float cx = Const.VW / 2f;
        float ty = game.uiTop + 40f;
        g.text("BOLT-7", cx, ty, Pal.UI_TEXT, 34, true, 1);
        g.rect(cx - 62, ty + 44, 124, 3, Pal.HAZARD_RED);
        g.rect(cx - 62, ty + 44, 40, 3, Pal.WARN_YELLOW);
        g.text("荒野回收", cx, ty + 52, Pal.UI_ACCENT, 13, true, 1);
        g.text("探索荒野 · 打怪掉落 · 合成台造装备", cx, ty + 72, Pal.UI_TEXT_DIM, 9, false, 1);

        Ui.Btn start = game.ui.byId(Game.B_START);
        if (start != null) {
            // 按钮由 drawButtons 画，这里只补一圈呼吸光
            float p = 0.5f + 0.5f * (float) Math.sin(game.time * 3f);
            g.frame(start.x - 4, start.y - 4, start.w + 8, start.h + 8,
                    withAlpha(Pal.UI_ACCENT, (int) (40 + 50 * p)), 1f);
        }

        g.text("最佳等级 " + game.bestLevel + "　最多击杀 " + game.bestKills,
                cx, game.uiBottom - 30, Pal.UI_TEXT_DIM, 9, false, 1);
        g.text("左侧拖动移动　·　右下攻击/冲刺　·　暂停里可调按键位置",
                cx, game.uiBottom - 18, 0x66788A96, 8, false, 1);
    }

    // ============================================================ 通用

    private void panel(GC g, float x, float y, float w, float h) {
        g.rect(x + 2, y + 3, w, h, 0x66000000);
        g.rect(x, y, w, h, Pal.UI_PANEL);
        g.frame(x, y, w, h, Pal.UI_LINE, 1f);
        g.rect(x + 1, y + 1, w - 2, 1, 0xFF222C35);
    }

    private void drawToasts(GC g, Game game) {
        float y = game.uiTop + 42f;
        for (int i = 0; i < game.msgs.size(); i++) {
            FloatMsg m = game.msgs.get(i);
            int a = m.alpha();
            if (a <= 3) continue;
            float w = g.textWidth(m.text, 8, false) + 18;
            float x = Const.VW / 2f - w / 2f;
            g.rect(x, y, w, 14, withAlpha(0xF00E1216, a));
            g.frame(x, y, w, 14, withAlpha(m.color, Math.min(180, a)), 1f);
            g.rect(x, y, 2, 14, withAlpha(m.color, a));
            g.text(m.text, Const.VW / 2f + 1, y + 3, withAlpha(Pal.UI_TEXT, a), 8, false, 1);
            y += 17;
        }
    }

    private void drawButtons(GC g, Game game) {
        for (int i = 0; i < game.ui.shown().size(); i++) {
            Ui.Btn b = game.ui.shown().get(i);
            if (b.custom) continue;        // 面板内容由各面板自己画
            if (b.round) drawRound(g, b);
            else drawRect(g, b);
        }
    }

    private void drawRect(GC g, Ui.Btn b) {
        float dy = b.down ? 1f : 0f;
        int fill = b.down ? Pal.UI_BTN_DOWN : Pal.UI_BTN;
        int line = b.style == Ui.ST_PRIMARY ? Pal.UI_ACCENT : Pal.UI_LINE;
        if (b.style == Ui.ST_DANGER) line = Pal.UI_WARN;

        g.rect(b.x, b.y + dy, b.w, b.h, b.enabled ? fill : 0xFF12181D);
        g.frame(b.x, b.y + dy, b.w, b.h, b.enabled ? line : 0xFF29343D, 1f);
        if (b.selected) g.frame(b.x - 2, b.y - 2, b.w + 4, b.h + 4, Pal.UI_ACCENT, 1f);

        float tx = b.x + b.w / 2f;
        float ty = b.y + dy + (b.h - 11) / 2f - 1;
        if (b.icon != Ui.ICON_NONE) {
            float ix = b.label.length() > 0 ? b.x + 14 : b.cx();
            icon(g, b.icon, ix, b.y + dy + b.h / 2f - 1, b.enabled ? Pal.UI_TEXT : 0xFF4B5862);
            if (b.label.length() > 0) {
                tx = b.x + (b.w + 10) / 2f;
                g.text(b.label, tx, ty, b.enabled ? Pal.UI_TEXT : 0xFF4B5862, 10, false, 1);
            }
        } else if (b.label.length() > 0) {
            g.text(b.label, tx, ty, b.enabled ? Pal.UI_TEXT : 0xFF4B5862, 10, false, 1);
        }
        if (b.sub.length() > 0 && b.h >= 22) {
            g.text(b.sub, b.x + b.w - 5, b.y + dy + b.h - 11, 0xFF7E8C97, 7, false, 2);
        }
    }

    private void drawRound(GC g, Ui.Btn b) {
        float dy = b.down ? 1.5f : 0f;
        float cx = b.cx(), cy = b.cy() + dy, r = b.w / 2f;
        int fill = b.style == Ui.ST_DANGER ? 0xFF3A1A16 : 0xFF18212A;
        if (b.down) fill = b.style == Ui.ST_DANGER ? 0xFF5A241B : 0xFF25333E;
        g.circle(cx, cy + 2, r, 0x66000000);
        g.circle(cx, cy, r, b.enabled ? fill : 0xFF12181D);
        int line = b.style == Ui.ST_DANGER ? Pal.HAZARD_RED : Pal.UI_ACCENT;
        g.ring(cx, cy, r - 1, b.enabled ? line : 0xFF2B353E, 2f);
        g.ring(cx, cy, r - 4, withAlpha(line, 50), 1f);
        icon(g, b.icon, cx, cy, b.enabled ? (b.style == Ui.ST_DANGER ? 0xFFFFD9C8 : Pal.UI_TEXT) : 0xFF4B5862);
        if (b.sub.length() > 0) {
            g.text(b.sub, cx, b.y + b.h - 8, 0xFFFFFFFF, 8, true, 1);
        }
    }

    private void icon(GC g, int id, float x, float y, int color) {
        switch (id) {
            case Ui.ICON_PAUSE:
                g.rect(x - 4, y - 5, 3, 10, color);
                g.rect(x + 1, y - 5, 3, 10, color);
                break;
            case Ui.ICON_BAG:
                g.frame(x - 6, y - 4, 12, 9, color, 1f);
                g.rect(x - 3, y - 7, 6, 1, color);
                g.line(x - 3, y - 6, x - 3, y - 4, color, 1f);
                g.line(x + 3, y - 6, x + 3, y - 4, color, 1f);
                break;
            case Ui.ICON_SOUND:
                speaker(g, x, y, color);
                g.arc(x - 1, y, 5, -52, 104, color, 1f);
                g.arc(x - 1, y, 8, -52, 104, withAlpha(color, 120), 1f);
                break;
            case Ui.ICON_MUTE:
                speaker(g, x, y, color);
                g.line(x + 2, y - 3, x + 7, y + 3, Pal.UI_WARN, 1.6f);
                g.line(x + 7, y - 3, x + 2, y + 3, Pal.UI_WARN, 1.6f);
                break;
            case Ui.ICON_SWORD:
                g.line(x - 5, y + 5, x + 5, y - 6, color, 2f);
                g.line(x + 5, y - 6, x + 7, y - 7, color, 1f);
                g.line(x - 6, y + 2, x - 2, y + 6, color, 2f);
                break;
            case Ui.ICON_DASH:
                g.line(x - 3, y - 5, x + 2, y, color, 2f);
                g.line(x + 2, y, x - 3, y + 5, color, 2f);
                g.line(x + 2, y - 5, x + 7, y, withAlpha(color, 120), 2f);
                g.line(x + 7, y, x + 2, y + 5, withAlpha(color, 120), 2f);
                break;
            case Ui.ICON_CLOSE:
                g.line(x - 4, y - 4, x + 4, y + 4, color, 1.6f);
                g.line(x + 4, y - 4, x - 4, y + 4, color, 1.6f);
                break;
            case Ui.ICON_CRAFT:
                g.rect(x - 5, y - 1, 10, 2, color);
                g.rect(x - 1, y - 5, 2, 10, color);
                break;
            case Ui.ICON_EQUIP:
                g.line(x, y + 5, x, y - 4, color, 2f);
                g.line(x, y - 4, x - 4, y, color, 2f);
                g.line(x, y - 4, x + 4, y, color, 2f);
                break;
            case Ui.ICON_TRASH:
                g.frame(x - 5, y - 3, 10, 9, color, 1f);
                g.rect(x - 6, y - 5, 12, 2, color);
                g.line(x - 2, y - 2, x - 2, y + 4, color, 1f);
                g.line(x + 2, y - 2, x + 2, y + 4, color, 1f);
                break;
            case Ui.ICON_PLAY:
                g.line(x - 4, y - 5, x + 5, y, color, 2f);
                g.line(x + 5, y, x - 4, y + 5, color, 2f);
                g.line(x - 4, y - 5, x - 4, y + 5, color, 2f);
                break;
            default:
                break;
        }
    }

    private void speaker(GC g, float x, float y, int color) {
        g.rect(x - 7, y - 2, 3, 5, color);
        g.line(x - 4, y, x - 1, y - 4, color, 2f);
        g.line(x - 1, y - 4, x - 1, y + 4, color, 2f);
        g.line(x - 4, y, x - 1, y + 4, color, 2f);
    }

    private static int withAlpha(int color, int a) {
        a = Math.max(0, Math.min(255, a));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
