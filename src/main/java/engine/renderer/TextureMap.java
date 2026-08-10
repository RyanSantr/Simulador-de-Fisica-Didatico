package engine.renderer;

import engine.math.ColorRGBA;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Textura pequena carregada do classpath para o renderer software.
 * O renderer amostra por face via UV, mantendo o custo baixo.
 */
public final class TextureMap {

    private static final Map<String, TextureMap> CACHE = new ConcurrentHashMap<>();

    private final String resourcePath;
    private final Image image;
    private final PixelReader pixels;
    private final int width;
    private final int height;

    private TextureMap(String resourcePath, Image image) {
        this.resourcePath = resourcePath;
        this.image = image;
        this.pixels = image == null ? null : image.getPixelReader();
        this.width = image == null ? 0 : Math.max(0, (int)Math.round(image.getWidth()));
        this.height = image == null ? 0 : Math.max(0, (int)Math.round(image.getHeight()));
    }

    public static TextureMap load(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) return null;
        return CACHE.computeIfAbsent(resourcePath, TextureMap::read);
    }

    public ColorRGBA sample(double u, double v, ColorRGBA fallback) {
        if (image == null || pixels == null || width < 1 || height < 1) return fallback;
        double wrappedU = u - Math.floor(u);
        double clampedV = Math.max(0.0, Math.min(1.0, v));
        int x = (int)Math.round(wrappedU * Math.max(0, width - 1));
        int y = (int)Math.round(clampedV * Math.max(0, height - 1));
        int argb = pixels.getArgb(x, y);
        double alpha = ((argb >>> 24) & 0xff) / 255.0;
        if (alpha <= 0.01) return fallback;
        return new ColorRGBA(
            ((argb >>> 16) & 0xff) / 255.0,
            ((argb >>> 8) & 0xff) / 255.0,
            (argb & 0xff) / 255.0,
            alpha
        );
    }

    public String getResourcePath() {
        return resourcePath;
    }

    private static TextureMap read(String resourcePath) {
        try (InputStream stream = TextureMap.class.getResourceAsStream(resourcePath)) {
            if (stream == null) return new TextureMap(resourcePath, null);
            Image image = new Image(stream);
            return new TextureMap(resourcePath, image);
        } catch (Exception ex) {
            return new TextureMap(resourcePath, null);
        }
    }
}
