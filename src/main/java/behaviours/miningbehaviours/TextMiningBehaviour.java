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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TextMiningBehaviour
 *
 * Behaviour cíclico que:
 *  1. Escucha mensajes con conversationId "RECIPE_SEARCH_RESULT" de RecipeSearchAgent
 *  2. Para cada receta, llama a Spoonacular /recipes/{id}/information
 *  3. Extrae y normaliza: ingredientes, tiempo, raciones, etiquetas dietéticas
 *  4. Construye un mensaje de texto compatible con GraphBehaviour
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
     *
     * Ejemplo de uso en un test:
     *   String fakeJson = "{ \"extendedIngredients\": [...] }";
     *   TextMiningBehaviour b = new TextMiningBehaviour(null, id -> fakeJson);
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
        return new RecipeData(name, new ArrayList<>(), -1, -1,
                false, false, false, false);
    }

    /**
     * Parsea el JSON de Spoonacular. No hace HTTP: testeable con cualquier String JSON.
     */
    RecipeData extractRecipeData(String name, String jsonBody) {
        JsonObject data = gson.fromJson(jsonBody, JsonObject.class);

        List<String> ingredients = new ArrayList<>();
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

        return new RecipeData(name, ingredients, readyInMinutes, servings,
                vegan, vegetarian, glutenFree, dairyFree);
    }

    /**
     * Construye el mensaje de texto para OntologyAgent.
     *
     * Lineas mandatorias (GraphBehaviour las lee directamente):
     *   userIngredients=egg,rice,tomato
     *   recipes=RecipeName:ing1,ing2;OtraReceta:ing3
     *
     * Lineas opcionales (para OntologyAgent y futuros agentes):
     *   recipeTimes=RecipeName:20;OtraReceta:35
     *   recipeServings=RecipeName:4;OtraReceta:2
     *   recipeTags=RecipeName:vegetarian;OtraReceta:vegan,glutenFree
     */
    String buildOutputMessage(String userIngredients, Map<String, RecipeData> recipeDataMap) {
        StringBuilder recipesLine  = new StringBuilder("recipes=");
        StringBuilder timesLine    = new StringBuilder("recipeTimes=");
        StringBuilder servingsLine = new StringBuilder("recipeServings=");
        StringBuilder tagsLine     = new StringBuilder("recipeTags=");

        boolean first = true;
        for (Map.Entry<String, RecipeData> entry : recipeDataMap.entrySet()) {
            if (!first) {
                recipesLine.append(";");
                timesLine.append(";");
                servingsLine.append(";");
                tagsLine.append(";");
            }
            first = false;

            String     key = entry.getKey();
            RecipeData d   = entry.getValue();

            recipesLine.append(key).append(":")
                    .append(String.join(",", d.ingredients));
            timesLine.append(key).append(":").append(d.readyInMinutes);
            servingsLine.append(key).append(":").append(d.servings);

            List<String> tags = new ArrayList<>();
            if (d.vegan)      tags.add("vegan");
            if (d.vegetarian) tags.add("vegetarian");
            if (d.glutenFree) tags.add("glutenFree");
            if (d.dairyFree)  tags.add("dairyFree");
            tagsLine.append(key).append(":").append(String.join(",", tags));
        }

        return "userIngredients=" + userIngredients + "\n"
                + recipesLine   + "\n"
                + timesLine     + "\n"
                + servingsLine  + "\n"
                + tagsLine      + "\n";
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
     * Ejemplo: "Pasta: Aglio; vegan" -> "Pasta Aglio vegan"
     */
    String sanitizeName(String name) {
        return name.replaceAll("[;:\n]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int     getIntSafe (JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : -1;
    }
    private boolean getBoolSafe(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Clase de datos
    // ════════════════════════════════════════════════════════════════════════

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

        RecipeData(String name, List<String> ingredients,
                   int readyInMinutes, int servings,
                   boolean vegan, boolean vegetarian,
                   boolean glutenFree, boolean dairyFree) {
            this.name           = name;
            this.ingredients    = ingredients;
            this.readyInMinutes = readyInMinutes;
            this.servings       = servings;
            this.vegan          = vegan;
            this.vegetarian     = vegetarian;
            this.glutenFree     = glutenFree;
            this.dairyFree      = dairyFree;
        }
    }
}