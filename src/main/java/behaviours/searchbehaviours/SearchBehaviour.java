package behaviours.searchbehaviours;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

/**
 * SearchBehaviour — búsqueda de recetas usando TheMealDB (gratuito, sin API key).
 *
 * TheMealDB sólo filtra por un ingrediente a la vez.
 * Estrategia: intentar con cada ingrediente de la lista hasta obtener resultados,
 * devolver hasta 3 recetas.
 *
 * Endpoint: https://www.themealdb.com/api/json/v1/1/filter.php?i={ingrediente}
 */
public class SearchBehaviour extends AchieveREResponder {

    private static final String BASE_URL = "https://www.themealdb.com/api/json/v1/1/filter.php?i=";
    private static final int    MAX_RESULTS = 3;

    private final HttpClient httpClient;
    private final Gson gson;

    // Constructor corregido (4 parámetros)
    public SearchBehaviour(Agent agent, MessageTemplate mt, HttpClient httpClient, Gson gson) {
        super(agent, mt);
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    protected ACLMessage handleRequest(ACLMessage request) {
        System.out.println("RecipeSearchAgent: Solicitud recibida para: "
                + request.getContent().split("\n")[0]);
        ACLMessage agree = request.createReply();
        agree.setPerformative(ACLMessage.AGREE);
        return agree;
    }

    @Override
    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
        ACLMessage inform = request.createReply();

        // Extraer la línea ingredients= del contenido multi-línea
        String rawContent = request.getContent().trim();
        String ingredients = rawContent;
        for (String line : rawContent.split("\n")) {
            if (line.startsWith("ingredients=")) {
                ingredients = line.substring("ingredients=".length()).trim();
                break;
            }
        }

        System.out.println("RecipeSearchAgent: Buscando en TheMealDB: " + ingredients);

        try {
            JsonArray meals = searchByIngredients(ingredients);

            JsonArray resultRecipes = new JsonArray();
            if (meals != null) {
                int count = 0;
                for (JsonElement el : meals) {
                    if (count >= MAX_RESULTS) break;
                    JsonObject meal = el.getAsJsonObject();
                    JsonObject recipe = new JsonObject();
                    recipe.addProperty("id", meal.get("idMeal").getAsInt());
                    recipe.addProperty("name", meal.get("strMeal").getAsString());
                    resultRecipes.add(recipe);
                    count++;
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("userIngredients", ingredients);
            result.add("recipes", resultRecipes);

            inform.setPerformative(ACLMessage.INFORM);
            inform.setContent(result.toString());

            System.out.println("RecipeSearchAgent: " + resultRecipes.size() + " recetas encontradas.");

        } catch (Exception e) {
            e.printStackTrace();
            inform.setPerformative(ACLMessage.FAILURE);
            inform.setContent("{\"error\": \"Error comunicando con TheMealDB.\"}");
        }

        return inform;
    }

    /**
     * Intenta buscar por cada ingrediente de la lista hasta encontrar resultados.
     * Devuelve null si ningún ingrediente produce resultados.
     */
    private JsonArray searchByIngredients(String ingredientsCsv) throws Exception {
        for (String ingredient : ingredientsCsv.split(",")) {
            ingredient = ingredient.trim();
            if (ingredient.isEmpty()) continue;

            String encoded = URLEncoder.encode(ingredient, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + encoded))
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                JsonObject body = gson.fromJson(res.body(), JsonObject.class);
                if (body.has("meals") && !body.get("meals").isJsonNull()) {
                    System.out.println("RecipeSearchAgent: resultados para '" + ingredient + "'.");
                    return body.getAsJsonArray("meals");
                }
            }
        }
        return null;
    }
}
