package engine.scene;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.physics.RigidBody;
import engine.renderer.Mesh;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Persiste cenas do sandbox em Properties, incluindo malhas desenhadas.
 */
public final class ScenarioStore {

    private ScenarioStore() {}

    public static void save(Path path, List<SceneObject> objects) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("object.count", Integer.toString(objects.size()));
        for (int i = 0; i < objects.size(); i++) {
            writeObject(properties, "object." + i + ".", objects.get(i));
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            properties.store(writer, "Physics3DEngine sandbox scenario");
        }
    }

    public static List<SceneObject> load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        int count = integer(properties, "object.count", 0);
        List<SceneObject> objects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            objects.add(readObject(properties, "object." + i + "."));
        }
        return objects;
    }

    private static void writeObject(Properties properties, String prefix, SceneObject object) {
        RigidBody body = object.getBody();
        properties.setProperty(prefix + "name", object.getName());
        properties.setProperty(prefix + "shape", object.getShapeType());
        properties.setProperty(prefix + "color", numbers(object.getColor().r, object.getColor().g, object.getColor().b, object.getColor().a));
        properties.setProperty(prefix + "position", vector(body.getPosition()));
        properties.setProperty(prefix + "velocity", vector(body.getVelocity()));
        properties.setProperty(prefix + "rotation", vector(body.getRotation()));
        properties.setProperty(prefix + "angularVelocity", vector(body.getAngularVelocity()));
        properties.setProperty(prefix + "mass", Double.toString(body.getMass()));
        properties.setProperty(prefix + "restitution", Double.toString(body.getRestitution()));
        properties.setProperty(prefix + "friction", Double.toString(body.getFriction()));
        properties.setProperty(prefix + "linearDamping", Double.toString(body.getLinearDamping()));
        properties.setProperty(prefix + "angularDamping", Double.toString(body.getAngularDamping()));
        properties.setProperty(prefix + "type", body.getType().name());
        properties.setProperty(prefix + "radius", Double.toString(body.getBoundingRadius()));

        List<Mesh.Triangle> triangles = object.getMesh().getTriangles();
        properties.setProperty(prefix + "mesh.count", Integer.toString(triangles.size()));
        for (int i = 0; i < triangles.size(); i++) {
            Mesh.Triangle tri = triangles.get(i);
            properties.setProperty(prefix + "mesh." + i, numbers(
                tri.v0.x, tri.v0.y, tri.v0.z,
                tri.v1.x, tri.v1.y, tri.v1.z,
                tri.v2.x, tri.v2.y, tri.v2.z,
                tri.u0, tri.v0uv, tri.u1, tri.v1uv, tri.u2, tri.v2uv
            ));
        }
    }

    private static SceneObject readObject(Properties properties, String prefix) {
        Mesh mesh = new Mesh(properties.getProperty(prefix + "shape", "ScenarioMesh"));
        int triangleCount = integer(properties, prefix + "mesh.count", 0);
        for (int i = 0; i < triangleCount; i++) {
            double[] values = values(properties.getProperty(prefix + "mesh." + i, ""), 15);
            mesh.addTriangle(new Mesh.Triangle(
                new Vec3(values[0], values[1], values[2]),
                new Vec3(values[3], values[4], values[5]),
                new Vec3(values[6], values[7], values[8]),
                values[9], values[10], values[11], values[12], values[13], values[14]
            ));
        }

        Vec3 position = vec(properties.getProperty(prefix + "position", "0,0,0"));
        RigidBody.PhysicsType type = RigidBody.PhysicsType.valueOf(properties.getProperty(prefix + "type", "DYNAMIC"));
        RigidBody body = new RigidBody(
            position,
            number(properties, prefix + "mass", 1),
            number(properties, prefix + "restitution", 0.35),
            number(properties, prefix + "friction", 0.55),
            number(properties, prefix + "linearDamping", 0.995),
            number(properties, prefix + "angularDamping", 0.88),
            type
        );
        body.setBoundingRadius(number(properties, prefix + "radius", 0.5));
        body.setVelocity(vec(properties.getProperty(prefix + "velocity", "0,0,0")));
        body.setRotation(vec(properties.getProperty(prefix + "rotation", "0,0,0")));
        body.setAngularVelocity(vec(properties.getProperty(prefix + "angularVelocity", "0,0,0")));

        double[] color = values(properties.getProperty(prefix + "color", "0.3,0.5,1,1"), 4);
        return new SceneObject(
            properties.getProperty(prefix + "name", "scenario_object"),
            mesh,
            body,
            new ColorRGBA(color[0], color[1], color[2], color[3]),
            properties.getProperty(prefix + "shape", "CUSTOM")
        );
    }

    private static String vector(Vec3 value) {
        return numbers(value.x, value.y, value.z);
    }

    private static Vec3 vec(String text) {
        double[] values = values(text, 3);
        return new Vec3(values[0], values[1], values[2]);
    }

    private static String numbers(double... values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i]);
        }
        return out.toString();
    }

    private static double[] values(String text, int count) {
        String[] split = text.split(",");
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = i < split.length ? parse(split[i], Double.NaN) : Double.NaN;
        }
        return values;
    }

    private static int integer(Properties properties, String key, int fallback) {
        return (int)parse(properties.getProperty(key), fallback);
    }

    private static double number(Properties properties, String key, double fallback) {
        return parse(properties.getProperty(key), fallback);
    }

    private static double parse(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
