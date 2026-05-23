package behaviours.nutritionbehaviours;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * NutritionBehaviour — información nutricional usando Spoonacular /nutritionWidget.json.
 *
 * Recibe: JSON {"id": <recipeId>, "name": "<recipeName>"}
 * Responde: JSON {"recipe":"...", "calories":"...", "carbs":"...", "fat":"...", "protein":"..."}
 *
 * Endpoint: https://api.spoonacular.com/recipes/{id}/nutritionWidget.json
 */
public class NutritionBehaviour extends AchieveREResponder {

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public NutritionBehaviour(Agent a, MessageTemplate mt,
                               HttpClient httpClient, Gson gson, String apiKey) {
        super(a, mt);
        this.httpClient = httpClient;
        this.gson       = gson;
        this.apiKey     = apiKey;
    }

    @Override
    protected ACLMessage handleRequest(ACLMessage request) {
        System.out.println("NutritionAgent: solicitud recibida para: " + request.getContent());
        ACLMessage agree = request.createReply();
        agree.setPerformative(ACLMessage.AGREE);
        return agree;
    }

    @Override
    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
        ACLMessage inform = request.createReply();

        try {
            JsonObject requestData = gson.fromJson(request.getContent(), JsonObject.class);
            int    recipeId   = requestData.get("id").getAsInt();
            String recipeName = requestData.get("name").getAsString();

            System.out.println("NutritionAgent: obteniendo nutrición para ID "
                    + recipeId + " de Spoonacular...");

            String url = "https://api.spoonacular.com/recipes/" + recipeId
                    + "/nutritionWidget.json?apiKey=" + apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url)).GET().build();
            HttpResponse<String> httpResponse =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonObject nutritionData = gson.fromJson(httpResponse.body(), JsonObject.class);

                JsonObject result = new JsonObject();
                result.addProperty("recipe",   recipeName);
                result.addProperty("calories", nutritionData.get("calories").getAsString());
                result.addProperty("carbs",    nutritionData.get("carbs").getAsString());
                result.addProperty("fat",      nutritionData.get("fat").getAsString());
                result.addProperty("protein",  nutritionData.get("protein").getAsString());

                inform.setPerformative(ACLMessage.INFORM);
                inform.setContent(result.toString());
            } else {
                inform.setPerformative(ACLMessage.FAILURE);
                inform.setContent("{\"error\": \"Spoonacular respondió con estado: "
                        + httpResponse.statusCode() + "\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            inform.setPerformative(ACLMessage.FAILURE);
            inform.setContent("{\"error\": \"Error obteniendo datos nutricionales.\"}");
        }

        return inform;
    }
}
