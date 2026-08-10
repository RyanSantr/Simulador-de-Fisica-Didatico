package engine.validation;

import engine.physics.MaterialState;
import engine.physics.MediumState;
import engine.physics.PhysicsWorld;
import engine.renderer.Camera;
import engine.simulation.LabMeasurement;
import engine.simulation.SimulationContext;
import engine.simulation.modules.CollisionModule;
import engine.simulation.modules.FreeFallModule;
import engine.simulation.modules.InclinedPlaneModule;
import engine.simulation.modules.PendulumModule;
import engine.simulation.modules.ProjectileModule;
import engine.simulation.modules.SolarSystemModule;
import engine.simulation.parameters.Parameter;
import engine.simulation.modules.SpringModule;
import engine.simulation.modules.TugOfWarModule;

import java.text.Normalizer;

public final class PhysicsValidation {

    private static final double DT = 1.0 / 240.0;

    private PhysicsValidation() {}

    public static void main(String[] args) {
        validateFreeFall();
        validateProjectileVacuumAndDrag();
        validateProjectileGlassImpact();
        validateCollisionMaterials();
        validateInclinedPlane();
        validatePendulum();
        validateSpring();
        validateSolarSystem();
        validateRocketMission();
        validateTugOfWar();
        validateParameterLimits();
        validateExtremeValues();
        validateMaterials();
        validateMedium();
        validateCollisionFriction();
        System.out.println("OK: validacoes fisicas passaram.");
    }

    private static void validateFreeFall() {
        SimulationContext context = context();
        FreeFallModule module = new FreeFallModule();
        module.getParameters().setValue("gravity", 9.81);
        module.getParameters().setValue("initial_height", 8.0);
        module.getParameters().setValue("initial_velocity", 0.0);
        module.onActivate(context);
        module.launch();   // o objeto so cai depois do comando de soltar

        double expectedTime = Math.sqrt(2.0 * 8.0 / 9.81);
        for (int i = 0; i < 600 && module.getDropTime() < expectedTime; i++) {
            module.onUpdate(DT);
        }

        assertClose("queda livre: tempo de impacto", expectedTime, module.getDropTime(), 1e-9);
        assertClose("queda livre: tempo teorico", expectedTime, module.getTheoreticalTime(), 1e-9);
        assertTrue("queda livre: velocidade escalar nao pode aparecer negativa",
            !module.telemetry().contains("Velocidade: -"));

        SimulationContext noGravityContext = context();
        noGravityContext.getPhysics().setGravityEnabled(false);
        FreeFallModule noGravity = new FreeFallModule();
        noGravity.getParameters().setValue("initial_height", 8.0);
        noGravity.getParameters().setValue("initial_velocity", 0.0);
        noGravity.onActivate(noGravityContext);
        noGravity.launch();
        for (int i = 0; i < 240; i++) noGravity.onUpdate(DT);
        assertClose("queda livre: altura fica constante sem gravidade",
            8.0, noGravity.sampleLabMeasurement().seriesAValue(), 1e-9);

        FreeFallModule moon = new FreeFallModule();
        moon.getParameters().setValue("gravity", 1.62);
        moon.getParameters().setValue("initial_height", 2.0);
        moon.getParameters().setValue("initial_velocity", 0.0);
        moon.onActivate(context());
        moon.launch();
        assertClose("queda livre: exemplo Lua 2 m",
            Math.sqrt(2.0 * 2.0 / 1.62), moon.getTheoreticalTime(), 1e-9);
    }

    private static void validateProjectileVacuumAndDrag() {
        ProjectileModule vacuum = projectile(MediumState.VACUUM);
        vacuum.launch();
        for (int i = 0; i < 120; i++) vacuum.onUpdate(DT);

        double v0 = 20.0;
        double angle = Math.toRadians(45.0);
        double expectedRange = v0 * v0 * Math.sin(2.0 * angle) / 9.81;
        assertClose("projetil: alcance teorico no vacuo",
            expectedRange, vacuum.getTheoreticalRange(), 1e-9);

        double vx = v0 * Math.cos(angle);
        double vy = v0 * Math.sin(angle) - 9.81 * 0.5;
        double expectedSpeed = Math.sqrt(vx * vx + vy * vy);
        double vacuumSpeed = vacuum.sampleLabMeasurement().seriesBValue();
        assertClose("projetil: velocidade no vacuo apos 0,5 s",
            expectedSpeed, vacuumSpeed, 0.08);

        ProjectileModule water = projectile(MediumState.WATER);
        water.launch();
        for (int i = 0; i < 120; i++) water.onUpdate(DT);
        double waterSpeed = water.sampleLabMeasurement().seriesBValue();
        assertTrue("projetil: agua deve reduzir velocidade mais que vacuo",
            waterSpeed < vacuumSpeed * 0.82);
        assertFinite("projetil: telemetria", water.sampleLabMeasurement());
    }

    private static void validateProjectileGlassImpact() {
        SimulationContext context = context();
        ProjectileModule module = new ProjectileModule();
        module.getParameters().setValue("projectile_material", MaterialState.STEEL.ordinal());
        module.getParameters().setValue("target_material", MaterialState.GLASS.ordinal());
        module.getParameters().setValue("medium", MediumState.VACUUM.ordinal());
        module.getParameters().setValue("speed", 60.0);
        module.getParameters().setValue("angle", 0.0);
        module.getParameters().setValue("gravity", 9.81);
        module.getParameters().setValue("n_targets", 1.0);
        module.onActivate(context);
        module.launch();
        for (int i = 0; i < 1200 && module.getTargetsHit() == 0; i++) {
            module.onUpdate(DT);
        }

        assertTrue("projetil: bola de aco deve atingir alvo de vidro", module.getTargetsHit() > 0);
        assertTrue("projetil: vidro deve reagir como material fragil",
            mentionsGlassBreak(module.telemetry()));
    }


    private static ProjectileModule projectile(MediumState medium) {
        SimulationContext context = context();
        ProjectileModule module = new ProjectileModule();
        module.getParameters().setValue("projectile_material", MaterialState.STONE.ordinal());
        module.getParameters().setValue("target_material", MaterialState.STEEL.ordinal());
        module.getParameters().setValue("medium", medium.ordinal());
        module.getParameters().setValue("speed", 20.0);
        module.getParameters().setValue("angle", 45.0);
        module.getParameters().setValue("gravity", 9.81);
        module.getParameters().setValue("n_targets", 1.0);
        module.onActivate(context);
        return module;
    }

    private static void validateCollisionMaterials() {
        CollisionModule module = new CollisionModule();
        module.getParameters().setValue("material_a", MaterialState.STONE.ordinal());
        module.getParameters().setValue("material_b", MaterialState.GELATIN.ordinal());
        module.getParameters().setValue("velocity_a", 6.0);
        module.onActivate(context());
        module.startCollision();
        for (int i = 0; i < 1000 && !module.isCollisionOccurred(); i++) {
            module.onUpdate(DT);
        }

        assertTrue("colisao: impacto deve acontecer", module.isCollisionOccurred());
        assertTrue("colisao: energia nao deve aumentar",
            module.getKineticEnergyAfter() <= module.getKineticEnergyBefore() * 1.0001);
        assertTrue("colisao: restituicao medida deve ser fisica",
            module.getMeasuredRestitution() >= 0.0 && module.getMeasuredRestitution() <= 1.0);

        CollisionModule glass = new CollisionModule();
        glass.getParameters().setValue("material_a", MaterialState.STEEL.ordinal());
        glass.getParameters().setValue("material_b", MaterialState.GLASS.ordinal());
        glass.getParameters().setValue("velocity_a", 25.0);
        glass.onActivate(context());
        glass.startCollision();
        for (int i = 0; i < 1000 && !glass.isCollisionOccurred(); i++) {
            glass.onUpdate(DT);
        }
        assertTrue("colisao: aco rapido deve quebrar vidro",
            mentionsGlassBreak(glass.telemetry()));
    }

    private static void validateInclinedPlane() {
        InclinedPlaneModule module = new InclinedPlaneModule();
        module.getParameters().setValue("angle", 30.0);
        module.getParameters().setValue("friction", 0.20);
        module.getParameters().setValue("gravity", 9.81);
        module.onActivate(context());
        module.launch();
        for (int i = 0; i < 240; i++) module.onUpdate(DT);

        double expectedA = 9.81 * (Math.sin(Math.toRadians(30.0)) - 0.20 * Math.cos(Math.toRadians(30.0)));
        assertClose("plano inclinado: velocidade apos 1 s",
            expectedA, module.sampleLabMeasurement().seriesBValue(), 0.04);
    }

    private static void validatePendulum() {
        PendulumModule module = new PendulumModule();
        module.getParameters().setValue("length", 4.2);
        module.getParameters().setValue("amplitude", 8.0);
        module.getParameters().setValue("gravity", 9.81);
        module.getParameters().setValue("damping", 0.0);
        module.onActivate(context());
        module.launch();

        double quarterPeriod = 0.5 * Math.PI * Math.sqrt(4.2 / 9.81);
        int steps = (int)Math.round(quarterPeriod / DT);
        for (int i = 0; i < steps; i++) module.onUpdate(DT);

        double angleDegrees = module.sampleLabMeasurement().seriesAValue();
        assertTrue("pendulo: deve cruzar perto do ponto central no quarto de periodo",
            Math.abs(angleDegrees) < 1.3);
    }

    private static void validateSpring() {
        SpringModule module = new SpringModule();
        module.getParameters().setValue("mass", 1.2);
        module.getParameters().setValue("stiffness", 18.0);
        module.getParameters().setValue("amplitude", 2.0);
        module.getParameters().setValue("damping", 0.0);
        module.onActivate(context());
        module.launch();
        module.onUpdate(DT);

        double expectedA = -18.0 * 2.0 / 1.2;
        assertClose("mola: aceleracao inicial por Hooke",
            expectedA, parseAcceleration(module.telemetry()), 0.20);
    }

    private static void validateSolarSystem() {
        SolarSystemModule module = new SolarSystemModule();
        module.onActivate(context());
        for (int i = 0; i < 20; i++) module.onUpdate(1.0 / 60.0);
        String telemetry = module.telemetry();
        assertTrue("sistema solar: telemetria deve carregar Terra", mentions(telemetry, "Terra"));
        assertTrue("sistema solar: telemetria nao deve conter NaN", !telemetry.contains("NaN"));
        assertTrue("sistema solar: telemetria nao deve conter Infinity", !telemetry.contains("Infinity"));
        double earthSpeed = parseTelemetryNumber(telemetry, "Velocidade orbital:", "km/s");
        assertTrue("sistema solar: velocidade orbital da Terra deve ficar perto de 29,8 km/s",
            earthSpeed > 28.0 && earthSpeed < 31.5);
    }

    /**
     * Missao de foguete, agora hospedada dentro do modulo Sistema Solar.
     * Verifica os numeros da transferencia de Hohmann Terra-Marte e a regra de
     * delta-v insuficiente para destinos fora do alcance do lancador.
     */
    private static void validateRocketMission() {
        SolarSystemModule module = new SolarSystemModule();
        module.getParameters().setValue("rocket", 3);          // Falcon Heavy
        module.getParameters().setValue("origin", 2);          // Terra
        module.getParameters().setValue("destination", 3);     // Marte
        module.getParameters().setValue("days_per_second", 240.0);
        module.onActivate(context());

        // Antes do comando a missao mostra o custo da rota: e nesse estado que
        // os numeros da transferencia ficam visiveis na telemetria.
        String initial = module.telemetry();
        assertTrue("missao: telemetria deve trazer o bloco da missao",
            mentions(initial, "missao de foguete"));
        assertTrue("missao: telemetria deve citar o foguete escolhido",
            mentions(initial, "Falcon Heavy"));
        assertTrue("missao: nave deve aguardar o comando de envio",
            mentions(initial, "aguardando comando"));
        assertTrue("missao: telemetria nao deve conter NaN", !initial.contains("NaN"));

        // t = pi * sqrt(a^3 / mu) com a = (1.000 + 1.524)/2 AU  =>  ~259 dias
        double transferDays = parseTelemetryNumber(initial, "Tempo de voo previsto:", "dias");
        assertClose("missao: tempo de Hohmann Terra-Marte", 259.0, transferDays, 9.0);

        // Delta-v heliocentrico da injecao trans-Marte: ~2,9 km/s
        double departure = parseTelemetryNumber(initial, "Delta-v exigido na partida:", "km/s");
        assertClose("missao: delta-v de partida Terra-Marte", 2.9, departure, 0.25);

        module.launch();   // a missao so parte sob comando do botao

        // Janela (ate ~780 d de periodo sinodico) + voo (~259 d), com folga.
        boolean reached = false;
        for (int i = 0; i < 4000 && !reached; i++) {
            module.onUpdate(1.0 / 60.0);
            String state = module.telemetry();
            reached = mentions(state, "CHEGOU ao destino") || mentions(state, "SOBREVOO do destino");
        }
        assertTrue("missao: a nave deve alcancar Marte pela transferencia de Hohmann", reached);

        // Netuno exige ~11,7 km/s, acima dos 4,8 km/s do Falcon Heavy.
        SolarSystemModule outOfRange = new SolarSystemModule();
        outOfRange.getParameters().setValue("rocket", 3);       // Falcon Heavy
        outOfRange.getParameters().setValue("origin", 2);       // Terra
        outOfRange.getParameters().setValue("destination", 7);  // Netuno
        outOfRange.onActivate(context());
        assertTrue("missao: destino fora de alcance deve acusar delta-v insuficiente",
            mentions(outOfRange.telemetry(), "delta-v insuficiente"));

        // Rotas que a mira de Lambert precisa fechar, incluindo o retorno e um
        // destino externo (onde o residuo fora do plano e maior).
        assertRouteArrives("Terra -> Venus", 2, 1, 3, 90, 190);
        assertRouteArrives("Marte -> Terra", 3, 2, 3, 180, 300);
        assertRouteArrives("Terra -> Jupiter", 2, 4, 6, 700, 1100);
    }

    /**
     * Executa uma rota completa e exige chegada ao destino dentro de uma faixa
     * plausivel de tempo de voo.
     */
    private static void assertRouteArrives(String label, int origin, int destination,
                                           int rocket, double minFlightDays, double maxFlightDays) {
        SolarSystemModule module = new SolarSystemModule();
        module.getParameters().setValue("rocket", rocket);
        module.getParameters().setValue("origin", origin);
        module.getParameters().setValue("destination", destination);
        module.getParameters().setValue("days_per_second", 400.0);
        module.onActivate(context());
        module.launch();

        String telemetry = "";
        boolean arrived = false;
        for (int i = 0; i < 9000 && !arrived; i++) {
            module.onUpdate(1.0 / 60.0);
            telemetry = module.telemetry();
            arrived = mentions(telemetry, "CHEGOU ao destino");
        }
        assertTrue("missao " + label + ": a nave deve alcancar o destino", arrived);

        double flightDays = parseTelemetryNumber(telemetry, "Voo real:", "dias");
        assertTrue(String.format("missao %s: tempo de voo %.0f d fora da faixa [%.0f, %.0f]",
                label, flightDays, minFlightDays, maxFlightDays),
            flightDays >= minFlightDays && flightDays <= maxFlightDays);
    }

    private static void validateTugOfWar() {
        SimulationContext context = context();
        TugOfWarModule module = new TugOfWarModule();
        module.onActivate(context);
        module.setLeftName("Ana");
        module.setRightName("Leo");

        for (int i = 0; i < 1200 && module.getWinner().isBlank(); i++) {
            if (i % 3 == 0) module.pressLeft();
            module.holdLeft(DT);
            module.onUpdate(DT);
            context.advanceSimTime(DT);
        }

        assertTrue("cabo de guerra: jogador A deve vencer com entrada dominante",
            module.getWinner().contains("Jogador A"));
        assertTrue("cabo de guerra: ranking deve registrar partida",
            !module.getRanking().isEmpty());
    }

    /**
     * Entrada livre: valores fora da faixa do slider sao aceitos, valores fora
     * dos limites fisicos sao recusados e ajustados para a fronteira.
     */
    private static void validateParameterLimits() {
        FreeFallModule module = new FreeFallModule();
        Parameter gravity = module.getParameters().get("gravity");
        Parameter height = module.getParameters().get("initial_height");

        // Dentro da faixa usual: sem aviso.
        assertTrue("parametro: valor usual nao deve gerar aviso",
            gravity.validate(9.81).severity() == Parameter.Severity.OK);

        // Fora da faixa do slider (0..30) mas fisicamente valido: aceito com aviso.
        Parameter.Feedback solarGravity = gravity.validate(274.0);
        assertTrue("parametro: gravidade do Sol deve ser aceita",
            solarGravity.severity() == Parameter.Severity.WARNING);
        assertClose("parametro: valor fora da faixa deve ser preservado",
            274.0, solarGravity.value(), 1e-9);
        assertTrue("parametro: aviso deve citar referencia real",
            mentions(solarGravity.message(), "Terra 9,81"));

        // Abaixo do limite fisico: recusado e ajustado.
        Parameter.Feedback negative = gravity.validate(-5.0);
        assertTrue("parametro: gravidade negativa deve ser recusada",
            negative.severity() == Parameter.Severity.REJECTED);
        assertClose("parametro: valor recusado vai para o limite", 0.0, negative.value(), 1e-9);

        // Altura nula nao existe como experimento de queda.
        assertTrue("parametro: altura zero deve ser recusada",
            height.validate(0.0).severity() == Parameter.Severity.REJECTED);

        // Entrada nao numerica nunca chega como NaN ao modelo.
        Parameter.Feedback nan = height.validate(Double.NaN);
        assertTrue("parametro: NaN deve ser recusado",
            nan.severity() == Parameter.Severity.REJECTED);
        assertTrue("parametro: NaN nao pode virar valor", Double.isFinite(nan.value()));

        // setValue direto (presets, cenarios salvos) tambem respeita os limites.
        gravity.setValue(Double.POSITIVE_INFINITY);
        assertTrue("parametro: setValue deve conter infinito", Double.isFinite(gravity.getValue()));
        gravity.setValue(-1e9);
        assertClose("parametro: setValue deve conter valor negativo", 0.0, gravity.getValue(), 1e-9);
    }

    /**
     * Valores extremos porem validos nao podem produzir NaN, infinito nem
     * travar a simulacao — o usuario tem liberdade para exagerar.
     */
    private static void validateExtremeValues() {
        // Gravidade solar com altura de montanha.
        FreeFallModule fall = new FreeFallModule();
        fall.getParameters().setValue("gravity", 274.0);
        fall.getParameters().setValue("initial_height", 8849.0);
        fall.onActivate(context());
        fall.launch();
        for (int i = 0; i < 2000; i++) fall.onUpdate(DT);
        assertFinite("queda extrema", fall.sampleLabMeasurement());
        assertTrue("queda extrema: telemetria sem NaN", !fall.telemetry().contains("NaN"));

        // Projetil hipersonico num meio denso.
        ProjectileModule projectile = new ProjectileModule();
        projectile.getParameters().setValue("speed", 5000.0);
        projectile.getParameters().setValue("angle", 80.0);
        projectile.getParameters().setValue("medium", MediumState.WATER.ordinal());
        projectile.onActivate(context());
        projectile.launch();
        for (int i = 0; i < 2000; i++) projectile.onUpdate(DT);
        assertFinite("projetil extremo", projectile.sampleLabMeasurement());
        assertTrue("projetil extremo: telemetria sem NaN", !projectile.telemetry().contains("NaN"));

        // Mola muito rigida com massa minuscula: caso mais duro para o integrador.
        SpringModule spring = new SpringModule();
        spring.getParameters().setValue("stiffness", 1e6);
        spring.getParameters().setValue("mass", 1e-3);
        spring.getParameters().setValue("amplitude", 500.0);
        spring.onActivate(context());
        spring.launch();
        for (int i = 0; i < 1000; i++) spring.onUpdate(DT);
        assertFinite("mola extrema", spring.sampleLabMeasurement());

        // Pendulo dando a volta completa, sem gravidade e com fio quilometrico.
        PendulumModule pendulum = new PendulumModule();
        pendulum.getParameters().setValue("amplitude", 180.0);
        pendulum.getParameters().setValue("length", 5000.0);
        pendulum.getParameters().setValue("gravity", 0.0);
        pendulum.onActivate(context());
        pendulum.launch();
        for (int i = 0; i < 1000; i++) pendulum.onUpdate(DT);
        assertFinite("pendulo extremo", pendulum.sampleLabMeasurement());

        // Caso mais duro do pendulo: gravidade solar com fio de 1 mm.
        PendulumModule stiffPendulum = new PendulumModule();
        stiffPendulum.getParameters().setValue("length", 0.001);
        stiffPendulum.getParameters().setValue("gravity", 274.0);
        stiffPendulum.getParameters().setValue("amplitude", 90.0);
        stiffPendulum.onActivate(context());
        stiffPendulum.launch();
        for (int i = 0; i < 1000; i++) stiffPendulum.onUpdate(DT);
        assertFinite("pendulo rigido", stiffPendulum.sampleLabMeasurement());
        assertTrue("pendulo rigido: telemetria sem NaN", !stiffPendulum.telemetry().contains("NaN"));

        // Rampa vertical sem atrito: vira queda livre.
        InclinedPlaneModule ramp = new InclinedPlaneModule();
        ramp.getParameters().setValue("angle", 90.0);
        ramp.getParameters().setValue("friction", 0.0);
        ramp.getParameters().setValue("gravity", 274.0);
        ramp.onActivate(context());
        ramp.launch();
        for (int i = 0; i < 1000; i++) ramp.onUpdate(DT);
        assertFinite("rampa extrema", ramp.sampleLabMeasurement());

        // Colisao a velocidade de bala entre corpos de tamanhos muito diferentes.
        CollisionModule collision = new CollisionModule();
        collision.getParameters().setValue("velocity_a", 5000.0);
        collision.getParameters().setValue("radius_a", 0.001);
        collision.getParameters().setValue("radius_b", 50.0);
        collision.onActivate(context());
        collision.startCollision();
        for (int i = 0; i < 2000; i++) collision.onUpdate(DT);
        assertFinite("colisao extrema", collision.sampleLabMeasurement());
        assertTrue("colisao extrema: energia nao pode crescer",
            collision.getKineticEnergyAfter() <= collision.getKineticEnergyBefore() * 1.0001);

        // Sistema solar acelerado alem da saturacao do passo por quadro.
        SolarSystemModule solar = new SolarSystemModule();
        solar.getParameters().setValue("days_per_second", 50_000.0);
        solar.onActivate(context());
        solar.launch();
        for (int i = 0; i < 600; i++) solar.onUpdate(DT);
        assertTrue("sistema solar acelerado: telemetria sem NaN",
            !solar.telemetry().contains("NaN"));
        assertTrue("sistema solar acelerado: telemetria sem infinito",
            !solar.telemetry().contains("Infinity"));
    }

    /**
     * O modelo de materiais tem que reproduzir comportamento real conhecido.
     *
     * <p>A referencia principal e a altura de quicada numa queda de 1 m sobre
     * placa de aco, que qualquer pessoa pode conferir na bancada: bola de
     * borracha volta perto de 70 cm, esfera de aco perto de 36 cm, gelatina nao
     * volta.
     */
    private static void validateMaterials() {
        double dropSpeed = Math.sqrt(2 * 9.81 * 1.0);   // queda de 1 m: 4,43 m/s

        assertClose("material: borracha sobre aco deve voltar a ~72 cm",
            0.72, bounceHeight(MaterialState.RUBBER, MaterialState.STEEL, dropSpeed), 0.10);
        assertClose("material: aco sobre aco deve voltar a ~36 cm",
            0.36, bounceHeight(MaterialState.STEEL, MaterialState.STEEL, dropSpeed), 0.08);
        assertClose("material: vidro sobre aco deve voltar a ~41 cm",
            0.41, bounceHeight(MaterialState.GLASS, MaterialState.STEEL, dropSpeed), 0.08);
        assertTrue("material: gelatina praticamente nao quica",
            bounceHeight(MaterialState.GELATIN, MaterialState.STEEL, dropSpeed) < 0.02);

        // A restituicao e do par, nao de um corpo isolado: quem deforma manda.
        // Numa esfera de gelatina contra aco, a gelatina domina o resultado.
        assertClose("material: par gelatina-aco fica junto do valor da gelatina",
            MaterialState.GELATIN.restitution,
            MaterialState.GELATIN.restitutionWith(MaterialState.STEEL, 1.0), 0.02);

        // Impacto elasto-plastico: acima da velocidade de ensaio a restituicao
        // cai com v^(-1/4).
        double slow = MaterialState.STEEL.restitutionWith(MaterialState.STEEL, 5.0);
        double fast = MaterialState.STEEL.restitutionWith(MaterialState.STEEL, 80.0);
        assertTrue("material: restituicao deve cair com a velocidade", fast < slow * 0.75);

        // Modulo de contato de Hertz: o mais flexivel domina o par.
        assertTrue("material: espuma deve amolecer o contato com aco",
            MaterialState.STEEL.effectiveModulusWith(MaterialState.FOAM)
                < MaterialState.STEEL.effectiveModulusWith(MaterialState.STEEL) / 1000.0);

        // Modo de falha vindo do alongamento na ruptura.
        assertTrue("material: vidro e pedra sao frageis",
            MaterialState.GLASS.isBrittle() && MaterialState.STONE.isBrittle());
        assertTrue("material: aco e borracha nao sao frageis",
            !MaterialState.STEEL.isBrittle() && !MaterialState.RUBBER.isBrittle());
        assertTrue("material: fragil deve romper com menos dano que ductil",
            MaterialState.GLASS.breakThreshold() < MaterialState.STEEL.breakThreshold());

        // Resiliencia sigma^2/(2 E rho): explica quem quica e quem estilhaca.
        assertTrue("material: borracha guarda mais energia elastica por kg que o aco",
            MaterialState.RUBBER.fractureJPerKg > MaterialState.STEEL.fractureJPerKg);
        assertTrue("material: pedra guarda menos energia elastica que o vidro",
            MaterialState.STONE.fractureJPerKg < MaterialState.GLASS.fractureJPerKg);

        // Nenhuma propriedade pode sair da faixa fisica.
        for (MaterialState m : MaterialState.values()) {
            assertTrue("material " + m.label + ": restituicao em [0,1]",
                m.restitution >= 0 && m.restitution <= 1);
            assertTrue("material " + m.label + ": densidade positiva", m.densityKgM3 > 0);
            assertTrue("material " + m.label + ": modulo de Young positivo", m.youngModulusPa > 0);
            assertTrue("material " + m.label + ": Poisson em (0, 0.5]",
                m.poissonRatio > 0 && m.poissonRatio <= 0.5);
            assertTrue("material " + m.label + ": dureza normalizada em [0,1]",
                m.hardness >= 0 && m.hardness <= 1);
            assertTrue("material " + m.label + ": resiliencia finita e positiva",
                Double.isFinite(m.fractureJPerKg) && m.fractureJPerKg > 0);
        }
    }

    /**
     * Empuxo e arrasto do meio.
     *
     * <p>O empuxo tem que depender da densidade <b>do corpo</b> (Arquimedes):
     * madeira flutua na agua, aco afunda. Um fator fixo por meio, como havia
     * antes, fazia os dois descerem igual.
     */
    private static void validateMedium() {
        // Arquimedes: fator = 1 - rho_meio/rho_corpo
        assertTrue("meio: madeira deve flutuar na agua",
            MediumState.WATER.floats(MaterialState.WOOD.densityKgM3));
        assertTrue("meio: espuma deve flutuar na agua",
            MediumState.WATER.floats(MaterialState.FOAM.densityKgM3));
        assertTrue("meio: aco deve afundar na agua",
            !MediumState.WATER.floats(MaterialState.STEEL.densityKgM3));
        assertTrue("meio: corpo que flutua tem fator de empuxo negativo",
            MediumState.WATER.buoyancyFactorFor(MaterialState.WOOD.densityKgM3) < 0);

        // Aco na agua: 1 - 998/7850 = 0,873
        assertClose("meio: aco na agua desce com 87% do peso",
            0.873, MediumState.WATER.buoyancyFactorFor(MaterialState.STEEL.densityKgM3), 0.01);
        assertClose("meio: no vacuo o peso e integral",
            1.0, MediumState.VACUUM.buoyancyFactorFor(1000), 1e-9);

        // Regimes de arrasto da esfera pelo numero de Reynolds.
        assertTrue("meio: regime de Stokes deve dar Cd alto",
            MediumState.WATER.dragCoefficient(0.1) > 100);
        assertClose("meio: regime de Newton deve dar Cd perto de 0,44",
            0.44, MediumState.AIR.dragCoefficient(1e4), 0.01);
        assertTrue("meio: crise do arrasto deve derrubar o Cd",
            MediumState.AIR.dragCoefficient(5e5) < MediumState.AIR.dragCoefficient(1e5));
        assertTrue("meio: arrasto deve disparar perto de Mach 1",
            MediumState.AIR.compressibilityFactor(400) > 1.5);
        assertClose("meio: abaixo de Mach 0,8 nao ha correcao",
            1.0, MediumState.AIR.compressibilityFactor(200), 1e-9);
        assertTrue("meio: vacuo nao pode ter arrasto",
            MediumState.VACUUM.dragForce(100, 0.22) == 0);
        assertTrue("meio: agua deve frear muito mais que ar",
            MediumState.WATER.dragForce(10, 0.22) > MediumState.AIR.dragForce(10, 0.22) * 100);
    }

    /**
     * Atrito de Coulomb nas colisoes oblíquas.
     *
     * <p>O coeficiente de atrito era declarado nos materiais mas nunca entrava
     * na colisao: so o impulso normal era aplicado. Numa batida de raspao e
     * justamente o atrito que separa borracha de vidro.
     */
    private static void validateCollisionFriction() {
        double grippy = obliqueDeflection(MaterialState.RUBBER, MaterialState.RUBBER);
        double slippery = obliqueDeflection(MaterialState.GLASS, MaterialState.GLASS);

        assertTrue("atrito: borracha deve desviar mais que vidro numa colisao de raspao",
            grippy > slippery * 1.05);
        assertTrue("atrito: colisao de raspao deve gerar desvio lateral", grippy > 1e-4);

        // O atrito so pode retirar energia, nunca acrescentar.
        for (MaterialState m : MaterialState.values()) {
            CollisionModule module = new CollisionModule();
            module.getParameters().setValue("material_a", m.ordinal());
            module.getParameters().setValue("material_b", m.ordinal());
            module.getParameters().setValue("velocity_a", 12.0);
            module.getParameters().setValue("offset_z", 0.35);
            module.onActivate(context());
            module.startCollision();
            for (int i = 0; i < 900 && !module.isCollisionOccurred(); i++) module.onUpdate(DT);
            assertTrue("atrito " + m.label + ": energia nao pode crescer na colisao oblíqua",
                module.getKineticEnergyAfter() <= module.getKineticEnergyBefore() * 1.0001);
        }
    }

    /** Velocidade lateral adquirida numa colisao de raspao (offset lateral). */
    private static double obliqueDeflection(MaterialState a, MaterialState b) {
        CollisionModule module = new CollisionModule();
        module.getParameters().setValue("material_a", a.ordinal());
        module.getParameters().setValue("material_b", b.ordinal());
        module.getParameters().setValue("velocity_a", 12.0);
        module.getParameters().setValue("offset_z", 0.35);
        module.onActivate(context());
        module.startCollision();
        for (int i = 0; i < 900 && !module.isCollisionOccurred(); i++) module.onUpdate(DT);
        return Math.abs(module.sampleLabMeasurement().seriesBValue());
    }

    /** Altura de retorno, em metros, de uma queda de 1 m: h = e^2 * h0. */
    private static double bounceHeight(MaterialState falling, MaterialState floor, double impactSpeed) {
        double e = falling.restitutionWith(floor, impactSpeed);
        return e * e;
    }

    private static SimulationContext context() {
        return new SimulationContext(new PhysicsWorld(), new Camera());
    }

    private static void assertFinite(String label, LabMeasurement measurement) {
        assertTrue(label + " series A finita", Double.isFinite(measurement.seriesAValue()));
        assertTrue(label + " series B finita", Double.isFinite(measurement.seriesBValue()));
    }

    private static double parseAcceleration(String telemetry) {
        return parseTelemetryNumber(telemetry, "Aceleracao:", "m/s2");
    }

    private static double parseTelemetryNumber(String telemetry, String marker, String unit) {
        // Busca tolerante a acentos e maiusculas/minusculas para nao quebrar com pequenas
        // mudancas de texto/idioma na telemetria da UI.
        String t = normalize(telemetry);
        String m = normalize(marker);
        String u = normalize(unit);
        int start = t.indexOf(m);
        if (start < 0) throw new AssertionError("Campo nao encontrado: " + marker + " em " + telemetry);
        start += m.length();
        int end = t.indexOf(u, start);
        if (end < 0) throw new AssertionError("Unidade nao encontrada: " + unit + " em " + telemetry);
        return Double.parseDouble(t.substring(start, end).replace(',', '.').trim());
    }

    /** Reconhece a quebra de um alvo de vidro de forma resiliente a variacoes de texto. */
    private static boolean mentionsGlassBreak(String telemetry) {
        String t = normalize(telemetry);
        return t.contains("vidro")
            && (t.contains("quebrad") || t.contains("estilhac") || t.contains("frag"));
    }

    /** Verifica presenca de um termo ignorando acentos e caixa. */
    private static boolean mentions(String telemetry, String term) {
        return normalize(telemetry).contains(normalize(term));
    }

    /** Minusculas + remocao de acentos (NFD) para comparacoes textuais estaveis. */
    private static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static void assertClose(String label, double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + " esperado=" + expected + " obtido=" + actual
                + " tolerancia=" + tolerance);
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }
}
