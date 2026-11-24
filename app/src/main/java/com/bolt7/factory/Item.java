package com.bolt7.factory;

/**
 * 装备：4 个槽位 × 5 个品阶（白 / 蓝 / 紫 / 橙 / 红）。
 * 数值只由"槽位 + 品阶"决定，玩家一眼就能判断该不该换。
 */
public final class Item {

    public static final int ATK = 0;    // 武器：攻击力
    public static final int HP = 1;     // 装甲：生命上限
    public static final int SPD = 2;    // 伺服：移速
    public static final int DASH = 3;   // 线圈：冲刺冷却
    public static final int SLOTS = 4;

    public static final int TIERS = 5;
    public static final int WHITE = 0, BLUE = 1, PURPLE = 2, ORANGE = 3, RED = 4;

    public static final int[] TIER_COLOR = {
            0xFFE6EDF3, 0xFF4C9BE8, 0xFFA96BE8, 0xFFF08A2C, 0xFFE4493C
    };
    public static final String[] TIER_NAME = {"普通", "精良", "稀有", "史诗", "传说"};
    public static final String[] SLOT_NAME = {"武器", "装甲", "伺服", "线圈"};
    public static final String[] SLOT_GLYPH = {"武", "甲", "速", "冲"};

    /** 武器名按"类型 × 品阶"走，其它槽位用"品阶 + 槽位名"。 */
    private static final String[] ADJ = {"锈蚀", "精制", "军用", "原型", "湮灭"};

    public final int slot;
    public final int tier;
    /** 武器类型（v8）：只有 ATK 槽有意义，0 太刀 / 1 重斧 / 2 长枪 / 3 双刃 */
    public final int type;

    public Item(int slot, int tier) {
        this(slot, tier, -1);
    }

    public Item(int slot, int tier, int type) {
        this.slot = Math.max(0, Math.min(SLOTS - 1, slot));
        this.tier = Math.max(0, Math.min(TIERS - 1, tier));
        // 旧存档没有类型字段：按品阶派生一个稳定的类型，不至于全变成同一把
        this.type = slot == ATK
                ? Weapon.clampType(type < 0 ? (this.tier * 3 + 1) % Weapon.TYPES : type)
                : 0;
    }

    /** 初始武器：白装太刀，开局就能打，而且出手最快。 */
    public static Item starter() {
        return new Item(ATK, WHITE, Weapon.KATANA);
    }

    /** 武器伤害 2..6；生命上限 +4..+20；移速 +6%..+22%；冲刺冷却 -6%..-26%。 */
    public int value() {
        switch (slot) {
            case ATK: return 2 + tier;
            // v10：原来用 (tier+1)/2 取整，蓝紫、橙红两档数值重复，图鉴上看着像 bug
            case HP: return 1 + tier;
            case SPD: return 6 + tier * 4;
            default: return 6 + tier * 5;
        }
    }

    public String name() {
        if (slot == ATK) return Weapon.name(type, tier);
        return ADJ[tier] + SLOT_NAME[slot];
    }

    public String effect() {
        switch (slot) {
            case ATK: return "伤害 " + value() + " · " + Weapon.desc(type);
            case HP: return "生命 +" + (value() * 4);   // 数字血量：1 点 = 4 HP
            case SPD: return "移速 +" + value() + "%";
            default: return "冲刺冷却 -" + value() + "%";
        }
    }

    public int color() {
        return TIER_COLOR[tier];
    }

    /** 背包格子里显示的短标记。 */
    public String glyph() {
        return SLOT_GLYPH[slot];
    }
}
