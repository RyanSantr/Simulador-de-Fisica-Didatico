package engine.simulation.modules;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.scene.SceneObject;
import engine.simulation.LabMeasurement;
import engine.simulation.ModuleSceneBuilder;
import engine.simulation.SimulationModule;
import engine.simulation.parameters.Parameter;

/**
 * Plano inclinado com atrito usando a componente da gravidade na rampa.
 */
public class InclinedPlaneModule extends SimulationModule {

    private SceneObject block;
    private Vec3 top;
    private Vec3 downSlope;
    private Vec3 surfaceNormal;
    private double travelled;
    private double speed;
    private double acceleration;
    private double rampLength;
    private boolean stopped;

    public InclinedPlaneModule() {
        super("inclined_plane", "Plano Inclinado",
            "Compare gravidade, angulo e atrito no deslizamento por uma rampa.",
            "Mecanica - Forcas e Atrito");
    }

    @Override
    protected void declareParameters() {
        parameters.add(Parameter.of("angle", "Angulo da rampa", 28)
            .range(5, 65).limits(0, 90).unit("graus").step(1)
            .reference("rampa acessivel 4,8 | escada residencial 30 | pista de esqui 45 graus")
            .outOfRangeNote("Em 0 grau nao ha componente de peso na rampa; em 90 vira queda livre."));
        parameters.add(Parameter.of("friction", "Atrito cinetico", 0.18)
            .range(0, 0.95).limits(0, 5).unit("").step(0.01)
            .reference("gelo 0,03 | teflon 0,04 | aco-aco 0,6 | borracha-asfalto 0,9 | silicone 1,2")
            .outOfRangeNote("Coeficientes acima de 1 existem (borracha macia), mas sao raros."));
        parameters.add(Parameter.of("gravity", "Gravidade", 9.81)
            .range(0, 30).limits(0, 1e6).unit("m/s2").step(0.1)
            .reference("Lua 1,62 | Marte 3,71 | Terra 9,81 | Jupiter 24,79 | Sol 274 m/s2"));
        parameters.add(Parameter.of("ramp_length", "Comprimento", 9.0)
            .range(3, 14).limits(0.1, 1e5).unit("m").step(0.5)
            .outOfRangeNote("Rampas muito longas saem do enquadramento da cena."));
    }

    @Override
    protected void buildScene() {
        ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
        context.setGravityStrength(0);
        double angle = Math.toRadians(parameters.getValue("angle", 28));
        rampLength = parameters.getValue("ramp_length", 9.0);
        downSlope = new Vec3(Math.cos(angle), -Math.sin(angle), 0);
        surfaceNormal = new Vec3(Math.sin(angle), Math.cos(angle), 0);
        double floorY = context.getPhysics().getFloorY();
        double rampHalfThickness = 0.12;
        double blockHalfSide = 0.375;
        Vec3 lowSurface = new Vec3(rampLength * 0.42, floorY + 0.18, 0);
        Vec3 surfaceCenter = lowSurface.sub(downSlope.mul(rampLength * 0.5));
        Vec3 center = surfaceCenter.sub(surfaceNormal.mul(rampHalfThickness));
        top = surfaceCenter.sub(downSlope.mul(rampLength * 0.5))
            .add(surfaceNormal.mul(blockHalfSide + 0.025));

        SceneObject ramp = builder.box(rampLength, 0.24, 2.4, ColorRGBA.fromHex("#64748b"))
            .at(center)
            .withRotation(0, 0, -angle)
            .isStatic()
            .named("rampa")
            .add();
        builder.box(rampLength, 0.08, 0.08, new ColorRGBA(0.36, 0.82, 0.85, 0.62))
            .at(surfaceCenter.add(surfaceNormal.mul(0.055)).add(new Vec3(0, 0, -1.18)))
            .withRotation(0, 0, -angle)
            .isStatic()
            .named("guarda_rampa_a")
            .add()
            .setShadowCaster(false);
        builder.box(rampLength, 0.08, 0.08, new ColorRGBA(0.36, 0.82, 0.85, 0.62))
            .at(surfaceCenter.add(surfaceNormal.mul(0.055)).add(new Vec3(0, 0, 1.18)))
            .withRotation(0, 0, -angle)
            .isStatic()
            .named("guarda_rampa_b")
            .add()
            .setShadowCaster(false);

        block = builder.box(0.75, 0.75, 0.75, ColorRGBA.fromHex("#f59e0b"))
            .at(top)
            .withRotation(0, 0, -angle)
            .isStatic()
            .named("bloco_rampa")
            .add();
        block.setRenderLayer(1);
        builder.box(0.08, Math.max(0.8, top.y - floorY), 0.08, ColorRGBA.fromHex("#93c5fd"))
            .at(top.x - 0.32, floorY + Math.max(0.8, top.y - floorY) * 0.5, -1.48)
            .isStatic()
            .named("marcador_topo")
            .add();
        for (int i = 1; i <= 4; i++) {
            builder.sphere(0.07, new ColorRGBA(1.0, 0.86, 0.32, 0.82))
                .at(surfaceCenter.sub(downSlope.mul(rampLength * 0.5))
                    .add(downSlope.mul(rampLength * i / 5.0))
                    .add(surfaceNormal.mul(0.08))
                    .add(new Vec3(0, 0, -0.92)))
                .isStatic()
                .named("marca_distancia_rampa_" + i)
                .add()
                .setShadowCaster(false);
        }

        travelled = 0;
        speed = 0;
        stopped = false;
        released = false;
        acceleration = slopeAcceleration(angle);
    }

    /**
     * O bloco fica retido no alto da rampa ate o comando de soltar, o que
     * permite ajustar angulo e atrito antes de ver o resultado.
     */
    private boolean released;

    @Override
    public String launchActionLabel() {
        return released ? "Soltar novamente" : "Soltar o bloco";
    }

    @Override
    public void launch() {
        if (released) onReset();
        released = true;
    }

    @Override
    public void onUpdate(double dt) {
        if (block == null || stopped || !released) return;
        double angle = Math.toRadians(parameters.getValue("angle", 28));
        acceleration = slopeAcceleration(angle);
        speed = Math.max(0, speed + acceleration * dt);
        travelled = Math.min(rampLength - 0.55, travelled + speed * dt);
        block.getBody().setPosition(top.add(downSlope.mul(travelled)));
        block.getBody().setVelocity(downSlope.mul(speed));
        if (travelled >= rampLength - 0.55 || acceleration <= 0 && speed < 1e-5) {
            stopped = true;
        }
    }

    @Override
    public void onParameterChanged(String paramName, double newValue) {
        onReset();
    }

    @Override
    public String telemetry() {
        return String.format(
            "Plano inclinado | %s%n%nMEDIDAS%nPercurso: %.2f m%nVelocidade: %.2f m/s%nAceleracao: %.2f m/s2%n%nCONFIGURACAO%nAngulo: %.1f graus%nCoeficiente de atrito: %.2f%n%nMODELO%n> Forca paralela: m.g.sen(theta)%n> Atrito: mu.N = mu.m.g.cos(theta)",
            acceleration <= 0 ? "atrito segura o bloco" : stopped ? "fim da rampa" : "deslizando",
            travelled, speed, Math.max(0, acceleration),
            parameters.getValue("angle", 30.0), parameters.getValue("friction", 0.18)
        );
    }

    private double slopeAcceleration(double angle) {
        if (!context.getPhysics().isGravityEnabled()) return 0;
        double gravity = parameters.getValue("gravity", 9.81);
        double friction = parameters.getValue("friction", 0.18);
        return Math.max(0, gravity * (Math.sin(angle) - friction * Math.cos(angle)));
    }

    @Override
    public LabMeasurement sampleLabMeasurement() {
        return new LabMeasurement(
            "plano inclinado",
            "percurso", "m", travelled,
            "velocidade", "m/s", speed,
            String.format("s=%.2f m | v=%.2f m/s | a=%.2f m/s2",
                travelled, speed, Math.max(0, acceleration))
        );
    }
}
