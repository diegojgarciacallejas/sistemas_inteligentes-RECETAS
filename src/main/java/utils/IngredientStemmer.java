package utils;

/**
 * IngredientStemmer — stemmer de ingredientes compartido.
 *
 * Centraliza la lógica de normalización plural→singular para que
 * OntologyProcessor y GraphBehaviour usen exactamente las mismas reglas.
 *
 * Mejoras respecto al stemmer anterior:
 *   - Ingredientes compuestos: se stemmiza solo el último token (la cabeza nominal)
 *     "chicken breasts" → "chicken breast"
 *     "green peppers"   → "green pepper"
 *     "egg yolks"       → "egg yolk"
 *   - Sufijos -ches, -shes, -xes cubiertos:
 *     "peaches" → "peach"    (antes quedaba "peache")
 *     "dishes"  → "dish"     (antes quedaba "dishe")
 *   - Método matches() para comparación parcial:
 *     "chicken" ↔ "chicken breast"  → true   (genérico ↔ específico)
 *     "pepper"  ↔ "bell pepper"     → true
 */
public final class IngredientStemmer {

    private IngredientStemmer() {}

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Devuelve la forma canónica (singular) de un ingrediente.
     * Para ingredientes compuestos, solo stemmiza la última palabra.
     */
    public static String stem(String word) {
        if (word == null || word.isEmpty()) return "";
        word = word.trim().toLowerCase();

        if (!word.contains(" ")) {
            return stemSingle(word);
        }

        // Compuesto: "chicken breasts" → "chicken breast"
        // Solo la última palabra (cabeza nominal) lleva el número gramatical.
        String[] parts = word.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(i == parts.length - 1 ? stemSingle(parts[i]) : parts[i]);
        }
        return sb.toString();
    }

    /**
     * Comprueba si dos ingredientes son equivalentes teniendo en cuenta:
     *   1. Igualdad exacta tras stemming.
     *   2. Relación genérico↔específico:
     *      "chicken" coincide con "chicken breast", "chicken thighs", etc.
     *      "pepper"  coincide con "bell pepper", "red pepper", etc.
     *
     * @param ingA primer ingrediente (p.ej. del usuario)
     * @param ingB segundo ingrediente (p.ej. de la receta)
     */
    public static boolean matches(String ingA, String ingB) {
        if (ingA == null || ingB == null) return false;
        String a = stem(ingA.trim().toLowerCase());
        String b = stem(ingB.trim().toLowerCase());
        if (a.equals(b)) return true;
        // "chicken" ↔ "chicken breast"
        if (b.startsWith(a + " ") || a.startsWith(b + " ")) return true;
        // "pepper" ↔ "bell pepper" (contiene como última palabra)
        if (b.endsWith(" " + a) || a.endsWith(" " + b)) return true;
        return false;
    }

    // ── Lógica interna ────────────────────────────────────────────────────────

    private static String stemSingle(String word) {
        if (word == null || word.isEmpty()) return word;
        int len = word.length();

        // -ies → -y : berries→berry, cherries→cherry
        if (len > 3 && word.endsWith("ies"))
            return word.substring(0, len - 3) + "y";

        // -oes → -o : tomatoes→tomato, potatoes→potato
        if (len > 4 && word.endsWith("oes"))
            return word.substring(0, len - 2);

        // -ches → -ch : peaches→peach, lunches→lunch
        if (len > 4 && word.endsWith("ches"))
            return word.substring(0, len - 2);

        // -shes → -sh : dishes→dish
        if (len > 4 && word.endsWith("shes"))
            return word.substring(0, len - 2);

        // -xes → -x : mixes→mix
        if (len > 3 && word.endsWith("xes"))
            return word.substring(0, len - 2);

        // -s genérico con protecciones comunes en ingredientes:
        //   -ss (bass, grass), -us (asparagus, citrus),
        //   -is (anís),        -as (peas)
        if (len > 3 && word.endsWith("s")
                && !word.endsWith("ss")
                && !word.endsWith("us")
                && !word.endsWith("is")
                && !word.endsWith("as"))
            return word.substring(0, len - 1);

        return word;
    }
}
