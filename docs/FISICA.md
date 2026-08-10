# A física por dentro

Detalhamento dos modelos físicos do simulador: materiais, meios, limites de parâmetro e
estabilidade numérica. O [README](../README.md) traz o resumo; aqui estão as fórmulas, as fontes e
as verificações.

---

## Índice

1. [Materiais](#materiais)
2. [Meios: arrasto e empuxo](#meios-arrasto-e-empuxo)
3. [Entrada de valores e limites físicos](#entrada-de-valores-e-limites-físicos)
4. [Estabilidade numérica](#estabilidade-numérica)
5. [Referências](#referências)

---

## Materiais

Sete materiais, descritos **apenas por propriedades que existem em tabela de engenharia**.
Tudo o mais é derivado, não arbitrado.

### O que cada material declara

| Material | ρ (kg/m³) | E | Ruptura | Along. | e (ensaio) | μ | Modo de falha |
|---|---|---|---|---|---|---|---|
| Gelatina | 1050 | 10 kPa | 12 kPa | 60% | 0,05 | 0,60 | dúctil |
| Espuma (EPS) | 30 | 5 MPa | 250 kPa | 5% | 0,20 | 0,50 | dúctil |
| Borracha | 1100 | 20 MPa | 20 MPa | 500% | 0,85 | 0,80 | elastomérico |
| Madeira | 550 | 11 GPa | 40 MPa | 1% | 0,50 | 0,40 | frágil |
| Vidro | 2500 | 70 GPa | 50 MPa | 0,1% | 0,65 | 0,40 | frágil |
| Pedra (granito) | 2650 | 50 GPa | 15 MPa | 0,1% | 0,55 | 0,60 | frágil |
| Aço | 7850 | 200 GPa | 400 MPa | 16% | 0,60 | 0,42 | dúctil |

`ρ` massa específica · `E` módulo de Young · `Along.` alongamento na ruptura ·
`e` coeficiente de restituição de ensaio de queda · `μ` atrito cinético

### O que é calculado

| Grandeza | Vem de |
|---|---|
| Dureza relativa | módulo de Young em escala logarítmica |
| Energia de fratura | módulo de resiliência `σ²/(2Eρ)` |
| Limiar de quebra | alongamento na ruptura (frágil × dúctil) |
| Restituição do par | complacência `(1−ν²)/E` de cada material |
| Dano no impacto | pressão de Hertz contra a tensão de ruptura |

### As fórmulas

**Módulo de contato efetivo** (Hertz):

```
1/E* = (1−ν_A²)/E_A + (1−ν_B²)/E_B
```

O termo mais flexível domina a soma, por isso aço contra espuma é governado pela espuma.

**Pico de pressão no impacto de esfera** (Johnson, *Contact Mechanics*, 1985):

```
p = 1,16 · (ρ v²)^(1/5) · E*^(4/5)
```

Comparar essa pressão com a tensão de ruptura diz se o material rompe. É critério de **tensão**,
não de energia total: uma pedra não estilhaça pela energia do conjunto, mas pela tensão concentrada
na região de contato.

**Módulo de resiliência.** Energia elástica armazenada até a ruptura, por unidade de massa:

```
U = σ² / (2 E ρ)
```

| Material | U (J/kg) |
|---|---|
| Borracha | 9091 |
| Espuma | 208 |
| Madeira | 132 |
| Aço | 51 |
| Vidro | 7,1 |
| Gelatina | 6,9 |
| Pedra | 0,8 |

É essa grandeza que explica por que borracha quica e pedra estilhaça.

### O par importa

A restituição **não é propriedade de um corpo isolado**: quem deforma dita a perda. Numa esfera de
gelatina contra placa de aço, praticamente toda a deformação acontece na gelatina, e o resultado
tem que ficar junto do valor dela, não no meio do caminho.

O peso de cada material é sua fração da complacência total do contato:

```
w_A = [(1−ν_A²)/E_A] / [(1−ν_A²)/E_A + (1−ν_B²)/E_B]
e_par = e_A^(w_A) · e_B^(1−w_A)
```

> **Por que não a média geométrica simples.** A primeira versão usava `√(e_A · e_B)`. Para
> gelatina contra aço isso dava 0,17, inflado pelo aço. Ponderando pela complacência, a gelatina
> domina (peso ≈ 1) e o resultado fica em 0,05, como deve ser.

### A velocidade importa

Acima da velocidade de ensaio (5 m/s, queda de ~1,3 m), **se** a pressão de contato superar a
resistência do material mais fraco, parte da energia vira deformação permanente:

```
e(v) = e_par · (5 / v)^(1/4)
```

O expoente −1/4 é o resultado clássico do impacto elasto-plástico.

> **Por que critério de tensão e não velocidade tabelada.** A primeira versão usava um limiar de
> escoamento por material e tomava o mínimo do par. O aço escoa a partir de ~0,1 m/s, então
> derrubava a restituição da borracha junto, fisicamente ao contrário, já que numa bola de
> borracha contra aço quem cede é a borracha, e o módulo baixo dela mantém a pressão abaixo do
> escoamento.

### Atrito na colisão

Numa batida de raspão (parâmetro *Offset lateral*) atua um impulso tangencial de Coulomb:

```
J_t ≤ μ_par · |J_n|,    μ_par = √(μ_A · μ_B)
```

Por ser limitado e sempre contrário ao movimento relativo tangencial, só pode **retirar** energia.
Parte do impulso vira rotação, com as esferas girando em sentidos opostos.

### Confira com uma régua

Altura de retorno numa queda de 1 m sobre placa de aço:

| Material | Simulador | Real |
|---|---|---|
| Borracha | 72 cm | 70–80 cm (superball) |
| Vidro | 41 cm | ~40 cm (bola de gude) |
| Aço | 36 cm | ~36 cm (esfera de rolamento, e ≈ 0,6) |
| Pedra | 31 cm | - |
| Madeira | 25 cm | - |
| Espuma | 4 cm | - |
| Gelatina | 0,25 cm | não quica |

---

## Meios: arrasto e empuxo

Cinco meios, declarando **massa específica, viscosidade dinâmica e velocidade do som**
(valores a 20 °C).

| Meio | ρ (kg/m³) | μ (Pa·s) | Som (m/s) |
|---|---|---|---|
| Vácuo | 0 | 0 | - |
| Ar | 1,225 | 1,81 × 10⁻⁵ | 343 |
| Água | 998 | 1,00 × 10⁻³ | 1481 |
| Óleo | 870 | 0,170 | 1450 |
| Gel | 1040 | 5,000 | 1500 |

### Empuxo (Arquimedes)

```
g_efetivo = g · (1 − ρ_meio / ρ_corpo)
```

Depende da densidade **do corpo**, não do meio sozinho. Fator negativo significa que o corpo sobe.

| Material | Vácuo | Ar | Água | Óleo |
|---|---|---|---|---|
| Aço | +1,00 | +1,00 | +0,87 | +0,89 |
| Pedra | +1,00 | +1,00 | +0,62 | +0,67 |
| Borracha | +1,00 | +1,00 | +0,09 | +0,21 |
| **Madeira** | +1,00 | +1,00 | **−0,81** ↑ | **−0,58** ↑ |
| **Espuma** | +1,00 | +0,96 | **−32,3** ↑ | **−28,0** ↑ |

↑ = flutua

> Um fator fixo por meio, como havia antes, fazia madeira e aço afundarem exatamente igual.

### Arrasto pelo número de Reynolds

```
Re = ρ v D / μ         F = ½ · C_d · ρ · A · v²
```

| Faixa de Re | Regime | C_d |
|---|---|---|
| < 1 | Stokes (viscoso puro) | 24/Re |
| 1 – 10³ | transição | Schiller–Naumann |
| 10³ – 2×10⁵ | Newton | ≈ 0,44 |
| 2×10⁵ – 4×10⁵ | crise do arrasto | cai para 0,18 |
| > 4×10⁵ | supercrítico | ≈ 0,18 |

**Divergência transônica:** entre Mach 0,8 e 1,2 o arrasto quase dobra. É o efeito que torna cara a
passagem da barreira do som.

### Velocidades terminais (esfera de 22 cm)

| Material | Ar | Água |
|---|---|---|
| Aço | 357 m/s | 14,8 m/s |
| Madeira | 120 m/s | flutua |
| Espuma | 27,4 m/s | flutua |

---

## Entrada de valores e limites físicos

Cada parâmetro tem **dois intervalos**, com papéis diferentes:

- **Faixa recomendada:** o que o slider percorre, ou seja, os valores típicos do experimento.
- **Limites físicos:** a fronteira do que faz sentido simular.

Digitando no campo de texto você sai da faixa recomendada à vontade: o valor é aceito, o slider se
estende e uma mensagem explica o que muda, citando referências reais.

```
~ Fora da faixa usual (0,00 a 30,00 m/s2), mas fisicamente valido.
  Referencia: Lua 1,62 | Marte 3,71 | Terra 9,81 | Jupiter 24,79 | Sol 274 m/s2.

! Gravidade nao pode ser menor que 0,00 m/s2. Ajustado para o limite.
```

| Grandeza | Limite | Motivo |
|---|---|---|
| Massa, comprimento, raio, rigidez | > 0 | não existem valores nulos ou negativos |
| Ângulo de rampa | 0 a 90° | fora disso não é uma rampa |
| Amplitude do pêndulo | 0 a 180° | acima de 90° só uma haste rígida sustenta |
| Velocidade | até 3,00 × 10⁸ m/s | a da luz; acima de 10% dela a mecânica newtoniana já não vale |
| Atrito | 0 a 5 | acima de 1 é raro, mas existe (borracha macia ≈ 1,2) |

Texto não numérico, `NaN` e infinito são recusados com mensagem e nunca chegam ao modelo -
inclusive por presets e cenários salvos.

Alguns avisos saem do próprio código: o de `dias por segundo` vem da constante
`MAX_DAYS_PER_FRAME`, *"acima de 720 d/s o passo por quadro satura em 12 dias e a simulação não
acelera mais"* (12 dias × 60 FPS).

---

## Estabilidade numérica

O Euler semi-implícito só é estável enquanto `ω·dt < 2`. Combinações válidas mas extremas violam
isso: uma mola de 10⁶ N/m com massa de 1 g dá `ω = 31 623 rad/s`, e um passo de 1/60 s daria
`ω·dt ≈ 527`. A amplitude **dobra a cada passo** até virar infinito e depois `NaN`.

**Sub-passos adaptativos.** Os módulos de mola e pêndulo dividem cada quadro em quantos passos
forem necessários para manter `ω·dt ≤ 0,25`, com teto de 4096 subdivisões:

```
mola:     ω = √(k/m)      taxa de amortecimento = c/m
pêndulo:  ω = √(g/L)      taxa de amortecimento = c
passo máximo = 0,25 / max(ω, taxa)
```

> **Duas escalas de tempo, não uma.** A primeira versão considerava só a frequência de oscilação.
> Com mola mole e amortecimento alto, `ω` fica pequeno, o método concluía que um passo bastava, e o
> termo `−c·v/m` divergia. O passo precisa ser limitado pela **mais rápida das duas escalas**.

Quando nem o teto de subdivisões resolve, o módulo marca `beyondResolution` e a telemetria diz
*"fora da resolução do integrador"* com a orientação de como sair dali, em vez de exibir números
sem significado.

---

## Referências

**Física e materiais**
- Halliday, D.; Resnick, R.; Walker, J. *Fundamentos de Física*
- Serway, R. A.; Jewett, J. W. *Princípios de Física*
- Callister, W. D. *Ciência e Engenharia de Materiais*
- Ashby, M. F. *Materials Selection in Mechanical Design*
- Johnson, K. L. *Contact Mechanics*, 1985, pressão de contato e impacto elasto-plástico
- Baraff, D.; Witkin, A. *Physically Based Modeling: Principles and Practice*
- Ericson, C. *Real-Time Collision Detection*
- White, F. M. *Fluid Mechanics*, regimes de arrasto e crise do arrasto
- Schiller, L.; Naumann, A. (1935), correlação de arrasto da esfera
- Engineering ToolBox, viscosidades, densidades e coeficientes de atrito e restituição

**Dados astronômicos (NASA/JPL)**
- *Approximate Positions of the Planets*, elementos keplerianos J2000
- *Planetary Physical Parameters*, períodos siderais de rotação e translação
- *Planetary Fact Sheet*, parâmetros físicos complementares
- *Planetary Satellite Mean Elements*, órbitas das luas
- *Solar System Simulator, Texture Maps*, mapas planetários

> Os mapas dos gigantes gasosos são representativos; as texturas servem à visualização e não
> substituem análise científica.

---

## Documentos relacionados

- [`README.md`](../README.md): visão geral e como usar
- [`DESENHO_3D.md`](DESENHO_3D.md): como o desenho livre vira sólido 3D
- [`CORRECOES.md`](CORRECOES.md): registro de correções, com o raciocínio de cada uma
- [`../ARCHITECTURE.md`](../ARCHITECTURE.md): arquitetura interna e padrões de projeto
