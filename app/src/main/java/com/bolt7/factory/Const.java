package com.bolt7.factory;

/** 全局常量。v4 起改为俯视角大地图：自由八向移动，不再有重力与跳跃。 */
public final class Const {
    private Const() {}

    // 虚拟画布。宽固定 480，高 272；高屏不裁切，宽屏按可见高度裁顶部
    public static final int VW = 480;
    public static final int VH = 272;

    /**
     * 宽屏裁切量：屏幕比 16:9 更宽时，纵向装不下整张画布，需要从上下各裁一点。
     * 上下 45/55 分，返回顶部裁掉的高度。GameView、预览工具、测试共用这一份算法，
     * 避免"预览好看、真机按键飞出屏幕"。
     */
    public static float cropTop(int screenW, int screenH) {
        return Math.round(cropTotal(screenW, screenH) * 0.45f);
    }

    public static float cropTotal(int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) return 0f;
        float visH = screenH / (screenW / (float) VW);
        return visH < VH ? VH - visH : 0f;
    }
    public static final int TILE = 16;

    // 世界：320 x 240 格 = 5120 x 3840 像素。四周是断崖（VOID），不是看不见的墙。
    // v7 把地图面积扩大到原来的 2.56 倍，横向要走 60 多秒才到头。
    public static final int MAP_W = 320;
    public static final int MAP_H = 240;
    public static final int EDGE = 3;               // 断崖厚度（格）

    // 移动
    public static final float SPEED = 84f;
    public static final float ACCEL = 900f;
    public static final float FRICTION = 1100f;

    // 冲刺
    public static final float DASH_V = 330f;
    public static final float DASH_T = 0.14f;
    public static final float DASH_CD = 1.15f;

    // 生命（v6 起改数字制）
    public static final int BASE_HP = 40;           // 1 级基础生命
    public static final int HP_PER_LV = 5;          // 每升 1 级 +5
    public static final float INVULN = 1.0f;

    // 每级成长
    public static final float LV_SPEED = 0.01f;     // 移速 +1%/级
    public static final float LV_LUCK = 0.001f;     // 幸运 +0.001/级
    public static final int LUCK_LV = 10;           // 10 级后才有幸运
    public static final float LV_RANGE = 0.05f;     // 挥砍距离 ×+0.05/级
    public static final float RANGE_MAX = 3f;       // 最多 3 倍

    // 自爆虫 / 盾卫 / 跳虫
    public static final float BOMB_FUSE = 0.85f;    // 自爆引信
    public static final float BOMB_RADIUS = 34f;    // 爆炸半径
    public static final float SHIELD_BLOCK = 0.25f; // 正面只吃 25% 伤害
    public static final float SHIELD_ARC = 0.62f;   // 正面判定半角余弦阈值（约 ±52°）

    // 怪物伤害：1 级只有 0.6 倍，之后随等级线性抬高
    public static final float DMG_BASE = 0.55f;
    public static final float DMG_PER_LV = 0.055f;
    public static final float ENEMY_HP_PER_LV = 0.06f;

    // 战斗（v8：节奏由 Weapon 表决定，这里的 ATK_* 只作默认值与旧存档兜底）
    public static final float ATK_CD = 0.24f;      // 太刀基准，其余见 Weapon.CD
    public static final float ATK_ARC = 0.95f;     // 挥击扇形半角（弧度）
    public static final float ATK_RANGE = 25f;
    public static final float ATK_ANIM = 0.17f;
    /** 攻击输入缓冲：冷却期间按下的这一下最多记 0.22 秒，冷却一到立刻出刀。 */
    public static final float ATK_BUFFER = 0.22f;
    /** 命中顿帧：世界时间缩放 + 持续时长见 Weapon.STOP。 */
    public static final float HITSTOP_SCALE = 0.10f;
    /** 辅助瞄准三档：0 关 / 1 轻（±25°）/ 2 强（±42°）。 */
    public static final float[] AIM_ARC = {0f, 0.44f, 0.73f};
    public static final String[] AIM_NAME = {"关闭", "轻微", "明显"};

    // 区域（v8）：以出生点为圆心的三条环带，越外越硬
    public static final int ZONE_R1 = 46;          // 格
    public static final int ZONE_R2 = 92;          // 格
    public static final float[] ZONE_HP = {1f, 1.55f, 2.4f};
    public static final float[] ZONE_DMG = {1f, 1.22f, 1.5f};

    // Boss 巢穴：每区一个，血量按区放大
    public static final int BOSS_SITES = 3;
    public static final float BOSS_HP_MUL = 9f;
    public static final float BOSS_R = 15f;

    // 切弹（v9）：任何武器挥出去都能把飞来的弹丸切掉；"反弹"词条再把它打回去
    public static final float BULLET_CUT_PAD = 10f;   // 切弹判定比刀光远一点，手感更宽容
    public static final float REFLECT_V = 320f;       // 反弹后的弹速
    public static final float REFLECT_RANGE = 260f;   // 反弹时索敌半径

    // 幸运特殊词条（v9）：10 级解锁幸运后才会进卡池
    public static final float DODGE_STEP = 0.07f;     // 闪躲每层 +7%
    public static final float DODGE_MAX = 0.55f;      // 闪避率上限
    public static final float AEGIS_CD = 15f;         // 庇护护盾充能时间
    public static final float AEGIS_CD_MIN = 7f;

    // 招架：攻击命中后短暂进入格挡姿态，挡掉 90% 伤害（至少留 1 点）
    public static final float GUARD_T = 0.85f;
    public static final float GUARD_KEEP = 0.10f;
    public static final float GUARD_FLASH = 0.16f;

    // 无限等级：第 n 级需要 need(n) 点能量
    public static final int LEVEL_NEED_BASE = 6;
    public static final int LEVEL_NEED_STEP = 2;
    public static final float LEVEL_LOSS = 0.30f;
    public static final float LEVEL_TOAST_T = 1.4f;

    // 背包
    public static final int BAG_SLOTS = 16;
    /**
     * 合成价格（v10）：台子上现在能指定"造哪一件"，所以拆成两档。
     * 定向（指定武器类型 / 指定部位）贵一点，随机（部位和类型都抽）便宜一块，
     * 想省钱就赌随机，想要什么就多花一块——两条路都留着。
     */
    public static final int CRAFT_COST = 3;         // 定向合成
    public static final int CRAFT_COST_RANDOM = 2;  // 随机合成

    // 摇杆
    public static final float JOY_R = 32f;          // 固定摇杆底盘半径
    public static final float JOY_REACH = 34f;      // 推杆最大位移
    public static final float JOY_DEAD = 7f;        // 死区

    // 合成台
    public static final float BENCH_USE_R = 30f;    // 多近能开合成台
    public static final int BENCH_COUNT = 10;       // 全图合成台数量

    // 分解：返还废铁 = DIMANTLE_BASE + 品阶（白 2 … 红 6）
    public static final int DISMANTLE_BASE = 2;

    // 相机
    public static final float CAM_LERP = 7f;

    // 死亡面板（Ui 与 Renderer 共用，保证框和按钮永远对得上）
    public static final float DEAD_W = 288f;
    public static final float DEAD_H = 176f;

    // 音频事件
    public static final int SFX_SWING = 0;
    public static final int SFX_COLLECT = 1;
    public static final int SFX_HURT = 2;
    public static final int SFX_DASH = 3;
    public static final int SFX_KILL = 4;
    public static final int SFX_LAND = 5;
    public static final int SFX_DOOR = 6;
    public static final int SFX_CLEAR = 7;
    public static final int SFX_DEATH = 8;
    public static final int SFX_SELECT = 9;
    public static final int SFX_EVOLVE = 10;
    public static final int SFX_SHOOT = 11;
    public static final int SFX_DROP = 12;
    public static final int SFX_EQUIP = 13;
    public static final int SFX_GUARD = 14;
    public static final int SFX_COUNT = 15;
}
