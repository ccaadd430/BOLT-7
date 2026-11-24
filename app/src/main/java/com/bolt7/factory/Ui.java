package com.bolt7.factory;

import java.util.ArrayList;

/**
 * UI 控件层。
 *
 * 这里只做三件事：**布局、命中测试、按下状态**。
 * 布局与绘制读的是同一份坐标（Renderer 直接遍历 shown），所以不会出现
 * "看得到但点不到"或"点得到但看不见"——这正是上一版暂停/静音键失效的原因
 * （宽屏裁掉了顶部 56px，按钮画在裁掉的区域里，触摸判定也一起失效）。
 *
 * 布局全部以 uiTop / uiBottom 为锚点，任何屏幕比例都不会跑出屏幕；
 * v6 起支持玩家自己拖按键，偏移叠在 baseX/baseY 之上并再次夹回可见带。
 */
public final class Ui {

    public static final int ICON_NONE = 0;
    public static final int ICON_PAUSE = 1;
    public static final int ICON_BAG = 2;
    public static final int ICON_SOUND = 3;
    public static final int ICON_MUTE = 4;
    public static final int ICON_SWORD = 5;
    public static final int ICON_DASH = 6;
    public static final int ICON_CLOSE = 7;
    public static final int ICON_CRAFT = 8;
    public static final int ICON_EQUIP = 9;
    public static final int ICON_TRASH = 10;
    public static final int ICON_PLAY = 11;

    public static final int ST_PANEL = 0;
    public static final int ST_PRIMARY = 1;
    public static final int ST_DANGER = 2;
    public static final int ST_ICON = 3;

    public static final class Btn {
        public final int id;
        public float x, y, w, h;
        /** 布局算出来的原位（未叠加玩家自定义偏移），拖动时用它反推偏移 */
        public float baseX, baseY;
        public String label = "";
        public String sub = "";
        public int icon = ICON_NONE;
        public int style = ST_PANEL;
        public boolean enabled = true;
        public boolean toggleOn;
        public boolean selected;
        public boolean round;
        public boolean triggerOnDown;
        public boolean custom;
        /** 是否参与触摸命中。摇杆底盘设为 false，由 Game 单独用圆形判定。 */
        public boolean hit = true;
        public boolean fired;
        public int pointer = -1;
        public boolean down;
        public float pressT;
        public boolean visible;

        Btn(int id) {
            this.id = id;
        }

        public boolean contains(float px, float py) {
            float pad = 3f;
            return px >= x - pad && px <= x + w + pad && py >= y - pad && py <= y + h + pad;
        }

        public float cx() {
            return x + w / 2f;
        }

        public float cy() {
            return y + h / 2f;
        }
    }

    private final ArrayList<Btn> all = new ArrayList<Btn>();
    private final ArrayList<Btn> shown = new ArrayList<Btn>();
    private Btn downBtn;

    public void begin() {
        for (int i = 0; i < all.size(); i++) all.get(i).visible = false;
    }

    public Btn put(int id, float x, float y, float w, float h) {
        Btn b = byId(id);
        if (b == null) {
            b = new Btn(id);
            all.add(b);
        }
        b.x = x;
        b.y = y;
        b.baseX = x;
        b.baseY = y;
        b.w = w;
        b.h = h;
        b.visible = true;
        b.enabled = true;
        b.toggleOn = false;
        b.selected = false;
        b.round = false;
        b.triggerOnDown = false;
        b.custom = false;
        b.hit = true;
        b.label = "";
        b.sub = "";
        b.icon = ICON_NONE;
        b.style = ST_PANEL;
        return b;
    }

    public Btn byId(int id) {
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == id) return all.get(i);
        }
        return null;
    }

    public void end() {
        shown.clear();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).visible) shown.add(all.get(i));
        }
        // 被隐藏的按钮不能保持按下
        if (downBtn != null && !downBtn.visible) downBtn = null;
    }

    public ArrayList<Btn> shown() {
        return shown;
    }

    public Btn hit(float x, float y) {
        for (int i = shown.size() - 1; i >= 0; i--) {
            Btn b = shown.get(i);
            if (!b.hit) continue;
            if (b.contains(x, y)) return b;
        }
        return null;
    }

    public void press(Btn b, int pointer, float x, float y) {
        release();
        if (b == null) return;
        downBtn = b;
        b.down = true;
        b.pointer = pointer;
        b.pressT = 0f;
        b.fired = false;
    }

    public void release() {
        if (downBtn != null) {
            downBtn.down = false;
            downBtn.pointer = -1;
            downBtn = null;
        }
    }

    public Btn downButton() {
        return downBtn;
    }

    public void tick(float dt) {
        for (int i = 0; i < all.size(); i++) {
            Btn b = all.get(i);
            if (b.down) b.pressT += dt;
        }
    }

    // ------------------------------------------------------------ 布局

    /** 每帧重建布局。坐标全部相对可见区域，永远不会画到裁切区。 */
    public void layout(Game g) {
        begin();
        float top = g.uiTop;
        float bot = g.uiBottom > top ? g.uiBottom : Const.VH;
        float avail = bot - top;
        switch (g.state) {
            case PLAY:
                layoutPlay(g, top, bot);
                if (g.customizing) layoutCustomize(g, top, bot);
                break;
            case PAUSED:
                layoutPause(g, top);
                break;
            case BAG:
                layoutBag(g, top, avail);
                break;
            case BENCH:
                layoutBench(g, top, avail);
                break;
            case CODEX:
                layoutCodex(g, top, avail);
                break;
            case LEVELUP:
                layoutLevelUp(g, top, avail);
                break;
            case DEAD:
                layoutDead(g, top, avail);
                break;
            default:
                layoutTitle(g, top, avail);
                break;
        }
        applyOffsets(g, top, bot);
        end();
    }

    /** 把玩家自己拖出来的偏移叠回按钮；夹在可见带里，拖不出屏幕。 */
    private void applyOffsets(Game g, float top, float bot) {
        for (int i = 0; i < shown.size(); i++) {
            Btn b = shown.get(i);
            if (!Game.movable(b.id)) continue;
            float x = b.baseX + g.offX(b.id);
            float y = b.baseY + g.offY(b.id);
            b.x = Math.max(0f, Math.min(Const.VW - b.w, x));
            b.y = Math.max(top, Math.min(bot - b.h, y));
        }
    }

    private void layoutPlay(Game g, float top, float bot) {
        float s = 30f;
        float y = top + 6f;

        Btn pause = put(Game.B_PAUSE, Const.VW - s - 6f, y, s, s);
        pause.icon = ICON_PAUSE;
        pause.style = ST_ICON;

        Btn bag = put(Game.B_BAG, Const.VW - s * 2 - 10f, y, s, s);
        bag.icon = ICON_BAG;
        bag.style = ST_ICON;
        bag.sub = String.valueOf(g.bag.count());

        Btn snd = put(Game.B_MUTE, Const.VW - s * 3 - 14f, y, s, s);
        snd.icon = g.muted ? ICON_MUTE : ICON_SOUND;
        snd.style = ST_ICON;

        // 摇杆底盘：默认左下角。固定模式下它是真底盘，跟随模式下只是一圈提示。
        // hit=false —— 它不能挡住左半屏的移动输入，Game 自己按圆形判定。
        float jr = Const.JOY_R;
        Btn joy = put(Game.B_JOY, 66f - jr, bot - 64f - jr, jr * 2f, jr * 2f);
        joy.custom = true;
        joy.hit = false;

        // 沿用 v3 的位置：右下大圆键中心 (VW-44, bot-26) 半径 30，冲刺 (VW-98, bot-24) 半径 24。
        // 注意锚的是 bot（可见区底部），不是 Const.VH——宽屏上 VH 会被裁掉。
        float ar = 30f;
        float acx = Const.VW - 44f;
        float acy = bot - 32f;
        Btn atk = put(Game.B_ATTACK, acx - ar, acy - ar, ar * 2f, ar * 2f);
        atk.round = true;
        atk.icon = ICON_SWORD;
        atk.label = "攻击";
        atk.style = ST_DANGER;
        atk.triggerOnDown = true;

        float dr = 24f;
        float dcx = Const.VW - 98f;
        float dcy = bot - 30f;
        Btn dash = put(Game.B_DASH, dcx - dr, dcy - dr, dr * 2f, dr * 2f);
        dash.round = true;
        dash.icon = ICON_DASH;
        dash.style = ST_PANEL;
        dash.triggerOnDown = true;
        dash.enabled = g.player != null && g.player.dashCd <= 0f;
        dash.sub = dash.enabled ? "" : String.format("%.1f", Math.max(0f, g.player == null ? 0f : g.player.dashCd));
    }

    private void layoutPause(Game g, float top) {
        float w = 268f, h = Math.min(186f, g.uiBottom - top - 12f);
        float x = (Const.VW - w) / 2f;
        float y = top + (g.uiBottom - top - h) / 2f;

        float full = w - 44f;
        float bw = (full - 8f) / 2f, bh = 28f;
        float bx = x + 22f;
        float by = y + 44f;

        Btn r = put(Game.B_RESUME, bx, by, full, bh);
        r.label = "继续游戏";
        r.style = ST_PRIMARY;
        r.icon = ICON_PLAY;

        Btn rs = put(Game.B_RESTART, bx, by + bh + 8f, bw, bh);
        rs.label = "重新开始";
        rs.style = ST_PANEL;

        Btn t = put(Game.B_TITLE, bx + bw + 8f, by + bh + 8f, bw, bh);
        t.label = "回到标题";
        t.enabled = true;

        Btn snd = put(Game.B_MUTE, bx, by + (bh + 8f) * 2f, bw, bh);
        snd.label = g.muted ? "声音：关" : "声音：开";
        snd.icon = g.muted ? ICON_MUTE : ICON_SOUND;
        snd.toggleOn = !g.muted;

        Btn lay = put(Game.B_LAYOUT, bx + bw + 8f, by + (bh + 8f) * 2f, bw, bh);
        lay.label = "按键布局";
        lay.style = ST_PANEL;

        // 辅助瞄准：三档循环。手机上这是"打得中"和"我打的就是我要打的方向"之间的开关
        Btn aim = put(Game.B_AIM, bx, by + (bh + 8f) * 3f, full, bh);
        aim.label = "辅助瞄准：" + Const.AIM_NAME[Math.max(0, Math.min(2, g.aimAssist))];
        aim.style = ST_PANEL;
        aim.toggleOn = g.aimAssist > 0;
    }

    /**
     * 布局模式的工具条：放在底部中间。
     * 上一条版本放在顶部，四个键宽度加起来会压到右上角的背包/暂停键；
     * 底部这一段（摇杆右侧、攻击键左侧）是唯一稳定空出来的横向空间。
     */
    private void layoutCustomize(Game g, float top, float bot) {
        float bh = 22f, y = bot - 28f;
        float w1 = 44f, w2 = 44f, w3 = 64f, w4 = 64f, gap = 5f;
        float total = w1 + w2 + w3 + w4 + gap * 3f;
        float x = (Const.VW - total) / 2f;

        Btn done = put(Game.B_DONE, x, y, w1, bh);
        done.label = "完成";
        done.style = ST_PRIMARY;
        done.icon = ICON_PLAY;

        Btn reset = put(Game.B_RESET, x + w1 + gap, y, w2, bh);
        reset.label = "复位";

        Btn fix = put(Game.B_FIX, x + w1 + w2 + gap * 2f, y, w3, bh);
        fix.label = g.btnFixed ? "按键锁:开" : "按键锁:关";
        fix.toggleOn = g.btnFixed;
        fix.style = ST_PANEL;

        Btn jf = put(Game.B_JOYFIX, x + w1 + w2 + w3 + gap * 3f, y, w4, bh);
        jf.label = g.joyFixed ? "摇杆:固定" : "摇杆:跟随";
        jf.toggleOn = g.joyFixed;
        jf.style = ST_PANEL;
    }

    private void layoutBag(Game g, float top, float avail) {
        float pw = Math.min(418f, Const.VW - 16f);
        float ph = Math.min(232f, avail - 10f);
        float px = (Const.VW - pw) / 2f;
        float py = top + (avail - ph) / 2f;

        float header = 24f;
        float footer = 46f;
        float gridTop = py + header + 6f;
        float gridH = ph - header - footer - 12f;
        float cell = Math.min(38f, (gridH - 15f) / 4f);
        float gap = 4f;
        float gridW = cell * 4f + gap * 3f;

        float leftW = 132f;
        float lx = px + 12f;
        float rx = px + pw - 12f - gridW;

        // 左侧 4 个装备槽
        float rowH = (gridH - 12f) / 4f;
        for (int i = 0; i < Item.SLOTS; i++) {
            Btn b = put(Game.B_SLOT + i, lx, gridTop + i * (rowH + 4f), leftW, rowH);
            Item it = g.gear(i);
            b.label = Item.SLOT_NAME[i];
            b.sub = it == null ? "空" : it.name();
            b.style = ST_PANEL;
            b.enabled = it != null;
            b.toggleOn = it != null;
            b.custom = true;
        }

        // 右侧背包网格
        for (int i = 0; i < Const.BAG_SLOTS; i++) {
            int col = i % 4, row = i / 4;
            Btn b = put(Game.B_ITEM + i, rx + col * (cell + gap), gridTop + row * (cell + gap), cell, cell);
            b.selected = g.selBag == i;
            b.style = ST_PANEL;
            b.enabled = g.bag.get(i) != null;
            b.custom = true;
        }

        // 关闭
        Btn close = put(Game.B_CLOSE, px + pw - 12f - 24f, py + 4f, 24f, 20f);
        close.icon = ICON_CLOSE;
        close.style = ST_ICON;

        // 底部按钮
        float by = py + ph - 32f;
        float bw = 84f, bh = 24f;
        Item sel = g.selectedItem();
        Btn eq = put(Game.B_EQUIP, lx, by, bw, bh);
        eq.label = "装备";
        eq.style = ST_PRIMARY;
        eq.icon = ICON_EQUIP;
        eq.enabled = sel != null;

        Btn dis = put(Game.B_DISMANTLE, lx + bw + 8f, by, bw, bh);
        dis.label = "分解";
        dis.icon = ICON_TRASH;
        dis.enabled = sel != null;

        // 图鉴：不看背包、不看身上，所有能造/能掉的装备都在这两页里
        Btn codex = put(Game.B_CODEX, lx + (bw + 8f) * 2f, by, bw, bh);
        codex.label = "图鉴";
        codex.style = ST_PANEL;

        boolean nearBench = g.benchNear() != null;
        Btn craft = put(Game.B_CRAFT, px + pw - 12f - bw, by, bw, bh);
        craft.label = "合成台";
        craft.sub = nearBench ? "可打开" : "需靠近";
        craft.icon = ICON_CRAFT;
        craft.enabled = nearBench;
    }

    /**
     * 合成台面板（v10）：8 个产物任选 —— 4 类武器 + 3 个部位 + 全随机。
     *
     * 旧版这里只放 4 个"部位"卡，卡上写的还是身上那件装备，看起来像"只能升级当前装备"。
     * 现在每一张卡就是一件可造的东西，跟身上穿什么完全无关。
     */
    private void layoutBench(Game g, float top, float avail) {
        float pw = Math.min(356f, Const.VW - 12f);
        float ph = Math.min(212f, avail - 6f);
        float px = (Const.VW - pw) / 2f;
        float py = top + (avail - ph) / 2f;
        g.panelX = px;
        g.panelY = py;
        g.panelW = pw;
        g.panelH = ph;

        float bx = px + 14f;
        float gap = 6f;
        float cw = (pw - 28f - gap * 3f) / 4f;
        float craftY = py + ph - 44f;
        float cardTop = py + 42f;
        float cardH = Math.max(24f, Math.min(40f, (craftY - 34f - cardTop - gap) / 2f));

        for (int i = 0; i < Game.PICKS; i++) {
            int col = i % 4, row = i / 4;
            Btn b = put(Game.B_CRAFT_PICK + i,
                    bx + col * (cw + gap), cardTop + row * (cardH + gap), cw, cardH);
            b.selected = g.benchPick == i;
            b.style = ST_PANEL;
            b.custom = true;
        }

        // v9：独立的"合成"大键。选产物不再直接花材料，必须按这里才产出。
        Btn craft = put(Game.B_CRAFT, bx, craftY, pw - 28f, 34f);
        craft.label = "合成 " + Game.PICK_NAME[Math.max(0, Math.min(Game.PICKS - 1, g.benchPick))];
        craft.sub = "废铁 " + g.bag.scrap + "/" + g.pickCost(g.benchPick);
        craft.icon = ICON_CRAFT;
        craft.style = ST_PRIMARY;
        craft.enabled = g.bag.scrap >= g.pickCost(g.benchPick) && !g.bag.full();

        // 图鉴：所有武器 / 装备一览，随时能翻
        Btn codex = put(Game.B_CODEX, px + pw - 12f - 24f - 6f - 46f, py + 4f, 46f, 20f);
        codex.label = "图鉴";
        codex.style = ST_PANEL;

        Btn close = put(Game.B_BENCH_CLOSE, px + pw - 12f - 24f, py + 4f, 24f, 20f);
        close.icon = ICON_CLOSE;
        close.style = ST_ICON;
    }

    /** 图鉴：两页 —— 20 把武器（4 类型 × 5 品阶）/ 15 件装备（3 部位 × 5 品阶）。 */
    private void layoutCodex(Game g, float top, float avail) {
        float pw = Math.min(468f, Const.VW - 8f);
        float ph = Math.min(252f, avail - 4f);
        float px = (Const.VW - pw) / 2f;
        float py = top + (avail - ph) / 2f;
        g.panelX = px;
        g.panelY = py;
        g.panelW = pw;
        g.panelH = ph;

        Btn w = put(Game.B_CODEX_TAB, px + 14f, py + 26f, 70f, 18f);
        w.label = "武器 " + (Weapon.TYPES * Item.TIERS);
        w.style = ST_PANEL;
        w.selected = g.codexPage == 0;
        w.toggleOn = g.codexPage == 0;

        Btn e = put(Game.B_CODEX_TAB + 1, px + 14f + 74f, py + 26f, 70f, 18f);
        e.label = "装备 " + ((Item.SLOTS - 1) * Item.TIERS);
        e.style = ST_PANEL;
        e.selected = g.codexPage == 1;
        e.toggleOn = g.codexPage == 1;

        Btn close = put(Game.B_CODEX_CLOSE, px + pw - 12f - 24f, py + 4f, 24f, 20f);
        close.icon = ICON_CLOSE;
        close.style = ST_ICON;
    }

    /** 升级三选一：三张竖卡，点哪张就吃哪条词条。 */
    private void layoutLevelUp(Game g, float top, float avail) {
        float pw = Math.min(444f, Const.VW - 10f);
        float ph = Math.min(206f, avail - 8f);
        float px = (Const.VW - pw) / 2f;
        float py = top + (avail - ph) / 2f;

        float gap = 8f;
        float cw = (pw - 24f - gap * 2f) / 3f;
        float cardTop = py + 36f;
        float ch = ph - 36f - 14f;
        for (int i = 0; i < 3; i++) {
            Btn b = put(Game.B_PERK + i, px + 12f + i * (cw + gap), cardTop, cw, ch);
            int id = g.offers[i];
            b.style = ST_PANEL;
            b.custom = true;
            b.triggerOnDown = true;
            b.enabled = id >= 0;
            b.label = id < 0 ? "" : Perk.NAME[id];
            b.sub = id < 0 ? "" : (id == Perk.HEAL ? "" : g.perkStack(id) + "/" + Perk.MAX);
        }
    }

    private void layoutDead(Game g, float top, float avail) {
        float w = Const.DEAD_W;
        float h = Math.min(Const.DEAD_H, avail - 16f);
        float x = (Const.VW - w) / 2f;
        float y = top + (avail - h) / 2f;
        Btn b = put(Game.B_RESTART, x + (w - 150f) / 2f, y + h - 42f, 150f, 30f);
        b.label = "再来一次";
        b.style = ST_PRIMARY;
        b.icon = ICON_PLAY;
    }

    private void layoutTitle(Game g, float top, float avail) {
        float w = 190f, h = 34f;
        float x = (Const.VW - w) / 2f;
        float y = top + avail * 0.60f;

        Btn start = put(Game.B_START, x, y, w, h);
        start.label = "开始探索";
        start.style = ST_PRIMARY;
        start.icon = ICON_PLAY;
        start.triggerOnDown = true;

        float s = 30f;
        Btn snd = put(Game.B_MUTE, Const.VW - s - 8f, top + 8f, s, s);
        snd.icon = g.muted ? ICON_MUTE : ICON_SOUND;
        snd.style = ST_ICON;
    }
}
