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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TextMiningBehaviour
 *
 * Behaviour cíclico que:
 *  1. Escucha mensajes con conversationId "RECIPE_SEARCH_RESULT" de RecipeSearchAgent
 *  2. Para cada receta, llama a Spoonacular /recipes/{id}/information
 *  3. Extrae y normaliza: ingredientes (con cantidades), tiempo, raciones,
 *     etiquetas dietéticas, cocinas, tipos de plato y puntuación de salud
 *  4. Construye un mensaje de texto compatible con GraphBehaviour y OntologyAgent
 *  5. Lo envía a OntologyAgent con conversationId "TEXT_MINING_RESULT"
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

    // ── Interfaz funcional para el fetching HTTP ────────────────────────────
    /**
     * Contrato para obtener el JSON de una receta dado su ID.
     * En producción: llama a Spoonacular.
     * En tests: devuelve un String JSON hardcodeado.
     */
    @FunctionalInterface
    public interface RecipeFetcher {
        /**
         * @param id ID de la receta en Spoonacular
         * @return cuerpo JSON de /recipes/{id}/information, o null si falla
         */
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

    /**
     * Constructor para tests: acepta un RecipeFetcher externo.
     * No necesita JADE corriendo ni acceso a red.
     */
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

            return buildOutputMessage(userIngredients, recipeDataMap);

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
                new ArrayList<>(), new ArrayList<>(), -1);
    }

    /**
     * Parsea el JSON de Spoonacular. No hace HTTP: testeable con cualquier String JSON.
     *
     * Extrae de extendedIngredients: nombre, cantidad (amount) y unidad (unit).
     * Extrae también: readyInMinutes, servings, etiquetas dietéticas,
     * cuisines, dishTypes y healthScore.
     */
    RecipeData extractRecipeData(String name, String jsonBody) {
        JsonObject data = gson.fromJson(jsonBody, JsonObject.class);

        List<String> ingredients       = new ArrayList<>();
        List<IngredientInfo> ingredientDetails = new ArrayList<>();

        if (data.has("extendedIngredients")) {
            for (JsonElement el : data.getAsJsonArray("extendedIngredients")) {
                JsonObject ing = el.getAsJsonObject();
                if (ing.has("name") && !ing.get("name").isJsonNull()) {
                    String normalized = normalize(ing.get("name").getAsString());
                    if (!normalized.isEmpty()) {
                        ingredients.add(normalized);
                        double amount = getDoubleSafe(ing, "amount");
                        String unit   = sanitizeUnit(getStringSafe(ing, "unit"));
                        ingredientDetails.add(new IngredientInfo(normalized, amount, unit));
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

        return new RecipeData(name, ingredients, ingredientDetails,
                readyInMinutes, servings, vegan, vegetarian, glutenFree, dairyFree,
                cuisines, dishTypes, healthScore);
    }

    /**
     * Construye el mensaje de texto para OntologyAgent.
     *
     * Lineas existentes (retro-compatibles con GraphBehaviour y OntologyAgent):
     *   userIngredients=egg,rice,tomato
     *   recipes=RecipeName:ing1,ing2;OtraReceta:ing3
     *   recipeTimes=RecipeName:20;OtraReceta:35
     *   recipeServings=RecipeName:4;OtraReceta:2
     *   recipeTags=RecipeName:vegetarian;OtraReceta:vegan,glutenFree
     *
     * Lineas nuevas (cantidades e información adicional):
     *   recipeIngredients=RecipeName:ing1|2|cups,ing2|100|g;OtraReceta:ing3|1|unit
     *   recipeCuisines=RecipeName:italian;OtraReceta:mediterranean
     *   recipeDishTypes=RecipeName:lunch,main course;OtraReceta:dinner
     *   recipeHealthScores=RecipeName:85;OtraReceta:72
     *
     * El separador '|' dentro de cada ingrediente en recipeIngredients indica:
     *   nombre|cantidad|unidad
     */
    String buildOutputMessage(String userIngredients, Map<String, RecipeData> recipeDataMap) {
        StringBuilder recipesLine        = new StringBuilder("recipes=");
        StringBuilder ingredientsLine    = new StringBuilder("recipeIngredients=");
        StringBuilder timesLine          = new StringBuilder("recipeTimes=");
        StringBuilder servingsLine       = new StringBuilder("recipeServings=");
        StringBuilder tagsLine           = new StringBuilder("recipeTags=");
        StringBuilder cuisinesLine       = new StringBuilder("recipeCuisines=");
        StringBuilder dishTypesLine      = new StringBuilder("recipeDishTypes=");
        StringBuilder healthScoresLine   = new StringBuilder("recipeHealthScores=");

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
            }
            first = false;

            String     key = entry.getKey();
            RecipeData d   = entry.getValue();

            // Línea existente: solo nombres de ingredientes
            recipesLine.append(key).append(":")
                    .append(String.join(",", d.ingredients));

            // Línea nueva: nombre|cantidad|unidad por ingrediente
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

            cuisinesLine.append(key).append(":")
                    .append(String.join(",", d.cuisines));
            dishTypesLine.append(key).append(":")
                    .append(String.join(",", d.dishTypes));
            healthScoresLine.append(key).append(":").append(d.healthScore);
        }

        return "userIngredients=" + userIngredients + "\n"
                + recipesLine        + "\n"
                + ingredientsLine    + "\n"
                + timesLine          + "\n"
                + servingsLine       + "\n"
                + tagsLine           + "\n"
                + cuisinesLine       + "\n"
                + dishTypesLine      + "\n"
                + healthScoresLine   + "\n";
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /**
     * Normaliza un nombre de ingrediente.
     * Ejemplo: "  Fresh Garlic! " -> "fresh garlic"
     */
    String normalize(String text) {
        return text.trim()
                .toLowerCase()
                .replaceAll("[;:\n]", "")
                .replaceAll("[^a-z0-9 ()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Sanitiza un nombre de receta eliminando los separadores del protocolo.
     * ; y : se usan como delimitadores en el mensaje entre agentes.
     */
    String sanitizeName(String name) {
        return name.replaceAll("[;:\n]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Elimina de la unidad los separadores del protocolo (|, ;, :).
     */
    private String sanitizeUnit(String unit) {
        if (unit == null) return "";
        return unit.replaceAll("[|;:\n]", "").trim();
    }

    /**
     * Formatea una cantidad eliminando decimales innecesarios.
     * Ejemplos: 2.0 -> "2", 1.5 -> "1.5", 0.25 -> "0.25"
     */
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
     * Datos extraidos de una receta.
     * Campos -1 = Spoonacular no los devolvio.
     * ingredients: nombres normalizados (compatibilidad con GraphBehaviour/OntologyAgent).
     * ingredientDetails: mismos ingredientes con cantidad y unidad.
     */
    static class RecipeData {
        final String             name;
        final List<String>       ingredients;
        final List<IngredientInfo> ingredientDetails;
        final int                readyInMinutes;
        final int                servings;
        final boolean            vegan;
        final boolean            vegetarian;
        final boolean            glutenFree;
        final boolean            dairyFree;
        final List<String>       cuisines;
        final List<String>       dishTypes;
        final int                healthScore;

        RecipeData(String name,
                   List<String> ingredients,
                   List<IngredientInfo> ingredientDetails,
                   int readyInMinutes, int servings,
                   boolean vegan, boolean vegetarian,
                   boolean glutenFree, boolean dairyFree,
                   List<String> cuisines,
                   List<String> dishTypes,
                   int healthScore) {
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
        }
    }
}
