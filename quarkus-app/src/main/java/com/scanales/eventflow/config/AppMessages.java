package com.scanales.eventflow.config;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle("i18n")
public interface AppMessages {

    @Message("Mi Perfil · Homedir")
    String profile_title();

    @Message("Perfil Digital")
    String profile_eyebrow();

    @Message("Gestiona tu agenda, elige tus charlas favoritas y mantente al día con la comunidad.")
    String profile_intro();

    @Message("Actualizar")
    String btn_refresh();

    @Message("Salir")
    String btn_logout();

    @Message("Ver perfil")
    String btn_view_profile();

    @Message("Conectar GitHub")
    String btn_link_github();

    @Message("Tablero de Misiones")
    String btn_quest_board();

    @Message("Guardar Clase")
    String btn_save_class();

    @Message("Explore Community")
    String btn_explore_community();

    @Message("Todas")
    String btn_all();

    @Message("Asistidas")
    String btn_attended();

    @Message("Pendientes")
    String btn_pending();

    @Message("Cuenta de GitHub vinculada exitosamente.")
    String msg_github_linked();

    @Message("No se pudo vincular GitHub ({error}). Inténtalo de nuevo.")
    String msg_github_error(String error);

    @Message("Vincula GitHub para aparecer en la Comunidad y unirte con tu usuario.")
    String msg_github_required();

    @Message("Aún no vinculado.")
    String msg_no_github();

    @Message("(Vinculado)")
    String msg_linked();

    @Message("Correo")
    String label_email();

    @Message("Registered Talks")
    String label_registered_talks();

    @Message("Attended Talks")
    String label_attended_talks();

    @Message("Ratings")
    String label_ratings();

    @Message("Integraciones")
    String section_integrations();

    @Message("GitHub")
    String section_gitHub();

    @Message("Resumen")
    String section_resume();

    @Message("Progreso")
    String section_progress();

    @Message("Identidad")
    String section_identity();

    @Message("Comunidad")
    String section_community();

    @Message("Agenda")
    String section_agenda();

    @Message("Configuración")
    String section_settings();

    @Message("Nivel {level}")
    String resume_level(Object level);

    @Message("Experience: {current} XP / {total} XP")
    String resume_exp(int current, int total);

    @Message("{count} active initiatives and open collaborations.")
    String community_initiatives(int count);

    @Message("Has agregado {added} charlas a tu agenda, has asistido a {attended} y calificado {rated}.")
    String agenda_intro(int added, long attended, long rated);

    @Message("{days} días · {speakers} oradores")
    String agenda_days_speakers(int days, int speakers);

    @Message("Día {day}")
    String agenda_day(int day);

    @Message("Total: {count}")
    String quest_total(int count);

    @Message("Rank {difficulty}")
    String quest_rank(String difficulty);

    @Message("LEVEL {level}")
    String home_level(int level);

    @Message("{count} members")
    String meta_members(int count);

    @Message("{count} projects")
    String meta_projects(int count);

    @Message("Detail: {detail}")
    String community_error_detail(String detail);

    @Message("Contribution Score: {score}")
    String contribution_score(int score);

    @Message("Gamify <span class=\"hd-text-gradient\">Real Work</span>.<br>No Simulations.")
    String home_hero_title_new();

    @Message("Build a verifiable engineering profile by solving real Open Source issues. Earn XP, level up, and join the elite guild.")
    String home_hero_subtitle_new();

    @Message("HomeDir · Plataforma Comunitaria OSSantiago")
    String footer_platform();

    @Message("Construido con Quarkus · Qute · Open Source")
    String footer_tech_stack();

    @Message("Acerca de / Versión")
    String footer_about();

    // --- Events Page ---
    @Message("Eventos - HomeDir")
    String events_title();

    @Message("Agenda · HomeDir")
    String events_subtitle();

    @Message("Eventos y charlas")
    String events_hero_title();

    @Message("Revisa el calendario completo, destacados pasados y enlaces directos para cada edición.")
    String events_hero_desc();

    @Message("Próximos")
    String events_card_upcoming();

    @Message("Programados")
    String events_card_upcoming_desc();

    @Message("Pasados")
    String events_card_past();

    @Message("Referencias")
    String events_card_past_desc();

    @Message("Hoy")
    String events_card_today();

    @Message("Hora local")
    String events_card_today_desc();

    @Message("Próximos eventos")
    String events_section_upcoming_subtitle();

    @Message("En curso o por comenzar")
    String events_section_upcoming_title();

    @Message("No hay eventos próximos por ahora.")
    String events_empty_upcoming();

    @Message("Historial")
    String events_section_past_subtitle();

    @Message("Eventos pasados")
    String events_section_past_title();

    @Message("No hay registros pasados aún.")
    String events_empty_past();

    // --- Projects Page ---
    @Message("Proyectos · HomeDir")
    String projects_title();

    @Message("Roadmap · HomeDir")
    String projects_subtitle();

    @Message("Proyectos Activos")
    String projects_hero_title();

    @Message("Sigue el estado de los módulos y ve directamente al repositorio o sección.")
    String projects_hero_desc();

    @Message("Abrir repositorio")
    String btn_open_repo();

    @Message("Conectar GitHub para unirse")
    String btn_connect_join();

    @Message("Módulos")
    String projects_section_modules();

    @Message("Roadmap visible")
    String projects_section_roadmap();

    @Message("Backend")
    String project_backend_eyebrow();

    @Message("Plataforma básica: autenticación, perfiles y orquestación de módulos.")
    String project_backend_desc();

    @Message("En Producción")
    String badge_production();

    @Message("Abrir")
    String btn_open();

    @Message("Tiempo Real")
    String project_realtime_eyebrow();

    @Message("Notificaciones Globales")
    String project_realtime_title();

    @Message("Canal WebSocket y centro de notificaciones para eventos y alertas.")
    String project_realtime_desc();

    @Message("Beta")
    String badge_beta();

    @Message("Personas")
    String project_people_eyebrow();

    @Message("Directorio de miembros y onboarding con GitHub para contribuir.")
    String project_people_desc();

    @Message("En Diseño")
    String badge_design();

    @Message("Estás navegando como invitado.")
    String home_guest_warning();

    @Message("Invitado")
    String home_guest_name();

    @Message("Visitante")
    String home_guest_role();

    @Message("Contribuciones")
    String home_stat_contributions();

    @Message("Misiones")
    String nav_quests();

    @Message("Eventos")
    String nav_events();

    @Message("Proyectos")
    String nav_projects();

    @Message("Inicio")
    String nav_home();

    @Message("Únete")
    String home_btn_join();

    @Message("Proponer Charla")
    String home_btn_propose();

    @Message("Aldea Comunitaria")
    String home_village_title();

    @Message("Conecta con otros desarrolladores, comparte conocimiento y crezcan juntos.")
    String home_village_desc();

    @Message("Estadísticas de la Comunidad")
    String home_stats_title();

    @Message("Miembros")
    String home_stats_members();

    @Message("Total XP")
    String home_stats_xp();

    @Message("Misiones Completadas")
    String home_stats_quests();

    @Message("Proyectos Activos")
    String home_stats_projects();

    @Message("Unirse a la Comunidad")
    String btn_join_community();

    @Message("Comunidad")
    String nav_community();

    @Message("Última actividad de la comunidad")
    String home_community_activity();

    @Message("Únete a la comunidad para colaborar y crecer.")
    String home_community_desc();

    @Message("Explorar")
    String home_btn_explore();

    @Message("Lista")
    String home_btn_roster();

    @Message("Revisa los próximos eventos y charlas.")
    String home_events_desc();

    @Message("Próximos eventos")
    String home_events_activity();

    @Message("Asistir")
    String home_btn_attend();

    @Message("Agenda")
    String home_btn_schedule();

    @Message("Contribuye a proyectos open source.")
    String home_projects_desc();

    @Message("Actualizaciones del proyecto")
    String home_projects_activity();

    @Message("Plataforma por")
    String platform_by();

    @Message("El hub para comunidades de desarrolladores")
    String platform_tagline();

    @Message("Miembro")
    String role_member();

    @Message("Visitante")
    String role_visitor();

    @Message("No se encontraron charlas para este día.")
    String agenda_no_talks();

    @Message("Miembro Oficial")
    String badge_official_member();

    @Message("Completado")
    String badge_completed();

    @Message("En Progreso")
    String badge_in_progress();

    @Message("Ya eres miembro")
    String btn_already_member();

    @Message("Reclamar")
    String btn_claim();

    @Message("Cerrada")
    String btn_closed();

    @Message("Conectar Ahora")
    String btn_connect_now();

    @Message("Explorar Misiones")
    String btn_explore_quests();

    @Message("Instrucciones")
    String btn_instructions();

    @Message("Unirse al Gremio")
    String btn_join_guild();

    @Message("Ingresar con Google")
    String btn_login_google();

    @Message("Entrar o Unirse")
    String btn_login_join();

    @Message("Procesando...")
    String btn_request_processing();

    @Message("Buscar")
    String btn_search();

    @Message("Comenzar")
    String btn_start();

    @Message("Ver Detalles")
    String btn_view_details();

    @Message("Ver Directorio")
    String btn_view_directory();

    @Message("Ver PR")
    String btn_view_pr();

    @Message("Ocurrió un problema al cargar la comunidad.")
    String community_error_desc();

    @Message("Error de Comunidad")
    String community_error_title();

    @Message("Construye tu perfil de ingeniero resolviendo problemas reales.")
    String community_hero_desc();

    @Message("Comunidad Open Source Santiago")
    String community_hero_title();

    @Message("Únete a nuestra comunidad para acceder a contenido y eventos exclusivos.")
    String community_join_card_desc();

    @Message("Únete")
    String community_join_card_eyebrow();

    @Message("Conviértete en Miembro")
    String community_join_card_title();

    @Message("¡Te has unido exitosamente a la comunidad!")
    String community_joined_desc();

    @Message("¡Bienvenido!")
    String community_joined_title();

    @Message("Tu cuenta ahora está vinculada con GitHub.")
    String community_linked_desc();

    @Message("Cuenta Vinculada")
    String community_linked_title();

    @Message("Conoce a los increíbles miembros de nuestra comunidad.")
    String community_members_desc();

    @Message("Miembros")
    String community_members_eyebrow();

    @Message("Conecta, Colabora, Crece")
    String community_subtitle();

    @Message("Nuestra Comunidad")
    String community_title();

    @Message("Mejores Colaboradores")
    String community_top_contributors();

    @Message("No se encontraron miembros en el directorio.")
    String directory_empty();

    @Message("Directorio de Miembros")
    String directory_title();

    @Message("Repositorio GitHub")
    String header_alpha_repo();

    @Message("Esta es una versión alpha. Podría fallar.")
    String header_alpha_text();

    @Message("Header Navigation")
    String header_aria_label();

    @Message("System is currently degraded.")
    String header_system_degraded();

    @Message("Hero Subtitle")
    String hero_subtitle();

    @Message("Tu Gremio (Clase de Misión)")
    String identity_guild();

    @Message("Elige tu arquetipo para que la comunidad conozca tu rol principal.")
    String identity_intro();

    @Message("Interesante")
    String motivation_interesting();

    @Message("Aprendizaje")
    String motivation_learning();

    @Message("Seleccionar...")
    String motivation_placeholder();

    @Message("Orador")
    String motivation_speaker();

    @Message("Trabajo")
    String motivation_work();

    @Message("¿Estás seguro de que quieres eliminar esta charla?")
    String msg_confirm_delete();

    @Message("Error al eliminar")
    String msg_error_removing();

    @Message("Error al guardar")
    String msg_error_saving();

    @Message("Panel Administración")
    String nav_admin_panel();

    @Message("Conectar GitHub")
    String nav_connect_github();

    @Message("Ingresar")
    String nav_login();

    @Message("Salir")
    String nav_logout();

    @Message("Mi Perfil")
    String nav_my_profile();

    @Message("Notificaciones")
    String nav_notifications();

    @Message("Conectado como")
    String nav_signed_in_as();

    @Message("Colaborador")
    String progress_contributor();

    @Message("Completa misiones para desbloquear nuevos rangos.")
    String progress_intro();

    @Message("Leyenda")
    String progress_legend();

    @Message("Mentor")
    String progress_mentor();

    @Message("Novato")
    String progress_novice();

    @Message("Árbol de Progreso")
    String progress_tree();

    @Message("Descripción Pública")
    String public_description();

    @Message("Título Público")
    String public_title();

    @Message("Tablero de Misiones")
    String quest_board_eyebrow();

    @Message("Completa misiones para ganar XP e insignias.")
    String quest_board_intro();

    @Message("Tablero de Misiones")
    String quest_board_title();

    @Message("Ver Misiones")
    String quest_empty_cta_btn();

    @Message("No hay misiones disponibles por ahora.")
    String quest_empty_cta_text();

    @Message("Vuelve más tarde para ver nuevas misiones.")
    String quest_empty_desc();

    @Message("Sin Misiones")
    String quest_empty_title();

    @Message("Mis Misiones")
    String quest_filter_mine();

    @Message("Historial de Misiones")
    String resume_history();

    @Message("Aún no has completado ninguna misión.")
    String resume_no_history();

    @Message("Buscar")
    String search_aria_label();

    @Message("Buscar...")
    String search_placeholder();

    @Message("Idioma")
    String settings_language();

    @Message("Selecciona tu idioma preferido.")
    String settings_language_intro();

    @Message("Guardar Preferencias")
    String settings_save();

    @Message("Progreso de XP")
    String xp_progress();

}
