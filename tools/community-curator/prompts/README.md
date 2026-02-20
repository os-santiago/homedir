# Community Curator Prompts

Prompt templates for LLM-assisted Community Picks curation.

## Files

- `system-curator.md`: system-level operating constraints.
- `user-curation-batch.md`: batch discovery + ranking prompt.
- `user-normalize-item.md`: single-item normalization into Homedir YAML schema.

## Usage

1. Start with `system-curator.md`.
2. Use `user-curation-batch.md` to build a candidate set.
3. Use `user-normalize-item.md` per selected item.
4. Save YAML files and validate with the regular curator/deploy flow.
