package engine.renderer;

import engine.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Malha 3D: lista de triângulos com normais pré-calculadas.
 * Usada pelo Renderer para rasterização software no JavaFX Canvas.
 */
public class Mesh {

    /** Triângulo com 3 vértices e normal da face. */
    public static final class Triangle {
        public final Vec3 v0, v1, v2;
        public final Vec3 normal;
        public final double u0, v0uv, u1, v1uv, u2, v2uv;

        public Triangle(Vec3 v0, Vec3 v1, Vec3 v2) {
            this(v0, v1, v2, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        public Triangle(Vec3 v0, Vec3 v1, Vec3 v2,
                        double u0, double v0uv,
                        double u1, double v1uv,
                        double u2, double v2uv) {
            this.v0 = v0; this.v1 = v1; this.v2 = v2;
            this.u0 = u0; this.v0uv = v0uv;
            this.u1 = u1; this.v1uv = v1uv;
            this.u2 = u2; this.v2uv = v2uv;
            Vec3 edge1 = v1.sub(v0);
            Vec3 edge2 = v2.sub(v0);
            this.normal = edge1.cross(edge2).normalize();
        }

        public boolean hasUv() {
            return Double.isFinite(u0) && Double.isFinite(v0uv)
                && Double.isFinite(u1) && Double.isFinite(v1uv)
                && Double.isFinite(u2) && Double.isFinite(v2uv);
        }

        public double centerU() {
            return (u0 + u1 + u2) / 3.0;
        }

        public double centerV() {
            return (v0uv + v1uv + v2uv) / 3.0;
        }

        /** Centróide do triângulo. */
        public Vec3 centroid() {
            return new Vec3(
                (v0.x + v1.x + v2.x) / 3.0,
                (v0.y + v1.y + v2.y) / 3.0,
                (v0.z + v1.z + v2.z) / 3.0
            );
        }
    }

    private final List<Triangle> triangles = new ArrayList<>();
    private final String name;

    public Mesh(String name) { this.name = name; }

    public void addTriangle(Triangle t) { triangles.add(t); }
    public List<Triangle> getTriangles() { return triangles; }
    public String getName()             { return name; }

    /**
     * Cria uma fita 3D extrudada seguindo exatamente os pontos de um desenho 2D.
     * Os pontos chegam em coordenadas locais da cena, no plano XY; a espessura
     * visual fica no plano XY e a profundidade no eixo Z.
     */
    public static Mesh createExtrudedStroke(String name, List<Vec3> points,
                                            double strokeRadius,
                                            double depth) {
        Mesh m = new Mesh(name);
        if (points == null || points.size() < 2) {
            return createBox(0.25, 0.25, 0.08);
        }

        double halfDepth = Math.max(0.03, depth * 0.5);
        double halfWidth = Math.max(0.04, strokeRadius);

        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            if (!isFinite(a) || !isFinite(b)) continue;
            Vec3 delta = b.sub(a);
            double len = Math.sqrt(delta.x * delta.x + delta.y * delta.y);
            if (len < 1e-5) continue;

            double nx = -delta.y / len;
            double ny =  delta.x / len;

            Vec3 aLFront = new Vec3(a.x + nx * halfWidth, a.y + ny * halfWidth, -halfDepth);
            Vec3 aRFront = new Vec3(a.x - nx * halfWidth, a.y - ny * halfWidth, -halfDepth);
            Vec3 bLFront = new Vec3(b.x + nx * halfWidth, b.y + ny * halfWidth, -halfDepth);
            Vec3 bRFront = new Vec3(b.x - nx * halfWidth, b.y - ny * halfWidth, -halfDepth);
            Vec3 aLBack  = new Vec3(a.x + nx * halfWidth, a.y + ny * halfWidth,  halfDepth);
            Vec3 aRBack  = new Vec3(a.x - nx * halfWidth, a.y - ny * halfWidth,  halfDepth);
            Vec3 bLBack  = new Vec3(b.x + nx * halfWidth, b.y + ny * halfWidth,  halfDepth);
            Vec3 bRBack  = new Vec3(b.x - nx * halfWidth, b.y - ny * halfWidth,  halfDepth);

            addQuad(m, aLFront, bLFront, bRFront, aRFront);
            addQuad(m, aRBack,  bRBack,  bLBack,  aLBack);
            addQuad(m, aLBack,  bLBack,  bLFront, aLFront);
            addQuad(m, aRFront, bRFront, bRBack,  aRBack);

            boolean startsStroke = i == 1 || !isFinite(points.get(i - 2));
            boolean endsStroke = i == points.size() - 1 || !isFinite(points.get(i + 1));
            if (startsStroke) {
                addQuad(m, aLBack, aLFront, aRFront, aRBack);
            }
            if (endsStroke) {
                addQuad(m, bLFront, bLBack, bRBack, bRFront);
            }
        }

        return m.getTriangles().isEmpty() ? createBox(0.25, 0.25, 0.08) : m;
    }

    /**
     * Converte um rascunho com varios tracos em malha 3D.
     * Contornos fechados viram solidos extrudados; linhas abertas mantem a
     * extrusao do traco para permitir detalhes no mesmo objeto.
     */
    public static Mesh createExtrudedDrawing(String name, List<Vec3> points,
                                             double strokeRadius,
                                             double depth) {
        Mesh m = new Mesh(name);
        if (points == null || points.size() < 2) {
            return createBox(0.25, 0.25, 0.08);
        }

        for (List<Vec3> rawStroke : splitStrokes(points)) {
            List<Vec3> stroke = cleanStroke(rawStroke, Math.max(0.0025, strokeRadius * 0.15));
            if (stroke.size() < 2) continue;
            if (isClosedContour(stroke, strokeRadius)) {
                addExtrudedContour(m, trimClosedContour(stroke, strokeRadius), depth);
            } else {
                copyTriangles(m, createExtrudedStroke(name + "Stroke", stroke, strokeRadius, depth));
            }
        }

        return m.getTriangles().isEmpty() ? createExtrudedStroke(name, points, strokeRadius, depth) : m;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DESENHO LIVRE EM VOLUME
    // ══════════════════════════════════════════════════════════════════════
    //
    //  A extrusao acima produz um recorte plano com espessura — util para
    //  placas e letreiros, mas nao e um solido. Os dois modos abaixo geram
    //  volume de verdade a partir do mesmo traco:
    //
    //    INFLAR    o contorno fechado vira um solido arredondado, mais espesso
    //              no centro e afinando ate zero na borda. Um circulo desenhado
    //              vira uma esfera achatada; uma estrela vira uma estrela gorda.
    //
    //    REVOLUCAO o traco e o perfil de um torno: girado 360 graus em torno do
    //              eixo vertical, produz vasos, tacas e pecas de xadrez.

    /** Numero de aneis entre o centro e a borda no modo inflar. */
    private static final int INFLATE_RINGS = 7;
    /** Pontos do contorno apos reamostragem, para limitar a contagem de triangulos. */
    private static final int INFLATE_CONTOUR_SAMPLES = 44;
    /** Passos angulares de uma volta completa no modo revolucao. */
    private static final int REVOLVE_SEGMENTS = 28;

    /**
     * Infla um contorno fechado num solido arredondado.
     *
     * <p>O contorno e reamostrado e depois encolhido em direcao ao centroide em
     * aneis concentricos. Cada anel recebe uma altura que segue um perfil
     * circular — {@code h = espessura * sqrt(1 - (k/K)^2)} — de modo que a
     * superficie sai abaulada no meio e encosta em zero exatamente na borda
     * desenhada. Espelhando para baixo, as duas metades se encontram no contorno
     * e fecham o solido sem costura.
     *
     * <p>Funciona para qualquer contorno visivel a partir do centroide, que e o
     * caso da quase totalidade dos desenhos a mao. Formas muito reentrantes
     * degradam suavemente, sem quebrar.
     *
     * @param thickness meia-espessura maxima, no centro da forma
     */
    public static Mesh createInflatedDrawing(String name, List<Vec3> rawContour, double thickness) {
        Mesh m = new Mesh(name);
        List<Vec3> contour = ensureCounterClockwise(cleanContour(rawContour));
        if (contour.size() < 3 || Math.abs(signedArea(contour)) < 1e-7) {
            return createExtrudedStroke(name, rawContour, 0.05, thickness);
        }

        List<Vec3> outline = resampleClosed(contour, INFLATE_CONTOUR_SAMPLES);
        int n = outline.size();
        Vec3 center = centroidOf(outline);
        double maxHeight = Math.max(0.04, thickness);

        // Aneis 1..INFLATE_RINGS, da vizinhanca do centro ate a borda desenhada.
        // O anel 0 nao existe de proposito: com encolhimento zero ele colapsaria
        // todos os pontos no proprio apice, gerando triangulos de area nula que
        // deixariam a malha com arestas soltas.
        Vec3[][] top = new Vec3[INFLATE_RINGS + 1][n];
        Vec3[][] bottom = new Vec3[INFLATE_RINGS + 1][n];
        for (int k = 1; k <= INFLATE_RINGS; k++) {
            double shrink = (double) k / INFLATE_RINGS;
            double height = maxHeight * Math.sqrt(Math.max(0, 1.0 - shrink * shrink));
            // No anel externo a altura e zero e as duas metades tem de coincidir
            // no mesmo vertice. Sem normalizar o sinal, a face de baixo ficaria
            // em -0.0 e a solda entre as metades deixaria de ser exata.
            double topZ = height == 0.0 ? 0.0 : height;
            double bottomZ = height == 0.0 ? 0.0 : -height;
            for (int i = 0; i < n; i++) {
                Vec3 p = outline.get(i);
                double x = center.x + (p.x - center.x) * shrink;
                double y = center.y + (p.y - center.y) * shrink;
                top[k][i] = new Vec3(x, y, topZ);
                bottom[k][i] = new Vec3(x, y, bottomZ);
            }
        }

        // Faixas entre aneis vizinhos.
        for (int k = 1; k < INFLATE_RINGS; k++) {
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                addQuad(m, top[k][i], top[k][j], top[k + 1][j], top[k + 1][i]);
                addQuad(m, bottom[k + 1][i], bottom[k + 1][j], bottom[k][j], bottom[k][i]);
            }
        }

        // Calotas: leque do apice ate o primeiro anel, fechando cada lado.
        Vec3 apexTop = new Vec3(center.x, center.y, maxHeight);
        Vec3 apexBottom = new Vec3(center.x, center.y, -maxHeight);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            m.addTriangle(new Triangle(apexTop, top[1][i], top[1][j]));
            m.addTriangle(new Triangle(apexBottom, bottom[1][j], bottom[1][i]));
        }
        return m;
    }

    /**
     * Gira um traco em torno do eixo vertical, como num torno mecanico.
     *
     * <p>Cada ponto do perfil vira um anel de raio igual a sua distancia ao
     * eixo. Ligando aneis consecutivos sai um solido de revolucao — a forma de
     * um vaso, de uma taca ou de uma peca de xadrez, conforme o traco.
     *
     * @param axisX posicao do eixo de giro no plano do desenho
     */
    public static Mesh createRevolvedDrawing(String name, List<Vec3> rawProfile, double axisX) {
        Mesh m = new Mesh(name);
        List<Vec3> profile = cleanStroke(rawProfile, 1e-4);
        if (profile.size() < 2) return createExtrudedStroke(name, rawProfile, 0.05, 0.2);

        // Perfil ordenado por altura: o torno sobe monotonicamente.
        profile = new ArrayList<>(profile);
        profile.sort((a, b) -> Double.compare(a.y, b.y));

        int steps = REVOLVE_SEGMENTS;
        int rows = profile.size();
        Vec3[][] ring = new Vec3[rows][steps];
        for (int r = 0; r < rows; r++) {
            Vec3 p = profile.get(r);
            double radius = Math.max(0.01, Math.abs(p.x - axisX));
            for (int s = 0; s < steps; s++) {
                double angle = 2 * Math.PI * s / steps;
                ring[r][s] = new Vec3(radius * Math.cos(angle), p.y, radius * Math.sin(angle));
            }
        }

        for (int r = 0; r < rows - 1; r++) {
            for (int s = 0; s < steps; s++) {
                int t = (s + 1) % steps;
                addQuad(m, ring[r][s], ring[r][t], ring[r + 1][t], ring[r + 1][s]);
            }
        }

        // Tampas nas duas pontas, para o solido ficar fechado.
        Vec3 bottomCenter = new Vec3(0, profile.get(0).y, 0);
        Vec3 topCenter = new Vec3(0, profile.get(rows - 1).y, 0);
        for (int s = 0; s < steps; s++) {
            int t = (s + 1) % steps;
            m.addTriangle(new Triangle(bottomCenter, ring[0][t], ring[0][s]));
            m.addTriangle(new Triangle(topCenter, ring[rows - 1][s], ring[rows - 1][t]));
        }
        return m;
    }

    /** Centroide simples dos vertices, usado como polo das calotas. */
    private static Vec3 centroidOf(List<Vec3> points) {
        double x = 0, y = 0;
        for (Vec3 p : points) { x += p.x; y += p.y; }
        return new Vec3(x / points.size(), y / points.size(), 0);
    }

    /**
     * Reamostra um contorno fechado em {@code samples} pontos igualmente
     * espacados ao longo do perimetro. Mantem a contagem de triangulos
     * previsivel, independentemente de quantos pontos o usuario desenhou.
     */
    private static List<Vec3> resampleClosed(List<Vec3> contour, int samples) {
        int n = contour.size();
        double[] cumulative = new double[n + 1];
        for (int i = 0; i < n; i++) {
            Vec3 a = contour.get(i);
            Vec3 b = contour.get((i + 1) % n);
            cumulative[i + 1] = cumulative[i] + Math.hypot(b.x - a.x, b.y - a.y);
        }
        double perimeter = cumulative[n];
        if (perimeter < 1e-9) return contour;

        List<Vec3> out = new ArrayList<>(samples);
        int edge = 0;
        for (int s = 0; s < samples; s++) {
            double target = perimeter * s / samples;
            while (edge < n - 1 && cumulative[edge + 1] < target) edge++;
            double segment = cumulative[edge + 1] - cumulative[edge];
            double t = segment < 1e-12 ? 0 : (target - cumulative[edge]) / segment;
            Vec3 a = contour.get(edge);
            Vec3 b = contour.get((edge + 1) % n);
            out.add(new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, 0));
        }
        return out;
    }

    private static void addExtrudedContour(Mesh m, List<Vec3> rawContour, double depth) {
        List<Vec3> contour = ensureCounterClockwise(cleanContour(rawContour));
        if (contour.size() < 3 || Math.abs(signedArea(contour)) < 1e-7) {
            copyTriangles(m, createExtrudedStroke(m.getName() + "Fallback", rawContour, 0.05, depth));
            return;
        }

        double halfDepth = Math.max(0.03, depth * 0.5);
        for (int[] tri : triangulate(contour)) {
            Vec3 a = contour.get(tri[0]);
            Vec3 b = contour.get(tri[1]);
            Vec3 c = contour.get(tri[2]);
            m.addTriangle(new Triangle(atDepth(a, -halfDepth), atDepth(b, -halfDepth), atDepth(c, -halfDepth)));
            m.addTriangle(new Triangle(atDepth(c, halfDepth), atDepth(b, halfDepth), atDepth(a, halfDepth)));
        }

        for (int i = 0; i < contour.size(); i++) {
            Vec3 a = contour.get(i);
            Vec3 b = contour.get((i + 1) % contour.size());
            addQuad(m, atDepth(a, halfDepth), atDepth(b, halfDepth),
                atDepth(b, -halfDepth), atDepth(a, -halfDepth));
        }
    }

    private static List<int[]> triangulate(List<Vec3> contour) {
        List<int[]> triangles = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < contour.size(); i++) remaining.add(i);

        int guard = contour.size() * contour.size();
        while (remaining.size() > 3 && guard-- > 0) {
            boolean earFound = false;
            for (int i = 0; i < remaining.size(); i++) {
                int prev = remaining.get((i + remaining.size() - 1) % remaining.size());
                int curr = remaining.get(i);
                int next = remaining.get((i + 1) % remaining.size());
                Vec3 a = contour.get(prev);
                Vec3 b = contour.get(curr);
                Vec3 c = contour.get(next);
                if (cross2d(a, b, c) <= 1e-8 || containsContourPoint(contour, remaining, prev, curr, next)) {
                    continue;
                }
                triangles.add(new int[]{prev, curr, next});
                remaining.remove(i);
                earFound = true;
                break;
            }
            if (!earFound) break;
        }

        if (remaining.size() == 3) {
            triangles.add(new int[]{remaining.get(0), remaining.get(1), remaining.get(2)});
        }
        if (remaining.size() > 3) {
            for (int i = 1; i < remaining.size() - 1; i++) {
                triangles.add(new int[]{remaining.get(0), remaining.get(i), remaining.get(i + 1)});
            }
        }
        if (triangles.isEmpty()) {
            for (int i = 1; i < contour.size() - 1; i++) {
                triangles.add(new int[]{0, i, i + 1});
            }
        }
        return triangles;
    }

    private static boolean containsContourPoint(List<Vec3> contour, List<Integer> remaining,
                                                int aIndex, int bIndex, int cIndex) {
        Vec3 a = contour.get(aIndex);
        Vec3 b = contour.get(bIndex);
        Vec3 c = contour.get(cIndex);
        for (int index : remaining) {
            if (index == aIndex || index == bIndex || index == cIndex) continue;
            if (pointInTriangle(contour.get(index), a, b, c)) return true;
        }
        return false;
    }

    private static boolean pointInTriangle(Vec3 p, Vec3 a, Vec3 b, Vec3 c) {
        double ab = cross2d(a, b, p);
        double bc = cross2d(b, c, p);
        double ca = cross2d(c, a, p);
        return ab >= -1e-9 && bc >= -1e-9 && ca >= -1e-9;
    }

    private static List<List<Vec3>> splitStrokes(List<Vec3> points) {
        List<List<Vec3>> strokes = new ArrayList<>();
        List<Vec3> stroke = new ArrayList<>();
        for (Vec3 point : points) {
            if (!isFinite(point)) {
                if (!stroke.isEmpty()) strokes.add(stroke);
                stroke = new ArrayList<>();
            } else {
                stroke.add(point);
            }
        }
        if (!stroke.isEmpty()) strokes.add(stroke);
        return strokes;
    }

    private static List<Vec3> cleanStroke(List<Vec3> stroke, double minDistance) {
        List<Vec3> clean = new ArrayList<>();
        for (Vec3 point : stroke) {
            if (!isFinite(point)) continue;
            if (clean.isEmpty() || clean.get(clean.size() - 1).distanceTo(point) >= minDistance) {
                clean.add(point);
            }
        }
        return clean;
    }

    private static List<Vec3> trimClosedContour(List<Vec3> stroke, double strokeRadius) {
        List<Vec3> contour = new ArrayList<>(stroke);
        if (contour.size() > 3
            && contour.get(0).distanceTo(contour.get(contour.size() - 1)) <= closeDistance(contour, strokeRadius)) {
            contour.remove(contour.size() - 1);
        }
        return contour;
    }

    private static List<Vec3> cleanContour(List<Vec3> contour) {
        List<Vec3> clean = cleanStroke(contour, 0.003);
        for (int i = clean.size() - 1; i >= 0 && clean.size() > 3; i--) {
            Vec3 prev = clean.get((i + clean.size() - 1) % clean.size());
            Vec3 curr = clean.get(i);
            Vec3 next = clean.get((i + 1) % clean.size());
            if (Math.abs(cross2d(prev, curr, next)) < 1e-7) clean.remove(i);
        }
        return clean;
    }

    private static List<Vec3> ensureCounterClockwise(List<Vec3> contour) {
        if (signedArea(contour) >= 0) return contour;
        List<Vec3> reversed = new ArrayList<>(contour.size());
        for (int i = contour.size() - 1; i >= 0; i--) reversed.add(contour.get(i));
        return reversed;
    }

    private static boolean isClosedContour(List<Vec3> stroke, double strokeRadius) {
        if (stroke.size() < 3) return false;
        return stroke.get(0).distanceTo(stroke.get(stroke.size() - 1)) <= closeDistance(stroke, strokeRadius);
    }

    private static double closeDistance(List<Vec3> stroke, double strokeRadius) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Vec3 point : stroke) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        double diagonal = Math.hypot(maxX - minX, maxY - minY);
        return Math.max(strokeRadius * 3.0, diagonal * 0.12);
    }

    private static double signedArea(List<Vec3> contour) {
        double area = 0;
        for (int i = 0; i < contour.size(); i++) {
            Vec3 a = contour.get(i);
            Vec3 b = contour.get((i + 1) % contour.size());
            area += a.x * b.y - b.x * a.y;
        }
        return area * 0.5;
    }

    private static double cross2d(Vec3 a, Vec3 b, Vec3 c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static Vec3 atDepth(Vec3 point, double z) {
        return new Vec3(point.x, point.y, z);
    }

    private static void copyTriangles(Mesh target, Mesh source) {
        for (Triangle triangle : source.getTriangles()) target.addTriangle(triangle);
    }

    private static void addQuad(Mesh m, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        m.addTriangle(new Triangle(a, b, c));
        m.addTriangle(new Triangle(a, c, d));
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    // ═══════════════════════════════════════════════
    //  Fábrica de geometrias primitivas
    // ═══════════════════════════════════════════════

    /** Cubo centrado na origem com metade do tamanho = size. */
    public static Mesh createBox(double sx, double sy, double sz) {
        Mesh m = new Mesh("Box");
        double x = sx, y = sy, z = sz;

        // 8 vértices
        Vec3[] v = {
            new Vec3(-x,-y,-z), new Vec3( x,-y,-z),
            new Vec3( x, y,-z), new Vec3(-x, y,-z),
            new Vec3(-x,-y, z), new Vec3( x,-y, z),
            new Vec3( x, y, z), new Vec3(-x, y, z),
        };
        // 6 faces × 2 triângulos
        int[][] faces = {
            {0,1,2},{0,2,3}, // frente
            {5,4,7},{5,7,6}, // trás
            {4,0,3},{4,3,7}, // esquerda
            {1,5,6},{1,6,2}, // direita
            {3,2,6},{3,6,7}, // topo
            {4,5,1},{4,1,0}, // base
        };
        for (int[] f : faces)
            m.addTriangle(new Triangle(v[f[0]], v[f[1]], v[f[2]]));
        return m;
    }

    /** Esfera UV com raio r, stacks lat e slices lon. */
    public static Mesh createSphere(double r, int stacks, int slices) {
        Mesh m = new Mesh("Sphere");
        for (int i = 0; i < stacks; i++) {
            double phi0 = Math.PI * i / stacks - Math.PI/2;
            double phi1 = Math.PI * (i+1) / stacks - Math.PI/2;
            for (int j = 0; j < slices; j++) {
                double th0 = 2*Math.PI * j / slices;
                double th1 = 2*Math.PI * (j+1) / slices;
                Vec3 a = sphereVert(r, phi0, th0);
                Vec3 b = sphereVert(r, phi0, th1);
                Vec3 c = sphereVert(r, phi1, th1);
                Vec3 d = sphereVert(r, phi1, th0);
                double u0 = (double)j / slices;
                double u1 = (double)(j + 1) / slices;
                double v0 = 1.0 - (double)i / stacks;
                double v1 = 1.0 - (double)(i + 1) / stacks;
                m.addTriangle(new Triangle(a, b, c, u0, v0, u1, v0, u1, v1));
                m.addTriangle(new Triangle(a, c, d, u0, v0, u1, v1, u0, v1));
            }
        }
        return m;
    }
    private static Vec3 sphereVert(double r, double phi, double theta) {
        return new Vec3(
            r * Math.cos(phi) * Math.cos(theta),
            r * Math.sin(phi),
            r * Math.cos(phi) * Math.sin(theta)
        );
    }

    /** Cone com raio base r, altura h e n lados. */
    public static Mesh createCone(double r, double h, int sides) {
        Mesh m = new Mesh("Cone");
        Vec3 apex = new Vec3(0, h/2, 0);
        Vec3 base = new Vec3(0, -h/2, 0);
        for (int i = 0; i < sides; i++) {
            double a0 = 2*Math.PI * i / sides;
            double a1 = 2*Math.PI * (i+1) / sides;
            Vec3 p0 = new Vec3(r*Math.cos(a0), -h/2, r*Math.sin(a0));
            Vec3 p1 = new Vec3(r*Math.cos(a1), -h/2, r*Math.sin(a1));
            m.addTriangle(new Triangle(apex, p0, p1)); // lateral
            m.addTriangle(new Triangle(base, p1, p0)); // base
        }
        return m;
    }

    /** Cilindro com raio r, altura h e n lados. */
    public static Mesh createCylinder(double r, double h, int sides) {
        Mesh m = new Mesh("Cylinder");
        Vec3 topCenter = new Vec3(0,  h/2, 0);
        Vec3 botCenter = new Vec3(0, -h/2, 0);
        for (int i = 0; i < sides; i++) {
            double a0 = 2*Math.PI * i / sides;
            double a1 = 2*Math.PI * (i+1) / sides;
            Vec3 t0 = new Vec3(r*Math.cos(a0),  h/2, r*Math.sin(a0));
            Vec3 t1 = new Vec3(r*Math.cos(a1),  h/2, r*Math.sin(a1));
            Vec3 b0 = new Vec3(r*Math.cos(a0), -h/2, r*Math.sin(a0));
            Vec3 b1 = new Vec3(r*Math.cos(a1), -h/2, r*Math.sin(a1));
            m.addTriangle(new Triangle(t0, b0, b1)); // lateral
            m.addTriangle(new Triangle(t0, b1, t1));
            m.addTriangle(new Triangle(topCenter, t1, t0)); // tampa topo
            m.addTriangle(new Triangle(botCenter, b0, b1)); // tampa base
        }
        return m;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FOGUETE
    // ══════════════════════════════════════════════════════════════════════
    //
    //  A nave e montada em duas malhas para permitir duas cores sem precisar
    //  de material por triangulo: o casco (branco) e os apendices (cor do
    //  modelo). As duas compartilham o mesmo sistema de coordenadas — eixo de
    //  simetria em +Y, centradas na origem — entao recebem a mesma transformacao
    //  de rotacao e ficam sempre encaixadas.
    //
    //          ^ +Y (direcao de voo)
    //          A          bico conico      0,18h .. 0,50h
    //         |_|         corpo cilindrico -0,34h .. 0,18h
    //        /|_|\        aletas           -0,34h .. -0,10h
    //         \_/         bocal (sino)     -0,50h .. -0,34h
    //
    //  Proporcoes conferidas para a silhueta continuar legivel com poucos
    //  pixels na tela, que e o caso na visao do sistema inteiro.

    private static final double ROCKET_NOSE_BASE   =  0.18;
    private static final double ROCKET_BODY_BOTTOM = -0.34;
    private static final double ROCKET_NOZZLE_END  = -0.50;
    private static final double ROCKET_FIN_TOP     = -0.10;

    /**
     * Casco do foguete: corpo cilindrico com bico conico.
     *
     * @param r     raio do corpo
     * @param h     altura total da nave (bico + corpo + bocal)
     * @param sides lados do cilindro; 10 ja da silhueta redonda o bastante
     */
    public static Mesh createRocketHull(double r, double h, int sides) {
        Mesh m = new Mesh("RocketHull");
        int n = Math.max(6, sides);
        double noseBase = h * ROCKET_NOSE_BASE;
        double bodyBottom = h * ROCKET_BODY_BOTTOM;
        Vec3 tip = new Vec3(0, h * 0.5, 0);
        Vec3 bottomCenter = new Vec3(0, bodyBottom, 0);

        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n;
            double a1 = 2 * Math.PI * (i + 1) / n;
            Vec3 noseRing0 = ring(r, noseBase, a0);
            Vec3 noseRing1 = ring(r, noseBase, a1);
            Vec3 base0 = ring(r, bodyBottom, a0);
            Vec3 base1 = ring(r, bodyBottom, a1);

            m.addTriangle(new Triangle(tip, noseRing0, noseRing1));   // bico
            addQuad(m, noseRing0, base0, base1, noseRing1);           // corpo
            m.addTriangle(new Triangle(bottomCenter, base1, base0));  // fundo
        }
        return m;
    }

    /**
     * Apendices do foguete: aletas triangulares e bocal do motor.
     *
     * @param r        raio do corpo (as aletas se projetam a partir dele)
     * @param h        altura total da nave, a mesma passada ao casco
     * @param finCount numero de aletas distribuidas ao redor do corpo
     */
    public static Mesh createRocketFins(double r, double h, int finCount) {
        Mesh m = new Mesh("RocketFins");
        int fins = Math.max(3, finCount);
        double bodyBottom = h * ROCKET_BODY_BOTTOM;
        double finTop = h * ROCKET_FIN_TOP;
        double nozzleEnd = h * ROCKET_NOZZLE_END;
        double finSpan = r * 2.1;

        for (int i = 0; i < fins; i++) {
            double angle = 2 * Math.PI * i / fins;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            // Espessura da aleta na direcao tangencial, para ela ter volume.
            double tx = -sin * r * 0.16;
            double tz = cos * r * 0.16;

            Vec3 rootTop = new Vec3(cos * r * 0.92, finTop, sin * r * 0.92);
            Vec3 rootBottom = new Vec3(cos * r * 0.92, bodyBottom, sin * r * 0.92);
            Vec3 tipBottom = new Vec3(cos * finSpan, bodyBottom, sin * finSpan);

            // Duas faces da aleta, deslocadas para os lados do plano radial.
            Vec3 a0 = rootTop.add(tx, 0, tz);
            Vec3 b0 = rootBottom.add(tx, 0, tz);
            Vec3 c0 = tipBottom.add(tx, 0, tz);
            Vec3 a1 = rootTop.add(-tx, 0, -tz);
            Vec3 b1 = rootBottom.add(-tx, 0, -tz);
            Vec3 c1 = tipBottom.add(-tx, 0, -tz);

            m.addTriangle(new Triangle(a0, b0, c0));
            m.addTriangle(new Triangle(a1, c1, b1));
            addQuad(m, a0, c0, c1, a1);   // borda inclinada
            addQuad(m, b0, b1, c1, c0);   // borda inferior
        }

        // Bocal do motor: tronco de cone que abre para baixo.
        int n = 10;
        double throat = r * 0.62;
        double bell = r * 1.05;
        Vec3 bellCenter = new Vec3(0, nozzleEnd, 0);
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n;
            double a1 = 2 * Math.PI * (i + 1) / n;
            Vec3 t0 = ring(throat, bodyBottom, a0);
            Vec3 t1 = ring(throat, bodyBottom, a1);
            Vec3 e0 = ring(bell, nozzleEnd, a0);
            Vec3 e1 = ring(bell, nozzleEnd, a1);
            addQuad(m, t0, e0, e1, t1);
            m.addTriangle(new Triangle(bellCenter, e0, e1));
        }
        return m;
    }

    /** Ponto de um anel horizontal de raio {@code r} na altura {@code y}. */
    private static Vec3 ring(double r, double y, double angle) {
        return new Vec3(r * Math.cos(angle), y, r * Math.sin(angle));
    }

    /** Tetraedro regular. */
    public static Mesh createTetrahedron(double r) {
        Mesh m = new Mesh("Tetrahedron");
        Vec3 v0 = new Vec3( 0,  r,  0);
        Vec3 v1 = new Vec3( r * Math.sqrt(8.0/9), -r/3.0,  0);
        Vec3 v2 = new Vec3(-r * Math.sqrt(2.0/9), -r/3.0,  r * Math.sqrt(2.0/3));
        Vec3 v3 = new Vec3(-r * Math.sqrt(2.0/9), -r/3.0, -r * Math.sqrt(2.0/3));
        m.addTriangle(new Triangle(v0, v1, v2));
        m.addTriangle(new Triangle(v0, v2, v3));
        m.addTriangle(new Triangle(v0, v3, v1));
        m.addTriangle(new Triangle(v1, v3, v2));
        return m;
    }

    /** Octaedro regular. */
    public static Mesh createOctahedron(double r) {
        Mesh m = new Mesh("Octahedron");
        Vec3 t = new Vec3(0, r, 0), b = new Vec3(0,-r, 0);
        Vec3 f = new Vec3(0, 0, r), bk = new Vec3(0, 0,-r);
        Vec3 l = new Vec3(-r,0, 0), ri = new Vec3(r, 0, 0);
        m.addTriangle(new Triangle(t, ri, f));
        m.addTriangle(new Triangle(t, f,  l));
        m.addTriangle(new Triangle(t, l,  bk));
        m.addTriangle(new Triangle(t, bk, ri));
        m.addTriangle(new Triangle(b, f,  ri));
        m.addTriangle(new Triangle(b, l,  f));
        m.addTriangle(new Triangle(b, bk, l));
        m.addTriangle(new Triangle(b, ri, bk));
        return m;
    }

    /** Dodecaedro aproximado (icosaedro subdividido). */
    public static Mesh createIcosahedron(double r) {
        double phi = (1 + Math.sqrt(5)) / 2;
        Vec3[] v = {
            new Vec3(-1, phi, 0).normalize().mul(r), new Vec3( 1, phi, 0).normalize().mul(r),
            new Vec3(-1,-phi, 0).normalize().mul(r), new Vec3( 1,-phi, 0).normalize().mul(r),
            new Vec3( 0,-1, phi).normalize().mul(r), new Vec3( 0, 1, phi).normalize().mul(r),
            new Vec3( 0,-1,-phi).normalize().mul(r), new Vec3( 0, 1,-phi).normalize().mul(r),
            new Vec3( phi, 0,-1).normalize().mul(r), new Vec3( phi, 0, 1).normalize().mul(r),
            new Vec3(-phi, 0,-1).normalize().mul(r), new Vec3(-phi, 0, 1).normalize().mul(r),
        };
        int[][] f = {
            {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},
            {1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
            {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},
            {4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}
        };
        Mesh m = new Mesh("Icosahedron");
        for (int[] face : f)
            m.addTriangle(new Triangle(v[face[0]], v[face[1]], v[face[2]]));
        return m;
    }

    /** Torus com raio externo R, raio do tubo r, e subdivisões. */
    public static Mesh createTorus(double R, double r, int sides, int rings) {
        Mesh m = new Mesh("Torus");
        for (int i = 0; i < rings; i++) {
            double u0 = 2*Math.PI * i / rings;
            double u1 = 2*Math.PI * (i+1) / rings;
            for (int j = 0; j < sides; j++) {
                double v0 = 2*Math.PI * j / sides;
                double v1 = 2*Math.PI * (j+1) / sides;
                Vec3 a = torusVert(R, r, u0, v0);
                Vec3 b = torusVert(R, r, u1, v0);
                Vec3 c = torusVert(R, r, u1, v1);
                Vec3 d = torusVert(R, r, u0, v1);
                m.addTriangle(new Triangle(a, b, c));
                m.addTriangle(new Triangle(a, c, d));
            }
        }
        return m;
    }
    private static Vec3 torusVert(double R, double r, double u, double v) {
        return new Vec3(
            (R + r*Math.cos(v)) * Math.cos(u),
            r * Math.sin(v),
            (R + r*Math.cos(v)) * Math.sin(u)
        );
    }
}
