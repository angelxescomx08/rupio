"""
Instala las imágenes de ejercicios de RepDB en la app (assets/ex) y actualiza "images" en exercises_es.json.

Por qué existe: la licencia gratuita de RepDB permite usar las imágenes DENTRO de la app, pero no volver a
publicarlas como colección (término 3). Como el repo de Rupio es público, las imágenes NO se suben a git
(app/src/main/assets/ex/ está en .gitignore) y se regeneran con este script.

Uso (desde la raíz del repo):
    git clone --depth 1 https://github.com/sergei-argutin/exercise-dataset.git ../repdb
    pip install pillow
    python tools/repdb/instalar_imagenes.py ../repdb

Qué hace:
- Lee tools/repdb/mapping.json: para cada ejercicio de Rupio (campo "src"), el id de RepDB o null.
- Escribe <src>__0.webp (inicio) y <src>__1.webp (fin) a 384×384, WebP calidad 80. Si RepDB solo tiene una
  pose ("main"), escribe solo __0. Si el ejercicio no tiene equivalente, no escribe nada y deja "images": [].
- Si la imagen no es exactamente el ejercicio (otro aparato, agarre, sin el peso…), el mapa trae una "nota" y se
  escribe como "imageNote" en exercises_es.json: la app la muestra debajo de la imagen en la pantalla del ejercicio.
- No borra archivos viejos de assets/ex: si cambias el mapa, vacía la carpeta antes (o muévela).

Crédito obligatorio (ya está en Perfil): "Exercise data by RepDB (repdb.co)" con enlace visible.
Licencia completa: LICENSE-DATA.md del repo de RepDB. Resumen: docs/IMAGENES_REPDB.md.
"""
import json
import sys
from pathlib import Path

from PIL import Image

SIZE = 384
QUALITY = 80


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("Uso: python tools/repdb/instalar_imagenes.py <carpeta del repo de RepDB>")
    repdb = Path(sys.argv[1])
    root = Path(__file__).resolve().parents[2]
    assets = root / "app" / "src" / "main" / "assets"
    out_dir = assets / "ex"
    out_dir.mkdir(parents=True, exist_ok=True)

    catalog_repdb = {r["id"]: r for r in json.loads((repdb / "exercises.json").read_text(encoding="utf-8"))["exercises"]}
    mapping = json.loads((Path(__file__).parent / "mapping.json").read_text(encoding="utf-8"))
    catalog_path = assets / "exercises_es.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))

    written = missing = 0
    for item in catalog:
        entry = mapping.get(item["src"])
        item.pop("imageNote", None)
        if entry is None:
            item["images"] = []
            missing += 1
            continue
        if entry.get("nota"):
            item["imageNote"] = entry["nota"]
        flat = catalog_repdb[entry["repdb"]]["images"]["flat"]
        poses = [flat["start"], flat["peak"]] if "start" in flat else [flat["main"]]
        images = []
        for k, pose in enumerate(poses):
            name = f"{item['src']}__{k}.webp"
            Image.open(repdb / pose).convert("RGB").resize((SIZE, SIZE), Image.LANCZOS) \
                .save(out_dir / name, "WEBP", quality=QUALITY, method=6)
            images.append(f"file:///android_asset/ex/{name}")
            written += 1
        item["images"] = images

    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False), encoding="utf-8")
    print(f"{written} imágenes escritas; {missing} ejercicios sin imagen.")


if __name__ == "__main__":
    main()
