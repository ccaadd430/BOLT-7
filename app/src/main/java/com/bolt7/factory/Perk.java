package com.bolt7.factory;

/**
 * 升级词条（v9）：12 条通用 + 3 条幸运特殊词条，每次升级抽 3 张，同一条可以叠 5 层。
 * 幸运词条（反弹/闪躲/庇护）只有 10 级解锁幸运之后才会进卡池。
 *
 * 设计意图：等级带来的成长从"固定 +5 血 +1% 速"变成"你在塑造自己的构筑"。
 * 每局都在做取舍，才有"再打一局"的动力——这是长线可玩性的核心。
 */
public final class Perk {

    public static final int DMG = 0;      // 伤害
    public static final int CD = 1;       // 攻速
    public static final int RANGE = 2;    // 挥砍距离
    public static final int ARC = 3;      // 扇角
    public static final int HP = 4;       // 生命上限
    public static final int SPD = 5;      // 移速
    public static final int LUCK = 6;     // 幸运
    public static final int GUARD = 7;    // 招架时长
    public static final int DASH = 8;     // 冲刺冷却
    public static final int STUN = 9;     // 命中硬直
    public static final int LEECH = 10;   // 击杀回血
    public static final int HEAL = 11;    // 立即治疗
    // ---- 幸运特殊词条（v9）：只有解锁幸运（10 级）之后才会出现在卡池里 ----
    public static final int REFLECT = 12; // 反弹：切掉的弹丸打回去
    public static final int DODGE = 13;   // 闪躲：概率完全免伤
    public static final int AEGIS = 14;   // 庇护：自动回充的护盾
    public static final int COUNT = 15;

    public static final String[] NAME = {
            "锋利", "疾风", "延展", "横扫",
            "体魄", "轻装", "运势", "铁壁",
            "脉冲", "压制", "汲取", "修复",
            "反弹", "闪躲", "庇护"
    };

    public static final String[] DESC = {
            "伤害 +10%",
            "攻速 +8%",
            "挥砍距离 +7%",
            "扇角 +10%",
            "生命上限 +8",
            "移速 +5%",
            "幸运 +1.5%",
            "招架 +0.15s",
            "冲刺冷却 -8%",
            "命中硬直 +0.04s",
            "击杀回血 +1",
            "立即回复 40% 生命",
            "切掉的弹丸打回敌人",
            "概率完全免疫这次伤害",
            "获得一层自动回充的护盾"
    };

    /** 每层提供的数值，单位与上面说明一致。 */
    public static final float[] STEP = {
            0.10f, 0.08f, 0.07f, 0.10f, 8f, 0.05f, 0.015f, 0.15f, 0.08f, 0.04f, 1f, 0.40f,
            0.25f, 0.07f, 1f
    };

    public static final int MAX = 5;

    private Perk() {
    }

    /** 幸运词条：10 级解锁幸运后才进卡池，卡面用金色描边区分。 */
    public static boolean isSpecial(int id) {
        return id == REFLECT || id == DODGE || id == AEGIS;
    }

    public static String descOf(int id, int stack) {
        if (id == HEAL) return DESC[HEAL];
        if (id == LEECH) return "击杀回血 +" + (stack + 1);
        if (id == REFLECT) return "反掷伤害 ×" + String.format(java.util.Locale.US, "%.1f",
                1.2f + 0.25f * (stack + 1));
        if (id == DODGE) return "闪避率 +" + Math.round(Const.DODGE_STEP * 100) + "%（上限 "
                + Math.round(Const.DODGE_MAX * 100) + "%）";
        if (id == AEGIS) return "护盾 " + secs(stack + 1) + " 秒回充";
        return DESC[id];
    }

    /** 卡面补充说明：只有幸运词条需要多一句"它到底怎么触发"。 */
    public static String note(int id) {
        switch (id) {
            case REFLECT:
                return "切弹 → 反掷，自动索敌";
            case DODGE:
                return "受击判定，成功完全免伤";
            case AEGIS:
                return "就绪时自动挡下 1 次伤害";
            default:
                return "";
        }
    }

    public static String secs(int stack) {
        float cd = Math.max(Const.AEGIS_CD_MIN, Const.AEGIS_CD - 1.6f * (stack - 1));
        return String.format(java.util.Locale.US, "%.0f", cd);
    }

    public static int color(int id) {
        switch (id) {
            case DMG:
                return 0xFFF0703E;
            case CD:
                return 0xFFF2C14E;
            case RANGE:
            case ARC:
                return 0xFF7FD4FF;
            case HP:
                return 0xFFE8453F;
            case SPD:
            case DASH:
                return 0xFF6FE3A6;
            case LUCK:
                return 0xFFF2C14E;
            case GUARD:
                return 0xFF8FB4FF;
            case STUN:
                return 0xFFC79BFF;
            case LEECH:
                return 0xFFE86FA8;
            case REFLECT:
                return 0xFF7FD4FF;
            case DODGE:
                return 0xFF6FE3A6;
            case AEGIS:
                return 0xFF8FB4FF;
            default:
                return 0xFFFF9EBA;
        }
    }
}
