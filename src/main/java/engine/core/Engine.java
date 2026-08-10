package engine.core;

import engine.math.ColorRGBA;
import engine.math.Vec3;
import engine.physics.PhysicsWorld;
import engine.renderer.Camera;
import engine.renderer.Renderer3D;
import engine.scene.ObjectFactory;
import engine.scene.ScenarioStore;
import engine.scene.SceneObject;
import engine.simulation.ModuleManager;
import engine.simulation.ModuleRegistry;
import engine.simulation.LabMeasurement;
import engine.simulation.SimulationContext;
import engine.simulation.SimulationModule;
import engine.simulation.modules.CollisionModule;
import engine.simulation.modules.ProjectileModule;
import engine.simulation.modules.TugOfWarModule;
import engine.ui.DrawingCanvas;
import engine.ui.Toolbar;
import engine.ui.simulation.ModulePanel;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.stage.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Aplicacao JavaFX e loop principal do simulador.
 *
 * <h2>Composicao</h2>
 * <pre>
 *   Engine
 *     ├── SimulationContext  estado compartilhado (cena, gravidade, tempo)
 *     ├── ModuleManager      ciclo de vida dos 9 modulos de simulacao
 *     ├── PhysicsWorld       integracao a 120 Hz e colisoes
 *     ├── Renderer3D         pipeline 3D por software
 *     ├── Camera             camera orbital/livre
 *     ├── ModulePanel        painel esquerdo: modulos, parametros, telemetria
 *     └── Toolbar            painel direito: render, sandbox, grafico
 * </pre>
 *
 * <h2>Ordem de cada frame</h2>
 * <ol>
 *   <li>Sincroniza as opcoes de render com os controles da Toolbar.</li>
 *   <li>Avanca fisica e modulo ativo — a menos que esteja pausado, caso em que
 *       so avanca se houver passos enfileirados pelo botao "Passo unico".</li>
 *   <li>Atualiza camera livre, grafico do laboratorio e HUD.</li>
 *   <li>Renderiza a cena do modulo ativo ou a do sandbox.</li>
 *   <li>Uma vez por segundo, atualiza FPS, estatisticas e telemetria (texto
 *       longo demais para reconstruir a 60 Hz).</li>
 * </ol>
 *
 * <h2>Dois modos de cena</h2>
 * O simulador alterna entre <b>modulo</b> (cena montada pelo experimento ativo,
 * vive em {@code context}) e <b>sandbox</b> (objetos desenhados pelo usuario,
 * vivem em {@code sandboxObjects}). As duas listas nunca coexistem no
 * PhysicsWorld: trocar de aba limpa uma e recarrega a outra.
 */
public class Engine extends Application {

    private static final double DEFAULT_WINDOW_W = 1280;
    private static final double DEFAULT_WINDOW_H = 820;
    private static final double MIN_WINDOW_W     = 840;
    private static final double MIN_WINDOW_H     = 560;
    private static final double MODULE_W         = 320;
    private static final double TOOLBAR_W        = 260;
    private static final double DRAW_W           = 280;
    private static final double CAMERA_WALK_SPEED = 8.0;
    private static final double CAMERA_FAST_FACTOR = 2.4;

    // Subsistemas
    private final PhysicsWorld physics  = new PhysicsWorld();
    private final Renderer3D   renderer = new Renderer3D();
    private final Camera       camera   = new Camera();

    // Sistema de módulos
    private SimulationContext context;
    private ModuleManager     moduleManager;

    // Objetos em modo sandbox
    private final List<SceneObject> sandboxObjects = new ArrayList<>();
    private boolean sandboxMode = false;

    // UI
    private Toolbar       toolbar;
    private ScrollPane    toolbarPane;
    private DrawingCanvas drawCanvas;
    private ModulePanel   modulePanel;
    private TabPane       modeTabs;
    private BorderPane    root;
    private StackPane     centerBox;
    private Canvas        renderCanvas;
    private GraphicsContext gc;
    private Label         modeLabel;
    private VBox          tugOverlay;
    private VBox          labHud;
    private Label         labHudTitle;
    private Label         labHudValues;
    private ComboBox<String> cbSolidMode;
    private TextField     tugLeftName;
    private TextField     tugRightName;
    private Label         tugTitle;
    private Label         tugLeftStats;
    private Label         tugRightStats;
    private Label         tugCenterStats;
    private Label         tugRankingStats;
    private ProgressBar   tugProgress;
    private VBox          tugRegistrationPane;
    private TabPane       tugGameTabs;
    private Button        tugSwapPlayers;
    private boolean       tugPlayersRegistered = false;

    // Câmera
    private double  mouseLastX, mouseLastY;
    private boolean orbitDragging = false;
    private boolean objectDragging = false;
    private double  objectDragY = 0;
    private SceneObject selectedSandboxObject;
    private final Set<KeyCode> pressedKeys = EnumSet.noneOf(KeyCode.class);

    // FPS
    private long   lastFrameTime = 0;
    private long   lastFpsUpdate = 0;
    private double fps = 60.0;
    private int    frameCount = 0;
    private double tugHudClock = 0;
    private double graphClock = 0;
    private int pendingSimulationSteps = 0;

    // Layout responsivo — ultimas larguras aplicadas (para pular recalculos redundantes)
    private double lastLeftWidth = -1;
    private double lastRightWidth = -1;

    public static void launch(String[] args) {
        Application.launch(Engine.class, args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("SimulaFisica 3D - Laboratorio Universitario");

        // Inicializar subsistemas
        context       = new SimulationContext(physics, camera);
        moduleManager = new ModuleManager(context);

        // Registrar módulos
        String defaultModule = ModuleRegistry.registerAll(moduleManager);

        // Callbacks
        moduleManager.setOnModuleChanged(mod -> {
            if (modulePanel != null) modulePanel.refresh();
            if (modeLabel   != null) modeLabel.setText("Modulo: " + mod.getDisplayName());
            updateModuleLayout(mod);
            if (toolbar != null) toolbar.getLabGraph().reset();
        });
        moduleManager.setOnModuleError(err -> System.err.println("[Engine] " + err));

        // Layout
        root = new BorderPane();
        root.setStyle("-fx-background-color: #0b1020; -fx-font-family: 'Segoe UI', Arial, sans-serif;");

        // Painel de módulos
        modulePanel = new ModulePanel(moduleManager);
        modulePanel.setPrefWidth(MODULE_W);
        modulePanel.setOnResetRequested(() -> moduleManager.resetActive());
        modulePanel.setOnLaunchRequested(this::handleLaunch);
        modulePanel.setOnPauseRequested(this::toggleLabPause);
        modulePanel.setOnStepRequested(this::queueSimulationStep);
        modulePanel.setOnExportRequested(this::handleExportLabCsv);
        modulePanel.setOnTimeScaleChanged(context::setTimeScale);

        // Toolbar global
        toolbar = new Toolbar();
        toolbar.setPrefWidth(TOOLBAR_W);
        toolbarPane = new ScrollPane(toolbar);
        toolbarPane.setFitToWidth(true);
        toolbarPane.setPrefWidth(TOOLBAR_W + 20);
        toolbarPane.setMinWidth(TOOLBAR_W);
        toolbarPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        toolbarPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        toolbarPane.setStyle("-fx-background: #111827; -fx-background-color: #111827;");

        // Canvas de desenho
        drawCanvas = new DrawingCanvas(DRAW_W, DEFAULT_WINDOW_H - 96);
        drawCanvas.setOnDrawComplete(result ->
            javafx.application.Platform.runLater(() -> addObjectFromDraw(result)));

        // Canvas 3D
        renderCanvas = new Canvas(820, DEFAULT_WINDOW_H);
        renderCanvas.setFocusTraversable(true);
        gc = renderCanvas.getGraphicsContext2D();

        centerBox = new StackPane(renderCanvas);
        centerBox.setStyle("-fx-background-color: #050816;");
        // Piso do viewport 3D: com min da janela (840) - painel esq (215) - painel dir (195)
        // sobram ~430px; garantir 360 evita que a area de render fique inutilizavel/achatada.
        centerBox.setMinWidth(360);
        centerBox.setMinHeight(300);
        HBox.setHgrow(renderCanvas, Priority.ALWAYS);
        renderCanvas.widthProperty().bind(centerBox.widthProperty());
        renderCanvas.heightProperty().bind(centerBox.heightProperty());
        tugOverlay = buildTugOverlay();
        tugOverlay.setVisible(false);
        tugOverlay.setManaged(false);
        labHud = buildLabHud();
        centerBox.getChildren().addAll(labHud, tugOverlay);

        modeTabs = buildModeTabs();
        root.setLeft(modeTabs);
        root.setCenter(centerBox);
        root.setRight(toolbarPane);
        root.setBottom(buildStatusBar());

        bindCameraEvents();
        bindToolbarCallbacks();

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double minW = Math.min(MIN_WINDOW_W, screen.getWidth() * 0.90);
        double minH = Math.min(MIN_WINDOW_H, screen.getHeight() * 0.90);
        double initialW = clamp(Math.min(DEFAULT_WINDOW_W, screen.getWidth() * 0.92), minW, screen.getWidth());
        double initialH = clamp(Math.min(DEFAULT_WINDOW_H, screen.getHeight() * 0.88), minH, screen.getHeight());
        Scene scene = new Scene(root, initialW, initialH);
        scene.setFill(Color.web("#06060f"));
        applyStylesheet(scene);
        bindKeyboard(scene);
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);
        scene.widthProperty().addListener((o, ov, nv)  -> onResize());
        scene.heightProperty().addListener((o, ov, nv) -> onResize());
        applyResponsiveLayout(initialW);

        String startupModule = getParameters().getNamed().getOrDefault("module", defaultModule);
        if (!moduleManager.activateById(startupModule)) {
            moduleManager.activateById(defaultModule);
        }
        startGameLoop();
        stage.show();
        stage.centerOnScreen();
    }

    /**
     * Inicia o {@link AnimationTimer} do JavaFX, que dispara uma vez por frame
     * (tipicamente 60 Hz) na thread da interface. Ver o javadoc da classe para
     * a ordem das etapas executadas aqui.
     */
    private void startGameLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameTime == 0) { lastFrameTime = now; lastFpsUpdate = now; return; }

                double dtReal = (now - lastFrameTime) * 1e-9;
                double dt     = Math.min(dtReal * context.getTimeScale(), 0.05);
                lastFrameTime = now;

                renderer.setWireframe(toolbar.isWireframe());
                renderer.setShowGrid(toolbar.isGridVisible());
                renderer.setShowShadows(toolbar.isShadowsEnabled());
                renderer.setShowVectors(toolbar.isVectorsEnabled());
                renderer.setPerformanceMode(toolbar.isPerformanceMode());
                renderer.setGravityVector(physics.getGravityVector());

                if (!sandboxMode && moduleManager.getActiveModule() instanceof TugOfWarModule tug) {
                    if (!tugPlayersRegistered) {
                        tugHudClock += dtReal;
                        if (tugHudClock >= 1.0 / 10.0) {
                            tugHudClock = 0;
                            updateTugRegistrationHud();
                        }
                    } else {
                        if (pressedKeys.contains(KeyCode.A)) tug.holdLeft(dt);
                        if (pressedKeys.contains(KeyCode.L)) tug.holdRight(dt);
                        tugHudClock += dtReal;
                        if (tugHudClock >= 1.0 / 30.0) {
                            tugHudClock = 0;
                            updateTugHud(tug);
                        }
                    }
                } else {
                    tugHudClock = 0;
                }

                boolean stepped = false;
                physics.setGravityEnabled(toolbar.isGravityEnabled());
                if (!context.isPaused()) {
                    physics.update(dt);
                    if (!sandboxMode) moduleManager.update(dt);
                } else if (!sandboxMode && pendingSimulationSteps > 0) {
                    double stepDt = 1.0 / 120.0;
                    physics.update(stepDt);
                    moduleManager.update(stepDt);
                    context.advanceSimTime(stepDt);
                    pendingSimulationSteps--;
                    stepped = true;
                }
                updateFreeCamera(dtReal);
                updateLabGraph(dtReal, stepped);
                context.tickFrame(dtReal);
                updateLabHud();
                modulePanel.updateLabState(context.isPaused(), context.getTimeScale(), context.getSimulationTime());

                // Render
                double w = renderCanvas.getWidth(), h = renderCanvas.getHeight();
                if (w > 0 && h > 0) {
                    camera.setAspect(w / h);
                    List<SceneObject> objs = sandboxMode ? sandboxObjects : context.getObjects();
                    renderer.render(gc, camera, objs, w, h);
                }

                // Bloco de 1 Hz: montar as strings de telemetria e estatisticas
                // a cada frame seria desperdicio — o usuario nao le 60 updates
                // por segundo e o custo de String.format aparece no profiler.
                frameCount++;
                if (now - lastFpsUpdate >= 1_000_000_000L) {
                    fps = frameCount; frameCount = 0; lastFpsUpdate = now;
                    int cnt = sandboxMode ? sandboxObjects.size() : context.getObjects().size();
                    toolbar.updateStats(cnt, fps,
                        physics.getActiveObjects(), physics.getCollisionChecks());
                    if (sandboxMode) {
                        modeLabel.setText("Sandbox: desenhos entram no PhysicsWorld com massa, colisão, atrito e gravidade.");
                    } else if (modulePanel != null && moduleManager.hasActiveModule()) {
                        String telemetry = moduleManager.getActiveModule().telemetry();
                        modulePanel.updateTelemetry(telemetry != null ? telemetry : String.format(
                            "Objetos: %d\nFPS: %.0f\nTempo sim.: %.1fs\nAtivos: %d\nColisões: %d",
                            context.getObjects().size(), fps,
                            context.getSimulationTime(),
                            physics.getActiveObjects(), physics.getCollisionChecks()));
                    }
                }
            }
        }.start();
    }

    // Câmera
    private void bindCameraEvents() {
        renderCanvas.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> renderCanvas.requestFocus());
        renderCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            renderCanvas.requestFocus();
            if (sandboxMode) {
                selectedSandboxObject = pickSandboxObject(e.getX(), e.getY());
                if (selectedSandboxObject != null) {
                    objectDragging = true;
                    objectDragY = selectedSandboxObject.getBody().getPosition().y;
                    selectedSandboxObject.setSelected(true);
                    selectedSandboxObject.getBody().setVelocity(Vec3.ZERO);
                    toolbar.showSelectedObject(selectedSandboxObject);
                    modeLabel.setText("Objeto selecionado: arraste para mover no plano atual.");
                    e.consume();
                    return;
                }
            }
            orbitDragging = true; mouseLastX = e.getX(); mouseLastY = e.getY();
        });
        renderCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (objectDragging && selectedSandboxObject != null) {
                Vec3 moved = screenToPlane(e.getX(), e.getY(), objectDragY);
                if (moved != null) {
                    selectedSandboxObject.getBody().setPosition(moved);
                    selectedSandboxObject.getBody().setVelocity(Vec3.ZERO);
                    selectedSandboxObject.getBody().setAngularVelocity(Vec3.ZERO);
                    toolbar.showSelectedObject(selectedSandboxObject);
                }
                e.consume();
                return;
            }
            if (!orbitDragging) return;
            camera.orbit(-(e.getX()-mouseLastX)*0.007, -(e.getY()-mouseLastY)*0.007);
            mouseLastX = e.getX(); mouseLastY = e.getY();
        });
        renderCanvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            orbitDragging = false;
            objectDragging = false;
        });
        renderCanvas.addEventHandler(ScrollEvent.SCROLL, e -> {
            camera.zoom(-e.getDeltaY() * 0.05); e.consume();
        });
    }

    // Toolbar
    private void bindToolbarCallbacks() {
        toolbar.getBtnFree().setOnAction(    e -> drawCanvas.setMode(DrawingCanvas.DrawMode.FREE));
        toolbar.getBtnRect().setOnAction(    e -> drawCanvas.setMode(DrawingCanvas.DrawMode.RECTANGLE));
        toolbar.getBtnCircle().setOnAction(  e -> drawCanvas.setMode(DrawingCanvas.DrawMode.CIRCLE));
        toolbar.getBtnTriangle().setOnAction(e -> drawCanvas.setMode(DrawingCanvas.DrawMode.TRIANGLE));
        toolbar.getBtnRandom().setOnAction(  e -> handleAddRandom());
        toolbar.getBtnExplode().setOnAction( e -> physics.explodeAt(Vec3.ZERO, 200.0));
        toolbar.getBtnClear().setOnAction(   e -> handleClear());
        toolbar.getBtnReset().setOnAction(   e -> camera.reset());
        toolbar.getBtnApplyObject().setOnAction(e -> handleApplySelectedObject());
        toolbar.getBtnSaveScenario().setOnAction(e -> handleSaveScenario());
        toolbar.getBtnLoadScenario().setOnAction(e -> handleLoadScenario());
    }

    private void bindKeyboard(Scene scene) {
        scene.setOnKeyPressed(e -> {
            if (scene.getFocusOwner() instanceof TextField) return;
            boolean firstPress = pressedKeys.add(e.getCode());
            if (sandboxMode) return;
            if (moduleManager.getActiveModule() instanceof TugOfWarModule tug) {
                if (!tugPlayersRegistered) {
                    if (e.getCode() == KeyCode.ENTER) {
                        startTugMatch();
                        e.consume();
                    }
                    return;
                }
                if (e.getCode() == KeyCode.A && firstPress) {
                    tug.pressLeft();
                    e.consume();
                } else if (e.getCode() == KeyCode.L && firstPress) {
                    tug.pressRight();
                    e.consume();
                } else if (e.getCode() == KeyCode.R) {
                    moduleManager.resetActive();
                    e.consume();
                }
            }
        });
        scene.setOnKeyReleased(e -> pressedKeys.remove(e.getCode()));
    }

    private void updateFreeCamera(double dtReal) {
        if (!sandboxMode && moduleManager.getActiveModule() instanceof TugOfWarModule) return;

        double forward = keyAxis(KeyCode.W, KeyCode.S);
        double right = keyAxis(KeyCode.D, KeyCode.A);
        double up = keyAxis(KeyCode.E, KeyCode.Q);
        double length = Math.sqrt(forward * forward + right * right + up * up);
        if (length < 1e-9) return;

        double radiusSpeed = Math.max(CAMERA_WALK_SPEED, camera.getRadius() * 0.34);
        double speed = radiusSpeed * (pressedKeys.contains(KeyCode.SHIFT) ? CAMERA_FAST_FACTOR : 1.0);
        double step = Math.min(dtReal, 0.05) * speed / length;
        camera.moveLocal(forward * step, right * step, up * step);
    }

    private double keyAxis(KeyCode positive, KeyCode negative) {
        return (pressedKeys.contains(positive) ? 1.0 : 0.0)
            - (pressedKeys.contains(negative) ? 1.0 : 0.0);
    }

    private void updateLabGraph(double dtReal, boolean forcedSample) {
        if (context.isPaused() && !forcedSample) return;
        graphClock += dtReal;
        if ((!forcedSample && graphClock < 0.08) || toolbar == null) return;
        graphClock = 0;
        LabMeasurement measurement = currentLabMeasurement();
        if (measurement != null) {
            toolbar.getLabGraph().addSample(context.getSimulationTime(), measurement);
            return;
        }
        SceneObject probe = graphProbe();
        if (probe == null) return;
        double height = probe.getBody().getPosition().y - physics.getFloorY();
        toolbar.getLabGraph().addSample(
            context.getSimulationTime(),
            height,
            probe.getBody().getVelocity().length(),
            probe.getName()
        );
    }

    private SceneObject graphProbe() {
        List<SceneObject> objects = sandboxMode ? sandboxObjects : context.getObjects();
        if (selectedSandboxObject != null && sandboxMode) return selectedSandboxObject;
        for (SceneObject object : objects) {
            if (object.getBody().getVelocity().lengthSq() > 1e-5) return object;
        }
        return objects.isEmpty() ? null : objects.get(0);
    }

    private LabMeasurement currentLabMeasurement() {
        if (sandboxMode || !moduleManager.hasActiveModule()) return null;
        return moduleManager.getActiveModule().sampleLabMeasurement();
    }

    // Ações
    private void addObjectFromDraw(DrawingCanvas.DrawResult result) {
        enableSandboxMode();
        ColorRGBA color = ColorRGBA.fromJFX(toolbar.getSelectedColor());
        drawCanvas.setStrokeColor(toolbar.getSelectedColor());
        SceneObject obj;
        if (result.hasFreePath()) {
            obj = ObjectFactory.createFromDrawing(result.path(), toolbar.getPhysicsPreset(),
                color, result.normalizedWidth(), result.normalizedHeight(), 7.0,
                selectedSolidMode());
        } else {
            obj = ObjectFactory.create(result.suggestedShape(), toolbar.getPhysicsPreset(),
                color, result.normalizedWidth(), result.normalizedHeight(), 7.0);
        }
        sandboxObjects.add(obj);
        physics.addBody(obj.getBody());
        selectSandboxObject(obj);
        modeLabel.setText(String.format(
            "Criado: %s%s | %d triangulos | massa=%.2f kg | restituição=%.2f | atrito=%.2f",
            obj.getShapeType(),
            result.hasFreePath() ? " (" + selectedSolidMode().label + ")" : "",
            obj.getMesh().getTriangles().size(), obj.getBody().getMass(),
            obj.getBody().getRestitution(), obj.getBody().getFriction()));
    }

    /** Modo de geracao do solido escolhido na aba Desenhar. */
    private ObjectFactory.SolidMode selectedSolidMode() {
        return cbSolidMode == null
            ? ObjectFactory.SolidMode.INFLATE
            : ObjectFactory.SolidMode.byLabel(cbSolidMode.getValue());
    }

    private void handleAddRandom() {
        SceneObject obj = ObjectFactory.createRandom();
        if (sandboxMode) {
            sandboxObjects.add(obj);
            physics.addBody(obj.getBody());
            selectSandboxObject(obj);
        }
        else context.addObject(obj);
    }

    private void handleClear() {
        if (sandboxMode) {
            physics.clear();
            sandboxObjects.clear();
            selectSandboxObject(null);
        }
        else moduleManager.resetActive();
    }

    private void handleApplySelectedObject() {
        if (!sandboxMode || selectedSandboxObject == null) {
            modeLabel.setText("Selecione um objeto do sandbox para editar massa, posicao e velocidade.");
            return;
        }

        try {
            var body = selectedSandboxObject.getBody();
            Vec3 position = new Vec3(
                parseField(toolbar.getPosX()),
                parseField(toolbar.getPosY()),
                parseField(toolbar.getPosZ()));
            Vec3 velocity = new Vec3(
                parseField(toolbar.getVelX()),
                parseField(toolbar.getVelY()),
                parseField(toolbar.getVelZ()));
            double mass = parseField(toolbar.getMass());
            if (!isFinite(position) || !isFinite(velocity) || !Double.isFinite(mass) || mass <= 0) {
                throw new IllegalArgumentException("Valor fisico invalido.");
            }

            body.setPosition(position);
            body.setVelocity(velocity);
            body.setAngularVelocity(Vec3.ZERO);
            body.setMass(mass);
            toolbar.showSelectedObject(selectedSandboxObject);
            modeLabel.setText(String.format(
                "Objeto aplicado: posicao=(%.2f, %.2f, %.2f) m | velocidade=%.2f m/s | massa=%.3f kg",
                position.x, position.y, position.z, velocity.length(), body.getMass()));
            renderCanvas.requestFocus();
        } catch (RuntimeException ex) {
            modeLabel.setText("Campos do objeto invalidos. Use numeros, por exemplo 2.5 ou 2,5.");
        }
    }

    private void handleSaveScenario() {
        if (!sandboxMode || sandboxObjects.isEmpty()) {
            modeLabel.setText("Crie ou carregue objetos no sandbox antes de salvar a cena.");
            return;
        }

        FileChooser chooser = scenarioChooser();
        chooser.setInitialFileName("cenario-fisico.properties");
        var file = chooser.showSaveDialog(renderCanvas.getScene().getWindow());
        if (file == null) return;
        try {
            ScenarioStore.save(file.toPath(), sandboxObjects);
            modeLabel.setText("Cena salva: " + file.getName() + " com " + sandboxObjects.size() + " objetos.");
        } catch (IOException ex) {
            modeLabel.setText("Nao foi possivel salvar a cena: " + ex.getMessage());
        }
        renderCanvas.requestFocus();
    }

    private void handleLoadScenario() {
        FileChooser chooser = scenarioChooser();
        var file = chooser.showOpenDialog(renderCanvas.getScene().getWindow());
        if (file == null) return;
        try {
            List<SceneObject> loaded = ScenarioStore.load(file.toPath());
            enableSandboxMode();
            physics.clear();
            sandboxObjects.clear();
            sandboxObjects.addAll(loaded);
            sandboxObjects.forEach(object -> physics.addBody(object.getBody()));
            selectSandboxObject(loaded.isEmpty() ? null : loaded.get(0));
            toolbar.getLabGraph().reset();
            modeLabel.setText("Cena carregada: " + file.getName() + " com " + loaded.size() + " objetos.");
        } catch (IOException | RuntimeException ex) {
            modeLabel.setText("Nao foi possivel carregar a cena: " + ex.getMessage());
        }
        renderCanvas.requestFocus();
    }

    private FileChooser scenarioChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Cenario do sandbox 3D");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Cenario Physics3D", "*.properties"));
        return chooser;
    }

    private double parseField(TextField field) {
        return Double.parseDouble(field.getText().trim().replace(',', '.'));
    }

    /** Aciona a acao manual do modulo ativo (botao do painel esquerdo). */
    private void handleLaunch() {
        if (moduleManager.hasActiveModule()) {
            moduleManager.getActiveModule().launch();
            if (modulePanel != null) modulePanel.refresh();
        }
        renderCanvas.requestFocus();
    }

    private void toggleLabPause() {
        context.togglePause();
        if (!context.isPaused()) pendingSimulationSteps = 0;
        modulePanel.updateLabState(context.isPaused(), context.getTimeScale(), context.getSimulationTime());
        renderCanvas.requestFocus();
    }

    private void queueSimulationStep() {
        if (!context.isPaused()) context.setPaused(true);
        pendingSimulationSteps = Math.min(24, pendingSimulationSteps + 1);
        modulePanel.updateLabState(true, context.getTimeScale(), context.getSimulationTime());
        renderCanvas.requestFocus();
    }

    private void handleExportLabCsv() {
        if (toolbar.getLabGraph().sampleCount() == 0) {
            modeLabel.setText("Execute a simulacao por alguns instantes antes de exportar o CSV.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar medidas do laboratorio");
        chooser.setInitialFileName("medidas-laboratorio.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        var file = chooser.showSaveDialog(renderCanvas.getScene().getWindow());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), toolbar.getLabGraph().toCsv());
            modeLabel.setText("CSV exportado: " + file.getName() + ".");
        } catch (IOException ex) {
            modeLabel.setText("Nao foi possivel exportar o CSV: " + ex.getMessage());
        }
        renderCanvas.requestFocus();
    }

    private SceneObject pickSandboxObject(double sx, double sy) {
        SceneObject best = null;
        double bestDist = 42.0;
        double w = renderCanvas.getWidth();
        double h = renderCanvas.getHeight();
        for (SceneObject obj : sandboxObjects) {
            double[] screen = camera.projectToScreen(obj.getBody().getPosition(), w, h);
            if (screen == null) continue;
            double dx = screen[0] - sx;
            double dy = screen[1] - sy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            double radiusPx = Math.max(26, 140.0 / Math.max(4.0, obj.getBody().getPosition().distanceTo(camera.getPosition())));
            if (dist < Math.max(bestDist, radiusPx) && dist < bestDist) {
                bestDist = dist;
                best = obj;
            }
        }
        selectSandboxObject(best);
        return best;
    }

    private void selectSandboxObject(SceneObject object) {
        selectedSandboxObject = object;
        for (SceneObject sandboxObject : sandboxObjects) sandboxObject.setSelected(sandboxObject == object);
        if (toolbar != null) toolbar.showSelectedObject(object);
    }

    private Vec3 screenToPlane(double sx, double sy, double planeY) {
        Vec3[] ray = camera.screenToWorldRay(sx, sy, renderCanvas.getWidth(), renderCanvas.getHeight());
        Vec3 origin = ray[0];
        Vec3 dir = ray[1];
        if (Math.abs(dir.y) < 1e-6) return null;
        double t = (planeY - origin.y) / dir.y;
        if (t <= 0) return null;
        Vec3 p = origin.add(dir.mul(t));
        double arena = physics.getArenaHalf() - 0.5;
        return new Vec3(clamp(p.x, -arena, arena), planeY, clamp(p.z, -arena, arena));
    }

    private VBox buildTugOverlay() {
        VBox overlay = new VBox(16);
        overlay.setAlignment(Pos.TOP_CENTER);
        overlay.setPadding(new Insets(26, 36, 20, 36));
        overlay.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(5,8,22,0.92), rgba(5,8,22,0.15));");
        StackPane.setAlignment(overlay, Pos.TOP_CENTER);

        HBox top = new HBox(16);
        top.setAlignment(Pos.CENTER);
        Button back = new Button("Voltar as simulacoes");
        back.setMinHeight(38);
        back.setStyle("-fx-background-color: #1f2937; -fx-text-fill: #e5e7eb; -fx-border-color: #475569; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 14px;");
        back.setOnAction(e -> moduleManager.activateById("free_fall"));

        Button reset = new Button("Resetar partida");
        reset.setMinHeight(38);
        reset.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-border-color: #60a5fa; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 14px;");
        reset.setOnAction(e -> {
            moduleManager.resetActive();
            renderCanvas.requestFocus();
        });

        tugSwapPlayers = new Button("Trocar competidores");
        tugSwapPlayers.setMinHeight(38);
        tugSwapPlayers.setStyle("-fx-background-color: #0f172a; -fx-text-fill: #dbeafe; -fx-border-color: #475569; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 14px;");
        tugSwapPlayers.setOnAction(e -> showTugRegistration());

        tugTitle = new Label("CABO DE GUERRA");
        tugTitle.setFont(Font.font("monospace", FontWeight.BOLD, 30));
        tugTitle.setStyle("-fx-text-fill: #f8fafc;");
        HBox.setHgrow(tugTitle, Priority.ALWAYS);
        top.getChildren().addAll(back, tugTitle, tugSwapPlayers, reset);

        tugLeftName = playerNameField("Jogador A");
        tugRightName = playerNameField("Jogador L");
        tugLeftName.setOnAction(e -> {
            if (tugPlayersRegistered) { updateTugNames(); renderCanvas.requestFocus(); }
            else startTugMatch();
        });
        tugRightName.setOnAction(e -> {
            if (tugPlayersRegistered) { updateTugNames(); renderCanvas.requestFocus(); }
            else startTugMatch();
        });
        tugLeftName.focusedProperty().addListener((o, old, focused) -> { if (!focused && tugPlayersRegistered) updateTugNames(); });
        tugRightName.focusedProperty().addListener((o, old, focused) -> { if (!focused && tugPlayersRegistered) updateTugNames(); });

        tugRegistrationPane = buildTugRegistrationPane();

        HBox score = new HBox(18);
        score.setAlignment(Pos.CENTER);
        tugLeftStats = scoreLabel("#ef4444");
        tugCenterStats = scoreLabel("#eab308");
        tugRightStats = scoreLabel("#3b82f6");
        score.getChildren().addAll(tugLeftStats, tugCenterStats, tugRightStats);
        HBox.setHgrow(tugLeftStats, Priority.ALWAYS);
        HBox.setHgrow(tugCenterStats, Priority.ALWAYS);
        HBox.setHgrow(tugRightStats, Priority.ALWAYS);

        tugRankingStats = new Label();
        tugRankingStats.setMaxWidth(880);
        tugRankingStats.setAlignment(Pos.CENTER);
        tugRankingStats.setFont(Font.font("monospace", FontWeight.BOLD, 14));
        tugRankingStats.setStyle("-fx-text-fill: #dbeafe; -fx-background-color: rgba(2,6,23,0.78); -fx-border-color: #334155; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
        tugRankingStats.setWrapText(true);

        tugProgress = new ProgressBar(0.5);
        tugProgress.setMaxWidth(Double.MAX_VALUE);
        tugProgress.setPrefWidth(760);
        tugProgress.setMinHeight(22);
        tugProgress.setStyle("-fx-accent: #eab308;");

        Label instructions = new Label("A segura/pulsa para esquerda   |   L segura/pulsa para direita   |   R reseta");
        instructions.setFont(Font.font("monospace", FontWeight.BOLD, 16));
        instructions.setStyle("-fx-text-fill: #cbd5e1;");
        instructions.setWrapText(true);
        instructions.setAlignment(Pos.CENTER);

        VBox matchPane = new VBox(12, score, tugProgress, instructions);
        matchPane.setAlignment(Pos.CENTER);
        matchPane.setPadding(new Insets(10, 10, 14, 10));
        matchPane.setMaxWidth(Double.MAX_VALUE);
        matchPane.setStyle("-fx-background-color: rgba(2,6,23,0.45); -fx-border-color: rgba(148,163,184,0.22); -fx-border-radius: 8; -fx-background-radius: 8;");
        VBox.setVgrow(tugProgress, Priority.NEVER);

        VBox rankingPane = new VBox(10, tugRankingStats);
        rankingPane.setAlignment(Pos.CENTER);
        rankingPane.setPadding(new Insets(18));
        rankingPane.setStyle("-fx-background-color: rgba(2,6,23,0.72); -fx-border-color: rgba(148,163,184,0.24); -fx-border-radius: 8; -fx-background-radius: 8;");

        Tab playTab = new Tab("Partida", matchPane);
        playTab.setClosable(false);
        Tab rankingTab = new Tab("Ranking", rankingPane);
        rankingTab.setClosable(false);
        tugGameTabs = new TabPane(playTab, rankingTab);
        tugGameTabs.setMaxWidth(940);
        // Altura maxima adaptavel: encolhe em janelas baixas (evita cortar/sobrepor o HUD
        // quando a altura chega ao novo minimo ~560px) e limita em 310px em telas altas.
        tugGameTabs.maxHeightProperty().bind(
            Bindings.min(310, Bindings.max(180, centerBox.heightProperty().subtract(250))));
        tugGameTabs.prefWidthProperty().bind(overlay.widthProperty().subtract(72));
        tugGameTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tugGameTabs.setStyle("-fx-background-color: transparent; -fx-tab-min-height: 34px; -fx-tab-max-height: 34px;");

        overlay.getChildren().addAll(top, tugRegistrationPane, tugGameTabs);
        return overlay;
    }

    private VBox buildTugRegistrationPane() {
        Label title = new Label("Registro dos competidores");
        title.setFont(Font.font("monospace", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #e0f2fe;");

        Label hint = new Label("Preencha os nomes antes de iniciar. A disputa libera as teclas A e L depois do registro.");
        hint.setWrapText(true);
        hint.setMaxWidth(620);
        hint.setAlignment(Pos.CENTER);
        hint.setFont(Font.font("monospace", 14));
        hint.setStyle("-fx-text-fill: #cbd5e1;");

        VBox leftBox = new VBox(6, registrationLabel("Competidor da tecla A"), tugLeftName);
        VBox rightBox = new VBox(6, registrationLabel("Competidor da tecla L"), tugRightName);
        HBox names = new HBox(18, leftBox, rightBox);
        names.setAlignment(Pos.CENTER);

        Button start = new Button("Iniciar partida");
        start.setMinHeight(44);
        start.setMaxWidth(360);
        start.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-border-color: #86efac; -fx-border-radius: 7; -fx-background-radius: 7; -fx-font-family: monospace; -fx-font-size: 16px; -fx-font-weight: bold;");
        start.setOnAction(e -> startTugMatch());

        VBox pane = new VBox(14, title, hint, names, start);
        pane.setAlignment(Pos.CENTER);
        pane.setMaxWidth(820);
        pane.setPadding(new Insets(22, 28, 24, 28));
        pane.setStyle("-fx-background-color: rgba(2,6,23,0.82); -fx-border-color: rgba(125,211,252,0.28); -fx-border-radius: 10; -fx-background-radius: 10;");
        return pane;
    }

    private Label registrationLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("monospace", FontWeight.BOLD, 13));
        label.setStyle("-fx-text-fill: #93c5fd;");
        return label;
    }

    private VBox buildLabHud() {
        VBox hud = new VBox(3);
        hud.setMaxWidth(300);
        hud.setMouseTransparent(true);
        hud.setPadding(new Insets(7, 9, 7, 9));
        hud.setStyle("-fx-background-color: rgba(2,6,23,0.58); -fx-border-color: rgba(125,211,252,0.22); " +
            "-fx-border-radius: 6; -fx-background-radius: 6;");
        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setMargin(hud, new Insets(10));

        labHudTitle = new Label("LAB");
        labHudTitle.setFont(Font.font("monospace", FontWeight.BOLD, 11));
        labHudTitle.setStyle("-fx-text-fill: #93c5fd;");

        labHudValues = new Label();
        labHudValues.setWrapText(true);
        labHudValues.setFont(Font.font("monospace", 11));
        labHudValues.setStyle("-fx-text-fill: #e0f2fe; -fx-line-spacing: 1px;");
        hud.getChildren().addAll(labHudTitle, labHudValues);
        return hud;
    }

    private void updateLabHud() {
        if (labHud == null || moduleManager == null) return;
        SimulationModule mod = moduleManager.getActiveModule();
        String name = sandboxMode ? "Sandbox 3D" : mod == null ? "Sem modulo" : mod.getDisplayName();
        LabMeasurement measurement = currentLabMeasurement();
        String reading = measurement == null
            ? genericHudReading()
            : measurement.hudLine();
        labHudTitle.setText(name.toUpperCase());
        labHudValues.setText(String.format(
            "t=%.2fs | %.2fx | %s%nG:%s | obj:%d%n%s",
            context.getSimulationTime(), context.getTimeScale(),
            context.isPaused() ? "pausado" : "rodando",
            toolbar.isGravityEnabled() ? "on" : "off",
            sandboxMode ? sandboxObjects.size() : context.getObjects().size(),
            reading
        ));
    }

    private String genericHudReading() {
        SceneObject probe = graphProbe();
        if (probe == null) return "sem amostra";
        return String.format("%s | h=%.2fm | v=%.2fm/s",
            probe.getName(),
            probe.getBody().getPosition().y - physics.getFloorY(),
            probe.getBody().getVelocity().length());
    }

    private TextField playerNameField(String text) {
        TextField field = new TextField(text);
        field.setMaxWidth(260);
        field.setMinHeight(40);
        field.setAlignment(Pos.CENTER);
        field.setFont(Font.font("monospace", FontWeight.BOLD, 16));
        field.setStyle("-fx-background-color: #0f172a; -fx-text-fill: #f8fafc; -fx-border-color: #475569; -fx-border-radius: 6; -fx-background-radius: 6;");
        return field;
    }

    private Label scoreLabel(String accent) {
        Label label = new Label();
        label.setMinWidth(180);
        label.setPrefWidth(260);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.setFont(Font.font("monospace", FontWeight.BOLD, 16));
        label.setStyle("-fx-text-fill: #f8fafc; -fx-background-color: rgba(15,23,42,0.88); -fx-border-color: " + accent + "; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14;");
        return label;
    }

    private void updateModuleLayout(SimulationModule mod) {
        if (root == null || tugOverlay == null) return;
        boolean tugMode = mod instanceof TugOfWarModule;
        tugHudClock = 0;
        tugOverlay.setVisible(tugMode);
        tugOverlay.setManaged(tugMode);
        labHud.setVisible(!tugMode);
        labHud.setManaged(!tugMode);
        root.setLeft(tugMode ? null : modeTabs);
        root.setRight(tugMode ? null : toolbarPane);
        if (tugMode) {
            renderer.useTugOfWarLighting();
            camera.setOrbitView(0.0, 1.34, 13.5, new Vec3(0, -3.78, 0));
            showTugRegistration();
            modeLabel.setText("Cabo de Guerra: registre os competidores antes de iniciar.");
        } else {
            renderer.useDefaultLighting();
        }
    }

    private void showTugRegistration() {
        tugPlayersRegistered = false;
        if (tugRegistrationPane != null) {
            tugRegistrationPane.setVisible(true);
            tugRegistrationPane.setManaged(true);
        }
        if (tugGameTabs != null) {
            tugGameTabs.setVisible(false);
            tugGameTabs.setManaged(false);
        }
        if (tugSwapPlayers != null) {
            tugSwapPlayers.setDisable(true);
        }
        updateTugRegistrationHud();
        javafx.application.Platform.runLater(() -> {
            if (tugLeftName != null) {
                tugLeftName.requestFocus();
                tugLeftName.selectAll();
            }
        });
    }

    private void startTugMatch() {
        updateTugNames();
        tugPlayersRegistered = true;
        if (tugRegistrationPane != null) {
            tugRegistrationPane.setVisible(false);
            tugRegistrationPane.setManaged(false);
        }
        if (tugGameTabs != null) {
            tugGameTabs.setVisible(true);
            tugGameTabs.setManaged(true);
            tugGameTabs.getSelectionModel().selectFirst();
        }
        if (tugSwapPlayers != null) {
            tugSwapPlayers.setDisable(false);
        }
        moduleManager.resetActive();
        if (moduleManager.getActiveModule() instanceof TugOfWarModule tug) {
            updateTugHud(tug);
            modeLabel.setText("Cabo de Guerra: " + tug.getLeftName() + " (A) vs " + tug.getRightName() + " (L).");
        }
        renderCanvas.requestFocus();
    }

    private void updateTugRegistrationHud() {
        if (tugTitle != null) tugTitle.setText("REGISTRO DOS COMPETIDORES");
        if (tugLeftStats != null) tugLeftStats.setText("Tecla A\naguardando nome");
        if (tugCenterStats != null) tugCenterStats.setText("Preencha os nomes\nEnter ou Iniciar partida");
        if (tugRightStats != null) tugRightStats.setText("Tecla L\naguardando nome");
        if (tugProgress != null) tugProgress.setProgress(0.5);
    }

    private void updateTugNames() {
        if (moduleManager.getActiveModule() instanceof TugOfWarModule tug) {
            tug.setLeftName(tugLeftName.getText());
            tug.setRightName(tugRightName.getText());
        }
    }

    private void updateTugHud(TugOfWarModule tug) {
        if (tugOverlay == null || !tugOverlay.isVisible()) return;
        double progress = (tug.getRopePosition() / tug.getWinLimit() + 1.0) * 0.5;
        tugProgress.setProgress(clamp(progress, 0, 1));
        String winner = tug.getWinner().isBlank() ? "DISPUTA EM ANDAMENTO" : tug.getWinner();
        tugTitle.setText(winner);
        tugLeftStats.setText(String.format("%s%nTecla A%nCliques: %d%nCPS: %.1f%nRecord partida: %.1f%nRecord geral: %.1f%nForca: %.1f N",
            tug.getLeftName(), tug.getLeftTaps(), tug.getLeftCps(), tug.getLeftBestCps(), tug.getLeftGlobalBestCps(), tug.getLeftForce()));
        tugRightStats.setText(String.format("%s%nTecla L%nCliques: %d%nCPS: %.1f%nRecord partida: %.1f%nRecord geral: %.1f%nForca: %.1f N",
            tug.getRightName(), tug.getRightTaps(), tug.getRightCps(), tug.getRightBestCps(), tug.getRightGlobalBestCps(), tug.getRightForce()));
        tugCenterStats.setText(String.format("Corda%nPosicao: %.2f m%nCentro: 0.00%nVitoria: %.1f m%n%n%s",
            tug.getRopePosition(), tug.getWinLimit(), tug.getCurrentTapLeader()));
        tugRankingStats.setText(buildTugRankingText(tug));
    }

    private String buildTugRankingText(TugOfWarModule tug) {
        StringBuilder text = new StringBuilder("Ranking de cliques: ");
        var ranking = tug.getRanking();
        if (ranking.isEmpty()) {
            text.append("termine uma partida para registrar o top.");
            text.append(String.format("  |  Agora: %s", tug.getCurrentTapLeader()));
            return text.toString();
        }
        for (int i = 0; i < ranking.size(); i++) {
            var entry = ranking.get(i);
            if (i > 0) text.append("  |  ");
            text.append(i + 1)
                .append(". ")
                .append(entry.playerName())
                .append(" ")
                .append(entry.taps())
                .append(" cliques")
                .append(" / ")
                .append(String.format("%.1f CPS", entry.bestCps()));
        }
        text.append("  |  Agora: ").append(tug.getCurrentTapLeader());
        return text.toString();
    }

    // Layout
    private HBox buildStatusBar() {
        modeLabel = new Label("Inicializando laboratorio...");
        modeLabel.setFont(Font.font("monospace", 13));
        modeLabel.setStyle("-fx-text-fill: #7dd3fc;");
        Label hint = new Label(
            "  |  Mouse = orbitar  |  WASD = andar  |  Q/E = altura  |  Shift = rapido  |  Scroll = zoom  |  " +
            "Painel esquerdo: experimento, parametros, presets e medidas");
        hint.setFont(Font.font("monospace", 13));
        hint.setStyle("-fx-text-fill: #cbd5e1;");
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);
        HBox bar = new HBox(modeLabel, hint);
        HBox.setHgrow(hint, Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 14, 8, 14));
        bar.setStyle("-fx-background-color: #111827; -fx-border-color: #334155; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    /**
     * Carrega o tema escuro da interface. Estilos embutidos no codigo tem
     * prioridade sobre a folha, entao ela cuida do que nao da para resolver
     * inline: barras de rolagem, secoes retrateis e o painel de resultados.
     */
    private void applyStylesheet(Scene scene) {
        var stylesheet = getClass().getResource("/br/com/feira/fisica/styles.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        } else {
            System.err.println("[Engine] styles.css nao encontrado; usando apenas estilos embutidos.");
        }
    }

    private void onResize() {
        if (renderCanvas != null)
            camera.setAspect(Math.max(1, renderCanvas.getWidth())
                           / Math.max(1, renderCanvas.getHeight()));
        if (root != null) applyResponsiveLayout(root.getWidth());
    }

    private void applyResponsiveLayout(double windowWidth) {
        if (windowWidth <= 0) return;

        // Larguras proporcionais com piso na largura minima de cada painel (fonte unica
        // de verdade nas constantes MIN_WIDTH). O clamp ja garante o piso em janelas
        // estreitas, sem o salto abrupto que existia no antigo branch "if < 980".
        double leftWidth  = clamp(windowWidth * 0.24, ModulePanel.MIN_WIDTH, 350);
        double rightWidth = clamp(windowWidth * 0.20, Toolbar.MIN_WIDTH, 290);

        // O resize dispara para cada pixel (largura e altura); pula o trabalho de layout
        // quando as larguras calculadas nao mudaram, evitando redraws redundantes.
        if (Math.abs(leftWidth - lastLeftWidth) < 0.5 && Math.abs(rightWidth - lastRightWidth) < 0.5) return;
        lastLeftWidth = leftWidth;
        lastRightWidth = rightWidth;

        if (modulePanel != null) modulePanel.setPanelWidth(leftWidth);
        if (modeTabs != null) {
            modeTabs.setPrefWidth(leftWidth);
            modeTabs.setMinWidth(leftWidth);
            modeTabs.setMaxWidth(leftWidth);
        }
        if (toolbar != null) toolbar.setPanelWidth(rightWidth);
        if (toolbarPane != null) {
            toolbarPane.setPrefWidth(rightWidth + 16);
            toolbarPane.setMinWidth(rightWidth);
            toolbarPane.setMaxWidth(rightWidth + 24);
        }
    }

    private TabPane buildModeTabs() {
        ScrollPane modulesPage = new ScrollPane(modulePanel);
        modulesPage.setFitToWidth(true);
        modulesPage.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        modulesPage.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        modulesPage.setStyle("-fx-background: #111827; -fx-background-color: #111827;");

        Tab modules = new Tab("Simulacoes", modulesPage);
        modules.setClosable(false);

        VBox drawTabContent = new VBox(8);
        drawTabContent.setPadding(new Insets(14));
        drawTabContent.setStyle("-fx-background-color: #111827;");

        Label title = new Label("Bancada de desenho 3D");
        title.setFont(Font.font("monospace", 18));
        title.setStyle("-fx-text-fill: #e0f2fe; -fx-font-weight: bold;");

        Label hint = new Label("Use o modo Livre para criar uma forma composta. O objeto vira uma malha 3D dentro do sandbox fisico.");
        hint.setWrapText(true);
        hint.setFont(Font.font("monospace", 13));
        hint.setStyle("-fx-text-fill: #cbd5e1;");

        // Como o traco vira geometria: recorte plano, volume inflado ou torno.
        Label solidLabel = new Label("Geracao do solido");
        solidLabel.setFont(Font.font("monospace", 12));
        solidLabel.setStyle("-fx-text-fill: #93c5fd;");

        cbSolidMode = new ComboBox<>();
        cbSolidMode.getItems().addAll(ObjectFactory.SolidMode.labels());
        cbSolidMode.setValue(ObjectFactory.SolidMode.INFLATE.label);
        cbSolidMode.setMaxWidth(Double.MAX_VALUE);
        cbSolidMode.setMinHeight(38);
        cbSolidMode.setStyle("-fx-background-color: #1f2937; -fx-border-color: #475569; " +
            "-fx-font-family: monospace; -fx-font-size: 13px;");

        Label solidHint = new Label(ObjectFactory.SolidMode.INFLATE.description);
        solidHint.setWrapText(true);
        solidHint.setFont(Font.font("monospace", 11));
        solidHint.setStyle("-fx-text-fill: #94a3b8;");
        cbSolidMode.setOnAction(e ->
            solidHint.setText(selectedSolidMode().description));

        Button createDrawing = new Button("Criar 3D");
        createDrawing.setMaxWidth(Double.MAX_VALUE);
        createDrawing.setMinHeight(38);
        createDrawing.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-border-color: #60a5fa; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-family: monospace; -fx-font-size: 14px;");
        createDrawing.setOnAction(e -> drawCanvas.createFreeDrawingObject());

        Button clearDrawing = new Button("Limpar desenho");
        clearDrawing.setMaxWidth(Double.MAX_VALUE);
        clearDrawing.setMinHeight(38);
        clearDrawing.setStyle("-fx-background-color: #1f2937; -fx-text-fill: #e5e7eb; -fx-border-color: #475569; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-family: monospace; -fx-font-size: 14px;");
        clearDrawing.setOnAction(e -> drawCanvas.clearFreeDrawing());

        Button undoStroke = new Button("Desfazer traco");
        undoStroke.setMaxWidth(Double.MAX_VALUE);
        undoStroke.setMinHeight(38);
        undoStroke.setStyle("-fx-background-color: #1f2937; -fx-text-fill: #e5e7eb; -fx-border-color: #475569; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-family: monospace; -fx-font-size: 14px;");
        undoStroke.setOnAction(e -> drawCanvas.undoLastFreeStroke());

        HBox drawActions = new HBox(8, createDrawing, undoStroke);
        HBox.setHgrow(createDrawing, Priority.ALWAYS);
        HBox.setHgrow(undoStroke, Priority.ALWAYS);
        clearDrawing.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(clearDrawing, Priority.ALWAYS);

        // O canvas fica dentro de um suporte com tamanho preferido FIXO.
        //
        // Amarrar o canvas direto na altura da VBox criava um ciclo de layout: a
        // altura da VBox e calculada a partir dos filhos, e o canvas era um deles
        // — entao redimensionar o canvas mudava a VBox, que redimensionava o
        // canvas de novo, indefinidamente. O sintoma era o canvas mudando de
        // tamanho a cada quadro (60 vezes por segundo), com o JavaFX lancando
        // NullPointerException no buffer de render a cada tentativa e inundando
        // o terminal, ate a interface parar de responder.
        //
        // Com o preferido fixo, o suporte nao consulta o canvas para se
        // dimensionar: ele recebe o espaco que sobra via VGROW e o canvas segue o
        // tamanho ja resolvido do suporte. O ciclo deixa de existir.
        Pane canvasHolder = new Pane(drawCanvas) {
            @Override protected double computePrefWidth(double height) { return 150; }
            @Override protected double computePrefHeight(double width) { return 170; }
            @Override protected double computeMinWidth(double height) { return 120; }
            @Override protected double computeMinHeight(double width) { return 140; }
        };
        drawCanvas.widthProperty().bind(canvasHolder.widthProperty());
        drawCanvas.heightProperty().bind(canvasHolder.heightProperty());

        drawTabContent.getChildren().addAll(title, hint,
            solidLabel, cbSolidMode, solidHint,
            drawActions, clearDrawing, canvasHolder);
        VBox.setVgrow(canvasHolder, Priority.ALWAYS);

        Tab draw = new Tab("Desenhar", drawTabContent);
        draw.setClosable(false);

        TabPane tabs = new TabPane(modules, draw);
        tabs.setPrefWidth(MODULE_W);
        tabs.setMinWidth(MODULE_W);
        tabs.setStyle("-fx-background-color: #111827; -fx-tab-min-height: 46px; -fx-tab-max-height: 46px; -fx-font-size: 14px;");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == draw) {
                enableSandboxMode();
            } else {
                disableSandboxMode();
            }
        });
        return tabs;
    }

    private void enableSandboxMode() {
        if (sandboxMode) return;
        sandboxMode = true;
        toolbar.getLabGraph().reset();
        context.clearScene();
        physics.clear();
        sandboxObjects.forEach(obj -> physics.addBody(obj.getBody()));
        modeLabel.setText("Sandbox: desenhe objetos; eles serão criados dentro da engine.");
    }

    private void disableSandboxMode() {
        if (!sandboxMode) return;
        sandboxMode = false;
        toolbar.getLabGraph().reset();
        objectDragging = false;
        selectSandboxObject(null);
        physics.clear();
        moduleManager.resetActive();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
