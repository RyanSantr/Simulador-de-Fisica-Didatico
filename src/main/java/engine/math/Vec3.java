package engine.math;

/**
 * Vetor 3D imutavel usado por toda a engine (fisica, renderer e camera).
 *
 * <p>Todas as operacoes retornam novas instancias — nenhum metodo altera o
 * proprio vetor. Isso evita bugs de aliasing (dois objetos compartilhando o
 * mesmo vetor de posicao) ao custo de uma alocacao por operacao.
 *
 * <p><b>Desempenho:</b> os metodos chamados dentro dos loops quentes
 * ({@link #lengthSq()}, {@link #distanceTo(Vec3)}, {@link #distanceSqTo(Vec3)})
 * calculam diretamente sobre os componentes, sem criar vetores temporarios.
 */
public final class Vec3 {

    public static final Vec3 ZERO   = new Vec3(0, 0, 0);
    public static final Vec3 ONE    = new Vec3(1, 1, 1);
    public static final Vec3 UP     = new Vec3(0, 1, 0);
    public static final Vec3 RIGHT  = new Vec3(1, 0, 0);
    public static final Vec3 FORWARD = new Vec3(0, 0, -1);

    public final double x, y, z;

    public Vec3(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    // ── Operações básicas ──────────────────────────
    public Vec3 add(Vec3 o)              { return new Vec3(x+o.x, y+o.y, z+o.z); }
    public Vec3 add(double dx, double dy, double dz) { return new Vec3(x+dx, y+dy, z+dz); }
    public Vec3 sub(Vec3 o)              { return new Vec3(x-o.x, y-o.y, z-o.z); }
    public Vec3 mul(double s)            { return new Vec3(x*s, y*s, z*s); }
    public Vec3 div(double s)            { return new Vec3(x/s, y/s, z/s); }
    public Vec3 negate()                 { return new Vec3(-x, -y, -z); }

    // ── Produto escalar e vetorial ─────────────────
    public double dot(Vec3 o)            { return x*o.x + y*o.y + z*o.z; }
    public Vec3 cross(Vec3 o) {
        return new Vec3(
            y*o.z - z*o.y,
            z*o.x - x*o.z,
            x*o.y - y*o.x
        );
    }

    // ── Magnitude ─────────────────────────────────
    public double lengthSq()             { return x*x + y*y + z*z; }
    public double length()               { return Math.sqrt(lengthSq()); }

    public Vec3 normalize() {
        double len = length();
        return len < 1e-10 ? ZERO : div(len);
    }

    // ── Distancias ────────────────────────────────
    // Calculadas componente a componente (sem `sub()`) porque sao chamadas por
    // objeto e por par de corpos a cada frame: evitar o Vec3 temporario aqui
    // remove milhares de alocacoes por segundo no renderer e na fisica.

    public double distanceSqTo(Vec3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceTo(Vec3 o) { return Math.sqrt(distanceSqTo(o)); }

    public Vec3 lerp(Vec3 to, double t) {
        return new Vec3(
            x + (to.x - x) * t,
            y + (to.y - y) * t,
            z + (to.z - z) * t
        );
    }

    public Vec3 reflect(Vec3 normal) {
        return sub(normal.mul(2.0 * dot(normal)));
    }

    public Vec3 clamp(double minLen, double maxLen) {
        double len = length();
        if (len < 1e-10) return ZERO;
        if (len < minLen) return normalize().mul(minLen);
        if (len > maxLen) return normalize().mul(maxLen);
        return this;
    }

    public static Vec3 random() {
        return new Vec3(
            Math.random() * 2 - 1,
            Math.random() * 2 - 1,
            Math.random() * 2 - 1
        ).normalize();
    }

    @Override
    public String toString() {
        return String.format("Vec3(%.3f, %.3f, %.3f)", x, y, z);
    }
}
