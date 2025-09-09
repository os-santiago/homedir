# Persistencia con HDP

Ver [HDP en el glosario](00-glossary.md#hdp-homedir-persist).

## Diseño
WAL append-only + MemTable + snapshots + segments + compaction.

## Durabilidad
Modos `safe`, `balanced`, `fast`.

## API
SDK embebido con opción REST.

## Aislamiento
Archivos, pools e I/O por namespace; cuotas de memoria y espacio.

## Backup/Restore
Snapshot + replay del WAL.

## Cuándo no usar
- joins complejos
- reporting pesado
- transacciones multi-agregado fuertes
