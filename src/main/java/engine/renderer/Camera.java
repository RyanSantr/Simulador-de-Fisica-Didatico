package engine.renderer;

import engine.math.Mat4;
import engine.math.Vec3;

/**
 * Camera 3D orbital com projecao perspectiva.
 *
 * <p>A posicao nao e guardada em XYZ, e sim em <b>coordenadas esfericas</b> ao
 * redor de um ponto alvo: {@code theta} (azimute), {@code phi} (elevacao) e
 * {@code radius} (distancia). Isso torna os controles naturais — arrastar o
 * mouse mexe em dois angulos, o scroll mexe no raio — e impede que a camera
 * "role" de lado ou passe do polo, ja que {@code phi} e limitado.
 *
 * <p>O modo livre (WASD/QE) desloca camera e alvo juntos pelo referencial
 * horizontal da visao atual, entao continuar orbitando depois funciona como
 * esperado.
 */
public class Camera {

    // ── Controle orbital ───────────────────────────
    private double theta   = 0.35;   // azimute (horizontal)
    private double phi     = 1.15;   // elevação (vertical)
    private double radius  = 22.0;   // distância do alvo

    // Limites
    private static final double MIN_RADIUS = 3.0;
    private static final double MAX_RADIUS = 60.0;
    private static final double MIN_PHI    = 0.05;
    private static final double MAX_PHI    = Math.PI - 0.05;

    // ── Ponto alvo da órbita ───────────────────────
    private Vec3 target = Vec3.ZERO;

    // ── Projeção ───────────────────────────────────
    private double fovY   = Math.toRadians(60);
    private double aspect = 1.0;
    private double near   = 0.1;
    private double far    = 300.0;

    // ── Posição calculada ──────────────────────────
    private Vec3 position = Vec3.ZERO;

    // ── Matrizes ──────────────────────────────────
    private Mat4 viewMatrix       = Mat4.identity();
    private Mat4 projMatrix       = Mat4.identity();
    private Mat4 viewProjMatrix   = Mat4.identity();

    public Camera() { updateMatrices(); }

    // ── Controle de órbita ─────────────────────────
    public void orbit(double dTheta, double dPhi) {
        theta += dTheta;
        phi   = Math.max(MIN_PHI, Math.min(MAX_PHI, phi + dPhi));
        updateMatrices();
    }

    public void zoom(double delta) {
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius + delta));
        updateMatrices();
    }

    public void panTarget(double dx, double dy) {
        moveLocal(0, dx, dy);
    }

    /**
     * Translada camera e alvo pelo referencial horizontal da visao atual.
     * A orbita continua disponivel depois do deslocamento.
     */
    public void moveLocal(double forwardDistance, double rightDistance, double upDistance) {
        Vec3 forward = new Vec3(-Math.sin(theta), 0, -Math.cos(theta));
        Vec3 right = new Vec3(Math.cos(theta), 0, -Math.sin(theta));
        target = target
            .add(forward.mul(forwardDistance))
            .add(right.mul(rightDistance))
            .add(Vec3.UP.mul(upDistance));
        updateMatrices();
    }

    public void reset() {
        theta = 0.35; phi = 1.15; radius = 22.0;
        target = Vec3.ZERO;
        updateMatrices();
    }

    public void setOrbitView(double theta, double phi, double radius, Vec3 target) {
        this.theta = theta;
        this.phi = Math.max(MIN_PHI, Math.min(MAX_PHI, phi));
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        this.target = target == null ? Vec3.ZERO : target;
        updateMatrices();
    }

    public void setTarget(Vec3 target) {
        this.target = target == null ? Vec3.ZERO : target;
        updateMatrices();
    }

    // ── Atualização das matrizes ───────────────────
    private void updateMatrices() {
        // Converter esféricas → cartesianas
        position = new Vec3(
            target.x + radius * Math.sin(phi) * Math.sin(theta),
            target.y + radius * Math.cos(phi),
            target.z + radius * Math.sin(phi) * Math.cos(theta)
        );
        viewMatrix     = Mat4.lookAt(position, target, Vec3.UP);
        projMatrix     = Mat4.perspective(fovY, aspect, near, far);
        viewProjMatrix = projMatrix.mul(viewMatrix);
    }

    // ── Projeção de ponto 3D → NDC / tela ─────────
    /**
     * Projeta um ponto 3D para coordenadas de tela [0..width, 0..height].
     * Retorna null se o ponto estiver atrás da câmera.
     */
    public double[] projectToScreen(Vec3 worldPos, double screenW, double screenH) {
        double[] screen = new double[3];
        return projectToScreen(worldPos, screenW, screenH, screen) ? screen : null;
    }

    /**
     * Projeta em um buffer reutilizavel — versao usada nos lacos de render,
     * onde alocar um array por vertice geraria lixo demais.
     *
     * @param screen buffer de saida com ao menos 3 posicoes:
     *               {@code [x_tela, y_tela, profundidade_ndc]}
     * @return {@code false} se o ponto caiu fora do frustum (atras da camera ou
     *         alem do plano distante), caso em que o buffer nao deve ser lido
     */
    public boolean projectToScreen(Vec3 worldPos, double screenW, double screenH, double[] screen) {
        if (screen == null || screen.length < 3) {
            throw new IllegalArgumentException("Screen buffer precisa ter ao menos 3 posicoes.");
        }
        Vec3 clip = viewProjMatrix.transformPoint(worldPos);
        if (clip.z < -1 || clip.z > 1) return false; // fora do frustum

        screen[0] = (clip.x * 0.5 + 0.5) * screenW;
        screen[1] = (1.0 - (clip.y * 0.5 + 0.5)) * screenH;
        screen[2] = clip.z;
        return true;
    }

    /** Verifica se ponto está na frente da câmera. */
    public boolean isInFront(Vec3 worldPos) {
        Vec3 toPoint = worldPos.sub(position);
        Vec3 forward = target.sub(position).normalize();
        return toPoint.dot(forward) > 0;
    }

    /** Raio no espaço mundo a partir de coordenadas de tela. */
    public Vec3[] screenToWorldRay(double sx, double sy, double screenW, double screenH) {
        // NDC
        double ndcX = (sx / screenW) * 2 - 1;
        double ndcY = 1 - (sy / screenH) * 2;
        // Direção em view space
        double tanHalfFov = Math.tan(fovY / 2);
        Vec3 rayDir = new Vec3(ndcX * tanHalfFov * aspect, ndcY * tanHalfFov, -1);
        // Transformar para world space
        Vec3 forward = target.sub(position).normalize();
        Vec3 right   = forward.cross(Vec3.UP).normalize();
        Vec3 up      = right.cross(forward).normalize();
        Vec3 worldDir = right.mul(rayDir.x).add(up.mul(rayDir.y)).add(forward.mul(-rayDir.z)).normalize();
        return new Vec3[]{ position, worldDir };
    }

    // ── Getters ────────────────────────────────────
    public Vec3  getPosition()       { return position; }
    public Vec3  getTarget()         { return target; }
    public Mat4  getViewMatrix()     { return viewMatrix; }
    public Mat4  getProjMatrix()     { return projMatrix; }
    public Mat4  getViewProjMatrix() { return viewProjMatrix; }
    public double getFovY()          { return fovY; }
    public double getRadius()        { return radius; }

    public void setAspect(double a) {
        if (Math.abs(aspect - a) < 1e-9) return;
        this.aspect = a;
        updateMatrices();
    }

    public void setFovY(double f) {
        if (Math.abs(fovY - f) < 1e-9) return;
        this.fovY = f;
        updateMatrices();
    }
}
