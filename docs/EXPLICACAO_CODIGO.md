# Explicacao do Codigo do SimulaFisica 3D

Este documento explica o codigo do projeto de ponta a ponta. A ideia e ajudar alguem que abriu o repositorio pela primeira vez a entender onde cada parte fica, qual classe faz o que e como a engine roda.

## 1. Visao geral

O projeto e uma aplicacao desktop em Java com JavaFX. Ele foi organizado como uma pequena engine 3D educacional:

```text
Usuario
  -> Interface JavaFX
  -> Engine
  -> ModuleManager
  -> SimulationModule ativo
  -> SimulationContext
  -> PhysicsWorld + Renderer3D
  -> Canvas 3D na tela
```

O projeto tem dois modos principais:

- modo de simulacoes, em que cada modulo representa um experimento de Fisica;
- modo sandbox/desenho, em que o usuario desenha uma forma 2D e ela vira objeto 3D.

O cabo de guerra tambem e um modulo, mas tem uma interface especial de tela cheia para ficar mais interativo durante a feira.

## 2. Entrada do programa

Arquivo:

```text
src/main/java/engine/Main.java
```

Classe:

```java
public class Main
```

Responsabilidade:

- ser o ponto de entrada da aplicacao;
- chamar `Engine.launch(args)`;
- separar o inicio do programa da classe grande da engine.

Fluxo:

```text
main()
  -> Engine.launch(args)
  -> JavaFX chama Engine.start(stage)
```

## 3. Classe principal da engine

Arquivo:

```text
src/main/java/engine/core/Engine.java
```

Classe:

```java
public class Engine extends Application
```

Essa e a classe que junta quase tudo.

Ela cria:

- a janela JavaFX;
- o `PhysicsWorld`;
- o `Renderer3D`;
- a `Camera`;
- o `SimulationContext`;
- o `ModuleManager`;
- o `ModulePanel`;
- a `Toolbar`;
- o `DrawingCanvas`;
- o `Canvas` onde a cena 3D e desenhada.

### 3.1 start(Stage stage)

Metodo principal do JavaFX.

Ele faz:

1. define o titulo da janela;
2. cria `SimulationContext`;
3. cria `ModuleManager`;
4. registra todos os modulos com `ModuleRegistry.registerAll`;
5. monta os paineis da interface;
6. configura eventos de teclado, mouse e toolbar;
7. ativa o modulo padrao;
8. inicia o loop principal.

### 3.2 startGameLoop()

Cria um `AnimationTimer`.

Esse timer roda a cada frame da aplicacao.

Fluxo resumido:

```text
calcula dt
atualiza configuracoes do renderer
le teclado/camera
atualiza fisica
atualiza modulo ativo
renderiza cena
atualiza HUD/grafico/estatisticas
```

O `dt` e o intervalo de tempo entre frames. Ele e usado para manter o movimento proporcional ao tempo real, sem depender diretamente do FPS.

### 3.3 bindCameraEvents()

Configura mouse e camera.

Faz:

- arrastar mouse para orbitar;
- scroll para zoom;
- clique/arrasto para selecionar e mover objetos no sandbox;
- calculo de raio da camera para interagir com objetos 3D.

### 3.4 bindKeyboard(Scene scene)

Configura teclado.

Usos:

- `WASD`: andar na cena;
- `Q/E`: subir e descer;
- `Shift`: movimento mais rapido;
- `A/L`: controles do cabo de guerra;
- `R`: resetar partida no cabo de guerra.

### 3.5 bindToolbarCallbacks()

Liga botoes da `Toolbar` as funcoes reais da engine.

Exemplos:

- botao `Aleatorio` chama `handleAddRandom`;
- botao `Limpar` chama `handleClear`;
- botao `Salvar cena` chama `handleSaveScenario`;
- botao `Carregar cena` chama `handleLoadScenario`;
- botao de gravidade liga/desliga `PhysicsWorld.setGravityEnabled`.

### 3.6 addObjectFromDraw()

Recebe um desenho feito no `DrawingCanvas` e transforma em `SceneObject`.

Se for desenho livre, chama:

```java
ObjectFactory.createFromDrawing(...)
```

Se for forma simples, chama:

```java
ObjectFactory.create(...)
```

Depois:

- adiciona o objeto na lista do sandbox;
- adiciona o corpo fisico no `PhysicsWorld`;
- seleciona o objeto;
- atualiza a toolbar.

### 3.7 modo sandbox

Metodos:

```java
enableSandboxMode()
disableSandboxMode()
```

O sandbox e ativado quando o usuario entra na aba de desenho.

No sandbox:

- a cena do modulo atual e desligada;
- os objetos desenhados passam a ser os objetos principais;
- o usuario pode mover, editar massa, velocidade e testar gravidade.

### 3.8 cabo de guerra

O cabo de guerra usa:

- `buildTugOverlay()`;
- `showTugRegistration()`;
- `startTugMatch()`;
- `updateTugHud()`;
- `updateTugRegistrationHud()`.

Fluxo:

```text
entra no modulo Cabo de Guerra
  -> mostra cadastro de competidores
  -> jogador informa nomes
  -> inicia partida
  -> teclas A e L passam a valer
  -> HUD mostra forca, toques e ranking
```

## 4. Pacote math

Pasta:

```text
src/main/java/engine/math
```

Esse pacote guarda estruturas matematicas usadas por toda a engine.

### 4.1 Vec3

Arquivo:

```text
Vec3.java
```

Representa um vetor 3D:

```text
(x, y, z)
```

Usado para:

- posicao;
- velocidade;
- aceleracao;
- direcao;
- forca;
- normal de triangulo;
- alvo da camera.

Principais metodos:

- `add`: soma vetores;
- `sub`: subtrai vetores;
- `mul`: multiplica por escalar;
- `dot`: produto escalar;
- `cross`: produto vetorial;
- `length`: modulo do vetor;
- `normalize`: transforma em vetor unitario;
- `distanceTo`: distancia entre pontos;
- `reflect`: reflexao em relacao a uma normal;
- `clamp`: limita o tamanho do vetor.

Exemplo:

```java
Vec3 velocidade = new Vec3(10, 5, 0);
Vec3 novaPosicao = posicao.add(velocidade.mul(dt));
```

### 4.2 Mat4

Arquivo:

```text
Mat4.java
```

Representa matriz 4x4.

Usada para:

- translacao;
- escala;
- rotacao;
- camera;
- perspectiva;
- transformacao de pontos 3D.

Principais metodos:

- `identity`;
- `translation`;
- `scale`;
- `rotationX`;
- `rotationY`;
- `rotationZ`;
- `perspective`;
- `lookAt`;
- `mul`;
- `transformPoint`;
- `transformDir`.

A camera e o renderer dependem muito dessa classe.

### 4.3 ColorRGBA

Arquivo:

```text
ColorRGBA.java
```

Representa cor com:

```text
red, green, blue, alpha
```

Usada para:

- cor dos objetos;
- mistura de cores;
- sombreamento;
- conversao para cor do JavaFX.

Principais metodos:

- `fromHex`;
- `fromJFX`;
- `mix`;
- `shade`;
- `darker`;
- `addSpecular`;
- `toJFX`.

## 5. Pacote physics

Pasta:

```text
src/main/java/engine/physics
```

Esse pacote e a base fisica generica da engine.

### 5.1 RigidBody

Arquivo:

```text
RigidBody.java
```

Representa um corpo fisico.

Guarda:

- posicao;
- velocidade;
- rotacao;
- velocidade angular;
- massa;
- massa inversa;
- restituicao;
- atrito;
- amortecimento;
- tipo fisico;
- raio de colisao.

Tipos fisicos:

```java
DYNAMIC
KINEMATIC
STATIC
```

`DYNAMIC` sofre fisica.

`STATIC` fica parado.

`KINEMATIC` existe para movimento controlado por codigo.

Metodo principal:

```java
integrate(double dt, Vec3 gravity)
```

Ele aplica Euler semi-implicito:

```text
velocidade += aceleracao * dt
posicao += velocidade * dt
```

Tambem resolve:

- colisao com chao;
- colisao com paredes;
- colisao esfera-esfera;
- impulso;
- explosao;
- sistema de sleep para parar objetos quase imoveis.

### 5.2 PhysicsWorld

Arquivo:

```text
PhysicsWorld.java
```

Gerencia todos os `RigidBody`.

Responsabilidades:

- guardar lista de corpos;
- atualizar a fisica;
- aplicar gravidade;
- resolver colisao com chao e paredes;
- resolver colisao entre corpos;
- contar objetos ativos e colisoes.

O mundo usa passo fixo:

```text
dt = 1 / 120 s
```

Isso deixa a fisica mais estavel.

Fluxo:

```text
update(deltaTime)
  -> acumula tempo
  -> step(1/120)
     -> integra corpos
     -> resolve chao/parede
     -> resolve pares de colisao
```

### 5.3 MaterialState

Arquivo:

```text
MaterialState.java
```

Enum com materiais usados nas simulacoes.

Materiais:

- gelatina;
- borracha;
- madeira;
- pedra;
- aco;
- vidro;
- espuma.

Cada material tem:

- densidade;
- restituicao;
- atrito;
- dureza;
- amortecimento linear;
- amortecimento angular;
- energia de fratura aproximada;
- cor.

Usado principalmente em:

- colisao;
- lancamento de projetil;
- dano aproximado;
- calculo de massa por volume.

### 5.4 MediumState

Arquivo:

```text
MediumState.java
```

Enum com meios fisicos do projetil:

- vacuo;
- ar;
- agua;
- oleo;
- gel.

Cada meio tem:

- densidade;
- escala de arrasto;
- fator de gravidade.

O modulo de projetil usa isso para simular resistencia do meio.

## 6. Pacote renderer

Pasta:

```text
src/main/java/engine/renderer
```

Esse pacote desenha a cena 3D no `Canvas` do JavaFX.

### 6.1 Camera

Arquivo:

```text
Camera.java
```

Controla a visao 3D.

Funciona com camera orbital:

- `theta`: angulo horizontal;
- `phi`: angulo vertical;
- `radius`: distancia ate o alvo;
- `target`: ponto observado.

Tambem permite movimento livre com:

```java
moveLocal(...)
```

Principais metodos:

- `orbit`;
- `zoom`;
- `moveLocal`;
- `reset`;
- `setOrbitView`;
- `setTarget`;
- `projectToScreen`;
- `screenToWorldRay`.

`projectToScreen` transforma um ponto 3D em coordenada 2D da tela.

### 6.2 Mesh

Arquivo:

```text
Mesh.java
```

Representa uma malha 3D formada por triangulos.

Cada triangulo tem:

- tres vertices;
- normal;
- coordenadas UV opcionais para textura.

Ela cria geometrias como:

- cubo;
- esfera;
- cone;
- cilindro;
- tetraedro;
- octaedro;
- icosaedro;
- torus;
- desenho extrudado.

Parte importante:

```java
createExtrudedDrawing(...)
```

Esse metodo transforma os pontos do desenho livre em uma malha 3D. Quando o contorno e fechado, ele tenta gerar uma superficie preenchida; quando e aberto, ele gera uma extrusao em forma de traco.

### 6.3 Renderer3D

Arquivo:

```text
Renderer3D.java
```

E o renderizador software da engine.

Ele nao usa OpenGL nem Vulkan. Ele desenha os triangulos manualmente no Canvas JavaFX.

Fluxo:

```text
render()
  -> limpa fundo
  -> desenha grade
  -> coleta triangulos dos objetos
  -> projeta vertices 3D para 2D
  -> calcula luz/sombra/cor
  -> ordena por profundidade
  -> desenha triangulos
  -> desenha vetores
```

Tecnicas usadas:

- painter's algorithm;
- back-face handling;
- flat shading;
- luz ambiente;
- luz principal e secundaria;
- sombras planares;
- wireframe opcional;
- modo leve com limite de triangulos;
- culling simples de objetos pequenos ou fora da tela.

### 6.4 TextureMap

Arquivo:

```text
TextureMap.java
```

Carrega e amostra texturas simples.

Usado principalmente no Sistema Solar para texturas de planetas.

## 7. Pacote scene

Pasta:

```text
src/main/java/engine/scene
```

Esse pacote define objetos da cena.

### 7.1 SceneObject

Arquivo:

```text
SceneObject.java
```

E a unidade principal renderizada na engine.

Um `SceneObject` junta:

- nome;
- malha 3D (`Mesh`);
- corpo fisico (`RigidBody`);
- cor;
- textura;
- transformacao de rotacao;
- visibilidade;
- estado selecionado;
- camada de renderizacao.

Isso e composicao: um objeto de cena e composto por partes menores.

### 7.2 ObjectFactory

Arquivo:

```text
ObjectFactory.java
```

Classe que cria objetos prontos.

Responsabilidades:

- receber tipo de forma;
- receber preset fisico;
- receber cor;
- calcular dimensoes;
- criar `Mesh`;
- criar `RigidBody`;
- calcular massa aproximada;
- devolver `SceneObject`.

Metodos principais:

- `create`;
- `createFromDrawing`;
- `createRandom`.

`createFromDrawing` e usado pelo modo de desenho livre.

### 7.3 ScenarioStore

Arquivo:

```text
ScenarioStore.java
```

Salva e carrega cenas do sandbox.

Usa `Properties`, um formato simples de chave e valor.

Ele salva:

- nome do objeto;
- tipo;
- cor;
- posicao;
- velocidade;
- rotacao;
- massa;
- propriedades fisicas;
- triangulos da malha.

Isso permite salvar tambem objetos desenhados pelo usuario.

## 8. Pacote simulation

Pasta:

```text
src/main/java/engine/simulation
```

Esse pacote organiza os experimentos.

### 8.1 SimulationModule

Arquivo:

```text
SimulationModule.java
```

Classe base abstrata para todos os modulos.

Ela define o contrato:

```java
protected abstract void buildScene();
public abstract void onUpdate(double dt);
```

Tambem oferece hooks:

- `declareParameters`;
- `onPostActivate`;
- `onPostReset`;
- `onDeactivate`;
- `onParameterChanged`;
- `sampleLabMeasurement`;
- `telemetry`.

Quando um modulo e ativado:

```text
onActivate(context)
  -> guarda contexto
  -> reseta tempo
  -> aplica parametros padrao
  -> buildScene()
  -> buildControlPanel()
  -> onPostActivate()
```

Quando reseta:

```text
onReset()
  -> limpa cena
  -> reseta tempo
  -> aplica parametros
  -> buildScene()
  -> onPostReset()
```

### 8.2 ModuleManager

Arquivo:

```text
ModuleManager.java
```

Gerencia os modulos.

Responsabilidades:

- registrar modulos;
- ativar modulo por ID;
- desativar modulo atual;
- resetar modulo ativo;
- enviar `onUpdate(dt)`;
- avisar a UI quando o modulo muda.

### 8.3 ModuleRegistry

Arquivo:

```text
ModuleRegistry.java
```

Registra todos os modulos disponiveis:

- `FreeFallModule`;
- `ProjectileModule`;
- `CollisionModule`;
- `InclinedPlaneModule`;
- `PendulumModule`;
- `SpringModule`;
- `SolarSystemModule`;
- `TugOfWarModule`.

O modulo padrao e:

```text
free_fall
```

### 8.4 SimulationContext

Arquivo:

```text
SimulationContext.java
```

Objeto compartilhado entre engine e modulos.

Ele guarda:

- referencia ao `PhysicsWorld`;
- referencia a `Camera`;
- lista de `SceneObject`;
- gravidade;
- escala de tempo;
- estado pausado;
- tempo simulado;
- FPS;
- observadores.

Os modulos nao mexem diretamente na `Engine`; eles usam o `SimulationContext`.

Isso reduz acoplamento.

### 8.5 ModuleSceneBuilder

Arquivo:

```text
ModuleSceneBuilder.java
```

Builder fluente para criar objetos na cena.

Exemplo de uso:

```java
builder.sphere(0.5, ColorRGBA.BLUE)
    .at(0, 5, 0)
    .isStatic()
    .named("bola")
    .add();
```

Ele deixa o codigo dos modulos mais legivel.

### 8.6 LabMeasurement

Arquivo:

```text
LabMeasurement.java
```

Representa uma amostra do laboratorio.

Guarda:

- nome do modulo;
- grandeza A;
- unidade A;
- valor A;
- grandeza B;
- unidade B;
- valor B;
- anotacao.

Usado pelo grafico e pela exportacao CSV.

### 8.7 LabPreset e LabPresetCatalog

Arquivos:

```text
LabPreset.java
LabPresetCatalog.java
```

Guardam presets de experimentos.

Um preset define um conjunto de parametros para repetir uma situacao.

Isso ajuda na apresentacao, porque o aluno consegue comparar experimentos sem digitar tudo de novo.

### 8.8 SimulationObserver

Arquivo:

```text
SimulationObserver.java
```

Interface de eventos.

Permite observar:

- objeto adicionado;
- objeto removido;
- cena limpa;
- reset de simulacao.

### 8.9 challenges

Pasta:

```text
src/main/java/engine/simulation/challenges
```

Contem:

- `Challenge`;
- `ChallengeResult`.

Essas classes representam desafios educacionais dentro de alguns modulos.

## 9. Modulos de simulacao

Pasta:

```text
src/main/java/engine/simulation/modules
```

Cada modulo herda de `SimulationModule`.

Todos seguem a mesma ideia:

```text
declareParameters()
buildScene()
onUpdate(dt)
onParameterChanged(...)
telemetry()
sampleLabMeasurement()
```

### 9.1 FreeFallModule

Arquivo:

```text
FreeFallModule.java
```

Simula queda livre.

Fisica usada:

```text
h = h0 + v0.t - 1/2.g.t^2
vy = v0 - g.t
velocidade escalar = |vy|
```

Caracteristicas:

- usa equacao analitica, nao depende do resolvedor generico;
- mostra altura, tempo, velocidade e sentido;
- trata velocidade exibida como modulo para evitar leitura negativa;
- permite alterar gravidade, altura, velocidade inicial e raio.

### 9.2 ProjectileModule

Arquivo:

```text
ProjectileModule.java
```

Simula lancamento de projetil.

Fisica usada:

- velocidade inicial decomposta em `vx` e `vy`;
- gravidade;
- arrasto proporcional a velocidade ao quadrado;
- meio fisico;
- impacto contra alvos;
- resposta por material.

Materiais:

- projetil tem massa por densidade;
- alvo pode quebrar, absorver ou reagir diferente conforme dureza, energia e material.

Meios:

- vacuo;
- ar;
- agua;
- oleo;
- gel.

### 9.3 CollisionModule

Arquivo:

```text
CollisionModule.java
```

Simula colisao entre dois corpos.

Permite escolher:

- material A;
- material B;
- raio dos objetos;
- velocidade;
- deslocamento lateral;
- gravidade.

Fisica usada:

- conservacao aproximada de momento;
- impulso;
- coeficiente de restituicao;
- energia cinetica;
- dano aproximado por material;
- fragmentos quando ha quebra.

### 9.4 InclinedPlaneModule

Arquivo:

```text
InclinedPlaneModule.java
```

Simula plano inclinado com atrito.

Equacao:

```text
a = g.(sen(theta) - mu.cos(theta))
```

Se o atrito for suficiente, o bloco nao acelera.

Se a componente da gravidade vencer o atrito, o bloco desliza.

### 9.5 PendulumModule

Arquivo:

```text
PendulumModule.java
```

Simula pendulo simples.

Equacao:

```text
theta'' = -(g/L).sen(theta) - c.theta'
```

Onde:

- `theta` e o angulo;
- `L` e o comprimento;
- `c` e amortecimento.

O modulo atualiza a massa e os marcadores do fio visualmente.

### 9.6 SpringModule

Arquivo:

```text
SpringModule.java
```

Simula oscilador massa-mola.

Equacoes:

```text
F = -k.x
a = (-k.x - c.v) / m
```

Mostra:

- deslocamento;
- velocidade;
- aceleracao;
- energia mecanica.

### 9.7 SolarSystemModule

Arquivo:

```text
SolarSystemModule.java
```

Simula sistema solar simplificado.

Usa:

- unidades astronomicas;
- dias;
- gravidade newtoniana do Sol;
- orbitas com elementos aproximados;
- integracao numerica;
- texturas;
- luas;
- aneis em planetas que possuem aneis no modelo visual.

Permite alterar:

- planeta observado;
- situacao orbital;
- intensidade;
- dias por segundo;
- foco da camera.

A escala visual e comprimida para caber na tela.

### 9.8 TugOfWarModule

Arquivo:

```text
TugOfWarModule.java
```

Minijogo de cabo de guerra.

Conceitos fisicos:

- forca resultante;
- massa efetiva;
- resistencia;
- aceleracao;
- velocidade;
- equilibrio.

Controles:

- jogador da esquerda usa `A`;
- jogador da direita usa `L`;
- nomes sao cadastrados antes da partida.

O modulo tambem guarda ranking com:

- nome;
- toques;
- melhor CPS;
- resultado.

## 10. Pacote parameters

Pasta:

```text
src/main/java/engine/simulation/parameters
```

### 10.1 Parameter

Representa um parametro editavel.

Exemplos:

- gravidade;
- massa;
- angulo;
- velocidade;
- material;
- meio fisico.

Cada parametro pode ter:

- nome interno;
- rotulo;
- valor padrao;
- minimo;
- maximo;
- passo;
- unidade;
- tooltip;
- opcoes.

### 10.2 ParameterSet

Colecao ordenada de parametros.

Permite:

- adicionar parametro;
- buscar por nome;
- mudar valor;
- resetar todos;
- iterar sobre todos.

O `ModulePanel` usa `ParameterSet` para construir a interface automaticamente.

## 11. Pacote ui

Pasta:

```text
src/main/java/engine/ui
```

### 11.1 ModulePanel

Arquivo:

```text
ui/simulation/ModulePanel.java
```

Painel das simulacoes.

Responsabilidades:

- mostrar lista de modulos;
- mostrar descricao do modulo ativo;
- criar controles de parametros;
- aplicar valores digitados;
- executar reset/lancamento;
- pausar;
- passo unico;
- exportar CSV;
- mostrar resultados e referencias.

Ele nao implementa a fisica; ele apenas conversa com `ModuleManager` e `SimulationModule`.

### 11.2 Toolbar

Arquivo:

```text
Toolbar.java
```

Painel direito da engine.

Controla:

- modo de desenho;
- forma 3D;
- material fisico;
- cor;
- objeto aleatorio;
- impulso;
- limpar;
- reset de camera;
- salvar/carregar cena;
- objeto selecionado;
- wireframe;
- grade;
- sombras;
- vetores;
- modo leve;
- gravidade;
- estatisticas;
- grafico.

### 11.3 DrawingCanvas

Arquivo:

```text
DrawingCanvas.java
```

Canvas 2D de entrada.

Modos:

- livre;
- retangulo;
- circulo;
- triangulo.

No modo livre:

- cada traco e guardado;
- multiplos tracos podem formar uma figura composta;
- ao clicar em `Criar 3D`, os pontos normalizados sao enviados para a engine;
- a engine chama `ObjectFactory.createFromDrawing`.

### 11.4 LabGraphCanvas

Arquivo:

```text
LabGraphCanvas.java
```

Desenha grafico simples das grandezas do modulo ativo.

Recebe `LabMeasurement` e mostra evolucao visual.

## 12. Fluxo completo de uma simulacao

Exemplo com queda livre:

```text
1. Usuario abre a aplicacao.
2. Engine registra todos os modulos.
3. ModuleRegistry retorna "free_fall".
4. ModuleManager ativa FreeFallModule.
5. FreeFallModule declara parametros e monta a cena.
6. Engine entra no AnimationTimer.
7. A cada frame:
   - PhysicsWorld atualiza corpos;
   - FreeFallModule.onUpdate(dt) calcula altura e velocidade;
   - Renderer3D desenha objetos;
   - ModulePanel mostra telemetria;
   - LabGraphCanvas recebe amostras.
8. Usuario muda gravidade.
9. ModulePanel altera Parameter.
10. ModuleManager chama onParameterChanged.
11. Modulo reseta e reconstrui a cena.
```

## 13. Fluxo completo do desenho livre

```text
1. Usuario entra na aba Desenhar.
2. Engine ativa sandbox.
3. Usuario escolhe modo Livre.
4. DrawingCanvas registra pontos do mouse.
5. Usuario clica em Criar 3D.
6. DrawingCanvas envia DrawResult.
7. Engine chama addObjectFromDraw.
8. ObjectFactory.createFromDrawing transforma pontos em Vec3.
9. Mesh.createExtrudedDrawing cria triangulos.
10. RigidBody recebe massa, atrito e colisao.
11. SceneObject e adicionado ao PhysicsWorld.
12. Renderer3D desenha o objeto no ambiente 3D.
```

## 14. Fluxo completo do cabo de guerra

```text
1. Usuario seleciona Cabo de Guerra.
2. Engine esconde paineis laterais e mostra overlay.
3. Tela de registro pede nomes.
4. Ao iniciar, TugOfWarModule reseta partida.
5. Tecla A chama pressLeft/holdLeft.
6. Tecla L chama pressRight/holdRight.
7. onUpdate calcula:
   - forca resultante;
   - aceleracao;
   - velocidade da corda;
   - posicao da corda.
8. Quando a posicao chega ao limite, registra vencedor.
9. Ranking e HUD sao atualizados.
```

## 15. Padroes de projeto usados

### Template Method

Usado em `SimulationModule`.

A classe base define o ciclo:

```text
onActivate -> buildScene -> onUpdate -> onReset
```

Cada modulo implementa sua parte especifica.

### Context Object

Usado em `SimulationContext`.

Os modulos recebem um objeto com tudo que precisam, sem depender diretamente da classe `Engine`.

### Builder

Usado em `ModuleSceneBuilder`.

Facilita criar objetos 3D com chamadas encadeadas.

### Registry

Usado em `ModuleRegistry`.

Centraliza quais modulos existem.

### Value Object

Usado em:

- `Vec3`;
- `ColorRGBA`;
- `LabMeasurement`;
- `LabPreset`;
- `ChallengeResult`.

### Observer

Usado em `SimulationObserver`.

Permite reagir a eventos da cena.

## 16. Onde alterar cada coisa

### Adicionar novo modulo

1. Criar uma classe em:

```text
src/main/java/engine/simulation/modules
```

2. Estender `SimulationModule`.

3. Implementar:

```java
declareParameters()
buildScene()
onUpdate(double dt)
telemetry()
sampleLabMeasurement()
```

4. Registrar em:

```text
ModuleRegistry.java
```

### Adicionar novo material

Editar:

```text
MaterialState.java
```

Adicionar novo item com:

- nome;
- densidade;
- restituicao;
- atrito;
- dureza;
- amortecimento;
- energia de fratura;
- cor.

### Adicionar nova forma 3D

1. Criar metodo em `Mesh.java`.
2. Adicionar enum em `ObjectFactory.ShapeType`.
3. Atualizar `ObjectFactory.buildMesh`.
4. Atualizar o ComboBox em `Toolbar.java`.

### Mudar visual da interface

Arquivos principais:

```text
Engine.java
ModulePanel.java
Toolbar.java
DrawingCanvas.java
LabGraphCanvas.java
```

### Mudar fisica generica

Arquivos:

```text
PhysicsWorld.java
RigidBody.java
```

### Mudar uma simulacao especifica

Editar apenas o modulo correspondente em:

```text
src/main/java/engine/simulation/modules
```

## 17. Arquivos de build

### build.gradle.kts

Configura o projeto Gradle.

Define:

- plugin Java;
- plugin JavaFX;
- versao do Java;
- classe principal;
- dependencias.

### settings.gradle.kts

Define o nome do projeto Gradle.

### pom.xml

Existe como alternativa Maven.

### gradlew e gradlew.bat

Scripts do Gradle Wrapper.

Permitem compilar sem instalar Gradle manualmente.

## 18. Comandos importantes

Rodar:

```bat
gradlew.bat run
```

Compilar:

```bat
gradlew.bat build
```

Gerar distribuicao:

```bat
gradlew.bat installDist
```

Executavel local empacotado:

```text
dist-next/Physics3DEngine/Physics3DEngine.exe
```

## 19. Resumo para explicar em sala

O codigo foi separado em camadas:

- `core` liga tudo;
- `math` faz as contas;
- `physics` simula corpos;
- `renderer` desenha 3D;
- `scene` representa objetos;
- `simulation` organiza experimentos;
- `ui` monta a interface.

A engine usa Programacao Orientada a Objetos porque cada parte da simulacao vira uma classe com responsabilidade propria. A fisica usa vetores, equacoes de movimento, integracao numerica e parametros reais simplificados. A interface JavaFX permite que o usuario altere valores e veja a resposta em tempo real.

Frase curta:

```text
O SimulaFisica 3D transforma conceitos de Fisica em objetos e modulos Java, mostrando em tempo real como as equacoes se comportam dentro de uma cena tridimensional interativa.
```
