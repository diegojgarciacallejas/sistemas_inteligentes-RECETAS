package main.java.behaviours.searchbehaviours;

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
import java.nio.charset.StandardCharsets;

public class SearchBehaviour extends AchieveREResponder {
    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public SearchBehaviour(Agent a, MessageTemplate mt, HttpClient httpClient, Gson gson, String apiKey) {
        super(a, mt);
        this.httpClient = httpClient;
        this.gson = gson;
        this.apiKey = apiKey;
    }

    @Override
    protected ACLMessage handleRequest(ACLMessage request) {
        System.out.println("RecipeSearchAgent: Received request to search for: " + request.getContent());
        ACLMessage agree = request.createReply();
        agree.setPerformative(ACLMessage.AGREE);
        return agree;
    }

    @Override
    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
        ACLMessage inform = request.createReply();
        String ingredients = request.getContent().trim();
        System.out.println("RecipeSearchAgent: Searching Spoonacular for: " + ingredients);

        try {
            String encodedIngredients = URLEncoder.encode(ingredients, StandardCharsets.UTF_8.toString());
            String url = "https://api.spoonacular.com/recipes/findByIngredients?ingredients=" + encodedIngredients + "&number=3&apiKey=" + apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonArray meals = gson.fromJson(httpResponse.body(), JsonArray.class);
                JsonArray resultRecipes = new JsonArray();

                for (JsonElement element : meals) {
                    JsonObject meal = element.getAsJsonObject();
                    JsonObject recipeInfo = new JsonObject();
                    recipeInfo.addProperty("id", meal.get("id").getAsInt());
                    recipeInfo.addProperty("name", meal.get("title").getAsString());
                    resultRecipes.add(recipeInfo);
                }

                JsonObject resultData = new JsonObject();
                resultData.addProperty("userIngredients", ingredients);
                resultData.add("recipes", resultRecipes);

                inform.setPerformative(ACLMessage.INFORM);
                inform.setContent(resultData.toString());
            } else {
                inform.setPerformative(ACLMessage.FAILURE);
                inform.setContent("{\"error\": \"Failed to retrieve recipes. Status: " + httpResponse.statusCode() + "\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            inform.setPerformative(ACLMessage.FAILURE);
            inform.setContent("{\"error\": \"Error communicating with Spoonacular API.\"}");
        }

        return inform;
    }
}
