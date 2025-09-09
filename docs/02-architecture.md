# Arquitectura del Monolito Modular

## Principios
- modularidad estricta
- blast radius contenido
- ligereza

## Plataforma
Monolito modular construido con Quarkus y guiado por DDD y arquitectura Hexagonal.

## Repositorios
Estructura separada para backend, web e infraestructura.

## Organización de carpetas
`modules/{users,events,spaces,pulse}` con subcarpetas `core/`, `app/`, `adapters/` y `migrations/`.

## Base de datos
Estrategia `schema-per-module`.

## Observabilidad mínima
Solo lo necesario para métricas, logs y tracing básicos.

## Evolución
Posible migración a "células" si el crecimiento lo exige.
