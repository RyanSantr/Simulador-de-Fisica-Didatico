package engine.ui;

import engine.scene.ObjectFactory;
import engine.scene.SceneObject;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Barra de ferramentas e painel de controle lateral.
 * Estilo dark-theme consistente com a engine.
 */
public class Toolbar extends VBox {

    /** Largura minima do painel; fonte unica reutilizada pelo layout responsivo do Engine. */
    public static final double MIN_WIDTH = 195;

    private double lastPanelWidth = -1;

    // ── Controles de modo de desenho ────────────────
    private final ToggleGroup drawModeGroup  = new ToggleGroup();
    private final ToggleButton btnFree       = createToggle("Livre",      drawModeGroup);
    private final ToggleButton btnRect       = createToggle("Retangulo",  drawModeGroup);
    private final ToggleButton btnCircle     = createToggle("Circulo",    drawModeGroup);
    private final ToggleButton btnTriangle   = createToggle("Triangulo",  drawModeGroup);

    // ── Forma 3D ───────────────────────────────────
    private final ComboBox<String> cbShape = new ComboBox<>();

    // ── Física ─────────────────────────────────────
    private final ToggleGroup physicsGroup  = new ToggleGroup();
    private final ToggleButton btnRigid     = createToggle("Madeira",   physicsGroup);
    private final ToggleButton btnBouncy    = createToggle("Borracha",  physicsGroup);
    private final ToggleButton btnStatic    = createToggle("Estatico",  physicsGroup);
    private final ToggleButton btnHeavy     = createToggle("Aco",       physicsGroup);

    // ── Cor ────────────────────────────────────────
    private final ColorPicker colorPicker = new ColorPicker(Color.web("#4488ff"));

    // ── Ações ──────────────────────────────────────
    private final Button btnRandom   = createBtn("Aleatorio");
    private final Button btnExplode  = createBtn("Impulso");
    private final Button btnClear    = createBtn("Limpar");
    private final Button btnReset    = createBtn("Reset Camera");
    private final Button btnSaveScenario = createBtn("Salvar cena");
    private final Button btnLoadScenario = createBtn("Carregar cena");

    // ── Renderer ───────────────────────────────────
    private final CheckBox cbWireframe  = createCheck("Wireframe");
    private final CheckBox cbGrid       = createCheck("Grade");
    private final CheckBox cbShadows    = createCheck("Sombras");
    private final CheckBox cbVectors    = createCheck("Vetores");
    private final CheckBox cbPerformance = createCheck("Modo leve");

    private final ToggleButton btnGravity = createToggle("Gravidade", new ToggleGroup());

    // ── Estatísticas ───────────────────────────────
    private final Label lblObjects   = createStat("Objetos: 0");
    private final Label lblFPS       = createStat("FPS: --");
    private final Label lblPhysics   = createStat("Ativos: 0");
    private final LabGraphCanvas labGraph = new LabGraphCanvas(326, 168);
    private final Label lblSelected = createStat("Selecionado: nenhum");
    private final TextField posX = createNumberField();
    private final TextField posY = createNumberField();
    private final TextField posZ = createNumberField();
    private final TextField velX = createNumberField();
    private final TextField velY = createNumberField();
    private final TextField velZ = createNumberField();
    private final TextField mass = createNumberField();
    private final Button btnApplyObject = createBtn("Aplicar objeto");
    private final Label lblCollisions= createStat("Colisões: 0");

    public Toolbar() {
        super(10);
        setPadding(new Insets(14));
        setPrefWidth(260);
        setMinWidth(MIN_WIDTH);
        setStyle("-fx-background-color: #111827; -fx-border-color: #334155; -fx-border-width: 0 0 0 1;");

        // Defaults selecionados
        btnRect.setSelected(true);
        btnRigid.setSelected(true);
        btnGravity.setSelected(true);
        cbGrid.setSelected(true);
        cbShadows.setSelected(true);
        cbVectors.setSelected(true);
        cbPerformance.setSelected(true);

        // Shape combobox
        cbShape.getItems().addAll(
            "Automatico", "Cubo", "Esfera", "Cone",
            "Cilindro", "Tetraedro", "Octaedro", "Icosaedro", "Torus"
        );
        cbShape.setValue("Automatico");
        styleCombo(cbShape);

        // Montar layout
        getChildren().addAll(
            section("BANCADA 3D"),
            hbox(btnFree, btnRect),
            hbox(btnCircle, btnTriangle),

            sep(),
            section("FORMA 3D"),
            cbShape,

            sep(),
            section("MATERIAL FISICO"),
            hbox(btnRigid, btnBouncy),
            hbox(btnStatic, btnHeavy),

            sep(),
            section("COR"),
            colorPicker,

            sep(),
            section("ACOES"),
            hbox(btnRandom, btnExplode),
            hbox(btnClear, btnReset),
            hbox(btnSaveScenario, btnLoadScenario),

            sep(),
            section("OBJETO SELECIONADO"),
            lblSelected,
            vectorRow("Posicao m", posX, posY, posZ),
            vectorRow("Velocidade m/s", velX, velY, velZ),
            valueRow("Massa kg", mass),
            btnApplyObject,

            sep(),
            section("DESEMPENHO E VISUAL"),
            hbox(cbWireframe, cbGrid),
            hbox(cbShadows, cbVectors),
            cbPerformance,
            btnGravity,

            sep(),
            section("ESTATISTICAS"),
            lblObjects, lblFPS, lblPhysics, lblCollisions,

            sep(),
            section("GRAFICO"),
            labGraph
        );

        // Estilizar botões de ação
        btnExplode.setStyle(btnExplode.getStyle() + "; -fx-text-fill: #ff8844;");
        btnClear.setStyle(btnClear.getStyle() + "; -fx-text-fill: #ff5555;");

        applyGlobalStyle();
    }

    // ── Construtores de widgets ────────────────────
    private static ToggleButton createToggle(String text, ToggleGroup g) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(g);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setMinHeight(38);
        btn.setFont(Font.font("monospace", 15));
        btn.setStyle(baseStyle());
        btn.selectedProperty().addListener((obs, o, n) ->
            btn.setStyle(n ? activeStyle() : baseStyle())
        );
        return btn;
    }

    private static Button createBtn(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setMinHeight(38);
        btn.setFont(Font.font("monospace", 15));
        btn.setStyle(baseStyle());
        return btn;
    }

    private static CheckBox createCheck(String text) {
        CheckBox cb = new CheckBox(text);
        cb.setFont(Font.font("monospace", 15));
        cb.setStyle("-fx-text-fill: #e5e7eb; -fx-padding: 7 0;");
        return cb;
    }

    private static Label createStat(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("monospace", 15));
        l.setStyle("-fx-text-fill: #7dd3fc; -fx-padding: 2 0;");
        return l;
    }

    private static TextField createNumberField() {
        TextField field = new TextField("0");
        field.setMinHeight(38);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setFont(Font.font("monospace", 13));
        field.setStyle("-fx-background-color: #0f172a; -fx-text-fill: #f8fafc; " +
            "-fx-border-color: #475569; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 5 7;");
        return field;
    }

    private static Label section(String title) {
        Label l = new Label(title);
        l.setFont(Font.font("monospace", 14));
        l.setStyle("-fx-text-fill: #93c5fd; -fx-font-weight: bold; -fx-padding: 10 0 3 0;");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static Separator sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: #1a1a35;");
        VBox.setMargin(s, new Insets(4, 0, 2, 0));
        return s;
    }

    private static HBox hbox(javafx.scene.Node... nodes) {
        HBox h = new HBox(8, nodes);
        for (javafx.scene.Node n : nodes) HBox.setHgrow(n, Priority.ALWAYS);
        h.setMaxWidth(Double.MAX_VALUE);
        return h;
    }

    private static VBox vectorRow(String title, TextField x, TextField y, TextField z) {
        return new VBox(4, compactLabel(title + "  X / Y / Z"), hbox(x, y, z));
    }

    private static VBox valueRow(String title, TextField value) {
        return new VBox(4, compactLabel(title), value);
    }

    private static Label compactLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("monospace", 13));
        label.setStyle("-fx-text-fill: #cbd5e1;");
        return label;
    }

    private static void styleCombo(ComboBox<?> cb) {
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setMinHeight(42);
        // A cor do texto (aqui e na lista suspensa) vem de styles.css.
        cb.setStyle("-fx-background-color: #1f2937; " +
                    "-fx-border-color: #475569; -fx-font-family: monospace; -fx-font-size: 14; -fx-padding: 4 8;");
    }

    private static String baseStyle() {
        return "-fx-background-color: #1f2937; -fx-text-fill: #e5e7eb; " +
               "-fx-border-color: #475569; -fx-border-radius: 6; -fx-background-radius: 6; " +
               "-fx-padding: 8 12; -fx-cursor: hand;";
    }

    private static String activeStyle() {
        return "-fx-background-color: #2563eb; -fx-text-fill: #ffffff; " +
               "-fx-border-color: #60a5fa; -fx-border-radius: 6; -fx-background-radius: 6; " +
               "-fx-padding: 8 12; -fx-cursor: hand;";
    }

    private void applyGlobalStyle() {
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        colorPicker.setMinHeight(40);
        colorPicker.setStyle("-fx-background-color: #1f2937; -fx-border-color: #475569;");
        btnGravity.setMaxWidth(Double.MAX_VALUE);
        btnGravity.setStyle(activeStyle()); // começa selecionado visualmente
    }

    // ── Atualizar estatísticas ─────────────────────
    public void updateStats(int objects, double fps, int active, int collisions) {
        lblObjects.setText("Objetos: " + objects);
        lblFPS.setText(String.format("FPS: %.0f", fps));
        lblPhysics.setText("Ativos: " + active);
        lblCollisions.setText("Colisões/frame: " + collisions);
    }

    public void setPanelWidth(double width) {
        double usableWidth = Math.max(MIN_WIDTH, width);
        // Evita recalcular layout e redesenhar o grafico do lab quando a largura nao mudou
        // (o resize dispara este metodo a cada pixel, inclusive em ajustes so de altura).
        if (Math.abs(usableWidth - lastPanelWidth) < 0.5) return;
        lastPanelWidth = usableWidth;
        setPrefWidth(usableWidth);
        setMinWidth(Math.min(MIN_WIDTH, usableWidth));
        setMaxWidth(usableWidth);
        labGraph.setWidth(Math.max(170, usableWidth - 32));
        labGraph.redrawNow();
    }

    // ── Getters para bindings no Engine ───────────
    public void showSelectedObject(SceneObject object) {
        if (object == null) {
            lblSelected.setText("Selecionado: nenhum");
            return;
        }

        var body = object.getBody();
        lblSelected.setText("Selecionado: " + object.getName() + " / " + object.getShapeType());
        posX.setText(format(body.getPosition().x));
        posY.setText(format(body.getPosition().y));
        posZ.setText(format(body.getPosition().z));
        velX.setText(format(body.getVelocity().x));
        velY.setText(format(body.getVelocity().y));
        velZ.setText(format(body.getVelocity().z));
        mass.setText(format(body.getMass()));
    }

    private static String format(double value) {
        return String.format(java.util.Locale.US, "%.4f", value);
    }

    public DrawingCanvas.DrawMode getDrawMode() {
        return switch (((ToggleButton) drawModeGroup.getSelectedToggle()).getText()) {
            case "Livre"     -> DrawingCanvas.DrawMode.FREE;
            case "Circulo"   -> DrawingCanvas.DrawMode.CIRCLE;
            case "Triangulo" -> DrawingCanvas.DrawMode.TRIANGLE;
            default          -> DrawingCanvas.DrawMode.RECTANGLE;
        };
    }

    public ObjectFactory.ShapeType getShapeType() {
        return switch (cbShape.getValue()) {
            case "Cubo"      -> ObjectFactory.ShapeType.BOX;
            case "Esfera"    -> ObjectFactory.ShapeType.SPHERE;
            case "Cone"      -> ObjectFactory.ShapeType.CONE;
            case "Cilindro"  -> ObjectFactory.ShapeType.CYLINDER;
            case "Tetraedro" -> ObjectFactory.ShapeType.TETRAHEDRON;
            case "Octaedro"  -> ObjectFactory.ShapeType.OCTAHEDRON;
            case "Icosaedro" -> ObjectFactory.ShapeType.ICOSAHEDRON;
            case "Torus"     -> ObjectFactory.ShapeType.TORUS;
            default          -> ObjectFactory.ShapeType.RANDOM;
        };
    }

    public ObjectFactory.PhysicsPreset getPhysicsPreset() {
        ToggleButton sel = (ToggleButton) physicsGroup.getSelectedToggle();
        if (sel == null) return ObjectFactory.PhysicsPreset.WOOD;
        return switch (sel.getText()) {
            case "Borracha" -> ObjectFactory.PhysicsPreset.RUBBER;
            case "Estatico" -> ObjectFactory.PhysicsPreset.STATIC;
            case "Aco"      -> ObjectFactory.PhysicsPreset.STEEL;
            default         -> ObjectFactory.PhysicsPreset.WOOD;
        };
    }

    public Color    getSelectedColor()      { return colorPicker.getValue(); }
    public boolean  isWireframe()           { return cbWireframe.isSelected(); }
    public boolean  isGridVisible()         { return cbGrid.isSelected(); }
    public boolean  isShadowsEnabled()      { return cbShadows.isSelected(); }
    public boolean  isVectorsEnabled()      { return cbVectors.isSelected(); }
    public boolean  isPerformanceMode()     { return cbPerformance.isSelected(); }
    public boolean  isGravityEnabled()      { return btnGravity.isSelected(); }

    public Button   getBtnRandom()  { return btnRandom; }
    public Button   getBtnExplode() { return btnExplode; }
    public Button   getBtnClear()   { return btnClear; }
    public Button   getBtnReset()   { return btnReset; }
    public Button   getBtnSaveScenario() { return btnSaveScenario; }
    public Button   getBtnLoadScenario() { return btnLoadScenario; }
    public Button   getBtnApplyObject()  { return btnApplyObject; }

    public TextField getPosX() { return posX; }
    public TextField getPosY() { return posY; }
    public TextField getPosZ() { return posZ; }
    public TextField getVelX() { return velX; }
    public TextField getVelY() { return velY; }
    public TextField getVelZ() { return velZ; }
    public TextField getMass() { return mass; }

    public ToggleButton getBtnGravity()        { return btnGravity; }
    public CheckBox     getCbWireframe()        { return cbWireframe; }
    public CheckBox     getCbGrid()             { return cbGrid; }
    public CheckBox     getCbShadows()          { return cbShadows; }
    public CheckBox     getCbPerformance()      { return cbPerformance; }
    public LabGraphCanvas getLabGraph()         { return labGraph; }
    public ToggleGroup  getDrawModeGroup()      { return drawModeGroup; }
    public ToggleButton getBtnFree()            { return btnFree; }
    public ToggleButton getBtnRect()            { return btnRect; }
    public ToggleButton getBtnCircle()          { return btnCircle; }
    public ToggleButton getBtnTriangle()        { return btnTriangle; }
}
