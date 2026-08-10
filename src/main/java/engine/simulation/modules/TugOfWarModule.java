package engine.simulation.modules;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.scene.SceneObject;
import engine.simulation.ModuleSceneBuilder;
import engine.simulation.SimulationModule;
import engine.simulation.parameters.Parameter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Minijogo de cabo de guerra para a Feira Tecnologica.
 *
 * Dois visitantes disputam no teclado:
 * Jogador 1 pressiona A, jogador 2 pressiona L. Cada toque aplica uma
 * contribuicao de forca, e a diferenca entre os lados move a marca central.
 */
public class TugOfWarModule extends SimulationModule {

    public static final String ID = "tug_of_war";

    private static final double WIN_LIMIT = 4.5;
    private static final double MAX_ROPE_SPEED = 5.0;

    private SceneObject rope;
    private SceneObject marker;
    private SceneObject leftPlayer;
    private SceneObject rightPlayer;

    private double ropePosition;
    private double ropeVelocity;
    private double leftForce;
    private double rightForce;
    private int leftTaps;
    private int rightTaps;
    private double leftBestCps;
    private double rightBestCps;
    private double leftGlobalBestCps;
    private double rightGlobalBestCps;
    private String leftName = "Jogador A";
    private String rightName = "Jogador L";
    private String winner = "";
    private boolean matchRecorded = false;
    private final Deque<Double> leftTapTimes = new ArrayDeque<>();
    private final Deque<Double> rightTapTimes = new ArrayDeque<>();
    private final List<RankingEntry> ranking = new ArrayList<>();

    public record RankingEntry(String playerName, int taps, double bestCps, String result) {}

    public TugOfWarModule() {
        super(ID,
            "Cabo de Guerra",
            "Dois visitantes aplicam forca no teclado e observam forca resultante, equilibrio e aceleracao.",
            "Mecanica - Dinamica");
    }

    @Override
    protected void declareParameters() {
        parameters.add(Parameter.of("tap_force", "Forca por toque", 1.25)
            .range(0.5, 5.0).limits(0, 1e5).unit("N").step(0.25)
            .tooltip("Quanto cada tecla pressionada adiciona de forca ao jogador."));

        parameters.add(Parameter.of("hold_force", "Forca segurando", 12.0)
            .range(4.0, 30.0).limits(0, 1e5).unit("N/s").step(0.5)
            .reference("puxao de um adulto ~ 300 N")
            .tooltip("Forca continua enquanto a tecla esta pressionada, sem depender do delay do teclado."));

        parameters.add(Parameter.of("decay", "Perda de forca", 2.4)
            .range(0.5, 6.0).limits(0, 1e5).unit("N/s").step(0.1)
            .outOfRangeNote("Sem perda de forca a disputa nunca volta ao equilibrio.")
            .tooltip("Dissipacao da forca acumulada. Valores altos exigem ritmo constante."));

        parameters.add(Parameter.of("mass", "Massa da corda", 8.0)
            .range(2.0, 20.0).limits(0.01, 1e6).unit("kg").step(0.5)
            .reference("corda de sisal de 20 m ~ 8 kg")
            .tooltip("Massa efetiva usada para calcular aceleracao pela forca resultante."));

        parameters.add(Parameter.of("resistance", "Resistencia", 1.4)
            .range(0.0, 5.0).limits(0, 1e5).unit("N").step(0.1)
            .tooltip("Atrito/resistencia que segura a corda perto do equilibrio."));
    }

    @Override
    protected void buildScene() {
        context.setGravityStrength(0);
        context.setPaused(false);

        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);

        builder.box(13.2, 0.10, 3.2, new ColorRGBA(0.08, 0.10, 0.16))
            .at(0, -4.92, 0)
            .isStatic()
            .named("palco_cabo")
            .add();

        builder.box(13.4, 0.08, 0.18, new ColorRGBA(0.26, 0.32, 0.48))
            .at(0, -3.05, -1.05)
            .isStatic()
            .named("fundo_cabo")
            .add();

        builder.box(11.0, 0.10, 0.35, new ColorRGBA(0.20, 0.23, 0.36))
            .at(0, -4.35, 0)
            .isStatic()
            .named("trilho_cabo")
            .add();

        builder.box(0.05, 0.95, 0.10, new ColorRGBA(1.0, 0.78, 0.28))
            .at(0, -4.05, 0)
            .isStatic()
            .named("linha_central")
            .add();

        builder.box(0.08, 1.4, 0.12, ColorRGBA.RED)
            .at(-WIN_LIMIT, -3.65, 0)
            .isStatic()
            .named("limite_esquerda")
            .add();

        builder.box(0.08, 1.4, 0.12, ColorRGBA.BLUE)
            .at(WIN_LIMIT, -3.65, 0)
            .isStatic()
            .named("limite_direita")
            .add();

        rope = builder.box(8.7, 0.16, 0.16, new ColorRGBA(0.95, 0.78, 0.35))
            .at(0, -3.75, 0)
            .isStatic()
            .named("corda")
            .add();

        marker = builder.box(0.25, 1.25, 0.38, ColorRGBA.WHITE)
            .at(0, -3.55, 0)
            .isStatic()
            .named("marca_central")
            .add();

        leftPlayer = builder.box(0.8, 1.2, 0.8, ColorRGBA.RED)
            .at(-5.8, -3.35, 0)
            .isStatic()
            .named("jogador_a")
            .add();

        rightPlayer = builder.box(0.8, 1.2, 0.8, ColorRGBA.BLUE)
            .at(5.8, -3.35, 0)
            .isStatic()
            .named("jogador_l")
            .add();

        resetMatchState();
    }

    @Override
    public void onUpdate(double dt) {
        if (!winner.isEmpty()) {
            updateVisuals();
            return;
        }

        double mass = parameters.getValue("mass", 8.0);
        double resistance = parameters.getValue("resistance", 1.4);
        double resultForce = rightForce - leftForce;
        double drag = ropeVelocity * resistance;
        double acceleration = (resultForce - drag) / Math.max(0.1, mass);

        ropeVelocity += acceleration * dt;
        ropeVelocity = clamp(ropeVelocity, -MAX_ROPE_SPEED, MAX_ROPE_SPEED);
        ropePosition += ropeVelocity * dt;
        ropePosition = clamp(ropePosition, -WIN_LIMIT, WIN_LIMIT);

        double decay = parameters.getValue("decay", 2.4);
        leftForce = Math.max(0, leftForce - decay * dt);
        rightForce = Math.max(0, rightForce - decay * dt);
        updateCpsWindows();

        if (ropePosition <= -WIN_LIMIT) {
            winner = "Jogador A venceu!";
            ropeVelocity = 0;
            recordMatch("Vitoria A");
        } else if (ropePosition >= WIN_LIMIT) {
            winner = "Jogador L venceu!";
            ropeVelocity = 0;
            recordMatch("Vitoria L");
        }

        updateVisuals();
    }

    @Override
    public void onParameterChanged(String paramName, double newValue) {
        if ("mass".equals(paramName) || "resistance".equals(paramName)) {
            ropeVelocity *= 0.5;
        }
    }

    @Override
    protected void onPostReset() {
        resetMatchState();
    }

    public void pressLeft() {
        if (!winner.isEmpty()) return;
        leftForce += parameters.getValue("tap_force", 1.25);
        leftTaps++;
        registerTap(leftTapTimes, true);
        nudgePlayer(leftPlayer, -0.08);
    }

    public void pressRight() {
        if (!winner.isEmpty()) return;
        rightForce += parameters.getValue("tap_force", 1.25);
        rightTaps++;
        registerTap(rightTapTimes, false);
        nudgePlayer(rightPlayer, 0.08);
    }

    public void holdLeft(double dt) {
        if (!winner.isEmpty()) return;
        leftForce += parameters.getValue("hold_force", 12.0) * dt;
    }

    public void holdRight(double dt) {
        if (!winner.isEmpty()) return;
        rightForce += parameters.getValue("hold_force", 12.0) * dt;
    }

    @Override
    public String launchActionLabel() { return "Reiniciar partida"; }

    @Override
    public void launch() { onReset(); }

    @Override
    public String telemetry() {
        double resultForce = rightForce - leftForce;
        String status = winner.isEmpty()
            ? "Aperte A vs L para disputar"
            : winner + "  (Reset para jogar de novo)";

        return String.format(
            "%s%n%n%s (A)  x  %s (L)%nToques: %d  x  %d%nCPS: %.1f  x  %.1f%nRecord CPS: %.1f  x  %.1f%nForca: %.1f N  x  %.1f N%nResultante: %.1f N%nPosicao: %.2f m%nVelocidade: %.2f m/s",
            status, leftName, rightName, leftTaps, rightTaps, getLeftCps(), getRightCps(),
            leftBestCps, rightBestCps, leftForce, rightForce, resultForce,
            ropePosition, ropeVelocity
        );
    }

    private void resetMatchState() {
        ropePosition = 0;
        ropeVelocity = 0;
        leftForce = 0;
        rightForce = 0;
        leftTaps = 0;
        rightTaps = 0;
        leftBestCps = 0;
        rightBestCps = 0;
        winner = "";
        matchRecorded = false;
        leftTapTimes.clear();
        rightTapTimes.clear();
        updateVisuals();
    }

    private void updateVisuals() {
        if (rope != null) {
            rope.getBody().setPosition(new Vec3(ropePosition * 0.35, -3.75, 0));
        }
        if (marker != null) {
            marker.getBody().setPosition(new Vec3(ropePosition, -3.55, 0));
        }
        if (leftPlayer != null) {
            leftPlayer.getBody().setPosition(new Vec3(-5.8 + ropePosition * 0.10, -3.35, 0));
        }
        if (rightPlayer != null) {
            rightPlayer.getBody().setPosition(new Vec3(5.8 + ropePosition * 0.10, -3.35, 0));
        }
    }

    private void nudgePlayer(SceneObject player, double offset) {
        if (player == null) return;
        Vec3 p = player.getBody().getPosition();
        player.getBody().setPosition(new Vec3(p.x + offset, p.y, p.z));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public String getLeftName() { return leftName; }
    public String getRightName() { return rightName; }
    public void setLeftName(String name) { leftName = cleanName(name, "Jogador A"); }
    public void setRightName(String name) { rightName = cleanName(name, "Jogador L"); }
    public int getLeftTaps() { return leftTaps; }
    public int getRightTaps() { return rightTaps; }
    public double getLeftForce() { return leftForce; }
    public double getRightForce() { return rightForce; }
    public double getRopePosition() { return ropePosition; }
    public double getWinLimit() { return WIN_LIMIT; }
    public String getWinner() { return winner; }
    public double getLeftCps() { updateCpsWindows(); return leftTapTimes.size(); }
    public double getRightCps() { updateCpsWindows(); return rightTapTimes.size(); }
    public double getLeftBestCps() { return leftBestCps; }
    public double getRightBestCps() { return rightBestCps; }
    public double getLeftGlobalBestCps() { return leftGlobalBestCps; }
    public double getRightGlobalBestCps() { return rightGlobalBestCps; }
    public int getTapLead() { return leftTaps - rightTaps; }
    public String getCurrentTapLeader() {
        if (leftTaps == rightTaps) return "Empate em cliques";
        return leftTaps > rightTaps
            ? leftName + " lidera por " + (leftTaps - rightTaps)
            : rightName + " lidera por " + (rightTaps - leftTaps);
    }
    public List<RankingEntry> getRanking() {
        return List.copyOf(ranking);
    }

    private void registerTap(Deque<Double> taps, boolean left) {
        taps.addLast(context.getSimulationTime());
        updateCpsWindows();
        if (left) {
            leftBestCps = Math.max(leftBestCps, taps.size());
            leftGlobalBestCps = Math.max(leftGlobalBestCps, taps.size());
        } else {
            rightBestCps = Math.max(rightBestCps, taps.size());
            rightGlobalBestCps = Math.max(rightGlobalBestCps, taps.size());
        }
    }

    private void recordMatch(String result) {
        if (matchRecorded) return;
        matchRecorded = true;
        ranking.add(new RankingEntry(leftName, leftTaps, leftBestCps, result));
        ranking.add(new RankingEntry(rightName, rightTaps, rightBestCps, result));
        ranking.sort(Comparator
            .comparingInt(RankingEntry::taps).reversed()
            .thenComparing(Comparator.comparingDouble(RankingEntry::bestCps).reversed()));
        while (ranking.size() > 6) ranking.remove(ranking.size() - 1);
    }

    private void updateCpsWindows() {
        double now = context == null ? 0 : context.getSimulationTime();
        trimWindow(leftTapTimes, now);
        trimWindow(rightTapTimes, now);
    }

    private void trimWindow(Deque<Double> taps, double now) {
        while (!taps.isEmpty() && now - taps.peekFirst() > 1.0) {
            taps.removeFirst();
        }
    }

    private static String cleanName(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String clean = value.trim();
        return clean.length() > 18 ? clean.substring(0, 18) : clean;
    }
}
