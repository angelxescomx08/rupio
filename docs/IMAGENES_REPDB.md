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
- **204 con imagen exacta** (mismo ejercicio y mismo equipo).
- **23 con imagen aproximada**: mismo movimiento, pero cambia un detalle (agarre, accesorio o el peso que se ve).
- **35 sin imagen**: RepDB no tiene uno equivalente. La app muestra el ícono de pesa y la técnica en texto.
- 9 tienen una sola pose (la imagen no se anima): Plancha, Plancha lateral, Cuerdas de batalla, Trepa de cuerda, Caminadora (cinta), Bicicleta fija, Elíptica, Escaladora (escaleras), Saltar la cuerda.

### Aproximadas (ejercicio de Rupio → imagen de RepDB usada)
- Press con mancuernas agarre neutro → Press de Banca con Mancuernas y Agarre Cerrado (`close-grip-db-bench-press`)
- Dominadas con agarre en V → Dominadas en Agarre Neutro (`neutral-grip-pull-ups`)
- Pullover en polea con cuerda → Jalón con Brazos Rectos (`straight-arm-pulldown`)
- Remo en T con apoyo de pecho → Remo en Máquina con Apoyo en el Pecho (`chest-supported-machine-row`)
- Remo en máquina → Remo en Máquina con Apoyo en el Pecho (`chest-supported-machine-row`)
- Elevación lateral a una mano → Elevación Lateral con Mancuernas (`lateral-raise`)
- Pájaros sentado → Pájaro Invertido con Mancuernas (`dumbbell-reverse-fly`)
- Curl alterno con mancuernas → Curl de Bíceps con Mancuernas (`bicep-curl`)
- Curl martillo alterno → Curl de Martillo con Mancuernas (`hammer-curl`)
- Extensión de tríceps con cuerda → Jalón de Tríceps en Cable (`tricep-pushdown`)
- Extensión de tríceps a una mano en polea → Extensión de Tríceps a Un Brazo en Polea (`single-arm-tricep-pushdown`)
- Press francés con barra Z → Skull Crusher (`skull-crusher`)
- Extensión de tríceps con mancuerna sobre la cabeza → Extensión de Tríceps con Mancuerna (`dumbbell-tricep-extension`)
- Fondos para tríceps → Fondos para Pecho (`dips`)
- Fondos en banco con peso → Fondos en Banco (`bench-dips`)
- Zancadas caminando con barra → Zancada con Barra (`barbell-lunge`)
- Subidas al cajón con mancuernas → Step Ups (`step-ups`)
- Subidas al cajón con barra → Step Ups (`step-ups`)
- Sentadilla sissy → Sissy Squat (`sissy-squat`)
- Caminata con banda → Caminata Sumo con Banda (`banded-sumo-walk`)
- Elevación de rodillas acostado → Crunches Inversos (`reverse-crunches`)
- Elevación de rodillas en paralelas → Elevación de Rodillas en Silla Romana (`captains-chair-knee-raise`)
- Caminadora (cinta) → Caminata en Cinta Inclinada (`incline-treadmill-walk`)

### Sin imagen
- Press inclinado agarre martillo
- Press inclinado en máquina
- Press declinado en máquina
- Cruce de poleas desde abajo
- Aperturas con polea en banco plano
- Aperturas inclinadas con polea
- Cruce de polea a una mano
- Press inclinado en polea
- Remo en polea alta
- Remo alto en máquina
- Remo con barra en banco inclinado
- Remo en landmine a una mano
- Extensiones lumbares en fitball
- Press de hombros en polea
- Press sentado con pesa rusa
- Elevaciones laterales con banda
- Elevación frontal con disco
- Encogimientos en polea
- Curl en polea alta
- Curl predicador en polea
- Extensión de tríceps agarre supino
- Press cerrado en Smith
- JM press
- Curl de muñeca inverso
- Sentadilla hack con barra
- Prensa en Smith
- Curl femoral de pie
- Glute ham raise
- Peso muerto de cargada
- Pantorrilla en prensa
- Crunch con peso
- Crunch oblicuo
- Leñador en polea
- Thruster con pesa rusa
- Cargada y press
