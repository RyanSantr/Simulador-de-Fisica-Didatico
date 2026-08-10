package engine.simulation.modules;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.scene.SceneObject;
import engine.simulation.LabMeasurement;
import engine.simulation.ModuleSceneBuilder;
import engine.simulation.SimulationModule;
import engine.simulation.parameters.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Oscilador massa-mola horizontal.
 */
public class SpringModule extends SimulationModule {

    private final List<SceneObject> springMarkers = new ArrayList<>();
    private SceneObject mass;
    private Vec3 anchor;
    private double displacement;
    private double velocity;
    private double acceleration;

    public SpringModule() {
        super("spring", "Mola",
            "Explore a lei de Hooke e a energia de um oscilador massa-mola.",
            "Mecanica - Oscilacoes");
    }

    @Override
    protected void declareParameters() {
        parameters.add(Parameter.of("mass", "Massa", 1.2)
            .range(0.1, 8).limits(1e-6, 1e9).unit("kg").step(0.1)
            .reference("bola de tenis 0,058 | notebook 1,5 | pessoa 70 kg")
            .outOfRangeNote("Massa maior deixa a oscilacao mais lenta: o periodo cresce com a raiz de m."));
        parameters.add(Parameter.of("stiffness", "Constante k", 18)
            .range(0.5, 80).limits(1e-3, 1e9).unit("N/m").step(0.5)
            .reference("mola de caneta 50 | mola de colchao 8 000 | suspensao de carro 30 000 N/m")
            .outOfRangeNote("Rigidez muito alta acelera a oscilacao alem do que o passo de 120 Hz resolve bem."));
        parameters.add(Parameter.of("amplitude", "Alongamento inicial", 2.0)
            .range(0.1, 5).limits(0, 1e4).unit("m").step(0.1)
            .outOfRangeNote("Molas reais deixam de obedecer Hooke muito antes do limite elastico."));
        parameters.add(Parameter.of("damping", "Amortecimento", 0.16)
            .range(0, 2).limits(0, 1e6).unit("N.s/m").step(0.02)
            .outOfRangeNote("Acima do amortecimento critico o sistema volta ao repouso sem oscilar."));
    }

    @Override
    protected void buildScene() {
        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        context.setGravityStrength(0);
        anchor = new Vec3(-5.2, -1.1, 0);
        displacement = parameters.getValue("amplitude", 2.0);
        velocity = 0;
        acceleration = 0;
        released = false;

        builder.box(0.25, 3.2, 2.2, ColorRGBA.fromHex("#475569"))
            .at(anchor.add(new Vec3(-0.2, 0, 0)))
            .isStatic().named("parede_mola").add();
        builder.box(11.2, 0.10, 1.55, ColorRGBA.fromHex("#17212f"))
            .at(anchor.x + 4.8, anchor.y - 0.64, 0)
            .isStatic().named("base_mola").add();
        builder.box(10.4, 0.06, 0.06, new ColorRGBA(0.34, 0.78, 0.84, 0.50))
            .at(anchor.x + 4.7, anchor.y - 0.56, -0.72)
            .isStatic().named("trilho_mola_a").add();
        builder.box(10.4, 0.06, 0.06, new ColorRGBA(0.34, 0.78, 0.84, 0.50))
            .at(anchor.x + 4.7, anchor.y - 0.56, 0.72)
            .isStatic().named("trilho_mola_b").add();
        builder.box(0.06, 2.0, 0.06, new ColorRGBA(1.0, 0.85, 0.35, 0.68))
            .at(anchor.x + 3.0, anchor.y, -0.95)
            .isStatic().named("equilibrio_mola").add().setShadowCaster(false);
        mass = builder.box(1.0, 1.0, 1.0, ColorRGBA.fromHex("#fb7185"))
            .at(massPosition()).isStatic().named("massa_mola").add();
        mass.setRenderLayer(1);
        springMarkers.clear();
        for (int i = 0; i < 14; i++) {
            springMarkers.add(builder.sphere(0.08, ColorRGBA.fromHex("#fde68a"))
                .at(anchor).isStatic().named("espira_" + i).add());
        }
        updateVisuals();
    }

    /**
     * Integra o oscilador com sub-passos adaptativos.
     *
     * <h3>Por que sub-dividir</h3>
     * O Euler semi-implicito so e estavel enquanto {@code omega * dt < 2}, sendo
     * {@code omega = sqrt(k/m)} a frequencia natural. Com a mola rigida e massa
     * pequena que o usuario pode digitar (k = 1e6 N/m, m = 1 g dao
     * omega = 31 623 rad/s), um unico passo de 1/60 s teria {@code omega * dt}
     * na casa de 500: a amplitude dobra a cada passo e em poucos quadros o
     * deslocamento vira infinito e depois NaN.
     *
     * <p>Dividir o quadro em passos menores mantem cada um dentro da regiao
     * estavel. O numero de subdivisoes tem teto para o app nao congelar em casos
     * absurdos; ao bate-lo, {@link #isBeyondResolution()} passa a avisar na
     * telemetria em vez de exibir numeros sem sentido.
     */
    /**
     * A massa fica presa no alongamento inicial ate o comando de soltar, como
     * quem estica a mola e depois libera.
     */
    private boolean released;

    @Override
    public String launchActionLabel() {
        return released ? "Soltar novamente" : "Soltar a mola";
    }

    @Override
    public void launch() {
        if (released) onReset();
        released = true;
    }

    @Override
    public void onUpdate(double dt) {
        if (mass == null || dt <= 0 || !released) return;

        double massKg = Math.max(1e-9, parameters.getValue("mass", 1.2));
        double k = parameters.getValue("stiffness", 18);
        double damping = parameters.getValue("damping", 0.16);

        // Duas escalas de tempo limitam o passo, e a mais rapida manda:
        //   - oscilacao:      omega = sqrt(k/m)
        //   - amortecimento:  taxa  = c/m
        // Considerar so a primeira era um erro: com amortecimento alto e mola
        // mole, omega fica pequeno, o metodo nao subdividia nada e o termo
        // -c.v/m estourava para infinito em poucos passos.
        double omega = Math.sqrt(Math.max(0, k) / massKg);
        double dampingRate = Math.max(0, damping) / massKg;
        double fastestRate = Math.max(omega, dampingRate);
        // Margem de 0,25 sobre o limite teorico de 2, com folga.
        double maxStableStep = fastestRate > 1e-9 ? 0.25 / fastestRate : dt;
        int steps = (int) Math.min(MAX_SUBSTEPS, Math.max(1, Math.ceil(dt / maxStableStep)));
        beyondResolution = steps >= MAX_SUBSTEPS && dt / steps > maxStableStep;

        double subDt = dt / steps;
        for (int i = 0; i < steps; i++) {
            acceleration = (-k * displacement - damping * velocity) / massKg;
            velocity += acceleration * subDt;
            displacement += velocity * subDt;
        }

        // Rede de seguranca: qualquer divergencia residual para aqui, em vez de
        // propagar NaN para o grafico, o CSV e a cena 3D.
        if (!Double.isFinite(displacement) || !Double.isFinite(velocity)) {
            displacement = parameters.getValue("amplitude", 2.0);
            velocity = 0;
            acceleration = 0;
            beyondResolution = true;
        }
        updateVisuals();
    }

    /** True quando os parametros exigem passo menor do que o simulador oferece. */
    public boolean isBeyondResolution() { return beyondResolution; }

    /** Teto de subdivisoes por quadro: protege o FPS em configuracoes extremas. */
    private static final int MAX_SUBSTEPS = 4096;

    private boolean beyondResolution;

    @Override
    public void onParameterChanged(String paramName, double newValue) {
        onReset();
    }

    @Override
    public String telemetry() {
        double k = parameters.getValue("stiffness", 18);
        double massKg = parameters.getValue("mass", 1.2);
        double energy = 0.5 * k * displacement * displacement + 0.5 * massKg * velocity * velocity;
        double omega = Math.sqrt(k / Math.max(1e-9, massKg));
        double period = omega > 1e-9 ? 2 * Math.PI / omega : 0;
        return String.format(
            "Massa-mola | %s%n%nMEDIDAS%nDeslocamento: %.2f m%nVelocidade: %.2f m/s%nAceleracao: %.2f m/s2%nEnergia mecanica: %.2f J%n%nCONFIGURACAO%nRigidez: %.1f N/m%nMassa: %.3f kg%nPeriodo natural: %.4f s%n%nMODELO%n> Lei de Hooke: F = -k.x   |   a = (-k.x - c.v)/m%s",
            beyondResolution ? "fora da resolucao do integrador" : "oscilador harmonico",
            displacement, velocity, acceleration, energy, k, massKg, period,
            beyondResolution
                ? String.format("%n> A combinacao de rigidez e massa exige passo menor que o simulador oferece."
                    + " Reduza a rigidez ou aumente a massa para o resultado voltar a ser confiavel.")
                : ""
        );
    }

    private void updateVisuals() {
        Vec3 pos = massPosition();
        mass.getBody().setPosition(pos);
        mass.getBody().setVelocity(new Vec3(velocity, 0, 0));
        for (int i = 0; i < springMarkers.size(); i++) {
            double t = (i + 1.0) / (springMarkers.size() + 1.0);
            double zig = (i % 2 == 0 ? 1 : -1) * 0.18;
            springMarkers.get(i).getBody().setPosition(new Vec3(
                anchor.x + (pos.x - anchor.x - 0.55) * t,
                anchor.y + zig,
                zig * 0.35
            ));
        }
    }

    private Vec3 massPosition() {
        return new Vec3(anchor.x + 3.0 + displacement, anchor.y, 0);
    }

    @Override
    public LabMeasurement sampleLabMeasurement() {
        return new LabMeasurement(
            "mola",
            "deslocamento", "m", displacement,
            "velocidade", "m/s", velocity,
            String.format("x=%.2f m | v=%.2f m/s | a=%.2f m/s2",
                displacement, velocity, acceleration)
        );
    }
}
