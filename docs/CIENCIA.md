# La ciencia detrás de Rupio

Qué dice la evidencia y **cómo lo usa la app**. Los textos de la app son simples a propósito; aquí está el detalle.

## 1. Cuándo subir el peso (motor de progresión)

| Evidencia | Qué dice | Cómo lo aplica Rupio (`domain/Training.kt`) |
|---|---|---|
| **ACSM 2009**, *Progression models in resistance training for healthy adults*, Med Sci Sports Exerc 41(3):687-708 ([PubMed](https://pubmed.ncbi.nlm.nih.gov/19204579/)) | Subir la carga **2–10 %** cuando puedes hacer el peso actual **1–2 repeticiones por encima** del objetivo. Principiantes: 8–12 RM. Hipertrofia: 1–2 min de descanso; cargas pesadas 3–5 min. | **Doble progresión**: rango de reps (p. ej. 8–12). Cuando *todas* las series al peso tope llegan al máximo → "Sube a X" (+2.5 % tren superior, +5 % tren inferior, redondeado a discos/mancuernas reales en lb o kg) y vuelves al mínimo. |
| **Regla 2×2** (NSCA, Baechle & Earle) | Si en 2 sesiones seguidas haces 2+ reps de más en la última serie, sube. | Segunda vía para subir aunque no todas las series lleguen al máximo. |
| **Plotkin et al. 2022**, PeerJ 10:e14142 ([artículo](https://peerj.com/articles/14142)) | 43 personas entrenadas, 8 semanas: progresar **repeticiones** con el mismo peso dio adaptaciones similares a progresar **peso**. | Si no toca subir, Rupio no te deja "estancado": te pide **+1 rep** en la serie más floja. Ejercicios **solo de repeticiones** (lagartijas, abdominales) progresan sumando reps y, al dominar el rango, sugieren una variante más difícil. |
| **Robinson et al. 2024**, Sports Med 54:2209-2231 (meta-regresiones) | La **hipertrofia** mejora al terminar las series más cerca del fallo; la **fuerza** es similar en un rango amplio de RIR (depende más de la carga). | Textos de la app: terminar la mayoría de series a 0–3 reps del fallo. Si registras RPE ≥ 9.5 sin completar el rango, Rupio **no** te sube el peso. |
| **Zourdos et al. 2016**, J Strength Cond Res 30(1):267-275 | Escala RPE basada en **repeticiones en reserva** (RPE 10 = 0 en reserva, 9 = 1, 8 = 2…). | Al tocar el número de una serie puedes anotar RPE con esa explicación. |
| Estancamiento / fallos repetidos | — | Si 2 sesiones seguidas quedas por debajo del mínimo, o tu 1RM estimado no mejora en 4 sesiones → "Baja ~10 %" (descarga). |

**Asistidos** (dominadas con banda/máquina): se registra la ayuda; al completar el rango se reduce la ayuda. **Tiempo** (plancha): +5 s o +10 %.

## 2. 1RM estimado

| Evidencia | Uso |
|---|---|
| Epley (1985) y Brzycki (1993); **LeSuer et al. 1997**, J Strength Cond Res 11(4):211-213: las ecuaciones correlacionan alto con el 1RM real, con errores mayores en peso muerto y con muchas repeticiones; la precisión baja por encima de ~10 reps. | 1 rep = el peso. 2–10 reps = promedio Epley/Brzycki. 11–12 = Epley. **Más de 12 no se estima** (sería ruido). Se usa para récords, la curva de fuerza y detectar estancamiento. |

## 3. Descansos

| Evidencia | Uso |
|---|---|
| **Singer et al. 2024**, Front Sports Act Living 6:1429789 ([artículo](https://www.frontiersin.org/journals/sports-and-active-living/articles/10.3389/fspor.2024.1429789/text)), meta-análisis bayesiano | Pequeño beneficio para la hipertrofia al descansar **más de 60 s**; **sin diferencias apreciables por encima de 90 s**. | Descanso por defecto **90 s**; sugerencia de 2–3 min en ejercicios pesados (ACSM). El temporizador es por ejercicio y configurable. |

## 4. Volumen semanal por músculo (gráfica "Series por músculo")

| Evidencia | Uso |
|---|---|
| **Schoenfeld et al. 2017**, J Sports Sci 35(11):1073-1082 | Relación dosis-respuesta entre series semanales y ganancia muscular; **10+ series/semana** por músculo se asocian con más crecimiento. | Marca visual en 10 series/semana. |
| **Pelland et al. 2026**, Sports Med 56(2):481-505 (67 estudios, 2 058 participantes) | Más volumen semanal → más hipertrofia **con rendimientos decrecientes**; distinguir series directas e indirectas mejora la predicción (método **fraccional**: indirecta = 0.5). La frecuencia afecta sobre todo a la fuerza. | Las series se cuentan con el método fraccional: músculo principal = 1, secundario = 0.5. El texto aclara que más series siguen sumando, pero cada vez menos. |

## 5. Superseries

| Evidencia | Uso |
|---|---|
| **Zhang et al. 2025**, Sports Med, meta-análisis (19 estudios, 313 participantes) | Las superseries **acortan la sesión** sin perder volumen ni adaptaciones crónicas (fuerza, hipertrofia). Agonista–antagonista permite **más repeticiones**; pares del mismo patrón **reducen el volumen total**. | Crear superseries es de un toque ("Unir con el siguiente") con descanso **compartido** (uno al terminar la ronda) o **individual**. Si ambos ejercicios trabajan el mismo músculo, Rupio avisa; si no, sugiere pares opuestos (pecho + espalda). |

## 6. Volumen de una sesión

Volumen = Σ (peso × reps) de **series efectivas completadas** (no cuenta calentamiento ni la asistencia de ejercicios asistidos). Se muestra en la unidad elegida (lb por defecto) y siempre también en la otra.

## 7. Técnica estándar y técnicas avanzadas

Para comparar una sesión con otra, el ejercicio debe ser el mismo (misma máquina, recorrido, velocidad y descanso parecido). Los drop sets ahorran tiempo y dan un crecimiento parecido al de las series normales (Coleman 2022; Sødal 2023), pero su peso y reps dependen del cansancio, así que **Rupio no los usa para decidir cuándo subir el peso** (tampoco los calentamientos), y no arranca el descanso antes de un drop set.

## 8. Cuánto entrenar

- Más series por semana dan más resultados, pero cada serie extra suma menos (Schoenfeld 2017; Pelland 2026). No hace falta entrenar horas todos los días.
- Con el mismo volumen semanal, entrenar 1, 2 o 3+ días da una fuerza parecida (Ralston 2018; Grgic 2018): los días sirven sobre todo para repartir el volumen.
- Programas cortos funcionan: alrededor de 4 series por músculo a la semana ya dan resultados (Iversen 2021).
- Repeticiones: el músculo crece parecido con cargas ligeras o pesadas si se llega cerca del fallo; la fuerza máxima mejora más con pesos altos (Schoenfeld 2017; Lopez 2021).
- Las personas sin experiencia progresan más rápido (Rhea 2003; Lopez 2021): por eso los primeros años son clave para construir buenas bases.
- Máquinas y pesos libres dan resultados parecidos en fuerza y músculo; te vuelves más fuerte en lo que practicas (Haugen 2023). La guía recomienda máquinas porque son más fáciles de aprender y de repetir igual, pero pregunta.

## 9. Nutrición y peso corporal

- Bajar de peso solo con ejercicio da resultados pequeños; las pérdidas importantes vienen de combinarlo con dieta (Donnelly 2009, ACSM).
- Para definir conservando músculo: déficit con un ritmo de ~0.5–1 % del peso por semana (Helms 2014). Para ganar músculo con poca grasa: superávit pequeño, ~0.25–0.5 % por semana (Iraki 2019).
- Proteína: ~1.6 g/kg/día es suficiente para la mayoría (Morton 2018).
- La gráfica de peso usa una tendencia (media móvil exponencial de 10 % por día, ajustada a los días sin registro) porque el peso diario varía 1–2 kg por agua y comida.

## 9b. Calorías estimadas de un entreno (v1.8)

| Evidencia | Uso |
|---|---|
| **2024 Adult Compendium of Physical Activities** (Herrmann et al., J Sport Health Sci 13(1):6-12, [pacompendium.com](https://pacompendium.com/conditioning-exercise)) | Código **02054**: pesas, varios ejercicios, 8–15 reps con carga variada = **3.5 MET**. (02052 sentadilla/peso muerto lento o explosivo = 5.0; 02050 powerlifting/fisicoculturismo vigoroso = 6.0.) | kcal = 3.5 × kg × horas. Es la descripción más parecida a una sesión típica de Rupio. Incluye el gasto en reposo (MET brutos). Peso: el último registrado; si no hay, 70 kg (y se dice). Si el entreno quedó abierto, máximo 5 min por serie efectiva y 3 h. Se redondea a 5 kcal y se avisa que puede variar ±20–30 %. |

## 9c. Reparto de músculos de un entreno (v1.8)

Mismo método fraccional que "Series por músculo" (Pelland et al. 2026): cada serie efectiva cuenta 1 para el músculo principal y 0.5 para cada secundario; los calentamientos no cuentan. Se muestran los 6 músculos con más series y el resto como "Otros", con series y porcentaje en texto.

## 10. Beneficios del ejercicio de fuerza

OMS 2020 (2+ días por semana); Momma 2022 (10–17 % menos mortalidad, enfermedad cardiovascular, cáncer y diabetes; máximo beneficio con ~30–60 min/semana); Gordon 2018 (depresión) y 2017 (ansiedad); Watson 2018 (densidad ósea); Westcott 2012 (metabolismo, presión, músculo).

Todos los enlaces están dentro de la app (Aprende → Glosario y en cada tutorial).
