package engine.simulation.parameters;

import java.util.*;
import java.util.function.Consumer;

/**
 * Parâmetro numérico ajustável de um módulo de simulação.
 *
 * <h2>Dois intervalos, com papéis diferentes</h2>
 * <ul>
 *   <li><b>Faixa recomendada</b> ({@link Builder#range}) — o que o slider
 *       percorre. São os valores típicos do experimento, escolhidos para caber
 *       na cena e dar resultado interessante.</li>
 *   <li><b>Limites físicos</b> ({@link Builder#limits}) — a fronteira do que faz
 *       sentido simular. Massa não pode ser negativa, ângulo de rampa não passa
 *       de 90 graus, velocidade não ultrapassa a da luz.</li>
 * </ul>
 *
 * <p>Digitando no campo de texto o usuário pode sair da faixa recomendada à
 * vontade: o valor é aceito e {@link #validate(double)} devolve um aviso
 * explicando o que muda, com referência real de comparação. Só os limites
 * físicos são intransponíveis — e ao serem atingidos, a mensagem diz por quê.
 *
 * <p>Sem {@code limits()} declarado o parâmetro aceita qualquer número finito.
 * Parâmetros de lista ({@link Builder#options}) ficam presos aos índices válidos.
 *
 * <p>Exemplo de declaração num módulo:
 * <pre>
 *   parameters.add(Parameter.of("gravity", "Gravidade", 9.81)
 *       .range(0, 30).limits(0, 1e6).unit("m/s2").step(0.1)
 *       .reference("Lua 1,62 | Terra 9,81 | Jupiter 24,79 | Sol 274 m/s2"));
 * </pre>
 */
public final class Parameter {

    /** Resultado da checagem de um valor digitado pelo usuário. */
    public enum Severity {
        /** Dentro da faixa recomendada. */
        OK,
        /** Fisicamente válido, mas fora do usual — a simulação avisa o efeito. */
        WARNING,
        /** Fora dos limites físicos: o valor foi ajustado para a fronteira. */
        REJECTED
    }

    /**
     * Veredito sobre um valor digitado.
     *
     * @param value    valor efetivamente aplicado (já dentro dos limites)
     * @param severity gravidade do desvio
     * @param message  explicação para o usuário; vazio quando tudo certo
     */
    public record Feedback(double value, Severity severity, String message) {
        public boolean isOk() { return severity == Severity.OK; }
    }

    // ── Metadados ──────────────────────────────────
    private final String name;
    private final String label;
    private final String unit;
    private final String tooltip;
    private final String reference;
    private final List<String> options;

    // ── Valores ────────────────────────────────────
    private final double defaultValue;
    private final double minValue;
    private final double maxValue;
    private final double hardMin;
    private final double hardMax;
    private final String outOfRangeNote;
    private final double step;
    private       double currentValue;

    // ── Callback ───────────────────────────────────
    private Consumer<Double> onChange;

    // ── Construtor privado (usar builder estático) ─
    private Parameter(Builder b) {
        this.name           = b.name;
        this.label          = b.label;
        this.unit           = b.unit;
        this.tooltip        = b.tooltip;
        this.reference      = b.reference;
        this.options        = List.copyOf(b.options);
        this.defaultValue   = b.defaultValue;
        this.minValue       = b.minValue;
        this.maxValue       = b.maxValue;
        this.hardMin        = b.hardMin;
        this.hardMax        = b.hardMax;
        this.outOfRangeNote = b.outOfRangeNote;
        this.step           = b.step;
        this.currentValue   = clampToLimits(b.defaultValue);
    }

    // ── Builder fluente ────────────────────────────
    public static Builder of(String name, String label, double defaultValue) {
        return new Builder(name, label, defaultValue);
    }

    public static final class Builder {
        final String name, label;
        final double defaultValue;
        double minValue = 0, maxValue = 100, step = 0.5;
        double hardMin = Double.NEGATIVE_INFINITY, hardMax = Double.POSITIVE_INFINITY;
        String unit = "", tooltip = "", reference = "", outOfRangeNote = "";
        List<String> options = List.of();

        Builder(String name, String label, double defaultValue) {
            this.name = name; this.label = label; this.defaultValue = defaultValue;
        }

        /** Faixa percorrida pelo slider: os valores típicos do experimento. */
        public Builder range(double min, double max) { this.minValue = min; this.maxValue = max; return this; }

        /** Fronteira do que faz sentido simular. Fora dela o valor é recusado. */
        public Builder limits(double min, double max) { this.hardMin = min; this.hardMax = max; return this; }

        /** Valores reais de comparação, exibidos ao sair da faixa recomendada. */
        public Builder reference(String text) { this.reference = text; return this; }

        /** Efeito de usar valores fora do usual (ex.: "o objeto sai do enquadramento"). */
        public Builder outOfRangeNote(String note) { this.outOfRangeNote = note; return this; }

        public Builder unit(String unit)             { this.unit = unit;    return this; }
        public Builder step(double step)             { this.step = step;    return this; }
        public Builder tooltip(String tip)           { this.tooltip = tip;  return this; }

        public Builder options(String... labels) {
            this.options = List.of(labels);
            this.minValue = 0;
            this.maxValue = Math.max(0, labels.length - 1);
            // Lista fechada: os limites físicos são os próprios índices válidos.
            this.hardMin = this.minValue;
            this.hardMax = this.maxValue;
            this.step = 1;
            return this;
        }

        public Parameter build()                     { return new Parameter(this); }
    }

    // ── API pública ────────────────────────────────

    /**
     * Aplica um valor, respeitando apenas os <b>limites físicos</b>.
     *
     * <p>A faixa recomendada não trava nada: o usuário pode digitar fora dela.
     * Use {@link #validate(double)} antes se quiser a explicação do desvio.
     */
    public void setValue(double value) {
        double clamped = clampToLimits(value);
        if (Math.abs(clamped - currentValue) < 1e-10) return;
        this.currentValue = clamped;
        if (onChange != null) onChange.accept(clamped);
    }

    /**
     * Verifica um valor digitado sem aplicá-lo.
     *
     * @return o valor que seria aplicado, a gravidade do desvio e a explicação
     */
    public Feedback validate(double requested) {
        if (Double.isNaN(requested) || Double.isInfinite(requested)) {
            return new Feedback(currentValue, Severity.REJECTED,
                "Valor nao numerico. Mantido " + format(currentValue) + describeUnit() + ".");
        }

        if (requested < hardMin) {
            return new Feedback(hardMin, Severity.REJECTED, String.format(
                "%s nao pode ser menor que %s%s. %sAjustado para o limite.",
                label, format(hardMin), describeUnit(), referenceSuffix()));
        }
        if (requested > hardMax) {
            return new Feedback(hardMax, Severity.REJECTED, String.format(
                "%s nao pode passar de %s%s. %sAjustado para o limite.",
                label, format(hardMax), describeUnit(), referenceSuffix()));
        }

        if (requested < minValue || requested > maxValue) {
            StringBuilder message = new StringBuilder(String.format(
                "Fora da faixa usual (%s a %s%s), mas fisicamente valido.",
                format(minValue), format(maxValue), describeUnit()));
            if (!outOfRangeNote.isBlank()) message.append(' ').append(outOfRangeNote);
            if (!reference.isBlank()) message.append(" Referencia: ").append(reference).append('.');
            return new Feedback(requested, Severity.WARNING, message.toString());
        }

        return new Feedback(requested, Severity.OK, "");
    }

    /** True se o valor atual está dentro da faixa recomendada. */
    public boolean isWithinRecommended() {
        return currentValue >= minValue && currentValue <= maxValue;
    }

    private double clampToLimits(double value) {
        if (Double.isNaN(value)) return hardMin > Double.NEGATIVE_INFINITY ? hardMin : 0;
        return Math.max(hardMin, Math.min(hardMax, value));
    }

    private String describeUnit() {
        return unit.isBlank() ? "" : " " + unit;
    }

    private String referenceSuffix() {
        return reference.isBlank() ? "" : "Referencia: " + reference + ". ";
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) return value > 0 ? "infinito" : "-infinito";
        double magnitude = Math.abs(value);
        if (magnitude != 0 && (magnitude >= 1e6 || magnitude < 1e-3)) {
            return String.format(java.util.Locale.US, "%.3g", value);
        }
        return magnitude >= 100 ? String.format("%.0f", value) : String.format("%.2f", value);
    }

    public void resetToDefault() { setValue(defaultValue); }

    public void setOnChange(Consumer<Double> cb) { this.onChange = cb; }

    // ── Getters ────────────────────────────────────
    public String getName()         { return name; }
    public String getLabel()        { return label; }
    public String getUnit()         { return unit; }
    public String getTooltip()      { return tooltip; }
    public double getValue()        { return currentValue; }
    public double getDefaultValue() { return defaultValue; }
    public double getMinValue()     { return minValue; }
    public double getMaxValue()     { return maxValue; }
    public double getHardMin()      { return hardMin; }
    public double getHardMax()      { return hardMax; }
    public String getReference()    { return reference; }
    public double getStep()         { return step; }
    public List<String> getOptions(){ return options; }
    public boolean hasOptions()     { return !options.isEmpty(); }
    public int getOptionIndex()     { return (int)Math.round(currentValue); }
    public String getSelectedOption() {
        if (options.isEmpty()) return "";
        int idx = Math.max(0, Math.min(options.size() - 1, getOptionIndex()));
        return options.get(idx);
    }

    /** Retorna o valor normalizado [0..1] dentro do range. */
    public double getNormalized() {
        double range = maxValue - minValue;
        return range < 1e-10 ? 0 : (currentValue - minValue) / range;
    }

    @Override
    public String toString() {
        return String.format("Param[%s=%.2f %s (%.1f..%.1f)]",
            name, currentValue, unit, minValue, maxValue);
    }
}
