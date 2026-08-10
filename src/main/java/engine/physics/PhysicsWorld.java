package engine.physics;

import engine.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Mundo fisico: integra todos os corpos rigidos e resolve as colisoes.
 *
 * <h2>Por que passo fixo de 120 Hz</h2>
 * O tempo real entre frames varia (queda de FPS, janela minimizada, maquina
 * lenta). Se a integracao usasse esse dt variavel, o mesmo experimento daria
 * resultados diferentes em cada computador — inaceitavel num simulador
 * educacional. A solucao e o <i>acumulador</i>: o tempo real entra em
 * {@link #accumulator} e a fisica avanca sempre em fatias iguais de
 * {@value #FIXED_DT} s. O que sobra fica guardado para o proximo frame.
 *
 * <p>O teto de {@value #MAX_SUBSTEPS} sub-passos evita a "espiral da morte":
 * se um frame demorar demais, a fisica processa no maximo 8 fatias e descarta
 * o excesso, em vez de tentar recuperar o atraso e travar de vez.
 *
 * <h2>Deteccao de colisao</h2>
 * Fase ampla por esfera envolvente: dois corpos so sao testados a fundo se a
 * distancia entre seus centros for menor que a soma dos raios. A comparacao usa
 * distancia ao quadrado para evitar a raiz quadrada no laco interno.
 */
public class PhysicsWorld {

    /** Fatias de integracao por frame; limita o custo quando ha engasgo de FPS. */
    private static final int MAX_SUBSTEPS = 8;

    /** Passo fixo de integracao: 120 Hz da estabilidade sem custar caro. */
    private static final double FIXED_DT = 1.0 / 120.0;

    /** Meia largura da arena (paredes invisiveis em +/- este valor em X e Z). */
    private static final double ARENA_HALF = 18.0;

    /** Altura do chao da cena, alinhada com a grade desenhada pelo renderer. */
    private static final double FLOOR_Y = -5.0;

    private final List<RigidBody> bodies = new ArrayList<>();
    private Vec3 gravity = new Vec3(0, -12.0, 0);
    private boolean gravityEnabled = true;
    private double accumulator;

    private int collisionChecks;
    private int activeObjects;

    public void addBody(RigidBody body) { bodies.add(body); }

    public void removeBody(RigidBody body) { bodies.remove(body); }

    public void clear() {
        bodies.clear();
        accumulator = 0;
        collisionChecks = 0;
        activeObjects = 0;
    }

    /**
     * Avanca a simulacao consumindo o tempo real em fatias de passo fixo.
     *
     * @param deltaTime tempo real desde o ultimo frame, em segundos. Valores
     *                  acima de 0,25 s (janela minimizada, debug pausado) sao
     *                  truncados: sem isso o acumulador estouraria e a cena
     *                  daria um salto enorme ao voltar.
     */
    public void update(double deltaTime) {
        // Um dt nao finito ou negativo envenenaria o acumulador: NaN nunca
        // satisfaz `>= FIXED_DT`, entao o laco pararia de rodar para sempre e a
        // fisica morreria em silencio, sem excecao nem sinal na tela.
        if (!Double.isFinite(deltaTime) || deltaTime <= 0) return;

        accumulator += Math.min(deltaTime, 0.25);

        int substeps = 0;
        while (accumulator >= FIXED_DT && substeps < MAX_SUBSTEPS) {
            step(FIXED_DT);
            accumulator -= FIXED_DT;
            substeps++;
        }
    }

    /**
     * Um sub-passo completo: integra, resolve o ambiente e depois os pares.
     *
     * <p>A ordem importa. Integrar todos os corpos primeiro garante que a
     * resolucao de colisao veja um estado consistente do mundo; resolver chao e
     * paredes antes dos pares evita que um corpo seja empurrado para dentro do
     * piso por uma colisao e so saia no frame seguinte.
     */
    private void step(double dt) {
        Vec3 effectiveGravity = gravityEnabled ? gravity : Vec3.ZERO;

        activeObjects = 0;
        for (RigidBody body : bodies) {
            if (body.getType() != RigidBody.PhysicsType.DYNAMIC) continue;
            body.integrate(dt, effectiveGravity);
            if (!body.isSleeping()) activeObjects++;
        }

        for (RigidBody body : bodies) {
            if (body.getType() != RigidBody.PhysicsType.DYNAMIC) continue;
            body.resolveFloorCollision(FLOOR_Y, dt);
            body.resolveWallCollision(-ARENA_HALF, ARENA_HALF, -ARENA_HALF, ARENA_HALF);
        }

        collisionChecks = 0;
        int n = bodies.size();
        for (int i = 0; i < n; i++) {
            RigidBody a = bodies.get(i);
            if (a.getType() == RigidBody.PhysicsType.STATIC) continue;

            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                RigidBody b = bodies.get(j);

                // Dynamic pairs are checked once; static bodies are checked only
                // from the movable side so large decorative scenes stay cheap.
                if (b.getType() != RigidBody.PhysicsType.STATIC && j <= i) continue;
                if (a.isSleeping() && b.isSleeping()) continue;

                double minDist = a.getBoundingRadius() + b.getBoundingRadius();
                if (a.getPosition().distanceSqTo(b.getPosition()) <= minDist * minDist) {
                    collisionChecks++;
                    RigidBody.resolveSphereSphere(a, b);
                }
            }
        }
    }

    public void explodeAt(Vec3 center, double strength) {
        bodies.forEach(body -> body.applyExplosion(center, strength));
    }

    public void setGravityEnabled(boolean enabled) { this.gravityEnabled = enabled; }
    public boolean isGravityEnabled() { return gravityEnabled; }
    public Vec3 getGravityVector() { return gravityEnabled ? gravity : Vec3.ZERO; }

    public void setGravityStrength(double g) {
        gravity = new Vec3(0, -Math.abs(g), 0);
    }

    public List<RigidBody> getBodies() { return bodies; }
    public int getBodyCount() { return bodies.size(); }
    public int getActiveObjects() { return activeObjects; }
    public int getCollisionChecks() { return collisionChecks; }
    public double getFloorY() { return FLOOR_Y; }
    public double getArenaHalf() { return ARENA_HALF; }
}
