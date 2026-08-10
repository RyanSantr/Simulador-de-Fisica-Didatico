package engine.math;

/**
 * Matriz 4x4 para transformações 3D (coluna-maior, compatível com OpenGL).
 * Usada para model, view e projection matrices.
 */
public final class Mat4 {

    // Armazenamento coluna-maior: m[coluna][linha]
    private final double[][] m;

    private Mat4(double[][] m) { this.m = m; }

    /** Cria matriz identidade. */
    public static Mat4 identity() {
        double[][] d = new double[4][4];
        d[0][0] = d[1][1] = d[2][2] = d[3][3] = 1.0;
        return new Mat4(d);
    }

    /** Cria matriz de translação. */
    public static Mat4 translation(Vec3 t) {
        Mat4 r = identity();
        r.m[3][0] = t.x;
        r.m[3][1] = t.y;
        r.m[3][2] = t.z;
        return r;
    }

    /** Cria matriz de escala. */
    public static Mat4 scale(Vec3 s) {
        Mat4 r = identity();
        r.m[0][0] = s.x;
        r.m[1][1] = s.y;
        r.m[2][2] = s.z;
        return r;
    }

    /** Cria matriz de rotação em torno do eixo X (radianos). */
    public static Mat4 rotationX(double angle) {
        Mat4 r = identity();
        double c = Math.cos(angle), s = Math.sin(angle);
        r.m[1][1] =  c; r.m[2][1] = -s;
        r.m[1][2] =  s; r.m[2][2] =  c;
        return r;
    }

    /** Cria matriz de rotação em torno do eixo Y (radianos). */
    public static Mat4 rotationY(double angle) {
        Mat4 r = identity();
        double c = Math.cos(angle), s = Math.sin(angle);
        r.m[0][0] =  c; r.m[2][0] =  s;
        r.m[0][2] = -s; r.m[2][2] =  c;
        return r;
    }

    /** Cria matriz de rotação em torno do eixo Z (radianos). */
    public static Mat4 rotationZ(double angle) {
        Mat4 r = identity();
        double c = Math.cos(angle), s = Math.sin(angle);
        r.m[0][0] =  c; r.m[1][0] = -s;
        r.m[0][1] =  s; r.m[1][1] =  c;
        return r;
    }

    /** Matriz perspectiva (fov em radianos, aspect = w/h). */
    public static Mat4 perspective(double fovY, double aspect, double near, double far) {
        double f = 1.0 / Math.tan(fovY / 2.0);
        double nf = 1.0 / (near - far);
        double[][] d = new double[4][4];
        d[0][0] = f / aspect;
        d[1][1] = f;
        d[2][2] = (far + near) * nf;
        d[2][3] = -1.0;
        d[3][2] = 2.0 * far * near * nf;
        return new Mat4(d);
    }

    /** Matriz lookAt (câmera). */
    public static Mat4 lookAt(Vec3 eye, Vec3 center, Vec3 up) {
        Vec3 f = center.sub(eye).normalize();
        Vec3 s = f.cross(up).normalize();
        Vec3 u = s.cross(f);
        double[][] d = new double[4][4];
        d[0][0] =  s.x; d[1][0] =  s.y; d[2][0] =  s.z;
        d[0][1] =  u.x; d[1][1] =  u.y; d[2][1] =  u.z;
        d[0][2] = -f.x; d[1][2] = -f.y; d[2][2] = -f.z;
        d[3][0] = -s.dot(eye);
        d[3][1] = -u.dot(eye);
        d[3][2] =  f.dot(eye);
        d[3][3] =  1.0;
        return new Mat4(d);
    }

    /** Multiplicação de matrizes. */
    public Mat4 mul(Mat4 o) {
        double[][] r = new double[4][4];
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++)
                for (int k = 0; k < 4; k++)
                    r[col][row] += m[k][row] * o.m[col][k];
        return new Mat4(r);
    }

    /** Transforma um Vec3 como ponto (w=1). */
    public Vec3 transformPoint(Vec3 v) {
        double x = m[0][0]*v.x + m[1][0]*v.y + m[2][0]*v.z + m[3][0];
        double y = m[0][1]*v.x + m[1][1]*v.y + m[2][1]*v.z + m[3][1];
        double z = m[0][2]*v.x + m[1][2]*v.y + m[2][2]*v.z + m[3][2];
        double w = m[0][3]*v.x + m[1][3]*v.y + m[2][3]*v.z + m[3][3];
        if (Math.abs(w) < 1e-10) return new Vec3(x, y, z);
        return new Vec3(x/w, y/w, z/w);
    }

    /** Transforma um Vec3 como direção (w=0). */
    public Vec3 transformDir(Vec3 v) {
        return new Vec3(
            m[0][0]*v.x + m[1][0]*v.y + m[2][0]*v.z,
            m[0][1]*v.x + m[1][1]*v.y + m[2][1]*v.z,
            m[0][2]*v.x + m[1][2]*v.y + m[2][2]*v.z
        );
    }

    public double get(int col, int row) { return m[col][row]; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Mat4[\n");
        for (int r = 0; r < 4; r++) {
            sb.append("  ");
            for (int c = 0; c < 4; c++)
                sb.append(String.format("%8.3f ", m[c][r]));
            sb.append('\n');
        }
        return sb.append(']').toString();
    }
}
