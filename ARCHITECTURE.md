# SimulaFísica 3D: arquitetura do sistema de módulos

## Visão Geral

```
src/main/java/engine/
│
├── Main.java                          ← Entry point
│
├── core/
│   └── Engine.java                    ← Loop principal (AnimationTimer) + JavaFX
│
├── math/
│   ├── Vec3.java                      ← Vetor 3D imutável
│   ├── Mat4.java                      ← Matriz 4×4
│   └── ColorRGBA.java                 ← Cor RGBA + shading
│
├── physics/
│   ├── RigidBody.java                 ← Corpo rígido (Euler semi-implícito)
│   └── PhysicsWorld.java              ← Mundo físico (Fixed Timestep 120Hz)
│
├── renderer/
│   ├── Mesh.java                      ← 8 primitivas geométricas
│   ├── Camera.java                    ← Câmera orbital
│   └── Renderer3D.java                ← Pipeline software (Painter's + Phong)
│
├── scene/
│   ├── SceneObject.java               ← Objeto = Mesh + RigidBody + Material
│   └── ObjectFactory.java             ← Fábrica de objetos com presets
│
├── simulation/                        ← ★ SISTEMA DE MÓDULOS (NOVO)
│   ├── SimulationContext.java         ← Estado compartilhado entre módulos
│   ├── SimulationModule.java          ← Classe base abstrata dos módulos
│   ├── SimulationObserver.java        ← Interface de eventos da cena
│   ├── ModuleManager.java             ← Gerenciador de ciclo de vida
│   ├── ModuleRegistry.java            ← Registro central de módulos
│   ├── ModuleSceneBuilder.java        ← Builder fluente de objetos 3D
│   │
│   ├── modules/                       ← Módulos educacionais
│   │   ├── FreeFallModule.java        ← Queda Livre (MRUV)
│   │   ├── ProjectileModule.java      ← Lançamento Oblíquo
│   │   ├── CollisionModule.java       ← Colisões e Conservação de Momento
│   │   ├── InclinedPlaneModule.java   ← Plano inclinado com atrito
│   │   ├── PendulumModule.java        ← Pêndulo simples não linear
│   │   ├── SpringModule.java          ← Oscilador massa-mola (Hooke)
│   │   ├── SolarSystemModule.java     ← Sistema solar + missões de foguete
│   │   └── TugOfWarModule.java        ← Cabo de guerra interativo
│   │
│   ├── parameters/
│   │   ├── Parameter.java             ← Parâmetro numérico tipado
│   │   └── ParameterSet.java          ← Coleção ordenada de parâmetros
│   │
│   └── challenges/
│       ├── Challenge.java             ← Desafio educacional (base abstrata)
│       └── ChallengeResult.java       ← Resultado imutável (Value Object)
│
└── ui/
    ├── DrawingCanvas.java             ← Canvas 2D de entrada (modo sandbox)
    ├── Toolbar.java                   ← Controles globais (renderer, física)
    └── simulation/
        └── ModulePanel.java           ← Painel de módulos e parâmetros
```

---

## Fluxo de Dados

```
[Usuário] ──seleciona módulo──► [ModulePanel]
                                      │
                                      ▼
                               [ModuleManager]
                               activateById("free_fall")
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                  ▼
             deactivateCurrent   clearScene()      FreeFallModule
                                   (Context)       .onActivate(ctx)
                                                         │
                                                   buildScene()
                                                   context.addObject(...)
                                                         │
                                                         ▼
                                                  [SimulationContext]
                                                  .addObject(obj)
                                                  physics.addBody(...)
                                                         │
                                                         ▼
                                                  [PhysicsWorld]
                                                  .addBody(RigidBody)

[AnimationTimer] ──each frame──►
    physics.update(dt)
    moduleManager.update(dt) ──► FreeFallModule.onUpdate(dt)
    renderer.render(gc, camera, context.getObjects(), w, h)
```

---

## Como Criar um Novo Módulo

### Passo 1. Criar a classe

```java
package engine.simulation.modules;

import engine.simulation.SimulationModule;
import engine.simulation.ModuleSceneBuilder;
import engine.simulation.parameters.Parameter;

public class MeuModulo extends SimulationModule {

    public MeuModulo() {
        super(
            "meu_modulo",              // ID único (snake_case)
            "Meu Módulo",              // Nome exibido na UI
            "Descrição breve.",        // Descrição
            "Mecânica, Dinâmica"      // Tópico curricular
        );
    }

    @Override
    protected void declareParameters() {
        parameters.add(Parameter.of("velocidade", "Velocidade", 10.0)
            .range(0, 30).unit("m/s").step(1.0)
            .tooltip("Velocidade inicial do objeto."));
    }

    @Override
    protected void buildScene() {
        double v = parameters.getValue("velocidade", 10.0);
        context.setGravityStrength(9.81);

        new ModuleSceneBuilder(context)
            .sphere(0.5, ColorRGBA.BLUE)
            .at(0, 5, 0)
            .withVelocity(v, 0, 0)
            .rigid()
            .named("meu_objeto")
            .add();
    }

    @Override
    public void onUpdate(double dt) {
        // Verificar condições a cada frame
    }

    @Override
    public void onParameterChanged(String name, double value) {
        if (name.equals("velocidade")) onReset();
    }
}
```

### Passo 2. Registrar no ModuleRegistry

```java
// Em ModuleRegistry.java, adicionar:
MeuModulo meu = new MeuModulo();
manager.register(meu);
```

### Passo 3. Pronto
A UI detecta automaticamente o novo módulo.

---

## Padrões de Projeto Utilizados

| Padrão          | Onde                          | Por quê                                       |
|-----------------|-------------------------------|-----------------------------------------------|
| Template Method | `SimulationModule`            | Define esqueleto do ciclo de vida (onActivate → buildScene → onUpdate) |
| Context Object  | `SimulationContext`           | Compartilha estado sem acoplamento direto     |
| Observer        | `SimulationObserver`          | Módulos reagem a eventos da cena              |
| Builder         | `ModuleSceneBuilder`          | Criação fluente e legível de objetos 3D       |
| Registry        | `ModuleRegistry`              | Centraliza o registro de módulos              |
| Value Object    | `ChallengeResult`, `Vec3`     | Imutabilidade e segurança em dados de resultado |
| Strategy        | `SimulationModule` (subclasses) | Cada módulo é uma estratégia diferente de simulação |

---

## Roadmap de Evolução

### Curto Prazo
- [ ] Módulo de Energia (cinética, potencial, conservação)
- [ ] Módulo de Oscilações (pêndulo, mola-massa)
- [ ] Módulo de Fluidos Simplificado (empuxo, densidade)
- [ ] Sistema de pontuação persistente (placar de turma)

### Médio Prazo
- [ ] Exportação de gráficos (posição × tempo, velocidade × tempo)
- [ ] Modo multiplayer local (dois alunos na mesma simulação)
- [ ] Versão web (JavaScript + WebGL / Three.js)
- [ ] Versão mobile (Kotlin + Android)

### Longo Prazo
- [ ] Simulação ambiental (reflorestamento, impacto ecológico)
- [ ] Integração com sensores IoT (Arduino + acelerômetro)
- [ ] Realidade Aumentada (câmera + overlay 3D)
- [ ] Dashboard de turma com analytics de aprendizado
- [ ] Módulo de Cidades Inteligentes (tráfego, energia)
