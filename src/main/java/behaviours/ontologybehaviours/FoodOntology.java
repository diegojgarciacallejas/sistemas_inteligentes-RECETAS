package ontology;

import java.util.*;

public class FoodOntology {

    // Mapa ingrediente → categoría
    private static final Map<String, FoodCategory> CATEGORIES = new HashMap<>();

    // Lista de reglas de sustitución
    private static final List<SubstitutionRule> SUBSTITUTIONS = new ArrayList<>();

    static {
        // PROTEÍNAS
        CATEGORIES.put("chicken", FoodCategory.PROTEIN);
        CATEGORIES.put("beef", FoodCategory.PROTEIN);
        CATEGORIES.put("pork", FoodCategory.PROTEIN);
        CATEGORIES.put("turkey", FoodCategory.PROTEIN);
        CATEGORIES.put("tuna", FoodCategory.PROTEIN);
        CATEGORIES.put("salmon", FoodCategory.PROTEIN);
        CATEGORIES.put("egg", FoodCategory.PROTEIN);
        CATEGORIES.put("eggs", FoodCategory.PROTEIN);
        CATEGORIES.put("tofu", FoodCategory.PROTEIN);
        CATEGORIES.put("shrimp", FoodCategory.PROTEIN);

        // CARBOHIDRATOS
        CATEGORIES.put("rice", FoodCategory.CARBOHYDRATE);
        CATEGORIES.put("pasta", FoodCategory.CARBOHYDRATE);
        CATEGORIES.put("bread", FoodCategory.CARBOHYDRATE);
        CATEGORIES.put("flour", FoodCategory.CARBOHYDRATE);
        CATEGORIES.put("oats", FoodCategory.CARBOHYDRATE);
        CATEGORIES.put("potato", FoodCategory.CARBOHYDRATE);
        CATEGORIES.put("potatoes", FoodCategory.CARBOHYDRATE);
        CATEGORIES.put("noodles", FoodCategory.CARBOHYDRATE);

        // LÁCTEOS
        CATEGORIES.put("milk", FoodCategory.DAIRY);
        CATEGORIES.put("cheese", FoodCategory.DAIRY);
        CATEGORIES.put("butter", FoodCategory.DAIRY);
        CATEGORIES.put("cream", FoodCategory.DAIRY);
        CATEGORIES.put("yogurt", FoodCategory.DAIRY);
        CATEGORIES.put("mozzarella", FoodCategory.DAIRY);
        CATEGORIES.put("parmesan", FoodCategory.DAIRY);

        // VERDURAS
        CATEGORIES.put("tomato", FoodCategory.VEGETABLE);
        CATEGORIES.put("onion", FoodCategory.VEGETABLE);
        CATEGORIES.put("garlic", FoodCategory.VEGETABLE);
        CATEGORIES.put("pepper", FoodCategory.VEGETABLE);
        CATEGORIES.put("spinach", FoodCategory.VEGETABLE);
        CATEGORIES.put("carrot", FoodCategory.VEGETABLE);
        CATEGORIES.put("broccoli", FoodCategory.VEGETABLE);
        CATEGORIES.put("lettuce", FoodCategory.VEGETABLE);
        CATEGORIES.put("zucchini", FoodCategory.VEGETABLE);
        CATEGORIES.put("mushroom", FoodCategory.VEGETABLE);
        CATEGORIES.put("mushrooms", FoodCategory.VEGETABLE);

        // FRUTAS
        CATEGORIES.put("lemon", FoodCategory.FRUIT);
        CATEGORIES.put("lime", FoodCategory.FRUIT);
        CATEGORIES.put("apple", FoodCategory.FRUIT);
        CATEGORIES.put("banana", FoodCategory.FRUIT);
        CATEGORIES.put("orange", FoodCategory.FRUIT);

        // GRASAS
        CATEGORIES.put("olive oil", FoodCategory.FAT);
        CATEGORIES.put("oil", FoodCategory.FAT);
        CATEGORIES.put("coconut oil", FoodCategory.FAT);

        // ESPECIAS
        CATEGORIES.put("salt", FoodCategory.SPICE);
        CATEGORIES.put("pepper", FoodCategory.SPICE);
        CATEGORIES.put("cumin", FoodCategory.SPICE);
        CATEGORIES.put("paprika", FoodCategory.SPICE);
        CATEGORIES.put("oregano", FoodCategory.SPICE);
        CATEGORIES.put("basil", FoodCategory.SPICE);
        CATEGORIES.put("thyme", FoodCategory.SPICE);

        // SUSTITUCIONES
        // formato: si no tienes X, puedes usar Y con compatibilidad Z
        SUBSTITUTIONS.add(new SubstitutionRule("cream", "milk", 0.7));
        SUBSTITUTIONS.add(new SubstitutionRule("cream", "yogurt", 0.6));
        SUBSTITUTIONS.add(new SubstitutionRule("butter", "olive oil", 0.7));
        SUBSTITUTIONS.add(new SubstitutionRule("chicken", "turkey", 0.9));
        SUBSTITUTIONS.add(new SubstitutionRule("chicken", "tofu", 0.6));
        SUBSTITUTIONS.add(new SubstitutionRule("beef", "pork", 0.8));
        SUBSTITUTIONS.add(new SubstitutionRule("pasta", "noodles", 0.9));
        SUBSTITUTIONS.add(new SubstitutionRule("pasta", "rice", 0.6));
        SUBSTITUTIONS.add(new SubstitutionRule("milk", "yogurt", 0.7));
        SUBSTITUTIONS.add(new SubstitutionRule("parmesan", "cheese", 0.8));
        SUBSTITUTIONS.add(new SubstitutionRule("mozzarella", "cheese", 0.8));
        SUBSTITUTIONS.add(new SubstitutionRule("lemon", "lime", 0.9));
    }

    // Devuelve la categoría de un ingrediente
    public static FoodCategory getCategory(String ingredient) {
        return CATEGORIES.getOrDefault(
                ingredient.toLowerCase().trim(), FoodCategory.OTHER);
    }

    // Dados los ingredientes del usuario, devuelve sustitutos posibles
    // para ingredientes que faltan en una receta
    public static List<String> findSubstitutes(
            String missingIngredient, List<String> userIngredients) {

        List<String> result = new ArrayList<>();
        for (SubstitutionRule rule : SUBSTITUTIONS) {
            if (rule.getOriginal().equalsIgnoreCase(missingIngredient)) {
                if (userIngredients.contains(rule.getSubstitute())) {
                    result.add(rule.getSubstitute());
                }
            }
        }
        return result;
    }

    // Devuelve todas las sustituciones posibles de un ingrediente
    public static List<SubstitutionRule> getSubstitutionRules(String ingredient) {
        List<SubstitutionRule> result = new ArrayList<>();
        for (SubstitutionRule rule : SUBSTITUTIONS) {
            if (rule.getOriginal().equalsIgnoreCase(ingredient)) {
                result.add(rule);
            }
        }
        return result;
    }
}