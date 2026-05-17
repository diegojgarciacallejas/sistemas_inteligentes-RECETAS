# GraphAgent y RecommendationAgent

## Descripción general

Esta parte del sistema multiagente implementa:

- `GraphAgent`
- `RecommendationAgent`

El objetivo es calcular recomendaciones de recetas personalizadas a partir de los ingredientes disponibles del usuario.

---

# GraphAgent

## Responsabilidad

El `GraphAgent` construye relaciones entre:

- ingredientes
- recetas

y calcula una similitud (`graphScore`) entre los ingredientes del usuario y los ingredientes necesarios para cada receta.

---

## Qué recibe

Recibe información procesada desde:

```text
OntologyAgent