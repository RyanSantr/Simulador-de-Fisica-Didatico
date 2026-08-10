<div align="center">

# 🪐 SimulaFísica 3D

**Um laboratório de física que você opera, com motor 3D escrito do zero em Java.**

Oito experimentos interativos, de largar uma bola a mandar um foguete para Marte,
rodando sobre uma engine de física e um renderizador 3D construídos à mão.
Sem OpenGL, sem Unity, sem biblioteca gráfica de terceiros.

![versão](https://img.shields.io/badge/versão-2.2.0-blue)
![java](https://img.shields.io/badge/Java-21-orange)
![javafx](https://img.shields.io/badge/JavaFX-21-informational)
![licença](https://img.shields.io/badge/licença-MIT-green)

</div>

---

## Índice

**Começando**
[O que é](#o-que-é) ·
[Como abrir](#como-abrir) ·
[Controles](#controles)

**Usando**
[Os oito experimentos](#os-oito-experimentos) ·
[Missões de foguete](#-missões-de-foguete) ·
[Desenhar em 3D](#-desenhar-em-3d) ·
[Modo laboratório](#modo-laboratório)

**Por dentro**
[Como o motor funciona](#como-o-motor-funciona) ·
[A física é conferível](#a-física-é-conferível) ·
[Estrutura do projeto](#estrutura-do-projeto) ·
[Documentação](#documentação)

---

## O que é

Um simulador feito para mostrar física acontecendo de verdade, com os cálculos sendo resolvidos na
hora em vez de animações prontas.

Os parâmetros que aparecem no livro-texto (gravidade, massa, ângulo, material, meio) estão em
sliders na tela. Você muda um valor e vê o resultado imediatamente na cena 3D, na tabela de
resultados e no gráfico.

|  |  |
|---|---|
| 🔧 **Motor próprio** | O renderizador 3D é feito à mão. Projeção, sombreamento, sombras e ordenação por profundidade, tudo desenhado num Canvas 2D. Roda em qualquer máquina com Java, sem drivers nem placa dedicada. |
| ⚛️ **Física de verdade** | Passo fixo de 120 Hz e integração de Euler semi-implícito. O mesmo experimento dá o mesmo resultado em qualquer computador, independente do FPS. |
| ✅ **Verificável** | Uma bateria de testes compara cada módulo com a solução analítica. Se a física quebrar, o build acusa. |

---

## Como abrir

### Opção 1: baixar e usar (recomendado)

Pegue o zip da sua plataforma, extraia e execute. Não precisa instalar nada além do Java 21.

| Sistema | Arquivo | O que executar |
|---|---|---|
| 🪟 Windows | `SimulaFisica3D-windows-x64.zip` | `SimulaFisica3D.bat` |
| 🐧 Linux | `SimulaFisica3D-linux-x64.zip` | `SimulaFisica3D.sh` |

> Se o computador não tiver Java, coloque um JDK 21 portátil dentro da pasta com o nome `runtime`.
> O lançador detecta e usa sozinho. Funciona até em máquinas sem permissão de instalação, como as
> de laboratório escolar.

### Opção 2: rodar do código

```bash
# Windows
.\gradlew.bat run

# Linux / macOS
./gradlew run
```

Para abrir direto num experimento:

```bash
./gradlew run --args="--module=solar_system"
```

IDs disponíveis: `free_fall`, `projectile`, `collisions`, `inclined_plane`, `pendulum`, `spring`,
`solar_system`, `tug_of_war`

### Opção 3: gerar os zips você mesmo

```bash
./gradlew portableZipAll
```

Sai em `build/distributions/`, com alvos para Windows, Linux e macOS (x64 e ARM64). Cada pacote traz
o jar, as bibliotecas JavaFX daquela plataforma e o lançador.

---

## Controles

### Câmera

| Ação | Efeito |
|---|---|
| Arrastar o mouse | Orbitar ao redor |
| Scroll | Zoom |
| `W` `A` `S` `D` | Andar pela cena |
| `Q` / `E` | Descer / subir |
| `Shift` | Movimento rápido |

### Cabo de Guerra

| Tecla | Ação |
|---|---|
| `A` | Puxar para a esquerda |
| `L` | Puxar para a direita |
| `R` | Reiniciar |

---

## Os oito experimentos

| Experimento | O que ensina |
|---|---|
| **Queda Livre** | MRUV: tempo de queda, velocidade, o efeito da gravidade |
| **Projéteis** | Lançamento oblíquo com arrasto real do ar, água, óleo ou gel |
| **Colisões** | Momento e energia, com sete materiais que se comportam como os de verdade |
| **Plano Inclinado** | A disputa entre o peso na rampa e o atrito |
| **Pêndulo** | Oscilação não linear, sem a aproximação de ângulo pequeno |
| **Mola** | Lei de Hooke num oscilador massa-mola |
| **Sistema Solar** | Órbitas reais de Mercúrio a Netuno, mais as missões de foguete |
| **Cabo de Guerra** | Jogo de força para dois no mesmo teclado |

Todo experimento espera o seu comando. Ajuste os parâmetros primeiro e clique no botão de ação
("Soltar o objeto", "Iniciar colisão", "Enviar para Marte", conforme o caso) para ver acontecer.

### Alguns em detalhe

**Queda Livre.** Ajuste a gravidade da Lua a Júpiter e compare o tempo medido com o teórico
√(2h/g). Desligando a gravidade o corpo não cai, o que deixa claro que a queda é efeito da força e
não uma propriedade do objeto.

**Projéteis.** No vácuo o alcance bate exatamente com v²·sen(2θ)/g. Na água não chega nem perto, e é
esse contraste que ensina. Cada meio tem viscosidade e densidade reais, então o arrasto muda de
regime conforme a velocidade.

**Colisões.** Escolha dois materiais e uma velocidade. A telemetria mostra energia antes e depois e
o coeficiente de restituição medido, que nunca passa de 1, porque energia não aparece do nada. Aço
rápido contra vidro quebra o alvo.

**Sistema Solar.** Mercúrio a Netuno em órbitas calculadas a partir dos elementos keplerianos J2000
da NASA/JPL, com texturas planetárias, inclinação axial real, anéis de Saturno e Urano e as luas
maiores dos gigantes gasosos. Aplique perturbações como Sol mais massivo, acelerar ou frear, e veja
a órbita responder.

---

## 🚀 Missões de foguete

Ficam dentro do Sistema Solar. Escolha o foguete, o planeta de partida e o de destino, e clique em
**Enviar para ⟨planeta⟩**. Nada acontece antes disso, então dá para observar as órbitas à vontade
primeiro.

Ao receber o comando, o simulador conduz a viagem inteira:

1. calcula a transferência de Hohmann e o ângulo de fase que abre a janela de lançamento;
2. verifica se o delta-v daquele foguete dá conta da manobra;
3. espera o alinhamento correto entre os dois planetas;
4. aplica o impulso e mira no ponto onde o destino estará na chegada;
5. desenha a rota prevista e acompanha a nave, com rastro e chama do motor, até o encontro.

### Seis lançadores reais

| Foguete | Δv | Carga útil | Alcança |
|---|---|---|---|
| Ariane 5 ECA | 3,1 km/s | 10 t | Vênus, Marte |
| Saturn V | 4,0 km/s | 45 t | Vênus, Marte |
| Falcon Heavy | 4,8 km/s | 16 t | Vênus, Marte |
| SLS Block 1B | 5,6 km/s | 27 t | Vênus, Marte |
| Starship (reabastecida) | 7,2 km/s | 100 t | quase Mercúrio |
| Atlas V 551 + Star 48 | 9,2 km/s | 8 t | até Júpiter |

Se faltar delta-v a missão não sai do planeta, e a telemetria diz quanto falta. Trocar o modelo até
a viagem acontecer é a lição central do módulo.

### Rotas verificadas

| Rota | Δv exigido | Tempo de voo |
|---|---|---|
| Terra → Vênus | 2,50 km/s | 117 dias |
| Terra → Marte | 2,94 km/s | 231 dias |
| Marte → Terra | 2,65 km/s | 231 dias |
| Terra → Júpiter | 8,79 km/s | 903 dias |
| Terra → Saturno | 10,29 km/s | fora do alcance de todos |

> Nenhum foguete chega a Saturno direto, e isso está certo. A Cassini precisou de quatro
> assistências gravitacionais. Mercúrio, apesar de perto, exige 7,5 km/s porque é preciso frear
> muito contra a gravidade solar.

---

## ✏️ Desenhar em 3D

Na aba **Desenhar**, o que você rabisca vira um corpo rígido de verdade dentro da física, com massa
calculada por densidade × volume, atrito e colisão.

| Modo de desenho | Vira |
|---|---|
| Retângulo | Cubo |
| Círculo | Esfera |
| Triângulo | Cone |
| Livre | Sólido 3D, do jeito que você escolher |

No modo Livre você decide como o traço vira volume:

| Modo | O que faz | Bom para |
|---|---|---|
| **Extrudar** | Espessura constante, como recorte de chapa | placas, letreiros |
| **Inflar** | O contorno incha, grosso no centro e afinando na borda | bichinhos, nuvens, formas orgânicas |
| **Revolução** | O traço vira perfil de torno e gira 360° | vasos, taças, peças de xadrez |

Um círculo desenhado no modo Inflar vira uma esfera achatada, e uma estrela vira uma estrela gorda.
No modo Revolução, um perfil lateral vira um vaso completo.

Objetos criados podem ser arrastados, editados numericamente (massa, posição, velocidade) e salvos
em arquivo junto com a cena.

📄 Algoritmos e fórmulas em [`docs/DESENHO_3D.md`](docs/DESENHO_3D.md)

---

## Modo laboratório

Disponível em todos os experimentos:

| Recurso | Para que serve |
|---|---|
| **Presets** | Configurações repetíveis, para comparar dois cenários sem ajustar slider por slider |
| **Pausar / Passo único** | Avança exatamente 1/120 s, para examinar o instante do impacto quadro a quadro |
| **Escala de tempo** | De 0,25× a 4× |
| **Valores livres** | O slider percorre a faixa usual, mas o campo aceita qualquer valor fisicamente válido. Digite a gravidade do Sol e o slider se estende |
| **Resultados** | Painel com os dados em linhas rótulo e valor, agrupadas por seção |
| **Gráfico e CSV** | Duas grandezas por experimento, exportáveis para planilha |
| **Modo leve** | Reduz sombras e triângulos para ganhar FPS em máquinas modestas |

---

## Como o motor funciona

### Física

**Passo fixo de 120 Hz.** O tempo real entre quadros varia. Se a integração usasse esse dt variável,
o mesmo experimento daria resultados diferentes em cada máquina. Um acumulador consome o tempo real
em fatias iguais, com teto de 8 sub-passos por quadro para evitar a espiral da morte.

**Euler semi-implícito.** A velocidade é atualizada antes da posição, e essa ordem é o que mantém a
energia estável em órbitas e osciladores. Com Euler explícito um pêndulo abriria sozinho.

**Colisões.** Fase ampla por esfera envolvente, resposta por impulso com restituição do par de
materiais e atrito de Coulomb no contato.

### Renderização

Pipeline completo por software, a cada quadro:

1. **Descarte.** Objetos fora da tela ou menores que ~1 pixel são pulados.
2. **Transformação.** Matriz modelo, depois view-projection, depois coordenadas de tela.
3. **Iluminação.** Shading plano com duas luzes direcionais, ambiente e especular Blinn-Phong.
4. **Ordenação.** Sem z-buffer, usando o algoritmo do pintor, do mais distante ao mais próximo.
5. **Sombras.** Projeção da geometria no plano do chão.

Cenas como o sistema solar geram dezenas de milhares de triângulos. Em vez de derrubar o FPS, o
renderizador para de acumular ao atingir o orçamento.

### Arquitetura

Cada experimento é uma subclasse de `SimulationModule` com ciclo de vida definido:

```
onActivate → buildScene → onUpdate ⟳ → onReset → onDeactivate
```

A interface se constrói sozinha a partir dos parâmetros que o módulo declara, então adicionar um
slider é uma linha de código e não um formulário novo.

📄 Detalhes em [`ARCHITECTURE.md`](ARCHITECTURE.md)

---

## A física é conferível

Nada aqui foi ajustado até parecer bom. Rode:

```bash
./gradlew validatePhysics
```

O comando executa cada módulo sem interface e compara com a solução analítica:

| Verifica | Contra |
|---|---|
| Queda livre | Tempo de impacto = √(2h/g) |
| Projétil | Alcance no vácuo = v²·sen(2θ)/g |
| Colisões | Energia cinética nunca aumenta |
| Plano inclinado | Aceleração = g(senθ − μ·cosθ) |
| Mola | Aceleração inicial = −kx/m |
| Sistema solar | Velocidade orbital da Terra ≈ 29,8 km/s |
| Missões | Hohmann Terra para Marte ≈ 259 dias, em 4 rotas completas |
| Materiais | Altura de quicada medida com régua: borracha 72 cm, aço 36 cm, vidro 41 cm |
| Meios | Madeira flutua, aço afunda com 87% do peso |
| Valores extremos | Sete cenários no limite sem produzir `NaN` |

> Dá para conferir em casa. Solte uma bola de borracha de 1 m de altura sobre uma superfície dura.
> Ela volta a uns 70 cm. O simulador diz 72.

📄 Modelos, fórmulas e fontes em [`docs/FISICA.md`](docs/FISICA.md)

---

## Estrutura do projeto

```
src/main/java/engine/
├── Main.java                     Ponto de entrada
├── core/Engine.java              Aplicação JavaFX, loop principal, layout
├── math/                         Vec3, Mat4, ColorRGBA
├── physics/                      PhysicsWorld, RigidBody, MaterialState, MediumState
├── renderer/                     Renderer3D, Camera, Mesh, TextureMap
├── scene/                        SceneObject, ObjectFactory, ScenarioStore
├── simulation/
│   ├── SimulationModule.java     Classe base dos experimentos
│   ├── ModuleManager.java        Ciclo de vida
│   ├── modules/                  Os 8 experimentos
│   └── parameters/               Parâmetros que geram a UI sozinhos
├── ui/                           DrawingCanvas, Toolbar, LabGraphCanvas
│   └── simulation/               ModulePanel, TelemetryView
└── validation/                   Bateria de validações numéricas
```

---

## Documentação

| Documento | Sobre |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Arquitetura interna, padrões de projeto, como criar um módulo novo |
| [`docs/FISICA.md`](docs/FISICA.md) | Materiais, meios, limites de parâmetro e estabilidade numérica |
| [`docs/DESENHO_3D.md`](docs/DESENHO_3D.md) | Como o desenho livre vira sólido 3D |
| [`docs/CORRECOES.md`](docs/CORRECOES.md) | Registro de correções, com o raciocínio de cada uma |
| [`docs/APRESENTACAO.html`](docs/APRESENTACAO.html) | Apresentação em slides |
| [`docs/PRINTS_DO_PROGRAMA.md`](docs/PRINTS_DO_PROGRAMA.md) | Telas comentadas |

---

## Criando um experimento novo

Crie a classe em `simulation/modules/`, estenda `SimulationModule` e registre em
`ModuleRegistry.registerAll()`. A interface, o gráfico e a telemetria se conectam sozinhos.

```java
public class MeuModulo extends SimulationModule {
    public MeuModulo() {
        super("meu_modulo", "Meu Módulo", "Descrição curta", "Tópico curricular");
    }

    @Override protected void declareParameters() {
        parameters.add(Parameter.of("massa", "Massa", 2.0)
            .range(0.1, 20).limits(1e-6, 1e9).unit("kg"));
    }

    @Override protected void buildScene() {
        new ModuleSceneBuilder(context).sphere(0.5, ColorRGBA.BLUE).at(0, 5, 0).add();
    }

    @Override public void onUpdate(double dt) { /* lógica por quadro */ }

    @Override public String telemetry() { return "..."; }
}
```

---

## Requisitos

- **Java 21** ([Adoptium](https://adoptium.net) é a distribuição recomendada)
- O JavaFX é baixado automaticamente pelo Gradle, não precisa instalar

---

## Licença

MIT, use livremente.
