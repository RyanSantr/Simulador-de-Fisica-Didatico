package engine.simulation.parameters;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Coleção ordenada de parâmetros de um módulo.
 * Provê acesso por nome e iteração em ordem de inserção.
 */
public final class ParameterSet implements Iterable<Parameter> {

    private final Map<String, Parameter> params = new LinkedHashMap<>();

    /** Adiciona um parâmetro ao conjunto. */
    public ParameterSet add(Parameter.Builder builder) {
        Parameter p = builder.build();
        params.put(p.getName(), p);
        return this;
    }

    /** Adiciona um parâmetro já construído. */
    public ParameterSet add(Parameter p) {
        params.put(p.getName(), p);
        return this;
    }

    /** Obtém parâmetro por nome. Retorna null se não encontrado. */
    public Parameter get(String name) { return params.get(name); }

    /** Obtém o valor double de um parâmetro. Retorna defaultIfAbsent se não encontrado. */
    public double getValue(String name, double defaultIfAbsent) {
        Parameter p = params.get(name);
        return p != null ? p.getValue() : defaultIfAbsent;
    }

    /** Altera o valor de um parâmetro pelo nome. */
    public boolean setValue(String name, double value) {
        Parameter p = params.get(name);
        if (p == null) return false;
        p.setValue(value);
        return true;
    }

    /** Reseta todos os parâmetros para seus valores default. */
    public void resetAll() { params.values().forEach(Parameter::resetToDefault); }

    /** Itera por (nome, parâmetro). */
    public void forEach(BiConsumer<String, Parameter> action) {
        params.forEach(action);
    }

    /** Todos os parâmetros em ordem de inserção. */
    public Collection<Parameter> all() {
        return Collections.unmodifiableCollection(params.values());
    }

    public int size()             { return params.size(); }
    public boolean has(String n)  { return params.containsKey(n); }

    @Override
    public Iterator<Parameter> iterator() { return params.values().iterator(); }
}
