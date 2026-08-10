package engine.physics;

import engine.math.ColorRGBA;

/**
 * Materiais reais usados pelas simulacoes, descritos por propriedades medidas.
 *
 * <h2>O que e dado e o que e derivado</h2>
 * Cada material declara apenas grandezas que existem em tabela de engenharia:
 * densidade, modulo de Young, coeficiente de Poisson, tensao de ruptura,
 * alongamento na ruptura, coeficiente de atrito e o coeficiente de restituicao
 * medido em ensaio de queda. Tudo o mais — dureza relativa, energia de fratura,
 * fragilidade, restituicao de um par de materiais — e <b>calculado</b> a partir
 * dessas propriedades, nao arbitrado.
 *
 * <h2>Formulas empregadas</h2>
 * <ul>
 *   <li><b>Modulo de contato efetivo</b> (Hertz):
 *       {@code 1/E* = (1-va^2)/Ea + (1-vb^2)/Eb}</li>
 *   <li><b>Pressao maxima de contato</b> no impacto de esfera (Johnson,
 *       <i>Contact Mechanics</i>, 1985): {@code p = 1,16 (rho v^2)^(1/5) E*^(4/5)}</li>
 *   <li><b>Modulo de resiliencia</b> — energia elastica armazenada ate a ruptura,
 *       por unidade de massa: {@code U = sigma^2 / (2 E rho)}</li>
 *   <li><b>Restituicao do par</b>: media geometrica dos valores de ensaio,
 *       corrigida pela velocidade com {@code e ~ v^(-1/4)} acima do limiar de
 *       escoamento (resultado classico de impacto elasto-plastico)</li>
 * </ul>
 *
 * <h2>Limite honesto do modelo</h2>
 * O motor trata os corpos como esferas rigidas e resolve o impacto por impulso.
 * Isso reproduz bem <i>quanto</i> o corpo perde de energia, como ele se compara a
 * outro material e se ele resiste ou rompe — mas nao substitui analise de
 * elementos finitos: nao ha deformacao real da malha, propagacao de trinca nem
 * dependencia de temperatura ou taxa de deformacao.
 *
 * <p>Fontes dos valores: Callister, <i>Ciencia e Engenharia de Materiais</i>;
 * Ashby, <i>Materials Selection in Mechanical Design</i>; Engineering ToolBox
 * (coeficientes de atrito e de restituicao de ensaio de queda).
 */
public enum MaterialState {

    //                        densidade  E (Pa)   Poisson  ruptura(Pa) along.  e_ensaio  atrito  cor
    GELATIN("Gelatina",          1050,   1.0e4,   0.50,     1.2e4,      0.60,   0.05,    0.60, "#f2408c"),
    RUBBER ("Borracha",          1100,   2.0e7,   0.49,     2.0e7,      5.00,   0.85,    0.80, "#1f2e33"),
    WOOD   ("Madeira",            550,   1.1e10,  0.35,     4.0e7,      0.010,  0.50,    0.40, "#9e612a"),
    STONE  ("Pedra",             2650,   5.0e10,  0.25,     1.5e7,      0.001,  0.55,    0.60, "#737b85"),
    STEEL  ("Aco",               7850,   2.0e11,  0.29,     4.0e8,      0.16,   0.60,    0.42, "#b8c7d6"),
    GLASS  ("Vidro",             2500,   7.0e10,  0.23,     5.0e7,      0.001,  0.65,    0.40, "#8cd9ff"),
    FOAM   ("Espuma",              30,   5.0e6,   0.10,     2.5e5,      0.05,   0.20,    0.50, "#ebebb8");

    private static final MaterialState[] VALUES = values();

    /** Constante do pico de pressao de contato no impacto de esfera (Johnson, 1985). */
    private static final double HERTZ_IMPACT_COEFFICIENT = 1.16;

    /** Alongamento na ruptura abaixo do qual o material rompe sem escoar. */
    private static final double BRITTLE_ELONGATION_LIMIT = 0.02;

    /**
     * Velocidade de impacto dos ensaios que originam os coeficientes tabelados
     * (queda de cerca de 1,3 m). Esse valor ja embute a perda plastica tipica —
     * por isso a correcao por velocidade so vale acima dele, senao a perda seria
     * contada duas vezes e o aco, que escoa desde ~0,1 m/s, ficaria sempre
     * abaixo do coeficiente que se mede na pratica.
     */
    private static final double REFERENCE_TEST_SPEED_MS = 5.0;

    /** Extremos de modulo de Young usados para normalizar a dureza relativa. */
    private static final double MIN_LOG_MODULUS = 4.0;    // 10 kPa (gel)
    private static final double MAX_LOG_MODULUS = 11.3;   // 200 GPa (aco)

    // ── Propriedades medidas ───────────────────────────────────────────
    public final String label;
    /** Massa especifica, kg/m3. */
    public final double densityKgM3;
    /** Modulo de Young, Pa. */
    public final double youngModulusPa;
    /** Coeficiente de Poisson, adimensional. */
    public final double poissonRatio;
    /** Tensao de ruptura (tracao ou flexao, o que limita), Pa. */
    public final double failureStrengthPa;
    /** Alongamento na ruptura (0,001 = 0,1%). Separa fragil de ductil. */
    public final double elongationAtBreak;
    /** Coeficiente de restituicao de ensaio de queda sobre superficie rigida. */
    public final double restitution;
    /** Coeficiente de atrito cinetico. */
    public final double friction;
    public final ColorRGBA color;

    // ── Propriedades derivadas (calculadas no construtor) ──────────────
    /** Dureza relativa 0..1, do modulo de Young em escala logaritmica. */
    public final double hardness;
    /** Energia elastica ate a ruptura por unidade de massa: sigma^2/(2 E rho). */
    public final double fractureJPerKg;
    /** Amortecimento linear do motor, correlacionado a densidade do material. */
    public final double linearDamping;
    /** Amortecimento angular do motor, correlacionado ao atrito superficial. */
    public final double angularDamping;

    MaterialState(String label, double densityKgM3, double youngModulusPa, double poissonRatio,
                  double failureStrengthPa, double elongationAtBreak, double restitution,
                  double friction, String colorHex) {
        this.label = label;
        this.densityKgM3 = densityKgM3;
        this.youngModulusPa = youngModulusPa;
        this.poissonRatio = poissonRatio;
        this.failureStrengthPa = failureStrengthPa;
        this.elongationAtBreak = elongationAtBreak;
        this.restitution = restitution;
        this.friction = friction;
        this.color = ColorRGBA.fromHex(colorHex);

        // Dureza: o modulo de Young varia sete ordens de grandeza entre gel e
        // aco, entao a normalizacao e feita em escala logaritmica.
        double logModulus = Math.log10(youngModulusPa);
        this.hardness = clamp01((logModulus - MIN_LOG_MODULUS) / (MAX_LOG_MODULUS - MIN_LOG_MODULUS));

        // Modulo de resiliencia por massa: quanto de energia o material guarda
        // elasticamente antes de romper. E o que explica a borracha quicar e a
        // pedra estilhacar.
        this.fractureJPerKg = failureStrengthPa * failureStrengthPa
            / (2.0 * youngModulusPa * densityKgM3);

        // Amortecimentos sao parametros do integrador, nao propriedades do
        // material: corpos leves perdem velocidade mais rapido no ar, e o atrito
        // superficial freia o giro. Derivados para acompanhar o material.
        this.linearDamping = clamp(0.978 + 0.020 * Math.log10(densityKgM3) / 4.0, 0.90, 0.999);
        this.angularDamping = clamp(0.98 - 0.24 * friction, 0.70, 0.99);
    }

    // ── Massa e energia ────────────────────────────────────────────────

    public double massForSphere(double radiusMeters) {
        double volume = 4.0 / 3.0 * Math.PI * radiusMeters * radiusMeters * radiusMeters;
        return Math.max(0.05, densityKgM3 * volume);
    }

    /** Fracao da energia de impacto que o material dissipa em vez de devolver. */
    public double impactAbsorption() {
        return 1.0 - hardness;
    }

    public double fractureEnergyForSphere(double radiusMeters) {
        return massForSphere(radiusMeters) * fractureJPerKg;
    }

    // ── Contato entre dois materiais ───────────────────────────────────

    /**
     * Modulo de contato efetivo do par, pela teoria de Hertz:
     * {@code 1/E* = (1-va^2)/Ea + (1-vb^2)/Eb}.
     *
     * <p>E o que faz o par importar: aco contra espuma e governado pela espuma,
     * porque o termo mais flexivel domina a soma.
     */
    public double effectiveModulusWith(MaterialState other) {
        double compliance = (1.0 - poissonRatio * poissonRatio) / youngModulusPa
            + (1.0 - other.poissonRatio * other.poissonRatio) / other.youngModulusPa;
        return 1.0 / Math.max(1e-30, compliance);
    }

    /**
     * Pico de pressao no contato durante o impacto, em Pa.
     * {@code p = 1,16 (rho v^2)^(1/5) E*^(4/5)} (Johnson, 1985).
     *
     * <p>Comparar esta pressao com {@link #failureStrengthPa} diz se o material
     * rompe — criterio de tensao, nao de energia total, que e o que corresponde
     * ao comportamento real: uma pedra nao estilhaca pela energia do conjunto,
     * mas pela tensao concentrada na regiao de contato.
     */
    public double contactPressureWith(MaterialState other, double impactSpeedMs) {
        if (impactSpeedMs <= 0) return 0;
        double effectiveDensity = 0.5 * (densityKgM3 + other.densityKgM3);
        return HERTZ_IMPACT_COEFFICIENT
            * Math.pow(effectiveDensity * impactSpeedMs * impactSpeedMs, 0.2)
            * Math.pow(effectiveModulusWith(other), 0.8);
    }

    /**
     * Coeficiente de restituicao do par, dependente da velocidade.
     *
     * <p>Duas correcoes sobre o valor de ensaio:
     * <ol>
     *   <li><b>Par</b> — media geometrica dos dois materiais. A restituicao nao e
     *       propriedade de um corpo isolado: depende de quem bate em quem.</li>
     *   <li><b>Velocidade</b> — acima do limiar de escoamento a deformacao deixa
     *       de ser elastica e parte da energia vira dano permanente. O expoente
     *       -1/4 e o resultado classico do impacto elasto-plastico.</li>
     * </ol>
     */
    public double restitutionWith(MaterialState other, double impactSpeedMs) {
        double pair = pairRestitution(other);
        if (impactSpeedMs <= REFERENCE_TEST_SPEED_MS) return clamp01(pair);

        // Acima da velocidade de ensaio, so ha perda adicional se o contato
        // realmente escoar — criterio de tensao, nao uma velocidade tabelada.
        // A diferenca aparece na bola de borracha contra placa de aco: quem cede
        // e a borracha, e o modulo baixo dela limita a pressao, entao o impacto
        // segue elastico e a bola volta alto.
        double pressure = contactPressureWith(other, impactSpeedMs);
        double yieldStrength = Math.min(failureStrengthPa, other.failureStrengthPa);
        if (pressure <= yieldStrength) return clamp01(pair);

        return clamp01(pair * Math.pow(REFERENCE_TEST_SPEED_MS / impactSpeedMs, 0.25));
    }

    /**
     * Restituicao do par, ponderada pela complacencia de cada material.
     *
     * <p>Quem deforma dita a perda: numa esfera de gelatina contra placa de aco
     * praticamente toda a deformacao acontece na gelatina, entao o resultado
     * deve ficar junto do valor dela — nao no meio do caminho. A media
     * geometrica simples erra exatamente nesses pares muito diferentes, puxando
     * a gelatina para cima por causa do aco.
     *
     * <p>O peso de cada material e sua fracao da complacencia total do contato,
     * {@code (1-v^2)/E}, a mesma grandeza que aparece no modulo de Hertz.
     */
    private double pairRestitution(MaterialState other) {
        double complianceA = (1.0 - poissonRatio * poissonRatio) / youngModulusPa;
        double complianceB = (1.0 - other.poissonRatio * other.poissonRatio) / other.youngModulusPa;
        double total = complianceA + complianceB;
        if (total <= 0) return restitution;
        double weightA = complianceA / total;
        return Math.pow(restitution, weightA) * Math.pow(other.restitution, 1.0 - weightA);
    }

    // ── Modo de falha ──────────────────────────────────────────────────

    /** True para materiais que rompem sem escoar antes (vidro, pedra, madeira). */
    public boolean isBrittle() {
        return elongationAtBreak < BRITTLE_ELONGATION_LIMIT;
    }

    /**
     * Dano acumulado ate a ruptura, 0..1. Materiais frageis rompem assim que a
     * tensao passa do limite; ducteis absorvem deformando antes de falhar.
     */
    public double breakThreshold() {
        return isBrittle()
            ? 0.35 + elongationAtBreak * 10.0        // fragil: rompe cedo
            : Math.min(0.98, 0.55 + elongationAtBreak * 0.9);
    }

    /** Descricao do modo de falha, para a telemetria. */
    public String failureMode() {
        if (isBrittle()) return "fragil: rompe sem deformacao permanente";
        if (elongationAtBreak > 1.0) return "elastomerico: recupera a forma";
        return "ductil: deforma antes de falhar";
    }

    /** Resumo das propriedades medidas, para tooltip e telemetria. */
    public String describeProperties() {
        return String.format(
            "densidade %.0f kg/m3 | E %s | ruptura %s | alongamento %.1f%% | atrito %.2f",
            densityKgM3, formatPressure(youngModulusPa), formatPressure(failureStrengthPa),
            elongationAtBreak * 100.0, friction);
    }

    private static String formatPressure(double pascals) {
        if (pascals >= 1e9) return String.format("%.0f GPa", pascals / 1e9);
        if (pascals >= 1e6) return String.format("%.0f MPa", pascals / 1e6);
        if (pascals >= 1e3) return String.format("%.0f kPa", pascals / 1e3);
        return String.format("%.0f Pa", pascals);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static MaterialState byIndex(double index) {
        int i = Math.max(0, Math.min(VALUES.length - 1, (int)Math.round(index)));
        return VALUES[i];
    }

    public static String[] labels() {
        String[] labels = new String[VALUES.length];
        for (int i = 0; i < VALUES.length; i++) labels[i] = VALUES[i].label;
        return labels;
    }
}
