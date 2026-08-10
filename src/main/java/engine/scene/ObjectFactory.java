package engine.scene;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.physics.RigidBody;
import engine.renderer.Mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fábrica de objetos 3D.
 * Recebe parâmetros de desenho (tipo, tamanho, cor, física) e produz SceneObject.
 */
public class ObjectFactory {

    private static final Random RNG = new Random();
    private static int counter = 0;

    public enum ShapeType {
        BOX, SPHERE, CONE, CYLINDER, TETRAHEDRON, OCTAHEDRON, ICOSAHEDRON, TORUS, DRAWN, RANDOM
    }

    /**
     * Como o traco desenhado vira geometria 3D.
     *
     * <p>A extrusao produz um recorte plano com espessura; os outros dois modos
     * geram volume de verdade a partir do mesmo desenho.
     */
    public enum SolidMode {
        /** Recorte plano com espessura — placas, letreiros, pecas chatas. */
        EXTRUDE("Extrudar (placa)",
            "O contorno ganha espessura constante, como um recorte de chapa."),
        /** Contorno fechado vira solido abaulado, mais grosso no centro. */
        INFLATE("Inflar (volume)",
            "O contorno fechado incha: grosso no centro, afinando ate a borda."),
        /** Traco girado 360 graus: vasos, tacas, pecas de xadrez. */
        REVOLVE("Revolucao (torno)",
            "O traco vira o perfil de um torno e gira 360 graus em torno do eixo vertical.");

        public final String label;
        public final String description;

        SolidMode(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public static String[] labels() {
            SolidMode[] values = values();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i].label;
            return labels;
        }

        public static SolidMode byLabel(String label) {
            for (SolidMode mode : values()) {
                if (mode.label.equals(label)) return mode;
            }
            return EXTRUDE;
        }
    }

    public enum PhysicsPreset {
        WOOD    (0.35, 0.62, 700,   0.992, 0.86, RigidBody.PhysicsType.DYNAMIC),
        RUBBER  (0.88, 0.82, 1100,  0.996, 0.90, RigidBody.PhysicsType.DYNAMIC),
        STEEL   (0.52, 0.34, 7850,  0.998, 0.94, RigidBody.PhysicsType.DYNAMIC),
        FOAM    (0.22, 0.76, 75,    0.985, 0.80, RigidBody.PhysicsType.DYNAMIC),
        STATIC  (0.0,  0.90, 1000,  1.000, 1.00, RigidBody.PhysicsType.STATIC),
        HEAVY   (0.18, 0.48, 12000, 0.997, 0.92, RigidBody.PhysicsType.DYNAMIC),
        RIGID   (0.35, 0.55, 1000,  0.995, 0.88, RigidBody.PhysicsType.DYNAMIC),
        BOUNCY  (0.85, 0.70, 950,   0.996, 0.90, RigidBody.PhysicsType.DYNAMIC),
        LIGHT   (0.60, 0.65, 180,   0.990, 0.82, RigidBody.PhysicsType.DYNAMIC);

        public final double restitution;
        public final double friction;
        public final double densityKgM3;
        public final double linearDamping;
        public final double angularDamping;
        public final RigidBody.PhysicsType type;

        PhysicsPreset(double restitution, double friction, double densityKgM3,
                      double linearDamping, double angularDamping,
                      RigidBody.PhysicsType type) {
            this.restitution = restitution;
            this.friction = friction;
            this.densityKgM3 = densityKgM3;
            this.linearDamping = linearDamping;
            this.angularDamping = angularDamping;
            this.type = type;
        }
    }

    /**
     * Cria um SceneObject completo.
     *
     * @param shapeType  tipo de geometria
     * @param preset     preset de física
     * @param color      cor do material
     * @param drawW      largura desenhada (normalizada 0..1)
     * @param drawH      altura desenhada  (normalizada 0..1)
     * @param spawnY     altura inicial de spawn
     */
    public static SceneObject create(ShapeType shapeType, PhysicsPreset preset,
                                     ColorRGBA color,
                                     double drawW, double drawH,
                                     double spawnY) {
        // Dimensões baseadas no desenho
        double sx = Math.max(0.3, drawW * 2.5);
        double sy = Math.max(0.3, drawH * 2.5);
        double sz = Math.max(0.3, (sx + sy) * 0.4);
        double r  = Math.max(0.3, (sx + sy) / 4.0);

        // Geometria
        Mesh mesh = buildMesh(shapeType, sx, sy, sz, r);

        // Raio de colisão aproximado
        double boundR = Math.max(r, Math.max(sx, Math.max(sy, sz)) * 0.6);

        // Posição inicial aleatória na arena
        Vec3 spawnPos = new Vec3(
            (RNG.nextDouble() - 0.5) * 14,
            spawnY + RNG.nextDouble() * 3,
            (RNG.nextDouble() - 0.5) * 10
        );

        // Corpo rígido: massa calculada por volume aproximado e densidade realista.
        double volume = estimateVolume(shapeType, sx, sy, sz, r);
        double mass = preset.type == RigidBody.PhysicsType.STATIC
            ? Double.MAX_VALUE
            : Math.max(0.05, volume * preset.densityKgM3);
        RigidBody body = new RigidBody(spawnPos, mass, preset.restitution,
            preset.friction, preset.linearDamping, preset.angularDamping, preset.type);
        body.setBoundingRadius(boundR);

        // Velocidade inicial aleatória
        if (preset.type == RigidBody.PhysicsType.DYNAMIC) {
            body.setVelocity(new Vec3(
                (RNG.nextDouble() - 0.5) * 4,
                1.0 + RNG.nextDouble() * 2,
                (RNG.nextDouble() - 0.5) * 4
            ));
            body.setAngularVelocity(new Vec3(
                (RNG.nextDouble() - 0.5) * 5,
                (RNG.nextDouble() - 0.5) * 5,
                (RNG.nextDouble() - 0.5) * 5
            ));
        }

        String name = shapeType.name().toLowerCase() + "_" + (++counter);
        return new SceneObject(name, mesh, body, color, shapeType.name());
    }

    /** Cria um objeto 3D a partir do traco livre, no modo de extrusao classico. */
    public static SceneObject createFromDrawing(List<double[]> normalizedPath,
                                                PhysicsPreset preset,
                                                ColorRGBA color,
                                                double normalizedWidth,
                                                double normalizedHeight,
                                                double spawnY) {
        return createFromDrawing(normalizedPath, preset, color,
            normalizedWidth, normalizedHeight, spawnY, SolidMode.EXTRUDE);
    }

    /**
     * Cria um objeto 3D a partir do traco livre desenhado no canvas.
     *
     * @param mode como o traco vira geometria — ver {@link SolidMode}
     */
    public static SceneObject createFromDrawing(List<double[]> normalizedPath,
                                                PhysicsPreset preset,
                                                ColorRGBA color,
                                                double normalizedWidth,
                                                double normalizedHeight,
                                                double spawnY,
                                                SolidMode mode) {
        if (normalizedPath == null || normalizedPath.size() < 2) {
            return create(ShapeType.BOX, preset, color, normalizedWidth, normalizedHeight, spawnY);
        }

        double scale = 5.2;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double[] p : normalizedPath) {
            if (!isFinitePoint(p)) continue;
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        if (!Double.isFinite(minX)) {
            return create(ShapeType.BOX, preset, color, normalizedWidth, normalizedHeight, spawnY);
        }
        double cx = (minX + maxX) * 0.5;
        double cy = (minY + maxY) * 0.5;
        double width = Math.max(0.15, (maxX - minX) * scale);
        double height = Math.max(0.15, (maxY - minY) * scale);
        double depth = Math.max(0.12, Math.min(0.5, (width + height) * 0.08));
        double strokeRadius = Math.max(0.035, Math.min(0.13, (width + height) * 0.02));

        List<Vec3> path = new ArrayList<>(normalizedPath.size());
        double maxRadius = 0.25;
        for (double[] p : normalizedPath) {
            if (!isFinitePoint(p)) {
                path.add(new Vec3(Double.NaN, Double.NaN, Double.NaN));
                continue;
            }
            double x = (p[0] - cx) * scale;
            double y = (cy - p[1]) * scale;
            Vec3 point = new Vec3(x, y, 0);
            path.add(point);
            maxRadius = Math.max(maxRadius, Math.sqrt(x * x + y * y) + strokeRadius + depth);
        }

        // Cada modo tem geometria e volume proprios: a massa precisa sair do
        // solido que o usuario realmente vai ver na cena.
        double inflateThickness = Math.max(0.10, Math.min(width, height) * 0.42);
        Mesh mesh;
        double visualVolume;
        switch (mode) {
            case INFLATE -> {
                mesh = Mesh.createInflatedDrawing("DrawnInflated", path, inflateThickness);
                // Solido abaulado: cerca de 2/3 do prisma de mesma area e altura,
                // contando as duas metades.
                visualVolume = Math.max(0.02, contourArea(path) * inflateThickness * 4.0 / 3.0);
                maxRadius = Math.max(maxRadius, inflateThickness);
            }
            case REVOLVE -> {
                double axisX = revolutionAxis(path);
                mesh = Mesh.createRevolvedDrawing("DrawnRevolved", path, axisX);
                visualVolume = Math.max(0.02, revolutionVolume(path, axisX));
                maxRadius = Math.max(maxRadius, profileMaxRadius(path, axisX));
            }
            default -> {
                mesh = Mesh.createExtrudedDrawing("DrawnShape", path, strokeRadius, depth);
                visualVolume = drawingVolume(path, strokeRadius, depth);
            }
        }
        double mass = preset.type == RigidBody.PhysicsType.STATIC
            ? Double.MAX_VALUE
            : Math.max(0.05, visualVolume * preset.densityKgM3);

        Vec3 spawnPos = new Vec3(
            (RNG.nextDouble() - 0.5) * 10,
            spawnY + RNG.nextDouble() * 2,
            (RNG.nextDouble() - 0.5) * 7
        );
        RigidBody body = new RigidBody(spawnPos, mass, preset.restitution,
            preset.friction, preset.linearDamping, preset.angularDamping, preset.type);
        body.setBoundingRadius(maxRadius);

        if (preset.type == RigidBody.PhysicsType.DYNAMIC) {
            body.setVelocity(new Vec3(
                (RNG.nextDouble() - 0.5) * 2.5,
                0.8 + RNG.nextDouble() * 1.4,
                (RNG.nextDouble() - 0.5) * 2.5
            ));
            body.setAngularVelocity(new Vec3(
                (RNG.nextDouble() - 0.5) * 3.0,
                (RNG.nextDouble() - 0.5) * 3.0,
                (RNG.nextDouble() - 0.5) * 3.0
            ));
        }

        return new SceneObject("drawn_" + (++counter), mesh, body, color, "DRAWN");
    }

    // ── Geometria dos modos de volume ──────────────────────────────────

    /** Area do maior contorno fechado do desenho, pela formula do shoelace. */
    private static double contourArea(List<Vec3> path) {
        double best = 0;
        List<Vec3> stroke = new ArrayList<>();
        for (Vec3 point : path) {
            if (!isFiniteVec(point)) {
                best = Math.max(best, Math.abs(shoelace(stroke)));
                stroke.clear();
            } else {
                stroke.add(point);
            }
        }
        best = Math.max(best, Math.abs(shoelace(stroke)));
        return Math.max(0.02, best);
    }

    private static double shoelace(List<Vec3> stroke) {
        if (stroke.size() < 3) return 0;
        double sum = 0;
        for (int i = 0; i < stroke.size(); i++) {
            Vec3 a = stroke.get(i);
            Vec3 b = stroke.get((i + 1) % stroke.size());
            sum += a.x * b.y - b.x * a.y;
        }
        return sum * 0.5;
    }

    /**
     * Eixo de giro do torno: a borda esquerda do desenho.
     *
     * <p>Girar em torno da lateral e o que produz a peca cheia — usar o centro
     * faria o solido furar a si mesmo, ja que os dois lados do traco se
     * sobreporiam.
     */
    private static double revolutionAxis(List<Vec3> path) {
        double minX = Double.POSITIVE_INFINITY;
        for (Vec3 point : path) {
            if (isFiniteVec(point)) minX = Math.min(minX, point.x);
        }
        return Double.isFinite(minX) ? minX : 0;
    }

    /** Maior raio do perfil, usado como raio envolvente do solido de revolucao. */
    private static double profileMaxRadius(List<Vec3> path, double axisX) {
        double radius = 0.25;
        for (Vec3 point : path) {
            if (!isFiniteVec(point)) continue;
            radius = Math.max(radius, Math.hypot(Math.abs(point.x - axisX), Math.abs(point.y)));
        }
        return radius;
    }

    /**
     * Volume do solido de revolucao, integrando discos {@code pi r^2 dy} ao
     * longo do perfil ordenado por altura.
     */
    private static double revolutionVolume(List<Vec3> path, double axisX) {
        List<Vec3> profile = new ArrayList<>();
        for (Vec3 point : path) {
            if (isFiniteVec(point)) profile.add(point);
        }
        if (profile.size() < 2) return 0.02;
        profile.sort((a, b) -> Double.compare(a.y, b.y));

        double volume = 0;
        for (int i = 0; i < profile.size() - 1; i++) {
            Vec3 lower = profile.get(i);
            Vec3 upper = profile.get(i + 1);
            double dy = upper.y - lower.y;
            if (dy <= 0) continue;
            // Tronco de cone entre os dois raios.
            double r1 = Math.abs(lower.x - axisX);
            double r2 = Math.abs(upper.x - axisX);
            volume += Math.PI * dy * (r1 * r1 + r1 * r2 + r2 * r2) / 3.0;
        }
        return volume;
    }

    /** Cria um objeto com parâmetros aleatórios (para demo e spawn rápido). */
    public static SceneObject createRandom() {
        ShapeType[] shapes = { ShapeType.BOX, ShapeType.SPHERE, ShapeType.CONE,
            ShapeType.CYLINDER, ShapeType.TETRAHEDRON, ShapeType.OCTAHEDRON,
            ShapeType.ICOSAHEDRON, ShapeType.TORUS };
        ShapeType shape = shapes[RNG.nextInt(shapes.length)];

        PhysicsPreset[] presets = { PhysicsPreset.WOOD, PhysicsPreset.RUBBER, PhysicsPreset.FOAM, PhysicsPreset.STEEL };
        PhysicsPreset preset = presets[RNG.nextInt(presets.length)];

        ColorRGBA[] colors = ColorRGBA.PALETTE;
        ColorRGBA color = colors[RNG.nextInt(colors.length)];

        double drawW = 0.2 + RNG.nextDouble() * 0.5;
        double drawH = 0.2 + RNG.nextDouble() * 0.5;

        return create(shape, preset, color, drawW, drawH, 6 + RNG.nextDouble() * 4);
    }

    private static Mesh buildMesh(ShapeType type,
                                   double sx, double sy, double sz, double r) {
        return switch (type) {
            case BOX          -> Mesh.createBox(sx/2, sy/2, sz/2);
            case SPHERE       -> Mesh.createSphere(r, 10, 12);
            case CONE         -> Mesh.createCone(r, sy, 8);
            case CYLINDER     -> Mesh.createCylinder(r*0.7, sy, 10);
            case TETRAHEDRON  -> Mesh.createTetrahedron(r);
            case OCTAHEDRON   -> Mesh.createOctahedron(r);
            case ICOSAHEDRON  -> Mesh.createIcosahedron(r);
            case TORUS        -> Mesh.createTorus(r*0.8, r*0.25, 8, 14);
            case DRAWN        -> Mesh.createBox(sx/2, sy/2, sz/2);
            case RANDOM       -> buildMesh(randomShape(), sx, sy, sz, r);
        };
    }

    private static ShapeType randomShape() {
        ShapeType[] s = { ShapeType.BOX, ShapeType.SPHERE, ShapeType.CONE,
                          ShapeType.TETRAHEDRON, ShapeType.OCTAHEDRON, ShapeType.ICOSAHEDRON, ShapeType.TORUS };
        return s[RNG.nextInt(s.length)];
    }

    private static double estimateVolume(ShapeType type, double sx, double sy, double sz, double r) {
        return switch (type) {
            case SPHERE -> 4.0 / 3.0 * Math.PI * r * r * r;
            case CONE -> Math.PI * r * r * sy / 3.0;
            case CYLINDER -> Math.PI * r * r * sy;
            case TORUS -> 2.0 * Math.PI * Math.PI * (r * 0.8) * (r * 0.25) * (r * 0.25);
            case TETRAHEDRON, OCTAHEDRON, ICOSAHEDRON -> Math.max(0.02, 0.45 * sx * sy * sz);
            case BOX, DRAWN, RANDOM -> Math.max(0.02, sx * sy * sz);
        };
    }

    private static double pathLength(List<Vec3> path) {
        double length = 0;
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1);
            Vec3 b = path.get(i);
            if (!isFiniteVec(a) || !isFiniteVec(b)) continue;
            length += b.distanceTo(a);
        }
        return Math.max(0.1, length);
    }

    private static double drawingVolume(List<Vec3> path, double strokeRadius, double depth) {
        double volume = pathLength(path) * (strokeRadius * 2.0) * depth;
        double contourArea = 0;
        List<Vec3> stroke = new ArrayList<>();
        for (Vec3 point : path) {
            if (!isFiniteVec(point)) {
                contourArea += closedStrokeArea(stroke, strokeRadius);
                stroke.clear();
            } else {
                stroke.add(point);
            }
        }
        contourArea += closedStrokeArea(stroke, strokeRadius);
        volume = Math.max(volume, contourArea * depth);
        return Math.max(0.015, volume);
    }

    private static double closedStrokeArea(List<Vec3> stroke, double strokeRadius) {
        if (stroke.size() < 3) return 0;
        Vec3 first = stroke.get(0);
        Vec3 last = stroke.get(stroke.size() - 1);
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
        if (first.distanceTo(last) > Math.max(strokeRadius * 3.0, diagonal * 0.12)) return 0;

        double area = 0;
        for (int i = 0; i < stroke.size(); i++) {
            Vec3 a = stroke.get(i);
            Vec3 b = stroke.get((i + 1) % stroke.size());
            area += a.x * b.y - b.x * a.y;
        }
        return Math.abs(area) * 0.5;
    }

    private static boolean isFinitePoint(double[] p) {
        return p != null && p.length >= 2 && Double.isFinite(p[0]) && Double.isFinite(p[1]);
    }

    private static boolean isFiniteVec(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
