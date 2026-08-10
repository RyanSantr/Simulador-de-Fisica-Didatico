package engine.simulation;

import engine.simulation.parameters.ParameterSet;
import engine.simulation.challenges.Challenge;
import javafx.scene.layout.Pane;

import java.util.List;

/**
 * Classe base abstrata para todos os módulos de simulação educacional.
 *
 * ══════════════════════════════════════════════════════
 *  CONTRATO DE CICLO DE VIDA
 * ══════════════════════════════════════════════════════
 *
 *  Sequência normal:
 *    1. onActivate(context)   → módulo recebe o contexto, monta a cena inicial
 *    2. onUpdate(dt)          → chamado a cada frame (60 Hz)
 *    3. onParameterChanged()  → disparado quando o usuário altera um parâmetro
 *    4. onReset()             → limpa a cena e recomeça do estado inicial
 *    5. onDeactivate()        → módulo libera recursos, a cena é limpa externamente
 *
 *  Threads: todos os métodos são chamados na JavaFX Application Thread.
 *
 * ══════════════════════════════════════════════════════
 *  PADRÃO DE EXTENSÃO
 * ══════════════════════════════════════════════════════
 *
 *  Para criar um novo módulo:
 *    1. Estender SimulationModule
 *    2. Implementar os métodos abstratos
 *    3. Registrar no ModuleRegistry
 *
 *  Exemplo mínimo:
 *    public class MeuModulo extends SimulationModule {
 *        public MeuModulo() { super("meu_modulo", "Meu Módulo", "Descrição curta"); }
 *        protected void buildScene() { adicionarObjetosViaContext(); }
 *        public void onUpdate(double dt) { atualizarLogicaPorFrame(dt); }
 *    }
 */
public abstract class SimulationModule {

    // ── Metadados (imutáveis) ──────────────────────
    private final String id;
    private final String displayName;
    private final String description;
    private final String curriculumTopic;  // ex: "Mecânica — Cinemática"

    // ── Estado ─────────────────────────────────────
    protected SimulationContext context;
    private   boolean           active = false;
    private   int               resetCount = 0;

    // ── Parâmetros e desafios ──────────────────────
    protected final ParameterSet parameters;
    private         List<Challenge> challenges;
    private         int             currentChallengeIndex = 0;

    // ── Painel de controle (UI lateral, opcional) ──
    private Pane controlPanel;

    // ══════════════════════════════════════════════
    //  CONSTRUTOR
    // ══════════════════════════════════════════════

    protected SimulationModule(String id, String displayName,
                                String description, String curriculumTopic) {
        this.id              = id;
        this.displayName     = displayName;
        this.description     = description;
        this.curriculumTopic = curriculumTopic;
        this.parameters      = new ParameterSet();
        declareParameters();
    }

    protected SimulationModule(String id, String displayName, String description) {
        this(id, displayName, description, "Física Geral");
    }

    // ══════════════════════════════════════════════
    //  MÉTODOS ABSTRATOS (obrigatório implementar)
    // ══════════════════════════════════════════════

    /**
     * Constrói a cena inicial do módulo usando context.addObject().
     * Chamado em onActivate() e em cada onReset().
     */
    protected abstract void buildScene();

    /**
     * Lógica por frame: verificar condições de vitória, atualizar UI, etc.
     * @param dt delta time em segundos (já aplicado o timeScale)
     */
    public abstract void onUpdate(double dt);

    // ══════════════════════════════════════════════
    //  CICLO DE VIDA (hooks — override conforme necessário)
    // ══════════════════════════════════════════════

    /**
     * Declara os parâmetros ajustáveis do módulo.
     * Chamado no construtor antes de onActivate().
     */
    protected void declareParameters() { /* override para declarar parâmetros */ }

    /**
     * Chamado pelo ModuleManager quando este módulo se torna ativo.
     * NÃO override em subclasses — use buildScene() e onPostActivate().
     */
    public final void onActivate(SimulationContext ctx) {
        this.context = ctx;
        this.active  = true;
        this.resetCount = 0;
        context.resetSimTime();
        applyDefaultParameters();
        buildScene();
        this.controlPanel = buildControlPanel();
        onPostActivate();
    }

    /**
     * Hook chamado após buildScene() e criação do painel.
     * Override para lógica adicional de inicialização.
     */
    protected void onPostActivate() {}

    /**
     * Reseta a simulação ao estado inicial do módulo.
     * Limpa a cena, reaplica parâmetros e reconstrói.
     */
    public final void onReset() {
        context.clearScene();
        context.resetSimTime();
        resetCount++;
        applyDefaultParameters();
        buildScene();
        onPostReset();
    }

    /**
     * Hook chamado após o reset e reconstrução da cena.
     */
    protected void onPostReset() {}

    /**
     * Chamado quando o módulo é desativado.
     * Override para liberar recursos específicos do módulo.
     */
    public void onDeactivate() {
        active = false;
    }

    /**
     * Chamado quando qualquer parâmetro muda via UI.
     * Override para reagir a mudanças específicas.
     */
    public void onParameterChanged(String paramName, double newValue) {}

    /**
     * Leitura compacta para HUD e grafico. Modulos com grandezas proprias
     * substituem este metodo para evitar leituras genericas da cena.
     */
    public LabMeasurement sampleLabMeasurement() { return null; }

    /**
     * Texto de telemetria exibido no painel Resultados. Modulos substituem
     * este metodo; null faz o Engine mostrar o resumo generico da cena.
     */
    public String telemetry() { return null; }

    /**
     * Rotulo do botao de acao manual do modulo (lancar projetil, iniciar
     * colisao, enviar foguete...).
     *
     * @return texto do botao, ou {@code null} se o modulo nao tem acao manual —
     *         nesse caso a UI esconde o botao
     */
    public String launchActionLabel() { return null; }

    /**
     * Executa a acao manual disparada pelo botao da UI.
     * So e chamado quando {@link #launchActionLabel()} nao e null.
     */
    public void launch() {}

    /**
     * Constrói o painel de controle específico do módulo (JavaFX).
     * @return Pane com controles, ou null para usar o painel padrão.
     */
    protected Pane buildControlPanel() { return null; }

    // ══════════════════════════════════════════════
    //  PARÂMETROS
    // ══════════════════════════════════════════════

    /**
     * Aplica os valores default dos parâmetros ao contexto.
     * Chamado em onActivate() e onReset().
     */
    private void applyDefaultParameters() {
        parameters.forEach((name, param) -> {
            if (name.equals("gravity")) {
                context.setGravityStrength(param.getValue());
            }
        });
    }

    // ══════════════════════════════════════════════
    //  DESAFIOS
    // ══════════════════════════════════════════════

    public void setChallenges(List<Challenge> challenges) {
        this.challenges = challenges;
        this.currentChallengeIndex = 0;
    }

    public Challenge getCurrentChallenge() {
        if (challenges == null || challenges.isEmpty()) return null;
        if (currentChallengeIndex >= challenges.size()) return null;
        return challenges.get(currentChallengeIndex);
    }

    public boolean advanceChallenge() {
        if (challenges == null) return false;
        currentChallengeIndex++;
        return currentChallengeIndex < challenges.size();
    }

    public boolean hasMoreChallenges() {
        return challenges != null && currentChallengeIndex < challenges.size() - 1;
    }

    public int getChallengeCount() {
        return challenges == null ? 0 : challenges.size();
    }

    public int getCurrentChallengeIndex() { return currentChallengeIndex; }

    // ══════════════════════════════════════════════
    //  GETTERS
    // ══════════════════════════════════════════════

    public String          getId()             { return id; }
    public String          getDisplayName()    { return displayName; }
    public String          getDescription()    { return description; }
    public String          getCurriculumTopic(){ return curriculumTopic; }
    public boolean         isActive()          { return active; }
    public int             getResetCount()     { return resetCount; }
    public ParameterSet    getParameters()     { return parameters; }
    public Pane            getControlPanel()   { return controlPanel; }
    protected SimulationContext getContext()   { return context; }

    @Override
    public String toString() {
        return String.format("Module[%s | active=%b | resets=%d]",
            displayName, active, resetCount);
    }
}
