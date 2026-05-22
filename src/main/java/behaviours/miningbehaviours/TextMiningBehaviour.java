package behaviours.miningbehaviours;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TextMiningBehaviour
 *
 * Behaviour cíclico que:
 *  1. Escucha mensajes con conversationId "RECIPE_SEARCH_RESULT" de RecipeSearchAgent
 *  2. Para cada receta, llama a Spoonacular /recipes/{id}/information
 *  3. Extrae y normaliza: ingredientes (con cantidades), tiempo, raciones,
 *     etiquetas dietéticas, cocinas, tipos de plato y puntuación de salud
 *  4. Aplica minería de texto real sobre el texto libre de cada receta:
 *     - Tokenización y eliminación de stopwords
 *     - Cálculo de TF-IDF (Term Frequency - Inverse Document Frequency)
 *     - Similitud coseno entre los ingredientes del usuario y cada receta
 *  5. Construye un mensaje de texto compatible con OntologyAgent y GraphBehaviour
 *  6. Lo envía a OntologyAgent con conversationId "TEXT_MINING_RESULT"
 *
 * Diseño para testabilidad:
 *   La interfaz funcional RecipeFetcher separa la lógica HTTP del parsing.
 *   En producción se usa el fetcher real (Spoonacular).
 *   En tests se inyecta una lambda que devuelve JSON hardcodeado, sin red.
 */
public class TextMiningBehaviour extends CyclicBehaviour {

    // ── Constantes ──────────────────────────────────────────────────────────
    private static final String MEALDB_BASE    = "https://www.themealdb.com/api/json/v1/1/";
    private static final String CONV_IN        = "RECIPE_SEARCH_RESULT";
    private static final String CONV_OUT       = "TEXT_MINING_RESULT";
    private static final String ONTOLOGY_AGENT = "OntologyAgent";

    /**
     * Ruta del fichero IDF pre-calculado sobre RecipeNLG.
     * Se carga como recurso del classpath (src/main/resources/idf_corpus.json).
     * Generado offline con utils.IdfCorpusBuilder (full_dataset.csv de RecipeNLG).
     */
    private static final String IDF_RESOURCE = "/idf_corpus.json";

    /**
     * Stopwords inglesas que no aportan información semántica relevante.
     * Se eliminan durante la tokenización para mejorar la calidad del TF-IDF.
     */
    /**
     * Verbos de instrucción de cocina. Si el nombre de un ingrediente empieza
     * por uno de estos verbos es porque Spoonacular ha filtrado mal y ha metido
     * texto de los pasos de la receta en extendedIngredients.
     */
    private static final Set<String> INSTRUCTION_VERBS = new HashSet<>(Arrays.asList(
            "add", "heat", "cook", "stir", "mix", "put", "place", "pour",
            "boil", "fry", "bake", "roast", "grill", "simmer", "saute",
            "chop", "slice", "dice", "combine", "whisk", "blend", "drain",
            "peel", "cut", "remove", "serve", "bring", "season", "toss"
    ));

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

    /**
     * IDF pre-calculado sobre el corpus RecipeNLG (2M recetas).
     * Si el fichero no está disponible se calcula sobre el corpus local
     * (las recetas devueltas por Spoonacular en la consulta actual).
     */
    private final Map<String, Double> corpusIdf;

    // ────────────────────────────────────────────────────────────────────────
    // Constructor de producción (uso normal con JADE)
    // ────────────────────────────────────────────────────────────────────────
    public TextMiningBehaviour(Agent agent) {
        super(agent);
        this.gson      = new Gson();
        this.corpusIdf = loadCorpusIdf();

        HttpClient httpClient = HttpClient.newHttpClient();
        this.fetcher = id -> {
            String url = "https://www.themealdb.com/api/json/v1/1/lookup.php?i=" + id;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }
            System.err.println("TextMiningAgent: TheMealDB devolvió "
                    + response.statusCode() + " para id=" + id);
            return null;
        };
    }

    /** Constructor para tests: acepta un RecipeFetcher externo y un IDF externo. */
    TextMiningBehaviour(Agent agent, RecipeFetcher fetcher) {
        super(agent);
        this.fetcher   = fetcher;
        this.gson      = new Gson();
        this.corpusIdf = loadCorpusIdf();
    }

    /** Constructor para tests que inyecta también el IDF directamente. */
    TextMiningBehaviour(Agent agent, RecipeFetcher fetcher, Map<String, Double> corpusIdf) {
        super(agent);
        this.fetcher   = fetcher;
        this.gson      = new Gson();
        this.corpusIdf = corpusIdf;
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

            // Preferencias del usuario propagadas desde SearchBehaviour
            int    maxTime     = root.has("maxTime")     ? root.get("maxTime").getAsInt()        : -1;
            String restrictions = root.has("restrictions") ? root.get("restrictions").getAsString().trim() : "";

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

            return buildOutputMessage(userIngredients, recipeDataMap, tfidfScores,
                    maxTime, restrictions);

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
        return new RecipeData(name, new ArrayList<>(), new ArrayList<>(),
                -1, -1, false, false, false, false,
                new ArrayList<>(), new ArrayList<>(), -1, "", "");
    }

    /**
     * Parsea el JSON de Spoonacular. No hace HTTP: testeable con cualquier String JSON.
     *
     * Extrae datos estructurados (ingredientes con cantidades, tiempos, etiquetas)
     * y texto libre (summary + instructions) para el posterior análisis TF-IDF.
     */
    /**
     * Parsea el JSON de TheMealDB (lookup.php?i={id}).
     * Formato: {"meals":[{strMeal, strInstructions, strArea, strCategory,
     *                     strIngredient1..20, strMeasure1..20, strTags, ...}]}
     */
    RecipeData extractRecipeData(String name, String jsonBody) {
        JsonObject root = gson.fromJson(jsonBody, JsonObject.class);

        // TheMealDB envuelve el resultado en {"meals":[{...}]}
        JsonObject data;
        if (root.has("meals") && !root.get("meals").isJsonNull()) {
            data = root.getAsJsonArray("meals").get(0).getAsJsonObject();
        } else {
            // Aceptar también JSON plano (tests)
            data = root;
        }

        List<String> ingredients             = new ArrayList<>();
        List<IngredientInfo> ingredientDetails = new ArrayList<>();

        // TheMealDB usa strIngredient1..20 + strMeasure1..20
        for (int i = 1; i <= 20; i++) {
            String ingKey     = "strIngredient" + i;
            String measureKey = "strMeasure"    + i;

            String rawName = getStringSafe(data, ingKey).trim();
            if (rawName.isEmpty()) continue;                          // fin de lista

            if (!isValidIngredientName(rawName)) continue;

            String normalized = normalize(rawName);
            if (normalized.isEmpty()) continue;

            ingredients.add(normalized);

            // Parsear medida: "1 cup", "200 g", "3/4 tsp" → amount + unit
            String measure = getStringSafe(data, measureKey).trim();
            double amount  = parseMeasureAmount(measure);
            String unit    = parseMeasureUnit(measure);
            ingredientDetails.add(new IngredientInfo(normalized, amount, unit));
        }

        // TheMealDB no tiene readyInMinutes, servings ni flags dietéticos
        int     readyInMinutes = -1;
        int     servings       = -1;
        boolean vegan          = false;
        boolean vegetarian     = false;
        boolean glutenFree     = false;
        boolean dairyFree      = false;
        int     healthScore    = -1;

        // Área geográfica → cuisine; Categoría → dishType
        List<String> cuisines  = new ArrayList<>();
        List<String> dishTypes = new ArrayList<>();
        String area     = getStringSafe(data, "strArea").trim();
        String category = getStringSafe(data, "strCategory").trim();
        if (!area.isEmpty()     && !area.equalsIgnoreCase("Unknown"))
            cuisines.add(area.toLowerCase());
        if (!category.isEmpty()) dishTypes.add(category.toLowerCase());

        // Instrucciones de cocción
        String instructions = getStringSafe(data, "strInstructions").trim();
        String rawText      = instructions;   // TheMealDB no tiene summary separado

        return new RecipeData(name, ingredients, ingredientDetails,
                readyInMinutes, servings, vegan, vegetarian, glutenFree, dairyFree,
                cuisines, dishTypes, healthScore, rawText, instructions);
    }

    /** Extrae el número de una cadena de medida como "1 cup", "200 g", "3/4". */
    private double parseMeasureAmount(String measure) {
        if (measure == null || measure.isBlank()) return 0.0;
        String[] parts = measure.trim().split("\\s+");
        try {
            String numPart = parts[0];
            if (numPart.contains("/")) {
                String[] frac = numPart.split("/");
                return Double.parseDouble(frac[0]) / Double.parseDouble(frac[1]);
            }
            return Double.parseDouble(numPart);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Extrae la unidad de una cadena de medida como "1 cup" → "cup". */
    private String parseMeasureUnit(String measure) {
        if (measure == null || measure.isBlank()) return "";
        String[] parts = measure.trim().split("\\s+", 2);
        return parts.length > 1 ? sanitizeUnit(parts[1]) : "";
    }

    /**
     * Construye el mensaje de texto para OntologyAgent (11 lineas).
     *
     * Lineas retro-compatibles:
     *   userIngredients=egg,rice,tomato
     *   recipes=RecipeName:ing1,ing2;OtraReceta:ing3
     *   recipeTimes=RecipeName:20;OtraReceta:35
     *   recipeServings=RecipeName:4;OtraReceta:2
     *   recipeTags=RecipeName:vegetarian;OtraReceta:vegan,glutenFree
     *
     * Lineas con información adicional:
     *   recipeIngredients=RecipeName:ing1|2|cups,ing2|100|g
     *   recipeCuisines=RecipeName:italian;OtraReceta:mediterranean
     *   recipeDishTypes=RecipeName:lunch;OtraReceta:main course
     *   recipeHealthScores=RecipeName:85;OtraReceta:72
     *
     * Minería de texto:
     *   recipeTfIdfScores=RecipeName:0.7523;OtraReceta:0.3241
     *
     * Instrucciones de cocción (texto libre sanitizado):
     *   recipeInstructions=RecipeName:Boil pasta, add garlic;OtraReceta:Fry chicken
     *   Los ';' y '|' de las instrucciones se reemplazan para no romper el protocolo.
     *   Se usa split(":", 2) para leer esta linea ya que el texto puede contener ':'.
     */
    /** Overload sin preferencias — mantiene compatibilidad con tests existentes. */
    String buildOutputMessage(String userIngredients,
                              Map<String, RecipeData> recipeDataMap,
                              Map<String, Double> tfidfScores) {
        return buildOutputMessage(userIngredients, recipeDataMap, tfidfScores, -1, "");
    }

    String buildOutputMessage(String userIngredients,
                              Map<String, RecipeData> recipeDataMap,
                              Map<String, Double> tfidfScores,
                              int maxTime,
                              String restrictions) {
        StringBuilder recipesLine        = new StringBuilder("recipes=");
        StringBuilder ingredientsLine    = new StringBuilder("recipeIngredients=");
        StringBuilder timesLine          = new StringBuilder("recipeTimes=");
        StringBuilder servingsLine       = new StringBuilder("recipeServings=");
        StringBuilder tagsLine           = new StringBuilder("recipeTags=");
        StringBuilder cuisinesLine       = new StringBuilder("recipeCuisines=");
        StringBuilder dishTypesLine      = new StringBuilder("recipeDishTypes=");
        StringBuilder healthScoresLine   = new StringBuilder("recipeHealthScores=");
        StringBuilder tfidfLine          = new StringBuilder("recipeTfIdfScores=");
        StringBuilder instructionsLine   = new StringBuilder("recipeInstructions=");

        boolean first = true;
        for (Map.Entry<String, RecipeData> entry : recipeDataMap.entrySet()) {
            if (!first) {
                recipesLine.append(";");
                ingredientsLine.append(";");
                timesLine.append(";");
                servingsLine.append(";");
                tagsLine.append(";");
                cuisinesLine.append(";");
                dishTypesLine.append(";");
                healthScoresLine.append(";");
                tfidfLine.append(";");
                instructionsLine.append(";");
            }
            first = false;

            String     key = entry.getKey();
            RecipeData d   = entry.getValue();

            recipesLine.append(key).append(":").append(String.join(",", d.ingredients));

            ingredientsLine.append(key).append(":");
            for (int i = 0; i < d.ingredientDetails.size(); i++) {
                if (i > 0) ingredientsLine.append(",");
                IngredientInfo info = d.ingredientDetails.get(i);
                ingredientsLine.append(info.name)
                        .append("|").append(formatAmount(info.amount))
                        .append("|").append(info.unit);
            }

            timesLine.append(key).append(":").append(d.readyInMinutes);
            servingsLine.append(key).append(":").append(d.servings);

            List<String> tags = new ArrayList<>();
            if (d.vegan)      tags.add("vegan");
            if (d.vegetarian) tags.add("vegetarian");
            if (d.glutenFree) tags.add("glutenFree");
            if (d.dairyFree)  tags.add("dairyFree");
            tagsLine.append(key).append(":").append(String.join(",", tags));

            cuisinesLine.append(key).append(":").append(String.join(",", d.cuisines));
            dishTypesLine.append(key).append(":").append(String.join(",", d.dishTypes));
            healthScoresLine.append(key).append(":").append(d.healthScore);

            double score = tfidfScores.getOrDefault(key, 0.0);
            tfidfLine.append(key).append(":").append(String.format(Locale.US, "%.4f", score));

            // Instrucciones sanitizadas: reemplazar ';' (sep. recetas) y '|' (sep. cantidades)
            String safeInstructions = d.instructions
                    .replaceAll(";", ",")
                    .replaceAll("\\|", "-")
                    .replaceAll("\\s+", " ")
                    .trim();
            instructionsLine.append(key).append(":").append(safeInstructions);
        }

        // Línea de preferencias de usuario (propagada a GraphAgent y RecommendationAgent)
        String userPrefsLine = buildUserPrefsLine(maxTime, restrictions);

        return "userIngredients=" + userIngredients + "\n"
                + recipesLine        + "\n"
                + ingredientsLine    + "\n"
                + timesLine          + "\n"
                + servingsLine       + "\n"
                + tagsLine           + "\n"
                + cuisinesLine       + "\n"
                + dishTypesLine      + "\n"
                + healthScoresLine   + "\n"
                + tfidfLine          + "\n"
                + instructionsLine   + "\n"
                + userPrefsLine      + "\n";
    }

    private String buildUserPrefsLine(int maxTime, String restrictions) {
        StringBuilder sb = new StringBuilder("userPrefs=");
        sb.append("maxTime:").append(maxTime);
        sb.append(";restrictions:").append(restrictions.replace(";", ","));
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Minería de texto: TF-IDF + similitud coseno
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Carga el IDF pre-calculado desde idf_corpus.json (classpath).
     *
     * El fichero se genera offline con utils.IdfCorpusBuilder sobre
     * el corpus RecipeNLG (full_dataset.csv) y se coloca en src/main/resources/.
     *
     * Si el fichero no existe o falla la carga, devuelve un mapa vacío:
     * computeTfIdfScores usará entonces el IDF local como fallback.
     */
    private Map<String, Double> loadCorpusIdf() {
        try (InputStream is = getClass().getResourceAsStream(IDF_RESOURCE)) {
            if (is == null) {
                System.out.println("TextMiningAgent: idf_corpus.json no encontrado. "
                        + "Usando IDF local (genera el fichero con utils.IdfCorpusBuilder).");
                return new HashMap<>();
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                @SuppressWarnings("unchecked")
                Map<String, Double> idf = gson.fromJson(reader, Map.class);
                System.out.println("TextMiningAgent: IDF de RecipeNLG cargado ("
                        + idf.size() + " terminos).");
                return idf;
            }
        } catch (Exception e) {
            System.err.println("TextMiningAgent: error cargando idf_corpus.json: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Calcula la puntuación TF-IDF + coseno para cada receta respecto al usuario.
     *
     * IDF utilizado (en orden de prioridad):
     *   1. corpusIdf: IDF pre-calculado sobre RecipeNLG (2M recetas) — preciso
     *      Términos comunes en recetas (garlic, salt) reciben peso bajo.
     *      Términos específicos (truffle, saffron) reciben peso alto.
     *   2. Fallback: IDF calculado sobre las recetas actuales de Spoonacular.
     *      Menos preciso (solo 3-5 documentos) pero funcional sin el fichero.
     *
     * Resultado: mapa de nombreReceta → puntuación coseno [0, 1]
     */
    Map<String, Double> computeTfIdfScores(String userIngredients,
                                           Map<String, RecipeData> recipeDataMap) {
        String userText = userIngredients.replace(",", " ");
        List<String> userTokens = tokenize(userText);

        // TF-IDF sobre la lista de ingredientes de cada receta (no sobre las instrucciones).
        // Así medimos similitud real entre lo que el usuario tiene y lo que la receta necesita.
        Map<String, List<String>> recipeTokens = new LinkedHashMap<>();
        for (Map.Entry<String, RecipeData> entry : recipeDataMap.entrySet()) {
            // Unimos los nombres de ingredientes como texto para tokenizar
            String ingredientText = String.join(" ", entry.getValue().ingredients);
            recipeTokens.put(entry.getKey(), tokenize(ingredientText));
        }

        // IDF: corpus externo (RecipeNLG) si está disponible, local si no
        Map<String, Double> idf;
        if (!corpusIdf.isEmpty()) {
            idf = corpusIdf;
        } else {
            List<List<String>> localCorpus = new ArrayList<>();
            localCorpus.add(userTokens);
            localCorpus.addAll(recipeTokens.values());
            idf = computeIdf(localCorpus);
        }

        Map<String, Double> userTfIdf = computeTfIdfVector(computeTf(userTokens), idf);

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
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /**
     * Decide si un nombre de Spoonacular es un ingrediente real o texto de instrucciones
     * que se coló en extendedIngredients por error de parseo.
     *
     * Rechaza si:
     *   - Más de 5 palabras ("in a soup pot over medium heat")
     *   - Más de 50 caracteres
     *   - Empieza con un verbo de instrucción ("add ginger and onion")
     *   - Empieza con preposición de lugar/modo ("in a", "over medium", "at high")
     */
    boolean isValidIngredientName(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.trim().toLowerCase();
        if (lower.length() > 50) return false;
        String[] words = lower.split("\\s+");
        if (words.length > 5) return false;
        if (words.length > 0 && INSTRUCTION_VERBS.contains(words[0])) return false;
        if (lower.startsWith("in ") || lower.startsWith("over ")
                || lower.startsWith("at ") || lower.startsWith("on a ")) return false;
        return true;
    }

    String normalize(String text) {
        return text.trim()
                .toLowerCase()
                .replaceAll("[;:\n]", "")
                .replaceAll("[^a-z0-9 ()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    String sanitizeName(String name) {
        return name.replaceAll("[;:\n]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

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
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : -1;
    }
    private double  getDoubleSafe(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0.0;
    }
    private boolean getBoolSafe  (JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }
    private String  getStringSafe(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    // ════════════════════════════════════════════════════════════════════════
    // Clases de datos
    // ════════════════════════════════════════════════════════════════════════

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
     * rawText:           summary + instructions concatenados, para TF-IDF interno
     * instructions:      pasos de cocción sanitizados, se envían a agentes downstream
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
        final String               instructions;

        RecipeData(String name,
                   List<String> ingredients,
                   List<IngredientInfo> ingredientDetails,
                   int readyInMinutes, int servings,
                   boolean vegan, boolean vegetarian,
                   boolean glutenFree, boolean dairyFree,
                   List<String> cuisines,
                   List<String> dishTypes,
                   int healthScore,
                   String rawText,
                   String instructions) {
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
            this.instructions      = instructions;
        }
    }
}
