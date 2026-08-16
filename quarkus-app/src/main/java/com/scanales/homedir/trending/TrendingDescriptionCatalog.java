package com.scanales.homedir.trending;

import java.util.Map;

/**
 * Curated Spanish descriptions for well-known trending repositories. Keys are {@code owner/name}
 * (lowercase). Falls back to the original description when a repo is not present.
 */
public final class TrendingDescriptionCatalog {

  private static final Map<String, String> ES_DESCRIPTIONS =
      Map.ofEntries(
          Map.entry(
              "facebook/react",
              "Biblioteca declarativa, eficiente y flexible de JavaScript para construir interfaces de usuario."),
          Map.entry(
              "astral-sh/ruff",
              "Linter y formateador de Python extremadamente rápido, escrito en Rust."),
          Map.entry(
              "langchain-ai/langchain",
              "Construcción de aplicaciones con LLMs mediante composición."),
          Map.entry("vhdl/hdl-lang", "Un lenguaje de descripción de hardware."),
          Map.entry(
              "sindresorhus/awesome",
              "Listas increíbles sobre todo lo relacionado con el software."),
          Map.entry(
              "freeCodeCamp/freeCodeCamp",
              "freeCodeCamp.org es un proyecto de código abierto y sin fines de lucro que te ayuda a aprender a programar."),
          Map.entry(
              "vuejs/core",
              "Vue.js es un framework progresivo de JavaScript para construir interfaces de usuario."),
          Map.entry(
              "facebook/create-react-app",
              "Configuración de aplicaciones React modernas sin configuración de compilación."),
          Map.entry(
              "twbs/bootstrap",
              "El framework CSS más popular para desarrollar sitios y aplicaciones web responsivos."),
          Map.entry(
              "microsoft/vscode",
              "Editor de código fuente optimizado para crear aplicaciones web modernas."),
          Map.entry(
              "flutter/flutter",
              "Kit de herramientas de UI de Google para crear aplicaciones nativas hermosas y compiladas."),
          Map.entry(
              "tensorflow/tensorflow",
              "Una plataforma de aprendizaje automático de código abierto de extremo a extremo."),
          Map.entry(
              "pytorch/pytorch",
              "Tensores y redes neuronales dinámicas en Python con una fuerte aceleración por GPU."),
          Map.entry(
              "kubernetes/kubernetes",
              "Sistema de orquestación de contenedores de código abierto para automatizar el despliegue, escalado y gestión."),
          Map.entry(
              "openai/whisper",
              "Modelo de reconocimiento de voz robusto para transcripción y traducción multilingüe."),
          Map.entry(
              "microsoft/terminal",
              "El nuevo terminal de Windows, una experiencia moderna y rápida para el símbolo del sistema."),
          Map.entry(
              "n8n-io/n8n",
              "Automatización de flujos de trabajo con interfaz visual y extensibilidad ilimitada."),
          Map.entry(
              "github/docs",
              "El repositorio público de la documentación de GitHub, que colabora con la comunidad."),
          Map.entry(
              "vercel/next.js",
              "Framework de React para la web, con renderizado del lado del servidor y generación estática."),
          Map.entry(
              "facebook/jest", "Framework de pruebas de JavaScript con enfoque en la simplicidad."),
          Map.entry(
              "npm/cli",
              "La CLI de npm, el administrador de paquetes de JavaScript más grande del mundo."));

  private TrendingDescriptionCatalog() {}

  /** Returns the curated Spanish description for owner/name, or null if not present. */
  public static String get(String owner, String name) {
    if (owner == null || name == null) {
      return null;
    }
    return ES_DESCRIPTIONS.get((owner + "/" + name).toLowerCase());
  }
}
