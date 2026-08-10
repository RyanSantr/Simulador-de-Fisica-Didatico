package engine.ui.simulation;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Painel de resultados: transforma o texto de telemetria de um modulo em
 * linhas legiveis, em vez de um unico bloco de texto corrido.
 *
 * <h2>Por que existe</h2>
 * Cada modulo produz sua telemetria como texto — formato simples de escrever e
 * de verificar nos testes numericos, que procuram rotulos como
 * {@code "Velocidade orbital:"}. Mostrar esse texto cru numa <i>Label</i> unica
 * gerava um paredao de trinta linhas monoespacadas, sem hierarquia: era
 * impossivel achar um numero rapidamente durante uma apresentacao.
 *
 * <p>Esta view mantem o contrato de texto (nada muda para os modulos nem para a
 * validacao) e cuida so da apresentacao, seguindo quatro regras de escrita:
 *
 * <table border="1">
 *   <caption>Convencao do texto de telemetria</caption>
 *   <tr><th>Linha</th><th>Vira</th></tr>
 *   <tr><td>Primeira linha sem {@code :}</td><td>faixa de destaque com o estado atual</td></tr>
 *   <tr><td>Linha toda em MAIUSCULAS</td><td>titulo de secao</td></tr>
 *   <tr><td>{@code Rotulo: valor}</td><td>linha com rotulo a esquerda e valor a direita</td></tr>
 *   <tr><td>Linha iniciada por {@code >}</td><td>nota explicativa, em texto menor</td></tr>
 * </table>
 *
 * <p>Linhas que nao casam com nenhuma regra viram nota — assim um modulo que
 * ainda escreva prosa continua aparecendo de forma legivel, sem quebrar nada.
 */
public final class TelemetryView extends VBox {

    /** Valores acima deste tamanho vao para a linha de baixo, sem espremer o rotulo. */
    private static final int INLINE_VALUE_LIMIT = 22;

    private String renderedText;

    public TelemetryView() {
        super(0);
        getStyleClass().add("telemetry-box");
    }

    /**
     * Atualiza o conteudo. Reconstroi os nos apenas quando o texto muda de fato
     * — o Engine chama isto a cada segundo e a maior parte das vezes o conteudo
     * e identico.
     */
    public void setTelemetry(String text) {
        String safe = text == null ? "" : text;
        if (safe.equals(renderedText)) return;
        renderedText = safe;

        getChildren().clear();
        boolean headlineUsed = false;

        for (String rawLine : safe.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                addSpacer();
                continue;
            }
            if (line.startsWith(">")) {
                addNote(line.substring(1).strip());
                continue;
            }

            int separator = line.indexOf(':');
            boolean isRow = separator > 0 && separator < line.length() - 1;

            // A ordem importa: uma linha com ':' e sempre par rotulo/valor,
            // mesmo que o valor esteja todo em maiusculas ("Estado: PRONTA").
            if (!headlineUsed && !isRow) {
                addHeadline(stripDecoration(line));
                headlineUsed = true;
            } else if (isRow) {
                addRow(line.substring(0, separator).strip(),
                       line.substring(separator + 1).strip());
            } else if (isSectionTitle(line)) {
                addSectionTitle(stripDecoration(line));
            } else {
                addNote(line);
            }
        }

        if (getChildren().isEmpty()) addNote("Sem dados para exibir.");
    }

    // ── Construcao dos nos ─────────────────────────────────────────────

    private void addHeadline(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("telemetry-headline");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(label);
    }

    private void addSectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("telemetry-section");
        label.setWrapText(true);
        getChildren().add(label);
    }

    private void addRow(String label, String value) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("telemetry-label");
        labelNode.setWrapText(true);

        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("telemetry-value");
        if (deservesEmphasis(value)) valueNode.getStyleClass().add("telemetry-value-strong");
        valueNode.setWrapText(true);

        // Valor curto: rotulo e valor lado a lado, valor alinhado a direita.
        // Valor longo: empilhados, para nenhum dos dois ficar espremido numa
        // coluna estreita quando a janela e pequena.
        if (value.length() <= INLINE_VALUE_LIMIT) {
            HBox row = new HBox(10, labelNode, valueNode);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("telemetry-row");
            HBox.setHgrow(labelNode, Priority.ALWAYS);
            labelNode.setMaxWidth(Double.MAX_VALUE);
            getChildren().add(row);
        } else {
            VBox stacked = new VBox(1, labelNode, valueNode);
            stacked.getStyleClass().add("telemetry-row");
            getChildren().add(stacked);
        }
    }

    private void addNote(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("telemetry-note");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(label);
    }

    private void addSpacer() {
        if (getChildren().isEmpty()) return;
        Region spacer = new Region();
        spacer.setMinHeight(5);
        getChildren().add(spacer);
    }

    // ── Classificacao das linhas ───────────────────────────────────────

    /** Titulo de secao: sem letras minusculas e com ao menos uma maiuscula. */
    private static boolean isSectionTitle(String line) {
        boolean hasUpper = false;
        for (char c : line.toCharArray()) {
            if (Character.isLowerCase(c)) return false;
            if (Character.isUpperCase(c)) hasUpper = true;
        }
        return hasUpper;
    }

    /** Destaca desfechos: chegada, quebra, vitoria, falha. */
    private static boolean deservesEmphasis(String value) {
        String lower = value.toLowerCase();
        return lower.contains("chegou")
            || lower.contains("sobrevoo")
            || lower.contains("venceu")
            || lower.contains("quebr")
            || lower.contains("insuficiente")
            || lower.contains("escape");
    }

    /** Remove hifens e sinais usados como moldura do titulo no texto cru. */
    private static String stripDecoration(String line) {
        return line.replaceAll("^[-=\\s]+|[-=\\s]+$", "").strip();
    }
}
