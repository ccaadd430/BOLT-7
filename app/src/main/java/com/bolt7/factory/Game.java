package com.bolt7.factory;

import java.util.ArrayList;
import java.util.Random;

/**
 * 游戏核心（纯 Java，无 Android 依赖，方便在桌面上跑断言与预览）。
 *
 * 一局 = 在一张 3200×2400 像素的大地图上自由探索：
 *   捡电池 → 升级；敲废料堆 → 废铁；打怪 → 装备；
 *   死亡扣 30% 等级，装备与背包保留，重开换一张新地图继续变强。
 */
public final class Game {

    public interface Audio {
        void sfx(int id);

        void buzz(int ms);
    }

    public enum State { TITLE, PLAY, PAUSED, BAG, BENCH, CODEX, DEAD, LEVELUP }

    // 按钮 id（Renderer / Ui / Game 共用）
    public static final int B_START = 1;
    public static final int B_PAUSE = 2;
    public static final int B_RESUME = 3;
    public static final int B_RESTART = 4;
    public static final int B_MUTE = 5;
    public static final int B_BAG = 6;
    public static final int B_CLOSE = 7;
    public static final int B_ATTACK = 8;
    public static final int B_DASH = 9;
    public static final int B_LAYOUT = 10;      // 进入按键布局
    public static final int B_DONE = 11;        // 布局：完成
    public static final int B_RESET = 12;       // 布局：复位
    public static final int B_FIX = 13;         // 布局：固定 / 自由
    public static final int B_JOY = 14;         // 摇杆底盘（可拖动）
    public static final int B_JOYFIX = 15;      // 摇杆：固定 / 跟随
    public static final int B_BENCH = 16;        // 打开合成台
    public static final int B_BENCH_CLOSE = 17;  // 关闭合成台
    /**
     * 合成台：产物选择（v10）。
     * 0..3 = 四类武器（太刀/重斧/长枪/双刃），4..6 = 装甲/伺服/线圈，7 = 全随机。
     * 以前这里只有 4 个"部位"，而且卡片上写的是身上那件，看起来像是"只能升级当前装备"。
     */
    public static final int B_CRAFT_PICK = 400;
    public static final int PICK_RANDOM = 7;
    public static final int PICKS = 8;
    public static final String[] PICK_NAME = {"太刀", "重斧", "长枪", "双刃", "装甲", "伺服", "线圈", "随机"};
    public static final int B_CODEX = 305;       // 打开图鉴
    public static final int B_CODEX_CLOSE = 306; // 关闭图鉴
    public static final int B_CODEX_TAB = 307;   // 图鉴页签：+0 武器 / +1 装备
    public static final int B_EQUIP = 300;
    public static final int B_DISMANTLE = 301;
    public static final int B_CRAFT = 302;
    public static final int B_TITLE = 303;
    public static final int B_ITEM = 100;
    public static final int B_SLOT = 200;
    public static final int B_AIM = 304;        // 暂停：辅助瞄准档位
    public static final int B_PERK = 500;       // 升级三选一（+0/+1/+2）

    public final Audio audio;
    public final Random rnd = new Random();
    public final Input input = new Input();
    public final Ui ui = new Ui();
    public final Bag bag = new Bag();
    public final ArrayList<Particle> particles = new ArrayList<Particle>();
    public final ArrayList<FloatMsg> msgs = new ArrayList<FloatMsg>();
    public final ArrayList<DmgText> dmgTexts = new ArrayList<DmgText>();
    public final Item[] gear = new Item[Item.SLOTS];

    public State state = State.TITLE;
    public World world;
    public Player player;

    public int energy, level = 1, bestLevel = 1;
    public int kills, bestKills, scrapTotal;
    public int levelBefore, levelLost;
    public float levelToastT;
    public int selBag = -1;

    public float camX, camY;
    public float uiTop;              // 可见区域顶部（虚拟画布坐标），由 View 每帧写入
    public float uiBottom = Const.VH; // 可见区域底部，宽屏时小于 VH；底部按键必须锚它
    public float time, stateT, shakeT, shakeAmp, hintT;
    public int tick;
    public boolean muted;
    public int spawnTimer;

    // ---- v8：辅助瞄准 / 顿帧 / 词条 / Boss ----
    /** 0 关 / 1 轻 / 2 强，暂停面板可切换，跨启动保留。 */
    public int aimAssist = 1;
    public float hitStop;                        // 剩余顿帧时间
    public final int[] perks = new int[Perk.COUNT];
    public final int[] offers = {-1, -1, -1};    // 本次升级的三张卡
    public int cores;                            // 已击杀 Boss 数
    public float bossBarT;                       // Boss 血条淡出计时
    public Enemy bossFocus;                      // 当前要显示血条的 Boss

    // ---- v9：幸运词条（反弹 / 闪躲 / 庇护）的状态 ----
    /** 庇护护盾的充能进度（秒），达到 aegisCd() 就绪。 */
    public float aegisT;
    public float aegisFlash;                     // 刚吃掉一次伤害的白闪
    public float dodgeFlash;                     // 闪避残影计时

    // ---- 按键布局（v6）：偏移按按钮 id 存，固定模式下禁止拖动 ----
    public boolean btnFixed = true;
    public boolean joyFixed;         // 摇杆：true = 底盘钉住，false = 按下位置起杆（v3 手感）
    public boolean customizing;
    /** 合成台当前选中的产物：0..3 武器类型 / 4 装甲 / 5 伺服 / 6 线圈 / 7 随机。 */
    public int benchPick = 0;
    /** 图鉴页：0 武器 / 1 装备。 */
    public int codexPage;
    /** 从哪一页打开图鉴，关掉时回到那一页（背包或合成台）。 */
    private State codexBack = State.BAG;
    /** 当前面板的矩形（Ui 布局时写入，Renderer 按它画，避免两边各算一套坐标）。 */
    public float panelX, panelY, panelW, panelH;
    private final float[] offX = new float[24];
    private final float[] offY = new float[24];
    private int dragPtr = -1;         // 正在拖动按钮的手指
    private int dragBtnId = -1;       // 拖的是哪一个
    private float dragDX, dragDY;     // 手指相对按钮左上角的偏移

    public Game(Audio audio) {
        this.audio = audio;
    }

    // ------------------------------------------------------------ 属性

    public static int needFor(int lv) {
        return Const.LEVEL_NEED_BASE + lv * Const.LEVEL_NEED_STEP;
    }

    public int needNow() {
        return needFor(level);
    }

    public Item gear(int slot) {
        return gear[slot];
    }

    public int gearTier(int slot) {
        Item it = gear[slot];
        return it == null ? -1 : it.tier;
    }

    /** 当前武器类型（0 太刀 / 1 重斧 / 2 长枪 / 3 双刃）。 */
    public int weaponType() {
        Item it = gear[Item.ATK];
        return it == null ? Weapon.KATANA : it.type;
    }

    /** 单次挥击伤害：品阶数值 × 类型倍率 × 词条加成。 */
    public int attack() {
        Item it = gear[Item.ATK];
        if (it == null) return 1;
        float v = it.value() * Weapon.dmg(it.type) * (1f + perks[Perk.DMG] * Perk.STEP[Perk.DMG]);
        return Math.max(1, Math.round(v));
    }

    /** 攻击冷却：类型基准 × 品阶提速 × 词条提速。 */
    public float atkCd() {
        Item it = gear[Item.ATK];
        float tierMul = 1f - (it == null ? 0f : it.tier * 0.02f);
        float perkMul = 1f - perks[Perk.CD] * Perk.STEP[Perk.CD];
        return Math.max(0.11f, Weapon.cd(weaponType()) * tierMul * perkMul);
    }

    /** 挥击动画时长，永远短于冷却。 */
    public float atkAnim() {
        return Math.max(0.12f, atkCd() * 0.72f);
    }

    /** 挥击扇形半角（弧度）。 */
    public float atkArc() {
        return Weapon.arc(weaponType()) * (1f + perks[Perk.ARC] * Perk.STEP[Perk.ARC]);
    }

    public float attackSpeedMul() {
        Item it = gear[Item.ATK];
        return 1f - (it == null ? 0 : it.tier * 0.02f);
    }

    /** 数字血量：1 级 40，每级 +5，装甲与"体魄"再叠。 */
    public int maxHp() {
        Item it = gear[Item.HP];
        int armor = it == null ? 0 : it.value() * 4;
        int perk = Math.round(perks[Perk.HP] * Perk.STEP[Perk.HP]);
        return Math.max(1, Const.BASE_HP + (level - 1) * Const.HP_PER_LV + armor + perk);
    }

    /** 移速：基础 84，每升 1 级 +1%，伺服与"轻装"再乘。 */
    public float runSpeed() {
        float base = Const.SPEED * (1f + (level - 1) * Const.LV_SPEED);
        Item it = gear[Item.SPD];
        base *= 1f + (it == null ? 0 : it.value()) / 100f;
        return base * (1f + perks[Perk.SPD] * Perk.STEP[Perk.SPD]);
    }

    /** 幸运：10 级起才有，每级 +0.001；"运势"词条额外加。 */
    public float luck() {
        return Math.max(0, level - Const.LUCK_LV) * Const.LV_LUCK
                + perks[Perk.LUCK] * Perk.STEP[Perk.LUCK];
    }

    public String luckText() {
        if (luck() <= 0f) return "--";
        return String.format(java.util.Locale.US, "%.1f%%", luck() * 100f);
    }

    /** 挥砍距离倍率：每级 +0.05，封顶 3 倍；"延展"再乘。 */
    public float rangeMul() {
        return Math.min(Const.RANGE_MAX, 1f + (level - 1) * Const.LV_RANGE);
    }

    public float atkRange() {
        return Weapon.range(weaponType()) * rangeMul()
                * (1f + perks[Perk.RANGE] * Perk.STEP[Perk.RANGE]);
    }

    /** 怪物伤害倍率：1 级 0.6 倍，每级 +0.055。 */
    public float enemyDmgMul() {
        return Const.DMG_BASE + Const.DMG_PER_LV * level;
    }

    public float dashCd() {
        float base = Const.DASH_CD * (1f - Math.min(0.30f, level * 0.008f));
        Item it = gear[Item.DASH];
        if (it != null) base *= 1f - it.value() / 100f;
        base *= 1f - perks[Perk.DASH] * Perk.STEP[Perk.DASH];
        return Math.max(0.35f, base);
    }

    /** 命中后敌人被定住多久。 */
    public float hitStun() {
        return Weapon.stun(weaponType()) + perks[Perk.STUN] * Perk.STEP[Perk.STUN];
    }

    /** 招架时长：基础 0.85s + 铁壁词条。 */
    public float guardTime() {
        return Const.GUARD_T + perks[Perk.GUARD] * Perk.STEP[Perk.GUARD];
    }

    // ------------------------------------------------------------ 幸运词条

    /** 闪避率：每层 7%，幸运再补一点；有上限，避免无敌。 */
    public float dodgeChance() {
        if (perks[Perk.DODGE] <= 0) return 0f;
        return Math.min(Const.DODGE_MAX, perks[Perk.DODGE] * Const.DODGE_STEP + luck() * 1.2f);
    }

    /** 庇护护盾的回充时间：层数越高越快，幸运再缩短。 */
    public float aegisCd() {
        if (perks[Perk.AEGIS] <= 0) return Const.AEGIS_CD;
        float cd = Const.AEGIS_CD - 1.6f * (perks[Perk.AEGIS] - 1) - luck() * 20f;
        return Math.max(Const.AEGIS_CD_MIN, cd);
    }

    public boolean aegisReady() {
        return perks[Perk.AEGIS] > 0 && aegisT >= aegisCd();
    }

    /** 护盾充能进度 0..1，给 HUD 画小环。 */
    public float aegisProgress() {
        if (perks[Perk.AEGIS] <= 0) return 0f;
        return clamp(aegisT / Math.max(0.01f, aegisCd()), 0f, 1f);
    }

    /**
     * 受伤前的最后一道闸：庇护护盾优先，其次闪避。
     * 返回 true 表示这次伤害被完全吃掉，调用方不要再扣血。
     */
    public boolean absorbHit() {
        if (player == null) return false;
        if (aegisReady()) {
            aegisT = 0f;
            aegisFlash = 0.7f;
            player.invuln = Math.max(player.invuln, 0.45f);
            burst(player.x, player.y, 18, 0xFF8FB4FF, 120f, 0.55f);
            popDamage(player.x, player.y - 20, "庇护", 0xFFBBD4FF, 8, 0.7f);
            addShake(2.4f, 0.18f);
            if (audio != null) audio.sfx(Const.SFX_GUARD);
            return true;
        }
        float dodge = dodgeChance();
        if (dodge > 0f && rnd.nextFloat() < dodge) {
            dodgeFlash = 0.45f;
            player.invuln = Math.max(player.invuln, 0.35f);
            burst(player.x, player.y, 10, 0xFF6FE3A6, 90f, 0.4f);
            popDamage(player.x, player.y - 20, "闪避", 0xFFA8F5CE, 8, 0.65f);
            if (audio != null) audio.sfx(Const.SFX_DASH);
            return true;
        }
        return false;
    }

    /** 离某点最近的活着的敌人，反弹索敌用。 */
    public Enemy nearestEnemy(float x, float y, float range) {
        if (world == null) return null;
        Enemy best = null;
        float bd = range;
        for (int i = 0; i < world.enemies.size(); i++) {
            Enemy en = world.enemies.get(i);
            if (!en.alive) continue;
            float d = (float) Math.hypot(en.x - x, en.y - y);
            if (d < bd) {
                bd = d;
                best = en;
            }
        }
        return best;
    }

    /**
     * 辅助瞄准：在玩家朝向两侧 ±AIM_ARC 内找最近的敌人，把挥击角吸过去。
     * 手机上没有鼠标，这一层是"打得中"的关键；强度可关。
     */
    public float aimAngle() {
        if (player == null) return 0f;
        float base = (float) Math.atan2(player.faceY, player.faceX);
        if (aimAssist <= 0 || world == null) return base;
        float lim = Const.AIM_ARC[aimAssist];
        float best = lim + 1f, bestA = base;
        float reach = atkRange() * 1.4f + 14f;
        for (int i = 0; i < world.enemies.size(); i++) {
            Enemy en = world.enemies.get(i);
            if (!en.alive) continue;
            float dx = en.x - player.x, dy = en.y - player.y;
            float d = (float) Math.hypot(dx, dy);
            if (d > reach || d < 0.01f) continue;
            float a = (float) Math.atan2(dy, dx);
            float off = Math.abs(wrapPi(a - base));
            if (off < best) {
                best = off;
                bestA = a;
            }
        }
        return bestA;
    }

    public void cycleAim() {
        aimAssist = (aimAssist + 1) % 3;
        ui.layout(this);
    }

    private static float wrapPi(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a < -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }

    public Item selectedItem() {
        return bag.get(selBag);
    }

    // ------------------------------------------------------------ 流程

    public void startRun() {
        state = State.PLAY;
        stateT = 0f;
        time = 0f;
        energy = 0;
        kills = 0;
        selBag = -1;
        levelToastT = 0f;
        levelLost = 0;
        hintT = 8f;
        particles.clear();
        msgs.clear();
        dmgTexts.clear();

        customizing = false;
        benchPick = 0;
        hitStop = 0f;
        offers[0] = offers[1] = offers[2] = -1;
        bossFocus = null;
        bossBarT = 0f;
        aegisT = 0f;
        aegisFlash = 0f;
        dodgeFlash = 0f;
        world = new World(rnd.nextLong());
        for (int i = 0; i < world.enemies.size(); i++) {
            Enemy en = world.enemies.get(i);
            en.scaleTo(level, en.zone);
        }
        player = new Player(world.spawnX, world.spawnY);
        player.hp = maxHp();
        player.spawnProtect = 1.2f;
        if (gear[Item.ATK] == null) gear[Item.ATK] = Item.starter();
        // v9：所有部位从白装起步。更高品阶只能靠掉落 / 合成，而不是一开局就自带紫装
        for (int i = 0; i < Item.SLOTS; i++) {
            if (gear[i] == null) {
                gear[i] = new Item(i, Item.WHITE, i == Item.ATK ? Weapon.KATANA : 0);
            }
        }

        camX = clamp(player.x - Const.VW / 2f, 0f, Const.MAP_W * Const.TILE - Const.VW);
        camY = clamp(player.y - Const.VH / 2f, 0f, Const.MAP_H * Const.TILE - Const.VH);
        ui.layout(this);
    }

    public void toTitle() {
        state = State.TITLE;
        stateT = 0f;
        world = null;
        player = null;
        particles.clear();
        msgs.clear();
        dmgTexts.clear();
        selBag = -1;
        ui.layout(this);
    }

    public void pause() {
        if (state == State.PLAY) {
            customizing = false;
            state = State.PAUSED;
            stateT = 0f;
            stopMoving();
            ui.layout(this);
        }
    }

    private void stopMoving() {
        input.moveX = 0f;
        input.moveY = 0f;
        input.joyActive = false;
        input.joyDX = 0f;
        input.joyDY = 0f;
        joyId = -1;
    }

    public void resume() {
        if (state == State.CODEX) {
            closeCodex();
            return;
        }
        if (state == State.PAUSED || state == State.BAG || state == State.BENCH) {
            state = State.PLAY;
            stateT = 0f;
            ui.layout(this);
        }
    }

    public void togglePause() {
        if (state == State.PLAY) pause();
        else if (state == State.CODEX) closeCodex();
        else if (state == State.PAUSED || state == State.BAG || state == State.BENCH) resume();
    }

    public void openBag() {
        if (state == State.PLAY) {
            state = State.BAG;
            selBag = -1;
            stopMoving();
            ui.layout(this);
        }
    }

    public void closeBag() {
        if (state == State.BAG) {
            state = State.PLAY;
            selBag = -1;
            ui.layout(this);
        }
    }

    /** 玩家脚下附近的合成台，没有就返回 null。 */
    public Bench benchNear() {
        if (world == null || player == null) return null;
        for (int i = 0; i < world.benches.size(); i++) {
            Bench b = world.benches.get(i);
            if (Math.hypot(b.x - player.x, b.y - player.y) < Const.BENCH_USE_R) return b;
        }
        return null;
    }

    public void openBench() {
        if (state != State.PLAY) return;
        Bench b = benchNear();
        if (b == null) {
            msg("需要靠近合成台（地图上的橙色工作台）", Pal.UI_WARN);
            return;
        }
        b.seen = true;
        state = State.BENCH;
        stateT = 0f;
        stopMoving();
        ui.layout(this);
    }

    public void closeBench() {
        if (state == State.BENCH) {
            state = State.PLAY;
            stateT = 0f;
            ui.layout(this);
        }
    }

    /**
     * 在合成台造一件指定产物：废铁 → 背包。
     *
     * pick 见 {@link #PICK_NAME}：前 4 个是武器类型，中间 3 个是部位，最后一个是全随机。
     * 品阶永远随机（等级与幸运抬权重）——"造什么"由你定，"造出多好"由运气定，
     * 这样既不会因为随机而拿不到想要的手感，也没把"刷装备"这件事直接买断。
     */
    public void craftAt(int pick) {
        if (state != State.BENCH) return;
        if (benchNear() == null) {
            msg("离开合成台了", Pal.UI_WARN);
            closeBench();
            return;
        }
        pick = Math.max(0, Math.min(PICKS - 1, pick));
        int cost = pick == PICK_RANDOM ? Const.CRAFT_COST_RANDOM : Const.CRAFT_COST;
        if (bag.scrap < cost) {
            msg("废铁不够：需要 " + cost + "，现有 " + bag.scrap, Pal.UI_WARN);
            return;
        }
        if (bag.full()) {
            msg("背包已满，无法合成", Pal.UI_WARN);
            return;
        }
        bag.scrap -= cost;
        int slot = pick == PICK_RANDOM ? rnd.nextInt(Item.SLOTS)
                : pick < 4 ? Item.ATK : pick - 3;
        // 武器：选了类型就照做，随机档才让类型也一起抽
        int type = slot != Item.ATK ? 0
                : pick < 4 ? pick : rnd.nextInt(Weapon.TYPES);
        int tier = rollTier(false, 0);
        Item it = new Item(slot, tier, type);
        bag.add(it);
        msg("合成 " + it.name() + "　" + it.effect(), it.color());
        burst(player.x, player.y, 14, it.color(), 90f, 0.5f);
        if (audio != null) audio.sfx(Const.SFX_EQUIP);
        ui.layout(this);
    }

    /** 单个产物的价格（随机档便宜一块）。 */
    public int pickCost(int pick) {
        return pick == PICK_RANDOM ? Const.CRAFT_COST_RANDOM : Const.CRAFT_COST;
    }

    // ------------------------------------------------------------ 图鉴

    /** 图鉴：背包或合成台里都能翻，不消耗任何东西。 */
    public void openCodex() {
        if (state != State.BAG && state != State.BENCH) return;
        codexBack = state;
        state = State.CODEX;
        stateT = 0f;
        ui.layout(this);
    }

    public void closeCodex() {
        if (state != State.CODEX) return;
        state = codexBack == State.BENCH ? State.BENCH : State.BAG;
        stateT = 0f;
        ui.layout(this);
    }

    public void codexTab(int page) {
        codexPage = page == 0 ? 0 : 1;
        ui.layout(this);
    }

    public void toggleMute() {
        muted = !muted;
        ui.layout(this);
    }

    // ------------------------------------------------------------ 主循环

    public void update(float dt, Input in) {
        tick++;
        time += dt;
        stateT += dt;
        if (shakeT > 0f) shakeT -= dt;
        if (hintT > 0f) hintT -= dt;
        if (levelToastT > 0f) levelToastT -= dt;
        updateParticles(dt);
        updateMsgs(dt);
        updateDmgTexts(dt);
        ui.tick(dt);
        ui.layout(this);

        Input.Ev e;
        while ((e = in.next()) != null) {
            handlePointer(e);
        }
        ui.layout(this);

        // 攻击键按住 = 连续挥击（冷却由武器决定）
        Ui.Btn atkBtn = ui.byId(B_ATTACK);
        if (state == State.PLAY && !customizing && atkBtn != null && atkBtn.down) {
            input.attackPressed = true;
        }

        if (customizing) {
            // 布局模式：世界停住，只有按钮可以拖
            if (player != null) player.atkT = Math.max(0f, player.atkT - dt);
            return;
        }

        if (state != State.PLAY) {
            if (player != null) player.atkT = Math.max(0f, player.atkT - dt);
            return;
        }

        // 顿帧：命中瞬间把世界时间压到 10%，输入与 UI 不受影响
        float wdt = dt;
        if (hitStop > 0f) {
            hitStop -= dt;
            wdt = dt * Const.HITSTOP_SCALE;
        }
        if (bossBarT > 0f) bossBarT -= dt;
        if (aegisFlash > 0f) aegisFlash -= dt;
        if (dodgeFlash > 0f) dodgeFlash -= dt;
        if (perks[Perk.AEGIS] > 0 && aegisT < aegisCd()) {
            aegisT = Math.min(aegisCd(), aegisT + wdt);
        }

        player.update(wdt, in, world.map, this);
        if (state != State.PLAY) return;

        Bench nb = benchNear();
        if (nb != null) nb.seen = true;

        updateEnemies(wdt);
        updatePlasmas(wdt);
        updateNodes(wdt);
        updateBatteries(wdt);
        updateLoot(wdt);
        resolveCombat();
        updateSpawner(wdt);
        updateBossFocus();
        updateCamera(dt);
    }

    // ------------------------------------------------------------ 指针

    private void handlePointer(Input.Ev e) {
        switch (e.type) {
            case Input.DOWN:
                onDown(e.id, e.x, e.y);
                break;
            case Input.MOVE:
                onMove(e.id, e.x, e.y);
                break;
            case Input.UP:
                onUp(e.id, e.x, e.y);
                break;
            default:
                ui.release();
                input.joyActive = false;
                input.moveX = input.moveY = 0f;
                joyId = -1;
                break;
        }
    }

    private int joyId = -1;
    private float joyOx, joyOy;

    /** 点到了脚边的合成台？把屏幕坐标换回世界坐标再判圆。 */
    private boolean tapBench(float x, float y) {
        if (world == null) return false;
        float wx = x + camX, wy = y + camY;
        for (int i = 0; i < world.benches.size(); i++) {
            Bench b = world.benches.get(i);
            if (Math.hypot(b.x - wx, b.y - 8f - wy) < 20f
                    && Math.hypot(b.x - player.x, b.y - player.y) < Const.BENCH_USE_R) {
                return true;
            }
        }
        return false;
    }

    private void onDown(int id, float x, float y) {
        if (customizing) {
            // 摇杆底盘不参与 ui.hit（否则会挡住左半屏），单独用圆形判定
            Ui.Btn joy = ui.byId(B_JOY);
            if (joy != null && Math.hypot(x - joy.cx(), y - joy.cy()) <= Const.JOY_R + 8f) {
                // 不写 down 状态：摇杆底盘由 drawJoystick 自己画，走 ui.press 会留下"按下"残影
                if (!btnFixed) {
                    dragPtr = id;
                    dragBtnId = B_JOY;
                    dragDX = joy.cx() - x;
                    dragDY = joy.cy() - y;
                }
                return;
            }
            Ui.Btn cb = ui.hit(x, y);
            if (cb == null) return;
            ui.press(cb, id, x, y);
            if (movable(cb.id) && !btnFixed) {
                dragPtr = id;
                dragBtnId = cb.id;
                dragDX = cb.x - x;
                dragDY = cb.y - y;
            } else if (cb.triggerOnDown) {
                cb.fired = true;
                fire(cb.id);
            }
            return;
        }
        if (joyId >= 0 && ui.downButton() == null) {
            // 摇杆已占用，第二根手指按到按钮也能用
        }
        Ui.Btn b = ui.hit(x, y);
        if (b != null && b.enabled) {
            ui.press(b, id, x, y);
            if (b.triggerOnDown) {
                b.fired = true;
                fire(b.id);
            }
            return;
        }
        if (state == State.PLAY && tapBench(x, y)) {
            openBench();
            return;
        }
        // 固定摇杆：只有按在底盘范围内才起杆，基点钉死在底盘圆心
        if (state == State.PLAY && joyFixed && joyId < 0) {
            Ui.Btn jb = ui.byId(B_JOY);
            if (jb != null && Math.hypot(x - jb.cx(), y - jb.cy()) <= Const.JOY_R + 12f) {
                joyId = id;
                joyOx = jb.cx();
                joyOy = jb.cy();
                input.joyActive = true;
                input.joyX = joyOx;
                input.joyY = joyOy;
                input.joyDX = 0f;
                input.joyDY = 0f;
                input.moveX = 0f;
                input.moveY = 0f;
                return;
            }
        }
        // 跟随手感：左半屏任意位置按下就出摇杆，位置跟着手指，不要求先找准圆圈
        if (state == State.PLAY && !joyFixed && joyId < 0 && x < Const.VW * 0.5f
                && y > uiTop + 44f && y < uiBottom - 4f) {
            joyId = id;
            joyOx = clamp(x, 36f, Const.VW * 0.6f);
            joyOy = clamp(y, uiTop + 62f, uiBottom - 42f);
            input.joyActive = true;
            input.joyX = joyOx;
            input.joyY = joyOy;
            input.joyDX = 0f;
            input.joyDY = 0f;
            input.moveX = 0f;
            input.moveY = 0f;
        } else if (state != State.PLAY) {
            // 面板空白区域：不做任何事，避免误触
        }
    }

    private void onMove(int id, float x, float y) {
        if (customizing && id == dragPtr && dragBtnId >= 0) {
            Ui.Btn db = ui.byId(dragBtnId);
            if (db != null) {
                if (dragBtnId == B_JOY) {
                    // 摇杆按圆心拖，夹在可见带里，别把底盘拖出屏幕
                    float cx = clamp(x + dragDX, Const.JOY_R + 2f, Const.VW - Const.JOY_R - 2f);
                    float cy = clamp(y + dragDY,
                            uiTop + Const.JOY_R + 2f, uiBottom - Const.JOY_R - 2f);
                    setOffset(B_JOY, cx - db.baseX - db.w / 2f, cy - db.baseY - db.h / 2f);
                } else {
                    float nx = clamp(x + dragDX, 0f, Const.VW - db.w);
                    float ny = clamp(y + dragDY, uiTop, uiBottom - db.h);
                    setOffset(dragBtnId, nx - db.baseX, ny - db.baseY);
                }
                ui.layout(this);
            }
            return;
        }
        Ui.Btn b = ui.downButton();
        if (b != null && b.pointer == id) {
            float pad = 10f;
            if (x < b.x - pad || x > b.x + b.w + pad || y < b.y - pad || y > b.y + b.h + pad) {
                ui.release();
            }
        }
        if (id != joyId) return;
        float dx = x - joyOx, dy = y - joyOy;
        float len = (float) Math.hypot(dx, dy);
        float max = 34f;
        if (len > max) {
            dx = dx / len * max;
            dy = dy / len * max;
            len = max;
        }
        input.joyDX = dx;
        input.joyDY = dy;
        if (joyFixed) {
            // 固定模式：基点永远在底盘圆心上
            Ui.Btn jb = ui.byId(B_JOY);
            if (jb != null) {
                joyOx = jb.cx();
                joyOy = jb.cy();
            }
        } else {
            // 跟随模式：基点跟着手指走，但慢一点，避免抖动；夹在可见带里，别飘出屏幕
            joyOx += (x - dx - joyOx) * 0.12f;
            joyOy += (y - dy - joyOy) * 0.12f;
            joyOx = clamp(joyOx, 36f, Const.VW * 0.6f);
            joyOy = clamp(joyOy, uiTop + 62f, uiBottom - 42f);
        }
        input.joyX = joyOx;
        input.joyY = joyOy;

        float dead = 7f;
        if (len < dead) {
            input.moveX = 0f;
            input.moveY = 0f;
        } else {
            float k = Math.min(1f, (len - dead) / (max - dead));
            input.moveX = dx / len * k;
            input.moveY = dy / len * k;
        }
    }

    private void onUp(int id, float x, float y) {
        if (customizing && id == dragPtr) {
            dragPtr = -1;
            dragBtnId = -1;
            ui.release();
            return;
        }
        Ui.Btn b = ui.downButton();
        if (b != null && b.pointer == id) {
            boolean inside = b.contains(x, y);
            boolean fired = b.fired;
            ui.release();
            if (inside && b.enabled && !fired) fire(b.id);
        }
        if (id == joyId) {
            joyId = -1;
            input.joyActive = false;
            input.joyDX = 0f;
            input.joyDY = 0f;
            input.moveX = 0f;
            input.moveY = 0f;
        }
    }

    // ------------------------------------------------------------ 按键布局

    /** 只有这几个键允许拖动。 */
    public static boolean movable(int id) {
        return id == B_ATTACK || id == B_DASH || id == B_PAUSE || id == B_BAG
                || id == B_MUTE || id == B_JOY;
    }

    public float offX(int id) {
        return id >= 0 && id < offX.length ? offX[id] : 0f;
    }

    public float offY(int id) {
        return id >= 0 && id < offY.length ? offY[id] : 0f;
    }

    public void setOffset(int id, float dx, float dy) {
        if (!movable(id)) return;
        offX[id] = dx;
        offY[id] = dy;
    }

    public void resetLayout() {
        for (int i = 0; i < offX.length; i++) {
            offX[i] = 0f;
            offY[i] = 0f;
        }
        msg("按键位置已复位", Pal.UI_TEXT);
    }

    public void enterLayout() {
        state = State.PLAY;          // 从暂停/背包直接进布局，世界仍然停着
        customizing = true;
        stateT = 0f;
        stopMoving();
        dragPtr = -1;
        dragBtnId = -1;
        ui.layout(this);
    }

    public void exitLayout() {
        customizing = false;
        dragPtr = -1;
        dragBtnId = -1;
        ui.layout(this);
    }

    public void toggleFixed() {
        btnFixed = !btnFixed;
        msg(btnFixed ? "按键：固定" : "按键：可拖动", Pal.UI_TEXT);
        ui.layout(this);
    }

    public void toggleJoyFixed() {
        joyFixed = !joyFixed;
        stopMoving();
        msg(joyFixed ? "摇杆：固定底盘（只有按底盘才动）" : "摇杆：跟随手指（左半屏任意位置）", Pal.UI_TEXT);
        ui.layout(this);
    }

    // ------------------------------------------------------------ 按钮

    public void fire(int id) {
        if (customizing && id != B_DONE && id != B_RESET && id != B_FIX && id != B_JOYFIX) return;
        if (audio != null && id != B_ATTACK && id != B_DASH) audio.sfx(Const.SFX_SELECT);

        if (id == B_MUTE) {
            toggleMute();
            return;
        }
        if (id == B_AIM) {
            cycleAim();
            return;
        }
        if (id >= B_PERK && id < B_PERK + 3) {
            choosePerk(id - B_PERK);
            return;
        }
        if (id == B_START) {
            startRun();
            return;
        }
        if (id == B_PAUSE) {
            pause();
            return;
        }
        if (id == B_RESUME) {
            resume();
            return;
        }
        if (id == B_RESTART) {
            startRun();
            return;
        }
        if (id == B_TITLE) {
            toTitle();
            return;
        }
        if (id == B_BAG) {
            openBag();
            return;
        }
        if (id == B_CLOSE) {
            closeBag();
            return;
        }
        if (id == B_LAYOUT) {
            enterLayout();
            return;
        }
        if (id == B_DONE) {
            exitLayout();
            pause();
            return;
        }
        if (id == B_RESET) {
            resetLayout();
            return;
        }
        if (id == B_FIX) {
            toggleFixed();
            return;
        }
        if (id == B_JOYFIX) {
            toggleJoyFixed();
            return;
        }
        if (id == B_BENCH) {
            openBench();
            return;
        }
        if (id == B_BENCH_CLOSE) {
            closeBench();
            return;
        }
        if (id >= B_CRAFT_PICK && id < B_CRAFT_PICK + PICKS) {
            // v9：点产物只负责"选中"，真正花材料要按下面的合成键——避免误触把废铁花掉
            benchPick = id - B_CRAFT_PICK;
            ui.layout(this);
            if (audio != null) audio.sfx(Const.SFX_SELECT);
            return;
        }
        if (id == B_CODEX) {
            openCodex();
            return;
        }
        if (id == B_CODEX_CLOSE) {
            closeCodex();
            return;
        }
        if (id == B_CODEX_TAB || id == B_CODEX_TAB + 1) {
            codexTab(id - B_CODEX_TAB);
            if (audio != null) audio.sfx(Const.SFX_SELECT);
            return;
        }
        if (id == B_ATTACK) {
            if (!customizing) input.attackPressed = true;
            return;
        }
        if (id == B_DASH) {
            input.dashPressed = true;
            return;
        }
        if (id == B_EQUIP) {
            equipSelected();
            return;
        }
        if (id == B_DISMANTLE) {
            dismantleSelected();
            return;
        }
        if (id == B_CRAFT) {
            // 面板里的合成键（BENCH）与背包入口（PLAY）复用同一个 id：
            // 面板里按下去要真的产出，否则这个按钮就只是个摆设。
            if (state == State.BENCH) craftAt(benchPick);
            else craft();
            return;
        }
        if (id >= B_ITEM && id < B_ITEM + Const.BAG_SLOTS) {
            int i = id - B_ITEM;
            if (bag.get(i) != null) selBag = (selBag == i ? -1 : i);
            ui.layout(this);
            return;
        }
        if (id >= B_SLOT && id < B_SLOT + Item.SLOTS) {
            unequip(id - B_SLOT);
            return;
        }
    }

    private void equipSelected() {
        Item it = selectedItem();
        if (it == null) return;
        Item cur = gear[it.slot];
        gear[it.slot] = it;
        bag.remove(selBag);
        if (cur != null) bag.add(cur);
        selBag = -1;
        if (it.slot == Item.HP) player.hp = Math.min(maxHp(), player.hp + it.value());
        msg("已装备 " + it.name() + "　" + it.effect(), it.color());
        burst(player.x, player.y, 12, it.color(), 90f, 0.5f);
        if (audio != null) audio.sfx(Const.SFX_EQUIP);
        ui.layout(this);
    }

    private void unequip(int slot) {
        Item it = gear[slot];
        if (it == null) return;
        if (bag.full()) {
            msg("背包已满，无法卸下", Pal.UI_WARN);
            return;
        }
        gear[slot] = null;
        bag.add(it);
        if (slot == Item.HP) player.hp = Math.min(player.hp, maxHp());
        msg("已卸下 " + it.name(), Pal.UI_TEXT);
        ui.layout(this);
    }

    /** 分解：返还废铁（白 2 … 红 6），是合成台的材料来源。 */
    private void dismantleSelected() {
        Item it = selectedItem();
        if (it == null) return;
        bag.remove(selBag);
        selBag = -1;
        int gain = Const.DISMANTLE_BASE + it.tier;
        bag.scrap += gain;
        scrapTotal += gain;
        msg("分解 " + it.name() + " → 废铁 +" + gain + "（共 " + bag.scrap + "）", Pal.SCRAP);
        burst(player.x, player.y, 12, Pal.SCRAP, 80f, 0.45f);
        burst(player.x, player.y, 6, it.color(), 60f, 0.4f);
        if (audio != null) audio.sfx(Const.SFX_COLLECT);
        ui.layout(this);
    }

    /** 背包里的"合成"按钮：只是把你带到合成台的入口，材料在台子上才花。 */
    private void craft() {
        Bench b = benchNear();
        if (b == null) {
            msg("合成需要合成台：地图上的橙色工作台，点它打开", Pal.UI_WARN);
            return;
        }
        openBench();
    }

    // ------------------------------------------------------------ 更新

    private void updateEnemies(float dt) {
        for (int i = world.enemies.size() - 1; i >= 0; i--) {
            Enemy en = world.enemies.get(i);
            if (!en.alive) {
                world.enemies.remove(i);
                continue;
            }
            if (Math.abs(en.x - player.x) > 700f || Math.abs(en.y - player.y) > 500f) continue;
            en.update(dt, world.map, player, this, rnd);
        }
    }

    private void updatePlasmas(float dt) {
        for (int i = world.plasmas.size() - 1; i >= 0; i--) {
            Plasma p = world.plasmas.get(i);
            p.update(dt, world.map);
            if (!p.alive) {
                burst(p.x, p.y, 3, p.friendly ? Pal.CYAN : Pal.HAZARD_GLOW, 40f, 0.25f);
                world.plasmas.remove(i);
                continue;
            }
            // 反弹回来的弹丸：只打敌人
            if (p.friendly) {
                for (int j = 0; j < world.enemies.size(); j++) {
                    Enemy en = world.enemies.get(j);
                    if (!en.alive) continue;
                    if (Math.hypot(en.x - p.x, en.y - p.y) > Plasma.R + en.radius()) continue;
                    boolean dead = en.damage(p.dmg, p.x, p.y, world.map);
                    en.stun = Math.max(en.stun, 0.08f);
                    popDamage(en.x, en.y - 14, "-" + p.dmg, 0xFFAEE4FF, 7, 0.45f);
                    burst(p.x, p.y, 9, 0xFF7FD4FF, 90f, 0.4f);
                    world.plasmas.remove(i);
                    if (dead) onEnemyKilled(en);
                    if (audio != null) audio.sfx(Const.SFX_HURT);
                    break;
                }
                continue;
            }
            if (Math.hypot(p.x - player.x, p.y - player.y) > 9f) continue;
            if (player.dashT > 0f) {
                burst(p.x, p.y, 6, Pal.CYAN, 70f, 0.3f);
                world.plasmas.remove(i);
            } else if (player.invuln <= 0f) {
                player.hurt(Math.max(1, Math.round(2f * enemyDmgMul())), p.x, p.y, this);
                world.plasmas.remove(i);
                if (player.dead) return;
            }
        }
    }

    private void updateNodes(float dt) {
        for (int i = 0; i < world.nodes.size(); i++) {
            world.nodes.get(i).update(dt);
        }
    }

    private void updateBatteries(float dt) {
        float magnet = level >= 6 ? 26f : 0f;
        for (int i = world.batteries.size() - 1; i >= 0; i--) {
            Battery b = world.batteries.get(i);
            b.update(dt);
            if (b.taken) {
                world.batteries.remove(i);
                continue;
            }
            float d = (float) Math.hypot(b.x - player.x, b.y - player.y);
            if (magnet > 0f && d < 60f && d > 1f) {
                float pull = 140f * (1f - d / 60f) + 30f;
                b.x += (player.x - b.x) / d * pull * dt;
                b.y += (player.y - b.y) / d * pull * dt;
                d = (float) Math.hypot(b.x - player.x, b.y - player.y);
            }
            if (d < 14f) {
                b.taken = true;
                addEnergy(b.value());
                burst(b.x, b.y, 10, Pal.ENERGY, 80f, 0.45f);
                if (audio != null) audio.sfx(Const.SFX_COLLECT);
            }
        }
    }

    private void updateLoot(float dt) {
        for (int i = world.loots.size() - 1; i >= 0; i--) {
            Loot l = world.loots.get(i);
            l.update(dt);
            if (l.taken) {
                world.loots.remove(i);
                continue;
            }
            float d = (float) Math.hypot(l.x - player.x, l.y - player.y);
            if (level >= 6 && d < 70f && d > 1f) {
                float pull = 150f * (1f - d / 70f) + 30f;
                l.x += (player.x - l.x) / d * pull * dt;
                l.y += (player.y - l.y) / d * pull * dt;
                d = (float) Math.hypot(l.x - player.x, l.y - player.y);
            }
            if (d > 16f) continue;
            l.taken = true;
            pickup(l.item);
        }
    }

    private void pickup(Item it) {
        if (bag.add(it)) {
            msg("拾取 " + it.name() + "（背包 " + bag.count() + "/" + Const.BAG_SLOTS + "）", it.color());
            if (audio != null) audio.sfx(Const.SFX_DROP);
        } else {
            int gain = Const.DISMANTLE_BASE + it.tier;
            bag.scrap += gain;
            scrapTotal += gain;
            msg("背包已满，自动分解 → 废铁 +" + gain, Pal.SCRAP);
        }
        ui.layout(this);
    }

    private void resolveCombat() {
        // 玩家挥击：起手很快就判（重武器天然慢），判定角 = 刀光角，二者不再打架
        if (player.atkT > 0f && !player.atkHitDone && player.atkT <= atkAnim() * 0.88f) {
            player.atkHitDone = true;
            float fx = (float) Math.cos(player.atkAng), fy = (float) Math.sin(player.atkAng);
            float cosLim = (float) Math.cos(atkArc());
            boolean hitAny = false;
            for (int i = 0; i < world.enemies.size(); i++) {
                Enemy en = world.enemies.get(i);
                if (!en.alive) continue;
                float dx = en.x - player.x, dy = en.y - player.y;
                float d = (float) Math.hypot(dx, dy);
                if (d > atkRange() + en.radius()) continue;
                float dot = (dx * fx + dy * fy) / Math.max(0.01f, d);
                if (dot < cosLim) continue;
                hitAny = true;
                int dealt = attack();
                boolean dead = en.damage(dealt, player.x, player.y, world.map);
                en.stun = Math.max(en.stun, hitStun() * (en.boss ? 0.22f : 1f));
                int shown = dead ? dealt : en.lastDamage(dealt);
                popDamage(en.x, en.y - 14, "-" + shown,
                        dead ? 0xFFFFD9A0 : (shown < dealt ? Pal.E_SHIELDER : 0xFFF2F6FA), 7, 0.5f);
                guardUp();
                burst(en.x, en.y, dead ? 14 : 5, dead ? en.color() : 0xFFFFFFFF, dead ? 120f : 70f, 0.45f);
                addShake(dead ? 3f : 1.5f, 0.12f);
                if (dead) onEnemyKilled(en);
                else if (audio != null) audio.sfx(Const.SFX_HURT);
            }
            // 挥击也能敲废料堆
            for (int i = 0; i < world.nodes.size(); i++) {
                Node n = world.nodes.get(i);
                if (n.taken) continue;
                float dx = n.x - player.x, dy = n.y - player.y;
                float d = (float) Math.hypot(dx, dy);
                if (d > atkRange() + 8f) continue;
                float dot = (dx * fx + dy * fy) / Math.max(0.01f, d);
                if (dot < cosLim - 0.12f) continue;
                n.hp--;
                n.flash = 0.15f;
                burst(n.x, n.y, 5, Pal.SCRAP, 70f, 0.4f);
                if (n.hp <= 0) {
                    n.taken = true;
                    int gain = 1 + rnd.nextInt(2);
                    bag.scrap += gain;
                    scrapTotal += gain;
                    msg("废铁 +" + gain + "（" + bag.scrap + "）", Pal.SCRAP);
                    if (rnd.nextFloat() < 0.22f) {
                        Item it = rollItem(rnd.nextInt(Item.SLOTS), rollTier(false, 0));
                        world.loots.add(new Loot(n.x, n.y + 4f, it));
                    }
                    if (audio != null) audio.sfx(Const.SFX_KILL);
                } else if (audio != null) {
                    audio.sfx(Const.SFX_HURT);
                }
            }
            // 切弹：所有武器挥出去都能切掉飞来的弹丸；带"反弹"时打回去
            int cut = cutBullets(fx, fy, cosLim);
            if (cut > 0 && !hitAny) hitStop = Math.max(hitStop, 0.022f);
            hitAny = hitAny || cut > 0;
            // 顿帧：打得越重，世界停得越久。这一下"咔"的停顿就是打击感
            if (hitAny) hitStop = Math.max(hitStop, Weapon.stop(weaponType()));
        }

        // 接触伤害
        for (int i = 0; i < world.enemies.size(); i++) {
            Enemy en = world.enemies.get(i);
            if (!en.alive || en.hitCd > 0f) continue;
            float d = (float) Math.hypot(en.x - player.x, en.y - player.y);
            if (d > 10f + en.radius() * 0.6f) continue;
            if (player.dashT > 0f) {
                if (en.damage(attack() + 1, player.x, player.y, world.map)) onEnemyKilled(en);
                en.hitCd = 0.3f;
                continue;
            }
            if (player.invuln > 0f || player.spawnProtect > 0f) continue;
            player.hurt(en.contactDamage(level), en.x, en.y, this);
            en.hitCd = 0.5f;
            if (player.dead) return;
        }
    }

    /**
     * 切弹：扇形范围内飞来的弹丸全部切掉。
     * 有"反弹"词条时改成打回去，自动索敌最近的敌人。
     * 返回切掉的发数（用于顿帧/音效）。
     */
    private int cutBullets(float fx, float fy, float cosLim) {
        int n = 0;
        boolean reflect = perks[Perk.REFLECT] > 0;
        for (int i = world.plasmas.size() - 1; i >= 0; i--) {
            Plasma p = world.plasmas.get(i);
            if (!p.alive || p.friendly) continue;
            float dx = p.x - player.x, dy = p.y - player.y;
            float d = (float) Math.hypot(dx, dy);
            if (d > atkRange() + Const.BULLET_CUT_PAD) continue;
            float dot = (dx * fx + dy * fy) / Math.max(0.01f, d);
            if (dot < cosLim) continue;

            if (reflect) {
                Enemy target = nearestEnemy(p.x, p.y, Const.REFLECT_RANGE);
                float ax = -p.vx, ay = -p.vy;
                if (target != null) {
                    float tx = target.x - p.x, ty = target.y - p.y;
                    float td = Math.max(0.01f, (float) Math.hypot(tx, ty));
                    ax = tx / td;
                    ay = ty / td;
                } else {
                    float al = Math.max(0.01f, (float) Math.hypot(ax, ay));
                    ax /= al;
                    ay /= al;
                }
                p.friendly = true;
                p.vx = ax * Const.REFLECT_V;
                p.vy = ay * Const.REFLECT_V;
                p.dmg = Math.max(3, Math.round((attack() + 3)
                        * (1.15f + 0.25f * perks[Perk.REFLECT])));
                p.t = 0f;
                burst(p.x, p.y, 8, Pal.CYAN, 90f, 0.35f);
                popDamage(p.x, p.y - 10, "反弹", 0xFFAEE4FF, 7, 0.45f);
                if (audio != null) audio.sfx(Const.SFX_GUARD);
            } else {
                world.plasmas.remove(i);
                burst(p.x, p.y, 5, 0xFFEAF7FF, 60f, 0.28f);
                popDamage(p.x, p.y - 8, "切", 0xFFD8E8F5, 7, 0.35f);
            }
            n++;
        }
        if (n > 0) {
            addShake(n >= 2 ? 2.2f : 1.2f, 0.10f);
            if (!reflect && audio != null) audio.sfx(Const.SFX_HURT);
        }
        return n;
    }

    private void onEnemyKilled(Enemy en) {
        kills++;
        bestKills = Math.max(bestKills, kills);
        if (en.elite()) addEnergy(2);
        burst(en.x, en.y, en.elite() ? 26 : 12, en.color(), en.elite() ? 160f : 110f, 0.7f);
        addShake(en.elite() ? 5f : 2.5f, en.elite() ? 0.3f : 0.14f);
        if (audio != null) audio.sfx(Const.SFX_KILL);

        // 汲取：每杀一只回一点血，长局续航全靠它
        if (perks[Perk.LEECH] > 0 && !player.dead) {
            int heal = Math.round(perks[Perk.LEECH] * Perk.STEP[Perk.LEECH]);
            player.hp = Math.min(maxHp(), player.hp + heal);
        }

        if (en.boss) {
            onBossKilled(en);
            return;
        }

        float chance = Math.min(1f, en.dropChance() * (1f + luck() * 4f));
        if (rnd.nextFloat() < chance) {
            int slot = en.elite() ? (rnd.nextBoolean() ? Item.ATK : Item.HP) : rnd.nextInt(Item.SLOTS);
            int tier = rollTier(en.elite(), en.type);
            Item it = rollItem(slot, tier);
            world.loots.add(new Loot(en.x, en.y, it));
            if (audio != null) audio.sfx(Const.SFX_DROP);
            if (tier >= Item.ORANGE) {
                burst(en.x, en.y, 16, it.color(), 130f, 0.6f);
                msg("掉落 " + Item.TIER_NAME[tier] + "：" + it.name(), it.color());
            }
        }
    }

    /** 随机出装：武器槽随机一个类型，其它部位类型无所谓。 */
    public Item rollItem(int slot, int tier) {
        return new Item(slot, tier, slot == Item.ATK ? rnd.nextInt(Weapon.TYPES) : 0);
    }

    /** Boss 倒下：核心 +1、掉三件高品阶、给一大笔废铁，是"推进"的锚点。 */
    private void onBossKilled(Enemy en) {
        cores++;
        bag.scrap += 12;
        scrapTotal += 12;
        for (int i = 0; i < 3; i++) {
            int slot = i == 0 ? Item.ATK : rnd.nextInt(Item.SLOTS);
            int tier = Math.min(Item.RED, Item.ORANGE + (i == 0 ? 1 : 0));
            world.loots.add(new Loot(en.x + (i - 1) * 9f, en.y + 6f, rollItem(slot, tier)));
        }
        if (world != null) world.clearBossSite(en);
        burst(en.x, en.y, 60, 0xFFFFD166, 220f, 1.1f);
        addShake(9f, 0.6f);
        msg("守卫倒下　核心 +1（" + cores + "/" + Const.BOSS_SITES + "）　废铁 +12", 0xFFFFD166);
        bossFocus = null;
        if (audio != null) {
            audio.sfx(Const.SFX_EVOLVE);
            audio.buzz(120);
        }
    }

    /** 品阶掉落：等级越高、怪越强，权重越偏上。 */
    /**
     * 品阶权重：白 / 蓝 / 紫 / 橙 / 红。
     * 抽签与"合成台概率预览"共用这一份数据，免得 UI 写的概率和实际不一致。
     */
    public int[] tierWeights(boolean elite) {
        // 幸运直接换成"品阶权重偏移"：每 0.001 幸运约 +0.3 点，
        // 10 级解锁，30 级时幸运 0.02 → 约 +6 点，肉眼能感觉到紫装变多。
        int luckBonus = Math.round(luck() * 300f);
        int bonus = Math.min(28, level * 3 / 2) + (elite ? 16 : 0) + luckBonus;
        return new int[]{
                Math.max(16, 64 - bonus * 2),
                26,
                10 + bonus,
                3 + bonus / 2,
                1 + bonus / 3
        };
    }

    private int rollTier(boolean elite, int type) {
        int[] w = tierWeights(elite);
        int total = 0;
        for (int v : w) total += v;
        int r = rnd.nextInt(total);
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) return i;
        }
        return 0;
    }

    /** 合成台的产出概率文案：按当前等级/幸运实时算，玩家知道自己在赌什么。 */
    public String craftOdds() {
        // 用"白蓝紫橙红"而不是"普通精良稀有"——玩家看颜色比读词快
        final String[] shortName = {"白", "蓝", "紫", "橙", "红"};
        int[] w = tierWeights(false);
        int total = 0;
        for (int v : w) total += v;
        // 前四档四舍五入，最后一档用 100 减出来，保证总和正好 100%
        int[] pct = new int[w.length];
        int used = 0;
        for (int i = 0; i < w.length - 1; i++) {
            pct[i] = Math.round(w[i] * 100f / total);
            used += pct[i];
        }
        pct[w.length - 1] = Math.max(0, 100 - used);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            if (i > 0) sb.append(" · ");
            sb.append(shortName[i]).append(pct[i]).append('%');
        }
        return sb.toString();
    }

    private void addEnergy(int v) {
        energy += v;
        while (state == State.PLAY && energy >= needNow()) {
            energy -= needNow();
            levelUp();
        }
    }

    /** 升一级并弹出三选一（也供测试/调试调用）。 */
    public void levelUp() {
        level++;
        bestLevel = Math.max(bestLevel, level);
        levelToastT = Const.LEVEL_TOAST_T;
        if (player != null) {
            // 升级回 30% 而不是回满：回满会让"修复"和续航彻底失去意义
            player.hp = Math.min(maxHp(), player.hp + Math.max(4, Math.round(maxHp() * 0.30f)));
            burst(player.x, player.y, 30, Pal.ENERGY, 150f, 0.8f);
        }
        addShake(4f, 0.35f);
        msg("LV " + level + "　选一个词条", Pal.ENERGY);
        if (level == Const.LUCK_LV) msg("解锁幸运：掉落品质提升", 0xFFFFD166);
        if (audio != null) {
            audio.sfx(Const.SFX_EVOLVE);
            audio.buzz(40);
        }
        openLevelUp();
    }

    // ------------------------------------------------------------ 升级三选一

    public void openLevelUp() {
        rollOffers();
        state = State.LEVELUP;
        stateT = 0f;
        stopMoving();
        ui.layout(this);
    }

    /** 抽三张：没满层的词条里随机，不重复；可选项不足时用"修复"补位。 */
    private void rollOffers() {
        boolean specialOK = level >= Const.LUCK_LV;
        int[] pool = new int[Perk.COUNT];
        int n = 0;
        for (int i = 0; i < Perk.COUNT; i++) {
            if (i != Perk.HEAL && perks[i] >= Perk.MAX) continue;
            if (Perk.isSpecial(i) && !specialOK) continue;
            pool[n++] = i;
        }
        if (n == 0) pool[n++] = Perk.HEAL;
        for (int i = 0; i < 3; i++) {
            if (n == 0) {
                offers[i] = -1;
                continue;
            }
            int k = rnd.nextInt(n);
            offers[i] = pool[k];
            pool[k] = pool[n - 1];
            n--;
        }
        // 幸运词条：解锁后额外给一次"出现机会"，幸运越高越常见（不是必出）
        if (specialOK && rnd.nextFloat() < 0.45f + luck() * 6f) {
            boolean has = Perk.isSpecial(offers[0]) || Perk.isSpecial(offers[1]) || Perk.isSpecial(offers[2]);
            if (!has) {
                int[] sp = new int[3];
                int sn = 0;
                for (int id = Perk.REFLECT; id <= Perk.AEGIS; id++) {
                    if (perks[id] < Perk.MAX) sp[sn++] = id;
                }
                if (sn > 0) offers[rnd.nextInt(3)] = sp[rnd.nextInt(sn)];
            }
        }
    }

    public int perkStack(int id) {
        return id < 0 || id >= Perk.COUNT ? 0 : perks[id];
    }

    public void choosePerk(int slot) {
        if (state != State.LEVELUP || slot < 0 || slot > 2) return;
        int id = offers[slot];
        if (id < 0) return;
        if (id == Perk.HEAL) {
            player.hp = Math.min(maxHp(), player.hp + Math.round(maxHp() * Perk.STEP[Perk.HEAL]));
        } else if (perks[id] < Perk.MAX) {
            perks[id]++;
        }
        offers[0] = offers[1] = offers[2] = -1;
        state = State.PLAY;
        stateT = 0f;
        msg("词条 · " + Perk.NAME[id] + (id == Perk.HEAL ? "" : " ×" + perks[id]), Perk.color(id));
        burst(player.x, player.y, 16, Perk.color(id), 110f, 0.6f);
        if (audio != null) audio.sfx(Const.SFX_SELECT);
        ui.layout(this);
        // 能量溢出：一次加了好几个等级，继续弹下一张
        if (energy >= needNow()) openLevelUp();
    }

    private void updateSpawner(float dt) {
        spawnTimer--;
        if (spawnTimer <= 0) {
            spawnTimer = 240;
            if (world.enemies.size() < 30) {
                if (world.spawnEnemyNear(player.x, player.y, rnd, 30)) {
                    Enemy en = world.enemies.get(world.enemies.size() - 1);
                    en.scaleTo(level, en.zone);
                }
            }
        }
        if ((tick % 300) == 0 && world.batteries.size() < 150) {
            world.spawnBatteryNear(player.x, player.y, rnd);
        }
    }

    /** 走进 Boss 巢穴附近就把血条亮起来；离开 1 秒后淡出。 */
    private void updateBossFocus() {
        if (world == null || player == null) return;
        Enemy best = null;
        float bd = 260f;
        for (int i = 0; i < world.enemies.size(); i++) {
            Enemy en = world.enemies.get(i);
            if (!en.boss || !en.alive) continue;
            float d = (float) Math.hypot(en.x - player.x, en.y - player.y);
            if (d < bd) {
                bd = d;
                best = en;
            }
        }
        if (best != null) {
            bossFocus = best;
            bossBarT = 1.2f;
        } else if (bossBarT <= 0f) {
            bossFocus = null;
        }
    }

    private void updateCamera(float dt) {
        float vcY = (uiTop + uiBottom) / 2f;   // 可见带的正中，而不是整张画布的正中
        float tx = player.x - Const.VW / 2f;
        float ty = player.y - vcY;
        tx = clamp(tx, 0f, Const.MAP_W * Const.TILE - Const.VW);
        ty = clamp(ty, 0f, Const.MAP_H * Const.TILE - Const.VH);
        float k = Math.min(1f, dt * Const.CAM_LERP);
        camX += (tx - camX) * k;
        camY += (ty - camY) * k;
    }

    // ------------------------------------------------------------ 事件

    public void onSwing() {
        if (audio != null) audio.sfx(Const.SFX_SWING);
    }

    public void onDash() {
        if (player == null) return;
        burst(player.x, player.y, 8, Pal.CYAN, 90f, 0.35f);
        if (audio != null) audio.sfx(Const.SFX_DASH);
    }

    public void onPlayerHurt(int dmg, boolean blocked, float x, float y) {
        if (blocked) {
            popDamage(x, y - 27, "格挡 -" + dmg, Pal.GUARD, 9, 0.9f);
            burst(x, y, 7, Pal.GUARD, 85f, 0.35f);
            addShake(2f, 0.12f);
            if (audio != null) audio.sfx(Const.SFX_GUARD);
        } else {
            popDamage(x, y - 27, "-" + dmg, 0xFFFF5A48, 10, 0.9f);
            addShake(4f, 0.22f);
            burst(x, y, 9, Pal.HAZARD_RED, 100f, 0.4f);
            if (audio != null) {
                audio.sfx(Const.SFX_HURT);
                audio.buzz(35);
            }
        }
    }

    /** 挥击命中敌人 → 进入招架姿态，0.85 秒内受伤只吃 10%。 */
    private void guardUp() {
        if (player == null) return;
        boolean wasDown = player.guard <= 0f;
        player.guard = Math.max(player.guard, guardTime());
        player.guardFlash = Const.GUARD_FLASH;
        if (wasDown) {
            msg(String.format(java.util.Locale.US, "招架：%.1f 秒内受伤 -90%%", guardTime()), Pal.GUARD);
        }
    }

    public void onChargerDash() {
        if (audio != null) audio.sfx(Const.SFX_SHOOT);
    }

    public void onEnemyArm() {
        if (audio != null) audio.sfx(Const.SFX_SELECT);
    }

    public void onLeap() {
        if (audio != null) audio.sfx(Const.SFX_DASH);
    }

    /** 自爆虫炸开：范围伤害 + 一点废铁。 */
    public void enemyExplode(Enemy en) {
        burst(en.x, en.y, 26, Pal.HAZARD_GLOW, 190f, 0.6f);
        burst(en.x, en.y, 10, 0xFF3A1208, 120f, 0.7f);
        addShake(6f, 0.32f);
        kills++;
        bestKills = Math.max(bestKills, kills);
        bag.scrap += 1;
        scrapTotal += 1;
        msg("自爆虫炸了　废铁 +1", Pal.HAZARD_GLOW);
        if (player != null && player.invuln <= 0f && player.spawnProtect <= 0f
                && Math.hypot(player.x - en.x, player.y - en.y) < Const.BOMB_RADIUS) {
            player.hurt(Math.max(1, Math.round(6f * enemyDmgMul())), en.x, en.y, this);
        }
        if (audio != null) {
            audio.sfx(Const.SFX_HURT);
            audio.buzz(60);
        }
    }

    /** 跳虫落地冲击波。 */
    public void enemyShock(float x, float y) {
        burst(x, y, 12, Pal.WARN_YELLOW, 110f, 0.35f);
        addShake(2.5f, 0.16f);
        if (audio != null) audio.sfx(Const.SFX_LAND);
        if (player != null && player.invuln <= 0f && player.spawnProtect <= 0f
                && Math.hypot(player.x - x, player.y - y) < 26f) {
            player.hurt(Math.max(1, Math.round(4f * enemyDmgMul())), x, y, this);
        }
    }

    public void fireEnemyPlasma(float x, float y, float vx, float vy) {
        world.plasmas.add(new Plasma(x, y, vx, vy));
        if (audio != null) audio.sfx(Const.SFX_SHOOT);
    }

    /** 喷吐者的酸液：慢一点、颜色不同，能一眼看出不是炮台的弹。 */
    public void fireAcid(float x, float y, float vx, float vy) {
        world.plasmas.add(new Plasma(x, y, vx, vy, true));
        if (audio != null) audio.sfx(Const.SFX_SHOOT);
    }

    public void killPlayer() {
        if (state == State.DEAD) return;
        state = State.DEAD;
        stateT = 0f;
        addShake(6f, 0.4f);
        bestLevel = Math.max(bestLevel, level);
        bestKills = Math.max(bestKills, kills);
        if (player != null) {
            player.hp = 0;
            player.dead = true;
        }
        levelBefore = level;
        int lost = Math.max(1, Math.round(level * Const.LEVEL_LOSS));
        if (level <= 1) lost = 0;
        level = Math.max(1, level - lost);
        levelLost = levelBefore - level;
        energy = 0;
        selBag = -1;
        if (player != null) {
            burst(player.x, player.y, 26, Pal.HAZARD_RED, 150f, 0.8f);
            burst(player.x, player.y, 12, Pal.WARN_YELLOW, 100f, 0.6f);
        }
        if (audio != null) {
            audio.sfx(Const.SFX_DEATH);
            audio.buzz(120);
        }
        ui.layout(this);
    }

    public void addShake(float amp, float dur) {
        shakeAmp = Math.max(shakeAmp, amp);
        shakeT = Math.max(shakeT, dur);
    }

    public void burst(float x, float y, int count, int color, float speed, float life) {
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            float s = speed * (0.35f + rnd.nextFloat() * 0.75f);
            particles.add(new Particle(x, y,
                    (float) Math.cos(a) * s, (float) Math.sin(a) * s,
                    life * (0.6f + rnd.nextFloat() * 0.6f), color,
                    rnd.nextInt(2) + 1));
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            if (!particles.get(i).update(dt)) particles.remove(i);
        }
        if (particles.size() > 300) particles.subList(0, particles.size() - 300).clear();
    }

    private void updateMsgs(float dt) {
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (!msgs.get(i).update(dt)) msgs.remove(i);
        }
        while (msgs.size() > 3) msgs.remove(0);
    }

    private void updateDmgTexts(float dt) {
        for (int i = dmgTexts.size() - 1; i >= 0; i--) {
            if (!dmgTexts.get(i).update(dt)) dmgTexts.remove(i);
        }
        while (dmgTexts.size() > 24) dmgTexts.remove(0);
    }

    /** 伤害数字：世界坐标，由渲染器换算到屏幕。 */
    public void popDamage(float x, float y, String text, int color, float size, float life) {
        dmgTexts.add(new DmgText(x, y, text, color, size, life));
    }

    public void msg(String text, int color) {
        msgs.add(new FloatMsg(text, color));
        while (msgs.size() > 3) msgs.remove(0);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public float shakeX() {
        return shakeT > 0f ? (rnd.nextFloat() * 2f - 1f) * shakeAmp : 0f;
    }

    public float shakeY() {
        return shakeT > 0f ? (rnd.nextFloat() * 2f - 1f) * shakeAmp : 0f;
    }
}
