package engine.simulation.modules;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.physics.MaterialState;
import engine.physics.RigidBody;
import engine.scene.SceneObject;
import engine.simulation.LabMeasurement;
import engine.simulation.ModuleSceneBuilder;
import engine.simulation.SimulationModule;
import engine.simulation.challenges.Challenge;
import engine.simulation.challenges.ChallengeResult;
import engine.simulation.parameters.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Modulo de colisoes com estados materiais estabelecidos.
 *
 * Cada corpo tem material, raio, massa calculada por densidade e propriedades
 * de contato: restitucao, atrito, amortecimento e dureza.
 */
public class CollisionModule extends SimulationModule {

    private static final String ID = "collisions";

    public enum CollisionScenario {
        LINEAR,
        OBLIQUE,
        NEWTON_CRADLE
    }

    private CollisionScenario scenario = CollisionScenario.LINEAR;

    private SceneObject objA;
    private SceneObject objB;

    private Vec3 posA = Vec3.ZERO;
    private Vec3 posB = Vec3.ZERO;
    private Vec3 simVelA = Vec3.ZERO;
    private Vec3 simVelB = Vec3.ZERO;
    private double floorY;
    private double elapsed;

    private Vec3 velA_before = Vec3.ZERO;
    private Vec3 velB_before = Vec3.ZERO;
    private Vec3 velA_after = Vec3.ZERO;
    private Vec3 velB_after = Vec3.ZERO;
    private boolean collisionOccurred = false;
    private double collisionTimer = 0;
    private double prevDist = Double.MAX_VALUE;
    private double collisionDist;
    private double preparedVelocityA;
    private boolean started = false;
    private double damageA = 0;
    private double damageB = 0;
    private String stateA = "integro";
    private String stateB = "integro";
    private String responseA = "aguardando impacto";
    private String responseB = "aguardando impacto";
    private final List<SceneObject> impactFragments = new ArrayList<>();

    public CollisionModule() {
        super(ID, "Colisoes",
            "Compare como materiais reais reagem ao impacto: gelatina, borracha, madeira, pedra, aco, vidro e espuma.",
            "Mecanica - Dinamica e Impulso");
    }

    @Override
    protected void declareParameters() {
        parameters.add(Parameter.of("material_a", "Material A", MaterialState.STONE.ordinal())
            .options(MaterialState.labels())
            .tooltip("Estado fisico do objeto A: densidade, atrito, dureza e elasticidade."));

        parameters.add(Parameter.of("material_b", "Material B", MaterialState.GELATIN.ordinal())
            .options(MaterialState.labels())
            .tooltip("Estado fisico do objeto B: gelatina absorve impacto; pedra e aco resistem mais."));

        parameters.add(Parameter.of("radius_a", "Raio A", 0.42)
            .range(0.05, 3.00).limits(1e-4, 1e4).unit("m").step(0.02)
            .reference("bola de tenis 0,033 | bola de boliche 0,109 | esfera de demolicao 0,5 m")
            .tooltip("Tamanho do objeto A. A massa e calculada por densidade x volume."));

        parameters.add(Parameter.of("radius_b", "Raio B", 0.42)
            .range(0.05, 3.00).limits(1e-4, 1e4).unit("m").step(0.02)
            .reference("bola de tenis 0,033 | bola de boliche 0,109 | esfera de demolicao 0,5 m")
            .tooltip("Tamanho do objeto B. A massa e calculada por densidade x volume."));

        parameters.add(Parameter.of("velocity_a", "Velocidade A", 6.0)
            .range(-80, 80).limits(-2.99e8, 2.99e8).unit("m/s").step(0.5)
            .reference("saque de tenis 70 | bala de pistola 380 | luz 3,00e8 m/s")
            .outOfRangeNote("Acima de 3,0e7 m/s (10% da luz) a mecanica newtoniana deste simulador deixa de valer.")
            .tooltip("Componente horizontal inicial do objeto A. Valor negativo move para a esquerda."));

        parameters.add(Parameter.of("offset_z", "Offset lateral", 0.0)
            .range(-8, 8).limits(-1e4, 1e4).unit("m").step(0.2)
            .outOfRangeNote("Deslocamento maior que a soma dos raios faz os corpos passarem sem colidir.")
            .tooltip("Deslocamento lateral de B para criar colisao obliqua."));

        parameters.add(Parameter.of("gravity", "Gravidade", 9.81)
            .range(0, 30).limits(0, 1e6).unit("m/s2").step(0.1)
            .reference("Lua 1,62 | Marte 3,71 | Terra 9,81 | Jupiter 24,79 | Sol 274 m/s2"));
    }

    @Override
    protected void buildScene() {
        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);

        MaterialState matA = materialA();
        MaterialState matB = materialB();
        double rA = parameters.getValue("radius_a", 0.42);
        double rB = parameters.getValue("radius_b", 0.42);
        double mA = massA();
        double mB = massB();
        double vA = parameters.getValue("velocity_a", 6.0);
        double offZ = parameters.getValue("offset_z", 0.0);
        floorY = context.getPhysics().getFloorY();
        double xA = -5.4;
        double xB = 2.2;

        context.setGravityStrength(0);
        collisionDist = rA + rB;
        posA = new Vec3(xA, floorY + rA, 0);
        posB = new Vec3(xB, floorY + rB, offZ);
        preparedVelocityA = vA;
        simVelA = Vec3.ZERO;
        simVelB = Vec3.ZERO;
        elapsed = 0;

        objA = builder
            .sphere(rA, matA.color)
            .at(posA)
            .withMaterial(matA.restitution, matA.friction, matA.linearDamping, matA.angularDamping)
            .withMass(mA)
            .isStatic()
            .named("obj_A_" + matA.label)
            .add();

        objB = builder
            .sphere(rB, matB.color)
            .at(posB)
            .withMaterial(matB.restitution, matB.friction, matB.linearDamping, matB.angularDamping)
            .withMass(mB)
            .isStatic()
            .named("obj_B_" + matB.label)
            .add();

        velA_before = new Vec3(vA, 0, 0);
        velB_before = Vec3.ZERO;
        velA_after = Vec3.ZERO;
        velB_after = Vec3.ZERO;
        collisionOccurred = false;
        collisionTimer = 0;
        prevDist = Double.MAX_VALUE;
        started = false;
        damageA = 0;
        damageB = 0;
        stateA = "integro";
        stateB = "integro";
        responseA = "aguardando impacto";
        responseB = "aguardando impacto";
        impactFragments.clear();

        builder.box(28, 0.018, 0.035, new ColorRGBA(0.32, 0.75, 0.82, 0.45))
            .at(0, floorY - 0.006, -0.78).isStatic().named("trilha_objeto_a").add();
        builder.box(28, 0.018, 0.035, new ColorRGBA(0.95, 0.50, 0.44, 0.42))
            .at(0, floorY - 0.006, offZ + 0.78).isStatic().named("trilha_objeto_b").add();

        builder.box(0.05, 1.2, 0.05, new ColorRGBA(0.6, 0.7, 1.0, 0.55))
            .at(xA, floorY + 0.7, 0).isStatic().named("marcador_a").add();

        builder.box(0.05, 1.2, 0.05, new ColorRGBA(1.0, 0.55, 0.55, 0.55))
            .at(xB, floorY + 0.7, offZ).isStatic().named("marcador_b").add();
    }

    @Override
    public void onUpdate(double dt) {
        if (objA == null || objB == null) return;
        if (!started) {
            objA.getBody().setPosition(posA);
            objB.getBody().setPosition(posB);
            return;
        }

        elapsed += dt;
        double rA = parameters.getValue("radius_a", 0.42);
        double rB = parameters.getValue("radius_b", 0.42);

        double remainingDt = dt;
        if (!collisionOccurred) {
            double impactTime = timeOfImpact(dt);
            if (impactTime >= 0 && impactTime <= dt) {
                posA = clampArena(posA.add(simVelA.mul(impactTime)), rA);
                posB = clampArena(posB.add(simVelB.mul(impactTime)), rB);
                resolveMaterialImpact();
                remainingDt = Math.max(0, dt - impactTime);
            }
        }

        if (collisionOccurred) {
            collisionTimer += dt;
            velA_after = simVelA;
            velB_after = simVelB;
        }

        double dampA = Math.pow(materialA().linearDamping, dt * 45.0);
        double dampB = Math.pow(materialB().linearDamping, dt * 45.0);
        if (stateA.equals("quebrado")) dampA = Math.pow(0.92, dt * 45.0);
        if (stateB.equals("quebrado")) dampB = Math.pow(0.92, dt * 45.0);
        simVelA = new Vec3(simVelA.x * dampA, 0, simVelA.z * dampA);
        simVelB = new Vec3(simVelB.x * dampB, 0, simVelB.z * dampB);

        posA = clampArena(posA.add(simVelA.mul(remainingDt)), rA);
        posB = clampArena(posB.add(simVelB.mul(remainingDt)), rB);
        objA.getBody().setPosition(posA);
        objB.getBody().setPosition(posB);
        prevDist = posA.distanceTo(posB);
    }

    @Override
    public void onParameterChanged(String paramName, double newValue) {
        onReset();
    }

    @Override
    public String launchActionLabel() { return "Iniciar colisao"; }

    @Override
    public void launch() { startCollision(); }

    public void startCollision() {
        if (started) return;
        started = true;
        simVelA = new Vec3(preparedVelocityA, 0, 0);
        simVelB = Vec3.ZERO;
        velA_before = simVelA;
        velB_before = simVelB;
        velA_after = Vec3.ZERO;
        velB_after = Vec3.ZERO;
        collisionOccurred = false;
        collisionTimer = 0;
        elapsed = 0;
    }

    public static List<Challenge> buildChallenges() {
        return List.of(
            new Challenge("col_01",
                "Colisao entre materiais",
                "Compare Gelatina x Pedra, Borracha x Aco e Vidro x Madeira. Observe energia perdida.",
                "Materiais moles absorvem energia; materiais duros conservam mais velocidade.",
                400, 120.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Troque os materiais e observe a telemetria.");
                }
            },
            new Challenge("col_02",
                "Colisao obliqua",
                "Aumente o offset lateral e veja a velocidade se dividir em x e z apos o impacto.",
                "O normal de colisao muda quando os centros nao estao alinhados.",
                500, 120.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Use Offset lateral diferente de zero.");
                }
            }
        );
    }

    public boolean isCollisionOccurred() { return collisionOccurred; }
    public Vec3 getVelABefore() { return velA_before; }
    public Vec3 getVelBBefore() { return velB_before; }
    public Vec3 getVelAAfter() { return velA_after; }
    public Vec3 getVelBAfter() { return velB_after; }

    public double getMomentumBefore() {
        return massA() * velA_before.length() + massB() * velB_before.length();
    }

    public double getMomentumAfter() {
        return massA() * velA_after.length() + massB() * velB_after.length();
    }

    public double getKineticEnergyBefore() {
        return 0.5 * massA() * velA_before.lengthSq() + 0.5 * massB() * velB_before.lengthSq();
    }

    public double getKineticEnergyAfter() {
        return 0.5 * massA() * velA_after.lengthSq() + 0.5 * massB() * velB_after.lengthSq();
    }

    public double getMeasuredRestitution() {
        double vRelBefore = velA_before.sub(velB_before).length();
        double vRelAfter = velA_after.sub(velB_after).length();
        return vRelBefore > 0.01 ? vRelAfter / vRelBefore : 0;
    }

    @Override
    public String telemetry() {
        if (!started) {
            return String.format(
                "Colisao pronta para iniciar%n%nCORPO A%nMaterial: %s%nMassa: %.1f kg%nRaio: %.2f m%n%nCORPO B%nMaterial: %s%nMassa: %.1f kg%nRaio: %.2f m%n%n> Clique em Iniciar colisao para aplicar %.2f m/s no corpo A.%n> O impacto usa massa, dureza, atrito e restituicao dos materiais.",
                materialA().label, massA(), parameters.getValue("radius_a", 0.42),
                materialB().label, massB(), parameters.getValue("radius_b", 0.42),
                preparedVelocityA
            );
        }
        double keBefore = getKineticEnergyBefore();
        double keAfter = getKineticEnergyAfter();
        double absorbed = keBefore > 0.01 ? Math.max(0, (1.0 - keAfter / keBefore) * 100.0) : 0.0;
        MaterialState matA = materialA();
        MaterialState matB = materialB();
        double impactSpeed = velA_before.sub(velB_before).length();
        return String.format(
            "Colisao entre %s e %s%n%nANTES E DEPOIS%nVelocidade A: %.2f -> %.2f m/s%nVelocidade B: %.2f -> %.2f m/s%nMomento: %.1f -> %.1f kg.m/s%nEnergia: %.1f -> %.1f J%nEnergia absorvida: %.1f%%%nRestituicao medida: %.2f%n%nCONTATO%nRestituicao do par: %.3f%nModulo de contato: %.2f GPa%nPressao no impacto: %.0f MPa%n%nCORPO A%nMaterial: %s (m=%.1f kg)%nPropriedades: %s%nModo de falha: %s%nEstado: %s (dano %.0f%%)%nResposta: %s%n%nCORPO B%nMaterial: %s (m=%.1f kg)%nPropriedades: %s%nModo de falha: %s%nEstado: %s (dano %.0f%%)%nResposta: %s",
            matA.label, matB.label,
            velA_before.length(), velA_after.length(),
            velB_before.length(), velB_after.length(),
            getMomentumBefore(), getMomentumAfter(),
            keBefore, keAfter, absorbed, getMeasuredRestitution(),
            matA.restitutionWith(matB, impactSpeed),
            matA.effectiveModulusWith(matB) / 1e9,
            matA.contactPressureWith(matB, impactSpeed) / 1e6,
            matA.label, massA(), matA.describeProperties(), matA.failureMode(),
            stateA, damageA * 100.0, responseA,
            matB.label, massB(), matB.describeProperties(), matB.failureMode(),
            stateB, damageB * 100.0, responseB
        );
    }

    @Override
    public LabMeasurement sampleLabMeasurement() {
        return new LabMeasurement(
            "colisao",
            "velocidade A", "m/s", simVelA.length(),
            "velocidade B", "m/s", simVelB.length(),
            String.format("A=%.2f m/s | B=%.2f m/s | energia depois=%.1f J",
                simVelA.length(), simVelB.length(), getKineticEnergyAfter())
        );
    }

    /**
     * Restituicao do par na velocidade de impacto atual.
     *
     * <p>Antes era {@code min(eA, eB)} multiplicado por um fator arbitrario de
     * dureza. Agora vem do modelo de material: media geometrica dos valores de
     * ensaio, corrigida pela velocidade — acima do limiar de escoamento parte da
     * energia vira deformacao permanente e a restituicao cai com v^(-1/4).
     */
    private double combinedRestitution() {
        double approachSpeed = Math.abs(preparedVelocityA);
        return materialA().restitutionWith(materialB(), approachSpeed);
    }

    private double timeOfImpact(double dt) {
        Vec3 relPos = posB.sub(posA);
        Vec3 relVel = simVelB.sub(simVelA);
        double radius = collisionDist;
        double c = relPos.lengthSq() - radius * radius;
        if (c <= 0) return 0;

        double a = relVel.lengthSq();
        if (a < 1e-10) return -1;

        double b = 2.0 * relPos.dot(relVel);
        if (b >= 0) return -1;

        double disc = b * b - 4.0 * a * c;
        if (disc < 0) return -1;

        double t = (-b - Math.sqrt(disc)) / (2.0 * a);
        return (t >= 0 && t <= dt) ? t : -1;
    }

    private void resolveMaterialImpact() {
        Vec3 normal = posB.sub(posA).normalize();
        if (normal.lengthSq() < 1e-10) normal = Vec3.RIGHT;
        double velAlongNormal = simVelB.sub(simVelA).dot(normal);

        velA_before = simVelA;
        velB_before = simVelB;
        double impactSpeed = velA_before.sub(velB_before).length();
        collisionOccurred = true;
        collisionTimer = 0;

        if (velAlongNormal < 0) {
            double e = combinedRestitution();
            double invMassA = 1.0 / massA();
            double invMassB = 1.0 / massB();
            double impulseMag = -(1.0 + e) * velAlongNormal / (invMassA + invMassB);
            Vec3 impulse = normal.mul(impulseMag);
            simVelA = simVelA.sub(impulse.mul(invMassA));
            simVelB = simVelB.add(impulse.mul(invMassB));
            applyFrictionImpulse(normal, impulseMag, invMassA, invMassB);
        }

        damageA = materialDamage(materialB(), materialA(), parameters.getValue("radius_b", 0.42), parameters.getValue("radius_a", 0.42), impactSpeed) * 0.45;
        damageB = materialDamage(materialA(), materialB(), parameters.getValue("radius_a", 0.42), parameters.getValue("radius_b", 0.42), impactSpeed);
        stateA = applyCollisionDamage(objA, materialA(), damageA, posA, normal.negate(), "A");
        stateB = applyCollisionDamage(objB, materialB(), damageB, posB, normal, "B");
        responseA = materialResponseDescription(materialA(), stateA.equals("quebrado"), damageA);
        responseB = materialResponseDescription(materialB(), stateB.equals("quebrado"), damageB);

        Vec3 reactionB = materialCollisionReaction(materialA(), materialB(), velA_before, normal,
            parameters.getValue("radius_a", 0.42), parameters.getValue("radius_b", 0.42), damageB);
        Vec3 reactionA = materialCollisionReaction(materialB(), materialA(), velB_before, normal.negate(),
            parameters.getValue("radius_b", 0.42), parameters.getValue("radius_a", 0.42), damageA);
        simVelB = blendMaterialReaction(simVelB, reactionB, materialB(), damageB);
        simVelA = blendMaterialReaction(simVelA, reactionA, materialA(), damageA);
        enforceEnergyBudget();

        double dist = posA.distanceTo(posB);
        double overlap = collisionDist - dist;
        if (overlap > 0) {
            double invMassA = 1.0 / massA();
            double invMassB = 1.0 / massB();
            double totalInvMass = invMassA + invMassB;
            posA = posA.sub(normal.mul(overlap * invMassA / totalInvMass));
            posB = posB.add(normal.mul(overlap * invMassB / totalInvMass));
        }
    }

    /**
     * Dano por criterio de tensao: compara o pico de pressao de contato com a
     * tensao de ruptura do alvo.
     *
     * <p>O modelo anterior comparava a energia cinetica total com a energia de
     * fratura da peca inteira — o que exagerava a resistencia de corpos grandes,
     * ja que na pratica a falha comeca na regiao de contato, nao no volume todo.
     * A pressao de Hertz captura essa concentracao.
     */
    private double materialDamage(MaterialState striker, MaterialState target,
                                  double strikerRadius, double targetRadius, double speed) {
        double contactPressure = striker.contactPressureWith(target, speed);
        double stressRatio = contactPressure / Math.max(1.0, target.failureStrengthPa);

        // Corpos maiores distribuem melhor a carga: o dano relativo cai com a
        // razao entre o raio do alvo e o do projetil.
        double sizeRelief = Math.sqrt(Math.max(0.05, targetRadius) / Math.max(0.01, strikerRadius));
        return stressRatio / (STRESS_DAMAGE_SCALE * sizeRelief);
    }

    /**
     * Divisor que converte a razao tensao/resistencia em dano acumulado.
     *
     * <p>A pressao de Hertz supera a tensao de ruptura em quase qualquer impacto
     * perceptivel — e por isso que vidro trinca com facilidade. Esta escala situa
     * a faixa util do experimento nas velocidades que o usuario consegue ajustar,
     * preservando a ordem entre materiais, que e o que o modulo ensina.
     */
    private static final double STRESS_DAMAGE_SCALE = 55.0;

    private String applyCollisionDamage(SceneObject object, MaterialState material, double damage, Vec3 impactPos, Vec3 normal, String suffix) {
        if (object == null) return "integro";
        if (damage >= materialBreakThreshold(material)) {
            object.setVisible(false);
            createFragments(material, object.getBody().getBoundingRadius(), impactPos, normal, suffix);
            return "quebrado";
        }
        if (damage >= 0.45) {
            object.setColor(damagedColor(material, true));
            return "trincado";
        }
        if (damage >= 0.12) {
            object.setColor(damagedColor(material, false));
            return "marcado";
        }
        return "integro";
    }

    /**
     * Movimento que o alvo ganha pelo jeito do material responder ao golpe —
     * espuma afunda, gelatina balanca, vidro estilhaca para os lados.
     *
     * <p>Todo o resultado e escalado pela razao de massas, inclusive a parcela
     * lateral. Antes o termo lateral ignorava essa razao, e um projetil leve
     * conseguia sacudir um corpo milhoes de vezes mais pesado como se as
     * inercias fossem parecidas — o que fabricava energia cinetica.
     */
    private Vec3 materialCollisionReaction(MaterialState striker, MaterialState target, Vec3 incomingVelocity,
                                           Vec3 normalTowardTarget, double strikerRadius, double targetRadius, double damage) {
        if (incomingVelocity.lengthSq() < 1e-8) return Vec3.ZERO;
        double massRatio = striker.massForSphere(strikerRadius) / Math.max(0.05, target.massForSphere(targetRadius));
        double transfer = Math.min(1.2, massRatio);
        double response = materialMotionResponse(target) * (0.55 + Math.min(1.5, damage) * 0.45);
        Vec3 alongImpact = normalTowardTarget.mul(Math.max(0, incomingVelocity.dot(normalTowardTarget)));
        Vec3 lateral = new Vec3(incomingVelocity.x, 0, incomingVelocity.z).mul(0.12 * response * transfer);
        return alongImpact.mul(response * transfer).add(lateral).clamp(0, materialMaxTargetSpeed(target));
    }

    /**
     * Impulso tangencial de atrito seco (Coulomb) no ponto de contato.
     *
     * <p>Ate aqui o coeficiente de atrito de cada material era declarado mas
     * nunca entrava na colisao: so o impulso normal era aplicado, e por isso uma
     * esfera de borracha ({@code mu = 0,80}) raspava contra outra exatamente
     * como o vidro ({@code mu = 0,40}). Numa batida de raspao isso e justamente
     * o que diferencia os materiais.
     *
     * <p>O impulso segue a lei de Coulomb: opoe-se ao deslizamento e nao passa de
     * {@code mu} vezes o impulso normal. Como e limitado e sempre contrario ao
     * movimento relativo tangencial, ele so pode <b>retirar</b> energia — nunca
     * acrescentar. O atrito do par usa a media geometrica dos coeficientes, a
     * pratica usual quando so ha valor por material.
     *
     * @param normal      normal do contato, de A para B
     * @param normalImpulse magnitude do impulso normal ja aplicado
     */
    private void applyFrictionImpulse(Vec3 normal, double normalImpulse,
                                      double invMassA, double invMassB) {
        Vec3 relative = simVelB.sub(simVelA);
        Vec3 tangential = relative.sub(normal.mul(relative.dot(normal)));
        double slipSpeed = tangential.length();
        if (slipSpeed < 1e-6) return;   // impacto frontal: nao ha deslizamento

        Vec3 slipDirection = tangential.div(slipSpeed);
        double pairFriction = Math.sqrt(materialA().friction * materialB().friction);

        // Impulso que zeraria o deslizamento, limitado pelo atrito disponivel.
        double stoppingImpulse = slipSpeed / (invMassA + invMassB);
        double frictionImpulse = Math.min(stoppingImpulse, pairFriction * Math.abs(normalImpulse));

        Vec3 impulse = slipDirection.mul(frictionImpulse);
        simVelA = simVelA.add(impulse.mul(invMassA));
        simVelB = simVelB.sub(impulse.mul(invMassB));

        // O atrito converte deslizamento em rotacao: parte do impulso tangencial
        // vira torque nas esferas, girando-as em sentidos opostos.
        Vec3 spinAxis = normal.cross(slipDirection);
        if (spinAxis.lengthSq() > 1e-12) {
            double spinA = frictionImpulse * invMassA * SPIN_COUPLING;
            double spinB = frictionImpulse * invMassB * SPIN_COUPLING;
            if (objA != null) objA.getBody().setAngularVelocity(spinAxis.normalize().mul(-spinA));
            if (objB != null) objB.getBody().setAngularVelocity(spinAxis.normalize().mul(spinB));
        }
    }

    /**
     * Quanto do impulso de atrito vira rotacao visivel.
     *
     * <p>Para uma esfera macica, o momento de inercia e {@code 2/5 m r^2}, entao
     * o ganho de velocidade angular seria {@code 5 J_t /(2 m r)}. O fator abaixo
     * mantem essa proporcionalidade numa escala legivel na cena — o giro aqui e
     * indicativo, ja que o motor nao integra dinamica de corpo rigido completa.
     */
    private static final double SPIN_COUPLING = 2.5;

    /**
     * Garante que a colisao nunca devolva mais energia do que recebeu.
     *
     * <p>A etapa de resposta do material molda <i>como</i> cada corpo reage, mas
     * e uma redistribuicao — nao pode ser fonte de energia. Este travamento
     * transforma isso em invariante do modulo: se a soma pos-impacto passar da
     * pre-impacto, as duas velocidades sao reduzidas na mesma proporcao.
     * Sem ele, combinacoes extremas de massa violavam a segunda lei.
     */
    private void enforceEnergyBudget() {
        double mA = massA();
        double mB = massB();
        double before = 0.5 * mA * velA_before.lengthSq() + 0.5 * mB * velB_before.lengthSq();
        double after = 0.5 * mA * simVelA.lengthSq() + 0.5 * mB * simVelB.lengthSq();
        if (after <= before || after < 1e-12) return;

        double scale = Math.sqrt(before / after);
        simVelA = simVelA.mul(scale);
        simVelB = simVelB.mul(scale);
    }

    private Vec3 blendMaterialReaction(Vec3 impulseVelocity, Vec3 materialVelocity, MaterialState material, double damage) {
        double keepImpulse = switch (material) {
            case RUBBER -> 0.72;
            case GLASS -> damage >= materialBreakThreshold(material) ? 0.22 : 0.52;
            case WOOD -> 0.50;
            case STONE -> 0.38;
            case STEEL -> 0.24;
            case FOAM -> 0.18;
            case GELATIN -> 0.12;
        };
        return impulseVelocity.mul(keepImpulse).add(materialVelocity.mul(1.0 - keepImpulse));
    }

    private void createFragments(MaterialState material, double baseRadius, Vec3 impactPos, Vec3 normal, String suffix) {
        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        int pieces = fragmentCount(material);
        for (int i = 0; i < pieces; i++) {
            double side = Math.max(0.045, baseRadius * fragmentScale(material, i));
            double angle = Math.PI * 2.0 * i / Math.max(1, pieces);
            Vec3 spread = new Vec3(Math.cos(angle), 0.35 + 0.15 * (i % 2), Math.sin(angle)).normalize();
            Vec3 velocity = spread.add(normal.mul(0.8)).normalize().mul(fragmentSpeed(material, velA_before.sub(velB_before).length()));
            SceneObject fragment = builder.box(side, side, side, material.color.mix(ColorRGBA.WHITE, 0.25))
                .at(impactPos.add(spread.mul(baseRadius * 0.35)))
                .withVelocity(velocity)
                .withMass(Math.max(0.02, material.massForSphere(side * 0.5) * 0.18))
                .withMaterial(Math.min(0.38, material.restitution), material.friction, 0.988, 0.82)
                .named("frag_col_" + material.label + "_" + suffix + "_" + i)
                .add();
            impactFragments.add(fragment);
        }
    }

    private double materialMotionResponse(MaterialState material) {
        return switch (material) {
            case RUBBER -> 0.62;
            case GELATIN -> 0.12;
            case FOAM -> 0.08;
            case WOOD -> 0.26;
            case STONE -> 0.12;
            case STEEL -> 0.045;
            case GLASS -> 0.18;
        };
    }

    private double materialMaxTargetSpeed(MaterialState material) {
        return switch (material) {
            case RUBBER -> 11.0;
            case GLASS -> 8.5;
            case WOOD -> 7.0;
            case STONE -> 4.5;
            case STEEL -> 2.2;
            case FOAM -> 2.4;
            case GELATIN -> 2.0;
        };
    }

    /**
     * Dano necessario para romper. Vem do alongamento na ruptura do material:
     * fragil rompe cedo, ductil aguenta deformando. Antes era uma tabela fixa
     * por material, desconectada das propriedades.
     */
    private double materialBreakThreshold(MaterialState material) {
        return material.breakThreshold();
    }

    private ColorRGBA damagedColor(MaterialState material, boolean severe) {
        return switch (material) {
            case GELATIN -> material.color.mix(ColorRGBA.MAGENTA, severe ? 0.45 : 0.22);
            case RUBBER -> material.color.mix(ColorRGBA.WHITE, severe ? 0.18 : 0.08);
            case FOAM -> material.color.mix(ColorRGBA.ORANGE, severe ? 0.36 : 0.18);
            case GLASS -> material.color.mix(ColorRGBA.WHITE, severe ? 0.62 : 0.34);
            case STEEL -> material.color.mix(ColorRGBA.BLACK, severe ? 0.28 : 0.14);
            case WOOD -> material.color.mix(ColorRGBA.ORANGE, severe ? 0.42 : 0.22);
            case STONE -> material.color.mix(ColorRGBA.WHITE, severe ? 0.25 : 0.12);
        };
    }

    private int fragmentCount(MaterialState material) {
        return switch (material) {
            case GLASS -> 14;
            case WOOD -> 8;
            case STONE -> 6;
            case FOAM, GELATIN -> 5;
            case RUBBER -> 4;
            case STEEL -> 3;
        };
    }

    private double fragmentScale(MaterialState material, int index) {
        return switch (material) {
            case GLASS -> 0.10 + 0.025 * (index % 3);
            case FOAM, GELATIN -> 0.18 + 0.05 * (index % 2);
            case STEEL -> 0.16;
            default -> 0.18 + 0.035 * (index % 3);
        };
    }

    private double fragmentSpeed(MaterialState material, double impactSpeed) {
        return switch (material) {
            case GLASS -> 2.3 + impactSpeed * 0.11;
            case WOOD -> 1.7 + impactSpeed * 0.07;
            case STONE -> 1.4 + impactSpeed * 0.05;
            case FOAM -> 0.8 + impactSpeed * 0.025;
            case GELATIN -> 0.55 + impactSpeed * 0.018;
            case RUBBER -> 1.2 + impactSpeed * 0.04;
            case STEEL -> 0.6 + impactSpeed * 0.015;
        };
    }

    private String materialResponseDescription(MaterialState material, boolean broken, double damage) {
        if (broken) {
            return switch (material) {
                case GLASS -> "estilhacou";
                case WOOD -> "rachou e soltou lascas";
                case STONE -> "fraturou em blocos";
                case FOAM -> "rasgou e dissipou energia";
                case GELATIN -> "rompeu apos absorver impacto";
                case RUBBER -> "rompeu apos deformar";
                case STEEL -> "falhou estruturalmente";
            };
        }
        if (damage >= 0.45) {
            return switch (material) {
                case GLASS -> "trincou com baixo amortecimento";
                case RUBBER -> "deformou e devolveu energia";
                case GELATIN -> "absorveu impacto e deslocou pouco";
                case FOAM -> "amassou e amortecou";
                case STEEL -> "marcou pouco e quase nao moveu";
                case WOOD -> "trincou e arrastou";
                case STONE -> "trincou com baixa velocidade final";
            };
        }
        return switch (material) {
            case RUBBER -> "recuou e rebateu";
            case GELATIN -> "absorveu quase tudo";
            case FOAM -> "amorteceu";
            case STEEL -> "resistiu";
            case GLASS -> "vibrou sem quebrar";
            case WOOD -> "arrastou";
            case STONE -> "moveu pouco";
        };
    }

    private Vec3 clampArena(Vec3 p, double radius) {
        double arena = context.getPhysics().getArenaHalf() - radius;
        return new Vec3(
            clamp(p.x, -arena, arena),
            floorY + radius,
            clamp(p.z, -arena, arena)
        );
    }

    public void setScenario(CollisionScenario s) { this.scenario = s; onReset(); }
    public CollisionScenario getScenario() { return scenario; }

    private MaterialState materialA() {
        return MaterialState.byIndex(parameters.getValue("material_a", MaterialState.STONE.ordinal()));
    }

    private MaterialState materialB() {
        return MaterialState.byIndex(parameters.getValue("material_b", MaterialState.GELATIN.ordinal()));
    }

    private double massA() {
        return materialA().massForSphere(parameters.getValue("radius_a", 0.42));
    }

    private double massB() {
        return materialB().massForSphere(parameters.getValue("radius_b", 0.42));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
