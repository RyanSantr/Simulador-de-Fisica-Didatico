package engine.simulation.modules;

import engine.math.ColorRGBA;
import engine.math.Mat4;
import engine.math.Vec3;
import engine.renderer.Mesh;
import engine.renderer.TextureMap;
import engine.scene.SceneObject;
import engine.simulation.ModuleSceneBuilder;
import engine.simulation.SimulationModule;
import engine.simulation.challenges.Challenge;
import engine.simulation.challenges.ChallengeResult;
import engine.simulation.parameters.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Sistema solar heliocentrico integrado em AU e dias.
 *
 * <h2>Dinamica</h2>
 * Cada planeta parte dos elementos keplerianos J2000 da NASA/JPL e e integrado
 * sob a gravidade newtoniana do Sol com Velocity Verlet. A cena 3D comprime a
 * escala radial (logaritmica) para mostrar planetas internos e externos juntos.
 *
 * <h2>Missao de foguete</h2>
 * O modulo tambem hospeda uma missao interplanetaria opcional. Escolhendo um
 * modelo de foguete, um planeta de partida e um de destino, o simulador:
 * <ol>
 *   <li>calcula a <b>transferencia de Hohmann</b> entre as duas orbitas e o
 *       <b>angulo de fase</b> que abre a janela de lancamento;</li>
 *   <li>verifica se o delta-v do foguete escolhido da conta da manobra;</li>
 *   <li>mantem o foguete acoplado ao planeta de origem ate o alinhamento;</li>
 *   <li>aplica o impulso e integra a nave sob a <i>mesma</i> gravidade solar dos
 *       planetas — inclusive com a massa solar alterada pelas situacoes.</li>
 * </ol>
 * A nave, seu rastro e a rota prevista aparecem na cena junto com os planetas.
 */
public class SolarSystemModule extends SimulationModule {

    private static final String ID = "solar_system";
    private static final double SOLAR_MU_AU3_DAY2 = 0.0002959122082855911;
    private static final double SUN_CAPTURE_RADIUS_AU = 0.06;
    private static final double ORBIT_STEP_DAYS = 0.18;
    private static final int TRAIL_MARKERS = 10;
    private static final int ORBIT_GUIDE_MARKERS = 36;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double SYSTEM_VIEW_RADIUS = 39.0;
    private static final double PLANET_FOCUS_MIN_RADIUS = 3.35;
    private static final double PLANET_FOCUS_MARGIN = 2.85;

    // ── Constantes da missao de foguete ────────────────────────────────────
    /** Conversao de AU/dia para km/s, usada na telemetria de delta-v. */
    private static final double AU_DAY_TO_KM_S = 149_597_870.7 / 86_400.0;
    /**
     * Corredor de aproximacao: abaixo desta distancia a nave conta como chegada.
     *
     * <p>O valor acompanha o raio orbital do destino, como a esfera de influencia
     * gravitacional de cada planeta — a de Jupiter tem 0,32 AU, a de Marte apenas
     * 0,004 AU. Um raio fixo seria generoso demais nos planetas internos e
     * impossivel nos externos.
     *
     * <p>Chegar ao corredor encerra a <i>fase heliocentrica</i> da viagem, que e
     * o que este modulo simula. A captura em orbita do planeta exigiria ainda a
     * queima de frenagem mostrada na telemetria.
     */
    private static double rendezvousRadiusAu(Planet destination) {
        return Math.max(0.05, Math.min(0.45, destination.orbitAu * 0.04));
    }

    /** Acima do corredor, mas dentro deste raio, a passagem conta como sobrevoo. */
    private static double flybyRadiusAu(Planet destination) {
        return rendezvousRadiusAu(destination) * 3.5;
    }
    /**
     * Fracao do voo antes da qual nenhuma aproximacao conta como chegada.
     *
     * <p>Sem essa trava a missao "chegava" na metade do caminho: por volta dos
     * 50% da transferencia a nave e o planeta de destino ficam quase alinhados
     * em angulo, separados so pela diferenca de raio, e a distancia entre eles
     * cai a ~0,1 AU antes de crescer de novo. O encontro real acontece no fim da
     * elipse, quando a nave atinge o afelio junto com o planeta.
     */
    private static final double RENDEZVOUS_EARLIEST_FRACTION = 0.80;
    /**
     * Fracao do tempo de Hohmann usada como alvo da mira de Lambert.
     *
     * <p>A transferencia de Hohmann liga dois pontos separados por exatamente
     * 180 graus — o <b>caso degenerado</b> do problema de Lambert: infinitos
     * planos contem a reta que une dois pontos antipodais, entao existem
     * infinitas trajetorias validas e o jacobiano da solucao numerica fica
     * singular. Mirar um instante levemente anterior (angulo de ~175 graus em
     * vez de 180) remove a ambiguidade sem mudar a fisica de forma perceptivel.
     */
    private static final double AIM_TIME_FRACTION = 0.97;

    /** Diagnostico da convergencia da mira; ligue com -Dsolar.debugAim=true. */
    private static final boolean DEBUG_AIM = Boolean.getBoolean("solar.debugAim");
    private static final int ROCKET_TRAIL_MARKERS = 18;
    private static final int ROCKET_PATH_MARKERS = 46;

    /**
     * Raio minimo dos marcadores da missao (rastro, rota e sinalizador).
     *
     * <p>O renderer descarta objetos simples cujo raio aparente fique abaixo de
     * ~1,15 pixel quando o modo leve esta ligado — que e o padrao. Na visao do
     * sistema inteiro (camera a 39 unidades), esse limite corresponde a um raio
     * de cerca de 0,07: marcadores menores que isso simplesmente sumiam da cena.
     */
    private static final double ROCKET_MARKER_MIN_RADIUS = 0.085;
    private static final double ROCKET_BODY_RADIUS = 0.11;
    private static final double ROCKET_BODY_HEIGHT = 0.68;
    /** Branco levemente azulado do casco, contrastando com a cor do modelo nas aletas. */
    private static final ColorRGBA ROCKET_HULL_COLOR = ColorRGBA.fromHex("#f4f7fb");

    private final List<PlanetBody> bodies = new ArrayList<>();
    private final List<SceneObject> solarHalos = new ArrayList<>();
    private SceneObject sun;
    private Vec3 lastFocusedPlanetPosition;
    private double simulatedDays;
    private double currentSolarMassFactor = 1.0;

    // ── Estado da missao de foguete ────────────────────────────────────────
    private final List<SceneObject> rocketTrail = new ArrayList<>();
    private final List<Vec3> rocketTrailAu = new ArrayList<>();
    private final List<SceneObject> rocketPathMarkers = new ArrayList<>();
    private SceneObject rocketObject;   // casco: corpo + bico
    private SceneObject rocketFins;     // aletas + bocal, na cor do modelo
    private SceneObject rocketGlow;
    private MissionPhase missionPhase = MissionPhase.OFF;
    private Vec3 rocketPositionAu = Vec3.ZERO;
    private Vec3 rocketVelocityAuDay = Vec3.ZERO;
    private double missionRequiredPhase;
    private double missionTransferDays;
    private double missionDepartureDeltaV;   // km/s
    private double missionArrivalDeltaV;     // km/s
    private double missionLaunchDay = -1;
    private double missionArrivalDay = -1;
    private double missionClosestAu = Double.MAX_VALUE;
    private double missionAppliedDeltaV;      // km/s efetivamente aplicados
    private boolean missionAimCorrected;      // true se a mira de Lambert convergiu
    private double previousPhaseError = Double.NaN;
    private double rocketTrailClock;

    public SolarSystemModule() {
        super(ID, "Sistema Solar",
            "Orbitas reais de Mercurio a Netuno. Compare perturbacoes orbitais e lance foguetes entre planetas por transferencia de Hohmann.",
            "Gravitacao - Orbitas, Vetores e Viagem Interplanetaria");
    }

    @Override
    protected void declareParameters() {
        parameters.add(Parameter.of("planet", "Planeta observado", Planet.EARTH.ordinal())
            .options(Planet.labels())
            .tooltip("O planeta selecionado recebe os impulsos de velocidade e aparece na telemetria."));

        parameters.add(Parameter.of("situation", "Situacao", Situation.STABLE.ordinal())
            .options(Situation.labels())
            .tooltip("Escolha a condicao orbital a comparar."));

        parameters.add(Parameter.of("intensity", "Intensidade", 70.0)
            .range(0, 100).limits(0, 100).unit("%").step(1)
            .tooltip("Controla quanto a situacao altera a massa solar ou a velocidade do planeta."));

        parameters.add(Parameter.of("days_per_second", "Dias por segundo", 12.0)
            .range(0.2, 180).limits(0.01, 1e5).unit("d/s").step(0.2)
            .reference("orbita da Terra 365 d | de Netuno 60 182 d")
            // O avanco por quadro satura em MAX_DAYS_PER_FRAME; a 60 FPS isso da
            // 12 x 60 = 720 d/s. Acima disso o valor nao acelera mais nada.
            .outOfRangeNote("Acima de 720 d/s o passo por quadro satura em 12 dias e a simulacao nao acelera mais.")
            .tooltip("Acelera o tempo da simulacao sem mudar as equacoes orbitais."));

        parameters.add(Parameter.of("camera_focus", "Foco camera", CameraFocus.SYSTEM.ordinal())
            .options(CameraFocus.labels())
            .tooltip("Veja o sistema inteiro, acompanhe o planeta observado ou siga o foguete."));

        // ── Missao de foguete ──────────────────────────────────────────────
        // Ja vem ligada com um lancador capaz de fazer Terra -> Marte: assim o
        // foguete aparece na cena assim que o modulo abre, sem exigir setup.
        parameters.add(Parameter.of("rocket", "Foguete", RocketModel.FALCON_HEAVY.ordinal())
            .options(RocketModel.labels())
            .tooltip("Escolha o lancador da missao. Cada modelo tem um delta-v real disponivel: se for pouco para a transferencia, a missao nao sai do planeta."));

        parameters.add(Parameter.of("origin", "Planeta de partida", Planet.EARTH.ordinal())
            .options(Planet.labels())
            .tooltip("Planeta onde o foguete espera a janela de lancamento, orbitando o Sol junto com ele."));

        parameters.add(Parameter.of("destination", "Planeta de destino", Planet.MARS.ordinal())
            .options(Planet.labels())
            .tooltip("Planeta que a nave deve interceptar no fim da elipse de transferencia."));
    }

    @Override
    protected void buildScene() {
        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        context.setGravityStrength(0);
        context.getCamera().setOrbitView(0.58, 1.10, SYSTEM_VIEW_RADIUS, Vec3.ZERO);

        currentSolarMassFactor = situation().solarMassFactor(strength());
        bodies.clear();
        solarHalos.clear();
        lastFocusedPlanetPosition = null;
        simulatedDays = 0;

        sun = builder.sphere(1.15, 10, 16, ColorRGBA.fromHex("#ffd35b"))
            .at(0, 0, 0)
            .isStatic()
            .named("sol")
            .add();
        solarHalos.add(builder.sphere(1.42, 5, 8, new ColorRGBA(1.0, 0.65, 0.15, 0.12))
            .at(0, 0, 0).isStatic().named("halo_sol_0").add());
        solarHalos.add(builder.sphere(1.72, 5, 8, new ColorRGBA(1.0, 0.40, 0.08, 0.07))
            .at(0, 0, 0).isStatic().named("halo_sol_1").add());

        for (Planet planet : Planet.values()) {
            addOrbitGuide(builder, planet);
            bodies.add(createPlanet(builder, planet));
        }
        buildRocketScene(builder);
        context.getObjects().forEach(object -> object.setShadowCaster(false));
        frameCamera();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MISSAO DE FOGUETE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Monta a nave na cena e planeja a transferencia, se houver missao ativa.
     * Sem foguete escolhido (ou com origem igual ao destino) nada e criado.
     */
    private void buildRocketScene(ModuleSceneBuilder builder) {
        rocketTrail.clear();
        rocketTrailAu.clear();
        rocketPathMarkers.clear();
        rocketObject = null;
        rocketFins = null;
        rocketGlow = null;
        missionPhase = MissionPhase.OFF;
        missionLaunchDay = -1;
        missionArrivalDay = -1;
        missionClosestAu = Double.MAX_VALUE;
        missionAppliedDeltaV = 0;
        missionAimCorrected = false;
        previousPhaseError = Double.NaN;
        rocketTrailClock = 0;

        RocketModel rocket = rocketModel();
        if (rocket == RocketModel.NONE || originPlanet() == destinationPlanet()) return;

        planMission();

        PlanetBody origin = bodyOf(originPlanet());
        rocketPositionAu = origin == null ? Vec3.ZERO : origin.positionAu;
        rocketVelocityAuDay = origin == null ? Vec3.ZERO : origin.velocityAuDay;

        // A nave nasce pronta no planeta de partida e so parte quando o usuario
        // aciona o botao. Delta-v insuficiente ja e sinalizado de imediato.
        missionPhase = rocket.deltaVKmS >= missionDepartureDeltaV
            ? MissionPhase.READY
            : MissionPhase.INSUFFICIENT_DELTA_V;

        // Dimensoes: a nave e um simbolo, nao esta em escala — nem os planetas
        // estao em relacao as orbitas. O tamanho precisa passar do limiar de
        // descarte de objetos pequenos do renderer (ver ROCKET_MARKER_MIN_RADIUS),
        // senao o modo leve some com o foguete na visao do sistema inteiro.
        Vec3 renderPosition = toRenderPosition(rocketPositionAu);

        rocketObject = builder.mesh(
                Mesh.createRocketHull(ROCKET_BODY_RADIUS, ROCKET_BODY_HEIGHT, 10),
                ROCKET_BODY_HEIGHT * 0.5, ROCKET_HULL_COLOR)
            .at(renderPosition).isStatic().named("foguete_" + rocket.label).add();

        rocketFins = builder.mesh(
                Mesh.createRocketFins(ROCKET_BODY_RADIUS, ROCKET_BODY_HEIGHT, 4),
                ROCKET_BODY_HEIGHT * 0.5, rocket.color)
            .at(renderPosition).isStatic().named("foguete_aletas").add();

        rocketGlow = builder.sphere(ROCKET_MARKER_MIN_RADIUS * 1.6, 6, 10,
                new ColorRGBA(1.0, 0.62, 0.18, 0.55))
            .at(renderPosition).isStatic().named("foguete_sinalizador").add();

        for (int i = 0; i < ROCKET_TRAIL_MARKERS; i++) {
            double alpha = 0.10 + 0.40 * (double)(i + 1) / ROCKET_TRAIL_MARKERS;
            rocketTrail.add(builder.icosahedron(ROCKET_MARKER_MIN_RADIUS,
                    new ColorRGBA(1.0, 0.78, 0.32, alpha))
                .at(renderPosition).isStatic().named("rastro_foguete_" + i).add());
            rocketTrailAu.add(rocketPositionAu);
        }
        updateRocketVisuals(0);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACAO MANUAL (botao do painel)
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public String launchActionLabel() {
        RocketModel rocket = rocketModel();
        if (rocket == RocketModel.NONE) return "Escolha um foguete";
        if (originPlanet() == destinationPlanet()) return "Escolha destino diferente";
        return switch (missionPhase) {
            case INSUFFICIENT_DELTA_V -> "Delta-v insuficiente";
            case READY -> "Enviar para " + destinationPlanet().label;
            case WAITING_WINDOW, TRANSFER -> "Missao em andamento";
            default -> "Nova missao para " + destinationPlanet().label;
        };
    }

    /**
     * Envia a nave: adianta o relogio ate a janela de lancamento e arma a
     * partida. Chamado pelo botao do painel — nada acontece sem esse comando.
     */
    @Override
    public void launch() {
        if (rocketModel() == RocketModel.NONE
            || originPlanet() == destinationPlanet()
            || missionPhase == MissionPhase.INSUFFICIENT_DELTA_V) {
            return;
        }

        // Missao ja concluida (ou perdida): remonta a cena antes de repetir.
        if (missionPhase == MissionPhase.ARRIVED || missionPhase == MissionPhase.FLYBY
            || missionPhase == MissionPhase.MISSED || missionPhase == MissionPhase.TRANSFER) {
            onReset();
        }
        if (missionPhase != MissionPhase.READY) return;

        missionPhase = MissionPhase.WAITING_WINDOW;
        fastForwardToLaunchWindow();
    }

    /**
     * Avanca planetas e nave ate pouco antes da janela de lancamento.
     *
     * <p>Sem isso a missao Terra-Marte exigiria mais de 400 dias simulados de
     * espera antes de qualquer coisa acontecer — na velocidade padrao, mais de
     * meio minuto olhando o foguete parado. Para destinos externos, muito pior.
     * O adiantamento deixa uma folga curta para que a partida ainda seja vista.
     */
    private void fastForwardToLaunchWindow() {
        if (missionPhase != MissionPhase.WAITING_WINDOW) return;

        PlanetBody origin = bodyOf(originPlanet());
        PlanetBody destination = bodyOf(destinationPlanet());
        if (origin == null || destination == null) return;

        final double marginDays = 12.0;   // folga visivel antes da partida
        final int maxSteps = 240_000;     // teto de seguranca (~43 000 dias)

        for (int step = 0; step < maxSteps; step++) {
            double phase = normalizeAngle(
                orbitalAngle(destination.positionAu) - orbitalAngle(origin.positionAu));
            double error = normalizeAngle(phase - missionRequiredPhase);
            double relativeRate = TWO_PI / destinationPlanet().periodDays
                - TWO_PI / originPlanet().periodDays;
            if (Math.abs(relativeRate) < 1e-12) return;

            // Tempo restante ate o alinhamento, seguindo o sentido da aproximacao.
            double daysToWindow = -error / relativeRate;
            if (daysToWindow < 0) daysToWindow += TWO_PI / Math.abs(relativeRate);
            if (daysToWindow <= marginDays) break;

            for (PlanetBody body : bodies) integrate(body, ORBIT_STEP_DAYS);
            simulatedDays += ORBIT_STEP_DAYS;
        }

        rocketPositionAu = origin.positionAu;
        rocketVelocityAuDay = origin.velocityAuDay;
        for (int i = 0; i < rocketTrailAu.size(); i++) rocketTrailAu.set(i, rocketPositionAu);

        // Os rastros guardam posicoes anteriores ao salto; sem reancorar, cada
        // planeta apareceria ligado a um borrao de marcadores na posicao antiga.
        for (PlanetBody body : bodies) {
            for (int i = 0; i < body.trailAu.size(); i++) body.trailAu.set(i, body.positionAu);
        }

        // Reposiciona tudo que ja existe na cena apos o salto no tempo.
        for (PlanetBody body : bodies) updatePlanetVisuals(body, 0);
        updateRocketVisuals(0);
    }

    /**
     * Calcula a elipse de Hohmann entre as duas orbitas: tempo de voo, delta-v
     * de partida e de frenagem e o angulo de fase que abre a janela.
     *
     * <p>Usa os semieixos maiores como raios circulares equivalentes. As orbitas
     * reais tem excentricidade pequena (exceto Mercurio), entao a aproximacao
     * erra pouco — e o erro residual aparece honestamente na cena como um
     * sobrevoo em vez de encontro perfeito.
     */
    private void planMission() {
        Planet origin = originPlanet();
        Planet destination = destinationPlanet();
        double mu = SOLAR_MU_AU3_DAY2 * currentSolarMassFactor;

        double r1 = origin.orbitAu;
        double r2 = destination.orbitAu;
        double transferSemiMajor = (r1 + r2) * 0.5;

        missionTransferDays = Math.PI * Math.sqrt(
            transferSemiMajor * transferSemiMajor * transferSemiMajor / mu);

        // Equacao vis-viva: v = sqrt(mu * (2/r - 1/a))
        double circular1 = Math.sqrt(mu / r1);
        double circular2 = Math.sqrt(mu / r2);
        double departure = Math.sqrt(mu * (2.0 / r1 - 1.0 / transferSemiMajor));
        double arrival = Math.sqrt(mu * (2.0 / r2 - 1.0 / transferSemiMajor));
        missionDepartureDeltaV = Math.abs(departure - circular1) * AU_DAY_TO_KM_S;
        missionArrivalDeltaV = Math.abs(circular2 - arrival) * AU_DAY_TO_KM_S;

        // O destino precisa estar adiantado o bastante para chegar ao ponto de
        // encontro junto com a nave: fase = 180 graus menos o quanto ele anda
        // durante a viagem.
        double destinationRate = TWO_PI / destination.periodDays;
        missionRequiredPhase = normalizeAngle(Math.PI - destinationRate * missionTransferDays);
    }

    /** Avanca a missao em um sub-passo de {@code dtDays}. */
    private void advanceMission(double dtDays) {
        if (missionPhase == MissionPhase.OFF) return;

        PlanetBody origin = bodyOf(originPlanet());
        PlanetBody destination = bodyOf(destinationPlanet());
        if (origin == null || destination == null) return;

        switch (missionPhase) {
            // Pronta na plataforma: acompanha o planeta, sem nada a simular.
            case READY -> {
                rocketPositionAu = origin.positionAu;
                rocketVelocityAuDay = origin.velocityAuDay;
            }
            case WAITING_WINDOW -> {
                // Antes do lancamento a nave acompanha o planeta de partida.
                rocketPositionAu = origin.positionAu;
                rocketVelocityAuDay = origin.velocityAuDay;
                if (isLaunchWindowOpen(origin, destination)) launchRocket(origin);
            }
            case TRANSFER -> {
                integrateRocket(dtDays);
                double distance = rocketPositionAu.distanceTo(destination.positionAu);
                double flightDays = simulatedDays - missionLaunchDay;

                // So mede a menor distancia na fase final: a aproximacao do meio
                // do voo e geometria da transferencia, nao um encontro.
                boolean nearRendezvous =
                    flightDays >= missionTransferDays * RENDEZVOUS_EARLIEST_FRACTION;
                if (nearRendezvous) missionClosestAu = Math.min(missionClosestAu, distance);

                if (nearRendezvous && distance <= rendezvousRadiusAu(destinationPlanet())) {
                    missionPhase = MissionPhase.ARRIVED;
                    missionArrivalDay = simulatedDays;
                } else if (simulatedDays > missionLaunchDay + missionTransferDays * 1.35) {
                    // Passou da hora do encontro: virou sobrevoo ou erro de mira.
                    missionArrivalDay = simulatedDays;
                    missionPhase = missionClosestAu <= flybyRadiusAu(destinationPlanet())
                        ? MissionPhase.FLYBY
                        : MissionPhase.MISSED;
                }
            }
            // Depois de chegar a nave fica em orbita junto ao planeta de destino.
            case ARRIVED -> {
                rocketPositionAu = destination.positionAu;
                rocketVelocityAuDay = destination.velocityAuDay;
            }
            // Sobrevoo e erro de mira continuam em orbita solar livre.
            case FLYBY, MISSED -> integrateRocket(dtDays);
            case INSUFFICIENT_DELTA_V -> {
                rocketPositionAu = origin.positionAu;
                rocketVelocityAuDay = origin.velocityAuDay;
            }
            default -> { }
        }
    }

    /**
     * Detecta a abertura da janela pelo cruzamento do erro de fase por zero.
     *
     * <p>Comparar apenas {@code |erro| < tolerancia} falharia com passos de tempo
     * grandes (o usuario pode acelerar para 180 dias/s e pular a janela inteira).
     * Observar a troca de sinal captura o instante certo em qualquer velocidade.
     */
    private boolean isLaunchWindowOpen(PlanetBody origin, PlanetBody destination) {
        double currentPhase = normalizeAngle(
            orbitalAngle(destination.positionAu) - orbitalAngle(origin.positionAu));
        double error = normalizeAngle(currentPhase - missionRequiredPhase);

        boolean crossedZero = !Double.isNaN(previousPhaseError)
            && Math.signum(error) != Math.signum(previousPhaseError)
            && Math.abs(error) < 0.6 && Math.abs(previousPhaseError) < 0.6;
        previousPhaseError = error;
        return crossedZero || Math.abs(error) < 0.01;
    }

    /**
     * Aplica o impulso de partida e comeca a transferencia.
     *
     * <p>O chute inicial e o delta-v tangencial de Hohmann — aproveitar a
     * velocidade orbital que a nave herda do planeta e o que torna a manobra
     * barata. Em seguida a mira e refinada por {@link #solveDepartureVelocity}
     * para apontar a nave ao ponto onde o destino <i>estara</i> na chegada.
     */
    private void launchRocket(PlanetBody origin) {
        rocketPositionAu = origin.positionAu;

        Vec3 tangent = origin.velocityAuDay.normalize();
        double signedDeltaV = destinationPlanet().orbitAu > originPlanet().orbitAu
            ? missionDepartureDeltaV      // subir de orbita: acelerar
            : -missionDepartureDeltaV;    // descer de orbita: frear
        Vec3 hohmannVelocity = origin.velocityAuDay
            .add(tangent.mul(signedDeltaV / AU_DAY_TO_KM_S));

        // Mira: onde o destino estara no instante do encontro (ver AIM_TIME_FRACTION).
        double aimTimeDays = missionTransferDays * AIM_TIME_FRACTION;
        PlanetBody destination = bodyOf(destinationPlanet());
        Vec3 aimPoint = destination == null ? null
            : propagatePosition(destination.positionAu, destination.velocityAuDay, aimTimeDays);
        Vec3 aimed = aimPoint == null ? null
            : solveDepartureVelocity(rocketPositionAu, aimPoint, aimTimeDays, hohmannVelocity);

        rocketVelocityAuDay = aimed != null ? aimed : hohmannVelocity;
        missionAppliedDeltaV = rocketVelocityAuDay.sub(origin.velocityAuDay).length() * AU_DAY_TO_KM_S;
        missionAimCorrected = aimed != null;

        missionLaunchDay = simulatedDays;
        missionPhase = MissionPhase.TRANSFER;
        buildPredictedPath();
    }

    /**
     * Propaga um estado (posicao, velocidade) sob a gravidade solar sem tocar na
     * cena. Usado para prever onde o planeta de destino estara na chegada e para
     * avaliar tentativas de mira.
     */
    private Vec3 propagatePosition(Vec3 position, Vec3 velocity, double totalDays) {
        Vec3 p = position;
        Vec3 v = velocity;
        double remaining = totalDays;
        while (remaining > 1e-9) {
            double step = Math.min(ORBIT_STEP_DAYS, remaining);
            Vec3 a0 = solarAcceleration(p);
            Vec3 half = v.add(a0.mul(step * 0.5));
            p = p.add(half.mul(step));
            v = half.add(solarAcceleration(p).mul(step * 0.5));
            remaining -= step;
        }
        return p;
    }

    /**
     * Resolve o problema de Lambert por tiro (shooting) com Newton-Raphson:
     * acha a velocidade de partida que leva a nave de {@code start} ate
     * {@code target} no tempo de voo dado.
     *
     * <p>Por que e necessario: a transferencia de Hohmann classica supoe orbitas
     * circulares e coplanares. As orbitas reais dos planetas sao elipticas e
     * inclinadas, entao o impulso puramente tangencial erra o alvo por decimos
     * de AU. Missoes reais resolvem exatamente este problema — mirar onde o
     * planeta <i>vai estar</i>, nao onde ele esta.
     *
     * <h3>Por que a correcao acontece so no plano da transferencia</h3>
     * Numa transferencia de ~180 graus, girar a trajetoria em torno do eixo que
     * passa pelo ponto de partida mantem os dois extremos no lugar. Ou seja:
     * acrescentar velocidade <i>fora do plano</i> nao move o ponto de chegada —
     * essa direcao e um <b>modo nulo</b> do problema. Tentar resolver o sistema
     * 3x3 completo faz o solver pedir correcoes gigantescas nessa direcao inutil
     * (medidas em 0,03 AU/dia, quase o dobro da velocidade orbital da Terra) e
     * nao converge.
     *
     * <p>Por isso o ajuste e feito apenas nas duas direcoes do plano orbital,
     * por minimos quadrados. O desvio remanescente fora do plano vem da
     * diferenca de inclinacao entre as orbitas e nao tem como ser corrigido por
     * um unico impulso nessa geometria — missoes reais gastam delta-v extra numa
     * manobra de mudanca de plano.
     */
    private Vec3 solveDepartureVelocity(Vec3 start, Vec3 target, double timeOfFlight, Vec3 guess) {
        final double tolerance = 2e-4;   // ~30 000 km: bem dentro do raio de encontro
        final double probe = 1e-7;       // perturbacao das diferencas finitas (AU/dia)

        // Teto do passo. A Terra orbita a 0,0172 AU/dia, entao uma correcao dessa
        // ordem ja mudaria o regime da trajetoria; 0,002 AU/dia (~3,5 km/s)
        // mantem cada iteracao dentro do fisicamente plausivel.
        final double maxStep = 0.002;

        // Base ortonormal do plano de transferencia: radial e transversal.
        Vec3 normal = start.cross(guess);
        if (normal.lengthSq() < 1e-24) return null;
        Vec3 radial = start.normalize();
        Vec3 transverse = normal.normalize().cross(radial).normalize();

        Vec3 velocity = guess;
        double missLength = propagatePosition(start, velocity, timeOfFlight).sub(target).length();
        if (DEBUG_AIM) System.err.printf("[mira] inicio: erro=%.6f AU, tof=%.1f d%n", missLength, timeOfFlight);

        for (int iteration = 0; iteration < 14 && missLength > tolerance; iteration++) {
            Vec3 miss = propagatePosition(start, velocity, timeOfFlight).sub(target);

            // Colunas do jacobiano nas duas direcoes uteis do plano.
            Vec3 jr = propagatePosition(start, velocity.add(radial.mul(probe)), timeOfFlight)
                .sub(target).sub(miss).div(probe);
            Vec3 jt = propagatePosition(start, velocity.add(transverse.mul(probe)), timeOfFlight)
                .sub(target).sub(miss).div(probe);

            // Minimos quadrados 2x2: (J^T J) c = -J^T miss
            double a11 = jr.dot(jr), a12 = jr.dot(jt), a22 = jt.dot(jt);
            double b1 = -jr.dot(miss), b2 = -jt.dot(miss);
            double det = a11 * a22 - a12 * a12;
            if (Math.abs(det) < 1e-20) break;

            Vec3 step = radial.mul((b1 * a22 - b2 * a12) / det)
                .add(transverse.mul((a11 * b2 - a12 * b1) / det));
            if (DEBUG_AIM) System.err.printf("[mira] it=%d erro=%.6f passo=%.6f%n",
                iteration, missLength, step.length());
            if (!Double.isFinite(step.lengthSq())) break;
            if (step.length() > maxStep) step = step.normalize().mul(maxStep);

            // Busca linear: so aceita o passo se ele realmente aproximar do alvo.
            boolean improved = false;
            for (int backtrack = 0, scale = 1; backtrack < 10; backtrack++, scale *= 2) {
                Vec3 candidate = velocity.add(step.div(scale));
                double candidateMiss = propagatePosition(start, candidate, timeOfFlight)
                    .sub(target).length();
                if (candidateMiss < missLength - 1e-9) {
                    velocity = candidate;
                    missLength = candidateMiss;
                    improved = true;
                    break;
                }
            }
            if (!improved) break;   // ja esta no melhor que este metodo alcanca
        }

        if (DEBUG_AIM) System.err.printf("[mira] fim: erro=%.6f AU%n", missLength);
        // Aceita a mira se ela garante a entrada no corredor de aproximacao;
        // senao a missao volta ao impulso de Hohmann puro e a telemetria avisa.
        return missLength <= rendezvousRadiusAu(destinationPlanet()) ? velocity : null;
    }

    /** Integra a nave sob a gravidade solar (mesmo metodo dos planetas). */
    private void integrateRocket(double dtDays) {
        Vec3 a0 = solarAcceleration(rocketPositionAu);
        Vec3 halfVelocity = rocketVelocityAuDay.add(a0.mul(dtDays * 0.5));
        rocketPositionAu = rocketPositionAu.add(halfVelocity.mul(dtDays));
        rocketVelocityAuDay = halfVelocity.add(solarAcceleration(rocketPositionAu).mul(dtDays * 0.5));
    }

    /**
     * Desenha a rota prevista integrando uma copia do estado de lancamento.
     * Mostrar a elipse inteira no instante da partida deixa visivel que a nave
     * nao viaja em linha reta ate o alvo.
     */
    private void buildPredictedPath() {
        for (SceneObject marker : rocketPathMarkers) context.removeObject(marker);
        rocketPathMarkers.clear();

        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        Vec3 position = rocketPositionAu;
        Vec3 velocity = rocketVelocityAuDay;
        int steps = (int)Math.ceil(missionTransferDays * 1.02 / ORBIT_STEP_DAYS);
        int sampleEvery = Math.max(1, steps / ROCKET_PATH_MARKERS);
        ColorRGBA pathColor = new ColorRGBA(0.58, 0.88, 1.0, 0.30);

        for (int i = 0; i < steps; i++) {
            Vec3 a0 = solarAcceleration(position);
            Vec3 halfVelocity = velocity.add(a0.mul(ORBIT_STEP_DAYS * 0.5));
            position = position.add(halfVelocity.mul(ORBIT_STEP_DAYS));
            velocity = halfVelocity.add(solarAcceleration(position).mul(ORBIT_STEP_DAYS * 0.5));
            if (i % sampleEvery == 0) {
                SceneObject marker = builder.icosahedron(ROCKET_MARKER_MIN_RADIUS * 0.9, pathColor)
                    .at(toRenderPosition(position)).isStatic().named("rota_" + i).add();
                marker.setShadowCaster(false);
                rocketPathMarkers.add(marker);
            }
        }
    }

    /** Posiciona nave, chama do motor e rastro na cena. */
    private void updateRocketVisuals(double dt) {
        if (rocketObject == null) return;

        Vec3 renderPosition = toRenderPosition(rocketPositionAu);

        // Casco e aletas sao malhas separadas (para terem cores diferentes) que
        // compartilham posicao e rotacao: sempre se movem como uma peca so.
        Vec3 direction = rocketVelocityAuDay.lengthSq() > 1e-18
            ? rocketVelocityAuDay.normalize()
            : Vec3.UP;
        double yaw = Math.atan2(direction.x, direction.z);
        double pitch = Math.acos(Math.max(-1.0, Math.min(1.0, direction.y)));
        Mat4 attitude = Mat4.rotationY(yaw).mul(Mat4.rotationX(pitch));

        rocketObject.getBody().setPosition(renderPosition);
        rocketObject.setRotationTransform(attitude);
        if (rocketFins != null) {
            rocketFins.getBody().setPosition(renderPosition);
            rocketFins.setRotationTransform(attitude);
        }

        // O sinalizador fica sempre visivel — e o que torna a nave localizavel na
        // visao do sistema inteiro. Durante a queima ele vira chama do motor:
        // desloca para tras e fica alaranjado; parado, e um halo discreto.
        boolean burning = missionPhase == MissionPhase.TRANSFER;
        rocketGlow.setColor(burning
            ? new ColorRGBA(1.0, 0.55, 0.12, 0.85)
            : new ColorRGBA(0.60, 0.85, 1.0, 0.40));
        rocketGlow.getBody().setPosition(burning
            ? renderPosition.sub(direction.mul(ROCKET_BODY_HEIGHT * 0.55))
            : renderPosition);

        rocketTrailClock += dt * parameters.getValue("days_per_second", 12.0);
        if (rocketTrailClock >= Math.max(0.6, missionTransferDays / 110.0)) {
            rocketTrailClock = 0;
            rocketTrailAu.remove(0);
            rocketTrailAu.add(rocketPositionAu);
        }
        for (int i = 0; i < rocketTrail.size(); i++) {
            rocketTrail.get(i).getBody().setPosition(toRenderPosition(rocketTrailAu.get(i)));
        }
    }

    /** Angulo do corpo no plano orbital (o plano da ecliptica e X-Z na cena). */
    private double orbitalAngle(Vec3 positionAu) {
        return Math.atan2(positionAu.z, positionAu.x);
    }

    private PlanetBody bodyOf(Planet planet) {
        for (PlanetBody body : bodies) {
            if (body.planet == planet) return body;
        }
        return null;
    }

    @Override
    public void onUpdate(double dt) {
        if (bodies.isEmpty()) return;
        double days = Math.min(12.0, dt * parameters.getValue("days_per_second", 12.0));
        simulatedDays += days;
        while (days > 1e-9) {
            double step = Math.min(ORBIT_STEP_DAYS, days);
            for (PlanetBody body : bodies) {
                integrate(body, step);
            }
            // A nave avanca depois dos planetas para ver as posicoes do mesmo
            // instante ao medir distancia ao destino e angulo de fase.
            advanceMission(step);
            days -= step;
        }
        for (PlanetBody body : bodies) {
            updatePlanetVisuals(body, dt);
        }
        updateRocketVisuals(dt);
        updateSolarVisuals();
        updateFocusVisuals();
    }

    @Override
    public void onParameterChanged(String paramName, double newValue) {
        switch (paramName) {
            // Velocidade do tempo: lida a cada frame, nao exige remontar a cena.
            case "days_per_second" -> { }

            // Trocar o alvo da camera nao pode reiniciar a simulacao: abortaria
            // uma missao em voo so porque o usuario quis olhar de outro angulo.
            case "camera_focus" -> {
                lastFocusedPlanetPosition = null;
                if (cameraFocus() == CameraFocus.SYSTEM) {
                    context.getCamera().setOrbitView(0.58, 1.10, SYSTEM_VIEW_RADIUS, Vec3.ZERO);
                } else {
                    frameCamera();
                }
            }

            // Planeta observado, situacao, intensidade e parametros da missao
            // mudam as condicoes iniciais: a cena precisa ser remontada.
            default -> onReset();
        }
    }

    public static List<Challenge> buildChallenges() {
        return List.of(
            new Challenge("solar_01",
                "Planetas internos e externos",
                "Observe Mercurio, Terra e Netuno em Orbita base com o mesmo tempo acelerado.",
                "Periodos orbitais maiores fazem os planetas externos mudarem de posicao mais devagar.",
                350, 120.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Troque o planeta observado e compare a telemetria.");
                }
            },
            new Challenge("solar_02",
                "Perturbacao orbital",
                "Acelere ou freie o planeta observado e compare sua nova orbita com a orbita base.",
                "Energia orbital maior aumenta a orbita; energia demais gera trajetoria de escape.",
                500, 160.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Use Situacao e Intensidade para criar a perturbacao.");
                }
            },
            new Challenge("solar_03",
                "Janela de lancamento",
                "Escolha o Falcon Heavy, rota Terra -> Marte, e acompanhe a nave esperar o alinhamento antes de partir.",
                "O alvo precisa estar adiantado o suficiente para chegar ao ponto de encontro junto com a nave.",
                450, 180.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Compare o angulo de fase atual com o necessario na telemetria.");
                }
            },
            new Challenge("solar_04",
                "Ate onde cada foguete chega",
                "Mantenha a rota Terra -> Jupiter e troque o modelo do foguete ate a missao conseguir partir.",
                "O delta-v exigido cresce com a distancia do destino; so os lancadores mais potentes alcancam os gigantes gasosos.",
                600, 180.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Leia o delta-v disponivel de cada foguete e o exigido pela rota.");
                }
            }
        );
    }

    /**
     * Telemetria do modulo, no formato lido por {@code TelemetryView}: primeira
     * linha e o destaque, titulos em maiusculas abrem secoes, {@code Rotulo:
     * valor} vira linha de dados e {@code >} marca nota explicativa.
     *
     * <p>O conteudo e filtrado por relevancia — a ficha completa do planeta e as
     * constantes do modelo so aparecem quando ha espaco para elas, e o bloco da
     * missao mostra os campos da fase atual, nao todos de uma vez.
     */
    @Override
    public String telemetry() {
        PlanetBody selected = selectedBody();
        if (selected == null) return "Sistema Solar carregando...";

        Planet planet = selected.planet;
        OrbitState orbit = orbitState(selected);
        StringBuilder text = new StringBuilder();

        // Sem acentos e sem sinais fora do ASCII, como no resto da telemetria:
        // o texto tambem vai para console e CSV, onde a codificacao varia.
        text.append(planet.label).append(" | ").append(orbit.description).append(lineBreak());

        text.append(lineBreak()).append("ORBITA").append(lineBreak());
        row(text, "Distancia ao Sol", String.format("%.3f AU", selected.positionAu.length()));
        row(text, "Velocidade orbital",
            String.format("%.2f km/s", auPerDayToKmPerSecond(selected.velocityAuDay.length())));
        row(text, "Excentricidade", String.format("%.3f", orbit.eccentricity));
        row(text, "Periodo orbital", String.format("%.1f dias", planet.periodDays));

        text.append(lineBreak()).append("PLANETA").append(lineBreak());
        row(text, "Massa", String.format("%.3f Terras", planet.massEarth));
        row(text, "Raio", String.format("%.3f Terras", planet.radiusEarth));
        row(text, "Gravidade", String.format("%.2f m/s2", planet.surfaceGravityMs2));
        row(text, "Inclinacao axial", String.format("%.1f graus", Math.toDegrees(planet.axialTiltRadians)));
        row(text, "Luas visiveis", Integer.toString(selected.moons.size()));

        text.append(missionTelemetry());

        text.append(lineBreak()).append("CENARIO").append(lineBreak());
        row(text, "Situacao", situation().label);
        if (situation() != Situation.STABLE) {
            row(text, "Intensidade", String.format("%.0f%%", strength() * 100.0));
        }
        row(text, "Massa solar", String.format("%.0f%%", currentSolarMassFactor * 100.0));
        row(text, "Tempo simulado", String.format("%.0f dias", simulatedDays));

        return text.toString();
    }

    /** Acrescenta uma linha {@code Rotulo: valor} ao texto de telemetria. */
    private static void row(StringBuilder text, String label, String value) {
        text.append(label).append(": ").append(value).append(lineBreak());
    }

    private static String lineBreak() {
        return System.lineSeparator();
    }

    /**
     * Bloco da missao. Mostra apenas os campos uteis na fase atual: antes da
     * partida interessa o custo da rota; em voo, a distancia que falta; depois
     * da chegada, a comparacao entre previsto e realizado.
     */
    private String missionTelemetry() {
        StringBuilder text = new StringBuilder();
        text.append(lineBreak()).append("MISSAO DE FOGUETE").append(lineBreak());

        RocketModel rocket = rocketModel();
        if (rocket == RocketModel.NONE) {
            return text.append("> Escolha um modelo em 'Foguete' para lancar uma nave entre dois planetas.")
                .append(lineBreak()).toString();
        }
        if (originPlanet() == destinationPlanet()) {
            return text.append("> Escolha planetas de partida e destino diferentes.")
                .append(lineBreak()).toString();
        }

        PlanetBody destination = bodyOf(destinationPlanet());
        row(text, "Estado", missionStateLabel());
        row(text, "Foguete", rocket.label);
        row(text, "Rota", originPlanet().label + " -> " + destinationPlanet().label);

        switch (missionPhase) {
            case READY, INSUFFICIENT_DELTA_V -> {
                row(text, "Delta-v disponivel", String.format("%.1f km/s", rocket.deltaVKmS));
                row(text, "Delta-v exigido na partida", String.format("%.2f km/s", missionDepartureDeltaV));
                row(text, "Tempo de voo previsto", String.format("%.0f dias", missionTransferDays));
            }
            case WAITING_WINDOW -> {
                row(text, "Angulo de fase necessario",
                    String.format("%.1f graus", Math.toDegrees(missionRequiredPhase)));
                row(text, "Angulo de fase atual", String.format("%.1f graus", currentPhaseDegrees()));
                row(text, "Tempo de voo previsto", String.format("%.0f dias", missionTransferDays));
            }
            case TRANSFER -> {
                row(text, "Distancia ao destino", String.format("%.3f AU",
                    destination == null ? 0 : rocketPositionAu.distanceTo(destination.positionAu)));
                row(text, "Velocidade da nave",
                    String.format("%.2f km/s", auPerDayToKmPerSecond(rocketVelocityAuDay.length())));
                row(text, "Tempo de voo previsto", String.format("%.0f dias", missionTransferDays));
                row(text, "Delta-v aplicado", String.format("%.2f km/s", missionAppliedDeltaV));
            }
            default -> {
                row(text, "Voo real", String.format("%.0f dias", missionArrivalDay - missionLaunchDay));
                row(text, "Tempo de voo previsto", String.format("%.0f dias", missionTransferDays));
                row(text, "Maior aproximacao", missionClosestAu == Double.MAX_VALUE
                    ? "-" : String.format("%.3f AU", missionClosestAu));
                row(text, "Delta-v aplicado", String.format("%.2f km/s", missionAppliedDeltaV));
            }
        }

        String hint = missionHint();
        if (!hint.isEmpty()) text.append("> ").append(hint).append(lineBreak());
        return text.toString();
    }

    /** Angulo de fase atual entre origem e destino, em graus. */
    private double currentPhaseDegrees() {
        PlanetBody origin = bodyOf(originPlanet());
        PlanetBody destination = bodyOf(destinationPlanet());
        if (origin == null || destination == null) return 0;
        return Math.toDegrees(normalizeAngle(
            orbitalAngle(destination.positionAu) - orbitalAngle(origin.positionAu)));
    }

    private String missionStateLabel() {
        return switch (missionPhase) {
            case OFF -> "sem missao";
            case READY -> "PRONTA - aguardando comando de envio";
            case INSUFFICIENT_DELTA_V -> "NAO LANCADO - delta-v insuficiente";
            case WAITING_WINDOW -> "aguardando janela de lancamento";
            case TRANSFER -> String.format("em transferencia (dia %.0f do voo)",
                simulatedDays - missionLaunchDay);
            case ARRIVED -> String.format("CHEGOU ao destino no dia %.0f", missionArrivalDay);
            case FLYBY -> String.format("SOBREVOO do destino no dia %.0f", missionArrivalDay);
            case MISSED -> "errou o alvo e seguiu em orbita solar";
        };
    }

    private String missionHint() {
        return switch (missionPhase) {
            case OFF -> "";
            case READY -> "Clique em \"Enviar para " + destinationPlanet().label
                + "\" no painel de acoes para iniciar a missao.";
            case INSUFFICIENT_DELTA_V -> String.format(
                "Faltam %.2f km/s. Escolha um foguete mais potente ou um destino mais proximo. Missoes reais contornam isso com assistencia gravitacional.",
                missionDepartureDeltaV - rocketModel().deltaVKmS);
            case WAITING_WINDOW -> "A nave orbita junto com o planeta de partida ate o alvo estar na posicao certa.";
            case TRANSFER -> "Meia elipse com perielio e afelio nas duas orbitas: o alvo chega ao ponto de encontro no mesmo instante.";
            case ARRIVED -> String.format("Voo real: %.0f dias | previsto por Hohmann: %.0f dias.",
                missionArrivalDay - missionLaunchDay, missionTransferDays);
            case FLYBY -> "Passou perto, mas fora do raio de encontro: as orbitas reais sao elipticas, e a transferencia foi calculada por circulos equivalentes.";
            case MISSED -> "A perturbacao aplicada ao planeta ou a excentricidade da orbita desalinhou o encontro.";
        };
    }

    private PlanetBody createPlanet(ModuleSceneBuilder builder, Planet planet) {
        OrbitalVectors orbitalVectors = referenceOrbit(planet);
        Vec3 position = orbitalVectors.positionAu;
        Vec3 velocity = orbitalVectors.velocityAuDay;

        if (planet == selectedPlanet()) {
            velocity = velocity.mul(situation().tangentialFactor(strength()));
            velocity = velocity.add(position.normalize().mul(situation().radialKickAuDay(strength())));
        }

        boolean selected = planet == selectedPlanet();
        SceneObject object = builder.sphere(planet.renderRadius, selected ? 12 : 8, selected ? 18 : 12, planet.color)
            .at(toRenderPosition(position))
            .isStatic()
            .named("planeta_" + planet.label)
            .add();
        object.setTexture(TextureMap.load(planet.texturePath));

        PlanetBody body = new PlanetBody(planet, object, position, velocity);
        createTrail(builder, body);
        createRings(builder, body);
        createMoons(builder, body);
        return body;
    }

    private void addOrbitGuide(ModuleSceneBuilder builder, Planet planet) {
        ColorRGBA guideColor = planet.color.mix(ColorRGBA.BLACK, 0.58);
        for (int i = 0; i < ORBIT_GUIDE_MARKERS; i++) {
            double eccentricAnomaly = TWO_PI * i / ORBIT_GUIDE_MARKERS;
            Vec3 physical = orbitalPosition(planet, eccentricAnomaly);
            builder.icosahedron(planet == selectedPlanet() ? 0.065 : 0.04, guideColor)
                .at(toRenderPosition(physical))
                .isStatic()
                .named("orbita_" + planet.label + "_" + i)
                .add();
        }
    }

    private OrbitalVectors referenceOrbit(Planet planet) {
        double eccentricAnomaly = solveEccentricAnomaly(
            planet.meanLongitudeRadians - planet.longitudePerihelionRadians,
            planet.orbitalEccentricity
        );
        Vec3 position = orbitalPosition(planet, eccentricAnomaly);

        double cosE = Math.cos(eccentricAnomaly);
        double sinE = Math.sin(eccentricAnomaly);
        double e = planet.orbitalEccentricity;
        double eccentricRate = Math.sqrt(SOLAR_MU_AU3_DAY2 / (planet.orbitAu * planet.orbitAu * planet.orbitAu))
            / Math.max(1e-9, 1.0 - e * cosE);
        Vec3 velocity = orbitalPlaneToWorld(
            planet,
            -planet.orbitAu * sinE * eccentricRate,
            planet.orbitAu * Math.sqrt(1.0 - e * e) * cosE * eccentricRate
        );
        return new OrbitalVectors(position, velocity);
    }

    private Vec3 orbitalPosition(Planet planet, double eccentricAnomaly) {
        double e = planet.orbitalEccentricity;
        return orbitalPlaneToWorld(
            planet,
            planet.orbitAu * (Math.cos(eccentricAnomaly) - e),
            planet.orbitAu * Math.sqrt(1.0 - e * e) * Math.sin(eccentricAnomaly)
        );
    }

    private Vec3 orbitalPlaneToWorld(Planet planet, double xOrbital, double yOrbital) {
        double ascendingNode = planet.longitudeAscendingNodeRadians;
        double argumentOfPerihelion = planet.longitudePerihelionRadians - ascendingNode;
        double cosNode = Math.cos(ascendingNode);
        double sinNode = Math.sin(ascendingNode);
        double cosPeri = Math.cos(argumentOfPerihelion);
        double sinPeri = Math.sin(argumentOfPerihelion);
        double cosInclination = Math.cos(planet.orbitalInclinationRadians);
        double sinInclination = Math.sin(planet.orbitalInclinationRadians);

        double eclipticX = (cosPeri * cosNode - sinPeri * sinNode * cosInclination) * xOrbital
            + (-sinPeri * cosNode - cosPeri * sinNode * cosInclination) * yOrbital;
        double eclipticY = (cosPeri * sinNode + sinPeri * cosNode * cosInclination) * xOrbital
            + (-sinPeri * sinNode + cosPeri * cosNode * cosInclination) * yOrbital;
        double eclipticZ = sinPeri * sinInclination * xOrbital
            + cosPeri * sinInclination * yOrbital;
        return new Vec3(eclipticX, eclipticZ, eclipticY);
    }

    private double solveEccentricAnomaly(double meanAnomaly, double eccentricity) {
        double normalizedMean = normalizeAngle(meanAnomaly);
        double eccentricAnomaly = normalizedMean + eccentricity * Math.sin(normalizedMean);
        for (int i = 0; i < 8; i++) {
            double residual = eccentricAnomaly - eccentricity * Math.sin(eccentricAnomaly) - normalizedMean;
            double derivative = 1.0 - eccentricity * Math.cos(eccentricAnomaly);
            eccentricAnomaly -= residual / derivative;
        }
        return eccentricAnomaly;
    }

    private void integrate(PlanetBody body, double dtDays) {
        if (body.capturedBySun) return;

        Vec3 a0 = solarAcceleration(body.positionAu);
        Vec3 halfVelocity = body.velocityAuDay.add(a0.mul(dtDays * 0.5));
        Vec3 nextPosition = body.positionAu.add(halfVelocity.mul(dtDays));
        if (nextPosition.length() <= SUN_CAPTURE_RADIUS_AU) {
            body.positionAu = nextPosition.normalize().mul(SUN_CAPTURE_RADIUS_AU);
            body.velocityAuDay = Vec3.ZERO;
            body.capturedBySun = true;
            body.object.setColor(body.planet.color.mix(ColorRGBA.BLACK, 0.55));
            return;
        }

        Vec3 a1 = solarAcceleration(nextPosition);
        body.positionAu = nextPosition;
        body.velocityAuDay = halfVelocity.add(a1.mul(dtDays * 0.5));
    }

    private Vec3 solarAcceleration(Vec3 positionAu) {
        double r = Math.max(SUN_CAPTURE_RADIUS_AU, positionAu.length());
        double mu = SOLAR_MU_AU3_DAY2 * currentSolarMassFactor;
        return positionAu.mul(-mu / (r * r * r));
    }

    private OrbitState orbitState(PlanetBody body) {
        if (body.capturedBySun) {
            return new OrbitState(1.0, "capturado pelo Sol apos perder energia orbital");
        }

        double r = body.positionAu.length();
        double speedSq = body.velocityAuDay.lengthSq();
        double mu = SOLAR_MU_AU3_DAY2 * currentSolarMassFactor;
        double energy = speedSq * 0.5 - mu / r;
        Vec3 h = body.positionAu.cross(body.velocityAuDay);
        Vec3 eccentricityVector = body.velocityAuDay.cross(h).div(mu).sub(body.positionAu.normalize());
        double eccentricity = eccentricityVector.length();

        if (energy >= 0 || eccentricity >= 1.0) {
            return new OrbitState(eccentricity, "energia positiva: tendencia de escape do sistema");
        }
        if (eccentricity < 0.08) {
            return new OrbitState(eccentricity, "orbita ligada quase circular");
        }
        if (situation() == Situation.STRONGER_SUN) {
            return new OrbitState(eccentricity, "gravidade solar maior curva a trajetoria para dentro");
        }
        if (situation() == Situation.WEAKER_SUN) {
            return new OrbitState(eccentricity, "gravidade solar menor alarga a orbita");
        }
        if (situation() == Situation.BRAKE_PLANET) {
            return new OrbitState(eccentricity, "velocidade menor reduz o perihelio");
        }
        if (situation() == Situation.ACCELERATE_PLANET) {
            return new OrbitState(eccentricity, "velocidade maior eleva o afelio");
        }
        if (situation() == Situation.RADIAL_IMPACT) {
            return new OrbitState(eccentricity, "impulso radial tornou a orbita mais alongada");
        }
        return new OrbitState(eccentricity, "orbita ligada eliptica");
    }

    private PlanetBody selectedBody() {
        Planet selected = selectedPlanet();
        for (PlanetBody body : bodies) {
            if (body.planet == selected) return body;
        }
        return null;
    }

    private void createTrail(ModuleSceneBuilder builder, PlanetBody body) {
        Vec3 renderPosition = toRenderPosition(body.positionAu);
        for (int i = 0; i < TRAIL_MARKERS; i++) {
            double alpha = 0.06 + 0.30 * (double)(i + 1) / TRAIL_MARKERS;
            SceneObject marker = builder.icosahedron(body.planet == selectedPlanet() ? 0.060 : 0.038,
                    new ColorRGBA(body.planet.color.r, body.planet.color.g, body.planet.color.b, alpha))
                .at(renderPosition)
                .isStatic()
                .named("rastro_" + body.planet.label + "_" + i)
                .add();
            body.trailObjects.add(marker);
            body.trailAu.add(body.positionAu);
        }
    }

    private void createRings(ModuleSceneBuilder builder, PlanetBody body) {
        if (!body.planet.hasRings) return;
        ColorRGBA ringColor = body.planet == Planet.SATURN
            ? new ColorRGBA(0.86, 0.79, 0.58, 0.88)
            : new ColorRGBA(0.66, 0.88, 0.90, 0.48);
        body.ring = builder.torus(body.planet.renderRadius * 1.38, Math.max(0.018, body.planet.renderRadius * 0.10), ringColor)
            .at(body.object.getBody().getPosition())
            .isStatic()
            .named("aneis_" + body.planet.label)
            .add();
    }

    private void createMoons(ModuleSceneBuilder builder, PlanetBody body) {
        for (NaturalSatellite satellite : NaturalSatellite.values()) {
            if (satellite.parent != body.planet) continue;
            Vec3 position = moonRenderPosition(body.object.getBody().getPosition(), satellite, 0);
            SceneObject moon = builder.sphere(satellite.renderRadius, 5, 8, satellite.color)
                .at(position)
                .isStatic()
                .named("lua_" + satellite.label)
                .add();
            body.moons.add(new MoonBody(satellite, moon));
        }
    }

    private void updatePlanetVisuals(PlanetBody body, double dt) {
        Vec3 renderPosition = toRenderPosition(body.positionAu);
        body.object.getBody().setPosition(renderPosition);
        body.spinRadians = planetSpin(body.planet);
        body.object.setRotationTransform(planetRotation(body.planet, body.spinRadians));

        body.trailClock += dt * parameters.getValue("days_per_second", 12.0);
        if (body.trailClock >= body.planet.trailCadenceDays()) {
            body.trailClock = 0;
            body.trailAu.remove(0);
            body.trailAu.add(body.positionAu);
        }
        for (int i = 0; i < body.trailObjects.size(); i++) {
            body.trailObjects.get(i).getBody().setPosition(toRenderPosition(body.trailAu.get(i)));
        }
        if (body.ring != null) {
            body.ring.getBody().setPosition(renderPosition);
            body.ring.setRotationTransform(planetRotation(body.planet, body.spinRadians * 0.10));
        }
        for (MoonBody moon : body.moons) {
            moon.object.getBody().setPosition(moonRenderPosition(renderPosition, moon.satellite, simulatedDays));
            moon.object.getBody().setRotation(new Vec3(0, simulatedDays / Math.max(0.2, moon.satellite.periodDays), 0));
        }
    }

    private void updateSolarVisuals() {
        if (sun == null) return;
        sun.getBody().setRotation(new Vec3(0.12, simulatedDays * 0.02, 0));
        for (int i = 0; i < solarHalos.size(); i++) {
            double pulse = 0.5 + 0.5 * Math.sin(simulatedDays * 0.10 + i * 1.7);
            solarHalos.get(i).setColor(i == 0
                ? new ColorRGBA(1.0, 0.65 + pulse * 0.12, 0.12, 0.08 + pulse * 0.12)
                : new ColorRGBA(1.0, 0.34 + pulse * 0.10, 0.05, 0.04 + pulse * 0.08));
            solarHalos.get(i).getBody().setRotation(new Vec3(0, simulatedDays * (0.006 + i * 0.003), 0));
        }
    }

    /**
     * Mantem a camera acompanhando o alvo escolhido.
     *
     * <p>Desloca o alvo pelo mesmo vetor que o corpo andou, em vez de fixa-lo na
     * posicao: assim o usuario continua livre para orbitar e dar zoom enquanto a
     * camera segue o planeta ou a nave.
     */
    private void updateFocusVisuals() {
        Vec3 focusPosition = currentFocusPosition();
        if (focusPosition == null) {
            lastFocusedPlanetPosition = null;
            return;
        }
        if (lastFocusedPlanetPosition == null) {
            context.getCamera().setTarget(focusPosition);
        } else {
            Vec3 delta = focusPosition.sub(lastFocusedPlanetPosition);
            context.getCamera().setTarget(context.getCamera().getTarget().add(delta));
        }
        lastFocusedPlanetPosition = focusPosition;
    }

    /**
     * Posicao de cena do alvo atual da camera, ou null para ver o sistema todo.
     *
     * <p>Com foco no foguete e nenhuma missao ativa, cai para o planeta
     * observado: melhor que deixar a camera presa sem explicacao.
     */
    private Vec3 currentFocusPosition() {
        return switch (cameraFocus()) {
            case SYSTEM -> null;
            case PLANET -> selectedObjectPosition();
            case ROCKET -> rocketObject != null
                ? rocketObject.getBody().getPosition()
                : selectedObjectPosition();
        };
    }

    private Vec3 selectedObjectPosition() {
        PlanetBody selected = selectedBody();
        return selected == null ? null : selected.object.getBody().getPosition();
    }

    private void frameCamera() {
        Vec3 focusPosition = currentFocusPosition();
        if (focusPosition == null) return;

        double radius = PLANET_FOCUS_MIN_RADIUS;
        if (cameraFocus() == CameraFocus.PLANET) {
            PlanetBody selected = selectedBody();
            if (selected != null) radius = planetFocusRadius(selected);
        }
        context.getCamera().setOrbitView(0.58, 1.10, radius, focusPosition);
        lastFocusedPlanetPosition = focusPosition;
    }

    private double planetFocusRadius(PlanetBody body) {
        double localRadius = body.planet.renderRadius;
        if (body.ring != null) {
            localRadius = Math.max(localRadius, body.planet.renderRadius * 1.58);
        }
        for (MoonBody moon : body.moons) {
            double moonEnvelope = moon.satellite.renderOrbitRadius() + moon.satellite.renderRadius;
            localRadius = Math.max(localRadius, moonEnvelope);
        }
        return Math.max(PLANET_FOCUS_MIN_RADIUS, localRadius * PLANET_FOCUS_MARGIN);
    }

    private Vec3 moonRenderPosition(Vec3 planetPosition, NaturalSatellite satellite, double days) {
        double direction = satellite.periodDays < 0 ? -1.0 : 1.0;
        double period = Math.max(0.05, Math.abs(satellite.periodDays));
        double angle = satellite.initialAngleRadians + direction * days * Math.PI * 2.0 / period;
        double radius = satellite.renderOrbitRadius();
        double y = Math.sin(angle) * radius * Math.sin(satellite.inclinationRadians);
        Vec3 local = new Vec3(
            Math.cos(angle) * radius,
            y,
            Math.sin(angle) * radius * Math.cos(satellite.inclinationRadians)
        );
        return planetPosition.add(local);
    }

    private Mat4 planetRotation(Planet planet, double spinRadians) {
        return Mat4.rotationX(planet.axialTiltRadians).mul(Mat4.rotationY(spinRadians));
    }

    private double planetSpin(Planet planet) {
        return TWO_PI * simulatedDays / planet.rotationPeriodDays;
    }

    private Vec3 toRenderPosition(Vec3 positionAu) {
        double radius = positionAu.length();
        if (radius < 1e-9) return Vec3.ZERO;
        double compressed = 1.35 + Math.log1p(radius) * 4.25;
        return positionAu.normalize().mul(compressed);
    }

    private double auPerDayToKmPerSecond(double velocity) {
        return velocity * 149_597_870.7 / 86_400.0;
    }

    private double normalizeAngle(double angle) {
        double normalized = angle % TWO_PI;
        if (normalized > Math.PI) normalized -= TWO_PI;
        if (normalized < -Math.PI) normalized += TWO_PI;
        return normalized;
    }

    private static double deg(double degrees) {
        return Math.toRadians(degrees);
    }

    private double strength() {
        return parameters.getValue("intensity", 70.0) / 100.0;
    }

    private Planet selectedPlanet() {
        return Planet.byIndex(parameters.getValue("planet", Planet.EARTH.ordinal()));
    }

    private Situation situation() {
        return Situation.byIndex(parameters.getValue("situation", Situation.STABLE.ordinal()));
    }

    private CameraFocus cameraFocus() {
        return CameraFocus.byIndex(parameters.getValue("camera_focus", CameraFocus.SYSTEM.ordinal()));
    }

    private RocketModel rocketModel() {
        return RocketModel.byIndex(parameters.getValue("rocket", RocketModel.FALCON_HEAVY.ordinal()));
    }

    private Planet originPlanet() {
        return Planet.byIndex(parameters.getValue("origin", Planet.EARTH.ordinal()));
    }

    private Planet destinationPlanet() {
        return Planet.byIndex(parameters.getValue("destination", Planet.MARS.ordinal()));
    }

    private static final class PlanetBody {
        private final Planet planet;
        private final SceneObject object;
        private final List<Vec3> trailAu = new ArrayList<>();
        private final List<SceneObject> trailObjects = new ArrayList<>();
        private final List<MoonBody> moons = new ArrayList<>();
        private Vec3 positionAu;
        private Vec3 velocityAuDay;
        private SceneObject ring;
        private double spinRadians;
        private double trailClock;
        private boolean capturedBySun;

        private PlanetBody(Planet planet, SceneObject object, Vec3 positionAu, Vec3 velocityAuDay) {
            this.planet = planet;
            this.object = object;
            this.positionAu = positionAu;
            this.velocityAuDay = velocityAuDay;
        }
    }

    private record MoonBody(NaturalSatellite satellite, SceneObject object) {}

    private record OrbitalVectors(Vec3 positionAu, Vec3 velocityAuDay) {}

    private record OrbitState(double eccentricity, String description) {}

    /**
     * Etapas da missao interplanetaria.
     *
     * <p>{@code READY} e o estado de repouso: a nave fica montada no planeta de
     * partida e nada e simulado ate o usuario acionar o botao de envio. Sem
     * isso a missao comecaria sozinha ao abrir o modulo, o que atrapalha quem
     * quer apenas observar as orbitas.
     */
    private enum MissionPhase {
        OFF,
        READY,
        INSUFFICIENT_DELTA_V,
        WAITING_WINDOW,
        TRANSFER,
        ARRIVED,
        FLYBY,
        MISSED
    }

    /**
     * Lancadores reais com o delta-v heliocentrico que conseguem entregar.
     *
     * <p>O valor de {@code deltaVKmS} e a mudanca de velocidade que o foguete
     * imprime <i>depois</i> de escapar do planeta de partida — e essa parcela
     * que define ate onde a carga util chega. Os numeros consideram cargas
     * tipicas de sonda; missoes reais esticam o alcance com assistencia
     * gravitacional, manobra que este modulo nao simula.
     */
    private enum RocketModel {
        NONE("Sem foguete", 0.0, 0, "-", "#94a3b8"),
        ARIANE_5("Ariane 5 ECA", 3.1, 10_000, "Lancador comercial europeu (1996-2023)", "#cbd5e1"),
        SATURN_V("Saturn V", 4.0, 45_000, "Levou a Apollo a Lua; maior foguete operacional ate hoje", "#e2e8f0"),
        FALCON_HEAVY("Falcon Heavy", 4.8, 16_000, "SpaceX, propulsores reutilizaveis", "#f1f5f9"),
        SLS_BLOCK_1B("SLS Block 1B", 5.6, 27_000, "Lancador do programa Artemis da NASA", "#fed7aa"),
        STARSHIP("Starship (reabastecida)", 7.2, 100_000, "Depende de reabastecimento em orbita terrestre", "#e5e7eb"),
        ATLAS_V_551("Atlas V 551 + Star 48", 9.2, 8_000, "Levou a New Horizons: lancamento mais rapido ja feito", "#dbeafe");

        private final String label;
        private final double deltaVKmS;
        private final int payloadKg;
        private final String note;
        private final ColorRGBA color;

        RocketModel(String label, double deltaVKmS, int payloadKg, String note, String colorHex) {
            this.label = label;
            this.deltaVKmS = deltaVKmS;
            this.payloadKg = payloadKg;
            this.note = note;
            this.color = ColorRGBA.fromHex(colorHex);
        }

        private static String[] labels() {
            String[] labels = new String[values().length];
            for (int i = 0; i < values().length; i++) labels[i] = values()[i].label;
            return labels;
        }

        private static RocketModel byIndex(double value) {
            int index = Math.max(0, Math.min(values().length - 1, (int)Math.round(value)));
            return values()[index];
        }
    }

    private enum CameraFocus {
        SYSTEM("Sistema inteiro"),
        PLANET("Planeta observado"),
        ROCKET("Foguete");

        private final String label;

        CameraFocus(String label) {
            this.label = label;
        }

        private static String[] labels() {
            String[] labels = new String[values().length];
            for (int i = 0; i < values().length; i++) labels[i] = values()[i].label;
            return labels;
        }

        private static CameraFocus byIndex(double value) {
            int index = Math.max(0, Math.min(values().length - 1, (int)Math.round(value)));
            return values()[index];
        }
    }

    private enum Situation {
        STABLE("Orbita base"),
        STRONGER_SUN("Sol mais massivo"),
        WEAKER_SUN("Sol menos massivo"),
        ACCELERATE_PLANET("Acelerar planeta"),
        BRAKE_PLANET("Frear planeta"),
        RADIAL_IMPACT("Impacto radial");

        private final String label;

        Situation(String label) {
            this.label = label;
        }

        private double solarMassFactor(double strength) {
            return switch (this) {
                case STRONGER_SUN -> 1.0 + 0.85 * strength;
                case WEAKER_SUN -> 1.0 - 0.65 * strength;
                default -> 1.0;
            };
        }

        private double tangentialFactor(double strength) {
            return switch (this) {
                case ACCELERATE_PLANET -> 1.0 + 0.58 * strength;
                case BRAKE_PLANET -> 1.0 - 0.52 * strength;
                default -> 1.0;
            };
        }

        private double radialKickAuDay(double strength) {
            return this == RADIAL_IMPACT ? 0.018 * strength : 0.0;
        }

        private static String[] labels() {
            String[] labels = new String[values().length];
            for (int i = 0; i < values().length; i++) labels[i] = values()[i].label;
            return labels;
        }

        private static Situation byIndex(double value) {
            int index = Math.max(0, Math.min(values().length - 1, (int)Math.round(value)));
            return values()[index];
        }
    }

    private enum NaturalSatellite {
        MOON(Planet.EARTH, "Lua", 384_400, 27.322, 0.095, 0.089, 0.20, "#cfd2d6"),
        PHOBOS(Planet.MARS, "Phobos", 9_376, 0.319, 0.035, 0.019, 1.18, "#b6a491"),
        DEIMOS(Planet.MARS, "Deimos", 23_463, 1.263, 0.030, 0.031, 3.55, "#998b7a"),

        IO(Planet.JUPITER, "Io", 421_800, 1.769, 0.072, 0.001, 0.35, "#dbc567"),
        EUROPA(Planet.JUPITER, "Europa", 671_100, 3.551, 0.068, 0.008, 1.42, "#c4b399"),
        GANYMEDE(Planet.JUPITER, "Ganymede", 1_070_400, 7.155, 0.088, 0.003, 2.36, "#a99b83"),
        CALLISTO(Planet.JUPITER, "Callisto", 1_882_700, 16.689, 0.082, 0.003, 4.02, "#7e7164"),

        MIMAS(Planet.SATURN, "Mimas", 186_000, 0.942, 0.040, 0.027, 0.45, "#d8d4cb"),
        ENCELADUS(Planet.SATURN, "Enceladus", 238_400, 1.370, 0.046, 0.000, 1.10, "#e6e5dc"),
        TETHYS(Planet.SATURN, "Tethys", 295_000, 1.888, 0.052, 0.019, 1.85, "#c8c2b6"),
        DIONE(Planet.SATURN, "Dione", 377_700, 2.737, 0.054, 0.000, 2.60, "#b8aea3"),
        RHEA(Planet.SATURN, "Rhea", 527_200, 4.518, 0.060, 0.005, 3.20, "#cbc4b8"),
        TITAN(Planet.SATURN, "Titan", 1_221_900, 15.945, 0.098, 0.005, 4.75, "#d49b52"),

        MIRANDA(Planet.URANUS, "Miranda", 129_900, 1.413, 0.040, 0.075, 0.70, "#bbb8b7"),
        ARIEL(Planet.URANUS, "Ariel", 191_000, 2.520, 0.052, 0.001, 1.62, "#d2d0cf"),
        UMBRIEL(Planet.URANUS, "Umbriel", 266_000, 4.144, 0.052, 0.002, 2.58, "#8c8889"),
        TITANIA(Planet.URANUS, "Titania", 436_300, 8.706, 0.064, 0.001, 3.45, "#b9b4ad"),
        OBERON(Planet.URANUS, "Oberon", 583_500, 13.463, 0.062, 0.001, 4.55, "#9f978f"),

        PROTEUS(Planet.NEPTUNE, "Proteus", 117_600, 1.122, 0.042, 0.001, 1.00, "#928e8a"),
        TRITON(Planet.NEPTUNE, "Triton", 354_800, -5.877, 0.074, 2.74, 3.15, "#c5b7a5");

        private final Planet parent;
        private final String label;
        private final double semiMajorAxisKm;
        private final double periodDays;
        private final double renderRadius;
        private final double inclinationRadians;
        private final double initialAngleRadians;
        private final ColorRGBA color;

        NaturalSatellite(Planet parent, String label, double semiMajorAxisKm, double periodDays,
                         double renderRadius, double inclinationRadians, double initialAngleRadians, String colorHex) {
            this.parent = parent;
            this.label = label;
            this.semiMajorAxisKm = semiMajorAxisKm;
            this.periodDays = periodDays;
            this.renderRadius = renderRadius;
            this.inclinationRadians = inclinationRadians;
            this.initialAngleRadians = initialAngleRadians;
            this.color = ColorRGBA.fromHex(colorHex);
        }

        private double renderOrbitRadius() {
            double compressedDistance = Math.log1p(semiMajorAxisKm / 100_000.0) * 0.29;
            return parent.renderRadius * 1.48 + Math.max(0.12, compressedDistance);
        }
    }

    private enum Planet {
        MERCURY("Mercurio", 0.38709927, 87.9691, 0.20563593, deg(7.00497902),
            deg(252.25032350), deg(77.45779628), deg(48.33076593),
            0.0553, 0.383, 3.70, 0.18, "#a9a39a",
            58.6462, deg(0.034), false, "/textures/planets/mercury.jpg"),
        VENUS("Venus", 0.72333566, 224.701, 0.00677672, deg(3.39467605),
            deg(181.97909950), deg(131.60246718), deg(76.67984255),
            0.815, 0.949, 8.87, 0.26, "#d7a567",
            -243.018, deg(177.36), false, "/textures/planets/venus.jpg"),
        EARTH("Terra", 1.00000261, 365.256, 0.01671123, deg(-0.00001531),
            deg(100.46457166), deg(102.93768193), deg(0.0),
            1.000, 1.000, 9.81, 0.27, "#4da6ff",
            0.99726968, deg(23.439), false, "/textures/planets/earth.jpg"),
        MARS("Marte", 1.52371034, 686.980, 0.09339410, deg(1.84969142),
            deg(-4.55343205), deg(-23.94362959), deg(49.55953891),
            0.107, 0.532, 3.71, 0.21, "#d7644c",
            1.02595676, deg(25.19), false, "/textures/planets/mars.jpg"),
        JUPITER("Jupiter", 5.20288700, 4332.589, 0.04838624, deg(1.30439695),
            deg(34.39644051), deg(14.72847983), deg(100.47390909),
            317.83, 11.21, 24.79, 0.62, "#d7b18c",
            0.41354, deg(3.13), false, "/textures/planets/jupiter.jpg"),
        SATURN("Saturno", 9.53667594, 10759.22, 0.05386179, deg(2.48599187),
            deg(49.95424423), deg(92.59887831), deg(113.66242448),
            95.16, 9.45, 10.44, 0.54, "#d2bf83",
            0.44401, deg(26.73), true, "/textures/planets/saturn.jpg"),
        URANUS("Urano", 19.18916464, 30688.5, 0.04725744, deg(0.77263783),
            deg(313.23810451), deg(170.95427630), deg(74.01692503),
            14.54, 4.01, 8.69, 0.39, "#7ad7df",
            -0.71833, deg(97.77), true, "/textures/planets/uranus.jpg"),
        NEPTUNE("Netuno", 30.06992276, 60182.0, 0.00859048, deg(1.77004347),
            deg(-55.12002969), deg(44.96476227), deg(131.78422574),
            17.15, 3.88, 11.15, 0.39, "#4c72e6",
            0.67125, deg(28.32), false, "/textures/planets/neptune.jpg");

        private final String label;
        private final double orbitAu;
        private final double periodDays;
        private final double orbitalEccentricity;
        private final double orbitalInclinationRadians;
        private final double meanLongitudeRadians;
        private final double longitudePerihelionRadians;
        private final double longitudeAscendingNodeRadians;
        private final double massEarth;
        private final double radiusEarth;
        private final double surfaceGravityMs2;
        private final double renderRadius;
        private final ColorRGBA color;
        private final double rotationPeriodDays;
        private final double axialTiltRadians;
        private final boolean hasRings;
        private final String texturePath;

        Planet(String label, double orbitAu, double periodDays, double orbitalEccentricity,
               double orbitalInclinationRadians, double meanLongitudeRadians,
               double longitudePerihelionRadians, double longitudeAscendingNodeRadians,
               double massEarth, double radiusEarth, double surfaceGravityMs2, double renderRadius,
               String colorHex,
               double rotationPeriodDays, double axialTiltRadians, boolean hasRings, String texturePath) {
            this.label = label;
            this.orbitAu = orbitAu;
            this.periodDays = periodDays;
            this.orbitalEccentricity = orbitalEccentricity;
            this.orbitalInclinationRadians = orbitalInclinationRadians;
            this.meanLongitudeRadians = meanLongitudeRadians;
            this.longitudePerihelionRadians = longitudePerihelionRadians;
            this.longitudeAscendingNodeRadians = longitudeAscendingNodeRadians;
            this.massEarth = massEarth;
            this.radiusEarth = radiusEarth;
            this.surfaceGravityMs2 = surfaceGravityMs2;
            this.renderRadius = renderRadius;
            this.color = ColorRGBA.fromHex(colorHex);
            this.rotationPeriodDays = rotationPeriodDays;
            this.axialTiltRadians = axialTiltRadians;
            this.hasRings = hasRings;
            this.texturePath = texturePath;
        }

        private double trailCadenceDays() {
            return Math.max(0.5, Math.min(120.0, periodDays / 72.0));
        }

        private static String[] labels() {
            String[] labels = new String[values().length];
            for (int i = 0; i < values().length; i++) labels[i] = values()[i].label;
            return labels;
        }

        private static Planet byIndex(double value) {
            int index = Math.max(0, Math.min(values().length - 1, (int)Math.round(value)));
            return values()[index];
        }
    }
}
