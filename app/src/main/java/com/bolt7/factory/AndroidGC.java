package com.bolt7.factory;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.HashMap;

/**
 * GC 的 Android 实现：把核心渲染指令翻译成 Canvas 调用。
 *
 * 关键点：逻辑坐标仍是 480×272，但输出画布按设备宽度做**整数倍超采样**
 * （1080p 屏用 3 倍，即 1440×816，再缩到 1080）。上一版是 480 直接拉大到
 * 1080，文字和 1px 线条被插值糊成一片——这就是"太糊"的根因。
 *
 * 这里所有坐标统一乘 s，字号也按 s 放大后光栅化，所以文字是原生清晰度；
 * 像素图用最近邻放大，保持硬边。
 */
public final class AndroidGC implements GC {

    private Canvas c;
    /** 逻辑像素 → 画布像素 */
    private float s = 1f;
    /** 画布内的纵向偏移（宽屏从顶部裁掉的逻辑像素 × s） */
    private float oy = 0f;

    private final Paint fill = new Paint();
    private final Paint stroke = new Paint();
    private final Paint text = new Paint();
    private final RectF rect = new RectF();
    private final Rect dstRect = new Rect();
    private static final Typeface FONT_REG = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface FONT_BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private final HashMap<Spr, Bitmap> cache = new HashMap<Spr, Bitmap>();

    public AndroidGC(Canvas c) {
        this.c = c;
        fill.setStyle(Paint.Style.FILL);
        fill.setAntiAlias(false);
        fill.setFilterBitmap(false);       // 像素图放大必须硬边
        fill.setDither(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setAntiAlias(true);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setAntiAlias(true);
        text.setTypeface(FONT_BOLD);
    }

    public void bind(Canvas canvas) {
        this.c = canvas;
    }

    public void setView(float scale, float offY) {
        this.s = scale;
        this.oy = offY;
    }

    private float X(float v) {
        return v * s;
    }

    private float Y(float v) {
        return v * s + oy;
    }

    private float L(float v) {
        return v * s;
    }

    @Override
    public void clear(int color) {
        c.drawColor(color);
    }

    @Override
    public void rect(float x, float y, float w, float h, int color) {
        fill.setColor(color);
        c.drawRect(X(x), Y(y), X(x + w), Y(y + h), fill);
    }

    @Override
    public void frame(float x, float y, float w, float h, int color, float t) {
        stroke.setColor(color);
        stroke.setStrokeWidth(t * s);
        c.drawRect(X(x) + t * s / 2f, Y(y) + t * s / 2f,
                X(x + w) - t * s / 2f, Y(y + h) - t * s / 2f, stroke);
    }

    @Override
    public void circle(float cx, float cy, float r, int color) {
        fill.setColor(color);
        c.drawCircle(X(cx), Y(cy), r * s, fill);
    }

    @Override
    public void ring(float cx, float cy, float r, int color, float t) {
        stroke.setColor(color);
        stroke.setStrokeWidth(t * s);
        c.drawCircle(X(cx), Y(cy), r * s, stroke);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, int color, float t) {
        stroke.setColor(color);
        stroke.setStrokeWidth(t * s);
        c.drawLine(X(x1), Y(y1), X(x2), Y(y2), stroke);
    }

    @Override
    public void arc(float cx, float cy, float r, float startDeg, float sweepDeg, int color, float t) {
        stroke.setColor(color);
        stroke.setStrokeWidth(t * s);
        rect.set(X(cx - r), Y(cy - r), X(cx + r), Y(cy + r));
        c.drawArc(rect, startDeg, sweepDeg, false, stroke);
    }

    @Override
    public void text(String str, float x, float y, int color, float size, boolean bold, int align) {
        text.setColor(color);
        text.setTextSize(size * s);
        text.setTypeface(bold ? FONT_BOLD : FONT_REG);
        text.setTextAlign(align == 1 ? Paint.Align.CENTER : align == 2 ? Paint.Align.RIGHT : Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(str, X(x), Y(y) - fm.ascent, text);
    }

    @Override
    public float textWidth(String str, float size, boolean bold) {
        text.setTextSize(size * s);
        text.setTypeface(bold ? FONT_BOLD : FONT_REG);
        return text.measureText(str) / s;      // 布局仍按逻辑单位
    }

    @Override
    public void sprite(Spr sp, float x, float y) {
        dstRect.set(Math.round(X(x)), Math.round(Y(y)),
                Math.round(X(x + sp.w)), Math.round(Y(y + sp.h)));
        c.drawBitmap(bitmap(sp), null, dstRect, fill);
    }

    @Override
    public void sprite(Spr sp, float x, float y, float scale) {
        dstRect.set(Math.round(X(x)), Math.round(Y(y)),
                Math.round(X(x + sp.w * scale)), Math.round(Y(y + sp.h * scale)));
        c.drawBitmap(bitmap(sp), null, dstRect, fill);
    }

    private Bitmap bitmap(Spr sp) {
        Bitmap b = cache.get(sp);
        if (b == null) {
            b = Bitmap.createBitmap(sp.px, sp.w, sp.h, Bitmap.Config.ARGB_8888);
            cache.put(sp, b);
        }
        return b;
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        c.save();
        c.clipRect(X(x), Y(y), X(x + w), Y(y + h));
    }

    @Override
    public void clipReset() {
        c.restore();
    }
}
