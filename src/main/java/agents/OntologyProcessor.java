package agents;

import behaviours.ontologybehaviours.FoodOntology;
import utils.IngredientStemmer;

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
     * Delega en IngredientStemmer para mantener las reglas centralizadas.
     * Se expone como static para no romper OntologyAgentTest que lo llama directamente.
     */
    static String stem(String word) {
        return IngredientStemmer.stem(word);
    }
}
