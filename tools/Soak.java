import com.bolt7.factory.*;

import java.util.Random;

/**
 * 十分钟压测：机器人随机探索、连续挥击、定时开背包。
 * 目的不是打得好，而是把长时间运行才会暴露的问题（列表膨胀、死循环、异常）跑出来。
 */
public final class Soak {

    public static void main(String[] a) {
        Game g = new Game(null);
        g.rnd.setSeed(99L);
        g.startRun();
        g.player.spawnProtect = 1e9f;

        Random r = new Random(7);
        float dx = 1, dy = 0;
        int maxEnemies = 0, maxParticles = 0, maxLoot = 0;
        long t0 = System.nanoTime();

        for (int f = 0; f < 36000; f++) {
            if (f % 25 == 0) {
                float bestX = dx, bestY = dy;
                float bestScore = -1e9f;
                for (int k = 0; k < 8; k++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    float cx = (float) Math.cos(ang), cy = (float) Math.sin(ang);
                    float score = 0f;
                    for (int step = 1; step <= 4; step++) {
                        boolean ok = mapProbe(g, cx * 10 * step, cy * 10 * step);
                        score += ok ? 1f : -4f;
                        if (!ok) break;
                    }
                    score += r.nextFloat() * 0.6f;
                    if (score > bestScore) {
                        bestScore = score;
                        bestX = cx;
                        bestY = cy;
                    }
                }
                dx = bestX;
                dy = bestY;
            }
            g.input.moveX = dx;
            g.input.moveY = dy;
            g.input.attackPressed = true;
            if (f % 300 == 0) g.input.dashPressed = true;

            g.update(1f / 60f, g.input);
            g.input.endFrame();

            if (g.state == Game.State.LEVELUP) g.choosePerk((int) (f % 3));
            if (g.state == Game.State.BAG) g.closeBag();
            if (g.state == Game.State.PAUSED) g.resume();
            if (g.state == Game.State.DEAD) break;

            maxEnemies = Math.max(maxEnemies, g.world.enemies.size());
            maxParticles = Math.max(maxParticles, g.particles.size());
            maxLoot = Math.max(maxLoot, g.world.loots.size());

            if (f % 1200 == 0) {
                // 定期开合背包，确保面板状态不会把游戏卡住
                g.openBag();
                g.ui.layout(g);
                g.closeBag();
            }
        }

        long ms = (System.nanoTime() - t0) / 1000000L;
        System.out.println("模拟 10 分钟 (" + 36000 + " 帧) 用时 " + ms + " ms，平均 "
                + String.format("%.2f", ms / 36000.0) + " ms/帧");
        System.out.println("等级 " + g.level + "，击杀 " + g.kills + "，废铁 " + g.bag.scrap
                + "，背包 " + g.bag.count() + "/" + Const.BAG_SLOTS);
        System.out.println("峰值实体：怪 " + maxEnemies + "，粒子 " + maxParticles + "，掉落 " + maxLoot);
        System.out.println("坐标 " + String.format("%.0f,%.0f", g.player.x, g.player.y)
                + " / 地图 " + (Const.MAP_W * Const.TILE) + "x" + (Const.MAP_H * Const.TILE));

        boolean ok = g.state == Game.State.PLAY
                && g.level >= 1
                && maxEnemies < 100
                && maxParticles <= 300
                && maxLoot < 200;
        System.out.println(ok ? "SOAK OK" : "SOAK FAIL");
        if (!ok) System.exit(1);
    }

    /** 探一下这个方向 12px 后是否安全（不撞墙、不进危险区）。结果写在 game.probeOk。 */
    static boolean mapProbe(Game g, float ox, float oy) {
        float x = g.player.x + ox;
        float y = g.player.y + oy;
        return !g.world.map.boxHits(x, y, Player.HW + 2, Player.HH + 2)
                && !g.world.map.hazardAtPx(x, y);
    }
}
