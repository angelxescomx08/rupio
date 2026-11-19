---
inclusion: always
---

# Migraciones de Neon

En este proyecto, cada migración nueva o cambiada en `db/migrations/` se aplica en Neon **producción** sin preguntar: el usuario lo autorizó para siempre. No basta con dejar el archivo escrito.

- **Cómo**: `powershell -File db/neon/aplicar.ps1 db/migrations/NNN_nombre.sql --verificar="select ..."`. Abre Chrome con la sesión guardada de la consola de Neon (perfil en `.agents/neon/perfil`, fuera de git), pide la conexión del rol dueño y corre el archivo con `pg`. La cadena de conexión nunca se imprime.
- **Destino**: el script solo aplica si el endpoint es el de la app (`ep-wild-art-bdlip6s4`, proyecto "Rupio") y está en la rama principal (`production`). Si no coincide, se detiene: no forzarlo y avisar al usuario.
- **Sesión vencida**: si el script pide iniciar sesión, la ventana de Chrome queda abierta hasta 10 min para que el usuario entre.
- **Antes**: las migraciones deben poder correrse más de una vez (`if not exists`, `on conflict`, `create or replace`).
- **Después**: verificar con `--verificar` que el cambio quedó y anotarlo en el documento de la versión (`docs/QA_V19.md` o el que toque) con la fecha.
- **Para cualquier otro cambio en producción**, como borrar datos o cambiar permisos, sí hay que preguntar.
