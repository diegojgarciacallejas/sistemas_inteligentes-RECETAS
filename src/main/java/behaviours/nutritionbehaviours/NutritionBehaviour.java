package behaviours.nutritionbehaviours;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.nio.charset.StandardCharsets;

/**
 * NutritionBehaviour — información nutricional usando OpenFoodFacts (gratuito, sin API key).
 *
 * Recibe: JSON {"id": ..., "name": "recipeName"}
 * Busca el nombre de la receta en OpenFoodFacts y extrae los macros por 100 g del
 * primer producto encontrado.
 *
 * Endpoint: https://world.openfoodfacts.org/cgi/search.pl
 */
public class NutritionBehaviour extends AchieveREResponder {

    private static final String BASE_URL =
            "https://world.openfoodfacts.org/cgi/search.pl"
            + "?action=process&json=true&page_size=1&fields=product_name,nutriments"
            + "&search_terms=";

    private final HttpClient httpClient;
    private final Gson gson;

    public NutritionBehaviour(Agent a, MessageTemplate mt, HttpClient httpClient, Gson gson) {
        super(a, mt);
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    protected ACLMessage handleRequest(ACLMessage request) {
        System.out.println("NutritionAgent: Solicitud recibida para: " + request.getContent());
        ACLMessage agree = request.createReply();
        agree.setPerformative(ACLMessage.AGREE);
        return agree;
    }

    @Override
    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
        ACLMessage inform = request.createReply();

        try {
            JsonObject requestData = gson.fromJson(request.getContent(), JsonObject.class);
            String recipeName = requestData.get("name").getAsString();

            System.out.println("NutritionAgent: Buscando nutrición para '" + recipeName + "' en OpenFoodFacts...");

            String encoded = URLEncoder.encode(recipeName, StandardCharsets.UTF_8);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + encoded))
                    .header("User-Agent", "RecipeRecommenderAgent/1.0")
                    .GET()
                    .build();

            HttpResponse<String> httpResponse =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonObject body = gson.fromJson(httpResponse.body(), JsonObject.class);
                JsonArray products = body.has("products") ? body.getAsJsonArray("products") : null;

                if (products != null && products.size() > 0) {
                    JsonObject nutriments = products.get(0).getAsJsonObject()
                            .getAsJsonObject("nutriments");

                    JsonObject result = new JsonObject();
                    result.addProperty("recipe",   recipeName);
                    result.addProperty("calories", formatNutrient(nutriments, "energy-kcal_100g") + " kcal");
                    result.addProperty("carbs",    formatNutrient(nutriments, "carbohydrates_100g") + "g");
                    result.addProperty("fat",      formatNutrient(nutriments, "fat_100g") + "g");
                    result.addProperty("protein",  formatNutrient(nutriments, "proteins_100g") + "g");

                    inform.setPerformative(ACLMessage.INFORM);
                    inform.setContent(result.toString());
                } else {
                    inform.setPerformative(ACLMessage.FAILURE);
                    inform.setContent("{\"error\": \"No se encontraron datos nutricionales para: " + recipeName + "\"}");
                }
            } else {
                inform.setPerformative(ACLMessage.FAILURE);
                inform.setContent("{\"error\": \"OpenFoodFacts respondió con estado: " + httpResponse.statusCode() + "\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            inform.setPerformative(ACLMessage.FAILURE);
            inform.setContent("{\"error\": \"Error al obtener datos nutricionales.\"}");
        }

        return inform;
    }

    private String formatNutrient(JsonObject nutriments, String key) {
        if (nutriments == null || !nutriments.has(key) || nutriments.get(key).isJsonNull()) {
            return "N/A";
        }
        double val = nutriments.get(key).getAsDouble();
        return val == Math.floor(val) ? String.valueOf((int) val) : String.format("%.1f", val);
    }
}
