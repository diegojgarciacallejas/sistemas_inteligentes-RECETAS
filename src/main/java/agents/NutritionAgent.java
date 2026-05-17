package agents;

import com.google.gson.Gson;
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

public class NutritionAgent extends Agent {
    private HttpClient httpClient;
    private Gson gson;
    private static final String API_KEY = "74e8728ac10847199e9b7db0f0d97a4e";

    @Override
    protected void setup() {
        System.out.println("NutritionAgent " + getAID().getName() + " is ready.");
        httpClient = HttpClient.newHttpClient();
        gson = new Gson();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("nutrition-analysis");
        sd.setName("JADE-nutrition");
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
                System.out.println("NutritionAgent: Received request to analyze: " + request.getContent());
                ACLMessage agree = request.createReply();
                agree.setPerformative(ACLMessage.AGREE);
                return agree;
            }

            @Override
            protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
                ACLMessage inform = request.createReply();
                
                try {
                    JsonObject requestData = gson.fromJson(request.getContent(), JsonObject.class);
                    int recipeId = requestData.get("id").getAsInt();
                    String recipeName = requestData.get("name").getAsString();
                    
                    System.out.println("NutritionAgent: Getting nutrition for ID " + recipeId + " from Spoonacular...");
                    
                    String url = "https://api.spoonacular.com/recipes/" + recipeId + "/nutritionWidget.json?apiKey=" + API_KEY;
                    
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .build();

                    HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                    if (httpResponse.statusCode() == 200) {
                        JsonObject nutritionData = gson.fromJson(httpResponse.body(), JsonObject.class);
                        
                        JsonObject result = new JsonObject();
                        result.addProperty("recipe", recipeName);
                        result.addProperty("calories", nutritionData.get("calories").getAsString());
                        result.addProperty("carbs", nutritionData.get("carbs").getAsString());
                        result.addProperty("fat", nutritionData.get("fat").getAsString());
                        result.addProperty("protein", nutritionData.get("protein").getAsString());

                        inform.setPerformative(ACLMessage.INFORM);
                        inform.setContent(result.toString());
                    } else {
                        inform.setPerformative(ACLMessage.FAILURE);
                        inform.setContent("{\"error\": \"Failed to retrieve nutrition. Check API key. Status: " + httpResponse.statusCode() + "\"}");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    inform.setPerformative(ACLMessage.FAILURE);
                    inform.setContent("{\"error\": \"Invalid format or API error. Expected JSON with 'id' and 'name'.\"}");
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
