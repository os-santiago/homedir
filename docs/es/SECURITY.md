# Política de Seguridad

Homedir toma la seguridad de nuestros usuarios y colaboradores en serio. Si crees que has encontrado una vulnerabilidad, sigue el proceso a continuación para ayudarnos a solucionarla de forma rápida y responsable.

---

## Versiones soportadas (solo mayores)

Solo damos soporte a la línea de versión mayor actualmente activa. Cuando se publica una nueva versión mayor, las anteriores quedan sin soporte inmediatamente.

**Versión mayor activa actual: `2.x`**

| Versión | Soporte |
|-------:|-----------|
| 2.x | Activa |
| < 2.x | Sin soporte |

> Esta política mantiene el proyecto rápido y seguro, en línea con nuestro enfoque de desarrollo basado en trunk.

---

## Reportar una vulnerabilidad

**No abras un Issue o Discussion público para problemas de seguridad.**

Usa uno de estos canales privados:

1. **GitHub – Reporte privado de vulnerabilidad (preferido)**
   Ve a la pestaña **Security** del repositorio → **Report a vulnerability** y sigue el formulario.

2. **Email**
   Envía los detalles a **sergio.canales.e@gmail.com** con el asunto:
   `HOMEDIR SECURITY: <título corto>`

Incluye cuando aplique:
- Versiones afectadas (ej. `2.2.1`)
- Entorno (local/Docker/Kubernetes/OpenShift)
- Resumen del impacto (¿qué puede hacer un atacante?)
- Pasos de reproducción o prueba de concepto
- Logs/config relevantes (oculta secretos)
- Cualquier mitigación o workaround conocido

---

## Respuesta y tiempos

Apuntamos a los siguientes SLA:

- **Acuse de recibo:** dentro de **48 horas**
- **Triage y evaluación inicial:** dentro de **5 días hábiles**
- **Actualizaciones de estado:** al menos **semanales** hasta la resolución

**Objetivos de referencia** (alineados a nuestra escala de severidad P0–P3):

| Severidad | Meta de solución | Ventana de divulgación* |
|---------|-----------------|-------------------------|
| P0 (Crítica) | ASAP, típicamente ≤ **7 días** | Coordinada, típicamente ≤ **30 días** |
| P1 (Alta) | Típicamente ≤ **14–21 días** | Coordinada, típicamente ≤ **60 días** |
| P2 (Media) | Próximo lanzamiento programado | Coordinada, típicamente ≤ **90 días** |
| P3 (Baja) | Cuando sea posible | Notas agrupadas / siguiente release |

\* Practicamos **divulgación coordinada** con quien reporta.

---

## Divulgación y CVE

- Publicaremos un **GitHub Security Advisory** con detalles, créditos (si se desea) y versiones corregidas.
- Cuando aplique, solicitaremos un **CVE ID** y lo referiremos en el advisory y en las notas de versión.

---

## Refugio seguro para investigación de buena fe

No perseguiremos ni apoyaremos acciones legales para la **investigación de seguridad de buena fe** que:
- Evite violaciones de privacidad, degradación del servicio o destrucción/exfiltración de datos
- Respete los límites de tasa y use solo tus propias cuentas/datos
- Detenga las pruebas y reporte inmediatamente al encontrar una vulnerabilidad
- Mantenga los detalles confidenciales hasta que haya un arreglo y se acuerde una divulgación coordinada

Actividades fuera de alcance incluyen ingeniería social, ataques físicos, spam y DDoS.
