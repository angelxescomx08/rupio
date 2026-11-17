# Apoya Rupio · qué dice la investigación y cómo se aplicó

Rupio es gratis y sin anuncios. Quien quiera apoyarlo hace un **pago único** (de por vida, sin suscripción) en uno de tres niveles. Ninguno desbloquea funciones: todos quitan el mensaje de apoyo y solo cambian los adornos.

| Nivel | ID en Play Console | Precio sugerido | Recompensas |
|---|---|---|---|
| ☕ Un café | `rupio_apoyo_cafe` | $49 MXN | Quita el mensaje + insignia ☕ |
| 💪 Un licuado (recomendado) | `rupio_apoyo_licuado` | $99 MXN | Lo anterior + insignia 💪 + 4 colores (turquesa, rosa, lila) |
| 🏆 Fan de Rupio | `rupio_apoyo_fan` | $199 MXN | Lo anterior + insignia animada + dorado exclusivo + ícono dorado |

## Cada decisión y su estudio

| Decisión en la app | Por qué | Fuente |
|---|---|---|
| El mensaje sale **al terminar el entreno**, ~1.4 s después de que se ve el resumen y los récords | Cuando la gente está de buen humor ayuda más (dona, coopera). Terminar un entreno y ver un récord es uno de esos momentos. | Isen & Levin 1972 (*JPSP* 21:384); revisión de Carlson, Charlin & Miller 1988 |
| **No sale en los primeros 2 entrenos** | Los pagos voluntarios dependen sobre todo de la **reciprocidad**: primero la app tiene que haberle servido. | Regner & Barria 2009 (*J. Econ. Behav. Organ.*, Magnatune); Regner 2015 (*J. Behav. Exp. Econ.* 57:205) |
| El mensaje recuerda **lo que ya hizo con la app** ("Llevas 24 entrenamientos y 48,300 lb levantados") | Así la reciprocidad se vuelve concreta: dice qué recibió, con sus propios datos. | Mismas fuentes de reciprocidad |
| Habla **una persona con nombre** ("Hola, soy Ángel… lo hago yo solo") | Se ayuda más a una persona concreta e identificada que a una organización o a algo abstracto ("efecto de la víctima identificable"). Es más fuerte con **una sola** persona. | Kogut & Ritov 2005 (*J. Behav. Decis. Mak.*); Small & Loewenstein 2003 |
| **Tres niveles** y el de en medio marcado como "Recomendado" | Con tres opciones, la de en medio gana elecciones ("efecto compromiso"). | Simonson 1989 (*J. Consumer Research* 16:158) |
| Dice **"Recomendado"**, no "el más elegido" | Afirmar que otros eligen algo cuando no hay datos es prueba social falsa (un patrón engañoso). | Guía de patrones engañosos (deceptive.design); Which? 2026 |
| **"Tú decides: Rupio seguirá completo y gratis aunque no pagues"** | Recordar que la persona es libre de decir que no aumenta la disposición a aceptar (técnica "pero eres libre"). **Ojo:** un meta-análisis de 2023 encontró que el efecto es pequeño o nulo en los estudios de mejor calidad; aquí se usa sobre todo porque es lo correcto y evita la sensación de presión. | Carpenter 2013 (meta-análisis, 42 estudios); Fillon et al. 2023 (*Meta-Psychology*, re-examen) |
| **Cero culpa o vergüenza** ("No, prefiero no apoyar…") y la **X se ve desde el primer segundo** | El "confirmshaming" y los botones de cerrar escondidos dañan la confianza: en 2026, 25 % de los consumidores dejó de usar una plataforma por estos patrones y 22 % perdió toda la confianza en la empresa. | Which? *Dark Patterns 2026*; Knyzelis 2024 (*Creativity Studies*) |
| **Pantalla de gracias** con confeti, firma y la insignia | Recibir un "gracias" hace que la gente se sienta valorada y vuelva a ayudar (más del doble en los experimentos). **Ojo:** los experimentos 1 y 2 de ese artículo los corrió F. Gino, cuyos datos en otros trabajos se han cuestionado; el experimento 3 (recaudadores) no lo corrió ella. | Grant & Gino 2010 (*JPSP* 98:946) |
| **Insignia visible** en el perfil | Cuando un pago voluntario se ve, la gente paga más (imagen social). Rupio no tiene red social, así que el efecto aquí es sobre todo hacia uno mismo. | Hofmann et al. 2025 (*Front. Behav. Econ.*, 2 experimentos, n≈2 000) |
| **Pago único, sin suscripción** | En 2026, ~41 % de los usuarios dice estar cansado de las suscripciones; además, Rupio casi no tiene costos por usuario. | Benchmarks de RevenueCat/Adapty 2026 |
| Se puede pagar **en efectivo (OXXO)** y el pago queda "pendiente" hasta completarse | En México mucha gente no usa tarjeta. Google Play lo soporta; la app lo muestra y lo activa sola. | Documentación de Google Play Billing |

## Reglas del mensaje (en código: `SupportPolicy`)

- Sale **desde el 3.er entreno terminado**, **una vez por entreno** (girar el teléfono o volver atrás no lo repite).
- **Nunca** a quien ya apoyó, con cualquier nivel.
- Ocupa la pantalla, pero se cierra con **un toque** (X arriba o "Ahora no" abajo).
- Además hay una fila fija **"Apoya Rupio ❤️"** en Perfil, para quien quiera apoyar sin esperar al mensaje.

## Cosas a medir cuando esté publicada

1. % de mensajes que terminan en compra (por nivel).
2. Si el nivel de $49 se lleva casi todo, prueba subirlo a $69.
3. Que **no** suban las desinstalaciones ni bajen las reseñas tras el mensaje. Si pasa, sube `FIRST_PROMPT_AT_WORKOUT` o muéstralo cada 2 entrenos.
