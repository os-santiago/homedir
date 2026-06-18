#!/usr/bin/env python3
"""
Discord Bot para crear y gestionar issues de GitHub
Implementa comandos slash y notificaciones automáticas
"""

import discord
from discord import app_commands
from discord.ext import tasks
import os
import json
from datetime import datetime
from typing import Optional, List
from github import Github, GithubException
import asyncio

# Configuración desde variables de entorno
DISCORD_BOT_TOKEN = os.getenv('DISCORD_BOT_TOKEN')
GITHUB_TOKEN = os.getenv('GITHUB_TOKEN')
GITHUB_REPO = os.getenv('GITHUB_REPO')

# Archivo para almacenar el mapeo de usuarios a issues
MAPPING_FILE = 'issue_mapping.json'

# Validar configuración
if not all([DISCORD_BOT_TOKEN, GITHUB_TOKEN, GITHUB_REPO]):
    raise ValueError(
        "Faltan variables de entorno requeridas: "
        "DISCORD_BOT_TOKEN, GITHUB_TOKEN, GITHUB_REPO"
    )


class IssueMapping:
    """Gestiona el mapeo entre usuarios de Discord e issues de GitHub"""

    def __init__(self, filepath: str):
        self.filepath = filepath
        self.data = self._load()

    def _load(self) -> dict:
        """Carga el mapeo desde el archivo JSON"""
        try:
            if os.path.exists(self.filepath):
                with open(self.filepath, 'r', encoding='utf-8') as f:
                    return json.load(f)
        except Exception as e:
            print(f"Error cargando mapeo: {e}")
        return {}

    def _save(self):
        """Guarda el mapeo al archivo JSON"""
        try:
            with open(self.filepath, 'w', encoding='utf-8') as f:
                json.dump(self.data, f, indent=2, ensure_ascii=False)
        except Exception as e:
            print(f"Error guardando mapeo: {e}")

    def add_issue(self, discord_user_id: int, issue_number: int, issue_state: str = 'open'):
        """Registra un issue para un usuario"""
        user_key = str(discord_user_id)
        if user_key not in self.data:
            self.data[user_key] = []

        self.data[user_key].append({
            'issue_number': issue_number,
            'state': issue_state,
            'created_at': datetime.utcnow().isoformat(),
            'notified_closed': False,
            'notified_ready': False
        })
        self._save()

    def get_user_issues(self, discord_user_id: int) -> List[dict]:
        """Obtiene todos los issues de un usuario"""
        user_key = str(discord_user_id)
        return self.data.get(user_key, [])

    def update_issue_state(self, discord_user_id: int, issue_number: int,
                          new_state: str, notified_closed: bool = False,
                          notified_ready: bool = False):
        """Actualiza el estado de un issue"""
        user_key = str(discord_user_id)
        if user_key in self.data:
            for issue in self.data[user_key]:
                if issue['issue_number'] == issue_number:
                    issue['state'] = new_state
                    if notified_closed:
                        issue['notified_closed'] = notified_closed
                    if notified_ready:
                        issue['notified_ready'] = notified_ready
                    self._save()
                    break

    def get_all_tracked_issues(self) -> List[tuple]:
        """Retorna todos los issues rastreados como tuplas (user_id, issue_data)"""
        result = []
        for user_id, issues in self.data.items():
            for issue in issues:
                result.append((int(user_id), issue))
        return result


class DiscordGitHubBot(discord.Client):
    """Bot de Discord para gestionar issues de GitHub"""

    def __init__(self):
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(intents=intents)

        self.tree = app_commands.CommandTree(self)
        self.github = Github(GITHUB_TOKEN)
        self.repo = self.github.get_repo(GITHUB_REPO)
        self.mapping = IssueMapping(MAPPING_FILE)

    async def setup_hook(self):
        """Configuración inicial del bot"""
        await self.tree.sync()
        print(f"Comandos sincronizados para {self.user}")

    async def on_ready(self):
        """Evento cuando el bot está listo"""
        print(f'Bot conectado como {self.user}')
        print(f'Conectado a repositorio: {GITHUB_REPO}')
        if not self.check_issues_task.is_running():
            self.check_issues_task.start()

    @tasks.loop(minutes=5)
    async def check_issues_task(self):
        """Tarea en segundo plano que revisa el estado de los issues cada 5 minutos"""
        print(f"[{datetime.now()}] Revisando estado de issues...")

        try:
            tracked_issues = self.mapping.get_all_tracked_issues()

            for discord_user_id, issue_data in tracked_issues:
                issue_number = issue_data['issue_number']

                try:
                    # Obtener issue actual de GitHub
                    gh_issue = self.repo.get_issue(issue_number)

                    # Verificar si está cerrado
                    if gh_issue.state == 'closed' and not issue_data.get('notified_closed', False):
                        await self._notify_user(
                            discord_user_id,
                            f"Tu issue #{issue_number} ha sido cerrado: {gh_issue.title}\n"
                            f"URL: {gh_issue.html_url}"
                        )
                        self.mapping.update_issue_state(
                            discord_user_id, issue_number, 'closed', notified_closed=True
                        )

                    # Verificar si tiene la etiqueta 'listo-para-validacion'
                    label_names = [label.name for label in gh_issue.labels]
                    if 'listo-para-validacion' in label_names and not issue_data.get('notified_ready', False):
                        await self._notify_user(
                            discord_user_id,
                            f"Tu issue #{issue_number} está listo para validación: {gh_issue.title}\n"
                            f"URL: {gh_issue.html_url}"
                        )
                        self.mapping.update_issue_state(
                            discord_user_id, issue_number, gh_issue.state, notified_ready=True
                        )

                except GithubException as e:
                    print(f"Error obteniendo issue #{issue_number}: {e}")
                except Exception as e:
                    print(f"Error procesando issue #{issue_number}: {e}")

        except Exception as e:
            print(f"Error en check_issues_task: {e}")

    async def _notify_user(self, discord_user_id: int, message: str):
        """Envía una notificación DM a un usuario"""
        try:
            user = await self.fetch_user(discord_user_id)
            if user:
                await user.send(message)
                print(f"Notificación enviada a usuario {discord_user_id}")
        except discord.Forbidden:
            print(f"No se puede enviar DM al usuario {discord_user_id} (DMs deshabilitados)")
        except Exception as e:
            print(f"Error enviando notificación a {discord_user_id}: {e}")


# Instanciar el bot
bot = DiscordGitHubBot()


@bot.tree.command(name="ayuda", description="Crear un nuevo issue en GitHub")
@app_commands.describe(
    titulo="Título del issue",
    descripcion="Descripción detallada del problema o solicitud",
    prioridad="Prioridad del issue (P1=Crítica, P2=Alta, P3=Media, P4=Baja, P5=Mínima)",
    etiquetas="Etiquetas separadas por comas (opcional)"
)
async def ayuda(
    interaction: discord.Interaction,
    titulo: str,
    descripcion: str,
    prioridad: str,
    etiquetas: Optional[str] = None
):
    """Comando para crear un issue en GitHub"""

    # Validar prioridad
    prioridades_validas = ['P1', 'P2', 'P3', 'P4', 'P5']
    prioridad = prioridad.upper()

    if prioridad not in prioridades_validas:
        await interaction.response.send_message(
            f"Prioridad inválida. Usa: {', '.join(prioridades_validas)}",
            ephemeral=True
        )
        return

    await interaction.response.defer(ephemeral=False)

    try:
        # Preparar etiquetas
        labels = [prioridad]
        if etiquetas:
            labels.extend([label.strip() for label in etiquetas.split(',') if label.strip()])

        # Preparar cuerpo del issue
        issue_body = f"{descripcion}\n\n---\n"
        issue_body += f"**Creado por:** {interaction.user.name} (Discord)\n"
        issue_body += f"**Prioridad:** {prioridad}\n"
        issue_body += f"**Fecha:** {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')} UTC"

        # Crear issue en GitHub
        issue = bot.repo.create_issue(
            title=titulo,
            body=issue_body,
            labels=labels
        )

        # Registrar en el mapeo
        bot.mapping.add_issue(interaction.user.id, issue.number)

        # Responder al usuario
        await interaction.followup.send(
            f"Issue creado exitosamente!\n"
            f"**Número:** #{issue.number}\n"
            f"**Título:** {titulo}\n"
            f"**Prioridad:** {prioridad}\n"
            f"**URL:** {issue.html_url}\n\n"
            f"Recibirás notificaciones cuando el issue sea cerrado o esté listo para validación."
        )

        print(f"Issue #{issue.number} creado por {interaction.user.name}")

    except GithubException as e:
        error_msg = f"Error al crear el issue en GitHub: {e.data.get('message', str(e))}"
        print(error_msg)
        await interaction.followup.send(
            f"Error al crear el issue. Por favor, intenta de nuevo más tarde.\n"
            f"Detalles: {error_msg}",
            ephemeral=True
        )

    except Exception as e:
        error_msg = f"Error inesperado: {str(e)}"
        print(error_msg)
        await interaction.followup.send(
            f"Ocurrió un error inesperado. Por favor, contacta al administrador.\n"
            f"Detalles: {error_msg}",
            ephemeral=True
        )


@bot.tree.command(name="mis-issues", description="Ver tus issues de GitHub")
async def mis_issues(interaction: discord.Interaction):
    """Comando para listar los issues del usuario"""

    await interaction.response.defer(ephemeral=True)

    try:
        user_issues = bot.mapping.get_user_issues(interaction.user.id)

        if not user_issues:
            await interaction.followup.send(
                "No tienes issues registrados.",
                ephemeral=True
            )
            return

        # Construir lista de issues
        issues_list = []
        for issue_data in user_issues:
            issue_number = issue_data['issue_number']

            try:
                gh_issue = bot.repo.get_issue(issue_number)

                status = "Abierto" if gh_issue.state == 'open' else "Cerrado"
                labels = ', '.join([label.name for label in gh_issue.labels])

                # Verificar si tiene la etiqueta especial
                has_ready_label = 'listo-para-validacion' in [l.name for l in gh_issue.labels]
                ready_indicator = " [Listo para validación]" if has_ready_label else ""

                issues_list.append(
                    f"**#{issue_number}** - {gh_issue.title}\n"
                    f"  Estado: {status}{ready_indicator}\n"
                    f"  Etiquetas: {labels}\n"
                    f"  URL: {gh_issue.html_url}"
                )

            except GithubException as e:
                issues_list.append(
                    f"**#{issue_number}** - Error al obtener detalles: {e}"
                )

        # Enviar respuesta (dividir si es muy largo)
        response = f"Tus issues ({len(user_issues)}):\n\n" + "\n\n".join(issues_list)

        # Discord tiene límite de 2000 caracteres
        if len(response) > 2000:
            # Dividir en múltiples mensajes
            await interaction.followup.send(
                f"Tus issues ({len(user_issues)}): (mostrando primeros resultados)",
                ephemeral=True
            )
            for issue_text in issues_list[:10]:  # Limitar a 10 primeros
                await interaction.followup.send(issue_text, ephemeral=True)
        else:
            await interaction.followup.send(response, ephemeral=True)

    except Exception as e:
        error_msg = f"Error obteniendo tus issues: {str(e)}"
        print(error_msg)
        await interaction.followup.send(
            f"Ocurrió un error al obtener tus issues.\n"
            f"Detalles: {error_msg}",
            ephemeral=True
        )


# Iniciar el bot
if __name__ == "__main__":
    try:
        print("Iniciando Discord bot...")
        print(f"Repositorio: {GITHUB_REPO}")
        bot.run(DISCORD_BOT_TOKEN)
    except Exception as e:
        print(f"Error fatal al iniciar el bot: {e}")
        raise
