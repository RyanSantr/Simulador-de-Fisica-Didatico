package engine.simulation.modules;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.physics.MaterialState;
import engine.physics.MediumState;
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
 * Lancamento de projeteis com meio e materiais definidos.
 *
 * O projetil tem massa calculada por densidade; o meio aplica arrasto
 * proporcional a v^2; os alvos usam materiais com restitucao e dureza.
 */
public class ProjectileModule extends SimulationModule {

    private static final String ID = "projectile";
    private static final double CD_SPHERE = 0.47;
    private static final double PROJECTILE_STEP = 1.0 / 240.0;

    private final List<SceneObject> targets = new ArrayList<>();
    private final List<Boolean> targetHit = new ArrayList<>();
    private final List<Boolean> targetBroken = new ArrayList<>();
    private final List<Double> targetIntegrity = new ArrayList<>();
    private final List<Vec3> targetHomePositions = new ArrayList<>();
    private final List<SceneObject> impactFragments = new ArrayList<>();
    private final List<Vec3> trajectory = new ArrayList<>();
    private final List<SceneObject> previewMarkers = new ArrayList<>();
    private static final int MAX_TRAIL = 200;

    private SceneObject projectile;
    private Vec3 projectileVelocity = Vec3.ZERO;
    private final List<Vec3> targetVelocities = new ArrayList<>();
    private boolean launched = false;
    private double flightTime = 0;
    private int targetsHit = 0;
    private Vec3 launchPos = Vec3.ZERO;
    private double lastImpactSpeed = 0;
    private double lastImpactDamage = 0;
    private double lastImpactEnergy = 0;
    private String lastTargetResponse = "sem resposta material";
    private String lastImpact = "Nenhum impacto ainda";

    public ProjectileModule() {
        super(ID, "Lancamento de Projeteis",
            "Lance um projetil realista em vacuo, ar, agua, oleo ou gel, contra alvos de materiais diferentes.",
            "Mecanica - Cinematica 2D e Dinamica dos Fluidos");
    }

    @Override
    protected void declareParameters() {
        parameters.add(Parameter.of("projectile_material", "Projetil", MaterialState.STONE.ordinal())
            .options(MaterialState.labels())
            .tooltip("Material do projetil: define massa, atrito e elasticidade."));

        parameters.add(Parameter.of("target_material", "Material alvo", MaterialState.GELATIN.ordinal())
            .options(MaterialState.labels())
            .tooltip("Material dos alvos: define como reagem ao impacto."));

        parameters.add(Parameter.of("medium", "Meio", MediumState.AIR.ordinal())
            .options(MediumState.labels())
            .tooltip("Meio atravessado pelo projetil: vacuo, ar, agua, oleo ou gel."));

        parameters.add(Parameter.of("speed", "Velocidade", 15.0)
            .range(0.1, 200).limits(0, 2.99e8).unit("m/s").step(1.0)
            .reference("saque de tenis 70 | som 343 | bala de fuzil 900 | luz 3,00e8 m/s")
            .outOfRangeNote("Acima de 3,0e7 m/s (10% da luz) a mecanica newtoniana deste simulador deixa de valer.")
            .tooltip("Magnitude da velocidade inicial de lancamento."));

        parameters.add(Parameter.of("angle", "Angulo", 45.0)
            .range(0, 90).limits(-90, 90).unit("graus").step(1.0)
            .reference("alcance maximo no vacuo: 45 graus")
            .outOfRangeNote("Angulo negativo lanca para baixo, valido para tiro de plataforma alta.")
            .tooltip("Angulo de lancamento. Em vacuo, 45 graus maximiza alcance."));

        parameters.add(Parameter.of("projectile_radius", "Raio projetil", 0.22)
            .range(0.03, 2.00).limits(1e-4, 1e4).unit("m").step(0.01)
            .reference("bala de fuzil 0,0039 | bola de tenis 0,033 | bala de canhao 0,08 m")
            .outOfRangeNote("O arrasto cresce com a area frontal: projeteis largos freiam muito mais no ar e na agua.")
            .tooltip("Tamanho do projetil. A massa vem da densidade do material."));

        parameters.add(Parameter.of("gravity", "Gravidade", 9.81)
            .range(0, 30).limits(0, 1e6).unit("m/s2").step(0.1)
            .reference("Lua 1,62 | Marte 3,71 | Terra 9,81 | Jupiter 24,79 | Sol 274 m/s2")
            .tooltip("Aceleracao gravitacional antes do fator de empuxo do meio."));

        parameters.add(Parameter.of("n_targets", "Numero de alvos", 3)
            .range(1, 6).limits(1, 40).unit("").step(1)
            .outOfRangeNote("Muitos alvos deixam a cena poluida e o alcance util menor.")
            .tooltip("Numero de alvos feitos com o material selecionado."));
    }

    @Override
    protected void buildScene() {
        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        MediumState medium = medium();
        MaterialState targetMaterial = targetMaterial();
        int nTgts = (int)parameters.getValue("n_targets", 3);
        double floorY = context.getPhysics().getFloorY();

        context.setGravityStrength(effectiveGravity());
        launchPos = new Vec3(-14, floorY + 0.35, 0);

        builder.box(1.6, 0.3, 1.6, ColorRGBA.fromHex("#334455"))
            .at(launchPos.x, floorY + 0.15, launchPos.z)
            .isStatic().named("plataforma_lancamento").add();

        builder.box(30, 0.018, 0.045, new ColorRGBA(0.34, 0.72, 0.78, 0.44))
            .at(0, floorY - 0.006, -1.7).isStatic().named("borda_corredor_a").add();
        builder.box(30, 0.018, 0.045, new ColorRGBA(0.34, 0.72, 0.78, 0.44))
            .at(0, floorY - 0.006, 1.7).isStatic().named("borda_corredor_b").add();

        targets.clear();
        targetHit.clear();
        targetBroken.clear();
        targetIntegrity.clear();
        targetHomePositions.clear();
        targetVelocities.clear();
        impactFragments.clear();
        previewMarkers.clear();
        double[] targetDistances = computeTargetDistances(nTgts);
        double targetRadius = 0.42;

        for (int i = 0; i < nTgts; i++) {
            double x = launchPos.x + targetDistances[i];
            double y = floorY + targetRadius;
            SceneObject target = builder
                .sphere(targetRadius, targetMaterial.color)
                .at(x, y, 0)
                .withMaterial(targetMaterial.restitution, targetMaterial.friction,
                    targetMaterial.linearDamping, targetMaterial.angularDamping)
                .withMass(targetMaterial.massForSphere(targetRadius))
                .isStatic()
                .named("alvo_" + targetMaterial.label + "_" + i)
                .add();

            targets.add(target);
            targetHit.add(false);
            targetBroken.add(false);
            targetIntegrity.add(1.0);
            targetHomePositions.add(target.getBody().getPosition());
            targetVelocities.add(Vec3.ZERO);
        }

        for (int i = 0; i < 18; i++) {
            SceneObject marker = builder.sphere(0.07, 6, 8, new ColorRGBA(0.96, 0.77, 0.25, 0.82))
                .at(launchPos)
                .isStatic()
                .named("trajetoria_prevista_" + i)
                .add();
            marker.setShadowCaster(false);
            previewMarkers.add(marker);
        }
        updatePreviewTrajectory();

        launched = false;
        flightTime = 0;
        targetsHit = 0;
        trajectory.clear();
        projectile = null;
        projectileVelocity = Vec3.ZERO;
        lastImpactSpeed = 0;
        lastImpactDamage = 0;
        lastImpactEnergy = 0;
        lastTargetResponse = "sem resposta material";
        lastImpact = "Nenhum impacto ainda";
    }

    @Override
    public String launchActionLabel() { return "Executar lancamento"; }

    @Override
    public void launch() {
        if (projectile != null) {
            context.removeObject(projectile);
            trajectory.clear();
        }
        resetTargetsForLaunch();

        MaterialState projectileMaterial = projectileMaterial();
        double radius = parameters.getValue("projectile_radius", 0.22);
        double speed = parameters.getValue("speed", 15.0);
        double angle = Math.toRadians(parameters.getValue("angle", 45.0));
        double vx = speed * Math.cos(angle);
        double vy = speed * Math.sin(angle);

        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        projectile = builder
            .sphere(radius, projectileMaterial.color)
            .at(launchPos.add(new Vec3(0.25, 0.55, 0)))
            .withMaterial(projectileMaterial.restitution, projectileMaterial.friction,
                projectileMaterial.linearDamping, projectileMaterial.angularDamping)
            .withMass(projectileMaterial.massForSphere(radius))
            .isStatic()
            .named("projetil_" + projectileMaterial.label)
            .add();

        projectileVelocity = new Vec3(vx, vy, 0);
        launched = true;
        flightTime = 0;
        targetsHit = 0;
        lastImpactSpeed = 0;
        lastImpactDamage = 0;
        lastImpactEnergy = 0;
        lastTargetResponse = "sem resposta material";
        lastImpact = "Em voo";
        for (int i = 0; i < targetHit.size(); i++) {
            targetHit.set(i, false);
            targetVelocities.set(i, Vec3.ZERO);
        }
    }

    @Override
    public void onUpdate(double dt) {
        updateTargets(dt);
        if (!launched || projectile == null) return;

        double remaining = Math.min(dt, 0.08);
        while (remaining > 1e-8 && launched) {
            double step = Math.min(PROJECTILE_STEP, remaining);
            stepProjectile(step);
            remaining -= step;
        }
    }

    private void stepProjectile(double dt) {
        RigidBody body = projectile.getBody();
        Vec3 oldPos = body.getPosition();
        flightTime += dt;
        projectileVelocity = projectileVelocity.add(accelerationFromMedium().mul(dt));
        Vec3 pos = oldPos.add(projectileVelocity.mul(dt));

        if (trajectory.isEmpty() || pos.distanceTo(trajectory.get(trajectory.size() - 1)) > 0.3) {
            if (trajectory.size() >= MAX_TRAIL) trajectory.remove(0);
            trajectory.add(pos);
        }

        for (int i = 0; i < targets.size(); i++) {
            if (targetHit.get(i) || targetBroken.get(i)) continue;
            SceneObject target = targets.get(i);
            double minDist = body.getBoundingRadius() + target.getBody().getBoundingRadius();
            double alpha = segmentSphereAlpha(oldPos, pos, target.getBody().getPosition(), minDist);
            if (alpha >= 0) {
                pos = oldPos.lerp(pos, alpha);
                targetHit.set(i, true);
                targetsHit++;
                lastImpactSpeed = projectileVelocity.length();
                lastImpactEnergy = impactEnergy(projectileMaterial(), body.getBoundingRadius(), lastImpactSpeed);
                MaterialState targetMat = targetMaterial();
                MaterialState projectileMat = projectileMaterial();
                lastImpactDamage = damageRatio(projectileMat, targetMat, body.getBoundingRadius(), target.getBody().getBoundingRadius(), lastImpactSpeed);
                Vec3 normal = pos.sub(target.getBody().getPosition()).normalize();
                if (normal.lengthSq() < 1e-10) normal = projectileVelocity.negate().normalize();
                targetVelocities.set(i, targetReactionVelocity(projectileMat, targetMat, projectileVelocity, normal,
                    body.getBoundingRadius(), target.getBody().getBoundingRadius(), lastImpactDamage));
                projectileVelocity = projectileVelocity.reflect(normal)
                    .mul(projectileReboundFactor(projectileMat, targetMat, lastImpactDamage));
                pos = pos.add(normal.mul(0.03));
                applyTargetDamage(i, pos, normal, target, lastImpactDamage);
                lastTargetResponse = materialResponseDescription(targetMat, targetBroken.get(i), lastImpactDamage);
                lastImpact = String.format("Impacto em %s: %.1f m/s (%.1f km/h), energia %.0f J, dano %.0f%%, estado %s, resposta: %s",
                    targetMat.label, lastImpactSpeed, lastImpactSpeed * 3.6,
                    lastImpactEnergy, lastImpactDamage * 100.0, targetState(i), lastTargetResponse);
                break;
            }
        }

        if (pos.y < context.getPhysics().getFloorY() + body.getBoundingRadius()) {
            body.setPosition(new Vec3(pos.x, context.getPhysics().getFloorY() + body.getBoundingRadius(), pos.z));
            projectileVelocity = new Vec3(projectileVelocity.x * 0.45, -projectileVelocity.y * projectileMaterial().restitution * 0.35, projectileVelocity.z * 0.45);
            if (projectileVelocity.length() < 0.35) launched = false;
        } else {
            body.setPosition(pos);
        }
        if (pos.x > 18.0 || pos.x < -18.0) launched = false;
    }

    private double segmentSphereAlpha(Vec3 from, Vec3 to, Vec3 center, double radius) {
        Vec3 d = to.sub(from);
        Vec3 f = from.sub(center);
        double a = d.lengthSq();
        if (a < 1e-10) return from.distanceTo(center) <= radius ? 0 : -1;
        double b = 2.0 * f.dot(d);
        double c = f.lengthSq() - radius * radius;
        double disc = b * b - 4.0 * a * c;
        if (disc < 0) return -1;
        double sqrt = Math.sqrt(disc);
        double t1 = (-b - sqrt) / (2.0 * a);
        double t2 = (-b + sqrt) / (2.0 * a);
        if (t1 >= 0 && t1 <= 1) return t1;
        if (t2 >= 0 && t2 <= 1) return t2;
        return -1;
    }

    @Override
    public void onParameterChanged(String paramName, double newValue) {
        switch (paramName) {
            case "gravity" -> {
                context.setGravityStrength(effectiveGravity());
                updatePreviewTrajectory();
            }
            case "speed", "angle", "projectile_radius" -> updatePreviewTrajectory();
            default -> onReset();
        }
    }

    public static List<Challenge> buildChallenges() {
        return List.of(
            new Challenge("proj_01",
                "Vacuo x Agua",
                "Lance com os mesmos valores em Vacuo e depois em Agua. Compare alcance e tempo de voo.",
                "No vacuo nao ha arrasto; na agua o arrasto cresce muito com a velocidade.",
                400, 120.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Troque o meio e lance novamente.");
                }
            },
            new Challenge("proj_02",
                "Material do alvo",
                "Compare o impacto em Gelatina, Borracha, Pedra e Aco.",
                "Alvos duros devolvem mais velocidade; alvos moles absorvem mais energia.",
                600, 180.0) {
                @Override
                protected ChallengeResult evaluateCondition(double dt) {
                    return ChallengeResult.running(getId(), "Troque Material alvo e observe o impacto.");
                }
            }
        );
    }

    /**
     * Aceleracao do projetil: peso corrigido pelo empuxo mais o arrasto do meio.
     *
     * <p>O empuxo segue Arquimedes e depende da densidade <b>do projetil</b> —
     * uma esfera de madeira na agua sobe, uma de aco desce quase com a gravidade
     * cheia. O arrasto usa o coeficiente da esfera para o numero de Reynolds do
     * escoamento, e nao um fator fixo por meio.
     */
    private Vec3 accelerationFromMedium() {
        MediumState medium = medium();
        MaterialState material = projectileMaterial();
        Vec3 gravity = context.getPhysics().isGravityEnabled()
            ? new Vec3(0, -parameters.getValue("gravity", 9.81)
                * medium.buoyancyFactorFor(material.densityKgM3), 0)
            : Vec3.ZERO;
        if (medium.densityKgM3 <= 0 || projectile == null) return gravity;

        Vec3 vel = projectileVelocity;
        double speed = vel.length();
        if (speed < 0.01) return gravity;

        double radius = projectile.getBody().getBoundingRadius();
        double force = medium.dragForce(speed, radius);
        double mass = material.massForSphere(radius);
        Vec3 drag = vel.normalize().mul(-force / Math.max(0.05, mass));
        return gravity.add(drag);
    }

    /** Gravidade efetiva do experimento, ja com o empuxo do meio. */
    private double effectiveGravity() {
        return parameters.getValue("gravity", 9.81)
            * medium().buoyancyFactorFor(projectileMaterial().densityKgM3);
    }

    private void updateTargets(double dt) {
        double floorY = context.getPhysics().getFloorY();
        for (int i = 0; i < targets.size(); i++) {
            Vec3 v = targetVelocities.get(i);
            if (v.lengthSq() < 0.0001) continue;
            SceneObject target = targets.get(i);
            double r = target.getBody().getBoundingRadius();
            Vec3 gravity = context.getPhysics().isGravityEnabled()
                ? new Vec3(0, -parameters.getValue("gravity", 9.81), 0)
                : Vec3.ZERO;
            v = v.add(gravity.mul(dt));
            Vec3 p = target.getBody().getPosition().add(v.mul(dt));
            if (p.y < floorY + r) {
                p = new Vec3(p.x, floorY + r, p.z);
                v = new Vec3(v.x * materialFloorSlide(targetMaterial()), -v.y * materialFloorBounce(targetMaterial()), v.z * materialFloorSlide(targetMaterial()));
            }
            v = v.mul(Math.pow(materialMotionDamping(targetMaterial()), dt * 60.0));
            target.getBody().setPosition(p);
            targetVelocities.set(i, v.length() < 0.05 ? Vec3.ZERO : v);
        }
    }

    private double impactTransfer(MaterialState projectile, MaterialState target) {
        double hardnessRatio = projectile.hardness / Math.max(0.05, projectile.hardness + target.hardness);
        double absorption = target.impactAbsorption() * 0.45;
        return Math.max(0.08, Math.min(0.65, hardnessRatio * (1.0 - absorption)));
    }

    private void resetTargetsForLaunch() {
        for (SceneObject fragment : new ArrayList<>(impactFragments)) {
            context.removeObject(fragment);
        }
        impactFragments.clear();
        for (int i = 0; i < targets.size(); i++) {
            SceneObject target = targets.get(i);
            target.setVisible(true);
            target.setColor(targetMaterial().color);
            target.getBody().setPosition(targetHomePositions.get(i));
            targetHit.set(i, false);
            targetBroken.set(i, false);
            targetIntegrity.set(i, 1.0);
            targetVelocities.set(i, Vec3.ZERO);
        }
    }

    private double impactEnergy(MaterialState material, double radius, double speed) {
        return 0.5 * material.massForSphere(radius) * speed * speed;
    }

    private double damageRatio(MaterialState projectile, MaterialState target, double projectileRadius, double targetRadius, double speed) {
        // Criterio de tensao (pressao de Hertz contra a resistencia do alvo), o
        // mesmo do modulo de colisoes — ver MaterialState.contactPressureWith.
        double contactPressure = projectile.contactPressureWith(target, speed);
        double stressRatio = contactPressure / Math.max(1.0, target.failureStrengthPa);
        double sizeRelief = Math.sqrt(Math.max(0.05, targetRadius) / Math.max(0.01, projectileRadius));
        return stressRatio / (STRESS_DAMAGE_SCALE * sizeRelief);
    }

    private void applyTargetDamage(int index, Vec3 impactPos, Vec3 normal, SceneObject target, double damage) {
        double integrity = Math.max(0, targetIntegrity.get(index) - damage);
        targetIntegrity.set(index, integrity);
        double breakLimit = materialBreakThreshold(targetMaterial());
        if (integrity <= 0.0 || damage >= breakLimit) {
            targetBroken.set(index, true);
            target.setVisible(false);
            targetVelocities.set(index, Vec3.ZERO);
            createFragments(index, impactPos, normal);
        } else if (integrity <= 0.55 || damage >= 0.45) {
            target.setColor(damagedColor(targetMaterial(), damage, true));
        } else {
            target.setColor(damagedColor(targetMaterial(), damage, false));
        }
    }

    private void createFragments(int targetIndex, Vec3 impactPos, Vec3 normal) {
        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        MaterialState mat = targetMaterial();
        double baseRadius = targets.get(targetIndex).getBody().getBoundingRadius();
        int pieces = fragmentCount(mat);
        for (int i = 0; i < pieces; i++) {
            double side = Math.max(0.045, baseRadius * fragmentScale(mat, i));
            double angle = (Math.PI * 2.0 * i) / 7.0;
            Vec3 spread = new Vec3(Math.cos(angle), 0.45 + 0.12 * (i % 2), Math.sin(angle)).normalize();
            Vec3 velocity = spread.add(normal.mul(0.8)).normalize().mul(fragmentSpeed(mat, lastImpactSpeed));
            SceneObject fragment = builder.box(side, side, side, mat.color.mix(ColorRGBA.WHITE, 0.25))
                .at(impactPos.add(spread.mul(baseRadius * 0.35)))
                .withVelocity(velocity)
                .withMass(Math.max(0.02, mat.massForSphere(side * 0.5) * 0.18))
                .withMaterial(Math.min(0.38, mat.restitution), mat.friction, 0.988, 0.82)
                .named("fragmento_" + mat.label + "_" + targetIndex + "_" + i)
                .add();
            impactFragments.add(fragment);
        }
    }

    private String targetState(int index) {
        if (targetBroken.get(index)) return "quebrado";
        double integrity = targetIntegrity.get(index);
        if (integrity <= 0.55) return "trincado";
        if (integrity < 0.90) return "marcado";
        return "integro";
    }

    private Vec3 targetReactionVelocity(MaterialState projectile, MaterialState target, Vec3 incomingVelocity,
                                        Vec3 normal, double projectileRadius, double targetRadius, double damage) {
        double projectileMass = projectile.massForSphere(projectileRadius);
        double targetMass = target.massForSphere(targetRadius);
        double massRatio = projectileMass / Math.max(0.05, targetMass);
        double response = materialMotionResponse(target) * (0.65 + Math.min(1.5, damage) * 0.35);
        Vec3 push = incomingVelocity.mul(response * Math.min(1.2, massRatio));
        Vec3 lift = Vec3.UP.mul(materialLiftResponse(target) * Math.min(8.0, incomingVelocity.length() * 0.10));
        Vec3 normalKick = normal.negate().mul(materialNormalKick(target) * incomingVelocity.length());
        return push.add(lift).add(normalKick).clamp(0, materialMaxTargetSpeed(target));
    }

    private double projectileReboundFactor(MaterialState projectile, MaterialState target, double damage) {
        double base = switch (target) {
            case RUBBER -> 0.72;
            case STEEL -> 0.58;
            case STONE -> 0.42;
            case GLASS -> damage >= materialBreakThreshold(target) ? 0.18 : 0.36;
            case WOOD -> 0.30;
            case FOAM -> 0.10;
            case GELATIN -> 0.08;
        };
        return Math.min(0.82, base * (0.65 + projectile.restitution * 0.35));
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

    private double materialLiftResponse(MaterialState material) {
        return switch (material) {
            case RUBBER -> 0.55;
            case FOAM -> 0.18;
            case GELATIN -> 0.10;
            case WOOD -> 0.16;
            case GLASS -> 0.26;
            case STONE -> 0.08;
            case STEEL -> 0.035;
        };
    }

    private double materialNormalKick(MaterialState material) {
        return switch (material) {
            case RUBBER -> 0.24;
            case GLASS -> 0.16;
            case WOOD -> 0.10;
            case STONE -> 0.06;
            case STEEL -> 0.025;
            case FOAM, GELATIN -> 0.02;
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

    private double materialMotionDamping(MaterialState material) {
        return switch (material) {
            case RUBBER -> 0.992;
            case GLASS -> 0.985;
            case WOOD -> 0.976;
            case STONE -> 0.968;
            case STEEL -> 0.955;
            case FOAM -> 0.920;
            case GELATIN -> 0.900;
        };
    }

    private double materialFloorBounce(MaterialState material) {
        return switch (material) {
            case RUBBER -> 0.62;
            case GLASS -> 0.22;
            case WOOD -> 0.18;
            case STONE -> 0.14;
            case STEEL -> 0.08;
            case FOAM -> 0.05;
            case GELATIN -> 0.02;
        };
    }

    private double materialFloorSlide(MaterialState material) {
        return switch (material) {
            case RUBBER -> 0.82;
            case GLASS -> 0.74;
            case WOOD -> 0.62;
            case STONE -> 0.55;
            case STEEL -> 0.46;
            case FOAM -> 0.22;
            case GELATIN -> 0.18;
        };
    }

    /**
     * Dano necessario para romper, derivado do alongamento na ruptura do
     * material — ver {@link MaterialState#breakThreshold()}.
     */
    private double materialBreakThreshold(MaterialState material) {
        return material.breakThreshold();
    }

    /** Ver CollisionModule: mesma escala, para os dois modulos concordarem. */
    private static final double STRESS_DAMAGE_SCALE = 55.0;

    private ColorRGBA damagedColor(MaterialState material, double damage, boolean severe) {
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
            case FOAM -> 5;
            case GELATIN -> 5;
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
                case GLASS -> "estilhacou em muitos fragmentos";
                case WOOD -> "rachou e soltou lascas";
                case STONE -> "fraturou em blocos";
                case FOAM -> "rasgou e perdeu forma";
                case GELATIN -> "rompeu e espalhou massa";
                case RUBBER -> "rompeu apos deformar";
                case STEEL -> "falhou estruturalmente";
            };
        }
        if (damage >= 0.45) {
            return switch (material) {
                case GLASS -> "trincou e quase nao absorveu impacto";
                case RUBBER -> "deformou e rebateu o projetil";
                case GELATIN -> "absorveu impacto com deformacao";
                case FOAM -> "amassou e dissipou energia";
                case STEEL -> "marcou pouco e quase nao deslocou";
                case WOOD -> "trincou e arrastou";
                case STONE -> "trincou com baixo deslocamento";
            };
        }
        return switch (material) {
            case RUBBER -> "recuou e devolveu energia";
            case GELATIN -> "absorveu quase tudo";
            case FOAM -> "amorteceu o impacto";
            case STEEL -> "resistiu com deslocamento minimo";
            case GLASS -> "vibrou sem quebrar";
            case WOOD -> "arrastou com atrito";
            case STONE -> "moveu pouco pela alta massa";
        };
    }

    private double[] computeTargetDistances(int n) {
        double[] d = new double[n];
        double step = 24.0 / (n + 1);
        for (int i = 0; i < n; i++) d[i] = step * (i + 1);
        return d;
    }

    private void updatePreviewTrajectory() {
        if (previewMarkers.isEmpty()) return;
        double speed = parameters.getValue("speed", 15);
        double angle = Math.toRadians(parameters.getValue("angle", 45));
        double gravity = Math.max(0.01, effectiveGravity());
        double vx = speed * Math.cos(angle);
        double vy = speed * Math.sin(angle);
        double floor = context.getPhysics().getFloorY() + 0.11;
        double maxTime = Math.max(0.7, 2.0 * Math.max(0.1, vy) / gravity);
        for (int i = 0; i < previewMarkers.size(); i++) {
            double t = maxTime * (i + 1.0) / (previewMarkers.size() + 1.0);
            double x = launchPos.x + 0.25 + vx * t;
            double y = launchPos.y + 0.55 + vy * t - 0.5 * gravity * t * t;
            SceneObject marker = previewMarkers.get(i);
            marker.setVisible(y > floor && x < 17.5);
            marker.getBody().setPosition(new Vec3(x, Math.max(floor, y), 0));
        }
    }

    @Override
    public String telemetry() {
        MaterialState projectileMaterial = projectileMaterial();
        MediumState medium = medium();
        double radius = parameters.getValue("projectile_radius", 0.22);
        double mass = projectileMaterial.massForSphere(radius);
        double speed = projectileVelocity.length();
        double reynolds = medium.reynolds(speed, radius * 2.0);
        double dragCoefficient = medium.dragCoefficient(reynolds) * medium.compressibilityFactor(speed);
        return String.format(
            "Projetil de %s em %s%n%nVOO%nTempo voo: %.2f s%nAlvos atingidos: %d de %d%nAlcance teorico no vacuo: %.1f m%nUltimo estado: %s%n%nIMPACTO%nEnergia: %.0f J%nDano: %.0f%%%nResposta material: %s%n%nMEIO%nMeio: %s%nPropriedades: %s%nReynolds atual: %s%nCoef. de arrasto: %.3f%nEmpuxo: %s%n%nMATERIAIS%nProjetil: %s (m=%.2f kg)%nPropriedades: %s%nAlvo: %s%nPropriedades: %s%nModo de falha: %s",
            projectileMaterial.label, medium.label,
            flightTime, targetsHit, targets.size(), getTheoreticalRange(), lastImpact,
            lastImpactEnergy, lastImpactDamage * 100.0, lastTargetResponse,
            medium.label, medium.describeProperties(),
            formatReynolds(reynolds), dragCoefficient, describeBuoyancy(medium, projectileMaterial),
            projectileMaterial.label, mass, projectileMaterial.describeProperties(),
            targetMaterial().label, targetMaterial().describeProperties(),
            targetMaterial().failureMode()
        );
    }

    /** Reynolds cobre varias ordens de grandeza; notacao cientifica quando cresce. */
    private static String formatReynolds(double reynolds) {
        if (reynolds <= 0) return "sem meio";
        if (reynolds < 1000) return String.format("%.1f (regime viscoso)", reynolds);
        if (reynolds < 2e5) return String.format("%.2e (regime de Newton)", reynolds);
        return String.format("%.2e (pos-crise do arrasto)", reynolds);
    }

    /** Explica o efeito do empuxo sobre este projetil neste meio. */
    private static String describeBuoyancy(MediumState medium, MaterialState material) {
        if (medium.densityKgM3 <= 0) return "nenhum (vacuo)";
        double factor = medium.buoyancyFactorFor(material.densityKgM3);
        if (medium.floats(material.densityKgM3)) {
            return String.format("flutua: peso efetivo %.0f%% e sobe", factor * 100.0);
        }
        return String.format("afunda com %.0f%% do peso", factor * 100.0);
    }

    public double getFlightTime() { return flightTime; }
    public int getTargetsHit() { return targetsHit; }
    public int getTotalTargets() { return targets.size(); }
    public List<Vec3> getTrajectory() { return List.copyOf(trajectory); }
    public boolean isLaunched() { return launched; }

    public double getTheoreticalRange() {
        double v = parameters.getValue("speed", 15);
        double a = Math.toRadians(parameters.getValue("angle", 45));
        double g = parameters.getValue("gravity", 9.81);
        return g > 0.01 ? v * v * Math.sin(2 * a) / g : 0;
    }

    private MaterialState projectileMaterial() {
        return MaterialState.byIndex(parameters.getValue("projectile_material", MaterialState.STONE.ordinal()));
    }

    private MaterialState targetMaterial() {
        return MaterialState.byIndex(parameters.getValue("target_material", MaterialState.GELATIN.ordinal()));
    }

    private MediumState medium() {
        return MediumState.byIndex(parameters.getValue("medium", MediumState.AIR.ordinal()));
    }

    @Override
    public LabMeasurement sampleLabMeasurement() {
        double height = 0;
        double speed = projectileVelocity.length();
        if (projectile != null) {
            height = Math.max(0, projectile.getBody().getPosition().y - context.getPhysics().getFloorY()
                - projectile.getBody().getBoundingRadius());
        }
        return new LabMeasurement(
            "projetil",
            "altura", "m", height,
            "velocidade", "m/s", speed,
            String.format("voo=%.2f s | altura=%.2f m | v=%.2f m/s | alvos=%d/%d",
                flightTime, height, speed, targetsHit, targets.size())
        );
    }
}
