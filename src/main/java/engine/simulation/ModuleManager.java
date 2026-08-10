package engine.simulation;

import java.util.*;
import java.util.function.Consumer;

/**
 * Gerenciador central de módulos de simulação.
 *
 * Responsabilidades:
 *   - Registro e descoberta de módulos
 *   - Ativação/desativação com ciclo de vida correto
 *   - Delegação do loop de update ao módulo ativo
 *   - Notificação de mudança de módulo para a UI
 *
 * Padrão de uso no Engine:
 * <pre>
 *   moduleManager = new ModuleManager(context);
 *   moduleManager.register(new FreeFallModule());
 *   moduleManager.register(new ProjectileModule());
 *   moduleManager.activateById("free_fall");
 * </pre>
 */
public final class ModuleManager {

    // ── Estado ─────────────────────────────────────
    private final SimulationContext             context;
    private final Map<String, SimulationModule> registry = new LinkedHashMap<>();
    private       SimulationModule              activeModule = null;

    // ── Callbacks para UI ──────────────────────────
    private Consumer<SimulationModule> onModuleChanged;
    private Consumer<String>           onModuleError;

    public ModuleManager(SimulationContext context) {
        this.context = Objects.requireNonNull(context, "context não pode ser null");
    }

    // ══════════════════════════════════════════════
    //  REGISTRO
    // ══════════════════════════════════════════════

    /**
     * Registra um módulo. Lança exceção se o ID já existir.
     */
    public ModuleManager register(SimulationModule module) {
        Objects.requireNonNull(module, "module não pode ser null");
        if (registry.containsKey(module.getId())) {
            throw new IllegalArgumentException(
                "Módulo já registrado com id: " + module.getId());
        }
        registry.put(module.getId(), module);
        return this; // fluent API
    }

    /**
     * Remove módulo do registro. Desativa primeiro se for o ativo.
     */
    public boolean unregister(String id) {
        if (activeModule != null && activeModule.getId().equals(id)) {
            deactivateCurrent();
        }
        return registry.remove(id) != null;
    }

    // ══════════════════════════════════════════════
    //  ATIVAÇÃO / DESATIVAÇÃO
    // ══════════════════════════════════════════════

    /**
     * Ativa um módulo pelo ID.
     * Desativa o módulo atual antes de ativar o novo.
     *
     * @return true se o módulo foi encontrado e ativado com sucesso
     */
    public boolean activateById(String id) {
        SimulationModule next = registry.get(id);
        if (next == null) {
            if (onModuleError != null)
                onModuleError.accept("Módulo não encontrado: " + id);
            return false;
        }

        if (activeModule == next) return true; // já ativo

        deactivateCurrent();

        try {
            context.clearScene();
            next.onActivate(context);
            activeModule = next;
            if (onModuleChanged != null) onModuleChanged.accept(next);
            return true;
        } catch (Exception e) {
            activeModule = null;
            if (onModuleError != null)
                onModuleError.accept("Erro ao ativar módulo '" + id + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Ativa o próximo módulo na ordem de registro.
     * Faz ciclo: após o último, volta ao primeiro.
     */
    public void activateNext() {
        List<String> ids = new ArrayList<>(registry.keySet());
        if (ids.isEmpty()) return;
        if (activeModule == null) { activateById(ids.get(0)); return; }

        int idx = ids.indexOf(activeModule.getId());
        activateById(ids.get((idx + 1) % ids.size()));
    }

    /**
     * Ativa o módulo anterior na ordem de registro.
     */
    public void activatePrevious() {
        List<String> ids = new ArrayList<>(registry.keySet());
        if (ids.isEmpty()) return;
        if (activeModule == null) { activateById(ids.get(ids.size()-1)); return; }

        int idx = ids.indexOf(activeModule.getId());
        activateById(ids.get((idx - 1 + ids.size()) % ids.size()));
    }

    private void deactivateCurrent() {
        if (activeModule != null) {
            try { activeModule.onDeactivate(); }
            catch (Exception e) { e.printStackTrace(); }
            activeModule = null;
        }
    }

    // ══════════════════════════════════════════════
    //  LOOP DE ATUALIZAÇÃO
    // ══════════════════════════════════════════════

    /**
     * Delega o update ao módulo ativo.
     * Chamado pelo Engine a cada frame, após physics.update().
     *
     * @param dt delta time já multiplicado pelo timeScale
     */
    public void update(double dt) {
        if (activeModule != null && activeModule.isActive()) {
            try {
                activeModule.onUpdate(dt);
            } catch (Exception e) {
                // Isola falhas do módulo: loga mas não trava o engine
                e.printStackTrace();
            }
        }
    }

    // ══════════════════════════════════════════════
    //  OPERAÇÕES NO MÓDULO ATIVO
    // ══════════════════════════════════════════════

    /** Reseta a simulação do módulo ativo. */
    public void resetActive() {
        if (activeModule != null) activeModule.onReset();
    }

    /**
     * Notifica o módulo ativo sobre mudança de parâmetro.
     */
    public void notifyParameterChanged(String paramName, double value) {
        if (activeModule != null) activeModule.onParameterChanged(paramName, value);
    }

    // ══════════════════════════════════════════════
    //  CONSULTAS
    // ══════════════════════════════════════════════

    public SimulationModule getActiveModule()           { return activeModule; }
    public boolean          hasActiveModule()           { return activeModule != null; }
    public SimulationModule getById(String id)          { return registry.get(id); }
    public Collection<SimulationModule> getAllModules()  { return Collections.unmodifiableCollection(registry.values()); }
    public int              getModuleCount()            { return registry.size(); }

    public boolean isActive(String id) {
        return activeModule != null && activeModule.getId().equals(id);
    }

    // ── Callbacks ──────────────────────────────────
    public void setOnModuleChanged(Consumer<SimulationModule> cb) { this.onModuleChanged = cb; }
    public void setOnModuleError(Consumer<String> cb)             { this.onModuleError   = cb; }

    @Override
    public String toString() {
        return String.format("ModuleManager[%d módulos | ativo=%s]",
            registry.size(),
            activeModule != null ? activeModule.getDisplayName() : "nenhum");
    }
}
