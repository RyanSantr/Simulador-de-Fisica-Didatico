package engine.physics;

/**
 * Meio onde o corpo se move, descrito por propriedades medidas a 20 graus C.
 *
 * <h2>O que e dado e o que e derivado</h2>
 * O meio declara apenas massa especifica, viscosidade dinamica e velocidade do
 * som — grandezas de tabela. O arrasto e o empuxo sao <b>calculados</b>:
 *
 * <ul>
 *   <li><b>Empuxo (Arquimedes)</b>: {@code g_efetivo = g (1 - rho_meio/rho_corpo)}.
 *       Depende da densidade <i>do corpo</i>, nao do meio sozinho — e por isso
 *       que madeira boia na agua e aco afunda. Um fator fixo por meio, como
 *       havia antes, fazia os dois descerem igual.</li>
 *   <li><b>Coeficiente de arrasto</b>: funcao do numero de Reynolds
 *       {@code Re = rho v D / mu}, cobrindo desde o regime de Stokes ate a
 *       crise do arrasto.</li>
 *   <li><b>Divergencia transonica</b>: perto da velocidade do som o arrasto
 *       dispara — o mesmo efeito que exige potencia extra para romper a
 *       barreira do som.</li>
 * </ul>
 *
 * <p>Fontes: Schiller e Naumann (1935) para a correlacao de arrasto da esfera;
 * White, <i>Fluid Mechanics</i>, para os regimes e a crise do arrasto;
 * Engineering ToolBox para viscosidades e densidades.
 */
public enum MediumState {

    //                    densidade  viscosidade  som (m/s)
    VACUUM("Vacuo",           0.0,     0.0,          0.0),
    AIR   ("Ar",              1.225,   1.81e-5,    343.0),
    WATER ("Agua",          998.0,     1.002e-3,  1481.0),
    OIL   ("Oleo",          870.0,     0.170,     1450.0),
    GEL   ("Gel",          1040.0,     5.000,     1500.0);

    private static final MediumState[] VALUES = values();

    /** Coeficiente de arrasto da esfera no regime de Newton (10^3 < Re < 2x10^5). */
    private static final double NEWTON_REGIME_CD = 0.44;
    /** Coeficiente apos a crise do arrasto, quando a camada limite fica turbulenta. */
    private static final double SUPERCRITICAL_CD = 0.18;
    private static final double CRISIS_START_RE = 2.0e5;
    private static final double CRISIS_END_RE = 4.0e5;

    public final String label;
    /** Massa especifica do meio, kg/m3. */
    public final double densityKgM3;
    /** Viscosidade dinamica, Pa.s. */
    public final double dynamicViscosityPaS;
    /** Velocidade do som no meio, m/s. Zero no vacuo. */
    public final double speedOfSoundMs;

    MediumState(String label, double densityKgM3, double dynamicViscosityPaS, double speedOfSoundMs) {
        this.label = label;
        this.densityKgM3 = densityKgM3;
        this.dynamicViscosityPaS = dynamicViscosityPaS;
        this.speedOfSoundMs = speedOfSoundMs;
    }

    // ── Empuxo ─────────────────────────────────────────────────────────

    /**
     * Fator que multiplica a gravidade por efeito do empuxo, pelo principio de
     * Arquimedes: {@code 1 - rho_meio/rho_corpo}.
     *
     * @return 1 no vacuo; menor que 1 para corpos que afundam devagar;
     *         <b>negativo</b> para corpos menos densos que o meio, que sobem
     */
    public double buoyancyFactorFor(double bodyDensityKgM3) {
        if (densityKgM3 <= 0) return 1.0;
        return 1.0 - densityKgM3 / Math.max(1e-6, bodyDensityKgM3);
    }

    /** True se um corpo com esta densidade flutua neste meio. */
    public boolean floats(double bodyDensityKgM3) {
        return densityKgM3 > 0 && bodyDensityKgM3 < densityKgM3;
    }

    // ── Arrasto ────────────────────────────────────────────────────────

    /**
     * Numero de Reynolds do escoamento ao redor da esfera:
     * {@code Re = rho v D / mu}. E ele que decide o regime do arrasto.
     */
    public double reynolds(double speedMs, double diameterM) {
        if (dynamicViscosityPaS <= 0) return 0;
        return densityKgM3 * Math.abs(speedMs) * diameterM / dynamicViscosityPaS;
    }

    /**
     * Coeficiente de arrasto de uma esfera para o Reynolds informado.
     *
     * <table border="1">
     *   <caption>Regimes</caption>
     *   <tr><th>Faixa</th><th>Comportamento</th></tr>
     *   <tr><td>Re &lt; 1</td><td>Stokes: {@code Cd = 24/Re}, arrasto viscoso puro</td></tr>
     *   <tr><td>1 a 1000</td><td>Schiller-Naumann, transicao</td></tr>
     *   <tr><td>10^3 a 2x10^5</td><td>Newton: Cd praticamente constante em 0,44</td></tr>
     *   <tr><td>2x10^5 a 4x10^5</td><td>crise do arrasto: a esteira estreita e o Cd despenca</td></tr>
     *   <tr><td>acima</td><td>supercritico, Cd por volta de 0,18</td></tr>
     * </table>
     */
    public double dragCoefficient(double reynolds) {
        if (reynolds <= 1e-9) return 0;
        if (reynolds < 1000.0) {
            // Schiller-Naumann; em Re baixo o termo 24/Re domina e recai em Stokes.
            return (24.0 / reynolds) * (1.0 + 0.15 * Math.pow(reynolds, 0.687));
        }
        if (reynolds < CRISIS_START_RE) return NEWTON_REGIME_CD;
        if (reynolds < CRISIS_END_RE) {
            double t = (reynolds - CRISIS_START_RE) / (CRISIS_END_RE - CRISIS_START_RE);
            return NEWTON_REGIME_CD + (SUPERCRITICAL_CD - NEWTON_REGIME_CD) * t;
        }
        return SUPERCRITICAL_CD;
    }

    /**
     * Multiplicador de arrasto perto e acima da velocidade do som.
     *
     * <p>Entre Mach 0,8 e 1,2 o arrasto chega a dobrar — a divergencia
     * transonica, que e o que torna cara a passagem da barreira do som. Acima
     * disso ele cai devagar, sem voltar ao valor subsonico.
     */
    public double compressibilityFactor(double speedMs) {
        if (speedOfSoundMs <= 0) return 1.0;
        double mach = Math.abs(speedMs) / speedOfSoundMs;
        if (mach < 0.8) return 1.0;
        if (mach < 1.2) return 1.0 + 2.5 * (mach - 0.8);
        return Math.max(1.4, 2.0 - 0.15 * (mach - 1.2));
    }

    /**
     * Forca de arrasto sobre uma esfera, em N:
     * {@code F = 1/2 Cd rho A v^2}, com Cd vindo do Reynolds e corrigido para
     * efeitos de compressibilidade.
     */
    public double dragForce(double speedMs, double radiusM) {
        if (densityKgM3 <= 0 || speedMs == 0) return 0;
        double area = Math.PI * radiusM * radiusM;
        double cd = dragCoefficient(reynolds(speedMs, radiusM * 2.0)) * compressibilityFactor(speedMs);
        return 0.5 * cd * densityKgM3 * area * speedMs * speedMs;
    }

    /** Resumo das propriedades, para telemetria. */
    public String describeProperties() {
        if (densityKgM3 <= 0) return "sem materia: nao ha arrasto nem empuxo";
        return String.format("densidade %.1f kg/m3 | viscosidade %s | som %.0f m/s",
            densityKgM3, formatViscosity(dynamicViscosityPaS), speedOfSoundMs);
    }

    private static String formatViscosity(double pascalSeconds) {
        if (pascalSeconds >= 1) return String.format("%.1f Pa.s", pascalSeconds);
        if (pascalSeconds >= 1e-3) return String.format("%.2f mPa.s", pascalSeconds * 1e3);
        return String.format("%.1f uPa.s", pascalSeconds * 1e6);
    }

    public static MediumState byIndex(double index) {
        int i = Math.max(0, Math.min(VALUES.length - 1, (int)Math.round(index)));
        return VALUES[i];
    }

    public static String[] labels() {
        String[] labels = new String[VALUES.length];
        for (int i = 0; i < VALUES.length; i++) labels[i] = VALUES[i].label;
        return labels;
    }
}
