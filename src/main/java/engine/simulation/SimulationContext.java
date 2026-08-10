package engine.simulation;

import engine.math.Vec3;
import engine.physics.PhysicsWorld;
import engine.renderer.Camera;
import engine.scene.SceneObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contexto compartilhado entre o Engine e todos os módulos de simulação.
 *
 * Segue o padrão de projeto "Context Object": encapsula o estado mutável
 * da cena/física de forma que os módulos possam ler e modificar a cena
 * sem acoplamento direto ao Engine ou à UI.
 *
 * O Engine cria e mantém UMA instância de SimulationContext.
 * Os módulos recebem a referência no método onActivate().
 */
public final class SimulationContext {

    // ── Subsistemas principais (referências, não propriedade) ──
    private final PhysicsWorld physics;
    private final Camera       camera;

    // ── Lista canônica de objetos da cena ─────────
    private final List<SceneObject> objects = new ArrayList<>();
    private final List<SceneObject> objectsView = Collections.unmodifiableList(objects);

    // ── Parâmetros globais ajustáveis ─────────────
    private double gravityStrength = 12.0;   // m/s² (modulo)
    private double timeScale       = 1.0;    // multiplicador de velocidade da simulação
    private boolean paused         = false;

    // ── Telemetria do frame ────────────────────────
    private double fps            = 60.0;
    private double simulationTime = 0.0;     // tempo acumulado de simulação (s)

    // ── Observadores (módulos podem ouvir eventos) ─
    private final List<SimulationObserver> observers = new ArrayList<>();

    public SimulationContext(PhysicsWorld physics, Camera camera) {
        this.physics = physics;
        this.camera  = camera;
    }

    // ── Objetos da cena ────────────────────────────

    /** Adiciona objeto à cena e ao mundo de física. */
    public void addObject(SceneObject obj) {
        objects.add(obj);
        physics.addBody(obj.getBody());
        notifyObservers(o -> o.onObjectAdded(obj));
    }

    /** Remove objeto da cena e do mundo de física. */
    public void removeObject(SceneObject obj) {
        objects.remove(obj);
        physics.removeBody(obj.getBody());
        notifyObservers(o -> o.onObjectRemoved(obj));
    }

    /** Remove todos os objetos. Notifica observadores uma única vez. */
    public void clearScene() {
        List<SceneObject> snapshot = new ArrayList<>(objects);
        objects.clear();
        physics.clear();
        snapshot.forEach(obj -> notifyObservers(o -> o.onObjectRemoved(obj)));
        notifyObservers(o -> o.onSceneCleared());
    }

    /** Visão não-modificável da lista de objetos. */
    public List<SceneObject> getObjects() {
        return objectsView;
    }

    // ── Parâmetros globais ─────────────────────────

    public void setGravityStrength(double g) {
        this.gravityStrength = Math.abs(g);
        physics.setGravityStrength(g);
    }

    public void setTimeScale(double scale) {
        this.timeScale = Math.max(0.0, Math.min(5.0, scale));
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void togglePause()        { setPaused(!paused); }

    public double getGravityStrength(){ return gravityStrength; }
    public double getTimeScale()      { return timeScale; }
    public boolean isPaused()         { return paused; }

    // ── Telemetria ─────────────────────────────────

    /** Chamado pelo Engine a cada frame para atualizar contadores. */
    public void tickFrame(double dtReal) {
        if (!paused) simulationTime += dtReal * timeScale;
        fps = fps * 0.9 + (dtReal > 0 ? 0.1 / dtReal : 0);
    }

    public double getFps()            { return fps; }
    public double getSimulationTime() { return simulationTime; }
    public void   resetSimTime()      { simulationTime = 0; }
    public void   advanceSimTime(double dtSimulated) {
        simulationTime += Math.max(0, dtSimulated);
    }

    // ── Subsistemas ────────────────────────────────

    public PhysicsWorld getPhysics() { return physics; }
    public Camera       getCamera()  { return camera;  }

    // ── Observadores ───────────────────────────────

    public void addObserver(SimulationObserver obs)    { observers.add(obs); }
    public void removeObserver(SimulationObserver obs) { observers.remove(obs); }

    @FunctionalInterface
    private interface ObserverAction {
        void call(SimulationObserver o);
    }

    private void notifyObservers(ObserverAction action) {
        observers.forEach(action::call);
    }
}
