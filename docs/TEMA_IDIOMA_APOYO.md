# v2.0 · Inglés, modo claro y visibilidad de Apoya Rupio

Qué se hizo, por qué y con qué fuente. Lo de cómo se pide el apoyo (momento, niveles, tono) sigue en `APOYO_CIENCIA.md`.

## 1. Idioma

**Cómo funciona**

- En Perfil → Idioma: *Automático / Español / English*. Automático sigue al teléfono: si el teléfono está en español sale español; en cualquier otro idioma sale inglés (es el idioma con más alcance para quien no habla español).
- Android 13+ usa el selector de idioma por app del sistema (`LocaleManager` + `res/xml/locales_config.xml`), así que también aparece en *Ajustes → Apps → Rupio → Idioma*. En Android 12 o menos se guarda en preferencias y se aplica en `attachBaseContext`. Fuente: [Android · Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages).
- Quien ya tenía la app instalada se queda en español (se detecta por `catalog_v` en `rupio_app`). Las instalaciones nuevas siguen al teléfono.
- Los textos usan `tr("es", "en")` (en `core/I18n.kt`) en vez de `strings.xml`: casi todo el texto vive en `domain/`, sin `Context`, con plurales y números armados en código. Así los dos idiomas quedan juntos, las pruebas de dominio siguen siendo Kotlin puro y no hubo que mover ~1 900 textos.
- El catálogo de ejercicios tiene su versión en inglés (`assets/exercises_en.json`, 262 ejercicios). Al cambiar de idioma se reescriben los nombres en la base local y la búsqueda guarda los dos nombres: "press banca" y "bench press" encuentran lo mismo.
- No se traducen los datos del usuario (nombres de rutinas, notas) ni los nombres del muro.
- El aviso de privacidad tiene traducción completa al inglés con una nota: la versión oficial es la de español.

**Reglas de traducción que se siguieron**

| Regla | Por qué | Fuente |
|---|---|---|
| Frases completas por idioma, nunca pegar pedazos ("Se desbloquea con" + nivel) | El orden de las palabras cambia entre idiomas; pegar pedazos produce frases rotas. | [Mozilla · Localization best practices for developers](https://mozilla-l10n.github.io/documentation/localization/dev_best_practices.html) ("Avoid concatenations", "Word order") |
| Plurales con su forma en cada idioma (1 workout / 2 workouts) | Inglés y español tienen formas distintas; "1 workouts" se ve descuidado. | Misma guía de Mozilla ("Use proper plural forms") |
| Dejar espacio para textos más largos | Una misma frase cambia de largo entre idiomas; los botones y chips no deben cortar palabras. | Misma guía de Mozilla ("Design for at least +30%") |
| Inglés en *sentence case* ("Start empty workout", no "Start Empty Workout") | Es la norma de Material y de la guía de estilo de Google; se lee más rápido. | [Google developer documentation style guide · Capitalization](https://developers.google.com/style/capitalization) |
| Vocabulario del gimnasio en inglés: *workout, routine, set, reps, PR, volume, rest, superset* | Son los términos de Strong y Hevy, las apps que la gente ya conoce. | Interfaces de Strong y Hevy |
| Fechas y números con el formato del idioma (`Tue, Nov 17, 2026`, `11:20 AM`, `6,736 kg`) | El usuario de cada idioma espera su formato. | `java.time` con el `Locale` del idioma |

## 2. Modo claro

**Cómo funciona**

- En Perfil → Tema: *Sistema / Claro / Oscuro*. Quien ya tenía la app se queda en oscuro; las instalaciones nuevas siguen al teléfono.
- El oscuro no cambió.

**Por qué ofrecer claro**: el texto oscuro sobre fondo claro (polaridad positiva) se lee mejor y más rápido, sobre todo con mucha luz alrededor, como en un gimnasio o al aire libre.

- Piepenbrock, Mayr, Mund & Buchner 2013. *Positive display polarity is advantageous for both younger and older adults*. Ergonomics 56(7):1116–1124. [doi:10.1080/00140139.2013.790485](https://doi.org/10.1080/00140139.2013.790485)
- Dobres, Chahine & Reimer 2017. *Effects of ambient illumination, contrast polarity, and letter size on text legibility under glance-like reading*. Applied Ergonomics 60:68–73. [doi:10.1016/j.apergo.2016.11.001](https://doi.org/10.1016/j.apergo.2016.11.001)

**Cómo se armó la paleta**

- Superficies por tono, como en Material 3: fondo gris azulado y tarjetas blancas encima, para separar sin sombras ni bordes gruesos. [Material 3 · Tone-based surfaces](https://m3.material.io/blog/tone-based-surface-color-m3), [Material 3 · Color roles](https://m3.material.io/styles/color/roles).
- Los colores con significado (verde de éxito, naranja, rojo, lila de descanso, gráficas) se oscurecieron para que se lean sobre blanco.
- Cada acento tiene un tono para oscuro (claro, con letra oscura encima) y otro para claro (oscuro, con letra blanca).
- El borde de los controles (campos, botones con borde, chips) tiene ≥ 3:1 sobre todas las superficies. [WCAG 2.1 · 1.4.11 Non-text contrast](https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast.html).
- Todo el texto pasa ≥ 4.5:1 (AA) sobre todas las superficies. [WCAG 2.1 · 1.4.3 Contrast (minimum)](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html).

| Rol | Claro | Contraste en el peor caso (sobre `surface3` #E6E8ED) |
|---|---|---|
| Fondo / tarjeta / tarjeta 2 / tarjeta 3 | #F4F5F8 / #FFFFFF / #EDEEF2 / #E6E8ED | — |
| Texto / secundario / tenue | #16171C / #4A4C57 / #5C5E69 | 14.6 / 6.96 / 5.25 |
| Borde de controles | #767986 | 3.53 |
| Éxito / aviso / peligro / descanso | #0A6B41 / #954500 / #B01F2D / #5B40C9 | 5.37 / 5.47 / 5.56 / 5.67 |
| Gráficas | #1E55BF | 5.51 |
| Acentos (azul, rosa, turquesa, lila, plata, dorado, aurora) | #2850C4, #AE1450, #006A7A, #7036C2, #4F5D70, #765300, #7F3592 | ≥ 5.12; letra blanca encima ≥ 6.28 |
| Tinta de los metales (bronce, plata, oro, aurora) | — | ≥ 7.67 (≥ 6.85 sobre su propio fondo) |

`V20Test` calcula estos contrastes con la fórmula de WCAG y falla si alguno baja del mínimo.

## 3. Recompensas

Todo Rupio sigue gratis. Las recompensas son solo adornos para quien paga y ninguna desbloquea funciones.

| Nivel | Precio | Qué agrega |
|---|---|---|
| ☕ Un café · Bronce | $49 MXN | Quita el mensaje · insignia ☕ bronce · anillo bronce en el avatar · color rosa |
| 💪 Un licuado · Plata | $99 MXN | Todo lo del café · insignia y anillo plata · colores turquesa, lila y plata · ícono plateado |
| 🏆 Fan · Oro | $199 MXN | Todo lo del licuado · insignia y anillo dorados animados · color e ícono dorados · confeti al terminar cada entreno |
| 🌟 Mecenas · Aurora | $499 MXN | Todo lo de Fan · insignia y anillo Aurora animados · color e ícono Aurora · tu nombre en el muro · subir en el muro con cada aporte extra |

El rosa bajó de Licuado a Café para que el nivel más barato también tenga algo que se vea.

**Que se entienda que son por pagar**

- En *Tus recompensas*: "Lo tienes por apoyar Rupio con 💪 Un licuado. ¡Gracias!".
- Lo bloqueado muestra un candado y "Se desbloquea con 🏆 Fan de Rupio".
- Debajo, siempre: "Todo Rupio es gratis. Estas recompensas son adornos para dar las gracias a quien paga: no desbloquean funciones."
- La insignia también sale en el resumen del entreno con "Gracias por apoyar Rupio".

Ver la insignia y que otros la vean refuerza el pago voluntario: Harbaugh 1998, *What do donations buy? A model of philanthropy based on prestige and warm glow*, J. Public Econ. 67(2):269–284, [doi:10.1016/S0047-2727(97)00062-5](https://doi.org/10.1016/S0047-2727(97)00062-5).

## 4. Dónde se ve el muro de agradecimientos

**Antes**: el muro solo estaba en la pestaña *Muro* de Apoya Rupio, y casi nadie llegaba ahí.

**Ahora**: una tarjeta corta (`WallPreviewCard`) con el podio (3 primeros) y "Rupio es gratis y sin anuncios gracias a estas N personas" aparece en:

| Lugar | Cuándo |
|---|---|
| Inicio, al final de la lista | Desde el 3.er entreno terminado (la misma regla que el mensaje de apoyo). Tiene "Ocultar" y no vuelve a salir. |
| Progreso, al final, antes de *Aprende* | Siempre |
| Perfil, junto a *Apoya Rupio* | Siempre |
| Apoya Rupio → Apoyar, antes de los niveles | Siempre |

A quien ya es Mecenas no se le muestra la tarjeta (ya tiene la suya, `WallSummaryCard`, con su lugar).

**Por qué así**

| Decisión | Por qué | Fuente |
|---|---|---|
| Al final de las pantallas, como tarjeta normal, sin colores de anuncio | Lo que parece anuncio o está arriba "estorbando" la gente lo ignora sin leerlo (ceguera a banners). Lo primero en Inicio sigue siendo entrenar. | [NN/g · Banner blindness revisited](https://www.nngroup.com/articles/banner-blindness-old-and-new-findings/) |
| En varias pantallas que ya se usan, no solo en un menú | Lo que está escondido detrás de un menú se descubre mucho menos. | [NN/g · Hamburger menus and hidden navigation](https://www.nngroup.com/articles/find-navigation-mobile-even-hamburger/) |
| En Inicio se puede ocultar y no sale los primeros entrenos | Evitar saturar; primero la app tiene que haber servido (reciprocidad, ver `APOYO_CIENCIA.md`). | Regner & Barria 2009 |
| El muro reconoce en público a quien aporta más | El reconocimiento público sube las donaciones. | Karlan & McConnell 2014, *Hey look at me: The effect of giving circles on giving*, J. Econ. Behav. Organ. 106:402–412, [doi:10.1016/j.jebo.2014.06.013](https://doi.org/10.1016/j.jebo.2014.06.013); Harbaugh 1998 |

## 5. Qué revisar en un teléfono

1. Cambiar el idioma en Perfil y en *Ajustes → Apps → Rupio → Idioma* (Android 13+).
2. Cambiar el ícono a plateado, dorado y Aurora: el launcher puede tardar unos segundos.
3. Modo claro al sol: que se lean los textos tenues y los bordes de los campos.
4. Instalar encima de la v1.9: debe seguir en español y oscuro.
