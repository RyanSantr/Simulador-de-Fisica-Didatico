package engine;

import engine.core.Engine;

/**
 * Physics3D Engine — Entry Point
 *
 * Requer Java 17+ com JavaFX no classpath.
 * Compilar e executar:
 *   javac --module-path <javafx-sdk>/lib --add-modules javafx.controls,javafx.fxml -d out $(find src -name "*.java")
 *   java  --module-path <javafx-sdk>/lib --add-modules javafx.controls,javafx.fxml -cp out engine.Main
 *
 * Ou usando o script run.sh incluído.
 */
public class Main {
    public static void main(String[] args) {
        Engine.launch(args);
    }
}
