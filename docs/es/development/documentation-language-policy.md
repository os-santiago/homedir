# Politica de idioma de documentacion

Esta politica define como se organiza y mantiene la documentacion en este repositorio.

## Alcance

- Aplica a toda la documentacion bajo `docs/`.
- Aplica a archivos markdown (`.md`) e indices de idioma.

## Idioma canonico

- El idioma canonico de mantenimiento es **ingles** (`docs/en`).
- Espanol (`docs/es`) se mantiene como espejo.

## Estructura permitida

- `docs/README.md` (solo gateway de idioma)
- `docs/en/**` (documentacion canonica)
- `docs/es/**` (traduccion o stubs)

No se permiten documentos markdown sueltos en el nivel raiz de `docs/`.

## Regla obligatoria de espejo

Por cada documento en `docs/en/<ruta>.md`, debe existir su equivalente en `docs/es/<ruta>.md`:

- Traduccion completa, o
- Stub que apunte al documento canonico en ingles.

Y viceversa:

- Si temporalmente la fuente canonica esta en espanol, `docs/en/<ruta>.md` debe existir como stub en ingles.

## Formato de stub

Todo stub debe incluir:

- Estado claro (`stub`).
- Enlace al documento canonico.
- Nota breve del estado de traduccion.

## Categorias

Mantener la misma ruta de categoria en ambos idiomas:

- `ai-context/`
- `architecture/`
- `community/`
- `development/`
- `events/`
- `features/`
- `modules/`
- `tasks/`
- `ui/`

## Flujo de contribucion

1. Crear/actualizar el documento canonico en `docs/en`.
2. Crear/actualizar el equivalente en `docs/es`.
3. Si no hay traduccion aun, agregar/actualizar stub en espanol.
4. Actualizar indices:
   - `docs/en/README.md`
   - `docs/es/README.md`
   - `docs/README.md` (si cambia la navegacion por idioma)
5. Verificar que no queden `.md` fuera de `docs/en` o `docs/es` (excepto `docs/README.md`).
