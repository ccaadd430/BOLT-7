package com.bolt7.factory;

/**
 * 色板。v4 的核心视觉规则（为"背景与陷阱难区分"专门定）：
 *   1. 地面永远低饱和、低对比，明度压在 20%~30%；
 *   2. 障碍物比地面暗一档，但顶部有 1px 冷灰高光，靠轮廓分离；
 *   3. 危险物只能用高饱和暖色（红/橙），并且带黑黄警示边 + 呼吸光；
 *   4. 可拾取物用高饱和冷色（绿/青），与危险色互补；
 *   5. 角色与怪物都描黑边，保证在任何地面上都能读出剪影。
 */
public final class Pal {
    private Pal() {}

    // 场景
    public static final int VOID = 0xFF07090B;
    // 地面：冷灰混凝土，明度统一压得很低；接缝和油渍提供"结构"而不是噪点
    public static final int GROUND_A = 0xFF2A323A;
    public static final int GROUND_B = 0xFF2E363E;
    public static final int GROUND_C = 0xFF262D34;
    public static final int GROUND_DUST = 0xFF333C44;
    public static final int GROUND_SEAM = 0xFF161B20;
    public static final int GROUND_SEAM_HI = 0xFF313A42;
    public static final int GROUND_CRACK = 0xFF181D21;
    public static final int GROUND_OIL = 0xFF262D34;
    public static final int GROUND_MOSS = 0xFF26302A;

    // 障碍：比地面暗一档，靠顶面高光和投影立起来（不再和地面同色）
    public static final int ROCK_DARK = 0xFF10161A;
    public static final int ROCK_MID = 0xFF212A31;
    public static final int ROCK_TOP = 0xFF56666F;
    public static final int WALL_DARK = 0xFF221A16;
    public static final int WALL_MID = 0xFF332720;
    public static final int WALL_HI = 0xFF4A3A2E;

    // 危险
    public static final int HAZARD_DEEP = 0xFF2C0D0A;
    public static final int HAZARD_PLATE = 0xFF3E1210;
    public static final int HAZARD_RED = 0xFFE8452F;
    public static final int HAZARD_GLOW = 0xFFFF7A3C;
    public static final int HAZARD_ARC = 0xFFFFD9A0;
    public static final int WARN_YELLOW = 0xFFF2C14E;

    // 拾取
    public static final int ENERGY = 0xFF7CE04A;
    public static final int ENERGY_DIM = 0xFF3F7A28;
    public static final int SCRAP = 0xFFB9A98C;
    public static final int CYAN = 0xFF46D8E6;

    // 招架/格挡：冷白偏青，与危险红、能量绿都不撞色
    public static final int GUARD = 0xFF8FE9F2;
    public static final int GUARD_DIM = 0xFF2E6B72;

    // 品阶
    public static final int T_WHITE = 0xFFE6EDF3;
    public static final int T_BLUE = 0xFF4C9BE8;
    public static final int T_PURPLE = 0xFFA96BE8;
    public static final int T_ORANGE = 0xFFF08A2C;
    public static final int T_RED = 0xFFE4493C;

    // 角色
    public static final int PLAYER_BODY = 0xFF7C8A96;
    public static final int PLAYER_LIGHT = 0xFFA6B4C0;
    public static final int PLAYER_DARK = 0xFF4A555F;
    public static final int PLAYER_VISOR = 0xFF46D8E6;
    public static final int PLAYER_ACCENT = 0xFFF2C14E;

    // 怪物
    public static final int E_CRAWLER = 0xFFE07A2C;
    public static final int E_DRONE = 0xFF9A6BE0;
    public static final int E_TURRET = 0xFFB6D24A;
    public static final int E_CHARGER = 0xFFE0453C;
    public static final int E_WARDEN = 0xFF8A97A3;
    public static final int E_WARDEN_EYE = 0xFFFF4A3C;
    // v7 新增四种：自爆虫（品红）/ 盾卫（钢蓝）/ 喷吐者（青绿）/ 跳虫（琥珀）
    public static final int E_BOMBER = 0xFFE05A8C;
    public static final int E_SHIELDER = 0xFF6C93A8;
    public static final int E_SPITTER = 0xFF4FD1A5;
    public static final int E_LEAPER = 0xFFE8C24A;

    // 合成台
    public static final int BENCH_STEEL = 0xFF5F6E79;
    public static final int BENCH_DARK = 0xFF232C33;
    public static final int BENCH_GLOW = 0xFFF2A93C;

    // UI
    public static final int UI_BG = 0xF00E1216;
    public static final int UI_PANEL = 0xFF141A20;
    public static final int UI_PANEL_2 = 0xFF1B232A;
    public static final int UI_LINE = 0xFF33414D;
    public static final int UI_LINE_SOFT = 0xFF232E38;
    public static final int UI_BTN = 0xFF1D262E;
    public static final int UI_BTN_DOWN = 0xFF2B3946;
    public static final int UI_TEXT = 0xFFE8EEF4;
    public static final int UI_TEXT_DIM = 0xFF8A99A6;
    public static final int UI_ACCENT = 0xFF46D8E6;
    public static final int UI_WARN = 0xFFE8452F;
    public static final int UI_OK = 0xFF7CE04A;

    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
}
