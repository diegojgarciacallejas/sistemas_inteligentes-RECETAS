package utils;

import java.util.*;

/**
 * RestrictionChecker — filtra recetas incompatibles con las restricciones del usuario.
 *
 * Restricciones soportadas (en español e inglés):
 *   vegetariano / vegetarian  — sin carne ni pescado
 *   vegano / vegan            — sin ningún producto animal
 *   sin gluten / gluten free  — sin cereales con gluten
 *   sin lactosa / dairy free  — sin lácteos
 *
 * Uso:
 *   boolean ok = RestrictionChecker.isCompatible("vegetariano, sin gluten", recipeIngredients);
 */
public final class RestrictionChecker {

    private RestrictionChecker() {}

    private static final Map<String, Set<String>> RULES = new HashMap<>();

    static {
        // ── Carne y pescado ───────────────────────────────────────────────────
        Set<String> meat = new HashSet<>(Arrays.asList(
                "beef", "chicken", "pork", "turkey", "lamb", "veal", "duck",
                "goose", "rabbit", "venison", "bacon", "ham", "sausage",
                "salami", "pepperoni", "chorizo", "lard", "anchovies",
                "tuna", "salmon", "cod", "halibut", "tilapia", "trout",
                "shrimp", "prawn", "crab", "lobster", "clam", "mussel",
                "fish", "seafood", "mince", "ground beef", "minced beef",
                "steak", "ribs", "chicken breast", "chicken thigh",
                "chicken wing", "pork belly", "pork chop", "lamb chop"
        ));
        RULES.put("vegetariano", meat);
        RULES.put("vegetarian",  meat);

        // ── Todo producto animal (vegano = vegetariano + lácteos + huevo + miel) ─
        Set<String> animal = new HashSet<>(meat);
        animal.addAll(Arrays.asList(
                "egg", "eggs", "egg yolk", "egg white",
                "milk", "cheese", "butter", "cream", "yogurt", "ghee",
                "honey", "gelatin", "whey", "casein",
                "mozzarella", "parmesan", "cheddar", "ricotta",
                "brie", "gouda", "feta", "cream cheese"
        ));
        RULES.put("vegano", animal);
        RULES.put("vegan",  animal);

        // ── Gluten ────────────────────────────────────────────────────────────
        Set<String> gluten = new HashSet<>(Arrays.asList(
                "flour", "wheat", "wheat flour", "bread", "breadcrumbs",
                "pasta", "noodles", "couscous", "semolina", "barley", "rye",
                "soy sauce", "teriyaki sauce", "beer"
        ));
        RULES.put("sin gluten",  gluten);
        RULES.put("gluten free", gluten);
        RULES.put("gluten-free", gluten);
        RULES.put("sin gluten",  gluten);

        // ── Lácteos ───────────────────────────────────────────────────────────
        Set<String> dairy = new HashSet<>(Arrays.asList(
                "milk", "cheese", "butter", "cream", "yogurt", "ghee",
                "mozzarella", "parmesan", "cheddar", "ricotta",
                "brie", "gouda", "feta", "cream cheese", "whey", "casein",
                "sour cream", "heavy cream", "double cream"
        ));
        RULES.put("sin lactosa",   dairy);
        RULES.put("sin lacteos",   dairy);
        RULES.put("sin lácteos",   dairy);
        RULES.put("dairy free",    dairy);
        RULES.put("dairy-free",    dairy);
        RULES.put("lactose free",  dairy);
        RULES.put("lactose-free",  dairy);
    }

    /**
     * @param restrictions  cadena separada por comas (ej: "vegetariano, sin gluten")
     * @param recipeIngredients lista de ingredientes normalizados de la receta
     * @return true si la receta es compatible con TODAS las restricciones
     */
    public static boolean isCompatible(String restrictions, List<String> recipeIngredients) {
        if (restrictions == null || restrictions.isBlank()) return true;
        if (recipeIngredients == null || recipeIngredients.isEmpty()) return true;

        for (String rawRestriction : restrictions.split("[,;]+")) {
            String r = rawRestriction.trim().toLowerCase();
            Set<String> forbidden = RULES.get(r);
            if (forbidden == null) continue;

            for (String ing : recipeIngredients) {
                String normalized = ing.trim().toLowerCase();
                if (forbidden.contains(normalized)) {
                    System.out.println("RestrictionChecker: receta rechazada — '"
                            + normalized + "' viola '" + r + "'");
                    return false;
                }
                // Comprobación parcial: "chicken breast" contiene "chicken"
                for (String forbidden_ing : forbidden) {
                    if (normalized.contains(forbidden_ing) || forbidden_ing.contains(normalized)) {
                        if (normalized.split(" ")[0].equals(forbidden_ing.split(" ")[0])) {
                            System.out.println("RestrictionChecker: receta rechazada — '"
                                    + normalized + "' viola '" + r + "' (parcial)");
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
