---
description: Realiza un análisis completo de salud y estabilidad del repositorio (Build, Tests, Runtime).
---
Este workflow ejecuta una serie de verificaciones para asegurar que el repositorio está en buen estado, incluyendo estilo de código, compilación, pruebas unitarias/integración y una validación de arranque de la aplicación.

## 1. Verificación de Estilo y Compilación
Ejecutamos `clean verify` para limpiar, compilar y correr pruebas. Esto también valida el estilo (Spotless) si está erróneamente configurado, pero aquí nos enfocamos en que el build pase.

// turbo
```bash
cd quarkus-app
./mvnw clean verify -DskipITs=false
```

## 2. Validación en Tiempo de Ejecución (Health Check)
Para verificar que la aplicación arranca correctamente y expone sus endpoints de salud.

1.  Inicia la aplicación en modo desarrollo en segundo plano.
2.  Verifica el endpoint `/q/health`.

```bash
# Iniciar app en background (ajustar tiempo de espera si es necesario)
cd quarkus-app
./mvnw quarkus:dev -Ddebug=false &
PID=$!
sleep 20 # Esperar a que arranque

# Verificar Health
curl -f http://localhost:8080/q/health || exit 1

# Limpiar
kill $PID
```

## 3. Validaciones Recomendadas (Manual / Browser)
Si el paso anterior fue exitoso, el backend está sano. Para validar el frontend "site functionality":
- Navegar a `http://localhost:8080/`
- Verificar carga de imágenes y estilos.
- Navegar a `/comunidad` y verificar la lista de miembros.
- Verificar flujo de "Ingresar" (Login).

> Nota: Las pruebas de integración en el paso 1 ya cubren gran parte de la lógica, pero la inspección visual es recomendada para cambios de UI.
