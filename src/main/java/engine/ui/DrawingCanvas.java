package engine.ui;

import engine.scene.ObjectFactory;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Canvas de desenho 2D (painel esquerdo).
 * Permite ao usuário esboçar formas que são convertidas em objetos 3D.
 */
public class DrawingCanvas extends Canvas {

    public enum DrawMode { FREE, RECTANGLE, CIRCLE, TRIANGLE }

    private DrawMode mode = DrawMode.RECTANGLE;
    private Color strokeColor = Color.web("#4488ff");

    private boolean drawing = false;
    private double startX, startY, endX, endY;
    private final List<double[]> freePoints = new ArrayList<>();
    private final List<List<double[]>> completedFreeStrokes = new ArrayList<>();

    // Callback quando o usuário finaliza um desenho
    private Consumer<DrawResult> onDrawComplete;

    /** Temporizador do flash de confirmacao (reaproveitado a cada desenho). */
    private final PauseTransition confirmFlash = new PauseTransition(Duration.millis(80));

    /**
     * Distancia minima entre pontos capturados, em pixels.
     *
     * <p>Sem esse filtro, um arrasto lento gera centenas de pontos praticamente
     * no mesmo lugar: eles nao mudam a forma, mas inflam a malha 3D e o custo de
     * repintura.
     */
    private static final double MIN_POINT_SPACING_PX = 2.0;

    /** Resultado do desenho: dimensões normalizadas + tipo. */
    public record DrawResult(
        ObjectFactory.ShapeType suggestedShape,
        double normalizedWidth,
        double normalizedHeight,
        Color color,
        List<double[]> path
    ) {
        public DrawResult(ObjectFactory.ShapeType suggestedShape,
                          double normalizedWidth,
                          double normalizedHeight,
                          Color color) {
            this(suggestedShape, normalizedWidth, normalizedHeight, color, List.of());
        }

        public boolean hasFreePath() {
            return path != null && path.size() > 1;
        }
    }

    public DrawingCanvas(double width, double height) {
        super(width, height);
        widthProperty().addListener((obs, oldValue, newValue) -> repaint());
        heightProperty().addListener((obs, oldValue, newValue) -> repaint());
        confirmFlash.setOnFinished(event -> {
            drawBackground();
            drawStatusMessage("Objeto criado!");
        });
        setupEvents();
        drawBackground();
    }

    private void setupEvents() {
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);
        setOnMouseClicked(this::onMouseClicked);
    }

    // ── Eventos de mouse ───────────────────────────
    private void onMousePressed(MouseEvent e) {
        if (mode == DrawMode.FREE && e.getClickCount() > 1 && !completedFreeStrokes.isEmpty()) {
            return;
        }
        drawing = true;
        startX = e.getX(); startY = e.getY();
        endX = startX; endY = startY;
        freePoints.clear();
        if (mode != DrawMode.FREE) completedFreeStrokes.clear();
        freePoints.add(new double[]{ startX, startY });
    }

    /**
     * Acompanha o arrasto do mouse.
     *
     * <p>No modo livre o traco e desenhado <b>incrementalmente</b>: so o segmento
     * novo vai para o canvas. Redesenhar tudo a cada ponto, como era antes,
     * custava O(pontos ja desenhados) por evento de mouse — o desenho ficava
     * progressivamente mais lento e, com varios tracos acumulados, travava.
     *
     * <p>As formas geometricas continuam com repintura completa: o retangulo, o
     * circulo e o triangulo mudam inteiros a cada movimento, e sao definidos por
     * apenas dois pontos, entao a repintura e barata.
     */
    private void onMouseDragged(MouseEvent e) {
        if (!drawing) return;

        double previousX = endX;
        double previousY = endY;
        endX = e.getX();
        endY = e.getY();

        if (mode != DrawMode.FREE) {
            freePoints.add(new double[]{ endX, endY });
            repaint();
            return;
        }

        // Pontos colados demais nao acrescentam forma, so custo: engordariam a
        // malha 3D e a repintura sem mudar o desenho.
        if (Math.hypot(endX - previousX, endY - previousY) < MIN_POINT_SPACING_PX) {
            endX = previousX;
            endY = previousY;
            return;
        }

        freePoints.add(new double[]{ endX, endY });
        GraphicsContext gc = getGraphicsContext2D();
        gc.setStroke(strokeColor);
        gc.setLineWidth(3.5);
        gc.strokeLine(previousX, previousY, endX, endY);
    }

    private void onMouseReleased(MouseEvent e) {
        if (!drawing) return;
        drawing = false;
        endX = e.getX(); endY = e.getY();

        double minX = Math.min(startX, endX);
        double maxX = Math.max(startX, endX);
        double minY = Math.min(startY, endY);
        double maxY = Math.max(startY, endY);
        if (mode == DrawMode.FREE && !freePoints.isEmpty()) {
            minX = freePoints.stream().mapToDouble(p -> p[0]).min().orElse(minX);
            maxX = freePoints.stream().mapToDouble(p -> p[0]).max().orElse(maxX);
            minY = freePoints.stream().mapToDouble(p -> p[1]).min().orElse(minY);
            maxY = freePoints.stream().mapToDouble(p -> p[1]).max().orElse(maxY);
        }

        double dw = maxX - minX;
        double dh = maxY - minY;

        // Mínimo de 5px para considerar como desenho válido
        if (dw + dh < 5) { repaint(); return; }

        double nw = dw / getWidth();
        double nh = dh / getHeight();

        if (mode == DrawMode.FREE) {
            completedFreeStrokes.add(copyStroke(freePoints));
            freePoints.clear();
            repaint();
            drawStatusMessage("Traço adicionado. Clique em Criar 3D.");
            return;
        }

        ObjectFactory.ShapeType shape = switch (mode) {
            case RECTANGLE -> ObjectFactory.ShapeType.BOX;
            case CIRCLE    -> ObjectFactory.ShapeType.SPHERE;
            case TRIANGLE  -> ObjectFactory.ShapeType.CONE;
            case FREE      -> ObjectFactory.ShapeType.DRAWN;
        };

        if (onDrawComplete != null)
            onDrawComplete.accept(new DrawResult(shape, nw, nh, strokeColor, normalizedPath()));

        // Flash de confirmação
        showConfirmFlash();
    }

    private void onMouseClicked(MouseEvent e) {
        if (mode == DrawMode.FREE && e.getClickCount() > 1) {
            createFreeDrawingObject();
            e.consume();
        }
    }

    /**
     * Converte os tracos desenhados (pixels do canvas) para coordenadas
     * normalizadas 0..1, que o {@link ObjectFactory} transforma em malha 3D.
     *
     * <p>Tracos diferentes sao separados por um ponto {@code NaN}, que funciona
     * como "levantar a caneta": a malha nao liga o fim de um traco ao inicio do
     * proximo. Pontos muito proximos sao descartados para reduzir a contagem de
     * triangulos do objeto final.
     */
    private List<double[]> normalizedPath() {
        if (mode != DrawMode.FREE) return List.of();
        if (completedFreeStrokes.isEmpty() && freePoints.size() < 2) return List.of();

        List<double[]> out = new ArrayList<>();
        for (List<double[]> stroke : completedFreeStrokes) {
            addNormalizedStroke(out, stroke);
        }
        if (!freePoints.isEmpty()) {
            addNormalizedStroke(out, freePoints);
        }

        return out;
    }

    private void addNormalizedStroke(List<double[]> out, List<double[]> stroke) {
        if (stroke.size() < 2) return;
        if (!out.isEmpty()) out.add(new double[]{Double.NaN, Double.NaN});

        double lastX = Double.NaN;
        double lastY = Double.NaN;
        for (double[] p : stroke) {
            double x = clamp01(p[0] / getWidth());
            double y = clamp01(p[1] / getHeight());
            if (Double.isNaN(lastX) || distanceSq(x, y, lastX, lastY) > 0.00004) {
                out.add(new double[]{x, y});
                lastX = x;
                lastY = y;
            }
        }
    }

    private static double distanceSq(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ── Renderização ───────────────────────────────
    private void repaint() {
        GraphicsContext gc = getGraphicsContext2D();
        drawBackground();

        gc.setStroke(strokeColor);
        gc.setLineWidth(3.5);
        gc.setFill(new Color(strokeColor.getRed(), strokeColor.getGreen(),
                             strokeColor.getBlue(), 0.18));

        switch (mode) {
            case RECTANGLE -> {
                double x = Math.min(startX, endX), y = Math.min(startY, endY);
                double w = Math.abs(endX - startX), h = Math.abs(endY - startY);
                gc.fillRect(x, y, w, h);
                gc.strokeRect(x, y, w, h);
            }
            case CIRCLE -> {
                double cx = (startX + endX) / 2, cy = (startY + endY) / 2;
                double rx = Math.abs(endX - startX) / 2;
                double ry = Math.abs(endY - startY) / 2;
                gc.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
                gc.strokeOval(cx - rx, cy - ry, rx * 2, ry * 2);
            }
            case TRIANGLE -> {
                double mx = (startX + endX) / 2;
                gc.beginPath();
                gc.moveTo(mx, startY);
                gc.lineTo(endX, endY);
                gc.lineTo(startX, endY);
                gc.closePath();
                gc.fill(); gc.stroke();
            }
            case FREE -> {
                drawCompletedFreeStrokes(gc);
                if (freePoints.size() > 1) {
                    gc.beginPath();
                    gc.moveTo(freePoints.get(0)[0], freePoints.get(0)[1]);
                    for (int i = 1; i < freePoints.size(); i++)
                        gc.lineTo(freePoints.get(i)[0], freePoints.get(i)[1]);
                    gc.stroke();
                }
            }
        }

        // Crosshair guia
        drawCrossGuide(gc);
    }

    private void drawBackground() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();

        // Fundo
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, w, h);

        // Grade
        gc.setStroke(Color.web("#243044"));
        gc.setLineWidth(0.8);
        for (double x = 0; x < w; x += 24) {
            gc.beginPath(); gc.moveTo(x, 0); gc.lineTo(x, h); gc.stroke();
        }
        for (double y = 0; y < h; y += 24) {
            gc.beginPath(); gc.moveTo(0, y); gc.lineTo(w, y); gc.stroke();
        }

        // Labels
        gc.setFill(Color.web("#e0f2fe"));
        gc.setFont(javafx.scene.text.Font.font("monospace", 15));
        gc.fillText("PAINEL DE DESENHO", 12, 24);
        gc.setFill(Color.web("#93c5fd"));
        gc.setFont(javafx.scene.text.Font.font("monospace", 13));
        gc.fillText("Modo: " + mode.name(), 12, 44);
        if (mode == DrawMode.FREE) {
            gc.setFill(Color.web("#cbd5e1"));
            gc.fillText("Tracos: " + completedFreeStrokes.size() + " | duplo clique cria 3D", 12, 64);
        }
    }

    private void drawCrossGuide(GraphicsContext gc) {
        if (!drawing) return;
        gc.setStroke(new Color(1, 1, 1, 0.20));
        gc.setLineWidth(1.0);
        gc.strokeLine(endX, 0, endX, getHeight());
        gc.strokeLine(0, endY, getWidth(), endY);
    }

    /**
     * Pisca o canvas para confirmar que o objeto foi criado.
     *
     * <p>Usa uma unica {@link PauseTransition} reaproveitada, executada na
     * thread do JavaFX. A versao anterior disparava uma {@code new Thread()} a
     * cada desenho, criando threads que nunca eram reaproveitadas nem encerradas.
     */
    private void showConfirmFlash() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(new Color(strokeColor.getRed(), strokeColor.getGreen(),
                             strokeColor.getBlue(), 0.12));
        gc.fillRect(0, 0, getWidth(), getHeight());
        confirmFlash.playFromStart();
    }

    private void drawStatusMessage(String msg) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(Color.web("#44ff88cc", 0.8));
        gc.setFont(javafx.scene.text.Font.font("monospace", 15));
        gc.fillText(msg, 10, getHeight() - 12);
    }

    // ── API pública ────────────────────────────────
    public void setMode(DrawMode mode)                          { this.mode = mode; repaint(); }
    public void setStrokeColor(Color c)                        { this.strokeColor = c; }
    public void setOnDrawComplete(Consumer<DrawResult> handler){ this.onDrawComplete = handler; }
    public DrawMode getMode()                                   { return mode; }

    public void createFreeDrawingObject() {
        List<double[]> path = normalizedPath();
        if (path.size() < 2 || onDrawComplete == null) return;
        Bounds b = currentFreeBounds();
        double nw = Math.max(0.05, b.width() / Math.max(1.0, getWidth()));
        double nh = Math.max(0.05, b.height() / Math.max(1.0, getHeight()));
        onDrawComplete.accept(new DrawResult(ObjectFactory.ShapeType.DRAWN, nw, nh, strokeColor, path));
        completedFreeStrokes.clear();
        freePoints.clear();
        showConfirmFlash();
    }

    public void clearFreeDrawing() {
        completedFreeStrokes.clear();
        freePoints.clear();
        repaint();
    }

    public void undoLastFreeStroke() {
        if (!completedFreeStrokes.isEmpty()) {
            completedFreeStrokes.remove(completedFreeStrokes.size() - 1);
        }
        freePoints.clear();
        repaint();
        drawStatusMessage(completedFreeStrokes.isEmpty()
            ? "Rascunho limpo."
            : "Ultimo traco removido.");
    }

    private void drawCompletedFreeStrokes(GraphicsContext gc) {
        for (List<double[]> stroke : completedFreeStrokes) {
            if (stroke.size() < 2) continue;
            gc.beginPath();
            gc.moveTo(stroke.get(0)[0], stroke.get(0)[1]);
            for (int i = 1; i < stroke.size(); i++) {
                gc.lineTo(stroke.get(i)[0], stroke.get(i)[1]);
            }
            gc.stroke();
        }
    }

    private static List<double[]> copyStroke(List<double[]> source) {
        List<double[]> copy = new ArrayList<>(source.size());
        for (double[] p : source) copy.add(new double[]{p[0], p[1]});
        return copy;
    }

    private Bounds currentFreeBounds() {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (List<double[]> stroke : completedFreeStrokes) {
            for (double[] p : stroke) {
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]);
                maxY = Math.max(maxY, p[1]);
            }
        }
        if (!Double.isFinite(minX)) return new Bounds(0, 0);
        return new Bounds(maxX - minX, maxY - minY);
    }

    private record Bounds(double width, double height) {}
}
