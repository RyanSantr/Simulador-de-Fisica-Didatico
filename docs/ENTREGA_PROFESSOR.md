# SimulaFisica 3D - Documento de Entrega

Este documento explica o que foi usado no projeto, como a engine foi organizada e como apresentar o sistema para o professor.

## 1. Objetivo do projeto

O projeto e uma engine educacional em Java para demonstrar conceitos de Fisica Computacional em um ambiente 3D interativo.

A proposta e mostrar que a programacao pode representar fenomenos fisicos reais, permitindo alterar dados de entrada e observar o resultado na cena:

- movimento uniforme e movimento uniformemente variado;
- queda livre;
- lancamento de projetil;
- colisoes com materiais;
- plano inclinado;
- pendulo simples;
- sistema massa-mola;
- sistema solar simplificado;
- desenho livre convertido em objeto 3D;
- cabo de guerra como atividade interativa.

## 2. Tecnologias usadas

### Java

Java foi escolhido por ser uma linguagem orientada a objetos, estavel e comum em cursos de programacao.

No projeto, Java e usado para:

- criar classes que representam objetos fisicos;
- controlar a simulacao em tempo real;
- calcular vetores, posicoes, velocidades e aceleracoes;
- organizar os modulos da engine;
- montar a interface grafica.

### JavaFX

JavaFX foi usado para criar a interface desktop.

Ele aparece no projeto em:

- janela principal;
- botoes, abas, campos de texto, sliders e seletores;
- canvas 2D de desenho;
- canvas 3D renderizado pela propria engine;
- painel de graficos e telemetria.

### Gradle

Gradle foi usado como sistema de build.

Com ele e possivel:

- compilar o projeto;
- executar a aplicacao;
- gerar distribuicao local;
- manter dependencias do JavaFX organizadas.

Comando principal:

```bash
./gradlew run
```

No Windows:

```bat
gradlew.bat run
```

Tambem e possivel abrir direto em um modulo, o que ajuda durante a apresentacao:

```bat
gradlew.bat run --args="--module=projectile"
gradlew.bat run --args="--module=collisions"
gradlew.bat run --args="--module=solar_system"
gradlew.bat run --args="--module=tug_of_war"
```

## 3. Ferramentas utilizadas

O projeto usou ferramentas de desenvolvimento, documentacao e apoio.

### Ferramentas de construcao do projeto

- `Java`: linguagem principal usada para implementar a engine, as classes, os calculos fisicos e os modulos.
- `JavaFX / OpenJFX`: biblioteca usada para a interface grafica, canvas 2D, canvas 3D, controles, abas e paineis.
- `Gradle`: ferramenta principal de build, usada para compilar e executar o projeto.
- `Maven`: suporte alternativo de build mantido pelo arquivo `pom.xml`.
- `IntelliJ IDEA`: IDE recomendada para abrir, executar e depurar o projeto.
- `Git`: controle de versao local.
- `GitHub`: hospedagem do repositorio e entrega online.

### Ferramentas de apoio

- `Codex / ChatGPT`: assistente de desenvolvimento usado para apoiar a arquitetura, organizacao do codigo, revisoes, documentacao.
- `PowerPoint / PPTX`: formato usado para a apresentacao do projeto.

Observacao: Codex foi usado como ferramenta de apoio. A explicacao em sala deve mostrar que os integrantes entendem o codigo, os conceitos fisicos e as escolhas feitas no projeto.

Um detalhamento maior esta em:

```text
docs/FERRAMENTAS_UTILIZADAS.md
```

Os prints do programa, com explicacao do que cada area da tela faz, estao em:

```text
docs/PRINTS_DO_PROGRAMA.md
```

## 4. Organizacao do codigo

A pasta principal do codigo e:

```text
src/main/java/engine
```

Ela foi dividida em pacotes para deixar o projeto com cara de software real.

### core

Contem a classe principal da engine.

Responsabilidades:

- iniciar JavaFX;
- montar a tela;
- conectar fisica, renderizacao e modulos;
- controlar o loop principal;
- receber teclado, mouse e comandos da interface.

Classe principal:

```text
engine.core.Engine
```

### math

Contem estruturas matematicas usadas pela engine.

Exemplos:

- `Vec3`: vetor 3D com x, y e z;
- `Mat4`: matriz 4x4 para transformacoes;
- `ColorRGBA`: cor com componentes vermelho, verde, azul e alfa.

### physics

Contem a base da simulacao fisica.

Exemplos:

- `RigidBody`: corpo rigido com massa, posicao, velocidade e aceleracao;
- `PhysicsWorld`: mundo fisico, gravidade, colisao e atualizacao;
- `MaterialState`: propriedades dos materiais;
- `MediumState`: propriedades do meio fisico, como ar, agua ou vacuo.

### renderer

Contem a renderizacao 3D.

Exemplos:

- `Camera`: camera orbital e livre;
- `Mesh`: malhas geometricas;
- `TextureMap`: texturas simples;
- `Renderer3D`: pipeline de desenho 3D no Canvas JavaFX.

### scene

Contem objetos da cena.

Exemplos:

- `SceneObject`: objeto com malha, corpo fisico e material;
- `ObjectFactory`: fabrica de formas;
- `ScenarioStore`: salvar e carregar cenas.

### simulation

Contem o sistema de modulos educacionais.

Exemplos:

- `SimulationModule`: classe base de cada simulacao;
- `ModuleManager`: ativa e atualiza o modulo atual;
- `ModuleRegistry`: registra os modulos disponiveis;
- `ModuleSceneBuilder`: cria objetos 3D de forma organizada;
- `LabMeasurement`: guarda dados para grafico e CSV.

### ui

Contem elementos da interface.

Exemplos:

- `Toolbar`: painel de ferramentas;
- `DrawingCanvas`: painel de desenho livre;
- `LabGraphCanvas`: grafico das medidas;
- `ModulePanel`: painel das simulacoes e parametros.

## 5. Conceitos de POO usados

O projeto usa Programacao Orientada a Objetos de forma direta.

### Classe

Cada parte importante do sistema e representada por uma classe:

- corpo fisico;
- vetor;
- camera;
- modulo de simulacao;
- objeto da cena;
- painel da interface.

### Encapsulamento

Cada classe guarda seus dados e expoe apenas os metodos necessarios.

Exemplo:

```java
body.getPosition();
body.setVelocity(...);
```

### Heranca e polimorfismo

As simulacoes herdam de `SimulationModule`.

Isso permite que a engine trate todos os modulos do mesmo jeito:

```java
module.onUpdate(dt);
module.onReset();
```

Mesmo assim, cada modulo executa sua propria regra fisica.

### Composicao

Um objeto da cena junta varias partes:

- uma malha 3D;
- um corpo fisico;
- um material;
- um nome;
- uma cor.

Isso deixa o codigo mais organizado do que colocar tudo em uma classe so.

## 6. Conceitos fisicos implementados

### Vetores

Vetores representam posicao, velocidade, aceleracao e forca em 3D:

```text
V = (x, y, z)
```

Eles aparecem em praticamente toda a engine.

### Movimento Uniforme

Movimento com velocidade constante.

Equacao:

```text
S = S0 + v.t
```

Uso no projeto:

- deslocamentos controlados;
- trajetorias sem aceleracao;
- comparacao com movimentos acelerados.

### Movimento Uniformemente Variado

Movimento com aceleracao constante.

Equacoes:

```text
v = v0 + a.t
S = S0 + v0.t + (a.t^2)/2
```

Uso no projeto:

- queda livre;
- lancamento de projetil;
- movimento com gravidade.

### Gravidade

A gravidade padrao usada e proxima de:

```text
g = 9,81 m/s^2
```

Ela pode ser ligada, desligada ou alterada dependendo do modulo.

### Colisoes

A engine calcula contato e resposta usando aproximacoes fisicas.

Nos modulos educacionais, a colisao considera:

- massa;
- velocidade;
- coeficiente de restituicao;
- atrito;
- dureza;
- fragilidade do material;
- energia aproximada do impacto.

### Lancamento de projetil

O projetil e afetado por:

- velocidade inicial;
- angulo;
- gravidade;
- meio fisico;
- arrasto.

Meios disponiveis:

- vacuo;
- ar;
- agua;
- oleo;
- gel.

### Plano inclinado

O plano inclinado compara a componente do peso com o atrito.

Equacao simplificada:

```text
a = g.(sen(theta) - mu.cos(theta))
```

### Pendulo

O pendulo usa uma equacao nao linear:

```text
theta'' = -(g/L).sen(theta) - c.theta'
```

Isso mostra que a simulacao nao e apenas uma animacao simples.

### Sistema massa-mola

Baseado na Lei de Hooke:

```text
F = -k.x
a = (-k.x - c.v) / m
```

### Sistema Solar

O sistema solar usa uma versao simplificada de gravitacao orbital.

Foram adicionados:

- planetas;
- orbitas;
- rotacao visual;
- luas;
- texturas representativas;
- foco de camera.

A escala visual e comprimida para caber na tela.

## 7. Interface e acessibilidade

A interface foi ajustada para ficar mais inclusiva e entregavel:

- janela maior;
- painel esquerdo mais largo;
- painel direito mais largo;
- botoes maiores;
- fontes maiores;
- contraste melhorado;
- area de desenho maior e responsiva;
- parametros mais visiveis;
- reducao de rolagens pequenas dentro de caixas apertadas.

O objetivo foi deixar a aplicacao utilizavel em apresentacao de sala, sem exigir que o visitante fique procurando controles pequenos.

## 8. Modos de uso

### Simulacoes

Na aba de simulacoes, o usuario escolhe um modulo e altera parametros.

Exemplos:

- massa;
- velocidade;
- angulo;
- gravidade;
- material;
- meio fisico;
- coeficiente de atrito.

### Desenho livre

Na aba de desenho, o usuario pode:

- desenhar formas livres;
- criar objetos 3D a partir do desenho;
- mover objetos;
- testar gravidade e colisao.

### Cabo de guerra

O cabo de guerra e uma atividade interativa.

Fluxo:

1. cadastrar nome dos competidores;
2. iniciar partida;
3. competidor da esquerda usa `A`;
4. competidor da direita usa `L`;
5. o sistema mostra quem esta aplicando mais forca;
6. o ranking registra resultados.

## 9. Como executar no IntelliJ IDEA

1. Abrir o IntelliJ IDEA.
2. Ir em `File > Open`.
3. Selecionar a pasta do projeto.
4. Esperar o Gradle importar.
5. Executar a classe:

```text
engine.Main
```

Outra opcao e abrir o terminal do IntelliJ e rodar:

```bat
gradlew.bat run
```

## 10. Como gerar build local

Para compilar:

```bat
gradlew.bat build
```

Para gerar distribuicao:

```bat
gradlew.bat installDist
```

O executavel empacotado, quando gerado com `jpackage`, fica normalmente em:

```text
dist-next/Physics3DEngine/Physics3DEngine.exe
```

## 11. Limitacoes honestas

O projeto e uma engine educacional, nao um simulador profissional certificado.

Limitacoes:

- colisoes gerais usam aproximacoes;
- deformacao de material e dano sao pedagogicos;
- sistema solar usa escala visual comprimida;
- desenho livre nao e CAD;
- renderer 3D usa Canvas JavaFX e pode exigir modo leve em cenas pesadas.

Mesmo assim, o projeto cumpre o objetivo de demonstrar Fisica Computacional, POO, vetores, simulacao e visualizacao 3D.

## 12. Referencias usadas

- Halliday, Resnick e Walker - Fundamentos de Fisica.
- Serway e Jewett - Principios de Fisica.
- Baraff e Witkin - Physically Based Modeling.
- Ericson - Real-Time Collision Detection.
- NASA/JPL SSD - dados orbitais e planetarios.
- NASA NSSDC - Planetary Fact Sheet.
- OpenJFX Documentation - JavaFX e Canvas.

O projeto mostra como a programacao orientada a objetos pode transformar conceitos de Fisica em simulacoes 3D interativas, permitindo testar parametros e visualizar o comportamento dos corpos em tempo real.
