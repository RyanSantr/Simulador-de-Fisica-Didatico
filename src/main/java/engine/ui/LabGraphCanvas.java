package engine.ui;

import engine.simulation.LabMeasurement;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grafico compacto do laboratorio com duas grandezas por simulacao.
 */
public final class LabGraphCanvas extends Canvas {

    private static final int MAX_SAMPLES = 180;
    private final List<Sample> samples = new ArrayList<>();
    private String trackedLabel = "sem objeto";
    private String seriesALabel = "altura";
    private String seriesAUnit = "m";
    private String seriesBLabel = "velocidade";
    private String seriesBUnit = "m/s";

    public LabGraphCanvas(double width, double height) {
        super(width, height);
        redraw();
    }

    public void addSample(double time, double height, double speed, String label) {
        addSample(time, height, speed, label, "altura", "m", "velocidade", "m/s");
    }

    public void addSample(double time, LabMeasurement measurement) {
        addSample(
            time,
            measurement.seriesAValue(),
            measurement.seriesBValue(),
            measurement.sourceLabel(),
            measurement.seriesALabel(),
            measurement.seriesAUnit(),
            measurement.seriesBLabel(),
            measurement.seriesBUnit()
        );
    }

    /**
     * Registra uma amostra.
     *
     * <p>Amostras nao finitas sao descartadas em vez de armazenadas. Um unico
     * {@code NaN} contaminaria o calculo da escala — {@code Math.min(x, NaN)}
     * devolve {@code NaN} —, e a partir dai todos os pontos cairiam fora do
     * grafico e o CSV exportado sairia com {@code NaN}. O sintoma seria um
     * grafico em branco sem nenhum erro visivel.
     */
    public void addSample(double time, double valueA, double valueB, String label,
                          String labelA, String unitA, String labelB, String unitB) {
        if (!Double.isFinite(time) || !Double.isFinite(valueA) || !Double.isFinite(valueB)) return;

        if (!samples.isEmpty() && time + 1e-6 < samples.get(samples.size() - 1).time) {
            samples.clear();
        }
        trackedLabel = label == null || label.isBlank() ? "objeto" : label;
        seriesALabel = labelA;
        seriesAUnit = unitA == null ? "" : unitA;
        seriesBLabel = labelB;
        seriesBUnit = unitB == null ? "" : unitB;
        samples.add(new Sample(time, valueA, valueB));
        if (samples.size() > MAX_SAMPLES) samples.remove(0);
        redraw();
    }

    public void reset() {
        samples.clear();
        trackedLabel = "sem objeto";
        seriesALabel = "altura";
        seriesAUnit = "m";
        seriesBLabel = "velocidade";
        seriesBUnit = "m/s";
        redraw();
    }

    public void redrawNow() {
        redraw();
    }

    public int sampleCount() {
        return samples.size();
    }

    public String toCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("fonte,tempo_s,")
            .append(csvHeader(seriesALabel, seriesAUnit)).append(",")
            .append(csvHeader(seriesBLabel, seriesBUnit)).append("\n");
        for (Sample sample : samples) {
            csv.append(csvCell(trackedLabel)).append(",")
                .append(format(sample.time)).append(",")
                .append(format(sample.valueA)).append(",")
                .append(format(sample.valueB)).append("\n");
        }
        return csv.toString();
    }

    private void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.setFill(Color.web("#020617"));
        gc.fillRoundRect(0, 0, w, h, 8, 8);
        gc.setStroke(Color.web("#334155"));
        gc.strokeRoundRect(0.5, 0.5, w - 1, h - 1, 8, 8);
        gc.setFont(Font.font("monospace", 11));
        gc.setFill(Color.web("#cbd5e1"));
        gc.fillText("Grafico: " + trackedLabel, 10, 16);
        gc.setFill(Color.web("#38bdf8"));
        gc.fillText(legend(seriesALabel, seriesAUnit), 10, h - 8);
        gc.setFill(Color.web("#f59e0b"));
        gc.fillText(legend(seriesBLabel, seriesBUnit), Math.min(w * 0.48, 136), h - 8);

        double left = 10;
        double top = 24;
        double right = w - 10;
        double bottom = h - 24;
        gc.setStroke(Color.web("#1e293b"));
        for (int i = 0; i < 4; i++) {
            double y = top + (bottom - top) * i / 3.0;
            gc.strokeLine(left, y, right, y);
        }
        if (samples.size() < 2) return;

        double minT = samples.get(0).time;
        double maxT = samples.get(samples.size() - 1).time;
        Range rangeA = range(true);
        Range rangeB = range(false);
        drawSeries(gc, left, top, right, bottom, minT, maxT, rangeA, true, Color.web("#38bdf8"));
        drawSeries(gc, left, top, right, bottom, minT, maxT, rangeB, false, Color.web("#f59e0b"));
    }

    private void drawSeries(GraphicsContext gc, double left, double top, double right, double bottom,
                            double minT, double maxT, Range range, boolean seriesA, Color color) {
        gc.setStroke(color);
        gc.setLineWidth(1.7);
        gc.beginPath();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            double value = seriesA ? sample.valueA : sample.valueB;
            double x = maxT <= minT ? left : left + (right - left) * (sample.time - minT) / (maxT - minT);
            double normalized = (value - range.min) / Math.max(1e-9, range.max - range.min);
            double y = bottom - (bottom - top) * Math.max(0, Math.min(1, normalized));
            if (i == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();
    }

    private Range range(boolean seriesA) {
        double min = 0;
        double max = 0;
        for (Sample sample : samples) {
            double value = seriesA ? sample.valueA : sample.valueB;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (Math.abs(max - min) < 1e-9) {
            max += 0.1;
            min -= 0.1;
        }
        return new Range(min, max);
    }

    private String legend(String label, String unit) {
        if (unit == null || unit.isBlank()) return label;
        return label + " " + unit;
    }

    private String csvHeader(String label, String unit) {
        return csvCell(unit == null || unit.isBlank() ? label : label + "_" + unit);
    }

    private String csvCell(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private String format(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private record Sample(double time, double valueA, double valueB) {}
    private record Range(double min, double max) {}
}
