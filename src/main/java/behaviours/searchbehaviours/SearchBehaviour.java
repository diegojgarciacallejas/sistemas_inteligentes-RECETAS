package behaviours.searchbehaviours;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import utils.IngredientTranslator;

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

    private final HttpClient httpClient;
    private final Gson gson;

    public SearchBehaviour(Agent agent, MessageTemplate template, HttpClient httpClient, Gson gson) {
        super(agent, template);
        this.httpClient = httpClient;
        this.gson = gson;
    }

    protected ACLMessage handleRequest(ACLMessage request) {
        System.out.println("RecipeSearchAgent: Received request to search for: " + request.getContent());
        ACLMessage reply = request.createReply();
        reply.setPerformative(ACLMessage.AGREE);
        return reply;
    }

    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
        ACLMessage reply = request.createReply();
        String rawContent  = request.getContent().trim();
        String ingredients = rawContent;
        int    maxTime     = -1;
        String restrictions = "";

        for (String line : rawContent.split("\n")) {
            if (line.startsWith("ingredients=")) {
                ingredients = line.substring("ingredients=".length()).trim();
            } else if (line.startsWith("maxTime=")) {
                try { maxTime = Integer.parseInt(line.substring("maxTime=".length()).trim()); }
                catch (NumberFormatException ignored) {}
            } else if (line.startsWith("restrictions=")) {
                restrictions = line.substring("restrictions=".length()).trim();
            }
        }

        // Traducir ES→EN
        String ingredientsEn = IngredientTranslator.translateIngredients(ingredients);
        if (!ingredientsEn.equals(ingredients)) {
            System.out.println("RecipeSearchAgent: traducción ES→EN: [" + ingredients + "] → [" + ingredientsEn + "]");
        }
        System.out.println("RecipeSearchAgent: Searching TheMealDB for: " + ingredientsEn);

        try {
            // Usar el primer ingrediente para buscar en TheMealDB
            String firstIngredient = ingredientsEn.split(",")[0].trim();
            String encoded = URLEncoder.encode(firstIngredient, StandardCharsets.UTF_8.toString());
            String url = "https://www.themealdb.com/api/json/v1/1/filter.php?i=" + encoded;

            HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> httpResponse = this.httpClient.send(httpRequest, BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonObject body = this.gson.fromJson(httpResponse.body(), JsonObject.class);
                JsonArray meals = body.has("meals") && !body.get("meals").isJsonNull()
                        ? body.getAsJsonArray("meals") : new JsonArray();

                JsonArray recipesArray = new JsonArray();
                int count = 0;
                for (JsonElement el : meals) {
                    if (count >= 3) break;
                    JsonObject meal = el.getAsJsonObject();
                    JsonObject rec = new JsonObject();
                    rec.addProperty("id", Integer.parseInt(meal.get("idMeal").getAsString()));
                    rec.addProperty("name", meal.get("strMeal").getAsString());
                    recipesArray.add(rec);
                    count++;
                }

                JsonObject result = new JsonObject();
                result.addProperty("userIngredients", ingredientsEn);
                result.add("recipes", recipesArray);
                if (maxTime > 0)           result.addProperty("maxTime", maxTime);
                if (!restrictions.isEmpty()) result.addProperty("restrictions", restrictions);

                // Reenviar al pipeline: TextMiningAgent escucha RECIPE_SEARCH_RESULT
                ACLMessage forward = new ACLMessage(ACLMessage.INFORM);
                forward.addReceiver(new AID("TextMiningAgent", AID.ISLOCALNAME));
                forward.setConversationId("RECIPE_SEARCH_RESULT");
                forward.setContent(result.toString());
                myAgent.send(forward);
                System.out.println("RecipeSearchAgent: reenviado a TextMiningAgent -> " + result);

                // ACK simple de vuelta al InterfaceAgent (protocolo FIPA_REQUEST)
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("Búsqueda completada: " + recipesArray.size() + " receta(s) enviadas al pipeline.");
            } else {
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setContent("{\"error\": \"TheMealDB status: " + httpResponse.statusCode() + "\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setContent("{\"error\": \"Error comunicando con TheMealDB: " + e.getMessage() + "\"}");
        }

        return reply;
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
