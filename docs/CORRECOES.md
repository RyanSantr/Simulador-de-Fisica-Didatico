# Registro de correções e melhorias

Documento técnico das mudanças aplicadas ao SimulaFísica 3D na versão 2.2.0.
Cada item traz **o que estava errado**, **por que era um problema** e **como foi resolvido**.

Os itens 12 a 16 documentam bugs de física encontrados **por teste numérico**, não por leitura de
código: rodar a missão e conferir os números contra a teoria revelou uma chegada falsa, um erro de
mira de 0,25 AU e um solver que não convergia por causa de uma degenerescência geométrica.

---

## Índice

- [Bugs corrigidos](#bugs-corrigidos)
- [Otimizações de desempenho](#otimizações-de-desempenho)
- [Limpeza e organização](#limpeza-e-organização)
- [Funcionalidade nova](#funcionalidade-nova)
- [Documentação](#documentação)
- [Como verificar](#como-verificar)

---

## Bugs corrigidos

### 1. Vazamento de memória a cada reset de simulação

**Arquivo:** `simulation/SimulationModule.java`

**O que acontecia.** O método `onReset()` registrava um novo `SimulationObserver` anônimo no
contexto a cada chamada:

```java
context.addObserver(new SimulationObserver() {
    @Override public void onSimulationReset() {}
});
```

**Por que era um problema.** O observador não fazia nada (corpo vazio), nunca era removido e a
lista de observadores do `SimulationContext` sobrevive à troca de módulos. Cada clique em
"Resetar simulação" adicionava mais um objeto permanente. E cada mudança de parâmetro também,
já que vários módulos chamam `onReset()` em resposta. Numa sessão de feira, com centenas de ajustes
de slider, a lista crescia indefinidamente e cada notificação de evento da cena percorria todos
eles. Degradação progressiva de desempenho sem causa visível.

**Correção.** Remoção completa do bloco. Não havia comportamento a preservar: o observador era
inerte.

---

### 2. Criação de threads a cada desenho no sandbox

**Arquivo:** `ui/DrawingCanvas.java`

**O que acontecia.** O flash de confirmação após criar um objeto disparava uma thread nova:

```java
new Thread(() -> {
    try { Thread.sleep(80); } catch (InterruptedException ignored) {}
    Platform.runLater(() -> { ... });
}).start();
```

**Por que era um problema.** Cada objeto desenhado criava uma thread do sistema operacional só
para dormir 80 ms. Threads não são recicladas nesse padrão: desenhar 200 objetos criava 200
threads. Além do custo de criação (~1 MB de pilha cada), é um antipadrão em aplicações JavaFX,
onde já existe infraestrutura de temporização integrada ao loop da interface.

**Correção.** Substituição por uma única `PauseTransition` criada no construtor e reaproveitada
com `playFromStart()`. Executa na thread do JavaFX, sem criar threads, e o callback foi movido
para `setOnFinished` no construtor.

---

### 3. Método `resize()` incompatível com as propriedades vinculadas

**Arquivo:** `ui/DrawingCanvas.java`

**O que acontecia.** A classe sobrescrevia `resize(double, double)` chamando `setWidth()` e
`setHeight()`.

**Por que era um problema.** O `Engine` vincula essas propriedades por *binding*:

```java
drawCanvas.widthProperty().bind(Bindings.max(150, drawTabContent.widthProperty().subtract(28)));
```

Chamar `setWidth()` numa propriedade vinculada lança `RuntimeException: A bound value cannot be
set`. O método só não quebrava o app porque `Canvas.isResizable()` retorna `false` e o layout do
JavaFX nunca o invocava. Era uma armadilha esperando alguém chamá-lo manualmente ou tornar o
canvas redimensionável.

**Correção.** Método removido. O redimensionamento já é tratado pelos *listeners* de
`widthProperty`/`heightProperty` registrados no construtor, que chamam `repaint()`.

---

### 4. Telemetria dependente de cadeia de `instanceof`

**Arquivo:** `core/Engine.java`

**O que acontecia.** O loop principal escolhia o texto de telemetria com oito testes de tipo
encadeados:

```java
if (module instanceof TugOfWarModule tug) { ... }
else if (module instanceof FreeFallModule freeFall) { ... }
else if (module instanceof ProjectileModule projectile) { ... }
// ... mais cinco
```

**Por que era um problema.** Violação direta do princípio aberto/fechado: todo módulo novo exigia
editar o `Engine`. Um módulo cujo `telemetry()` existisse mas não estivesse na cadeia caía
silenciosamente no texto genérico. Falha invisível, sem erro de compilação. Foi exatamente o que
teria acontecido com o módulo de foguete.

**Correção.** Declaração de `telemetry()` como método na classe base `SimulationModule`, retornando
`null` por padrão. O `Engine` passou a fazer uma única chamada polimórfica:

```java
String telemetry = moduleManager.getActiveModule().telemetry();
modulePanel.updateTelemetry(telemetry != null ? telemetry : resumoGenerico());
```

Sete imports desnecessários foram removidos do `Engine` e `@Override` foi adicionado nos oito
módulos, garantindo erro de compilação se alguma assinatura divergir no futuro.

---

## Otimizações de desempenho

### 5. Alocação por objeto em cada frame no descarte de visibilidade

**Arquivo:** `renderer/Renderer3D.java`

**Antes.** `roughlyVisible()` usava a sobrecarga de `projectToScreen` que aloca um `double[3]`
a cada chamada. O método roda uma vez por objeto por frame.

**Impacto.** O sistema solar tem mais de 400 objetos em cena (planetas, luas, marcadores de órbita,
rastros). A 60 FPS isso significava ~24.000 arrays descartáveis por segundo, alimentando o coletor
de lixo continuamente e causando micro-travadas.

**Depois.** Um buffer `double[3]` de instância, reaproveitado em todas as chamadas. A engine já
usava esse padrão no laço de vértices, mas não aqui. Zero alocações.

---

### 6. Vetores temporários no cálculo de distância

**Arquivo:** `math/Vec3.java`

**Antes.**

```java
public double distanceTo(Vec3 o)   { return sub(o).length(); }
public double distanceSqTo(Vec3 o) { return sub(o).lengthSq(); }
```

Cada chamada criava um `Vec3` intermediário só para descartá-lo em seguida.

**Impacto.** São os métodos mais chamados do projeto: o renderizador usa `distanceTo` por objeto
por frame; a fase ampla da física usa `distanceSqTo` por par de corpos por sub-passo (120 Hz).

**Depois.** Cálculo direto sobre os componentes, sem objeto intermediário:

```java
public double distanceSqTo(Vec3 o) {
    double dx = x - o.x, dy = y - o.y, dz = z - o.z;
    return dx * dx + dy * dy + dz * dz;
}
public double distanceTo(Vec3 o) { return Math.sqrt(distanceSqTo(o)); }
```

Mesmo resultado numérico, sem pressão sobre o coletor de lixo.

---

## Limpeza e organização

### 7. Código morto removido

| Local | Item | Situação |
|---|---|---|
| `ui/DrawingCanvas.java` | `inferShapeFromPoints()` (~30 linhas) | Calculava circularidade e razão de aspecto para inferir a forma do traço. Nunca era chamado, porque o modo livre sempre gera malha extrudada. |
| `ui/DrawingCanvas.java` | `resize(double, double)` | Ver bug #3. |
| `ui/simulation/ModulePanel.java` | `shortTabTitle(String)` | Encurtava nomes para abas que deixaram de existir quando o seletor virou lista de botões. |
| `core/Engine.java` | 7 imports de módulos | Usados apenas pela cadeia de `instanceof` eliminada. |

### 8. Documentação técnica no código

Comentários explicativos foram adicionados nas classes centrais, priorizando **o porquê** das
decisões em vez de repetir o que o código já diz:

- **`PhysicsWorld`**: por que passo fixo de 120 Hz produz resultados reprodutíveis; por que existe
  teto de sub-passos ("espiral da morte"); por que a ordem integrar → ambiente → pares importa.
- **`RigidBody`**: por que Euler semi-implícito e não explícito (estabilidade energética em
  órbitas e osciladores); como funciona o sistema de sono; por que o amortecimento é elevado a
  `dt·120` para ficar independente de FPS.
- **`Renderer3D`**: pipeline completo em seis etapas; por que o algoritmo do pintor exige
  ordenação por profundidade na ausência de z-buffer; como o orçamento de triângulos protege o FPS.
- **`Camera`**: por que coordenadas esféricas em vez de XYZ; o que o valor de retorno de
  `projectToScreen` significa.
- **`Engine`**: composição dos subsistemas; ordem das etapas de cada frame; por que a telemetria
  é reconstruída a 1 Hz e não a 60 Hz; como convivem os modos módulo e sandbox.
- **`Vec3`**: contrato de imutabilidade e a razão da otimização nos métodos de distância.

---

## Funcionalidade nova

### 9. Missões de foguete dentro do Sistema Solar

**Arquivo:** `simulation/modules/SolarSystemModule.java`

As missões interplanetárias ficam **no próprio módulo Sistema Solar**, não numa aba separada: a
nave divide a cena e o integrador com os planetas reais, então uma perturbação aplicada ao Sol
afeta também a trajetória do foguete.

- **Modelos de foguete**, seis lançadores reais (Ariane 5, Saturn V, Falcon Heavy, SLS Block 1B,
  Starship, Atlas V 551), cada um com o delta-v heliocêntrico e a carga útil que consegue entregar.
  Se o delta-v não der conta da rota, a missão não sai do planeta e a telemetria diz quanto falta.
- **Janela de lançamento**, o ângulo de fase é calculado pelos períodos orbitais e a nave
  permanece acoplada ao planeta de origem até o alinhamento.
- **Delta-v**, impulso pela equação vis-viva `v = √(μ(2/r − 1/a))`, aplicado tangencialmente.
- **Mira de Lambert**, refina a direção para o ponto onde o destino *estará* na chegada
  (ver correção #13).
- **Visualização**, foguete 3D orientado pelo vetor velocidade, chama do motor, rastro, rota
  prevista desenhada na cena e foco de câmera no foguete.
- **Telemetria**, delta-v exigido e aplicado, tempo de voo previsto × real, ângulo de fase atual
  e necessário, distância ao destino e maior aproximação.

Rotas confirmadas por teste: Terra→Vênus (117 d), Terra→Marte (231 d), Marte→Terra (231 d),
Terra→Júpiter (903 d). Saturno fica fora do alcance de todos os lançadores, correto, a Cassini
precisou de quatro assistências gravitacionais.

---

### 12. "Chegada" falsa na metade da viagem

**Arquivo:** `simulation/modules/SolarSystemModule.java`

**O que acontecia.** A missão Terra→Marte era dada como concluída aos 135 dias, quando a
transferência de Hohmann prevê 259.

**Por que era um problema.** Investigando a trajetória passo a passo, a distância ao alvo caía
monotonicamente de 1,13 AU até 0,10 AU e disparava a condição de chegada. Não era erro de
integração: por volta da metade da transferência a nave e o planeta de destino ficam quase
**alinhados em ângulo**, separados apenas pela diferença de raio (1,35 AU contra 1,50 AU). A
distância mínima do meio do voo é geometria real da transferência, não um encontro. O encontro
verdadeiro acontece no afélio, no fim da elipse.

**Correção.** A verificação de chegada (e a medida de maior aproximação) só valem a partir de 80%
do tempo de voo previsto. O trecho inicial deixou de contar.

---

### 13. Transferência por círculos errava o alvo por 0,25 AU

**Arquivo:** `simulation/modules/SolarSystemModule.java`

**O que acontecia.** Mesmo com a chegada corrigida, a nave passava a 0,254 AU de Marte, longe
demais para caracterizar encontro.

**Por que era um problema.** A transferência de Hohmann clássica pressupõe órbitas **circulares e
coplanares**. As órbitas reais do simulador são elípticas (Marte tem excentricidade 0,093, variando
de 1,38 a 1,67 AU do Sol) e inclinadas. Um impulso puramente tangencial calculado por raios médios
mira onde o planeta estaria num modelo idealizado, não onde ele realmente estará.

**Correção.** Mira de Lambert por tiro (*shooting*): propaga o destino até o instante de chegada e
resolve numericamente a velocidade de partida que leva a nave àquele ponto, com Newton-Raphson
sobre jacobiano por diferenças finitas e busca linear. É o mesmo problema que missões reais
resolvem, mirar onde o planeta *vai estar*.

---

### 14. O solver de mira não convergia (modo nulo da geometria de 180°)

**Arquivo:** `simulation/modules/SolarSystemModule.java`

**O que acontecia.** A mira de Lambert recém-implementada não convergia: o erro estacionava em
0,28 AU e o método desistia, caindo de volta no impulso de Hohmann puro.

**Por que era um problema.** Instrumentando as iterações, o passo de Newton pedia correções de
**0,023 a 0,041 AU/dia**, mais que a própria velocidade orbital da Terra (0,0172 AU/dia). Uma
correção desse tamanho joga a nave em trajetória hiperbólica.

A causa é geométrica: uma transferência de Hohmann liga dois pontos separados por ~180°. Girar a
trajetória em torno do eixo que passa pelo ponto de partida mantém **os dois extremos no lugar** -
ou seja, acrescentar velocidade *fora do plano* não move o ponto de chegada. Essa direção é um
**modo nulo** do problema: o jacobiano 3×3 é singular e o solver tenta corrigir numa direção que
não produz efeito algum.

**Correção.** O ajuste passou a ser feito apenas nas duas direções úteis do plano orbital (radial e
transversal), por mínimos quadrados 2×2, com passo limitado a 0,002 AU/dia e busca linear que só
aceita passos que realmente reduzam o erro. Convergência imediata:

```
[mira] inicio: erro=0,432494 AU
[mira] it=0   erro=0,432494  passo=0,000505
[mira] it=1   erro=0,058470  passo=0,000373
[mira] it=2   erro=0,035795  passo=0,000004
[mira] fim:   erro=0,035742 AU
```

O resíduo de 0,036 AU é exatamente a componente fora do plano, coerente com a inclinação de 1,85°
de Marte, e fisicamente incorrigível com um único impulso nessa geometria. Missões reais gastam
delta-v extra numa manobra dedicada de mudança de plano.

---

### 15. Corredor de aproximação fixo inviabilizava planetas externos

**Arquivo:** `simulation/modules/SolarSystemModule.java`

**O que acontecia.** Com raio de captura fixo em 0,055 AU, Terra→Júpiter sempre falhava.

**Por que era um problema.** O resíduo fora do plano cresce com a distância: em Júpiter (5,2 AU)
chega a ~0,12 AU, contra 0,036 AU em Marte. Mas 0,12 AU em Júpiter é *proporcionalmente*
equivalente a 0,03 AU em Marte. Um raio fixo é generoso demais nos planetas internos e impossível
nos externos, a esfera de influência gravitacional de Júpiter tem 0,32 AU, a de Marte apenas
0,004 AU.

**Correção.** O corredor passou a acompanhar o raio orbital do destino
(`max(0,05; min(0,45; 0,04 × a))`), na mesma ordem de grandeza das esferas de influência reais.
Terra→Júpiter passou a concluir em 903 dias.

---

### 16. Mudar o foco da câmera reiniciava a simulação

**Arquivo:** `simulation/modules/SolarSystemModule.java`

**O que acontecia.** `onParameterChanged` chamava `onReset()` para qualquer parâmetro exceto
`days_per_second`, inclusive `camera_focus`.

**Por que era um problema.** Trocar o alvo da câmera é uma ação puramente visual. Reconstruir a
cena inteira por causa dela sempre foi desperdício; com as missões passou a ser destrutivo: olhar
o foguete de outro ângulo **abortava a viagem em andamento** e devolvia a nave ao planeta de
partida.

**Correção.** O tratamento virou um `switch` explícito: `days_per_second` não faz nada,
`camera_focus` apenas reenquadra a câmera, e só os parâmetros que mudam condições iniciais
(planeta, situação, intensidade, foguete, origem, destino) reconstroem a cena.

---

### 17. O foguete não aparecia na cena

**Arquivo:** `simulation/modules/SolarSystemModule.java`

Quatro causas independentes, todas corrigidas:

**a) A missão vinha desligada.** O valor padrão do parâmetro `Foguete` era `Sem foguete`, então
nenhuma nave era criada até o usuário descobrir o seletor. Pior: com foco de câmera em "Foguete" e
nenhuma nave existindo, a câmera não tinha alvo e ficava parada, dando a impressão de que a opção
não funcionava. O padrão passou a ser **Falcon Heavy, Terra → Marte**, e o foco no foguete cai para
o planeta observado quando não há missão.

**b) Rastro e rota eram descartados pelo modo leve.** O renderizador pula objetos simples cujo raio
aparente fique abaixo de ~1,15 pixel, e o "Modo leve" vem ligado por padrão. Na visão do sistema
inteiro (câmera a 39 unidades) esse limite corresponde a um raio de cerca de 0,07; os marcadores de
rastro tinham 0,045 e os de rota 0,038. **Sumiam.** Agora todos partem de
`ROCKET_MARKER_MIN_RADIUS = 0,085`, com o motivo documentado na constante.

**c) A nave era pequena demais.** O cone tinha raio 0,085 contra 0,27 da Terra: ~4 px na visão do
sistema, colado no planeta de partida. Passou para 0,14 × 0,60 (raio aparente de 6 px) e ganhou um
**sinalizador sempre visível**, um halo azulado enquanto espera, que vira chama alaranjada atrás
do motor durante a queima.

**d) A espera pela janela era longa demais.** Terra → Marte só alinha depois de ~430 dias
simulados: na velocidade padrão, mais de meio minuto olhando a nave parada. Para destinos externos,
muito pior. O módulo agora **adianta o relógio até 12 dias antes da janela** ao montar a cena, o
que preserva a partida como algo visível sem obrigar a esperar. Os rastros dos planetas são
reancorados após o salto para não deixarem um borrão de marcadores na posição antiga.

**Verificação.** Teste automatizado sobre a cena real, com os parâmetros padrão:

```
objetos da nave na cena: 20
nave         raio aparente =  6,00 px  -> VISIVEL
sinalizador  raio aparente =  2,72 px  -> VISIVEL
rastro       raio aparente =  1,70 px  -> VISIVEL
frame 46     em transferencia (dia 0 do voo)    pos=(-4,18, 0,00,  0,93)
frame 589    em transferencia (dia 109 do voo)  pos=( 0,00, 0,00, -4,68)
frame 1190   CHEGOU ao destino no dia 660       pos=( 4,81,-0,15, -1,51)

distancia percorrida na cena: 15,20 unidades
deslocamento liquido: 9,31 unidades
rota prevista desenhada: 48 marcadores
```

O lançamento acontece em menos de um segundo de relógio real e a nave descreve um arco em volta do
Sol, percurso de 15,2 unidades para um deslocamento líquido de 9,3, ou seja, trajetória curva e
não linha reta.

---

### 18. Missão sob comando e foguete com geometria própria

**Arquivos:** `renderer/Mesh.java`, `simulation/modules/SolarSystemModule.java`,
`simulation/SimulationModule.java`, `ui/simulation/ModulePanel.java`, `core/Engine.java`

**O problema.** A missão partia sozinha ao abrir o módulo, atrapalhando quem só queria observar as
órbitas, e a nave era um cone simples, sem leitura de foguete.

**Modelo 3D.** Duas malhas novas em `Mesh`, compartilhando o mesmo sistema de coordenadas (eixo de
simetria em +Y, centradas na origem) para receberem a mesma transformação e ficarem sempre
encaixadas:

| Peça | Geometria | Triângulos |
|---|---|---|
| `createRocketHull` | corpo cilíndrico + bico cônico | 40 |
| `createRocketFins` | 4 aletas com espessura + bocal em sino | 54 |

Ficam em objetos separados para permitir **duas cores sem material por triângulo**: casco branco
azulado e apêndices na cor do modelo do foguete. A silhueta foi proporcionada para continuar
legível com poucos pixels, que é o caso na visão do sistema inteiro.

**Estado `READY`.** A nave nasce montada no planeta de partida e **nada é simulado** até o comando.
O adiantamento do relógio até a janela saiu da construção da cena e passou para o acionamento do
botão.

**Botão de ação genérico.** O `ModulePanel` decidia a visibilidade do botão comparando IDs de
módulo (`"projectile"`, `"collisions"`) e o `Engine` roteava a ação com `instanceof`, a mesma
fragilidade já corrigida na telemetria (item 4). Agora a classe base declara:

```java
public String launchActionLabel() { return null; }   // null esconde o botão
public void launch() {}
```

O painel usa o rótulo que o módulo devolve e o `Engine` faz uma chamada polimórfica. O rótulo do
Sistema Solar reflete o estado da missão: *"Enviar para Marte"* → *"Missão em andamento"* →
*"Nova missão para Marte"*, e avisa *"Delta-v insuficiente"* ou *"Escolha destino diferente"*
quando a rota não é viável.

**Verificação.**

```
casco:  40 triangulos | y de -0,231 a +0,340 | raio max 0,110
aletas: 54 triangulos | y de -0,340 a -0,068 | raio max 0,232

ANTES DO COMANDO
botao: "Enviar para Marte"          estado: PRONTA - aguardando comando de envio
apos 15 s sem comando               estado: PRONTA (nao partiu sozinha)

APOS O COMANDO
botao: "Missao em andamento"
frame   46  em transferencia (dia 0 do voo)
frame 1190  CHEGOU ao destino no dia 660
botao: "Nova missao para Marte"
```

---

### 19. Painel de resultados ilegível e layout sobrecarregado

**Arquivos:** `ui/simulation/TelemetryView.java` (novo), `ui/simulation/ModulePanel.java`,
`resources/br/com/feira/fisica/styles.css`, `core/Engine.java`, os 8 módulos

**O problema.** Os resultados apareciam como um único bloco de texto monoespaçado, no Sistema
Solar chegavam a **40 linhas corridas**, sem hierarquia. Achar um número durante uma apresentação
era impossível. O painel lateral inteiro era uma coluna de mais de 1500 px, com tudo sempre aberto.

**a) Folha de estilos morta.** `styles.css` existia no projeto desde uma versão de tema claro e
**nunca era carregada**, nenhum `getStylesheets().add(...)` no código. Foi reescrita como tema
escuro real e passou a ser carregada pelo `Engine`. Como estilos embutidos (`setStyle`) têm
prioridade sobre a folha em JavaFX, nada do visual existente quebrou: a folha cuida do que não dá
para resolver inline, **barras de rolagem** (que eram claras e destoavam), cabeçalhos de seção
retrátil e as classes do painel de resultados.

**b) Painel de resultados estruturado.** O novo `TelemetryView` transforma o texto em linhas
`rótulo → valor` alinhadas, com títulos de seção e notas em texto menor. Os módulos continuam
produzindo **texto**, formato simples de escrever e o que a validação numérica inspeciona, e a
view cuida só da apresentação, seguindo quatro regras:

| Linha no texto | Vira |
|---|---|
| primeira linha sem `:` | faixa de destaque com o estado atual |
| linha toda em MAIÚSCULAS | título de seção |
| `Rótulo: valor` | linha com rótulo à esquerda e valor à direita |
| linha iniciada por `>` | nota explicativa, em texto menor |

Linhas fora do padrão viram nota, então nada quebra. Valores longos empilham em vez de espremer o
rótulo, e desfechos (chegou, quebrou, insuficiente) ganham destaque de cor.

**c) Seções retráteis.** O painel virou um empilhado de `TitledPane`: *Simulações*, *Parâmetros*,
*Ações* e *Resultados* abertos; *Modo laboratório* e *Referências* recolhidos. O bloco de
referências deixou de ocupar espaço permanente.

**d) Menos informação de uma vez.** A telemetria do Sistema Solar caiu de **40 para 28 linhas**,
dividida em `ORBITA`, `PLANETA`, `MISSAO DE FOGUETE` e `CENARIO`, e o bloco da missão passou a
mostrar **só os campos da fase atual**: antes da partida o custo da rota, em voo a distância que
falta, depois da chegada a comparação previsto × realizado. Constantes do modelo (fórmulas,
unidades) saíram das linhas de dados e viraram notas no fim.

Os oito módulos foram padronizados nesse formato, antes cada um inventava seu próprio arranjo,
misturando fórmulas e medidas na mesma lista.

**e) Ordem dos botões.** Em *Ações*, o botão do módulo (lançar, colidir, enviar foguete) subiu para
o topo: é o mais usado, e ficava abaixo de *Resetar simulação*.

---

### 20. Entrada de valores livre, com limites físicos reais

**Arquivos:** `simulation/parameters/Parameter.java`, `ui/simulation/ModulePanel.java`,
os 8 módulos, `validation/PhysicsValidation.java`

**O problema.** `Parameter.setValue()` travava no intervalo do slider. Digitar 274 num campo cujo
slider ia até 30 aplicava **30**, sem qualquer aviso, o usuário achava que tinha configurado a
gravidade do Sol e estava simulando a da Terra triplicada.

**Dois intervalos, com papéis diferentes.** O parâmetro passou a separar:

| | Papel |
|---|---|
| `range(min, max)` | faixa **recomendada**, o que o slider percorre, os valores típicos |
| `limits(min, max)` | **limites físicos**, a fronteira do que faz sentido simular |

Digitando no campo, o usuário sai da faixa recomendada à vontade: o valor é aceito, o **slider se
estende** para acomodá-lo e uma mensagem explica o que muda. Só os limites físicos são
intransponíveis, massa não é negativa, ângulo de rampa não passa de 90°, velocidade não ultrapassa
a da luz.

**Mensagens com referência real.** Cada parâmetro declara valores reais de comparação, então o
aviso ensina em vez de só reclamar:

```
~ Fora da faixa usual (0,00 a 30,00 m/s2), mas fisicamente valido.
  Referencia: Lua 1,62 | Marte 3,71 | Terra 9,81 | Jupiter 24,79 | Sol 274 m/s2.

! Gravidade nao pode ser menor que 0,00 m/s2. Ajustado para o limite.
```

Alguns avisos vêm do próprio código, não de tabelas externas, o de `days_per_second` sai da
constante `MAX_DAYS_PER_FRAME`: *"acima de 720 d/s o passo por quadro satura em 12 dias e a
simulação não acelera mais"* (12 dias × 60 FPS).

**Tratamento de erro.** Texto não numérico, `NaN` e infinito são recusados com mensagem, sem nunca
chegar ao modelo. `setValue()`, usado por presets e cenários salvos, também passou a respeitar os
limites, então nem por essa via entra valor inválido.

---

### 21. Divergência numérica com parâmetros extremos

**Arquivos:** `simulation/modules/SpringModule.java`, `simulation/modules/PendulumModule.java`

**Como apareceu.** O teste novo de valores extremos (item 20) falhou logo na primeira execução:
`AssertionError: mola extrema series A finita`. Com k = 10⁶ N/m e m = 1 g o deslocamento virava
infinito e depois `NaN`, contaminando gráfico, CSV e cena 3D.

**A causa é matemática, não um erro de digitação.** O Euler semi-implícito só é estável enquanto
`ω·dt < 2`, sendo `ω = √(k/m)` a frequência natural. Nesse caso ω = 31 623 rad/s e um passo de
1/60 s dá `ω·dt ≈ 527`: a amplitude **dobra a cada passo**. O pêndulo tem a mesma restrição com
`ω = √(g/L)`, gravidade solar e fio de 1 mm levam ao mesmo colapso.

**Correção.** Sub-passos adaptativos: cada quadro é dividido em quantos passos forem necessários
para manter `ω·dt ≤ 0,25`, com teto de 4096 subdivisões para não travar o app. Quando nem o teto
resolve, o módulo marca `beyondResolution` e a telemetria diz claramente
*"fora da resolução do integrador"* com a orientação de como sair dali, em vez de exibir números
sem significado. Uma rede de segurança final restaura o estado inicial se algum `NaN` residual
escapar.

**Cobertura nova (item 21).** `validateExtremeValues()` executa sete cenários extremos porém válidos -
gravidade solar caindo do Everest, projétil a 5 km/s dentro d'água, mola rígida com massa de
grama, pêndulo dando volta completa, rampa vertical sem atrito, colisão de projétil contra corpo
50 m e sistema solar a 50 000 dias/s, exigindo em todos que as medidas fiquem finitas e a
telemetria não contenha `NaN` nem `Infinity`.

---

### 22. Texto ilegível nas listas suspensas

**Arquivos:** `resources/br/com/feira/fisica/styles.css`, `ui/simulation/ModulePanel.java`,
`ui/Toolbar.java`

**O que acontecia.** Ao abrir qualquer lista de múltipla escolha (material, meio, planeta, foguete,
escala de tempo, forma 3D), as opções apareciam praticamente invisíveis.

**Por que era um problema.** Os `ComboBox` recebiam `-fx-text-fill: #e5e7eb` por estilo embutido.
A lista que se abre vive num **popup separado**, mas o JavaFX liga esse popup ao ComboBox para
efeito de CSS, então ela **herdava a cor clara do texto** e ao mesmo tempo mantinha o fundo branco
padrão do tema Modena. Texto quase branco sobre fundo branco: contraste em torno de 1:1.

Era invisível justamente porque o estilo embutido resolvia o controle fechado e ignorava o popup.

**Correção.** Duas frentes:

1. A cor do texto saiu dos estilos embutidos e passou para `styles.css`, que trata **os dois**
   lados: o valor exibido no controle fechado (`.combo-box > .list-cell`) e as linhas da lista
   aberta (`.combo-box-popup > .list-view ... > .list-cell`), com estados de foco e seleção.
2. Menus de contexto e dicas de ferramenta, que sofriam do mesmo tema claro herdado, também foram
   para a paleta escura.

**Verificação.** Um teste renderiza o popup com a folha de estilos real e mede o contraste pela
fórmula da WCAG, usando a API de *snapshot* do JavaFX, sem capturar a tela:

```
imagem do popup: 244x179 px
fundo da linha : #0B1223
cor do texto   : #E0F2FD
contraste texto/fundo: 16,26:1
OK: acima de 4,5:1, legivel pelo criterio WCAG AA
```

O critério WCAG AA pede 4,5:1 para texto normal; o resultado fica em 16,26:1.

---

### 23. Materiais com propriedades reais, não constantes inventadas

**Arquivos:** `physics/MaterialState.java`, `simulation/modules/CollisionModule.java`,
`simulation/modules/ProjectileModule.java`, `validation/PhysicsValidation.java`

**O que estava errado.** Só a densidade e o atrito eram valores reais. `restitution`, `hardness` e
`fractureJPerKg` eram constantes arbitradas, e as regras que as combinavam também:

```java
// restituição do par, fórmula sem base física
min(eA, eB) * (0.65 + durezaMedia * 0.35)

// limiar de quebra, tabela fixa, desligada das propriedades
case GLASS -> 0.42;  case STONE -> 1.18;  case STEEL -> 3.40;
```

Isso produzia uma ordenação plausível entre materiais, mas nenhum número correspondia a algo
mensurável, e não havia como verificar se estava certo.

**Agora cada material declara só o que existe em tabela de engenharia:** densidade, módulo de
Young, coeficiente de Poisson, tensão de ruptura, alongamento na ruptura, atrito cinético e o
coeficiente de restituição de ensaio de queda. Todo o resto é **derivado**:

| Antes (arbitrado) | Agora (derivado de) |
|---|---|
| `hardness` 0..1 | módulo de Young em escala logarítmica |
| `fractureJPerKg` | módulo de resiliência `σ²/(2Eρ)` |
| limiar de quebra | alongamento na ruptura (frágil × dúctil) |
| restituição do par | complacência `(1-ν²)/E` de cada material |
| dano no impacto | pressão de Hertz contra a tensão de ruptura |

**Fórmulas empregadas** (todas padrão, citadas no código):

- Módulo de contato efetivo: `1/E* = (1-νA²)/EA + (1-νB²)/EB`
- Pico de pressão no impacto: `p = 1,16 (ρv²)^(1/5) E*^(4/5)`, Johnson, *Contact Mechanics*, 1985
- Restituição do par: média geométrica **ponderada pela complacência**, corrigida por `v^(-1/4)`
  acima da velocidade de ensaio

**Duas decisões que mudaram o resultado.** A primeira versão usava média geométrica simples e um
limiar de escoamento tabelado por material. Os dois estavam errados e o teste mostrou:

1. *Limiar por velocidade* usava o mínimo do par, então o aço, que escoa a partir de ~0,1 m/s -
   derrubava a restituição da borracha junto. Fisicamente ao contrário: numa bola de borracha
   contra placa de aço quem deforma é a borracha, e o módulo baixo dela mantém a pressão abaixo do
   escoamento. Passou a ser critério de tensão, calculado do contato.
2. *Média geométrica simples* inflava pares muito diferentes: gelatina contra aço dava 0,17, puxada
   para cima pelo aço. Ponderando pela complacência, a gelatina domina (peso ≈ 1) e o resultado
   fica em 0,05, como deve ser.

**Verificação contra a bancada.** A referência é a altura de retorno numa queda de 1 m sobre placa
de aço, qualquer um confere com uma régua:

| Material | Simulador | Real |
|---|---|---|
| Borracha | 72 cm | 70–80 cm (superball) |
| Vidro | 41 cm | ~40 cm (bola de gude) |
| Aço | 36 cm | ~36 cm (e ≈ 0,6, esfera de rolamento) |
| Pedra | 31 cm | - |
| Madeira | 25 cm | - |
| Espuma | 4 cm | - |
| Gelatina | 0,25 cm | não quica |

`validateMaterials()` fixa esses valores mais o modo de falha, a queda da restituição com a
velocidade, o amolecimento do contato pela espuma e a faixa física de cada propriedade.

**Limite honesto.** O motor trata os corpos como esferas rígidas e resolve o impacto por impulso.
Isso reproduz bem quanto de energia se perde, como um material se compara a outro e se a peça
resiste ou rompe, mas **não substitui elementos finitos**: não há deformação real da malha,
propagação de trinca, nem efeito de temperatura ou taxa de deformação.

---

### 24. Colisão criando energia com massas muito diferentes

**Arquivo:** `simulation/modules/CollisionModule.java`

**Como apareceu.** Ao rodar a validação com o modelo de materiais novo, o cenário extremo falhou:
`AssertionError: colisao extrema: energia nao pode crescer`. A sonda mostrou o tamanho do problema:

```
energia inicial: 6,250e+05 J
frame 0    antes=6,250e+05  depois=8,516e+08  razao=1362,5
```

**A causa é anterior a esta versão**, o teste apenas a expôs. Em `materialCollisionReaction`, a
parcela lateral do movimento não era escalada pela razão de massas:

```java
Vec3 lateral = new Vec3(v.x, 0, v.z).mul(0.12 * response);   // sem razão de massas
```

Um projétil de 0,05 kg sacudia lateralmente um corpo de 5,5 × 10⁸ kg como se as inércias fossem
parecidas. A conservação de momento dizia 4,5 × 10⁻⁷ m/s; o corpo saía a 1,76 m/s, energia criada
do nada. Com massas parecidas o erro passava despercebido; a entrada livre de valores (item 20)
tornou o caso alcançável pelo usuário.

**Correção.** Duas camadas:

1. A parcela lateral passou a ser escalada pela razão de massas, como já era a parcela normal.
2. `enforceEnergyBudget()` transformou a conservação em **invariante do módulo**: se a energia
   pós-impacto superar a pré-impacto, as duas velocidades são reduzidas na mesma proporção. A etapa
   de resposta do material molda *como* cada corpo reage, mas é redistribuição, nunca fonte.

---

### 25. Empuxo não dependia do corpo: madeira afundava igual ao aço

**Arquivos:** `physics/MediumState.java`, `simulation/modules/ProjectileModule.java`

**O que acontecia.** O meio tinha um `gravityFactor` constante, água = 0,72, aplicado a qualquer
projétil:

```java
context.setGravityStrength(gravidade * medium.gravityFactor);   // 0,72 para todo mundo
```

**Por que era um problema.** O empuxo de Arquimedes depende da densidade **do corpo**, não do meio
sozinho: `g_efetivo = g (1 − ρ_meio/ρ_corpo)`. Com um fator fixo, uma esfera de madeira e uma de aço
afundavam na água exatamente com a mesma desaceleração. Madeira (550 kg/m³) em água (998 kg/m³)
deveria **subir**, o fator real é negativo (−0,81).

**Correção.** `buoyancyFactorFor(densidadeDoCorpo)` implementa Arquimedes, e o módulo de projéteis
passa a densidade do material escolhido. O resultado sai como se espera:

| Material | Vácuo | Ar | Água | Óleo |
|---|---|---|---|---|
| Aço | +1,00 | +1,00 | +0,87 | +0,89 |
| Pedra | +1,00 | +1,00 | +0,62 | +0,67 |
| Borracha | +1,00 | +1,00 | +0,09 | +0,21 |
| **Madeira** | +1,00 | +1,00 | **−0,81** (flutua) | **−0,58** (flutua) |
| **Espuma** | +1,00 | +0,96 | **−32,3** (flutua) | **−28,0** (flutua) |

---

### 26. Arrasto com fator inventado, sem viscosidade nem Reynolds

**Arquivo:** `physics/MediumState.java`

**O que acontecia.** A força de arrasto usava um `Cd` fixo multiplicado por um `dragScale`
adimensional arbitrado por meio (ar 0,018, água 0,160, gel 0,420). O meio nem declarava viscosidade,
então não havia como distinguir regime viscoso de regime inercial.

**Correção.** O meio passou a declarar **viscosidade dinâmica** e **velocidade do som** (valores de
tabela a 20 °C), e o coeficiente de arrasto virou função do **número de Reynolds**
`Re = ρvD/μ`:

| Faixa de Re | Regime | Cd |
|---|---|---|
| < 1 | Stokes (viscoso puro) | 24/Re |
| 1 – 10³ | transição | Schiller–Naumann |
| 10³ – 2×10⁵ | Newton | ≈ 0,44 |
| 2×10⁵ – 4×10⁵ | crise do arrasto | cai para 0,18 |
| > 4×10⁵ | supercrítico | ≈ 0,18 |

Somou-se a **divergência transônica**: entre Mach 0,8 e 1,2 o arrasto quase dobra, é o efeito que
torna cara a passagem da barreira do som, e o simulador permite digitar velocidades supersônicas.

Velocidades terminais que saem do modelo (esfera de 22 cm): aço no ar 357 m/s, aço na água
14,8 m/s, espuma no ar 27,4 m/s, madeira e espuma na água flutuam.

---

### 27. Atrito dos materiais nunca era usado nas colisões

**Arquivo:** `simulation/modules/CollisionModule.java`

**O que acontecia.** Cada material declara seu coeficiente de atrito cinético, mas
`resolveMaterialImpact()` aplicava **apenas o impulso normal**. O atrito só chegava ao `RigidBody`,
que o usa para deslizamento no chão, nunca no impacto entre os corpos.

**Por que era um problema.** Numa colisão de raspão (o parâmetro `Offset lateral` existe exatamente
para isso), borracha (μ = 0,80) e vidro (μ = 0,40) se comportavam de forma idêntica. E nenhum
impacto gerava rotação, embora seja o atrito tangencial que faz uma bola girar ao bater de lado.

**Correção.** Impulso tangencial de Coulomb aplicado após o normal: opõe-se ao deslizamento e é
limitado a `μ × |impulso normal|`, com μ do par pela média geométrica. Por ser limitado e sempre
contrário ao movimento relativo, só pode **retirar** energia. Parte do impulso vira rotação, com as
esferas girando em sentidos opostos.

`validateCollisionFriction()` exige que a borracha desvie mais que o vidro numa colisão de raspão e
que, para os sete materiais, a energia nunca cresça.

---

## Caça a bugs dirigida

Três varreduras automatizadas, cada uma cobrindo uma camada, com o objetivo de encontrar falhas
antes do usuário. Resultado: **três bugs reais**, todos corrigidos.

| Varredura | Cobertura |
|---|---|
| **Módulos** | Os 8 módulos: ciclo de vida completo, cada parâmetro nos extremos do limite físico e em valores absurdos (`NaN`, ±10⁹), 12 resets seguidos, todos os presets do catálogo |
| **Engine** | Persistência de cena (ida e volta + arquivo corrompido), 360 combinações da fábrica de objetos, 5 desenhos degenerados, 120 corpos com explosões, câmera em posições extremas, integridade das 11 malhas |
| **Interface** | Troca de módulo ×32, telemetria dos 8 módulos + 11 entradas degeneradas, 10 campos × 15 entradas de texto, gráfico, canvas de desenho, 8 larguras de painel |

---

### 28. Mola e pêndulo explodiam com amortecimento alto

**Arquivos:** `simulation/modules/SpringModule.java`, `simulation/modules/PendulumModule.java`

**Como apareceu.** A varredura de módulos acusou telemetria com `NaN` na mola para
`damping = 10⁶`, `10⁹` e `NaN`.

**A causa foi uma correção incompleta minha.** Ao adicionar os sub-passos adaptativos (item 21), a
condição de estabilidade considerava **só** a frequência de oscilação `ω = √(k/m)`. Faltava a
segunda escala de tempo: a **taxa de amortecimento** `c/m`. Com mola mole e amortecimento alto,
`ω` fica pequeno, o método concluía que um passo bastava, e o termo `−c·v/m` divergia em poucos
quadros.

**Correção.** O passo passa a ser limitado pela **mais rápida das duas escalas**:
`max(ω, c/m)`. Mesma correção aplicada ao pêndulo, que tem a mesma estrutura.

---

### 29. `dt` inválido matava a física em silêncio

**Arquivo:** `physics/PhysicsWorld.java`

**O que acontecia.** `update(dt)` somava o tempo ao acumulador sem validar. Com `dt = NaN`, o
acumulador virava `NaN`, e como `NaN >= FIXED_DT` é sempre falso, o laço de integração **parava
para sempre**. A física morria sem exceção, sem log, sem nada na tela: os objetos simplesmente
congelavam. Com `dt` negativo, o acumulador ficava devedor e a simulação travava até se recuperar.

**Correção.** `dt` não finito ou não positivo é ignorado logo na entrada.

---

### 30. Uma amostra inválida apagava o gráfico inteiro

**Arquivo:** `ui/LabGraphCanvas.java`

**O que acontecia.** O cálculo da escala usa `Math.min`/`Math.max` sobre todas as amostras, e
`Math.min(x, NaN)` devolve `NaN`. Uma única amostra não finita fazia `range.min` e `range.max`
virarem `NaN`, e a partir daí **todos** os pontos caíam fora da área de desenho: gráfico em branco,
sem nenhum erro visível. O CSV exportado saía com `NaN`, quebrando a importação em planilha.

**Correção.** Amostras não finitas são recusadas na entrada, um valor que não é número não é
medida. Verificado: 4 amostras inválidas seguidas são descartadas, a próxima válida entra
normalmente e o CSV sai limpo.

---

## Desenho livre em volume

> O funcionamento completo, algoritmos, fórmulas de volume, decisões de projeto e limites
> conhecidos, está em [`DESENHO_3D.md`](DESENHO_3D.md). Aqui ficam só as correções.

### 31. O desenho livre não gerava sólido, só recorte com espessura

**Arquivos:** `renderer/Mesh.java`, `scene/ObjectFactory.java`, `core/Engine.java`

**O que havia.** `createExtrudedDrawing` pegava o contorno e dava espessura constante, um
recorte de chapa, como cortador de biscoito. Um círculo desenhado virava um disco chato, não uma
esfera. Não era geometria 3D no sentido de volume.

**Dois modos novos**, escolhidos num seletor na aba *Desenhar*:

**Inflar (volume).** O contorno é reamostrado em 44 pontos e encolhido em anéis concêntricos até o
centro. Cada anel recebe altura com perfil circular, `h = espessura · √(1 − (k/K)²)`, então a
superfície sai abaulada no meio e encosta em zero exatamente na borda desenhada. Espelhando para
baixo, as duas metades se encontram no contorno e fecham o sólido sem costura. Funciona para
qualquer contorno visível a partir do centroide, que é o caso de praticamente todo desenho à mão.

**Revolução (torno).** Cada ponto do traço vira um anel de raio igual à sua distância ao eixo, com
28 passos angulares. O eixo é a borda esquerda do desenho, girar em torno do centro faria o sólido
furar a si mesmo, já que os dois lados do traço se sobreporiam. Tampas fecham as duas pontas.

**Massa correta por modo.** Cada geometria tem seu volume: o inflado usa área × espessura × 4/3
(sólido abaulado), e a revolução integra troncos de cone `π·dy·(r₁² + r₁r₂ + r₂²)/3` ao longo do
perfil. O sólido inflado pesa mais que a placa equivalente porque tem mais matéria.

---

### 32. Anel degenerado deixava a malha inflada aberta

**Arquivo:** `renderer/Mesh.java`

**Como apareceu.** O teste de integridade acusou **178 arestas abertas** no sólido inflado. Malha
aberta quebra o sombreamento e as sombras planares.

Duas causas, encontradas em sequência:

1. **`-0.0` contra `+0.0`.** No anel externo a altura é zero, e a face de baixo usava `-height`,
   produzindo `-0.0`. Geometricamente é o mesmo ponto, mas a solda entre as metades deixava de ser
   exata. Corrigido normalizando o sinal quando a altura é zero. Caiu para 90 arestas abertas.
2. **Anel central colapsado.** O anel `k = 0` tinha encolhimento zero, o que colapsava os 44 pontos
   no próprio ápice, 44 triângulos de área nula, mais um leque redundante sobre eles. Esses
   triângulos degenerados é que deixavam as arestas soltas. Os anéis passaram a começar em `k = 1`,
   com o ápice ligado diretamente ao primeiro anel real.

Resultado: **zero arestas abertas** nos três modos, com toda aresta compartilhada por exatamente
duas faces.

---

### 33. Desenhar ficava progressivamente mais lento até travar

**Arquivo:** `ui/DrawingCanvas.java`

**O sintoma relatado:** o app trava ao usar o desenho livre.

**A investigação.** Medi as três fases suspeitas e eliminei duas:

| Fase | Resultado |
|---|---|
| Geração da malha | 5–27 ms mesmo com rabisco auto-interceptado de 1500 pontos, estrela de 150 pontas ou 12 traços separados |
| Renderização | 95–160 FPS mesmo com 8 objetos e 314 000 triângulos (o orçamento de triângulos protege) |
| **Captura do desenho** | **culpada** |

**A causa.** `onMouseDragged` chamava `repaint()` a cada ponto, e `repaint()` redesenha o fundo,
a grade e **todos os traços já concluídos**. O custo por evento de mouse era proporcional ao que já
havia na tela, comportamento quadrático no total de pontos. Medido:

| Desenho | Antes | Por ponto |
|---|---|---|
| 6 traços × 400 pontos | 192 ms | 0,080 ms |
| 15 traços × 400 pontos | 1002 ms | 0,167 ms |

O tempo por ponto **dobrava**: cada traço novo ficava mais lento que o anterior, até a interface
parar de responder.

**Correção.** No modo livre o traço passou a ser desenhado **incrementalmente**, apenas o segmento
novo vai para o canvas, O(1) por evento. As formas geométricas (retângulo, círculo, triângulo)
mantêm a repintura completa, porque mudam inteiras a cada movimento e são definidas por só dois
pontos. Somou-se um filtro de espaçamento mínimo de 2 px: pontos colados não mudam a forma, apenas
inflam a malha e o custo.

| Desenho | Antes | Depois | Ganho |
|---|---|---|---|
| 1 traço × 3000 pontos | 538 ms | 18 ms | 30× |
| 15 traços × 400 pontos | 1002 ms | 36 ms | 28× |

O custo por ponto passou a ser **constante** em 0,006 ms, em vez de crescer.

---

### 34. Ciclo de layout congelava o app na aba Desenhar

**Arquivo:** `core/Engine.java`

**O sintoma relatado:** o app trava e o terminal fica rodando infinitamente.

**A investigação.** O item 33 melhorou a fluidez, mas não explicava um congelamento com saída
infinita no terminal. Eliminei, medindo, três suspeitos:

| Suspeito | Veredito |
|---|---|
| Geração da malha | 5–27 ms nos piores casos, inocente |
| Física + render, 900 quadros com 6 objetos desenhados | zero exceções, inocente |
| Laços de `Mesh` (triangulação, limpeza de contorno, reamostragem) | todos limitados, inocente |

Restava a interface. Um teste que replica a aba *Desenhar* e conta redimensionamentos do canvas
por quadro deu o veredito:

```
apos  60 quadros:  63 redimensionamentos (+63 no ultimo segundo)
apos 120 quadros: 123 redimensionamentos (+60 no ultimo segundo)
apos 180 quadros: 183 redimensionamentos (+60 no ultimo segundo)

java.lang.NullPointerException: Cannot invoke "com.sun.prism.RTTexture.createGraphics()"
    at com.sun.javafx.sg.prism.NGCanvas$RenderBuf.validate(NGCanvas.java:214)
    at com.sun.javafx.sg.prism.NGCanvas.initCanvas(NGCanvas.java:644)
```

**Sessenta redimensionamentos por segundo, indefinidamente**, um por quadro. E a cada tentativa o
JavaFX falhava ao validar o buffer de render do canvas, lançando `NullPointerException`. Como o
JavaFX imprime a exceção e segue para o próximo pulso, o terminal enchia sem parar e a interface
deixava de responder. Os dois sintomas relatados, com a mesma causa.

**A causa: ciclo de layout.** O canvas tinha o tamanho amarrado ao do próprio pai:

```java
drawCanvas.heightProperty().bind(
    Bindings.max(170, drawTabContent.heightProperty().subtract(170)));
drawTabContent.getChildren().addAll(..., drawCanvas);   // o canvas é filho da VBox
```

A altura da VBox é calculada a partir dos filhos, e o canvas era um deles. Redimensionar o canvas
mudava a VBox, que redimensionava o canvas de novo, sem convergir. Acrescentar o seletor de modo
de sólido (item 31) empurrou o layout para a faixa em que a oscilação não se estabiliza.

**Correção.** O canvas passou a ficar dentro de um suporte com tamanho preferido **fixo**:

```java
Pane canvasHolder = new Pane(drawCanvas) {
    @Override protected double computePrefHeight(double width) { return 170; }
    // ...
};
drawCanvas.heightProperty().bind(canvasHolder.heightProperty());
VBox.setVgrow(canvasHolder, Priority.ALWAYS);
```

Como o suporte não consulta o canvas para se dimensionar, o ciclo deixa de existir: ele recebe o
espaço que sobra via `VGROW` e o canvas segue o tamanho já resolvido.

**Verificação.** De 60 redimensionamentos por segundo para **4 na montagem inicial e zero depois**.
Executando o app real por 25 s: **0 byte em stderr** e 9,9% de CPU.

---

### 35. Metade dos experimentos não tinha como ser iniciada

**Arquivos:** os 8 módulos, `ui/simulation/ModulePanel.java`, `validation/PhysicsValidation.java`

**O sintoma relatado:** não dá para iniciar as simulações, só aparece o botão de reset e nada
acontece.

**A investigação.** Um teste que percorre os oito módulos, lê o rótulo do botão de ação, verifica se
ele aparece e dispara o clique mostrou o quadro:

```
MODULO           ROTULO DO BOTAO         APARECE   SIMULA?
free_fall        (nenhum)                nao       SIM (automatico)
projectile       Executar lancamento     sim       apos o clique
collisions       Iniciar colisao         sim       apos o clique
inclined_plane   (nenhum)                nao       SIM (automatico)
pendulum         (nenhum)                nao       SIM (automatico)
spring           (nenhum)                nao       SIM (automatico)
solar_system     Enviar para Marte       sim       apos o clique
tug_of_war       (nenhum)                nao       -
```

**Dois problemas, um deles de projeto.**

**a) Metade dos módulos começava sozinha.** Queda livre, plano inclinado, pêndulo e mola disparavam
no instante em que o módulo abria. Como a queda livre é o módulo padrão, a pessoa abria o app e a
bola já estava no chão: nada para ver, e nenhum botão para repetir além de "Resetar simulação", que
não se lê como "iniciar". O relato descreve exatamente isso.

**Correção.** Os quatro passaram a esperar o comando, como projéteis e colisões já faziam. O objeto
fica suspenso, o bloco fica retido no alto da rampa, o pêndulo fica na amplitude inicial e a mola
fica esticada até o clique. Além de resolver o sintoma, isso espelha o experimento real: ajusta-se
tudo primeiro e só depois se solta.

| Módulo | Botão |
|---|---|
| Queda Livre | Soltar o objeto |
| Plano Inclinado | Soltar o bloco |
| Pêndulo | Soltar o pêndulo |
| Mola | Soltar a mola |
| Cabo de Guerra | Reiniciar partida |

Depois do primeiro uso o rótulo vira "Soltar novamente", e o clique reinicia o experimento.

**b) O rótulo do botão ficava travado.** A visibilidade era atualizada a cada troca de módulo, mas
o texto só quando o novo rótulo não era nulo:

```java
if (actionLabel != null) btnLaunch.setText(actionLabel);
```

Saindo de Colisões para o Pêndulo, o botão continuava escrito "Iniciar colisao", escondido. O texto
reaparecia errado na troca seguinte. Passou a ser sempre reescrito.

**Efeito na validação.** Como os módulos agora esperam o comando, a bateria de testes precisou
chamar `launch()` depois de `onActivate()`, em 11 pontos. A mudança deixa os testes mais fiéis ao
uso real: antes eles exercitavam um caminho que o usuário não tinha como acionar.

### 10. Distribuição portátil para Windows

**Arquivos:** `build.gradle.kts`, `src/winDist/` (novo)

Antes só existiam alvos Linux e macOS. O `build.gradle.kts` ganhou o conceito de pasta de
lançadores por alvo (`launcherDir`), permitindo scripts diferentes por plataforma:

```kotlin
data class PortableTarget(val id: String, val classifier: String, val launcherDir: String)

val portableTargets = listOf(
    PortableTarget("windows-x64",   "win",           "src/winDist"),
    PortableTarget("linux-x64",     "linux",         "src/linuxDist"),
    PortableTarget("linux-aarch64", "linux-aarch64", "src/linuxDist"),
    PortableTarget("mac-x64",       "mac",           "src/linuxDist"),
    PortableTarget("mac-aarch64",   "mac-aarch64",   "src/linuxDist")
)
```

O lançador `SimulaFisica3D.bat` detecta Java no PATH, aceita JDK portátil na pasta `runtime`,
monta o classpath excluindo os jars do JavaFX (que entram por `--module-path`, evitando erro de
*split-package*) e repassa argumentos de linha de comando.

### 11. Lançador Linux não repassava argumentos

**Arquivo:** `src/linuxDist/SimulaFisica3D.sh`

**O que acontecia.** A chamada final terminava em `engine.Main`, sem `"$@"`.

**Por que era um problema.** Argumentos passados ao script eram silenciosamente descartados.
`./SimulaFisica3D.sh --module=solar_system` abria o módulo padrão, sem qualquer aviso, o recurso
de abrir um experimento específico simplesmente não existia na versão Linux, embora funcionasse
na Windows.

**Correção.** Acréscimo de `"$@"` na chamada, igualando o comportamento das duas plataformas. Os
leia-me de ambas as distribuições agora documentam o uso e listam os nove IDs de módulo.

---

## Documentação

| Documento | Estado |
|---|---|
| `README.md` | Reescrito do zero: visão geral, os 8 experimentos, missões de foguete com tabela de lançadores e rotas, modo laboratório, sandbox, controles, funcionamento do motor, distribuições, validação, estrutura e guia de extensão |
| `ARCHITECTURE.md` | Árvore de módulos atualizada com os 8 experimentos |
| `docs/APRESENTACAO.html` | Apresentação em 8 slides navegáveis |
| `docs/CORRECOES.md` | Este documento |

Correções pontuais no README anterior: referência a um `run.sh` que não existe no projeto e lista
de módulos desatualizada.

---

## Como verificar

```bash
# Compila e roda todas as validações numéricas
./gradlew validatePhysics

# Build completo
./gradlew clean build

# Gera as duas distribuições
./gradlew portableZip_windows-x64 portableZip_linux-x64
```

Resultado esperado da validação:

```
> Task :validatePhysics
OK: validacoes fisicas passaram.

BUILD SUCCESSFUL
```

As verificações cobrem queda livre, projétil (vácuo, água e impacto em vidro), colisões, plano
inclinado, pêndulo, mola, sistema solar, missões de foguete (quatro rotas mais o caso de delta-v
insuficiente) e cabo de guerra.
