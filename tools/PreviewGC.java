import com.bolt7.factory.GC;
import com.bolt7.factory.Spr;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;

/** GC 的 Java2D 实现：桌面预览用，和 Android 走同一份 Renderer。 */
public final class PreviewGC implements GC {

    private final Graphics2D g;
    private final HashMap<Spr, BufferedImage> cache = new HashMap<Spr, BufferedImage>();

    public PreviewGC(Graphics2D g) {
        this.g = g;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private Color col(int argb) {
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    @Override
    public void clear(int color) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(col(color));
        g.fillRect(0, 0, 480, 272);
    }

    @Override
    public void rect(float x, float y, float w, float h, int color) {
        g.setColor(col(color));
        g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void frame(float x, float y, float w, float h, int color, float t) {
        g.setColor(col(color));
        g.setStroke(new BasicStroke(t));
        g.draw(new Rectangle2D.Float(x + t / 2, y + t / 2, w - t, h - t));
    }

    @Override
    public void circle(float cx, float cy, float r, int color) {
        g.setColor(col(color));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    @Override
    public void ring(float cx, float cy, float r, int color, float t) {
        g.setColor(col(color));
        g.setStroke(new BasicStroke(t));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, int color, float t) {
        g.setColor(col(color));
        g.setStroke(new BasicStroke(t, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    @Override
    public void arc(float cx, float cy, float r, float startDeg, float sweepDeg, int color, float t) {
        g.setColor(col(color));
        g.setStroke(new BasicStroke(t, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(cx - r, cy - r, r * 2, r * 2, startDeg, -sweepDeg, Arc2D.OPEN));
    }

    @Override
    public void text(String s, float x, float y, int color, float size, boolean bold, int align) {
        g.setColor(col(color));
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, Math.round(size)));
        FontMetrics fm = g.getFontMetrics();
        float w = fm.stringWidth(s);
        float tx = align == 1 ? x - w / 2 : align == 2 ? x - w : x;
        g.drawString(s, tx, y + fm.getAscent());
    }

    @Override
    public float textWidth(String s, float size, boolean bold) {
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, Math.round(size)));
        return g.getFontMetrics().stringWidth(s);
    }

    @Override
    public void sprite(Spr s, float x, float y) {
        g.drawImage(img(s), Math.round(x), Math.round(y), null);
    }

    @Override
    public void sprite(Spr s, float x, float y, float scale) {
        int w = Math.round(s.w * scale), h = Math.round(s.h * scale);
        g.drawImage(img(s), Math.round(x), Math.round(y), w, h, null);
    }

    private BufferedImage img(Spr s) {
        BufferedImage b = cache.get(s);
        if (b == null) {
            b = new BufferedImage(s.w, s.h, BufferedImage.TYPE_INT_ARGB);
            b.setRGB(0, 0, s.w, s.h, s.px, 0, s.w);
            cache.put(s, b);
        }
        return b;
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        g.setClip((int) x, (int) y, (int) w, (int) h);
    }

    @Override
    public void clipReset() {
        g.setClip(null);
    }
}
