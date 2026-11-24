package com.bolt7.factory;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * 渲染线程、触屏映射、存档。
 *
 * 触屏坐标统一换算到 480×272 虚拟画布，再投递给 Game 的事件队列；
 * 命中测试只在 Game/Ui 里做一份，避免"画的地方"和"点的地方"不一致。
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    /** 逻辑步长固定 1/120 秒：60Hz 屏每帧走两步，120Hz 屏一步，输入响应直接翻倍。 */
    private static final float STEP = 1f / 120f;
    /** 渲染节奏跟随屏幕刷新率（60/90/120），不再硬等 16ms 与屏幕拍频。 */
    private long frameNanos = 16666666L;

    private final Game game;
    private final Renderer renderer = new Renderer();
    private final Sfx sfx;
    private final SharedPreferences prefs;
    private final AndroidGC gc;

    private Bitmap vbuf;
    private Canvas vcanvas;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean paused;

    /** 超采样倍率：1080p 屏取 3，输出 1440×816 再缩到屏幕 */
    private int mult = 3;
    private int bufW = Const.VW * 3;
    private int bufH = Const.VH * 3;
    private float rate = 1f;      // 屏幕物理像素 / 逻辑像素
    private float srcTop;         // 逻辑坐标里被裁掉的顶部高度
    private float srcTopPx;       // 同上，单位是画布像素
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private final Paint blit = new Paint();

    private int savedLevel = -1;
    private int savedScrap = -1;
    private int savedBestLevel = -1;
    private int savedBestKills = -1;
    private int savedGearHash;
    private int savedBagHash;
    private int savedLayoutHash;
    private int savedPerkHash;

    public GameView(Context ctx) {
        super(ctx);
        prefs = ctx.getSharedPreferences("bolt7", Context.MODE_PRIVATE);
        sfx = new Sfx(ctx);
        game = new Game(new Game.Audio() {
            @Override
            public void sfx(int id) {
                sfx.play(id);
            }

            @Override
            public void buzz(int ms) {
                vibrate(ms);
            }
        });

        // ---- 读档：等级、装备、背包、废铁都跨启动保留 ----
        game.level = Math.max(1, prefs.getInt("level", 1));
        game.bestLevel = Math.max(game.level, prefs.getInt("bestLevel", 1));
        game.bestKills = prefs.getInt("bestKills", 0);
        game.bag.scrap = Math.max(0, prefs.getInt("scrap", 0));
        game.muted = prefs.getBoolean("muted", false);
        // 按键布局：固定开关 + 每个键的偏移
        game.btnFixed = prefs.getBoolean("btnFixed", true);
        game.joyFixed = prefs.getBoolean("joyFixed", false);
        game.aimAssist = Math.max(0, Math.min(2, prefs.getInt("aimAssist", 1)));
        game.cores = Math.max(0, prefs.getInt("cores", 0));
        for (int i = 0; i < Perk.COUNT; i++) {
            game.perks[i] = Math.max(0, Math.min(Perk.MAX, prefs.getInt("pk" + i, 0)));
        }
        for (int id = 1; id <= 17; id++) {
            if (!Game.movable(id)) continue;
            game.setOffset(id, prefs.getFloat("lx" + id, 0f), prefs.getFloat("ly" + id, 0f));
        }
        for (int i = 0; i < Item.SLOTS; i++) {
            int tier = prefs.getInt("gear" + i, -1);
            if (tier >= 0) game.gear[i] = new Item(i, tier, prefs.getInt("gt" + i, -1));
        }
        if (game.gear[Item.ATK] == null) game.gear[Item.ATK] = Item.starter();
        String bag = prefs.getString("bag", "");
        if (bag.length() > 0) {
            String[] parts = bag.split(",");
            for (String p : parts) {
                String[] f = p.split(":");
                if (f.length < 3) continue;
                try {
                    int idx = Integer.parseInt(f[0]);
                    int tier = Integer.parseInt(f[1]);
                    int slot = Integer.parseInt(f[2]);
                    int type = f.length >= 4 ? Integer.parseInt(f[3]) : -1;
                    if (idx >= 0 && idx < Const.BAG_SLOTS) {
                        game.bag.slots[idx] = new Item(slot, tier, type);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        savedGearHash = gearHash();
        savedBagHash = bagHash();
        savedLayoutHash = layoutHash();
        savedPerkHash = perkHash();

        sfx.setEnabled(!game.muted);
        getHolder().addCallback(this);
        setFocusable(true);
        setKeepScreenOn(true);
        blit.setAntiAlias(false);
        blit.setDither(false);
        gc = new AndroidGC(new Canvas());
        // 跟随屏幕刷新：120Hz 屏直接 120fps，60Hz 屏保持 60fps（逻辑步长仍是 1/120）
        try {
            android.view.WindowManager wm = (android.view.WindowManager)
                    ctx.getSystemService(Context.WINDOW_SERVICE);
            float hz = wm.getDefaultDisplay().getRefreshRate();
            if (hz > 0f) {
                float target = Math.max(60f, Math.min(120f, hz));
                frameNanos = (long) (1e9 / target) + 1L;
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ 生命周期

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        computeLayout();
        startLoop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        computeLayout();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopLoop();
        saveNow();
    }

    public void onPauseGame() {
        paused = true;
        if (game.state == Game.State.PLAY) game.pause();
        game.input.reset();
        saveNow();
    }

    public void onResumeGame() {
        paused = false;
        computeLayout();
    }

    public boolean isTitle() {
        return game.state == Game.State.TITLE;
    }

    public void togglePause() {
        if (game.state == Game.State.BAG) game.closeBag();
        else game.togglePause();
    }

    public void onBack() {
        if (game.state == Game.State.CODEX) game.closeCodex();
        else if (game.state == Game.State.BAG) game.closeBag();
        else if (game.state == Game.State.PAUSED) game.resume();
        else if (game.state == Game.State.DEAD) game.toTitle();
        else if (game.state == Game.State.PLAY) game.pause();
        else if (isTitle()) {
            if (getContext() instanceof android.app.Activity) {
                ((android.app.Activity) getContext()).finish();
            }
        }
    }

    public void onDestroyGame() {
        stopLoop();
        saveNow();
        sfx.release();
    }

    private void startLoop() {
        if (running) return;
        running = true;
        thread = new Thread(this, "bolt7-render");
        thread.start();
    }

    private void stopLoop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            try {
                t.join(800);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void computeLayout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float raw = w / (float) Const.VW;
        // 整数倍超采样：宁可多画几倍像素，也不要 480 直接拉大后满屏模糊
        int m = Math.max(2, Math.min(5, (int) Math.ceil(raw)));
        while (m > 2 && Const.VW * m > 2400) m--;   // 上限 2400（约 10MB 缓冲），5 倍足够 2K 屏不糊

        // 上下各裁一点，画面正中最接近玩家；关键：整条可见带是 [cropTop, VH-cropBot]
        float crop = Const.cropTotal(w, h);
        float cropTop = Const.cropTop(w, h);
        float cropBot = crop - cropTop;

        mult = m;
        bufW = Const.VW * m;
        bufH = Math.max(1, Math.round((Const.VH - crop) * m));
        rate = raw;
        srcTop = cropTop;
        // 负数：把画布整体上移 cropTop，露出的是 [cropTop, VH-cropBot] 这一段
        srcTopPx = -cropTop * m;
        game.uiTop = cropTop;
        game.uiBottom = Const.VH - cropBot;

        src.set(0, 0, bufW, bufH);
        if (crop > 0f) {
            dst.set(0, 0, w, h);
        } else {
            int dh = Math.round(bufH * w / (float) bufW);
            int top = (h - dh) / 2;
            dst.set(0, top, w, top + dh);
        }
        // 缩小用双线性（文字更锐），1:1 用最近邻
        blit.setFilterBitmap(bufW != w);
    }

    // ------------------------------------------------------------ 主循环

    @Override
    public void run() {
        long last = System.nanoTime();
        double acc = 0;
        long next = last;
        while (running) {
            long now = System.nanoTime();
            double dt = (now - last) / 1e9;
            last = now;
            if (dt > 0.25) dt = 0.25;

            if (!paused) {
                acc += dt;
                int steps = 0;
                while (acc >= STEP && steps < 8) {
                    game.update(STEP, game.input);
                    game.input.endFrame();
                    acc -= STEP;
                    steps++;
                }
                if (steps >= 8) acc = 0;
            } else {
                acc = 0;
            }

            drawFrame();
            persistIfNeeded();

            // 按屏幕刷新率排帧，而不是固定 sleep(16)：不再和屏幕拍频，也不累积漂移
            next += frameNanos;
            long wait = next - System.nanoTime();
            if (wait > 1000000L) {
                try {
                    Thread.sleep(wait / 1000000L, (int) (wait % 1000000L));
                } catch (InterruptedException e) {
                    break;
                }
            } else if (wait < -frameNanos * 3) {
                next = System.nanoTime();     // 落后太多（卡顿/GC）：重新对齐，不补帧
            }
        }
    }

    private void ensureBuffer() {
        if (vbuf == null || vbuf.getWidth() != bufW || vbuf.getHeight() != bufH) {
            vbuf = Bitmap.createBitmap(bufW, bufH, Bitmap.Config.ARGB_8888);
            vcanvas = new Canvas(vbuf);
            gc.bind(vcanvas);
        }
        gc.setView(mult, srcTopPx);
    }

    private void drawFrame() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        computeLayout();
        try {
            ensureBuffer();
            renderer.draw(gc, game);
        } catch (Throwable t) {
            return;      // 缓冲分配失败或绘制异常：丢一帧，别让渲染线程死掉
        }

        SurfaceHolder holder = getHolder();
        Canvas c = null;
        try {
            c = holder.lockCanvas();
            if (c != null) {
                c.drawColor(0xFF000000);
                c.drawBitmap(vbuf, src, dst, blit);
            }
        } catch (Throwable ignored) {
            // 表面被销毁时忽略
        } finally {
            if (c != null) {
                try {
                    holder.unlockCanvasAndPost(c);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------ 存档

    private int gearHash() {
        int h = 17;
        for (int i = 0; i < Item.SLOTS; i++) {
            h = h * 31 + game.gearTier(i);
        }
        return h;
    }

    private int bagHash() {
        int h = 19 + game.bag.scrap;
        for (int i = 0; i < Const.BAG_SLOTS; i++) {
            Item it = game.bag.get(i);
            h = h * 31 + (it == null ? -1 : it.slot + it.tier * 7 + i * 13);
        }
        return h;
    }

    private void persistIfNeeded() {
        int gh = gearHash();
        int bh = bagHash();
        int ph = perkHash();
        boolean dirty = game.level != savedLevel
                || game.bestLevel != savedBestLevel
                || game.bestKills != savedBestKills
                || gh != savedGearHash
                || bh != savedBagHash
                || ph != savedPerkHash
                || layoutHash() != savedLayoutHash;
        if (!dirty) return;
        savedLevel = game.level;
        savedBestLevel = game.bestLevel;
        savedBestKills = game.bestKills;
        savedGearHash = gh;
        savedBagHash = bh;
        savedPerkHash = ph;
        savedLayoutHash = layoutHash();
        saveNow();
    }

    /** 词条 / 辅助瞄准 / 核心数的变化也要落盘，否则重开一局构筑就没了。 */
    private int perkHash() {
        int h = 23 + game.aimAssist * 7 + game.cores * 31;
        for (int i = 0; i < Perk.COUNT; i++) h = h * 37 + game.perks[i];
        return h;
    }

    private int layoutHash() {
        int h = (game.btnFixed ? 7 : 11) * 31 + (game.joyFixed ? 13 : 17);
        for (int id = 1; id <= 17; id++) {
            if (!Game.movable(id)) continue;
            h = h * 31 + Math.round(game.offX(id) * 4f);
            h = h * 31 + Math.round(game.offY(id) * 4f);
        }
        return h;
    }

    private void saveNow() {
        SharedPreferences.Editor e = prefs.edit();
        e.putInt("level", game.level);
        e.putInt("bestLevel", game.bestLevel);
        e.putInt("bestKills", game.bestKills);
        e.putInt("scrap", game.bag.scrap);
        e.putBoolean("muted", game.muted);
        e.putBoolean("btnFixed", game.btnFixed);
        e.putBoolean("joyFixed", game.joyFixed);
        e.putInt("aimAssist", game.aimAssist);
        e.putInt("cores", game.cores);
        for (int i = 0; i < Perk.COUNT; i++) e.putInt("pk" + i, game.perks[i]);
        for (int id = 1; id <= 17; id++) {
            if (!Game.movable(id)) continue;
            e.putFloat("lx" + id, game.offX(id));
            e.putFloat("ly" + id, game.offY(id));
        }
        for (int i = 0; i < Item.SLOTS; i++) {
            e.putInt("gear" + i, game.gearTier(i));
            Item it = game.gear(i);
            e.putInt("gt" + i, it == null ? -1 : it.type);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Const.BAG_SLOTS; i++) {
            Item it = game.bag.get(i);
            if (it == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(i).append(':').append(it.tier).append(':').append(it.slot)
                    .append(':').append(it.type);
        }
        e.putString("bag", sb.toString());
        e.apply();
    }

    // ------------------------------------------------------------ 触屏

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                game.input.post(Input.DOWN, e.getPointerId(i), toVX(e.getX(i)), toVY(e.getY(i)));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    game.input.post(Input.MOVE, e.getPointerId(i), toVX(e.getX(i)), toVY(e.getY(i)));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                game.input.post(Input.UP, e.getPointerId(i), toVX(e.getX(i)), toVY(e.getY(i)));
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                game.input.post(Input.CANCEL, -1, 0f, 0f);
                return true;
            }
            default:
                return true;
        }
    }

    private float toVX(float x) {
        return clamp((x - dst.left) / rate, 0f, Const.VW);
    }

    private float toVY(float y) {
        // 高屏会上下留黑边，黑边里的触摸夹回画布内，避免算出画布外的坐标
        return clamp(srcTop + (y - dst.top) / rate, 0f, Const.VH);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void vibrate(int ms) {
        if (game.muted) return;
        try {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, 110));
            } else {
                v.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }
}
