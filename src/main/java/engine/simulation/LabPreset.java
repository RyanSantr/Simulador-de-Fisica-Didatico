package engine.simulation;

import java.util.Map;

/**
 * Conjunto conhecido de parametros para repetir experimentos.
 */
public record LabPreset(String label, String note, Map<String, Double> values) {
    public LabPreset {
        values = Map.copyOf(values);
    }

    @Override
    public String toString() {
        return label;
    }
}
