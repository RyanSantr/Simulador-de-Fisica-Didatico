package engine.scene;

import engine.math.ColorRGBA;
import engine.math.Mat4;
import engine.physics.RigidBody;
import engine.renderer.Mesh;
import engine.renderer.TextureMap;

/**
 * Objeto da cena: une malha 3D, corpo rígido e material visual.
 * É a unidade fundamental do motor.
 */
public class SceneObject {

    private final String     name;
    private final Mesh       mesh;
    private final RigidBody  body;
    private ColorRGBA        color;
    private TextureMap       texture;
    private Mat4             rotationTransform;
    private boolean          visible = true;
    private boolean          selected = false;
    private boolean          shadowCaster = true;
    private int              renderLayer = 0;

    // Metadados
    private final long   createdAt;
    private final String shapeType;

    public SceneObject(String name, Mesh mesh, RigidBody body,
                       ColorRGBA color, String shapeType) {
        this.name      = name;
        this.mesh      = mesh;
        this.body      = body;
        this.color     = color;
        this.shapeType = shapeType;
        this.createdAt = System.currentTimeMillis();
    }

    // ── Getters ────────────────────────────────────
    public String     getName()      { return name; }
    public Mesh       getMesh()      { return mesh; }
    public RigidBody  getBody()      { return body; }
    public ColorRGBA  getColor()     { return color; }
    public TextureMap getTexture()   { return texture; }
    public Mat4       getRotationTransform() { return rotationTransform; }
    public boolean    isVisible()    { return visible; }
    public boolean    isSelected()   { return selected; }
    public boolean    isShadowCaster(){ return shadowCaster; }
    public int        getRenderLayer() { return renderLayer; }
    public String     getShapeType() { return shapeType; }
    public long       getCreatedAt() { return createdAt; }

    public void setColor(ColorRGBA c)    { this.color    = c; }
    public void setTexture(TextureMap t) { this.texture  = t; }
    public void setRotationTransform(Mat4 transform) { this.rotationTransform = transform; }
    public void setVisible(boolean v)    { this.visible  = v; }
    public void setSelected(boolean s)   { this.selected = s; }
    public void setShadowCaster(boolean v) { this.shadowCaster = v; }
    public void setRenderLayer(int layer) { this.renderLayer = layer; }

    @Override
    public String toString() {
        return String.format("[%s | %s | pos=%.1f,%.1f,%.1f]",
            name, shapeType,
            body.getPosition().x, body.getPosition().y, body.getPosition().z);
    }
}
