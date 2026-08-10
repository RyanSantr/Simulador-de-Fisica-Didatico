# Como foi feito

Este documento resume a construcao da pequena engine 3D usada no projeto de Fisica Computacional.
A ideia central foi criar uma aplicacao JavaFX organizada em modulos, com simulacoes editaveis por parametros reais e visualizacao 3D leve o bastante para rodar em computador comum de feira.

## Objetivo do projeto

O app foi construido para demonstrar conceitos de Fisica usando programacao orientada a objetos:

- Movimento uniforme e movimento uniformemente variado.
- Queda livre com gravidade configuravel.
- Lancamento de projetil com meio fisico e materiais.
- Colisoes com massa, restituicao, atrito, dureza e dano aproximado.
- Plano inclinado com componente do peso e atrito.
- Pendulo simples nao linear.
- Oscilador massa-mola.
- Sistema solar com orbitas, texturas e satelites naturais em escala visual comprimida.
- Sandbox de desenho livre, em que a forma desenhada vira objeto 3D.

## Organizacao do codigo

A estrutura principal fica em `src/main/java/engine`:

- `core`: inicializa JavaFX, monta a interface, controla o loop principal e integra renderer, fisica e modulos.
- `math`: vetores e matrizes usados pelo renderer e pela fisica.
- `physics`: corpos rigidos, mundo fisico, gravidade, colisoes simples e estados de material/meio.
- `renderer`: camera, malhas, texturas e renderizacao 3D via `Canvas` JavaFX.
- `scene`: objetos da cena, fabrica de formas e persistencia de cenarios do sandbox.
- `simulation`: contrato dos modulos, contexto compartilhado, presets, metricas de laboratorio e registro das simulacoes.
- `ui`: painel de ferramentas, grafico e painel lateral dos modulos.

Cada simulacao estende `SimulationModule`. Isso deixa o projeto com uma arquitetura simples: o `ModuleManager` ativa um modulo por vez, chama `onUpdate(dt)` a cada frame e entrega a cena ao `Renderer3D`.

## Como a fisica foi implementada

O `PhysicsWorld` usa passo fixo de 120 Hz para manter estabilidade visual. Corpos dinamicos recebem gravidade, amortecimento e colisao com chao/paredes. As colisoes gerais usam volumes esfericos aproximados, porque isso e barato e suficiente para a feira.

Algumas simulacoes nao dependem do resolvedor generico porque precisam de comportamento mais didatico:

- Queda livre: usa equacao analitica de MUV, `h = h0 + v0.t - 1/2.g.t^2`, evitando erro acumulado.
- Projetil: integra em passos pequenos, aplica gravidade, arrasto quadratico do meio e detecta impacto por varredura de segmento contra esfera.
- Colisoes: calcula impulso e depois ajusta a resposta de acordo com material, massa e dano.
- Plano inclinado: usa `a = g(sen(theta) - mu.cos(theta))`.
- Pendulo: integra `theta'' = -(g/L).sen(theta) - c.theta'`.
- Mola: usa `a = (-k.x - c.v) / m`.
- Sistema solar: usa gravidade newtoniana do Sol em unidades astronomicas e dias, com integracao velocity-Verlet.

## Modo laboratorio

O modo laboratorio foi criado para deixar a apresentacao mais controlavel:

- Pausar, retomar e avancar um passo unico.
- Alterar escala de tempo sem mudar as equacoes.
- Usar presets repetiveis para comparar experimentos.
- Mostrar um HUD com medidas principais.
- Exportar CSV com as amostras do grafico.

As amostras usam `LabMeasurement`, e cada modulo informa suas duas grandezas principais. Assim o grafico muda de acordo com a simulacao: altura e velocidade na queda, percurso na rampa, angulo no pendulo, deslocamento na mola, velocidades na colisao e assim por diante.

## Otimizacoes aplicadas

Como o renderer e software, todo triangulo desenhado custa CPU. Por isso foram aplicadas algumas decisoes:

- `Modo leve` ligado por padrao na toolbar.
- Limite de triangulos por frame no renderer.
- Culling simples por objeto antes de projetar todos os triangulos.
- Pular objetos muito pequenos quando ocupam menos de cerca de 1 pixel.
- Limitar setas de vetores em cenas grandes.
- Reduzir marcadores de orbita e rastro no Sistema Solar.
- Reduzir subdivisao das esferas dos planetas e luas sem remover texturas.
- Evitar sombras em malhas muito pesadas no modo leve.

Essas escolhas mantem o visual 3D bom para apresentacao, mas reduzem bastante trabalho repetitivo de CPU.

## Interface

O visual foi mantido como bancada academica: escuro, limpo, com textos diretos e foco em parametros, medidas e resultado. A interface foi dividida em:

- Painel esquerdo: simulacoes, parametros, presets e resultados.
- Centro: cena 3D e HUD de medidas.
- Painel direito: desenho/sandbox, material, objeto selecionado, visual, performance e grafico.
- Rodape: ajuda curta de camera e interacao.

## Ferramentas utilizadas

O projeto foi construido com:

- `Java`, como linguagem principal.
- `JavaFX / OpenJFX`, para interface desktop, canvas 2D e renderizacao da cena 3D.
- `Gradle`, para compilar e executar o projeto.
- `Maven`, como suporte alternativo de build.
- `IntelliJ IDEA`, como IDE recomendada para abrir e executar.
- `Git`, para controle de versao.
- `GitHub`, para hospedagem e entrega do repositorio.
- `Codex / ChatGPT`, como assistente de apoio para arquitetura, implementacao, revisao, documentacao e apresentacao.
- `PowerPoint / PPTX`, como formato da apresentacao final.

Codex foi usado como apoio de desenvolvimento, nao como substituto da explicacao dos integrantes. Para apresentar bem, e importante entender as classes principais, os modulos de simulacao e as formulas fisicas usadas.

## Limitacoes honestas

Esta engine nao substitui um motor fisico profissional. Algumas escolhas sao simplificadas para caber no projeto:

- Colisoes gerais usam raio esferico aproximado.
- Deformacao de materiais e dano sao modelos pedagogicos, nao simulacao molecular.
- O Sistema Solar comprime escala visual e ignora perturbacoes entre planetas.
- O desenho livre vira malha extrudada a partir dos tracos, nao uma reconstrucao CAD perfeita.
- O renderer usa `Canvas` JavaFX e painter algorithm, entao cenas enormes ainda podem exigir o `Modo leve`.

Mesmo com essas limitacoes, a arquitetura permite explicar fisica, POO, vetores, integracao numerica, materiais e visualizacao 3D de forma pratica.

## Referencias usadas

- Halliday, Resnick e Walker, `Fundamentos de Fisica`.
- Serway e Jewett, `Principios de Fisica`.
- Baraff e Witkin, `Physically Based Modeling`.
- Ericson, `Real-Time Collision Detection`.
- NASA/JPL SSD e NASA NSSDC, dados planetarios e orbitais.
- OpenJFX Documentation, JavaFX e Canvas.
