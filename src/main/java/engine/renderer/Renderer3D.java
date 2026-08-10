package engine.renderer;

import engine.math.ColorRGBA;
import engine.math.Mat4;
import engine.math.Vec3;
import engine.scene.SceneObject;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

import java.util.ArrayList;
import java.util.List;

/**
 * Renderizador 3D por software, desenhando em um Canvas 2D do JavaFX.
 *
 * <p>Nao ha OpenGL nem Vulkan aqui: todo o pipeline e implementado a mao, o que
 * torna a engine 100% portatil (roda em qualquer maquina com Java) e serve como
 * demonstracao didatica de como uma placa de video funciona por dentro.
 *
 * <h2>Pipeline por frame</h2>
 * <ol>
 *   <li><b>Fundo e grade</b> — gradiente vertical + linhas do piso.</li>
 *   <li><b>Descarte por objeto</b> — objetos fora da tela ou pequenos demais
 *       (menos de ~1 pixel) sao pulados antes de tocar em seus triangulos.</li>
 *   <li><b>Transformacao</b> — cada vertice passa pela matriz modelo e depois
 *       pela view-projection da camera, chegando em coordenadas de tela.</li>
 *   <li><b>Iluminacao</b> — shading plano por face: duas luzes direcionais mais
 *       ambiente e um termo especular Blinn-Phong.</li>
 *   <li><b>Ordenacao (algoritmo do pintor)</b> — como nao existe z-buffer, os
 *       triangulos sao ordenados por profundidade e desenhados do mais distante
 *       para o mais proximo. A camada ({@code renderLayer}) tem prioridade sobre
 *       a profundidade, o que permite forcar sombras atras de tudo.</li>
 *   <li><b>Vetores</b> — setas opcionais de posicao, velocidade e gravidade.</li>
 * </ol>
 *
 * <h2>Orcamento de triangulos</h2>
 * Cenas como o sistema solar geram dezenas de milhares de triangulos. Em vez de
 * derrubar o FPS, o renderizador para de acumular ao atingir o orcamento
 * ({@value #PERFORMANCE_TRIANGLE_BUDGET} no modo leve,
 * {@value #FULL_TRIANGLE_BUDGET} no completo) e registra quantos foram
 * descartados, informacao exposta pelos getters de diagnostico.
 */
public class Renderer3D {

    private Vec3 lightDir = new Vec3(0.45, 1.0, 0.65).normalize();
    private Vec3 lightDir2 = new Vec3(-0.65, 0.55, -0.45).normalize();
    private ColorRGBA ambientLight = new ColorRGBA(0.18, 0.19, 0.25);
    private ColorRGBA lightColor = new ColorRGBA(1.0, 0.96, 0.86);
    private ColorRGBA lightColor2 = new ColorRGBA(0.22, 0.30, 0.55);
    private boolean wireframe;
    private boolean showGrid = true;
    private boolean showShadows = true;
    private boolean showVectors = true;
    private boolean performanceMode = true;
    private Vec3 gravityVector = Vec3.ZERO;

    private static final LinearGradient BG_GRADIENT = new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0.0, Color.web("#101923")),
        new Stop(0.46, Color.web("#091118")),
        new Stop(1.0, Color.web("#05070d"))
    );
    private static final Color GRID_COLOR = Color.web("#172033");
    private static final Color GRID_ACCENT = Color.web("#263653");
    private static final ColorRGBA SHADOW_COLOR = new ColorRGBA(0, 0, 0.05, 0.38);

    private record ProjectedTri(
        double x0, double y0,
        double x1, double y1,
        double x2, double y2,
        double depth,
        int layer,
        ColorRGBA color
    ) {}

    private final List<ProjectedTri> projectedTriangles = new ArrayList<>(4096);

    /** Buffer reaproveitado pelos testes de visibilidade por objeto (evita alocar por frame). */
    private final double[] cullingBuffer = new double[3];

    private int skippedSmallObjects;
    private int skippedTriangleBudget;

    private static final int PERFORMANCE_TRIANGLE_BUDGET = 8500;
    private static final int FULL_TRIANGLE_BUDGET = 18000;
    private static final int SHADOW_TRIANGLE_BUDGET = 2200;

    /**
     * Desenha um frame completo da cena.
     *
     * @param gc      contexto do Canvas onde tudo e pintado
     * @param camera  fonte das matrizes de view e projecao
     * @param objects objetos a desenhar (cena do modulo ativo ou do sandbox)
     * @param w       largura do canvas em pixels
     * @param h       altura do canvas em pixels
     */
    public void render(GraphicsContext gc, Camera camera,
                       List<SceneObject> objects, double w, double h) {
        gc.setFill(BG_GRADIENT);
        gc.fillRect(0, 0, w, h);
        gc.setFill(Color.web("#0f2c2f", 0.16));
        gc.fillRect(0, h * 0.58, w, h * 0.42);

        if (showGrid) renderGrid(gc, camera, w, h);

        projectedTriangles.clear();
        skippedSmallObjects = 0;
        skippedTriangleBudget = 0;
        int triangleBudget = performanceMode ? PERFORMANCE_TRIANGLE_BUDGET : FULL_TRIANGLE_BUDGET;
        for (SceneObject obj : objects) {
            if (!obj.isVisible()) continue;
            if (shouldSkipSmallObject(obj, camera, w, h)) {
                skippedSmallObjects++;
                continue;
            }
            if (projectedTriangles.size() >= triangleBudget) {
                skippedTriangleBudget++;
                continue;
            }
            collectTriangles(obj, camera, w, h, triangleBudget);
        }

        // Algoritmo do pintor: camada primeiro (sombras ficam no fundo), depois
        // profundidade decrescente — o mais longe e pintado antes e acaba coberto
        // pelo mais perto.
        projectedTriangles.sort((a, b) -> {
            int layerOrder = Integer.compare(a.layer(), b.layer());
            return layerOrder != 0 ? layerOrder : Double.compare(b.depth(), a.depth());
        });
        for (ProjectedTri tri : projectedTriangles) {
            drawTriangle(gc, tri);
        }
        if (showVectors) renderVectors(gc, camera, objects, w, h);
    }

    private void collectTriangles(SceneObject obj, Camera camera, double w, double h, int triangleBudget) {
        Mesh mesh = obj.getMesh();
        if (mesh == null) return;

        Vec3 pos = obj.getBody().getPosition();
        if (!roughlyVisible(obj, camera, w, h)) return;

        Vec3 rot = obj.getBody().getRotation();
        Mat4 rotation = obj.getRotationTransform();
        if (rotation == null) {
            rotation = Mat4.rotationY(rot.y)
                .mul(Mat4.rotationX(rot.x))
                .mul(Mat4.rotationZ(rot.z));
        }

        Mat4 model = Mat4.translation(pos).mul(rotation);
        Vec3 camPos = camera.getPosition();
        Vec3 shadowDirection = lightDir.negate();
        double[] s0 = new double[3];
        double[] s1 = new double[3];
        double[] s2 = new double[3];

        for (Mesh.Triangle tri : mesh.getTriangles()) {
            if (projectedTriangles.size() >= triangleBudget) {
                skippedTriangleBudget++;
                return;
            }
            Vec3 w0 = model.transformPoint(tri.v0);
            Vec3 w1 = model.transformPoint(tri.v1);
            Vec3 w2 = model.transformPoint(tri.v2);
            Vec3 normal = model.transformDir(tri.normal).normalize();
            Vec3 centroid = new Vec3(
                (w0.x + w1.x + w2.x) / 3.0,
                (w0.y + w1.y + w2.y) / 3.0,
                (w0.z + w1.z + w2.z) / 3.0
            );

            if (!camera.projectToScreen(w0, w, h, s0)
                || !camera.projectToScreen(w1, w, h, s1)
                || !camera.projectToScreen(w2, w, h, s2)) {
                continue;
            }
            if (isCompletelyOffScreen(s0, s1, s2, w, h)) continue;

            ColorRGBA color = computeShading(baseColor(obj, tri),
                viewFacingNormal(normal, centroid, camPos), centroid, camPos);
            projectedTriangles.add(new ProjectedTri(
                s0[0], s0[1],
                s1[0], s1[1],
                s2[0], s2[1],
                (s0[2] + s1[2] + s2[2]) / 3.0,
                obj.getRenderLayer(),
                color
            ));

            if (showShadows && obj.isShadowCaster() && centroid.y > -4.8
                && projectedTriangles.size() < SHADOW_TRIANGLE_BUDGET
                && (!performanceMode || mesh.getTriangles().size() <= 80)) {
                collectShadowTri(w0, w1, w2, shadowDirection, camera, w, h, s0, s1, s2);
            }
        }
    }

    /**
     * Descarte rapido por objeto: projeta apenas o centro e compara com a tela
     * mais uma margem proporcional ao raio aparente. Evita percorrer os
     * triangulos de objetos que estao claramente fora do campo de visao.
     *
     * <p>Usa o buffer reaproveitado {@link #cullingBuffer} porque roda uma vez
     * por objeto a cada frame — com centenas de corpos (ex.: sistema solar) a
     * versao que alocava um {@code double[3]} por chamada gerava lixo continuo.
     */
    private boolean roughlyVisible(SceneObject obj, Camera camera, double w, double h) {
        if (!camera.projectToScreen(obj.getBody().getPosition(), w, h, cullingBuffer)) return false;
        double radius = obj.getBody().getBoundingRadius();
        double distance = Math.max(0.5, obj.getBody().getPosition().distanceTo(camera.getPosition()));
        double pixelRadius = radius * Math.min(w, h) / distance;
        double margin = Math.max(80, pixelRadius * 3.0);
        return cullingBuffer[0] >= -margin && cullingBuffer[0] <= w + margin
            && cullingBuffer[1] >= -margin && cullingBuffer[1] <= h + margin;
    }

    private boolean shouldSkipSmallObject(SceneObject obj, Camera camera, double w, double h) {
        if (!performanceMode || obj.isSelected() || obj.getRenderLayer() > 0) return false;
        Mesh mesh = obj.getMesh();
        if (mesh == null || mesh.getTriangles().size() > 24) return false;
        double distance = Math.max(0.5, obj.getBody().getPosition().distanceTo(camera.getPosition()));
        double pixelRadius = obj.getBody().getBoundingRadius() * Math.min(w, h) / distance;
        return pixelRadius < 1.15;
    }

    private void collectShadowTri(Vec3 w0, Vec3 w1, Vec3 w2, Vec3 shadowDirection,
                                  Camera camera, double sw, double sh,
                                  double[] p0, double[] p1, double[] p2) {
        double floorY = -4.97;
        Vec3 s0 = projectToFloor(w0, shadowDirection, floorY);
        Vec3 s1 = projectToFloor(w1, shadowDirection, floorY);
        Vec3 s2 = projectToFloor(w2, shadowDirection, floorY);

        if (!camera.projectToScreen(s0, sw, sh, p0)
            || !camera.projectToScreen(s1, sw, sh, p1)
            || !camera.projectToScreen(s2, sw, sh, p2)) {
            return;
        }

        projectedTriangles.add(new ProjectedTri(
            p0[0], p0[1],
            p1[0], p1[1],
            p2[0], p2[1],
            0.999,
            -1,
            SHADOW_COLOR
        ));
    }

    private static Vec3 projectToFloor(Vec3 p, Vec3 shadowDirection, double floorY) {
        double t = (p.y - floorY) / (-shadowDirection.y + 1e-9);
        return new Vec3(p.x + shadowDirection.x * t, floorY, p.z + shadowDirection.z * t);
    }

    private ColorRGBA computeShading(ColorRGBA baseColor, Vec3 normal,
                                      Vec3 surfacePos, Vec3 camPos) {
        double diff1 = Math.max(0, normal.dot(lightDir));
        double diff2 = Math.max(0, normal.dot(lightDir2)) * 0.35;
        Vec3 viewDir = camPos.sub(surfacePos).normalize();
        Vec3 halfVec = viewDir.add(lightDir).normalize();
        double specular = Math.pow(Math.max(0, normal.dot(halfVec)), 32) * 0.45;

        double r = baseColor.r * (ambientLight.r + lightColor.r * diff1 + lightColor2.r * diff2) + specular;
        double g = baseColor.g * (ambientLight.g + lightColor.g * diff1 + lightColor2.g * diff2) + specular;
        double b = baseColor.b * (ambientLight.b + lightColor.b * diff1 + lightColor2.b * diff2) + specular;
        return new ColorRGBA(r, g, b, baseColor.a);
    }

    /**
     * Simulation meshes mix primitives, drawn extrusions and imported scene data.
     * Keep visible faces stable even when their winding order is not uniform.
     */
    private Vec3 viewFacingNormal(Vec3 normal, Vec3 surfacePos, Vec3 camPos) {
        return normal.dot(camPos.sub(surfacePos)) < 0 ? normal.negate() : normal;
    }

    private ColorRGBA baseColor(SceneObject obj, Mesh.Triangle triangle) {
        if (obj.getTexture() == null || !triangle.hasUv()) return obj.getColor();
        ColorRGBA texture = obj.getTexture().sample(triangle.centerU(), triangle.centerV(), obj.getColor());
        return texture.mix(obj.getColor(), 0.10);
    }

    private void drawTriangle(GraphicsContext gc, ProjectedTri tri) {
        gc.beginPath();
        gc.moveTo(tri.x0(), tri.y0());
        gc.lineTo(tri.x1(), tri.y1());
        gc.lineTo(tri.x2(), tri.y2());
        gc.closePath();

        Color fill = tri.color().toJFX();
        gc.setFill(fill);
        gc.fill();

        if (wireframe) {
            gc.setStroke(fill.brighter().brighter());
            gc.setLineWidth(0.5);
            gc.stroke();
        }
    }

    private void renderGrid(GraphicsContext gc, Camera camera, double sw, double sh) {
        double y = -5.0;
        int size = 18;
        double[] a = new double[3];
        double[] b = new double[3];
        double[] c = new double[3];
        double[] d = new double[3];

        gc.setLineWidth(0.5);
        for (int i = -size; i <= size; i++) {
            boolean aVisible = camera.projectToScreen(new Vec3(i, y, -size), sw, sh, a);
            boolean bVisible = camera.projectToScreen(new Vec3(i, y, size), sw, sh, b);
            boolean cVisible = camera.projectToScreen(new Vec3(-size, y, i), sw, sh, c);
            boolean dVisible = camera.projectToScreen(new Vec3(size, y, i), sw, sh, d);

            gc.setStroke((i % 5 == 0) ? GRID_ACCENT : GRID_COLOR);
            if (aVisible && bVisible) drawLine(gc, a, b);
            if (cVisible && dVisible) drawLine(gc, c, d);
        }
    }

    private static void drawLine(GraphicsContext gc, double[] a, double[] b) {
        gc.beginPath();
        gc.moveTo(a[0], a[1]);
        gc.lineTo(b[0], b[1]);
        gc.stroke();
    }

    private void renderVectors(GraphicsContext gc, Camera camera,
                               List<SceneObject> objects, double sw, double sh) {
        int drawn = 0;
        for (SceneObject object : objects) {
            if (!object.isVisible()) continue;
            Vec3 position = object.getBody().getPosition();
            Vec3 velocity = object.getBody().getVelocity();
            if (object.isSelected() || velocity.lengthSq() > 0.0025) {
                if (performanceMode && !object.isSelected() && drawn > 32) continue;
                drawArrow(gc, camera, Vec3.ZERO, position, sw, sh, Color.web("#64748b"), 1.0);
                drawArrow(gc, camera, position, position.add(velocity.mul(0.22)),
                    sw, sh, Color.web("#38bdf8"), 2.0);
                drawn++;
            }
            if (object.isSelected() && gravityVector.lengthSq() > 0.0025) {
                drawArrow(gc, camera, position, position.add(gravityVector.normalize().mul(1.15)),
                    sw, sh, Color.web("#f59e0b"), 2.0);
            }
        }
    }

    private void drawArrow(GraphicsContext gc, Camera camera, Vec3 start, Vec3 end,
                           double sw, double sh, Color color, double width) {
        double[] a = camera.projectToScreen(start, sw, sh);
        double[] b = camera.projectToScreen(end, sw, sh);
        if (a == null || b == null) return;

        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double length = Math.hypot(dx, dy);
        if (length < 2) return;

        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.strokeLine(a[0], a[1], b[0], b[1]);
        double ux = dx / length;
        double uy = dy / length;
        double head = Math.min(10, Math.max(5, length * 0.22));
        gc.strokeLine(b[0], b[1], b[0] - ux * head - uy * head * 0.48, b[1] - uy * head + ux * head * 0.48);
        gc.strokeLine(b[0], b[1], b[0] - ux * head + uy * head * 0.48, b[1] - uy * head - ux * head * 0.48);
    }

    private boolean isCompletelyOffScreen(double[] s0, double[] s1, double[] s2,
                                           double w, double h) {
        double margin = 200;
        return (s0[0] < -margin && s1[0] < -margin && s2[0] < -margin) ||
               (s0[0] > w + margin && s1[0] > w + margin && s2[0] > w + margin) ||
               (s0[1] < -margin && s1[1] < -margin && s2[1] < -margin) ||
               (s0[1] > h + margin && s1[1] > h + margin && s2[1] > h + margin);
    }

    public void setWireframe(boolean wireframe) { this.wireframe = wireframe; }
    public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }
    public void setShowShadows(boolean showShadows) { this.showShadows = showShadows; }
    public void setShowVectors(boolean showVectors) { this.showVectors = showVectors; }
    public void setPerformanceMode(boolean performanceMode) { this.performanceMode = performanceMode; }
    public void setGravityVector(Vec3 gravityVector) { this.gravityVector = gravityVector == null ? Vec3.ZERO : gravityVector; }
    public boolean isWireframe() { return wireframe; }
    public boolean isShowGrid() { return showGrid; }
    public boolean isShowShadows() { return showShadows; }
    public boolean isPerformanceMode() { return performanceMode; }
    public int getSkippedSmallObjects() { return skippedSmallObjects; }
    public int getSkippedTriangleBudget() { return skippedTriangleBudget; }

    public void setLightDirection(Vec3 dir) { this.lightDir = dir.normalize(); }

    public void useDefaultLighting() {
        lightDir = new Vec3(0.45, 1.0, 0.65).normalize();
        lightDir2 = new Vec3(-0.65, 0.55, -0.45).normalize();
        ambientLight = new ColorRGBA(0.18, 0.19, 0.25);
        lightColor = new ColorRGBA(1.0, 0.96, 0.86);
        lightColor2 = new ColorRGBA(0.22, 0.30, 0.55);
    }

    public void useTugOfWarLighting() {
        lightDir = new Vec3(-0.20, 0.85, 0.95).normalize();
        lightDir2 = new Vec3(0.75, 0.40, 0.70).normalize();
        ambientLight = new ColorRGBA(0.24, 0.24, 0.30);
        lightColor = new ColorRGBA(1.0, 0.92, 0.76);
        lightColor2 = new ColorRGBA(0.36, 0.46, 0.78);
    }
}
