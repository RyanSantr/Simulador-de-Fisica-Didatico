package engine.simulation;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.physics.RigidBody;
import engine.renderer.Mesh;
import engine.scene.ObjectFactory;
import engine.scene.SceneObject;

/**
 * Helper fluente para construção de cenas de simulação.
 *
 * Encapsula a criação de SceneObject + RigidBody + adição ao contexto,
 * reduzindo o boilerplate nos módulos e centralizando parâmetros físicos.
 *
 * Uso típico num módulo:
 * <pre>
 *   ModuleSceneBuilder builder = new ModuleSceneBuilder(context);
 *   builder.sphere(0.5, ColorRGBA.BLUE)
 *          .at(0, 10, 0)
 *          .withVelocity(5, 0, 0)
 *          .bouncy()
 *          .add();
 * </pre>
 */
public final class ModuleSceneBuilder {

    private final SimulationContext context;

    public ModuleSceneBuilder(SimulationContext context) {
        this.context = context;
    }

    // ── Objeto em construção (padrão Builder) ──────
    private Mesh       mesh;
    private Vec3       position     = Vec3.ZERO;
    private Vec3       velocity     = Vec3.ZERO;
    private Vec3       angularVel   = Vec3.ZERO;
    private Vec3       rotation     = Vec3.ZERO;
    private double     mass         = 1.0;
    private double     restitution  = 0.35;
    private double     friction     = 0.55;
    private double     linearDamping = 0.995;
    private double     angularDamping = 0.88;
    private double     boundRadius  = 0.5;
    private ColorRGBA  color        = ColorRGBA.BLUE;
    private String     name         = "obj";
    private String     shapeType    = "custom";
    private RigidBody.PhysicsType physicsType = RigidBody.PhysicsType.DYNAMIC;

    // ── Formas geométricas ─────────────────────────

    public ModuleSceneBuilder sphere(double radius, ColorRGBA c) {
        mesh = Mesh.createSphere(radius, 12, 16);
        boundRadius = radius; color = c; shapeType = "SPHERE"; mass = radius * 2;
        return this;
    }

    public ModuleSceneBuilder sphere(double radius, int stacks, int slices, ColorRGBA c) {
        mesh = Mesh.createSphere(radius, Math.max(4, stacks), Math.max(6, slices));
        boundRadius = radius; color = c; shapeType = "SPHERE"; mass = radius * 2;
        return this;
    }

    public ModuleSceneBuilder box(double w, double h, double d, ColorRGBA c) {
        mesh = Mesh.createBox(w/2, h/2, d/2);
        boundRadius = Math.sqrt(w*w + h*h + d*d) / 2;
        color = c; shapeType = "BOX"; mass = w * h * d * 0.8;
        return this;
    }

    public ModuleSceneBuilder cone(double radius, double height, ColorRGBA c) {
        mesh = Mesh.createCone(radius, height, 8);
        boundRadius = Math.max(radius, height / 2);
        color = c; shapeType = "CONE"; mass = radius * height;
        return this;
    }

    public ModuleSceneBuilder cylinder(double radius, double height, ColorRGBA c) {
        mesh = Mesh.createCylinder(radius, height, 10);
        boundRadius = Math.max(radius, height / 2);
        color = c; shapeType = "CYLINDER"; mass = radius * height * 1.5;
        return this;
    }

    public ModuleSceneBuilder torus(double outerRadius, double tubeRadius, ColorRGBA c) {
        mesh = Mesh.createTorus(outerRadius, tubeRadius, 8, 26);
        boundRadius = outerRadius + tubeRadius;
        color = c; shapeType = "TORUS"; mass = outerRadius * tubeRadius * 2;
        return this;
    }

    public ModuleSceneBuilder icosahedron(double radius, ColorRGBA c) {
        mesh = Mesh.createIcosahedron(radius);
        boundRadius = radius; color = c; shapeType = "ICOSAHEDRON"; mass = radius * 3;
        return this;
    }

    /**
     * Usa uma malha ja construida — para geometrias compostas que nao cabem
     * numa primitiva, como o casco e as aletas do foguete.
     *
     * @param custom      malha pronta
     * @param boundRadius raio envolvente, usado por colisao e pelos testes de
     *                    visibilidade do renderer
     */
    public ModuleSceneBuilder mesh(Mesh custom, double boundRadius, ColorRGBA c) {
        this.mesh = custom;
        this.boundRadius = boundRadius;
        color = c; shapeType = "CUSTOM"; mass = Math.max(0.1, boundRadius);
        return this;
    }

    // ── Posição e cinemática ───────────────────────

    public ModuleSceneBuilder at(double x, double y, double z) {
        position = new Vec3(x, y, z); return this;
    }

    public ModuleSceneBuilder at(Vec3 pos) {
        position = pos; return this;
    }

    public ModuleSceneBuilder withVelocity(double vx, double vy, double vz) {
        velocity = new Vec3(vx, vy, vz); return this;
    }

    public ModuleSceneBuilder withVelocity(Vec3 v) {
        velocity = v; return this;
    }

    public ModuleSceneBuilder withAngularVelocity(double ax, double ay, double az) {
        angularVel = new Vec3(ax, ay, az); return this;
    }

    public ModuleSceneBuilder withRotation(double rx, double ry, double rz) {
        rotation = new Vec3(rx, ry, rz); return this;
    }

    // ── Propriedades físicas ───────────────────────

    public ModuleSceneBuilder rigid()    { restitution = 0.30; physicsType = RigidBody.PhysicsType.DYNAMIC; return this; }
    public ModuleSceneBuilder bouncy()   { restitution = 0.85; physicsType = RigidBody.PhysicsType.DYNAMIC; return this; }
    public ModuleSceneBuilder isStatic() { physicsType = RigidBody.PhysicsType.STATIC; return this; }
    public ModuleSceneBuilder heavy()    { mass *= 5; restitution = 0.10; return this; }
    public ModuleSceneBuilder light()    { mass *= 0.2; restitution = 0.65; return this; }

    public ModuleSceneBuilder withMass(double m)        { mass = m; return this; }
    public ModuleSceneBuilder withRestitution(double r) { restitution = r; return this; }
    public ModuleSceneBuilder withMaterial(double restitution, double friction,
                                           double linearDamping, double angularDamping) {
        this.restitution = restitution;
        this.friction = friction;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
        return this;
    }

    // ── Nome ───────────────────────────────────────

    public ModuleSceneBuilder named(String n) { name = n; return this; }

    // ── Construção final ───────────────────────────

    /**
     * Constrói o objeto, adiciona ao contexto e retorna a instância.
     * Reinicia o builder para reutilização.
     */
    public SceneObject add() {
        if (mesh == null) throw new IllegalStateException("Forma geométrica não definida.");

        RigidBody body = new RigidBody(position, Math.max(0.01, mass), restitution,
            friction, linearDamping, angularDamping, physicsType);
        body.setBoundingRadius(boundRadius);
        body.setRotation(rotation);

        if (physicsType == RigidBody.PhysicsType.DYNAMIC) {
            body.setVelocity(velocity);
            body.setAngularVelocity(angularVel);
        }

        SceneObject obj = new SceneObject(name, mesh, body, color, shapeType);
        context.addObject(obj);

        // Reset para próximo uso
        resetState();
        return obj;
    }

    private void resetState() {
        mesh = null; position = Vec3.ZERO; velocity = Vec3.ZERO;
        angularVel = Vec3.ZERO; rotation = Vec3.ZERO;
        mass = 1.0; restitution = 0.35; friction = 0.55;
        linearDamping = 0.995; angularDamping = 0.88; boundRadius = 0.5;
        color = ColorRGBA.BLUE; name = "obj"; shapeType = "custom";
        physicsType = RigidBody.PhysicsType.DYNAMIC;
    }

    // ── Fábrica para objetos do ObjectFactory ──────

    /** Cria e adiciona um objeto usando o ObjectFactory padrão. */
    public SceneObject addFromFactory(ObjectFactory.ShapeType shape,
                                      ObjectFactory.PhysicsPreset preset,
                                      ColorRGBA color, double spawnY) {
        SceneObject obj = ObjectFactory.create(shape, preset, color, 0.3, 0.3, spawnY);
        context.addObject(obj);
        return obj;
    }
}
