# Imágenes de ejercicios (RepDB)

Desde la v1.9 las imágenes de los ejercicios son ilustraciones de **RepDB** (https://repdb.co), versión gratuita.
Antes eran las fotos de free-exercise-db. Se quitaron porque su propio autor dice que no sabe de dónde salieron
ni si son libres de derechos (issue #2 de yuhonas/free-exercise-db). La **lista** de ejercicios sí sigue basada en
free-exercise-db, que usa la licencia Unlicense (dominio público).

## Licencia gratuita de RepDB (resumen, manda `LICENSE-DATA.md` v1.0 de su repo)
1. Se pueden usar **dentro de apps**, también comerciales, sin pagar.
2. **Crédito obligatorio**: enlace visible «Exercise data by RepDB (repdb.co)». En Rupio está en **Perfil**, abajo (abre https://repdb.co). No quitarlo.
3. **No volver a publicarlas como colección** (dataset, repo de datos o API). Por eso `app/src/main/assets/ex/` está en `.gitignore`: el repo de Rupio es público.
4. Se pueden reducir, recortar o cambiar de color para la app. Aquí se reducen a 384×384, WebP calidad 80 (≈ 3.7 MB en total, antes 10 MB).
5. **No usarlas con IA generativa** (como referencia, para cambiarles el estilo, entrenar modelos, etc.).
   Por eso **los que faltan no se pueden dibujar "al estilo RepDB"** (ni con IA ni tomando sus imágenes de referencia): lo que salga cuenta como derivado y no se puede usar en la app. Si se hacen imágenes propias, deben partir de un estilo de Rupio, sin mirar las de RepDB.
6. Las animaciones de `premium-samples/` son solo para evaluar: no se usan.
7. Según RepDB, son obras originales generadas con IA por encargo suyo, sin copiar otras bases de datos.

La versión de pago (1024 px, animaciones, sin crédito) está en https://repdb.co/pricing.

## Cómo regenerarlas (por ejemplo, después de clonar el repo)
```bash
git clone --depth 1 https://github.com/sergei-argutin/exercise-dataset.git ../repdb
pip install pillow
python tools/repdb/instalar_imagenes.py ../repdb
```
`tools/repdb/mapping.json` une cada ejercicio de Rupio (`src`) con su id de RepDB. El script escribe
`assets/ex/<src>__0.webp` (inicio) y `__1.webp` (fin) y actualiza `"images"` en `exercises_es.json`. Si falta
alguna imagen, la prueba `QaDomainTest.catalogoIntegro` falla y dice qué archivo falta.

## Cobertura: 262 ejercicios
- **205 con imagen exacta** (mismo ejercicio y mismo equipo).
- **44 con imagen aproximada**: mismo movimiento, pero cambia un detalle (agarre, aparato o el peso que se ve). **38 llevan nota**: la pantalla del ejercicio dice debajo de la imagen qué cambia (campo `nota` del mapa → `imageNote` en `exercises_es.json`).
- **13 sin imagen**: RepDB no tiene uno equivalente. La app muestra el ícono de pesa y la técnica en texto.
- 9 tienen una sola pose (la imagen no se anima): Plancha, Plancha lateral, Cuerdas de batalla, Trepa de cuerda, Caminadora (cinta), Bicicleta fija, Elíptica, Escaladora (escaleras), Saltar la cuerda.
- 20 nov 2026: segunda revisión (también por nombre en español y descripción) → 7 más casi iguales; y 15 más del mismo movimiento con otro aparato, con nota. El catálogo de pago tiene los mismos 708 ejercicios: pagar no agrega ejercicios.

### Aproximadas (ejercicio de Rupio → imagen de RepDB usada · nota)
- Press con mancuernas agarre neutro → Press de Banca con Mancuernas y Agarre Cerrado (`close-grip-db-bench-press`)
- Press inclinado agarre martillo → Press con mancuernas en banco inclinado (`incline-db-press`) · _Aquí las palmas van una frente a la otra (agarre martillo)._
- Aperturas con polea en banco plano → Aperturas con Mancuernas (`db-fly`) · _La imagen muestra mancuernas; aquí se hace con poleas._
- Aperturas inclinadas con polea → Apertura con mancuernas en banco inclinado (`incline-dumbbell-fly`) · _La imagen muestra mancuernas; aquí se hace con poleas._
- Press inclinado en polea → Press con mancuernas en banco inclinado (`incline-db-press`) · _La imagen muestra mancuernas; aquí se hace en polea._
- Dominadas con agarre en V → Dominadas en Agarre Neutro (`neutral-grip-pull-ups`) · _La imagen muestra agarres paralelos separados; aquí usas el maneral en V con las manos juntas._
- Pullover en polea con cuerda → Jalón con Brazos Rectos (`straight-arm-pulldown`) · _La imagen muestra una barra; aquí se usa la cuerda._
- Remo en polea alta → Remo Sentado en Polea (`seated-cable-row`) · _La imagen muestra el remo sentado en polea baja; aquí jalas hacia el pecho alto con los codos abiertos._
- Remo en T con apoyo de pecho → Remo en Máquina con Apoyo en el Pecho (`chest-supported-machine-row`) · _La imagen muestra otra máquina de remo con pecho apoyado; el movimiento es el mismo._
- Remo alto en máquina → Remo en Máquina con Apoyo en el Pecho (`chest-supported-machine-row`) · _La imagen muestra un remo con pecho apoyado; aquí jalas desde arriba en la máquina de remo alto._
- Remo en máquina → Remo en Máquina con Apoyo en el Pecho (`chest-supported-machine-row`)
- Remo con barra en banco inclinado → Remo con Mancuernas con Apoyo en el Pecho (`chest-supported-db-row`) · _La imagen muestra mancuernas; aquí se hace con barra._
- Remo en landmine a una mano → Remo en T (`t-bar-row`) · _La imagen muestra el remo con las dos manos; aquí se hace a una mano._
- Extensiones lumbares en fitball → Extensión de Espalda (`back-extension`) · _La imagen muestra un banco de extensiones; aquí se hace sobre fitball._
- Press de hombros en polea → Press de Hombros con Mancuernas (`dumbbell-shoulder-press`) · _La imagen muestra mancuernas; aquí se hace en polea._
- Elevación lateral a una mano → Elevación Lateral con Mancuernas (`lateral-raise`) · _La imagen muestra los dos brazos; aquí se hace con uno a la vez._
- Elevaciones laterales con banda → Elevación Lateral con Mancuernas (`lateral-raise`) · _La imagen muestra mancuernas; aquí se hace con banda._
- Pájaros sentado → Pájaro Invertido con Mancuernas (`dumbbell-reverse-fly`) · _La imagen muestra la versión de pie; aquí se hace sentado, con el pecho cerca de los muslos._
- Encogimientos en polea → Encogimiento con Barra (`shrug`) · _La imagen muestra una barra; aquí se hace en polea._
- Curl alterno con mancuernas → Curl de Bíceps con Mancuernas (`bicep-curl`) · _La imagen muestra los dos brazos juntos; aquí se alternan._
- Curl martillo alterno → Curl de Martillo con Mancuernas (`hammer-curl`) · _La imagen muestra los dos brazos juntos; aquí se alternan._
- Curl predicador en polea → Curl en Banco Scott (`preacher-curl`) · _La imagen muestra barra Z; aquí se hace en polea._
- Extensión de tríceps con cuerda → Jalón de Tríceps en Cable (`tricep-pushdown`) · _La imagen muestra una barra; aquí se usa la cuerda._
- Extensión de tríceps agarre supino → Jalón de Tríceps en Cable (`tricep-pushdown`) · _Aquí las palmas van hacia arriba (agarre supino)._
- Extensión de tríceps a una mano en polea → Extensión de Tríceps a Un Brazo en Polea (`single-arm-tricep-pushdown`)
- Press francés con barra Z → Skull Crusher (`skull-crusher`) · _La imagen muestra barra recta; aquí se usa barra Z._
- Extensión de tríceps con mancuerna sobre la cabeza → Extensión de Tríceps con Mancuerna (`dumbbell-tricep-extension`)
- Press cerrado en Smith → Press de Banca con Agarre Cerrado (`close-grip-bench-press`) · _La imagen muestra barra libre; aquí se hace en la máquina Smith._
- Fondos para tríceps → Fondos para Pecho (`dips`) · _La imagen muestra la versión de pecho; para tríceps mantén el torso recto._
- Fondos en banco con peso → Fondos en Banco (`bench-dips`) · _La imagen muestra la versión sin peso; aquí pones un disco sobre las piernas._
- Curl de muñeca inverso → Curl de Muñeca Inverso con Mancuerna (`db-reverse-wrist-curl`) · _La imagen muestra mancuernas; aquí se hace con barra, palmas hacia abajo._
- Zancadas caminando con barra → Zancada con Barra (`barbell-lunge`) · _La imagen muestra la zancada en su lugar; aquí avanzas caminando._
- Subidas al cajón con mancuernas → Step Ups (`step-ups`) · _La imagen muestra la versión sin peso; aquí llevas una mancuerna en cada mano._
- Subidas al cajón con barra → Step Ups (`step-ups`) · _La imagen muestra la versión sin peso; aquí llevas la barra sobre la espalda._
- Sentadilla sissy → Sissy Squat (`sissy-squat`) · _La imagen muestra la versión sin peso._
- Curl femoral de pie → Curl de Pierna de Pie con Banda (`banded-standing-curl`) · _La imagen muestra la versión con banda; aquí se hace en la máquina de curl femoral de pie._
- Glute ham raise → Curl Nórdico de Isquiotibiales (`nordic-hamstring-curl`) · _La imagen muestra el curl nórdico en la máquina; el glute ham raise es muy parecido._
- Peso muerto de cargada → Peso Muerto con Barra (`deadlift`) · _La imagen muestra el peso muerto normal; aquí usas agarre de cargada._
- Caminata con banda → Caminata Sumo con Banda (`banded-sumo-walk`)
- Elevación de rodillas acostado → Crunches Inversos (`reverse-crunches`)
- Elevación de rodillas en paralelas → Elevación de Rodillas en Silla Romana (`captains-chair-knee-raise`) · _La imagen muestra la silla romana (con respaldo); en paralelas el movimiento es igual._
- Thruster con pesa rusa → Thruster (`thruster`) · _La imagen muestra una barra; aquí se hace con pesa rusa._
- Cargada y press → Clean and Press con Mancuernas (`dumbbell-clean-and-press`) · _La imagen muestra mancuernas; aquí se hace con barra._
- Caminadora (cinta) → Caminata en Cinta Inclinada (`incline-treadmill-walk`) · _La imagen muestra la cinta inclinada; también puedes caminar sin inclinación._

### Sin imagen
- Press inclinado en máquina
- Press declinado en máquina
- Cruce de poleas desde abajo
- Cruce de polea a una mano
- Press sentado con pesa rusa
- Elevación frontal con disco
- Curl en polea alta
- JM press
- Sentadilla hack con barra
- Prensa en Smith
- Pantorrilla en prensa
- Crunch con peso
- Leñador en polea
