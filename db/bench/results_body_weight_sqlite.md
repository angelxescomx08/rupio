# Peso corporal · SQLite (teléfono)

310,269 filas en total; usuario medido: 6,211 registros (20 años, ~85 % de los días). Carga: 0.53 s.

| Consulta | Puntos | p50 (ms) | p95 (ms) | Plan |
|---|---:|---:|---:|---|
| Gráfica 1 semana (diario) | 6 | 0.006 | 0.0071 | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?); USE TEMP B-TREE FOR GROUP BY; USE TEMP B-TREE FOR ORDER BY |
| Gráfica 1 mes (diario) | 27 | 0.0168 | 0.019 | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?); USE TEMP B-TREE FOR GROUP BY; USE TEMP B-TREE FOR ORDER BY |
| Gráfica 3 meses (diario) | 76 | 0.0402 | 0.0567 | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?); USE TEMP B-TREE FOR GROUP BY; USE TEMP B-TREE FOR ORDER BY |
| Gráfica 1 año (semanal) | 53 | 0.0741 | 0.094 | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?); USE TEMP B-TREE FOR GROUP BY; USE TEMP B-TREE FOR ORDER BY |
| Gráfica histórico 20 años (agrupada) | 153 | 1.1133 | 1.1763 | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on>? AND measured_on<?); USE TEMP B-TREE FOR GROUP BY; USE TEMP B-TREE FOR ORDER BY |
| Historial: página de 60 (keyset) | 60 | 0.0351 | 0.048 | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=? AND measured_on<?) |
| Último registro | 1 | 0.0025 | 0.0028 | SEARCH body_weights USING INDEX sqlite_autoindex_body_weights_1 (user_id=?) |
| Registrar/actualizar peso (con commit) |  | 0.1784 | 0.2222 | UPSERT por clave primaria (una fila) |
