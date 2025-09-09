# Política de calidad de código

Este repositorio ejecuta un análisis estático en cada solicitud de extracción a través de la calidad ** de PR - Análisis estático ** Flujo de trabajo. Solo el código tocado por el PR se escanea para mantener la retroalimentación rápida (menos de ~ 4 minutos).

## Severidad y activación

| Severidad | Significado | Efecto PR |
| --- | --- | --- |
| Crítico / alto | Probable defecto, problema de seguridad o comportamiento incorrecto | Bloquea el PR hasta que se solucione |
| Medio | Problema potencial o fuga de recursos | Reportado como advertencia, PR continúa |
| Bajo / estilo | Problemas menores o de estilo | Solo informativo |

Solo los hallazgos introducidos en una solicitud de extracción están cerrados. Los hallazgos existentes en 'Main` se rastrean en la línea de base y no bloquean a menos que se toquen.

## Resumen de relaciones públicas

Cada solicitud de extracción muestra una tarjeta de calidad con:

- Cuenta de nuevos hallazgos por severidad.
- Los tres hallazgos superiores, cada uno con una explicación de una línea y una solución sugerida.
- Un enlace de "Ver detalles" que apunta al informe completo del escáner.

Los mensajes usan un lenguaje sencillo, por ejemplo: "Riesgo de Npe Si` Foo` Viene Nulo. Sugencia: Validar Antes de Acceder ".

## línea de base y propiedad

La línea de base de los hallazgos existentes vive en `config/calidad/línea de base.sarif`. Los problemas ya presentes en 'Main` se ignoran a menos que las líneas afectadas cambien. Los autores pueden optar por "tomar posesión" y arreglar los hallazgos de referencia en sus relaciones públicas.

## Exclusiones y supresiones

- El código generado y los directorios de compilación se excluyen del análisis.
- Suprimir una regla solo con justificación:

  `` `Java
  // Quality-ignore-Next-Line: Razon
  `` `` ``

  o `@suppresswarnings (" regla ")` más un comentario. Todas las supresiones deben ser revisables y rastreables.

## flujo de trabajo de triaje

1. ** Refactor ** Cuando el hallazgo es válido.
2. ** Suprimir con justificación ** Si es un riesgo falso positivo o aceptable.
3. ** Escala ** al equipo cuando sea incierto o la regla necesita ajuste.

## Higiene de dependencia

Solicite solicitudes que modifiquen cualquier `pom.xml` activar la calidad ** PR - dependencias ** trabajo. Proporciona retroalimentación rápida (objetivo ≤ 4 min) sobre los problemas de dependencia de Maven.

- ** Bloques cuando la activación está aplicando **: dependencias duplicadas, rangos de versión abiertos, convergencia rota o coincidencia de versión Java.
-** Informes ** (modo de advertencia): dependencias no utilizadas o no declaradas detectadas por `Maven-dependency-plugin: analizar`.
- Las listas de resumen del trabajo agregan, actualizan o eliminaron dependencias más cualquier violación con sugerencias.

### Cómo arreglar

- Alinee las versiones en `DependencyManagement` o BOM.
- Eliminar duplicados o dependencias no utilizadas.
- Mueva las declaraciones a 'DependencyManagement' cuando las versiones divergen.

### Ejecutar localmente

`` `Bash
./dev/deps-check.sh
`` `` ``

Revise la salida y resuelva las advertencias antes de abrir un PR.

## Hallazgos comunes y cómo resolver

- ** Riesgo de puntero nulo **: p. llamar a un método en un objeto posiblemente nulo. * Corrección:* Verifique si hay nulo o use `opcional`.
- ** Problema de concurrencia **: p. Acceso no sincronizado al estado mutable compartido. * FIJO:* Sincronice o use estructuras seguras de hilo.
- ** Recurso no cerrado **: p. Abrir un archivo/transmisión sin cerrar. * FIJO:* Use el try-with recursos o cierre en el bloque finalmente.
- ** Problema de seguridad **: E.G. Uso de la entrada no confiable en consultas o registros. * FIJO:* Desinfectación de la entrada o use declaraciones preparadas.
- ** olor a rendimiento **: p. Concatenación de cadena repetida en un bucle. * FIJO:* Use `StringBuilder` o algoritmo más eficiente.

## solicitando excepciones

Si una regla se excluye en todo el repositorio, abra un problema que describe la regla y la justificación. El equipo revisará y actualizará la lista de exclusión si corresponde.