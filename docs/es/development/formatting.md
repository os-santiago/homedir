# Código Formateado con Spotless

Este repositorio utiliza [Spotless](https://github.com/diffplug/spotless) para asegurar un estilo de código consistente.

## Comandos útiles

Ejecuta el formateo automático antes de abrir un pull request:

```bash
mvn -f quarkus-app/pom.xml spotless:apply
```

Si quieres verificar que no hay archivos pendientes de formatear sin modificarlos, utiliza:

```bash
mvn -f quarkus-app/pom.xml spotless:check
```

## Integración con CI

Los flujos de integración continua ejecutan `spotless:check`. Si el formato no es el esperado, la verificación fallará con un mensaje similar a:

```
The following files had format violations...
```

Para resolverlo simplemente vuelve a ejecutar `spotless:apply` y confirma los cambios resultantes.
