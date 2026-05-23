package behaviours.searchbehaviours;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

public class SearchBehaviour extends AchieveREResponder {

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public SearchBehaviour(Agent agent, MessageTemplate template,
                           HttpClient httpClient, Gson gson, String apiKey) {
        super(agent, template);
        this.httpClient = httpClient;
        this.gson = gson;
        this.apiKey = apiKey;
    }

    protected ACLMessage handleRequest(ACLMessage request) {
        System.out.println("RecipeSearchAgent: Received request to search for: " + request.getContent());
        ACLMessage reply = request.createReply();
        reply.setPerformative(ACLMessage.AGREE);
        return reply;
    }

    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
        ACLMessage reply = request.createReply();
        String rawContent = request.getContent().trim();
        String ingredients = rawContent;
        for (String line : rawContent.split("\n")) {
            if (line.startsWith("ingredients=")) {
                ingredients = line.substring("ingredients=".length()).trim();
                break;
            }
        }

        // Traducir ES→EN para que Spoonacular entienda los ingredientes
        String ingredientsEn = IngredientTranslator.translateIngredients(ingredients);
        if (!ingredientsEn.equals(ingredients)) {
            System.out.println("RecipeSearchAgent: traducción ES→EN: [" + ingredients + "] → [" + ingredientsEn + "]");
        }
        System.out.println("RecipeSearchAgent: Searching Spoonacular for: " + ingredientsEn);

        try {
            String encoded = URLEncoder.encode(ingredientsEn, StandardCharsets.UTF_8.toString());
            String url = "https://api.spoonacular.com/recipes/findByIngredients?ingredients="
                    + encoded + "&number=3&apiKey=" + this.apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> httpResponse = this.httpClient.send(httpRequest, BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonArray results = this.gson.fromJson(httpResponse.body(), JsonArray.class);
                JsonArray recipesArray = new JsonArray();

                for (JsonElement el : results) {
                    JsonObject recipe = el.getAsJsonObject();
                    JsonObject rec = new JsonObject();
                    rec.addProperty("id", recipe.get("id").getAsInt());
                    rec.addProperty("name", recipe.get("title").getAsString());
                    recipesArray.add(rec);
                }

                JsonObject result = new JsonObject();
                result.addProperty("userIngredients", ingredientsEn);
                result.add("recipes", recipesArray);
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(result.toString());
            } else {
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setContent("{\"error\": \"Failed to retrieve recipes. Status: " + httpResponse.statusCode() + "\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setContent("{\"error\": \"Error communicating with Spoonacular API.\"}");
        }

        return reply;
    }
}
