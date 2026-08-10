package engine.simulation.modules;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.scene.SceneObject;
import engine.simulation.LabMeasurement;
import engine.simulation.ModuleSceneBuilder;
import engine.simulation.SimulationModule;
import engine.simulation.challenges.Challenge;
import engine.simulation.challenges.ChallengeResult;
import engine.simulation.parameters.Parameter;

import java.util.List;

/**
 * Queda livre deterministica baseada em MUV:
 * h(t) = h0 + v0.t - 1/2.g.t^2, usando eixo vertical positivo para cima.
 * A telemetria mostra velocidade escalar, portanto nunca negativa.
 */
public class FreeFallModule extends SimulationModule {

    private static final String ID = "free_fall";

    private SceneObject fallingObject;
    private SceneObject refSmall;
    private SceneObject refLarge;

    private double dropTime;
    private double initialHeight;
    private double initialVelocity;
    private double currentHeight;
    private double currentVelocity;
    private double currentSpeed;
    private double radius;
    private double floorY;
    private boolean landed;

    public FreeFallModule() {
        super(ID, "Queda Livre",
            "Simule queda livre por MUV vertical: S = S0 + v0.t - 1/2.g.t2.",
            "Mecanica - Cinematica");
    }

    @Override
    protected void declareParameters() {
        parameters.add(Parameter.of("gravity", "Gravidade", 9.81)
            .range(0, 30).limits(0, 1e6).unit("m/s2").step(0.1)
            .reference("Lua 1,62 | Marte 3,71 | Terra 9,81 | Jupiter 24,79 | Sol 274 m/s2")
            .outOfRangeNote("A queda fica rapida demais para acompanhar na tela.")
            .tooltip("Aceleracao gravitacional. Terra=9.81, Lua=1.62, Marte=3.72."));

        parameters.add(Parameter.of("initial_height", "Altura inicial", 8.0)
            .range(0.1, 100).limits(0.01, 1e7).unit("m").step(0.5)
            .reference("Torre de Pisa 56 | Torre Eiffel 330 | Everest 8 849 m")
            .outOfRangeNote("Acima de ~100 m o objeto comeca fora do enquadramento; as medidas continuam corretas.")
            .tooltip("Altura acima do chao fisico."));

        parameters.add(Parameter.of("initial_velocity", "Vel. inicial", 0.0)
            .range(0, 100).limits(0, 2.99e8).unit("m/s").step(0.5)
            .reference("Usain Bolt 12 | carro 28 | som 343 | luz 3,00e8 m/s")
            .outOfRangeNote("Acima de 3,0e7 m/s (10% da luz) a mecanica newtoniana deste simulador deixa de valer.")
            .tooltip("Modulo da velocidade inicial para cima. A velocidade escalar nunca e negativa."));

        parameters.add(Parameter.of("object_radius", "Raio do objeto", 0.5)
            .range(0.05, 5.0).limits(1e-4, 1e4).unit("m").step(0.1)
            .reference("bola de tenis 0,033 | bola de boliche 0,109 | esfera de demolicao 0,5 m")
            .tooltip("Tamanho visual. A aceleracao de queda independe da massa."));
    }

    @Override
    protected void buildScene() {
        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        floorY = context.getPhysics().getFloorY();
        context.setGravityStrength(0);

        initialHeight = parameters.getValue("initial_height", 8.0);
        initialVelocity = parameters.getValue("initial_velocity", 0.0);
        radius = parameters.getValue("object_radius", 0.5);
        double startY = floorY + radius + initialHeight;

        fallingObject = builder.sphere(radius, ColorRGBA.BLUE)
            .at(0, startY, 0)
            .isStatic()
            .named("bola_queda")
            .add();

        refSmall = builder.sphere(0.3, ColorRGBA.ORANGE)
            .at(-3, floorY + 0.3 + initialHeight, 0)
            .isStatic()
            .named("ref_pequena")
            .add();

        refLarge = builder.sphere(1.0, ColorRGBA.GREEN)
            .at(3, floorY + 1.0 + initialHeight, 0)
            .isStatic()
            .named("ref_grande")
            .add();

        builder.box(0.08, initialHeight + 1.6, 0.08, new ColorRGBA(0.32, 0.43, 0.58, 0.72))
            .at(-1.9, floorY + (initialHeight + 1.6) * 0.5, -0.9)
            .isStatic()
            .named("coluna_escala_esquerda")
            .add();
        builder.box(0.08, initialHeight + 1.6, 0.08, new ColorRGBA(0.32, 0.43, 0.58, 0.72))
            .at(1.9, floorY + (initialHeight + 1.6) * 0.5, -0.9)
            .isStatic()
            .named("coluna_escala_direita")
            .add();
        for (int tick = 0; tick <= 4; tick++) {
            double y = floorY + 0.16 + initialHeight * tick / 4.0;
            builder.box(3.9, 0.035, 0.05, new ColorRGBA(0.70, 0.82, 0.94, 0.46))
                .at(0, y, -0.9)
                .isStatic()
                .named("marca_altura_" + tick)
                .add()
                .setShadowCaster(false);
        }
        builder.box(0.05, initialHeight + 0.5, 0.05, new ColorRGBA(0.8, 0.85, 1.0, 0.65))
            .at(-1.4, floorY + (initialHeight + 0.5) * 0.5, 0)
            .isStatic()
            .named("regua_altura")
            .add();

        dropTime = 0;
        currentHeight = initialHeight;
        currentVelocity = initialVelocity;
        currentSpeed = Math.abs(initialVelocity);
        landed = false;
        released = false;
    }

    /**
     * O objeto fica suspenso ate o comando de soltar.
     *
     * <p>Antes a queda comecava sozinha ao abrir o modulo: quando a pessoa
     * olhava, a bola ja estava no chao e nao havia o que ver. Segurar o objeto
     * ate o clique tambem espelha o experimento real, em que se ajusta a altura
     * primeiro e so depois se solta.
     */
    private boolean released;

    @Override
    public String launchActionLabel() {
        return released ? "Soltar novamente" : "Soltar o objeto";
    }

    @Override
    public void launch() {
        if (released) onReset();   // buildScene devolve released para false
        released = true;
    }

    @Override
    public void onUpdate(double dt) {
        if (fallingObject == null || landed || !released) return;
        dropTime += dt;
        double g = context.getPhysics().isGravityEnabled()
            ? parameters.getValue("gravity", 9.81)
            : 0.0;

        currentHeight = initialHeight + initialVelocity * dropTime - 0.5 * g * dropTime * dropTime;
        currentVelocity = initialVelocity - g * dropTime;

        if (currentHeight <= 0) {
            currentHeight = 0;
            if (g > 0.01) {
                dropTime = getTheoreticalTime();
                currentVelocity = initialVelocity - g * dropTime;
            }
            landed = true;
        }
        currentSpeed = Math.abs(currentVelocity);
        updateObject(fallingObject, 0, radius, currentHeight);
        updateObject(refSmall, -3, 0.3, currentHeight);
        updateObject(refLarge, 3, 1.0, currentHeight);
    }

    private void updateObject(SceneObject obj, double x, double r, double height) {
        obj.getBody().setPosition(new Vec3(x, floorY + r + height, 0));
        obj.getBody().setVelocity(landed ? Vec3.ZERO : new Vec3(0, currentVelocity, 0));
    }

    @Override
    public void onParameterChanged(String paramName, double newValue) {
        onReset();
    }

    public static List<Challenge> buildChallenges() {
        return List.of(
            new Challenge("ff_01",
                "Comparar gravidades",
                "Use Terra, Lua e Marte. Compare o tempo ate o impacto.",
                "t = sqrt(2h/g) quando v0 = 0.",
                300, 60.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Ajuste a gravidade e observe o tempo.");
                }
            },
            new Challenge("ff_02",
                "Velocidade inicial",
                "Use velocidade inicial para cima e veja o objeto subir antes de cair.",
                "A componente vertical e vy = v0 - g.t; a velocidade escalar e |vy|.",
                400, 90.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Mude a velocidade inicial.");
                }
            }
        );
    }

    @Override
    public String telemetry() {
        double g = parameters.getValue("gravity", 9.81);
        String direction = landed ? "parado no impacto"
            : !context.getPhysics().isGravityEnabled() && Math.abs(currentVelocity) < 0.01 ? "sem gravidade"
            : currentVelocity > 0.01 ? "subindo"
            : currentVelocity < -0.01 ? "descendo"
            : "ponto mais alto";
        double theoretical = getTheoreticalTime();
        return String.format(
            "Queda livre | %s%n%nMEDIDAS%nTempo: %.2f s%nTempo teorico: %.2f s%nAltura atual: %.2f m%nVelocidade: %.2f m/s%nSentido: %s%n%nCONFIGURACAO%nAltura inicial: %.2f m%nGravidade: %s%n%nMODELO%n> h = h0 + v0.t - g.t2/2   |   vy = v0 - g.t",
            landed ? "impacto no chao" : "em queda",
            dropTime, theoretical, currentHeight, currentSpeed, direction, initialHeight,
            context.getPhysics().isGravityEnabled() ? String.format("%.2f m/s2", g) : "desligada"
        );
    }

    public double getDropTime() { return dropTime; }

    public double getTheoreticalTime() {
        double g = parameters.getValue("gravity", 9.81);
        if (g <= 0.01) return 0;
        double disc = initialVelocity * initialVelocity + 2 * g * initialHeight;
        return (initialVelocity + Math.sqrt(disc)) / g;
    }

    @Override
    public LabMeasurement sampleLabMeasurement() {
        return new LabMeasurement(
            "queda livre",
            "altura", "m", currentHeight,
            "velocidade", "m/s", currentSpeed,
            String.format("h=%.2f m | |vy|=%.2f m/s | t impacto teorico=%.2f s",
                currentHeight, currentSpeed, getTheoreticalTime())
        );
    }
}
