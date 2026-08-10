package engine.ui.simulation;

import engine.simulation.LabPreset;
import engine.simulation.LabPresetCatalog;
import engine.simulation.ModuleManager;
import engine.simulation.SimulationModule;
import engine.simulation.parameters.Parameter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * Painel lateral de controle de módulos educacionais.
 *
 * Exibe:
 *   - Seletor de módulo (abas ou lista)
 *   - Controles de parâmetros (sliders + campos de valor)
 *   - Botões de ação (Lançar, Reset, Próximo desafio)
 *   - Painel de telemetria/informação do módulo ativo
 */
public class ModulePanel extends VBox {

    /** Largura minima do painel; fonte unica reutilizada pelo layout responsivo do Engine. */
    public static final double MIN_WIDTH = 215;

    private static final String STYLE_DARK  = "-fx-background-color: #111827;";
    private static final String STYLE_TITLE = "-fx-text-fill: #e0f2fe; -fx-font-family: monospace; -fx-font-weight: bold;";
    private static final String STYLE_BTN   =
        "-fx-background-color: #1f2937; -fx-text-fill: #e5e7eb; -fx-border-color: #475569; " +
        "-fx-border-radius: 6; -fx-background-radius: 6; -fx-font-family: monospace; " +
        "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 9 12;";
    private static final String STYLE_BTN_ACTIVE =
        "-fx-background-color: #2563eb; -fx-text-fill: #ffffff; -fx-border-color: #60a5fa; " +
        "-fx-border-radius: 6; -fx-background-radius: 6; -fx-font-family: monospace; " +
        "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 9 12;";

    // ── Referências ────────────────────────────────
    private final ModuleManager manager;

    // ── Subpainéis ─────────────────────────────────
    private final VBox moduleButtonsPane = new VBox(6);
    private final VBox parametersPane    = new VBox(6);
    private final Label lblModuleName    = new Label("Nenhum módulo");
    private final Label lblModuleDesc    = new Label("");
    private final Label lblCurriculum    = new Label("");
    private final TelemetryView telemetryView = new TelemetryView();

    /** Guarda o texto do desafio para nao ser sobrescrito pela telemetria viva. */
    private String challengeBriefing = "";

    // Botões de ação do módulo
    private Button btnReset;
    private Button btnLaunch;
    private Button btnNextChallenge;
    private Button btnPause;
    private Button btnStep;
    private Button btnExportCsv;
    private ComboBox<String> cbTimeScale;
    private ComboBox<LabPreset> cbPreset;
    private Label lblLabClock;
    private Label lblPresetNote;

    // Mapa de controles por nome de parâmetro
    private final Map<String, Slider> paramSliders = new HashMap<>();
    private final Map<String, Label>  paramValues  = new HashMap<>();
    private final Map<String, TextField> paramInputs = new HashMap<>();
    private final Map<String, Label> paramStatus = new HashMap<>();
    private boolean applyingTypedValue = false;

    // Callbacks para ações
    private Runnable onResetRequested;
    private Runnable onLaunchRequested;
    private Runnable onPauseRequested;
    private Runnable onStepRequested;
    private Runnable onExportRequested;
    private DoubleConsumer onTimeScaleChanged;

    public ModulePanel(ModuleManager manager) {
        super(0);
        this.manager = manager;
        setPrefWidth(320);
        setMinWidth(MIN_WIDTH);
        setStyle(STYLE_DARK + "-fx-border-color: #334155; -fx-border-width: 0 1 0 0;");
        setPadding(new Insets(0));

        buildLayout();
        refresh();
    }

    public void setPanelWidth(double width) {
        double usableWidth = Math.max(MIN_WIDTH, width);
        setPrefWidth(usableWidth);
        setMinWidth(Math.min(MIN_WIDTH, usableWidth));
        setMaxWidth(usableWidth);
    }

    // ══════════════════════════════════════════════
    //  LAYOUT
    // ══════════════════════════════════════════════

    private void buildLayout() {
        // ── Header ──────────────────────────────
        VBox header = new VBox(5);
        header.setPadding(new Insets(18, 18, 14, 18));
        header.setStyle("-fx-background-color: #0b1223; -fx-border-color: #334155; -fx-border-width: 0 0 1 0;");

        Label appLabel = new Label("SimulaFisica 3D");
        appLabel.setFont(Font.font("monospace", FontWeight.BOLD, 22));
        appLabel.setStyle("-fx-text-fill: #e0f2fe;");

        Label subLabel = new Label("Fisica Computacional | JavaFX | POO");
        subLabel.setFont(Font.font("monospace", 13));
        subLabel.setStyle("-fx-text-fill: #94a3b8;");

        header.getChildren().addAll(appLabel, subLabel);

        // ── Seletor de módulos ───────────────────
        ScrollPane moduleScroll = new ScrollPane(moduleButtonsPane);
        moduleScroll.setFitToWidth(true);
        moduleScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        moduleScroll.setPrefHeight(210);
        moduleScroll.setMinHeight(140);

        // ── Info do módulo ───────────────────────
        VBox moduleInfo = new VBox(5);
        moduleInfo.setPadding(new Insets(12, 18, 12, 18));
        moduleInfo.setStyle("-fx-background-color: #0d1a25; -fx-border-color: #334155; -fx-border-width: 1 0;");

        lblModuleName.setFont(Font.font("monospace", FontWeight.BOLD, 17));
        lblModuleName.setStyle("-fx-text-fill: #bfdbfe;");
        lblModuleName.setWrapText(true);

        lblModuleDesc.setFont(Font.font("monospace", 13));
        lblModuleDesc.setStyle("-fx-text-fill: #cbd5e1; -fx-wrap-text: true;");
        lblModuleDesc.setWrapText(true);

        lblCurriculum.setFont(Font.font("monospace", 12));
        lblCurriculum.setStyle("-fx-text-fill: #86efac;");

        moduleInfo.getChildren().addAll(lblModuleName, lblModuleDesc, lblCurriculum);

        // ── Parâmetros ───────────────────────────
        parametersPane.setPadding(new Insets(0));

        // ── Ações ────────────────────────────────
        VBox actionsSection = new VBox(6);

        btnReset = styledBtn("Resetar simulacao");
        btnLaunch = styledBtn("Executar lancamento");
        btnNextChallenge = styledBtn("Proximo desafio");

        btnReset.setMaxWidth(Double.MAX_VALUE);
        btnLaunch.setMaxWidth(Double.MAX_VALUE);
        btnNextChallenge.setMaxWidth(Double.MAX_VALUE);
        btnLaunch.setStyle(STYLE_BTN_ACTIVE);

        btnReset.setOnAction(e -> { if (onResetRequested != null) onResetRequested.run(); });
        btnLaunch.setOnAction(e -> { if (onLaunchRequested != null) onLaunchRequested.run(); });
        btnNextChallenge.setOnAction(e -> {
            if (manager.hasActiveModule()) manager.getActiveModule().advanceChallenge();
            refresh();
        });

        // O botao de acao do modulo vem primeiro: e o mais usado.
        actionsSection.getChildren().addAll(btnLaunch, btnNextChallenge, btnReset);

        VBox labSection = new VBox(7);
        lblLabClock = new Label("Tempo simulado: 0.00 s");
        lblLabClock.setFont(Font.font("monospace", 12));
        lblLabClock.setStyle("-fx-text-fill: #bfdbfe;");

        btnPause = styledBtn("Pausar");
        btnStep = styledBtn("Passo unico");
        btnExportCsv = styledBtn("Exportar CSV");
        btnStep.setTooltip(new Tooltip("Avanca 1/120 s quando a simulacao esta pausada."));
        btnExportCsv.setTooltip(new Tooltip("Salva as amostras do grafico em CSV."));
        btnPause.setOnAction(e -> { if (onPauseRequested != null) onPauseRequested.run(); });
        btnStep.setOnAction(e -> { if (onStepRequested != null) onStepRequested.run(); });
        btnExportCsv.setOnAction(e -> { if (onExportRequested != null) onExportRequested.run(); });

        HBox playbackButtons = new HBox(7, btnPause, btnStep);
        HBox.setHgrow(btnPause, Priority.ALWAYS);
        HBox.setHgrow(btnStep, Priority.ALWAYS);
        btnPause.setMaxWidth(Double.MAX_VALUE);
        btnStep.setMaxWidth(Double.MAX_VALUE);

        cbTimeScale = new ComboBox<>();
        cbTimeScale.getItems().addAll("0.25x", "0.50x", "1.00x", "2.00x", "4.00x");
        cbTimeScale.setValue("1.00x");
        cbTimeScale.setMaxWidth(Double.MAX_VALUE);
        cbTimeScale.setMinHeight(38);
        cbTimeScale.setStyle("-fx-background-color: #1f2937; " +
            "-fx-border-color: #475569; -fx-font-family: monospace; -fx-font-size: 13px;");
        cbTimeScale.setOnAction(e -> {
            if (onTimeScaleChanged != null && cbTimeScale.getValue() != null) {
                onTimeScaleChanged.accept(Double.parseDouble(cbTimeScale.getValue().replace("x", "")));
            }
        });

        cbPreset = new ComboBox<>();
        cbPreset.setPromptText("Preset experimental");
        cbPreset.setMaxWidth(Double.MAX_VALUE);
        cbPreset.setMinHeight(38);
        cbPreset.setStyle("-fx-background-color: #1f2937; " +
            "-fx-border-color: #475569; -fx-font-family: monospace; -fx-font-size: 13px;");
        cbPreset.setOnAction(e -> applySelectedPreset());
        lblPresetNote = new Label("Presets repetem configuracoes para comparacao.");
        lblPresetNote.setWrapText(true);
        lblPresetNote.setFont(Font.font("monospace", 11));
        lblPresetNote.setStyle("-fx-text-fill: #94a3b8;");

        labSection.getChildren().addAll(
            lblLabClock,
            playbackButtons,
            new VBox(4, compactLabel("Escala do tempo"), cbTimeScale),
            new VBox(4, compactLabel("Preset"), cbPreset, lblPresetNote),
            btnExportCsv
        );

        // ── Telemetria ───────────────────────────
        ScrollPane telemetryScroll = new ScrollPane(telemetryView);
        telemetryScroll.setFitToWidth(true);
        telemetryScroll.setPrefHeight(300);
        telemetryScroll.setMinHeight(170);

        // ── Montar ──────────────────────────────
        // Blocos retrateis: quem esta apresentando abre so o que precisa e o
        // painel deixa de ser uma coluna unica de 1500 px de altura.
        getChildren().addAll(
            header,
            collapsible("SIMULACOES", moduleScroll, true),
            moduleInfo,
            collapsible("PARAMETROS", parametersPane, true),
            collapsible("ACOES", actionsSection, true),
            collapsible("RESULTADOS", telemetryScroll, true),
            collapsible("MODO LABORATORIO", labSection, false),
            collapsible("REFERENCIAS", referencesLabel(), false)
        );
    }

    /**
     * Envolve um bloco do painel numa secao que o usuario pode recolher.
     * O estilo do cabecalho vem de {@code styles.css} (classe {@code sim-section}).
     */
    private TitledPane collapsible(String title, javafx.scene.Node content, boolean expanded) {
        TitledPane pane = new TitledPane(title, content);
        pane.getStyleClass().add("sim-section");
        pane.setExpanded(expanded);
        pane.setAnimated(false);   // animacao atrasa a leitura durante a aula
        return pane;
    }

    // ══════════════════════════════════════════════
    //  REFRESH — sincroniza com módulo ativo
    // ══════════════════════════════════════════════

    /**
     * Atualiza o painel inteiro ao mudar de módulo ou estado.
     * Chamado pelo ModuleManager via callback.
     */
    public void refresh() {
        rebuildModuleButtons();
        rebuildParameters();
        rebuildPresets();
        updateModuleInfo();
    }

    private void rebuildModuleButtons() {
        moduleButtonsPane.getChildren().clear();
        for (SimulationModule mod : manager.getAllModules()) {
            Button btn = new Button(mod.getDisplayName());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setMinHeight(56);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setWrapText(true);
            btn.setFont(Font.font("monospace", FontWeight.BOLD, 14));
            boolean active = manager.isActive(mod.getId());
            btn.setText(mod.getDisplayName() + "\n" + mod.getCurriculumTopic());
            btn.setStyle((active ? STYLE_BTN_ACTIVE : STYLE_BTN) +
                (active ? "-fx-border-width: 1 1 1 4;" : "-fx-border-width: 1;") +
                "-fx-text-alignment: left; -fx-line-spacing: 2px;");
            btn.setOnAction(e -> {
                manager.activateById(mod.getId());
                refresh();
            });
            btn.setTooltip(new Tooltip(mod.getDescription()));
            moduleButtonsPane.getChildren().add(btn);

        }
    }

    private void rebuildParameters() {
        parametersPane.getChildren().clear();
        paramSliders.clear();
        paramValues.clear();
        paramInputs.clear();
        paramStatus.clear();

        SimulationModule mod = manager.getActiveModule();
        if (mod == null) return;

        for (Parameter param : mod.getParameters()) {
            VBox paramRow = buildParamRow(param, mod);
            parametersPane.getChildren().add(paramRow);
        }
    }

    private VBox buildParamRow(Parameter param, SimulationModule mod) {
        VBox row = new VBox(5);
        row.setPadding(new Insets(9, 10, 9, 10));
        row.setStyle("-fx-background-color: #0b1223; -fx-border-color: #253449; " +
            "-fx-border-radius: 7; -fx-background-radius: 7;");

        // Label com valor atual
        HBox labelRow = new HBox();
        labelRow.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(param.getLabel());
        lbl.setFont(Font.font("monospace", 13));
        lbl.setStyle("-fx-text-fill: #e5e7eb;");

        Label valLbl = new Label(formatParamValue(param));
        valLbl.setFont(Font.font("monospace", 13));
        valLbl.setStyle("-fx-text-fill: #7dd3fc;");
        HBox.setHgrow(lbl, Priority.ALWAYS);
        labelRow.getChildren().addAll(lbl, valLbl);

        if (param.hasOptions()) {
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().addAll(param.getOptions());
            combo.getSelectionModel().select(param.getOptionIndex());
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setMinHeight(34);
            // Sem -fx-text-fill aqui: a cor do texto vem de styles.css, que trata
            // tambem a lista suspensa. Definir inline vazava para o popup.
            combo.setStyle("-fx-background-color: #1f2937; " +
                "-fx-border-color: #475569; -fx-font-family: monospace; -fx-font-size: 13px;");
            combo.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
                int selected = Math.max(0, newVal.intValue());
                param.setValue(selected);
                valLbl.setText(param.getSelectedOption());
                manager.notifyParameterChanged(param.getName(), selected);
            });

            if (!param.getTooltip().isEmpty()) {
                Tooltip.install(row, new Tooltip(param.getTooltip()));
            }

            row.getChildren().addAll(labelRow, combo);
            return row;
        }

        // Slider
        Slider slider = new Slider(param.getMinValue(), param.getMaxValue(), param.getValue());
        slider.setBlockIncrement(param.getStep());
        slider.setMajorTickUnit((param.getMaxValue() - param.getMinValue()) / 4.0);
        slider.setMinHeight(30);
        slider.setStyle("-fx-control-inner-background: #1f2937;");
        slider.setMaxWidth(Double.MAX_VALUE);

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (applyingTypedValue) return;
            double snapped = snapToStep(newVal.doubleValue(), param.getStep());
            param.setValue(snapped);
            valLbl.setText(formatParamValue(param));
            TextField input = paramInputs.get(param.getName());
            if (input != null && !input.isFocused()) {
                input.setText(formatPlainValue(param));
            }
            manager.notifyParameterChanged(param.getName(), param.getValue());
        });

        TextField input = numericInput(formatPlainValue(param));
        input.setTooltip(new Tooltip(buildInputTooltip(param)));

        // Mensagem de validacao: some quando o valor esta na faixa usual.
        Label status = new Label();
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setFont(Font.font("monospace", 11));
        status.setVisible(false);
        status.setManaged(false);
        paramStatus.put(param.getName(), status);

        input.setOnAction(e -> applyTypedValue(input, slider, param, valLbl));
        input.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) applyTypedValue(input, slider, param, valLbl);
        });

        HBox controlRow = new HBox(8, slider, input);
        controlRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);

        // Tooltip
        if (!param.getTooltip().isEmpty()) {
            Tooltip.install(row, new Tooltip(param.getTooltip()));
        }

        paramSliders.put(param.getName(), slider);
        paramValues.put(param.getName(), valLbl);
        paramInputs.put(param.getName(), input);

        row.getChildren().addAll(labelRow, controlRow, status);
        return row;
    }

    /** Explica no tooltip do campo o que pode ser digitado. */
    private String buildInputTooltip(Parameter param) {
        StringBuilder tip = new StringBuilder("Digite qualquer valor e pressione Enter. ");
        tip.append("Virgula ou ponto decimal.\n");
        tip.append(String.format("Faixa usual: %.4g a %.4g%n",
            param.getMinValue(), param.getMaxValue()));
        boolean bounded = param.getHardMin() > Double.NEGATIVE_INFINITY
            || param.getHardMax() < Double.POSITIVE_INFINITY;
        if (bounded) {
            tip.append(String.format("Limite fisico: %.4g a %.4g%n",
                param.getHardMin(), param.getHardMax()));
        }
        if (!param.getReference().isBlank()) {
            tip.append("Referencia: ").append(param.getReference());
        }
        return tip.toString().strip();
    }

    private void updateModuleInfo() {
        SimulationModule mod = manager.getActiveModule();
        if (mod == null) {
            lblModuleName.setText("Nenhum módulo ativo");
            lblModuleDesc.setText("");
            lblCurriculum.setText("");
            return;
        }
        lblModuleName.setText(mod.getDisplayName());
        lblModuleDesc.setText(mod.getDescription());
        lblCurriculum.setText("Topico: " + mod.getCurriculumTopic());

        // Desafio atual: fica visivel ate a primeira telemetria chegar.
        var challenge = mod.getCurrentChallenge();
        challengeBriefing = challenge == null
            ? "Modo livre\n\n> Explore os parametros a vontade."
            : String.format("Desafio %d de %d%n%nDESAFIO%nTitulo: %s%n%n> %s%n> Dica: %s",
                mod.getCurrentChallengeIndex() + 1, mod.getChallengeCount(),
                challenge.getTitle(), challenge.getInstruction(), challenge.getHint());
        telemetryView.setTelemetry(challengeBriefing);

        // O botao de acao aparece apenas para modulos que declaram um rotulo.
        // O texto e sempre reescrito: mantê-lo quando o rotulo vem nulo deixava
        // o botao com a legenda do modulo anterior ("Iniciar colisao" no
        // pendulo, por exemplo), que reaparecia na proxima troca de modulo.
        String actionLabel = mod.launchActionLabel();
        btnLaunch.setText(actionLabel == null ? "" : actionLabel);
        btnLaunch.setVisible(actionLabel != null);
        btnLaunch.setManaged(actionLabel != null);
        btnNextChallenge.setVisible(mod.hasMoreChallenges());
        btnNextChallenge.setManaged(mod.hasMoreChallenges());
    }

    private void rebuildPresets() {
        cbPreset.getItems().clear();
        SimulationModule mod = manager.getActiveModule();
        if (mod == null) return;

        cbPreset.getItems().addAll(LabPresetCatalog.forModule(mod.getId()));
        boolean hasPresets = !cbPreset.getItems().isEmpty();
        cbPreset.setDisable(!hasPresets);
        cbPreset.setPromptText(hasPresets ? "Escolha um preset" : "Sem preset para este modulo");
        lblPresetNote.setText(hasPresets
            ? "Presets repetem configuracoes para comparacao."
            : "Este modulo usa o estado atual da cena.");
    }

    private void applySelectedPreset() {
        LabPreset preset = cbPreset.getValue();
        SimulationModule mod = manager.getActiveModule();
        if (preset == null || mod == null) return;

        preset.values().forEach((name, value) -> mod.getParameters().setValue(name, value));
        manager.resetActive();
        rebuildParameters();
        lblPresetNote.setText(preset.note());
    }

    // ══════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("monospace", 13));
        l.setStyle("-fx-text-fill: #93c5fd; -fx-font-weight: bold; -fx-padding: 8 0 4 0;");
        return l;
    }

    private Label referencesLabel() {
        Label label = new Label(
            "Halliday, Resnick e Walker - Fundamentos de Fisica\n" +
            "Serway e Jewett - Principios de Fisica\n" +
            "Baraff e Witkin - Physically Based Modeling\n" +
            "Ericson - Real-Time Collision Detection\n" +
            "NASA/JPL - Approximate Positions of the Planets\n" +
            "NASA - Planetary Fact Sheet\n" +
            "OpenJFX Documentation - JavaFX"
        );
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setFont(Font.font("monospace", 11));
        label.setStyle("-fx-text-fill: #94a3b8; -fx-line-spacing: 3px;");
        return label;
    }

    private Label compactLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("monospace", 12));
        label.setStyle("-fx-text-fill: #cbd5e1;");
        return label;
    }

    private Separator sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: #1a1a35;");
        return s;
    }

    private Button styledBtn(String text) {
        Button b = new Button(text);
        b.setStyle(STYLE_BTN);
        b.setMinHeight(40);
        b.setFont(Font.font("monospace", 14));
        return b;
    }

    private TextField numericInput(String value) {
        TextField input = new TextField(value);
        input.setPrefWidth(92);
        input.setMinWidth(82);
        input.setMaxWidth(104);
        input.setFont(Font.font("monospace", 13));
        input.setStyle(
            "-fx-background-color: #020617; -fx-text-fill: #e0f2fe; -fx-border-color: #475569; " +
            "-fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 6 8;"
        );
        return input;
    }

    private String formatParamValue(Parameter p) {
        if (p.hasOptions()) return p.getSelectedOption();
        double v = p.getValue();
        String unit = p.getUnit().isEmpty() ? "" : " " + p.getUnit();
        return p.getStep() < 0.5
            ? String.format("%.2f%s", v, unit)
            : String.format("%.1f%s", v, unit);
    }

    private String formatPlainValue(Parameter p) {
        double v = p.getValue();
        if (p.getStep() < 0.05) return String.format("%.3f", v);
        if (p.getStep() < 0.5) return String.format("%.2f", v);
        return String.format("%.1f", v);
    }

    /**
     * Aplica o valor digitado no campo de texto.
     *
     * <p>Aqui o usuário pode sair da faixa do slider: o valor é validado contra
     * os limites físicos do parâmetro, o slider se estende para acomodá-lo e a
     * mensagem explica o que muda quando o valor foge do usual.
     */
    private void applyTypedValue(TextField input, Slider slider, Parameter param, Label valLbl) {
        String raw = input.getText() == null ? "" : input.getText().trim().replace(',', '.');
        if (raw.isEmpty()) {
            input.setText(formatPlainValue(param));
            showParamStatus(param, null);
            return;
        }

        double typed;
        try {
            typed = Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            input.setText(formatPlainValue(param));
            showParamStatus(param, new Parameter.Feedback(param.getValue(),
                Parameter.Severity.REJECTED,
                "\"" + raw + "\" nao e um numero. Use 2.5 ou 2,5."));
            return;
        }

        Parameter.Feedback feedback = param.validate(typed);
        double oldValue = param.getValue();

        applyingTypedValue = true;
        try {
            param.setValue(feedback.value());
            expandSliderTo(slider, param.getValue());
            slider.setValue(param.getValue());
        } finally {
            applyingTypedValue = false;
        }

        valLbl.setText(formatParamValue(param));
        input.setText(formatPlainValue(param));
        showParamStatus(param, feedback);

        if (Math.abs(oldValue - param.getValue()) > 1e-9) {
            manager.notifyParameterChanged(param.getName(), param.getValue());
        }
    }

    /**
     * Estende o slider para conter um valor digitado fora da faixa recomendada,
     * mantendo o controle utilizável em vez de fixar o cursor na ponta.
     */
    private void expandSliderTo(Slider slider, double value) {
        if (value > slider.getMax()) slider.setMax(value);
        if (value < slider.getMin()) slider.setMin(value);
    }

    /** Exibe (ou esconde) a mensagem de validação abaixo do parâmetro. */
    private void showParamStatus(Parameter param, Parameter.Feedback feedback) {
        Label status = paramStatus.get(param.getName());
        if (status == null) return;

        if (feedback == null || feedback.isOk()) {
            status.setText("");
            status.setVisible(false);
            status.setManaged(false);
            return;
        }

        boolean rejected = feedback.severity() == Parameter.Severity.REJECTED;
        status.setText((rejected ? "! " : "~ ") + feedback.message());
        status.setStyle(rejected
            ? "-fx-text-fill: #fca5a5; -fx-padding: 3 0 0 0;"
            : "-fx-text-fill: #fcd34d; -fx-padding: 3 0 0 0;");
        status.setVisible(true);
        status.setManaged(true);
    }

    private double snapToStep(double value, double step) {
        return step > 0 ? Math.round(value / step) * step : value;
    }

    // ── API pública ────────────────────────────────
    public void setOnResetRequested(Runnable r)  { this.onResetRequested  = r; }
    public void setOnLaunchRequested(Runnable r) { this.onLaunchRequested = r; }
    public void setOnPauseRequested(Runnable r)  { this.onPauseRequested  = r; }
    public void setOnStepRequested(Runnable r)   { this.onStepRequested   = r; }
    public void setOnExportRequested(Runnable r) { this.onExportRequested = r; }
    public void setOnTimeScaleChanged(DoubleConsumer c) { this.onTimeScaleChanged = c; }

    /** Atualiza o painel de resultados com os dados do frame atual. */
    public void updateTelemetry(String text) {
        telemetryView.setTelemetry(text == null || text.isBlank() ? challengeBriefing : text);
    }

    public void updateLabState(boolean paused, double timeScale, double simTime) {
        btnPause.setText(paused ? "Retomar" : "Pausar");
        btnPause.setStyle(paused ? STYLE_BTN_ACTIVE : STYLE_BTN);
        btnStep.setDisable(!paused);
        lblLabClock.setText(String.format("Tempo simulado: %.2f s | escala: %.2fx", simTime, timeScale));
        String desired = String.format(Locale.US, "%.2fx", timeScale);
        if (!desired.equals(cbTimeScale.getValue())) cbTimeScale.setValue(desired);
    }
}
