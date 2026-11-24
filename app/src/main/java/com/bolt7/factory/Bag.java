package com.bolt7.factory;

/** 背包：16 格，只放装备；废铁单独计数（合成材料）。 */
public final class Bag {
    public final Item[] slots = new Item[Const.BAG_SLOTS];
    public int scrap;

    public boolean add(Item it) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = it;
                return true;
            }
        }
        return false;
    }

    public Item get(int i) {
        return i >= 0 && i < slots.length ? slots[i] : null;
    }

    public void remove(int i) {
        if (i >= 0 && i < slots.length) slots[i] = null;
    }

    public int count() {
        int n = 0;
        for (Item s : slots) if (s != null) n++;
        return n;
    }

    public boolean full() {
        for (Item s : slots) if (s == null) return false;
        return true;
    }

    /** 合成时丢掉最差的一件（品阶最低），用于满包替换。 */
    public int worstIndex() {
        int idx = -1, tier = 99;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && slots[i].tier < tier) {
                tier = slots[i].tier;
                idx = i;
            }
        }
        return idx;
    }
}
