# EventFlow site (Chirpy)

Sitio estático generado con [Jekyll](https://jekyllrb.com/) y el tema [Chirpy](https://chirpy.cotes.page/).

## Requisitos
- Ruby 3.2 o superior
- Bundler 2.4+

## Uso local

```bash
bundle install
bundle exec jekyll serve --config _config.yml,_config.dev.yml
```

El sitio estará disponible en `http://localhost:4000`.

## Despliegue
El workflow [`Deploy documentation site`](../.github/workflows/pages.yml) construye y publica automáticamente el sitio en GitHub Pages cuando hay cambios en `website/`.
