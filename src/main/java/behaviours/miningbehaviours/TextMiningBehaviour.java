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
 *  2. Para cada receta, llama a TheMealDB /lookup.php?i={id}
 *  3. Extrae y normaliza: ingredientes (strIngredient1..20), área/cocina,
 *     etiquetas dietéticas (strTags, strCategory) y tipo de plato
 *  4. Construye un mensaje de texto compatible con GraphBehaviour y OntologyAgent
 *  5. Lo envía a OntologyAgent con conversationId "TEXT_MINING_RESULT"
 *
 * Diseño para testabilidad:
 *   La interfaz funcional RecipeFetcher separa la lógica HTTP del parsing.
 *   En producción se usa el fetcher real (TheMealDB).
 *   En tests se inyecta una lambda que devuelve JSON hardcodeado, sin red.
 */
public class TextMiningBehaviour extends CyclicBehaviour {

    // ── Constantes ──────────────────────────────────────────────────────────
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
     * Parsea el JSON de TheMealDB. No hace HTTP: testeable con cualquier String JSON.
     *
     * TheMealDB devuelve: {"meals":[{...}]}
     * Ingredientes en strIngredient1..strIngredient20 / strMeasure1..strMeasure20.
     * Etiquetas dietéticas inferidas de strCategory y strTags.
     * readyInMinutes y servings no están disponibles en TheMealDB → -1.
     */
    RecipeData extractRecipeData(String name, String jsonBody) {
        JsonObject data = gson.fromJson(jsonBody, JsonObject.class);

        // TheMealDB envuelve la respuesta en "meals":[{...}]
        JsonObject meal;
        if (data.has("meals") && !data.get("meals").isJsonNull()) {
            meal = data.getAsJsonArray("meals").get(0).getAsJsonObject();
        } else {
            meal = data; // fallback para tests con objeto directo
        }

        List<String> ingredients       = new ArrayList<>();
        List<IngredientInfo> ingredientDetails = new ArrayList<>();

        // strIngredient1..strIngredient20 (cadena vacía = sin ingrediente)
        for (int i = 1; i <= 20; i++) {
            String ing = getStringSafe(meal, "strIngredient" + i);
            if (ing.isEmpty()) continue;
            String normalized = normalize(ing);
            if (normalized.isEmpty()) continue;
            String measure = sanitizeUnit(getStringSafe(meal, "strMeasure" + i));
            ingredients.add(normalized);
            ingredientDetails.add(new IngredientInfo(normalized, 0, measure));
        }

        // Etiquetas dietéticas: inferidas de strCategory y strTags
        String category = getStringSafe(meal, "strCategory").toLowerCase();
        String tagsRaw  = getStringSafe(meal, "strTags").toLowerCase();

        boolean vegetarian = category.contains("vegetarian") || tagsRaw.contains("vegetarian");
        boolean vegan      = category.contains("vegan")      || tagsRaw.contains("vegan");
        boolean glutenFree = tagsRaw.contains("gluten-free") || tagsRaw.contains("glutenfree");
        boolean dairyFree  = tagsRaw.contains("dairy-free")  || tagsRaw.contains("dairyfree");

        // Cocina: strArea (ej. "Italian", "Mexican")
        List<String> cuisines = new ArrayList<>();
        String area = getStringSafe(meal, "strArea");
        if (!area.isEmpty() && !area.equalsIgnoreCase("unknown")) {
            cuisines.add(sanitizeName(area).toLowerCase());
        }

        // Tipo de plato: categoría de TheMealDB
        List<String> dishTypes = new ArrayList<>();
        if (!category.isEmpty()) dishTypes.add(sanitizeName(category));

        return new RecipeData(name, ingredients, ingredientDetails,
                -1, -1, vegan, vegetarian, glutenFree, dairyFree,
                cuisines, dishTypes, -1);
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