package com.bolt7.factory;

/**
 * 极小的绘图接口。核心渲染代码只依赖它，因此同一份 Renderer 既能在 Android 上画，
 * 也能在桌面 JVM 上用 Java2D 画出 1:1 的预览图（tools/Preview.java）。
 * 这样"预览图"和真机画面走的是同一段布局与绘制逻辑，不会出现预览好看、真机错位。
 */
public interface GC {

    void clear(int color);

    void rect(float x, float y, float w, float h, int color);

    /** 空心矩形，t 为边框粗细（向内收） */
    void frame(float x, float y, float w, float h, int color, float t);

    void circle(float cx, float cy, float r, int color);

    void ring(float cx, float cy, float r, int color, float t);

    void line(float x1, float y1, float x2, float y2, int color, float t);

    /** 圆心、半径、起止角（度，0 = 右，顺时针）、线宽 */
    void arc(float cx, float cy, float r, float startDeg, float sweepDeg, int color, float t);

    /** 文本：y 为行顶，align 0 左 1 中 2 右 */
    void text(String s, float x, float y, int color, float size, boolean bold, int align);

    float textWidth(String s, float size, boolean bold);

    /** 像素图：左上角坐标 */
    void sprite(Spr s, float x, float y);

    /** 像素图缩放（最近邻） */
    void sprite(Spr s, float x, float y, float scale);

    void clip(float x, float y, float w, float h);

    void clipReset();
}
