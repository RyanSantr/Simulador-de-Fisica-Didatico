package engine.simulation;

/**
 * Amostra compacta para o HUD, grafico e exportacao do modo laboratorio.
 */
public record LabMeasurement(
    String sourceLabel,
    String seriesALabel,
    String seriesAUnit,
    double seriesAValue,
    String seriesBLabel,
    String seriesBUnit,
    double seriesBValue,
    String hudLine
) {
    public String seriesALegend() {
        return legend(seriesALabel, seriesAUnit);
    }

    public String seriesBLegend() {
        return legend(seriesBLabel, seriesBUnit);
    }

    private String legend(String label, String unit) {
        if (unit == null || unit.isBlank()) return label;
        return label + " (" + unit + ")";
    }
}
