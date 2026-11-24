package com.bolt7.factory;

/**
 * 合成台：地图上的固定设施，走近后点它就能打开合成界面。
 * 只存坐标，绘制与交互都在 Renderer / Game 里。
 */
public final class Bench {

    public final float x, y;
    /** 小地图上显示用：是否已经被玩家发现（靠近过）。 */
    public boolean seen;

    public Bench(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
