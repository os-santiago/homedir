"""Utility script for managing the ElevenLabs conversational AI knowledge base."""
from __future__ import annotations

import os
from typing import Dict, List, Optional

from elevenlabs.client import ElevenLabs


class KnowledgeBaseCleaner:
    """Cliente para limpiar y gestionar la base de conocimiento."""

    def __init__(self, api_key: Optional[str] = None) -> None:
        self.api_key = api_key or os.getenv("ELEVENLABS_API_KEY")
        if not self.api_key:
            raise ValueError(
                "La API key de ElevenLabs no está configurada. Define ELEVENLABS_API_KEY"
                " en el entorno o pásala explícitamente al constructor."
            )
        self.client = ElevenLabs(api_key=self.api_key)

    def list_all_documents(self) -> List[Dict[str, object]]:
        """Lista todos los documentos en la base de conocimiento."""
        try:
            response = self.client.conversational_ai.knowledge_base.list()
            documents: List[Dict[str, object]] = []

            print(f"\U0001F4C4 Encontrados {len(response.documents)} documentos:")
            for doc in response.documents:
                metadata = getattr(doc, "metadata", None)
                doc_info = {
                    "id": doc.id,
                    "name": doc.name,
                    "type": doc.type,
                    "size": getattr(metadata, "size_bytes", 0) if metadata else 0,
                }
                documents.append(doc_info)
                print(
                    f"  - {doc.name} (ID: {doc.id}) - Tipo: {doc.type}"
                    f" - Tamaño: {doc_info['size']} bytes"
                )

            return documents
        except Exception as exc:  # pylint: disable=broad-except
            print(f"\u274c Error listando documentos: {exc}")
            return []

    def delete_document(self, document_id: str) -> bool:
        """Elimina un documento específico."""
        try:
            knowledge_base = self.client.conversational_ai.knowledge_base

            # SDKs anteriores exponían el método delete directamente en
            # `knowledge_base`. Las versiones recientes lo movieron a
            # `knowledge_base.documents.delete`. Intentamos ambas opciones de
            # forma segura para mantener compatibilidad hacia atrás.
            delete_method = getattr(knowledge_base, "delete", None)

            if not callable(delete_method):
                documents_client = getattr(knowledge_base, "documents", None)
                delete_method = getattr(documents_client, "delete", None)

            if not callable(delete_method):
                raise AttributeError(
                    "El SDK de ElevenLabs no expone un método de borrado compatible."
                )

            # Algunas versiones aceptan el id como argumento posicional y otras
            # requieren palabras clave. Probamos ambos enfoques antes de fallar.
            try:
                delete_method(document_id)
            except TypeError:
                for key in ("document_id", "knowledge_base_document_id", "id"):
                    try:
                        delete_method(**{key: document_id})
                        break
                    except TypeError:
                        continue
                else:
                    raise

            print(f"\u2705 Documento eliminado: {document_id}")
            return True
        except Exception as exc:  # pylint: disable=broad-except
            print(f"\u274c Error eliminando documento {document_id}: {exc}")
            return False

    def delete_multiple_documents(self, document_ids: List[str]) -> Dict[str, List[str]]:
        """Elimina múltiples documentos."""
        results: Dict[str, List[str]] = {"success": [], "failed": []}

        print(f"\U0001F5D1\ufe0f Eliminando {len(document_ids)} documentos...")

        for doc_id in document_ids:
            if self.delete_document(doc_id):
                results["success"].append(doc_id)
            else:
                results["failed"].append(doc_id)

        print(f"\u2705 Eliminados: {len(results['success'])}")
        print(f"\u274c Fallidos: {len(results['failed'])}")

        return results

    def clear_all_knowledge_base(self, confirm: bool = False) -> Dict[str, List[str]]:
        """Elimina todos los documentos de la base de conocimiento."""
        if not confirm:
            print("\u26a0\ufe0f ADVERTENCIA: Esto eliminará TODOS los documentos.")
            confirmation = input("Escribe 'CONFIRMAR' para continuar: ")
            if confirmation != "CONFIRMAR":
                print("\u274c Operación cancelada")
                return {"success": [], "failed": []}

        print("\U0001F9F9 Limpiando toda la base de conocimiento...")

        documents = self.list_all_documents()

        if not documents:
            print("\u2705 La base de conocimiento ya está vacía")
            return {"success": [], "failed": []}

        document_ids = [doc["id"] for doc in documents]
        results = self.delete_multiple_documents(document_ids)

        print("\U0001F389 ¡Limpieza completa!")
        return results

    def delete_by_type(self, doc_type: str) -> Dict[str, List[str]]:
        """Elimina documentos por tipo (file, url, text)."""
        documents = self.list_all_documents()
        filtered_docs = [doc for doc in documents if doc["type"] == doc_type]

        if not filtered_docs:
            print(f"\U0001F4ED No se encontraron documentos del tipo '{doc_type}'")
            return {"success": [], "failed": []}

        print(
            f"\U0001F3AF Eliminando {len(filtered_docs)} documentos del tipo '{doc_type}'..."
        )

        document_ids = [doc["id"] for doc in filtered_docs]
        return self.delete_multiple_documents(document_ids)

    def delete_by_name_pattern(self, pattern: str) -> Dict[str, List[str]]:
        """Elimina documentos que contengan un patrón en el nombre."""
        documents = self.list_all_documents()
        filtered_docs = [doc for doc in documents if pattern.lower() in doc["name"].lower()]

        if not filtered_docs:
            print(
                f"\U0001F4ED No se encontraron documentos que contengan '{pattern}'"
            )
            return {"success": [], "failed": []}

        print(
            f"\U0001F3AF Eliminando {len(filtered_docs)} documentos que contengan '{pattern}'..."
        )

        document_ids = [doc["id"] for doc in filtered_docs]
        return self.delete_multiple_documents(document_ids)


def main() -> None:
    """Punto de entrada para la utilidad de limpieza."""
    try:
        cleaner = KnowledgeBaseCleaner()
    except ValueError as exc:
        print(f"\u274c {exc}")
        return

    print("\U0001F680 Cliente de Limpieza de Knowledge Base")
    print("=" * 50)

    while True:
        print("\n\U0001F4CB Opciones disponibles:")
        print("1. Listar todos los documentos")
        print("2. Eliminar documento específico")
        print("3. Limpiar toda la base de conocimiento")
        print("4. Eliminar por tipo (file/url/text)")
        print("5. Eliminar por patrón de nombre")
        print("6. Salir")

        choice = input("\nSelecciona una opción (1-6): ").strip()

        if choice == "1":
            cleaner.list_all_documents()
        elif choice == "2":
            doc_id = input("Ingresa el ID del documento a eliminar: ").strip()
            if doc_id:
                cleaner.delete_document(doc_id)
        elif choice == "3":
            cleaner.clear_all_knowledge_base()
        elif choice == "4":
            doc_type = input("Ingresa el tipo (file/url/text): ").strip()
            if doc_type in {"file", "url", "text"}:
                cleaner.delete_by_type(doc_type)
            else:
                print("\u274c Tipo inválido. Usa: file, url o text")
        elif choice == "5":
            pattern = input("Ingresa el patrón a buscar en nombres: ").strip()
            if pattern:
                cleaner.delete_by_name_pattern(pattern)
        elif choice == "6":
            print("\U0001F44B ¡Hasta luego!")
            break
        else:
            print("\u274c Opción inválida")


if __name__ == "__main__":
    main()
