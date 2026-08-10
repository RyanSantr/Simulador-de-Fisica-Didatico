package engine.simulation;

import engine.physics.MaterialState;
import engine.physics.MediumState;

import java.util.List;
import java.util.Map;

/**
 * Presets pequenos e repetiveis para comparar fenomenos durante a feira.
 */
public final class LabPresetCatalog {

    private LabPresetCatalog() {}

    public static List<LabPreset> forModule(String moduleId) {
        return switch (moduleId) {
            case "free_fall" -> List.of(
                preset("Terra - queda de 8 m", "Referencia com g=9.81 m/s2.",
                    "gravity", 9.81, "initial_height", 8.0, "initial_velocity", 0.0),
                preset("Lua - mesma altura", "Compare o tempo de impacto com gravidade lunar.",
                    "gravity", 1.62, "initial_height", 8.0, "initial_velocity", 0.0),
                preset("Lancamento vertical", "A bola sobe antes de retornar ao chao.",
                    "gravity", 9.81, "initial_height", 2.0, "initial_velocity", 10.0)
            );
            case "projectile" -> List.of(
                preset("Vacuo a 45 graus", "Base para comparar alcance sem arrasto.",
                    "medium", MediumState.VACUUM.ordinal(), "speed", 22.0, "angle", 45.0,
                    "target_material", MaterialState.STONE.ordinal()),
                preset("Agua com alvo macio", "O arrasto reduz a trajetoria e o alvo absorve impacto.",
                    "medium", MediumState.WATER.ordinal(), "speed", 28.0, "angle", 35.0,
                    "target_material", MaterialState.FOAM.ordinal()),
                preset("Aco contra vidro", "Impacto rapido para observar dano no alvo fragil.",
                    "projectile_material", MaterialState.STEEL.ordinal(), "target_material", MaterialState.GLASS.ordinal(),
                    "medium", MediumState.AIR.ordinal(), "speed", 48.0, "angle", 18.0)
            );
            case "collisions" -> List.of(
                preset("Pedra x gelatina", "Corpos com resposta material bem contrastante.",
                    "material_a", MaterialState.STONE.ordinal(), "material_b", MaterialState.GELATIN.ordinal(),
                    "velocity_a", 6.0, "offset_z", 0.0),
                preset("Borracha x aco", "Observe rebote e transferencia de impulso.",
                    "material_a", MaterialState.RUBBER.ordinal(), "material_b", MaterialState.STEEL.ordinal(),
                    "velocity_a", 8.0, "offset_z", 0.0),
                preset("Vidro obliquo", "Colisao lateral para comparar dano e direcao.",
                    "material_a", MaterialState.STEEL.ordinal(), "material_b", MaterialState.GLASS.ordinal(),
                    "velocity_a", 16.0, "offset_z", 0.36)
            );
            case "inclined_plane" -> List.of(
                preset("Rampa terrestre", "Deslizamento com atrito moderado.",
                    "gravity", 9.81, "angle", 28.0, "friction", 0.18, "ramp_length", 9.0),
                preset("Atrito alto", "Veja a condicao que segura o bloco.",
                    "gravity", 9.81, "angle", 18.0, "friction", 0.55, "ramp_length", 8.0),
                preset("Rampa lunar", "Mesma geometria com gravidade menor.",
                    "gravity", 1.62, "angle", 36.0, "friction", 0.10, "ramp_length", 10.0)
            );
            case "pendulum" -> List.of(
                preset("Pequeno angulo", "Aproxima o periodo teorico do pendulo simples.",
                    "length", 4.2, "amplitude", 12.0, "gravity", 9.81, "damping", 0.01),
                preset("Amplitude alta", "Mostra a nao linearidade da oscilacao.",
                    "length", 4.2, "amplitude", 68.0, "gravity", 9.81, "damping", 0.02),
                preset("Pendulo amortecido", "A energia se perde mais rapido.",
                    "length", 3.0, "amplitude", 42.0, "gravity", 9.81, "damping", 0.18)
            );
            case "spring" -> List.of(
                preset("Mola leve", "Oscilacao longa com baixa rigidez.",
                    "mass", 1.2, "stiffness", 10.0, "amplitude", 2.4, "damping", 0.08),
                preset("Mola rigida", "Aumente k e compare a frequencia.",
                    "mass", 1.2, "stiffness", 48.0, "amplitude", 1.4, "damping", 0.12),
                preset("Massa pesada", "Mais inercia para o mesmo alongamento.",
                    "mass", 5.0, "stiffness", 18.0, "amplitude", 2.0, "damping", 0.18)
            );
            default -> List.of();
        };
    }

    private static LabPreset preset(String label, String note, Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Preset requires parameter/value pairs.");
        }
        java.util.LinkedHashMap<String, Double> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], ((Number) entries[i + 1]).doubleValue());
        }
        return new LabPreset(label, note, Map.copyOf(values));
    }
}
