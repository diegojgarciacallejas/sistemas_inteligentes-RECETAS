package behaviours.textminingbehaviours;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TextMiningBehaviour
 *
 * Behaviour cíclico que:
 *  1. Escucha mensajes con conversationId "RECIPE_SEARCH_RESULT" de RecipeSearchAgent
 *  2. Para cada receta, llama a Spoonacular /recipes/{id}/information
<<<<<<< Updated upstream
 *  3. Extrae y normaliza: ingredientes, tiempo, raciones, etiquetas dietéticas
 *  4. Construye un mensaje de texto compatible con GraphBehaviour
 *  5. Lo envía a OntologyAgent con conversationId "TEXT_MINING_RESULT"
=======
 *  3. Extrae y normaliza: ingredientes (con cantidades), tiempo, raciones,
 *     etiquetas dietéticas, cocinas, tipos de plato y puntuación de salud
 *  4. Aplica minería de texto real sobre el texto libre de cada receta:
 *     - Tokenización y eliminación de stopwords
 *     - Cálculo de TF-IDF (Term Frequency - Inverse Document Frequency)
 *     - Similitud coseno entre los ingredientes del usuario y cada receta
 *  5. Construye un mensaje de texto compatible con OntologyAgent y GraphBehaviour
 *  6. Lo envía a OntologyAgent con conversationId "TEXT_MINING_RESULT"
>>>>>>> Stashed changes
 *
 * Diseño para testabilidad:
 *   La interfaz funcional RecipeFetcher separa la lógica HTTP del parsing.
 *   En producción se usa el fetcher real (Spoonacular).
 *   En tests se inyecta una lambda que devuelve JSON hardcodeado, sin red.
 */
public class TextMiningBehaviour extends CyclicBehaviour {

    // ── Constantes ──────────────────────────────────────────────────────────
    private static final String API_KEY        = "74e8728ac10847199e9b7db0f0d97a4e";
    private static final String CONV_IN        = "RECIPE_SEARCH_RESULT";
    private static final String CONV_OUT       = "TEXT_MINING_RESULT";
    private static final String ONTOLOGY_AGENT = "OntologyAgent";

    /**
     * Stopwords inglesas que no aportan información semántica relevante.
     * Se eliminan durante la tokenización para mejorar la calidad del TF-IDF.
     */
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "it", "its", "this", "that", "i", "you", "he", "she", "we",
            "they", "as", "up", "out", "into", "over", "if", "so", "than", "then",
            "per", "also", "very", "just", "your", "about", "some", "more", "all",
            "not", "no", "can", "our", "their", "each", "both", "such", "these",
            "those", "get", "got", "one", "two", "three", "four", "five", "six"
    ));

    // ── Interfaz funcional para el fetching HTTP ────────────────────────────
    @FunctionalInterface
    public interface RecipeFetcher {
        String fetch(int id) throws Exception;
    }

    // ── Estado ──────────────────────────────────────────────────────────────
    private final RecipeFetcher fetcher;
    private final Gson gson;

    // ────────────────────────────────────────────────────────────────────────
    // Constructor de producción (uso normal con JADE)
    // ────────────────────────────────────────────────────────────────────────
    public TextMiningBehaviour(Agent agent) {
        super(agent);
        this.gson = new Gson();

        HttpClient httpClient = HttpClient.newHttpClient();
        this.fetcher = id -> {
            String url = "https://api.spoonacular.com/recipes/" + id
                    + "/information?apiKey=" + API_KEY;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }
            System.err.println("TextMiningAgent: Spoonacular devolvió "
                    + response.statusCode() + " para id=" + id);
            return null;
        };
    }

<<<<<<< Updated upstream
    /**
     * Constructor para tests: acepta un RecipeFetcher externo.
     * No necesita JADE corriendo ni acceso a red.
     *
     * Ejemplo de uso en un test:
     *   String fakeJson = "{ \"extendedIngredients\": [...] }";
     *   TextMiningBehaviour b = new TextMiningBehaviour(null, id -> fakeJson);
     */
=======
    /** Constructor para tests: acepta un RecipeFetcher externo. */
>>>>>>> Stashed changes
    TextMiningBehaviour(Agent agent, RecipeFetcher fetcher) {
        super(agent);
        this.fetcher = fetcher;
        this.gson    = new Gson();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Ciclo principal del behaviour
    // ────────────────────────────────────────────────────────────────────────
    @Override
    public void action() {
        MessageTemplate template = MessageTemplate.MatchConversationId(CONV_IN);
        ACLMessage msg = myAgent.receive(template);

        if (msg != null) {
            System.out.println("TextMiningAgent <- recibe de RecipeSearchAgent:");
            System.out.println(msg.getContent());

            String output = processInput(msg.getContent());

            ACLMessage forward = new ACLMessage(ACLMessage.INFORM);
            forward.addReceiver(new AID(ONTOLOGY_AGENT, AID.ISLOCALNAME));
            forward.setConversationId(CONV_OUT);
            forward.setContent(output);
            myAgent.send(forward);

            System.out.println("TextMiningAgent -> envia a OntologyAgent:");
            System.out.println(output);

        } else {
            block();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Metodos de logica (package-private -> testeables directamente)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Orquesta el procesamiento completo a partir del JSON de entrada.
     */
    String processInput(String jsonInput) {
        try {
            JsonObject root = gson.fromJson(jsonInput, JsonObject.class);

            String userIngredients = root.has("userIngredients")
                    ? root.get("userIngredients").getAsString().trim()
                    : "";

            JsonArray recipes = root.has("recipes")
                    ? root.getAsJsonArray("recipes")
                    : new JsonArray();

            Map<String, RecipeData> recipeDataMap = new LinkedHashMap<>();

            for (JsonElement el : recipes) {
                JsonObject recipe = el.getAsJsonObject();
                int    id   = recipe.get("id").getAsInt();
                String name = recipe.get("name").getAsString();

                RecipeData data = fetchAndExtract(id, name);
                recipeDataMap.put(sanitizeName(name), data);
            }

            // ── Minería de texto: TF-IDF + similitud coseno ─────────────────
            Map<String, Double> tfidfScores = computeTfIdfScores(userIngredients, recipeDataMap);

            return buildOutputMessage(userIngredients, recipeDataMap, tfidfScores);

        } catch (Exception e) {
            System.err.println("TextMiningAgent: error procesando entrada: " + e.getMessage());
            return "error=TextMiningAgent no pudo procesar la entrada\n";
        }
    }

    /**
     * Usa el RecipeFetcher inyectado para obtener el JSON y lo parsea.
     * Si el fetcher falla, devuelve un RecipeData vacio (no rompe el flujo).
     */
    RecipeData fetchAndExtract(int id, String name) {
        try {
            String body = fetcher.fetch(id);
            if (body != null) {
                return extractRecipeData(name, body);
            }
        } catch (Exception e) {
            System.err.println("TextMiningAgent: error obteniendo receta id="
                    + id + " -> " + e.getMessage());
        }
<<<<<<< Updated upstream
        return new RecipeData(name, new ArrayList<>(), -1, -1,
                false, false, false, false);
=======
        return new RecipeData(name, new ArrayList<>(), new ArrayList<>(),
                -1, -1, false, false, false, false,
                new ArrayList<>(), new ArrayList<>(), -1, "");
>>>>>>> Stashed changes
    }

    /**
     * Parsea el JSON de Spoonacular. No hace HTTP: testeable con cualquier String JSON.
<<<<<<< Updated upstream
=======
     *
     * Extrae datos estructurados (ingredientes con cantidades, tiempos, etiquetas)
     * y texto libre (summary + instructions) para el posterior análisis TF-IDF.
>>>>>>> Stashed changes
     */
    RecipeData extractRecipeData(String name, String jsonBody) {
        JsonObject data = gson.fromJson(jsonBody, JsonObject.class);

<<<<<<< Updated upstream
        List<String> ingredients = new ArrayList<>();
=======
        List<String> ingredients             = new ArrayList<>();
        List<IngredientInfo> ingredientDetails = new ArrayList<>();

>>>>>>> Stashed changes
        if (data.has("extendedIngredients")) {
            for (JsonElement el : data.getAsJsonArray("extendedIngredients")) {
                JsonObject ing = el.getAsJsonObject();
                if (ing.has("name") && !ing.get("name").isJsonNull()) {
                    String normalized = normalize(ing.get("name").getAsString());
                    if (!normalized.isEmpty()) {
                        ingredients.add(normalized);
                    }
                }
            }
        }

        int readyInMinutes = getIntSafe(data, "readyInMinutes");
        int servings       = getIntSafe(data, "servings");
        boolean vegan      = getBoolSafe(data, "vegan");
        boolean vegetarian = getBoolSafe(data, "vegetarian");
        boolean glutenFree = getBoolSafe(data, "glutenFree");
        boolean dairyFree  = getBoolSafe(data, "dairyFree");

<<<<<<< Updated upstream
        return new RecipeData(name, ingredients, readyInMinutes, servings,
                vegan, vegetarian, glutenFree, dairyFree);
=======
        List<String> cuisines = new ArrayList<>();
        if (data.has("cuisines") && data.get("cuisines").isJsonArray()) {
            for (JsonElement c : data.getAsJsonArray("cuisines")) {
                if (!c.isJsonNull()) {
                    String cuisine = sanitizeName(c.getAsString());
                    if (!cuisine.isEmpty()) cuisines.add(cuisine.toLowerCase());
                }
            }
        }

        List<String> dishTypes = new ArrayList<>();
        if (data.has("dishTypes") && data.get("dishTypes").isJsonArray()) {
            for (JsonElement d : data.getAsJsonArray("dishTypes")) {
                if (!d.isJsonNull()) {
                    String dishType = sanitizeName(d.getAsString());
                    if (!dishType.isEmpty()) dishTypes.add(dishType.toLowerCase());
                }
            }
        }

        int healthScore = getIntSafe(data, "healthScore");

        // ── Texto libre para minería de texto ───────────────────────────────
        // summary: descripción general de la receta (puede contener HTML)
        // instructions: pasos de preparación en lenguaje natural
        String summary      = stripHtml(getStringSafe(data, "summary"));
        String instructions = stripHtml(getStringSafe(data, "instructions"));
        String rawText      = summary + " " + instructions;

        return new RecipeData(name, ingredients, ingredientDetails,
                readyInMinutes, servings, vegan, vegetarian, glutenFree, dairyFree,
                cuisines, dishTypes, healthScore, rawText);
>>>>>>> Stashed changes
    }

    /**
     * Construye el mensaje de texto para OntologyAgent.
     *
<<<<<<< Updated upstream
     * Lineas mandatorias (GraphBehaviour las lee directamente):
=======
     * Lineas retro-compatibles (GraphBehaviour y OntologyAgent las leen):
>>>>>>> Stashed changes
     *   userIngredients=egg,rice,tomato
     *   recipes=RecipeName:ing1,ing2;OtraReceta:ing3
     *
     * Lineas opcionales (para OntologyAgent y futuros agentes):
     *   recipeTimes=RecipeName:20;OtraReceta:35
     *   recipeServings=RecipeName:4;OtraReceta:2
     *   recipeTags=RecipeName:vegetarian;OtraReceta:vegan,glutenFree
<<<<<<< Updated upstream
     */
    String buildOutputMessage(String userIngredients, Map<String, RecipeData> recipeDataMap) {
        StringBuilder recipesLine  = new StringBuilder("recipes=");
        StringBuilder timesLine    = new StringBuilder("recipeTimes=");
        StringBuilder servingsLine = new StringBuilder("recipeServings=");
        StringBuilder tagsLine     = new StringBuilder("recipeTags=");
=======
     *
     * Lineas con cantidades e información adicional:
     *   recipeIngredients=RecipeName:ing1|2|cups,ing2|100|g
     *   recipeCuisines=RecipeName:italian;OtraReceta:mediterranean
     *   recipeDishTypes=RecipeName:lunch;OtraReceta:main course
     *   recipeHealthScores=RecipeName:85;OtraReceta:72
     *
     * Línea de minería de texto (TF-IDF + similitud coseno):
     *   recipeTfIdfScores=RecipeName:0.7523;OtraReceta:0.3241
     *   Valor entre 0 y 1: similitud entre los ingredientes del usuario
     *   y el texto libre (summary + instructions) de cada receta.
     */
    String buildOutputMessage(String userIngredients,
                              Map<String, RecipeData> recipeDataMap,
                              Map<String, Double> tfidfScores) {
        StringBuilder recipesLine      = new StringBuilder("recipes=");
        StringBuilder ingredientsLine  = new StringBuilder("recipeIngredients=");
        StringBuilder timesLine        = new StringBuilder("recipeTimes=");
        StringBuilder servingsLine     = new StringBuilder("recipeServings=");
        StringBuilder tagsLine         = new StringBuilder("recipeTags=");
        StringBuilder cuisinesLine     = new StringBuilder("recipeCuisines=");
        StringBuilder dishTypesLine    = new StringBuilder("recipeDishTypes=");
        StringBuilder healthScoresLine = new StringBuilder("recipeHealthScores=");
        StringBuilder tfidfLine        = new StringBuilder("recipeTfIdfScores=");
>>>>>>> Stashed changes

        boolean first = true;
        for (Map.Entry<String, RecipeData> entry : recipeDataMap.entrySet()) {
            if (!first) {
                recipesLine.append(";");
                timesLine.append(";");
                servingsLine.append(";");
                tagsLine.append(";");
<<<<<<< Updated upstream
=======
                cuisinesLine.append(";");
                dishTypesLine.append(";");
                healthScoresLine.append(";");
                tfidfLine.append(";");
>>>>>>> Stashed changes
            }
            first = false;

            String     key = entry.getKey();
            RecipeData d   = entry.getValue();

<<<<<<< Updated upstream
            recipesLine.append(key).append(":")
                    .append(String.join(",", d.ingredients));
=======
            recipesLine.append(key).append(":").append(String.join(",", d.ingredients));

            ingredientsLine.append(key).append(":");
            for (int i = 0; i < d.ingredientDetails.size(); i++) {
                if (i > 0) ingredientsLine.append(",");
                IngredientInfo info = d.ingredientDetails.get(i);
                ingredientsLine.append(info.name)
                        .append("|").append(formatAmount(info.amount))
                        .append("|").append(info.unit);
            }

>>>>>>> Stashed changes
            timesLine.append(key).append(":").append(d.readyInMinutes);
            servingsLine.append(key).append(":").append(d.servings);

            List<String> tags = new ArrayList<>();
            if (d.vegan)      tags.add("vegan");
            if (d.vegetarian) tags.add("vegetarian");
            if (d.glutenFree) tags.add("glutenFree");
            if (d.dairyFree)  tags.add("dairyFree");
            tagsLine.append(key).append(":").append(String.join(",", tags));
<<<<<<< Updated upstream
        }

        return "userIngredients=" + userIngredients + "\n"
                + recipesLine   + "\n"
                + timesLine     + "\n"
                + servingsLine  + "\n"
                + tagsLine      + "\n";
=======

            cuisinesLine.append(key).append(":").append(String.join(",", d.cuisines));
            dishTypesLine.append(key).append(":").append(String.join(",", d.dishTypes));
            healthScoresLine.append(key).append(":").append(d.healthScore);

            double score = tfidfScores.getOrDefault(key, 0.0);
            tfidfLine.append(key).append(":").append(String.format(Locale.US, "%.4f", score));
        }

        return "userIngredients=" + userIngredients + "\n"
                + recipesLine      + "\n"
                + ingredientsLine  + "\n"
                + timesLine        + "\n"
                + servingsLine     + "\n"
                + tagsLine         + "\n"
                + cuisinesLine     + "\n"
                + dishTypesLine    + "\n"
                + healthScoresLine + "\n"
                + tfidfLine        + "\n";
    }

    // ════════════════════════════════════════════════════════════════════════
    // Minería de texto: TF-IDF + similitud coseno
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Calcula la puntuación TF-IDF + coseno para cada receta respecto al usuario.
     *
     * El corpus se construye así:
     *   D0 = "documento del usuario" → sus ingredientes como texto
     *   D1..Dn = texto libre de cada receta (summary + instructions)
     *
     * Con ese corpus se calcula el IDF global, luego el vector TF-IDF de cada
     * documento, y finalmente la similitud coseno entre D0 y cada Di.
     *
     * Resultado: mapa de nombreReceta → puntuación [0, 1]
     */
    Map<String, Double> computeTfIdfScores(String userIngredients,
                                           Map<String, RecipeData> recipeDataMap) {
        // Documento del usuario: sus ingredientes separados por espacio
        String userText = userIngredients.replace(",", " ");
        List<String> userTokens = tokenize(userText);

        // Tokenizar el texto libre de cada receta
        Map<String, List<String>> recipeTokens = new LinkedHashMap<>();
        for (Map.Entry<String, RecipeData> entry : recipeDataMap.entrySet()) {
            recipeTokens.put(entry.getKey(), tokenize(entry.getValue().rawText));
        }

        // Corpus completo: usuario + todas las recetas
        List<List<String>> corpus = new ArrayList<>();
        corpus.add(userTokens);
        corpus.addAll(recipeTokens.values());

        // IDF global del corpus
        Map<String, Double> idf = computeIdf(corpus);

        // Vector TF-IDF del usuario
        Map<String, Double> userTfIdf = computeTfIdfVector(computeTf(userTokens), idf);

        // Similitud coseno de cada receta con el usuario
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : recipeTokens.entrySet()) {
            Map<String, Double> recipeTfIdf = computeTfIdfVector(computeTf(entry.getValue()), idf);
            scores.put(entry.getKey(), cosineSimilarity(userTfIdf, recipeTfIdf));
        }

        return scores;
    }

    /**
     * Elimina etiquetas HTML y entidades HTML de un texto.
     * Spoonacular devuelve el campo summary con HTML (<b>, <i>, &amp;, etc.).
     * Ejemplo: "A <b>vegan</b> recipe" → "A vegan recipe"
     */
    String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        return html.replaceAll("<[^>]+>", " ")
                   .replaceAll("&[a-zA-Z]+;", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * Tokeniza un texto en términos normalizados.
     * Aplica: lowercase, eliminación de puntuación, filtrado de stopwords
     * y descarte de tokens de menos de 3 caracteres.
     *
     * Ejemplo: "Add 2 cloves of fresh Garlic!" → ["cloves", "fresh", "garlic"]
     */
    List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) return tokens;

        String clean = text.toLowerCase()
                           .replaceAll("[^a-z0-9\\s]", " ")
                           .replaceAll("\\s+", " ")
                           .trim();

        for (String token : clean.split(" ")) {
            if (token.length() >= 3 && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Calcula el TF (Term Frequency) de un documento.
     * TF(t, d) = nº de veces que aparece t en d / nº total de términos en d
     */
    Map<String, Double> computeTf(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (tokens.isEmpty()) return tf;

        for (String token : tokens) {
            tf.merge(token, 1.0, Double::sum);
        }
        double total = tokens.size();
        tf.replaceAll((k, v) -> v / total);
        return tf;
    }

    /**
     * Calcula el IDF (Inverse Document Frequency) para todos los términos del corpus.
     *
     * Fórmula con suavizado para evitar divisiones por cero y dar peso 1
     * a términos que aparecen en todos los documentos:
     *   IDF(t) = log( (1 + N) / (1 + df(t)) ) + 1
     *
     * Donde N = nº de documentos, df(t) = nº de documentos que contienen t.
     */
    Map<String, Double> computeIdf(List<List<String>> corpus) {
        Map<String, Integer> df = new HashMap<>();
        int N = corpus.size();

        for (List<String> doc : corpus) {
            Set<String> uniqueTerms = new HashSet<>(doc);
            for (String term : uniqueTerms) {
                df.merge(term, 1, Integer::sum);
            }
        }

        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : df.entrySet()) {
            double val = Math.log((1.0 + N) / (1.0 + entry.getValue())) + 1.0;
            idf.put(entry.getKey(), val);
        }
        return idf;
    }

    /**
     * Calcula el vector TF-IDF de un documento.
     * TF-IDF(t, d) = TF(t, d) × IDF(t)
     *
     * Términos con TF-IDF alto son frecuentes en este documento
     * pero raros en el resto del corpus → son los más discriminativos.
     */
    Map<String, Double> computeTfIdfVector(Map<String, Double> tf, Map<String, Double> idf) {
        Map<String, Double> tfidf = new HashMap<>();
        for (Map.Entry<String, Double> entry : tf.entrySet()) {
            String term   = entry.getKey();
            double idfVal = idf.getOrDefault(term, 0.0);
            tfidf.put(term, entry.getValue() * idfVal);
        }
        return tfidf;
    }

    /**
     * Calcula la similitud coseno entre dos vectores TF-IDF.
     *
     * cos(A, B) = (A · B) / (||A|| × ||B||)
     *
     * Resultado entre 0 (sin similitud) y 1 (vectores idénticos).
     * Un valor alto indica que el texto de la receta comparte términos
     * relevantes con los ingredientes del usuario.
     */
    double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        double dotProduct = 0.0;
        double norm1      = 0.0;
        double norm2      = 0.0;

        for (Map.Entry<String, Double> entry : v1.entrySet()) {
            dotProduct += entry.getValue() * v2.getOrDefault(entry.getKey(), 0.0);
            norm1      += entry.getValue() * entry.getValue();
        }
        for (double val : v2.values()) {
            norm2 += val * val;
        }

        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
>>>>>>> Stashed changes
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    String normalize(String text) {
        return text.trim()
                .toLowerCase()
                .replaceAll("[;:\n]", "")
                .replaceAll("[^a-z0-9 ()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

<<<<<<< Updated upstream
    /**
     * Sanitiza un nombre de receta eliminando los separadores del protocolo.
     * ; y : se usan como delimitadores en el mensaje entre agentes.
     * Ejemplo: "Pasta: Aglio; vegan" -> "Pasta Aglio vegan"
     */
=======
>>>>>>> Stashed changes
    String sanitizeName(String name) {
        return name.replaceAll("[;:\n]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

<<<<<<< Updated upstream
    private int     getIntSafe (JsonObject o, String k) {
=======
    private String sanitizeUnit(String unit) {
        if (unit == null) return "";
        return unit.replaceAll("[|;:\n]", "").trim();
    }

    private String formatAmount(double amount) {
        if (amount <= 0) return "0";
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return String.valueOf((int) amount);
        }
        String formatted = String.format(Locale.US, "%.2f", amount);
        formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted;
    }

    private int     getIntSafe   (JsonObject o, String k) {
>>>>>>> Stashed changes
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : -1;
    }
    private boolean getBoolSafe(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Clase de datos
    // ════════════════════════════════════════════════════════════════════════

<<<<<<< Updated upstream
    /** Datos extraidos de una receta. Campos -1 = Spoonacular no los devolvio. */
    static class RecipeData {
        final String       name;
        final List<String> ingredients;
        final int          readyInMinutes;
        final int          servings;
        final boolean      vegan;
        final boolean      vegetarian;
        final boolean      glutenFree;
        final boolean      dairyFree;
=======
    /** Ingrediente con nombre, cantidad y unidad de medida. */
    static class IngredientInfo {
        final String name;
        final double amount;
        final String unit;

        IngredientInfo(String name, double amount, String unit) {
            this.name   = name;
            this.amount = amount;
            this.unit   = unit;
        }
    }

    /**
     * Datos extraídos de una receta.
     *
     * ingredients:       nombres normalizados (para GraphBehaviour/OntologyAgent)
     * ingredientDetails: mismos ingredientes con cantidad y unidad
     * rawText:           texto libre (summary + instructions) para TF-IDF
     */
    static class RecipeData {
        final String               name;
        final List<String>         ingredients;
        final List<IngredientInfo> ingredientDetails;
        final int                  readyInMinutes;
        final int                  servings;
        final boolean              vegan;
        final boolean              vegetarian;
        final boolean              glutenFree;
        final boolean              dairyFree;
        final List<String>         cuisines;
        final List<String>         dishTypes;
        final int                  healthScore;
        final String               rawText;
>>>>>>> Stashed changes

        RecipeData(String name, List<String> ingredients,
                   int readyInMinutes, int servings,
                   boolean vegan, boolean vegetarian,
<<<<<<< Updated upstream
                   boolean glutenFree, boolean dairyFree) {
            this.name           = name;
            this.ingredients    = ingredients;
            this.readyInMinutes = readyInMinutes;
            this.servings       = servings;
            this.vegan          = vegan;
            this.vegetarian     = vegetarian;
            this.glutenFree     = glutenFree;
            this.dairyFree      = dairyFree;
=======
                   boolean glutenFree, boolean dairyFree,
                   List<String> cuisines,
                   List<String> dishTypes,
                   int healthScore,
                   String rawText) {
            this.name              = name;
            this.ingredients       = ingredients;
            this.ingredientDetails = ingredientDetails;
            this.readyInMinutes    = readyInMinutes;
            this.servings          = servings;
            this.vegan             = vegan;
            this.vegetarian        = vegetarian;
            this.glutenFree        = glutenFree;
            this.dairyFree         = dairyFree;
            this.cuisines          = cuisines;
            this.dishTypes         = dishTypes;
            this.healthScore       = healthScore;
            this.rawText           = rawText;
>>>>>>> Stashed changes
        }
    }
}