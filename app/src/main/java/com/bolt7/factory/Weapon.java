package com.bolt7.factory;

/**
 * 武器类型（v8）：四类手感完全不同的近战武器，和品阶正交。
 *
 * 品阶（白→红）决定伤害数值，类型决定"怎么打"：
 *   太刀  0.24s 一刀，扇角窄，单点爆发快 —— 贴脸连击
 *   重斧  0.62s 一刀，扇角 197°，伤害 ×2.1 —— 抡起来清一片，但起手慢
 *   长枪  0.40s 一刀，射程 47px，扇角 63° —— 站在敌人打不到的地方捅
 *   双刃  0.33s 一刀，均衡，挥砍带双向刀光 —— 万金油
 *
 * 这些数字是"手感"本身，不要随手调：cd 决定节奏，arc 决定容错，
 * range 决定你敢不敢贴脸，lunge 决定挥砍有没有重量。
 */
public final class Weapon {

    public static final int KATANA = 0;
    public static final int AXE = 1;
    public static final int SPEAR = 2;
    public static final int TWIN = 3;
    public static final int TYPES = 4;

    public static final String[] NAME = {"太刀", "重斧", "长枪", "双刃"};
    public static final String[] TAG = {"极快连击", "横扫一片", "超远突刺", "均衡双刃"};

    /** 冷却（秒） */
    public static final float[] CD = {0.24f, 0.62f, 0.40f, 0.33f};
    /** 扇形半角（弧度） */
    public static final float[] ARC = {0.95f, 1.72f, 0.55f, 1.28f};
    /** 1 级基础攻击距离（像素），会再乘等级倍率 */
    public static final float[] RANGE = {25f, 34f, 47f, 29f};
    /** 伤害倍率，乘在装备基础伤害上 */
    public static final float[] DMG = {0.90f, 2.10f, 1.30f, 1.00f};
    /** 挥砍前冲速度（只在推动摇杆时生效） */
    public static final float[] LUNGE = {52f, 78f, 96f, 46f};
    /** 命中顿帧时长（秒）—— 越重越顿 */
    public static final float[] STOP = {0.028f, 0.072f, 0.048f, 0.040f};
    /** 命中硬直（秒）—— 敌人被打停多久 */
    public static final float[] STUN = {0.09f, 0.20f, 0.12f, 0.13f};

    private static final String[][] TIER_NAME = {
            {"锈蚀短刀", "精钢太刀", "高频振刃", "等离子太刀", "湮灭之刃"},
            {"断裂铁斧", "加固巨斧", "液压重斧", "熔核战斧", "裂地毁灭者"},
            {"铁管长矛", "精制长枪", "磁轨刺枪", "相位长枪", "湮灭穿刺者"},
            {"双持短刃", "精钢双刃", "蜂巢双刃", "等离子双刃", "湮灭双刃"}
    };

    private Weapon() {
    }

    public static int clampType(int t) {
        return t < 0 || t >= TYPES ? 0 : t;
    }

    public static String name(int type, int tier) {
        return TIER_NAME[clampType(type)][Math.max(0, Math.min(4, tier))];
    }

    public static float cd(int type) {
        return CD[clampType(type)];
    }

    public static float arc(int type) {
        return ARC[clampType(type)];
    }

    public static float range(int type) {
        return RANGE[clampType(type)];
    }

    public static float dmg(int type) {
        return DMG[clampType(type)];
    }

    public static float lunge(int type) {
        return LUNGE[clampType(type)];
    }

    public static float stop(int type) {
        return STOP[clampType(type)];
    }

    public static float stun(int type) {
        return STUN[clampType(type)];
    }

    /** 动画时长：略短于冷却，收刀后还有一点"空窗"，节奏才不粘。 */
    public static float anim(int type) {
        return Math.max(0.14f, cd(type) * 0.70f);
    }

    /** 一句话说明，背包/合成台里显示。 */
    public static String desc(int type) {
        int t = clampType(type);
        return String.format(java.util.Locale.US, "%.2fs · 距离 %.0f", CD[t], RANGE[t]);
    }
}
