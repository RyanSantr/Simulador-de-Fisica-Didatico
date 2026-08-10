package engine.simulation.challenges;

/**
 * Resultado imutável da avaliação de um desafio.
 * Padrão: Value Object — sem estado mutável.
 */
public final class ChallengeResult {

    public enum Status { RUNNING, SUCCESS, FAIL }

    private final String  challengeId;
    private final Status  status;
    private final int     score;
    private final String  message;
    private final String  timeFormatted;

    private ChallengeResult(String id, Status status, int score,
                             String message, String timeFormatted) {
        this.challengeId   = id;
        this.status        = status;
        this.score         = score;
        this.message       = message;
        this.timeFormatted = timeFormatted;
    }

    // ── Fábrica estática ───────────────────────────

    public static ChallengeResult running(String id, String progressMsg) {
        return new ChallengeResult(id, Status.RUNNING, 0, progressMsg, "");
    }

    public static ChallengeResult success(String id, int score, String time) {
        return new ChallengeResult(id, Status.SUCCESS, score,
            "Desafio concluído! Pontuação: " + score, time);
    }

    public static ChallengeResult fail(String id, String reason) {
        return new ChallengeResult(id, Status.FAIL, 0, reason, "");
    }

    // ── Consultas ──────────────────────────────────
    public boolean isRunning() { return status == Status.RUNNING; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isFail()    { return status == Status.FAIL; }

    public String getChallengeId()    { return challengeId; }
    public Status getStatus()         { return status; }
    public int    getScore()          { return score; }
    public String getMessage()        { return message; }
    public String getTimeFormatted()  { return timeFormatted; }

    @Override
    public String toString() {
        return String.format("ChallengeResult[%s | %s | score=%d | %s]",
            challengeId, status, score, message);
    }
}
