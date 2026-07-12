# Homedir Administrative CLI Tool

Esta carpeta contiene la herramienta administrativa local (`homedir-cli`) en Python para gestionar los módulos de la plataforma Homedir a través del backend REST seguro.

## Requisitos Previos

- Python 3.10+
- Servidor Quarkus de Homedir ejecutándose en local o producción.

## Instalación y Configuración

1. Instale las dependencias de Python necesarias:
   ```bash
   pip install requests click
   ```
   *(Opcional: instale `rich` para formatear y visualizar las tablas con estilos y colores enriquecidos: `pip install rich`)*

2. Asegúrese de que el backend de Quarkus se está ejecutando localmente:
   ```bash
   mvn -f quarkus-app/pom.xml quarkus:dev
   ```

3. Configure su token de API de administración en las variables de entorno de su terminal:
   - En Windows (PowerShell):
     ```powershell
     $env:HOMEDIR_ADMIN_TOKEN="su-token-aqui"
     ```
   - En Linux / macOS:
     ```bash
     export HOMEDIR_ADMIN_TOKEN="su-token-aqui"
     ```

## Referencia de Comandos

La herramienta CLI (`cli.py`) ofrece varios subcomandos para interactuar con la plataforma:

### 1. Verificar Estado y Conectividad
Obtenga la verificación de permisos y la salud general del backend:
```bash
python tools/homedir-cli/cli.py status
```

### 2. Listar Eventos
Muestra una tabla con todos los eventos registrados, sus fechas y su estado actual:
```bash
python tools/homedir-cli/cli.py events
```

### 3. Listar Propuestas del CFP (Call for Papers)
Muestra todas las propuestas de ponencias enviadas por la comunidad cruzando todos los eventos:
```bash
python tools/homedir-cli/cli.py cfp
```

### 4. Listar Solicitudes de Voluntarios (CFV)
Muestra una tabla con los detalles y estado de aprobación de los voluntarios postulados:
```bash
python tools/homedir-cli/cli.py volunteers
```

### 5. Resumen de Métricas
Obtenga un informe en tiempo real de vistas de eventos, ponencias registradas y número de asistentes estimados:
```bash
python tools/homedir-cli/cli.py metrics
```

## Opciones Globales

- `--api-url`: Define una dirección de API diferente (por defecto es `http://localhost:8080`).
  Ejemplo:
  ```bash
  python tools/homedir-cli/cli.py --api-url https://homedir-dev.opensourcesantiago.io status
  ```
- `--token`: Proporciona el token directamente en la línea de comandos en lugar de usar la variable de entorno.
