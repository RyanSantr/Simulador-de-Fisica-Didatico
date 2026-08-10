package engine.simulation.challenges;

import engine.simulation.SimulationContext;

/**
 * Representa um desafio educacional dentro de um módulo.
 *
 * Um desafio tem:
 *   - Enunciado (o que o aluno deve fazer)
 *   - Dica pedagógica
 *   - Condição de sucesso (avaliada a cada frame)
 *   - Pontuação máxima (reduz com o tempo ou tentativas)
 *
 * Ciclo de vida:
 *   1. start(context) → inicializa o estado do desafio
 *   2. evaluate(dt)   → avaliado todo frame; retorna ChallengeResult
 *   3. onSuccess() / onFail() → callbacks opcionais
 */
public abstract class Challenge {

    // ── Metadados ──────────────────────────────────
    private final String id;
    private final String title;
    private final String instruction;   // "O que fazer"
    private final String hint;          // "Dica pedagógica"
    private final int    maxScore;

    // ── Estado ─────────────────────────────────────
    protected SimulationContext context;
    private   double            elapsedTime   = 0;
    private   int               attempts      = 0;
    private   boolean           completed     = false;
    private   boolean           failed        = false;
    private   int               finalScore    = 0;

    // ── Limites ────────────────────────────────────
    private final double timeLimit;   // 0 = sem limite

    protected Challenge(String id, String title, String instruction,
                         String hint, int maxScore, double timeLimit) {
        this.id          = id;
        this.title       = title;
        this.instruction = instruction;
        this.hint        = hint;
        this.maxScore    = maxScore;
        this.timeLimit   = timeLimit;
    }

    // ── Ciclo de vida ──────────────────────────────

    public final void start(SimulationContext ctx) {
        this.context     = ctx;
        this.elapsedTime = 0;
        this.attempts    = 0;
        this.completed   = false;
        this.failed      = false;
        this.finalScore  = 0;
        onStart();
    }

    /** Override para configurar a cena do desafio. */
    protected void onStart() {}

    /**
     * Avalia o estado do desafio a cada frame.
     * @return ChallengeResult com o estado atual (RUNNING, SUCCESS, FAIL)
     */
    public final ChallengeResult evaluate(double dt) {
        if (completed || failed) return currentResult();

        elapsedTime += dt;

        // Verificar timeout
        if (timeLimit > 0 && elapsedTime >= timeLimit) {
            failed = true;
            onFail();
            return ChallengeResult.fail(id, "Tempo esgotado!");
        }

        // Delegar avaliação à subclasse
        ChallengeResult result = evaluateCondition(dt);

        if (result.isSuccess()) {
            completed   = true;
            finalScore  = computeScore();
            onSuccess(finalScore);
        } else if (result.isFail()) {
            failed = true;
            onFail();
        }

        return result;
    }

    /**
     * Implementação específica de cada desafio.
     * Deve retornar SUCCESS, FAIL ou RUNNING.
     */
    protected abstract ChallengeResult evaluateCondition(double dt);

    /** Calcula a pontuação final baseada no tempo e tentativas. */
    protected int computeScore() {
        double timeFactor    = timeLimit > 0 ? Math.max(0.2, 1.0 - elapsedTime / timeLimit) : 1.0;
        double attemptFactor = Math.max(0.3, 1.0 - attempts * 0.15);
        return (int)(maxScore * timeFactor * attemptFactor);
    }

    protected void onSuccess(int score) {}
    protected void onFail()             {}

    public void incrementAttempts()      { attempts++; }
    public void reset()                  { start(context); }

    // ── Resultado atual ─────────────────────────────
    private ChallengeResult currentResult() {
        if (completed) return ChallengeResult.success(id, finalScore, formatTime());
        if (failed)    return ChallengeResult.fail(id, "Desafio encerrado.");
        return ChallengeResult.running(id, progressDescription());
    }

    /** Override para descrever o progresso em tempo real. */
    protected String progressDescription() {
        return timeLimit > 0
            ? String.format("Tempo: %.1fs / %.1fs", elapsedTime, timeLimit)
            : String.format("Tempo decorrido: %.1fs", elapsedTime);
    }

    private String formatTime() {
        return String.format("%.1fs", elapsedTime);
    }

    // ── Getters ────────────────────────────────────
    public String  getId()          { return id; }
    public String  getTitle()       { return title; }
    public String  getInstruction() { return instruction; }
    public String  getHint()        { return hint; }
    public int     getMaxScore()    { return maxScore; }
    public double  getElapsedTime() { return elapsedTime; }
    public int     getAttempts()    { return attempts; }
    public boolean isCompleted()    { return completed; }
    public boolean isFailed()       { return failed; }
    public int     getFinalScore()  { return finalScore; }
    public double  getTimeLimit()   { return timeLimit; }
    public double  getTimeRemaining(){ return Math.max(0, timeLimit - elapsedTime); }
}
