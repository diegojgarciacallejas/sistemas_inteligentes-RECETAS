package main.java.agents;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RecipeSearchAgent extends Agent {
    private HttpClient httpClient;
    private Gson gson;

    private static final String API_KEY = "74e8728ac10847199e9b7db0f0d97a4e"; 

    @Override
    protected void setup() {
        System.out.println("RecipeSearchAgent " + getAID().getName() + " is ready.");
        httpClient = HttpClient.newHttpClient();
        gson = new Gson();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("recipe-search");
        sd.setName("JADE-recipe-search");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
        );

        addBehaviour(new AchieveREResponder(this, template) {
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
                    String url = "https://api.spoonacular.com/recipes/findByIngredients?ingredients=" + encodedIngredients + "&number=3&apiKey=" + API_KEY;
                    
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
                        resultData.addProperty("userIngredients", ingredients);  // línea nueva
                        resultData.add("recipes", resultRecipes);
                        
                        inform.setPerformative(ACLMessage.INFORM);
                        inform.setContent(resultData.toString());
                    } else {
                        inform.setPerformative(ACLMessage.FAILURE);
                        inform.setContent("{\"error\": \"Failed to retrieve recipes. Check API key. Status: " + httpResponse.statusCode() + "\"}");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    inform.setPerformative(ACLMessage.FAILURE);
                    inform.setContent("{\"error\": \"Error communicating with Spoonacular API.\"}");
                }
                
                return inform;
            }
        });
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}
