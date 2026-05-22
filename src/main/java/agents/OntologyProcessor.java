package agents;

import behaviours.ontologybehaviours.FoodOntology;

import java.util.ArrayList;
import java.util.List;

public class OntologyProcessor {

    public String process(String input) {
        String userIngredients = "";
        String recipesValue    = "";
        List<String> otherLines = new ArrayList<>();

        for (String line : input.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (line.startsWith("userIngredients=")) {
                userIngredients = line.replace("userIngredients=", "").trim();
            } else if (line.startsWith("recipes=")) {
                recipesValue = line.replace("recipes=", "").trim();
            } else {
                otherLines.add(line);
            }
        }

        // Construir lista de ingredientes del usuario NORMALIZADOS (plurales reducidos a raíz)
        // Así "eggs" y "egg" se tratan como el mismo ingrediente.
        List<String> userIngList = new ArrayList<>();
        for (String ing : userIngredients.split(",")) {
            String stemmed = stem(ing.trim().toLowerCase());
            if (!stemmed.isEmpty()) userIngList.add(stemmed);
        }

        StringBuilder enrichedRecipes = new StringBuilder("recipes=");
        boolean firstRecipe = true;

        if (!recipesValue.isEmpty()) {
            for (String entry : recipesValue.split(";")) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;

                String   recipeName        = parts[0].trim();
                String[] recipeIngredients = parts[1].split(",");

                List<String> enrichedIngredients = new ArrayList<>();
                for (String ing : recipeIngredients) {
                    String normalized = ing.trim().toLowerCase();
                    if (normalized.isEmpty()) continue;

                    enrichedIngredients.add(normalized);

                    // Comparar usando la raíz para cubrir egg/eggs, tomato/tomatoes, etc.
                    String stemmedNorm = stem(normalized);
                    if (!userIngList.contains(stemmedNorm)) {
                        // El usuario no tiene este ingrediente (ni en forma plural/singular)
                        // → buscar sustitutos en FoodOntology usando la forma canónica (raíz)
                        List<String> substitutes = FoodOntology.findSubstitutes(stemmedNorm, userIngList);
                        if (!substitutes.isEmpty()) {
                            System.out.println("OntologyAgent: '" + normalized
                                    + "' puede sustituirse por '" + substitutes.get(0) + "'");
                            enrichedIngredients.add(substitutes.get(0));
                        }
                    }
                }

                if (!firstRecipe) enrichedRecipes.append(";");
                firstRecipe = false;
                enrichedRecipes.append(recipeName).append(":")
                        .append(String.join(",", enrichedIngredients));
            }
        }

        StringBuilder output = new StringBuilder();
        output.append("userIngredients=").append(userIngredients).append("\n");
        output.append(enrichedRecipes).append("\n");
        for (String line : otherLines) {
            output.append(line).append("\n");
        }
        return output.toString();
    }

    /**
     * Stemmer de sufijos para nombres de ingredientes en inglés.
     *
     * Cubre los casos de pluralización más comunes en recetas:
     *   eggs       → egg        (-s)
     *   mushrooms  → mushroom   (-s)
     *   tomatoes   → tomato     (-oes)
     *   potatoes   → potato     (-oes)
     *   berries    → berry      (-ies)
     *   cherries   → cherry     (-ies)
     *   cloves     → clove      (-s)
     *   onions     → onion      (-s)
     *
     * Casos protegidos (no se despluralizan):
     *   peas       → peas       (termina en -as)
     *   asparagus  → asparagus  (termina en -us)
     *   grass      → grass      (termina en -ss)
     *
     * Ingredientes compuestos ("olive oil", "soy sauce") se devuelven sin cambios.
     */
    static String stem(String word) {
        if (word == null || word.isEmpty()) return "";
        word = word.trim().toLowerCase();
        // Ingrediente compuesto: no tocar ("olive oil", "coconut milk")
        if (word.contains(" ")) return word;

        int len = word.length();

        // -ies → -y : berries→berry, cherries→cherry, jalapeños→jalapeño
        if (len > 3 && word.endsWith("ies"))
            return word.substring(0, len - 3) + "y";

        // -oes → -o : tomatoes→tomato, potatoes→potato
        if (len > 4 && word.endsWith("oes"))
            return word.substring(0, len - 2);

        // -s → eliminar, con protecciones:
        //   -ss  : bass, grass
        //   -us  : asparagus, citrus
        //   -is  : (raro en ingredientes)
        //   -as  : peas
        if (len > 3 && word.endsWith("s")
                && !word.endsWith("ss")
                && !word.endsWith("us")
                && !word.endsWith("is")
                && !word.endsWith("as"))
            return word.substring(0, len - 1);

        return word;
    }
}
