package engine.physics;

import engine.math.Vec3;

/**
 * Corpo rigido: estado cinematico + propriedades de material de um objeto.
 *
 * <h2>Integracao (Euler semi-implicito)</h2>
 * A cada passo a velocidade e atualizada <b>antes</b> da posicao:
 * <pre>
 *   v += (F/m) * dt
 *   x += v * dt        &lt;- ja usa o v novo
 * </pre>
 * Essa ordem (semi-implicito, ou "simpletico") e o que mantem a energia
 * estavel em orbitas e osciladores. O Euler explicito, que usa o v antigo na
 * posicao, injeta energia a cada passo e faria um pendulo abrir sozinho.
 *
 * <h2>Sistema de sono</h2>
 * Corpos praticamente parados por {@value #SLEEP_TIME} s entram em modo
 * "dormindo" e param de ser integrados, ate serem tocados por uma colisao ou
 * pela API. Isso evita o tremor de objetos empilhados e economiza CPU em cenas
 * com muitos corpos em repouso.
 *
 * <h2>Amortecimento independente de FPS</h2>
 * O fator de damping e elevado a {@code dt * 120}, entao a perda de velocidade
 * por segundo e a mesma rodando a 30, 60 ou 144 FPS.
 */
public class RigidBody {

    // ── Estado cinemático ──────────────────────────
    private Vec3 position;
    private Vec3 velocity;
    private Vec3 angularVelocity;   // rotação por segundo (rad/s)
    private Vec3 rotation;          // ângulos de Euler acumulados (rad)
    private Vec3 force;             // força acumulada no frame

    // ── Propriedades físicas ───────────────────────
    private double mass;
    private double inverseMass;
    private final double restitution;   // 0 = inelástico, 1 = perfeitamente elástico
    private final double friction;
    private final double linearDamping;
    private final double angularDamping;
    private final PhysicsType type;

    // ── Forma para colisão ─────────────────────────
    private double boundingRadius;

    // ── Estado ────────────────────────────────────
    private boolean sleeping;
    private double sleepTimer;
    private static final double SLEEP_THRESHOLD = 0.05;
    private static final double SLEEP_TIME = 0.8;

    public enum PhysicsType {
        DYNAMIC,   // Afetado por física
        KINEMATIC, // Move por código, não por física
        STATIC     // Completamente imóvel
    }

    public RigidBody(Vec3 position, double mass, double restitution, PhysicsType type) {
        this(position, mass, restitution, 0.55, 0.995, 0.88, type);
    }

    public RigidBody(Vec3 position, double mass, double restitution,
                     double friction, double linearDamping,
                     double angularDamping, PhysicsType type) {
        this.position        = position;
        this.velocity        = Vec3.ZERO;
        this.angularVelocity = Vec3.ZERO;
        this.rotation        = Vec3.ZERO;
        this.force           = Vec3.ZERO;
        this.mass            = mass;
        this.inverseMass     = (type == PhysicsType.STATIC) ? 0 : 1.0 / mass;
        this.restitution     = clamp(restitution, 0.0, 1.0);
        this.friction        = clamp(friction, 0.0, 1.0);
        this.linearDamping   = clamp(linearDamping, 0.90, 1.0);
        this.angularDamping  = clamp(angularDamping, 0.70, 1.0);
        this.type            = type;
        this.boundingRadius  = 1.0;
        this.sleeping        = false;
        this.sleepTimer      = 0;
    }

    // ── Integração (Euler semi-implícito) ──────────
    public void integrate(double dt, Vec3 gravity) {
        if (type != PhysicsType.DYNAMIC || sleeping) return;

        // Aplicar gravidade
        Vec3 totalForce = force.add(gravity.mul(mass));

        // Integrar velocidade linear
        velocity = velocity.add(totalForce.mul(inverseMass * dt));
        velocity = velocity.mul(Math.pow(linearDamping, dt * 120.0));

        // Integrar posição
        position = position.add(velocity.mul(dt));

        // Integrar velocidade angular
        angularVelocity = angularVelocity.mul(Math.pow(angularDamping, dt * 120.0));
        rotation = rotation.add(angularVelocity.mul(dt));

        // Reset força acumulada
        force = Vec3.ZERO;

        // Verificar sleep
        updateSleep(dt);
    }

    private void updateSleep(double dt) {
        double kinetic = velocity.lengthSq() + angularVelocity.lengthSq();
        if (kinetic < SLEEP_THRESHOLD) {
            sleepTimer += dt;
            if (sleepTimer > SLEEP_TIME) {
                sleeping = true;
                velocity = Vec3.ZERO;
                angularVelocity = Vec3.ZERO;
            }
        } else {
            sleepTimer = 0;
            sleeping = false;
        }
    }

    // ── Colisão com plano infinito (normal apontando para cima) ──
    public void resolveFloorCollision(double floorY, double dt) {
        if (type != PhysicsType.DYNAMIC) return;
        double bottom = position.y - boundingRadius;
        if (bottom < floorY) {
            // Separar
            position = new Vec3(position.x, floorY + boundingRadius, position.z);

            // Impulso normal
            if (velocity.y < 0) {
                double impulse = -(1 + restitution) * velocity.y;
                // Aplicar somente se acima de threshold (evita jitter)
                if (Math.abs(impulse) < 0.15) impulse = 0;
                velocity = new Vec3(velocity.x, velocity.y + impulse, velocity.z);
            }

            double tangentialDamping = Math.max(0.0, 1.0 - friction * 2.2 * dt);
            velocity = new Vec3(velocity.x * tangentialDamping, velocity.y, velocity.z * tangentialDamping);
            sleeping = false;
            sleepTimer = 0;
        }
    }

    // ── Colisão com paredes (AABB infinito) ────────
    public void resolveWallCollision(double minX, double maxX, double minZ, double maxZ) {
        if (type != PhysicsType.DYNAMIC) return;
        double r = boundingRadius;

        if (position.x - r < minX) {
            position = new Vec3(minX + r, position.y, position.z);
            velocity = new Vec3(Math.abs(velocity.x) * restitution, velocity.y, velocity.z);
        } else if (position.x + r > maxX) {
            position = new Vec3(maxX - r, position.y, position.z);
            velocity = new Vec3(-Math.abs(velocity.x) * restitution, velocity.y, velocity.z);
        }

        if (position.z - r < minZ) {
            position = new Vec3(position.x, position.y, minZ + r);
            velocity = new Vec3(velocity.x, velocity.y, Math.abs(velocity.z) * restitution);
        } else if (position.z + r > maxZ) {
            position = new Vec3(position.x, position.y, maxZ - r);
            velocity = new Vec3(velocity.x, velocity.y, -Math.abs(velocity.z) * restitution);
        }
    }

    // ── Colisão esfera-esfera ──────────────────────
    public static void resolveSphereSphere(RigidBody a, RigidBody b) {
        if (a.type == PhysicsType.STATIC && b.type == PhysicsType.STATIC) return;
        if (a.sleeping && b.sleeping) return;

        Vec3 delta = b.position.sub(a.position);
        double distSq = delta.lengthSq();
        double minDist = a.boundingRadius + b.boundingRadius;

        if (distSq >= minDist * minDist || distSq < 1e-8) return;

        double dist = Math.sqrt(distSq);
        Vec3 normal = delta.div(dist);

        // Penetração
        double penetration = minDist - dist;

        // Separar (massa inversamente proporcional)
        double totalInvMass = a.inverseMass + b.inverseMass;
        if (totalInvMass < 1e-10) return;

        Vec3 correction = normal.mul(penetration / totalInvMass * 1.02);
        if (a.type == PhysicsType.DYNAMIC) a.position = a.position.sub(correction.mul(a.inverseMass));
        if (b.type == PhysicsType.DYNAMIC) b.position = b.position.add(correction.mul(b.inverseMass));

        // Velocidade relativa ao longo da normal
        Vec3 relVel = b.velocity.sub(a.velocity);
        double velAlongNormal = relVel.dot(normal);
        if (velAlongNormal > 0) return; // já separando

        double e = Math.min(a.restitution, b.restitution);
        double j = -(1 + e) * velAlongNormal / totalInvMass;

        Vec3 impulse = normal.mul(j);
        if (a.type == PhysicsType.DYNAMIC) {
            a.velocity = a.velocity.sub(impulse.mul(a.inverseMass));
            a.sleeping = false; a.sleepTimer = 0;
        }
        if (b.type == PhysicsType.DYNAMIC) {
            b.velocity = b.velocity.add(impulse.mul(b.inverseMass));
            b.sleeping = false; b.sleepTimer = 0;
        }

        // Torque por colisão (spin)
        if (a.type == PhysicsType.DYNAMIC)
            a.angularVelocity = a.angularVelocity.add(Vec3.random().mul(2.5));
        if (b.type == PhysicsType.DYNAMIC)
            b.angularVelocity = b.angularVelocity.add(Vec3.random().mul(2.5));
    }

    // ── Aplicar forças externas ────────────────────
    public void applyImpulse(Vec3 impulse) {
        if (type != PhysicsType.DYNAMIC) return;
        velocity = velocity.add(impulse.mul(inverseMass));
        sleeping = false;
        sleepTimer = 0;
    }

    public void applyExplosion(Vec3 center, double strength) {
        if (type != PhysicsType.DYNAMIC) return;
        Vec3 dir = position.sub(center);
        double dist = dir.length();
        if (dist < 0.01) return;
        double forceMag = strength / (dist * dist + 1);
        applyImpulse(dir.normalize().mul(forceMag));
        angularVelocity = Vec3.random().mul(strength * 0.5 / (dist + 1));
        sleeping = false;
    }

    // ── Getters / Setters ──────────────────────────
    public Vec3 getPosition()        { return position; }
    public Vec3 getVelocity()        { return velocity; }
    public Vec3 getRotation()        { return rotation; }
    public Vec3 getAngularVelocity() { return angularVelocity; }
    public double getBoundingRadius(){ return boundingRadius; }
    public PhysicsType getType()     { return type; }
    public boolean isSleeping()      { return sleeping; }
    public double getMass()          { return mass; }
    public double getRestitution()   { return restitution; }
    public double getFriction()      { return friction; }
    public double getLinearDamping() { return linearDamping; }
    public double getAngularDamping(){ return angularDamping; }

    public void setPosition(Vec3 p)        { this.position = p; sleeping = false; }
    public void setVelocity(Vec3 v)        { this.velocity = v; sleeping = false; }
    public void setRotation(Vec3 r)        { this.rotation = r == null ? Vec3.ZERO : r; }
    public void setAngularVelocity(Vec3 v) { this.angularVelocity = v; }
    public void setBoundingRadius(double r){ this.boundingRadius = r; }
    public void setMass(double mass) {
        if (type == PhysicsType.STATIC) return;
        this.mass = Math.max(0.01, mass);
        this.inverseMass = 1.0 / this.mass;
        wake();
    }
    public void wake()                     { sleeping = false; sleepTimer = 0; }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
