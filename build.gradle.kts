plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "br.com.feira"
version = "2.2.0"

val javafxVersion = "21.0.11"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = javafxVersion
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    mainClass.set("engine.Main")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.register<JavaExec>("validatePhysics") {
    group = "verification"
    description = "Executa validacoes numericas das simulacoes fisicas principais."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("engine.validation.PhysicsValidation")
}

// Nao acoplado a `check`/`build`: as validacoes inspecionam textos de telemetria da UI,
// entao uma futura mudanca de texto/idioma nao deve quebrar o build inteiro. Rode sob
// demanda com `./gradlew validatePhysics` (ou adicione ao CI explicitamente se desejar).

// ── Distribuicoes portateis multiplataforma ────────────────────────────────
// O jar do app e independente de plataforma; o que muda entre alvos e apenas o
// classifier das bibliotecas nativas do JavaFX e a pasta de lancadores
// (scripts .bat para Windows, shell/desktop para Linux e macOS).
data class PortableTarget(val id: String, val classifier: String, val launcherDir: String)

val portableTargets = listOf(
    PortableTarget("windows-x64", "win", "src/winDist"),
    PortableTarget("linux-x64", "linux", "src/linuxDist"),
    PortableTarget("linux-aarch64", "linux-aarch64", "src/linuxDist"),
    PortableTarget("mac-x64", "mac", "src/linuxDist"),
    PortableTarget("mac-aarch64", "mac-aarch64", "src/linuxDist")
)

portableTargets.forEach { target ->
    val runtimeConfig = configurations.create("javafxRuntime_${target.id}") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
    dependencies {
        // Notacao artifact-only (@jar): resolve o jar do classifier diretamente, ignorando
        // os metadados de modulo do JavaFX (que trazem variantes por SO e causariam
        // ambiguidade). Como listamos os 4 modulos, nao dependemos de transitividade.
        listOf("base", "controls", "fxml", "graphics").forEach { mod ->
            runtimeConfig("org.openjfx:javafx-$mod:$javafxVersion:${target.classifier}@jar")
        }
    }

    val distDirName = "portable/SimulaFisica3D-${target.id}"
    val distTask = tasks.register<Copy>("portableDist_${target.id}") {
        group = "distribution"
        description = "Pasta portatil (${target.id}) com app, JavaFX e lancadores."
        dependsOn("jar")
        into(layout.buildDirectory.dir(distDirName))
        from(tasks.named("jar")) { into("lib") }
        from(runtimeConfig) { into("lib") }
        from(target.launcherDir)
    }

    tasks.register<Zip>("portableZip_${target.id}") {
        group = "distribution"
        description = "Compacta a versao portatil (${target.id}) do SimulaFisica 3D."
        dependsOn(distTask)
        archiveFileName.set("SimulaFisica3D-${target.id}.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
        from(layout.buildDirectory.dir(distDirName)) {
            eachFile {
                if (name.endsWith(".sh") || name.endsWith(".desktop")) {
                    permissions {
                        unix("rwxr-xr-x")
                    }
                }
            }
        }
    }
}

// Aliases de compatibilidade: os antigos alvos "linuxPortable*" apontam para linux-x64.
tasks.register("linuxPortableDist") {
    group = "distribution"
    description = "Alias para portableDist_linux-x64."
    dependsOn("portableDist_linux-x64")
}
tasks.register("linuxPortableZip") {
    group = "distribution"
    description = "Alias para portableZip_linux-x64."
    dependsOn("portableZip_linux-x64")
}

// Gera todas as distribuicoes portateis de uma vez.
tasks.register("portableZipAll") {
    group = "distribution"
    description = "Gera os zips portateis de todos os alvos (Windows, Linux e macOS)."
    dependsOn(portableTargets.map { "portableZip_${it.id}" })
}

tasks.wrapper {
    gradleVersion = "9.5.0"
    distributionType = Wrapper.DistributionType.BIN
}
