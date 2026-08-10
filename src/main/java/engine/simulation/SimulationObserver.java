package engine.simulation;

import engine.scene.SceneObject;

/**
 * Interface de observação de eventos da cena.
 * Módulos implementam esta interface para reagir a mudanças
 * de estado sem acoplamento direto ao SimulationContext.
 *
 * Todos os métodos têm implementação default vazia,
 * permitindo que módulos implementem apenas os eventos relevantes.
 */
public interface SimulationObserver {

    /** Disparado quando um objeto é adicionado à cena. */
    default void onObjectAdded(SceneObject obj) {}

    /** Disparado quando um objeto é removido da cena. */
    default void onObjectRemoved(SceneObject obj) {}

    /** Disparado quando a cena é completamente limpa. */
    default void onSceneCleared() {}

    /** Disparado quando a simulação é resetada ao estado inicial do módulo. */
    default void onSimulationReset() {}

    /** Disparado quando um desafio é completado com sucesso. */
    default void onChallengeCompleted(String challengeId, int score) {}

    /** Disparado quando um parâmetro global é alterado. */
    default void onParameterChanged(String paramName, double newValue) {}
}
