package engine.math;

import javafx.scene.paint.Color;

/**
 * Cor RGBA com utilitários de conversão e manipulação.
 */
public final class ColorRGBA {

    public final double r, g, b, a;

    public ColorRGBA(double r, double g, double b, double a) {
        this.r = clamp(r); this.g = clamp(g);
        this.b = clamp(b); this.a = clamp(a);
    }
    public ColorRGBA(double r, double g, double b) { this(r, g, b, 1.0); }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    public static ColorRGBA fromHex(String hex) {
        Color c = Color.web(hex);
        return new ColorRGBA(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity());
    }

    public static ColorRGBA fromJFX(Color c) {
        return new ColorRGBA(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity());
    }

    /** Cor mais escura (para sombras). */
    public ColorRGBA darker(double factor) {
        return new ColorRGBA(r*factor, g*factor, b*factor, a);
    }

    /** Mistura com outra cor. */
    public ColorRGBA mix(ColorRGBA o, double t) {
        return new ColorRGBA(
            r + (o.r-r)*t, g + (o.g-g)*t,
            b + (o.b-b)*t, a + (o.a-a)*t
        );
    }

    /** Aplica iluminação difusa simples. */
    public ColorRGBA shade(double intensity) {
        double i = Math.max(0.08, intensity);
        return new ColorRGBA(r*i, g*i, b*i, a);
    }

    /** Converte para javafx.scene.paint.Color. */
    public Color toJFX() { return new Color(r, g, b, a); }

    /** Especular → branco aditivo. */
    public ColorRGBA addSpecular(double spec) {
        return new ColorRGBA(r+spec, g+spec, b+spec, a);
    }

    // Cores pré-definidas
    public static final ColorRGBA WHITE   = new ColorRGBA(1,1,1);
    public static final ColorRGBA BLACK   = new ColorRGBA(0,0,0);
    public static final ColorRGBA RED     = new ColorRGBA(1,0.2,0.2);
    public static final ColorRGBA GREEN   = new ColorRGBA(0.2,1,0.4);
    public static final ColorRGBA BLUE    = new ColorRGBA(0.26,0.53,1);
    public static final ColorRGBA YELLOW  = new ColorRGBA(1,0.8,0.1);
    public static final ColorRGBA CYAN    = new ColorRGBA(0.2,1,1);
    public static final ColorRGBA MAGENTA = new ColorRGBA(1,0.2,1);
    public static final ColorRGBA ORANGE  = new ColorRGBA(1,0.55,0.1);
    public static final ColorRGBA PURPLE  = new ColorRGBA(0.6,0.2,1);

    public static final ColorRGBA[] PALETTE = {
        BLUE, RED, GREEN, YELLOW, CYAN, MAGENTA, ORANGE, PURPLE
    };

    @Override
    public String toString() {
        return String.format("RGBA(%.2f,%.2f,%.2f,%.2f)", r, g, b, a);
    }
}
