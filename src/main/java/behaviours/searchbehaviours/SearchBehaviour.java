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
 * SearchBehaviour — búsqueda de recetas usando Spoonacular /findByIngredients.
 *
 * Recibe del InterfaceAgent un contenido multi-línea con el formato:
 *   ingredients=tomate, pollo, arroz
 *   quantities=...
 *   persons=2
 *   maxTime=30
 *   ...
 *
 * Llama a Spoonacular, construye el JSON de resultado y lo envía directamente
 * a TextMiningAgent con conversationId "RECIPE_SEARCH_RESULT".
 *
 * Endpoint: https://api.spoonacular.com/recipes/findByIngredients
 */
public class SearchBehaviour extends AchieveREResponder {

    private static final String SPOONACULAR_URL =
            "https://api.spoonacular.com/recipes/findByIngredients?number=3&ingredients=";

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public SearchBehaviour(Agent agent, MessageTemplate template,
                           HttpClient httpClient, Gson gson, String apiKey) {
        super(agent, template);
        this.httpClient = httpClient;
        this.gson       = gson;
        this.apiKey     = apiKey;
    }

    @Override
    protected ACLMessage handleRequest(ACLMessage request) {
        System.out.println("RecipeSearchAgent: solicitud recibida de "
                + request.getSender().getLocalName());
        ACLMessage agree = request.createReply();
        agree.setPerformative(ACLMessage.AGREE);
        return agree;
    }

    @Override
    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
        ACLMessage reply = request.createReply();

        // Extraer la línea ingredients= del contenido multi-línea del GUI
        String rawContent = request.getContent().trim();
        String ingredients = rawContent; // fallback: contenido completo si no hay prefijo
        for (String line : rawContent.split("\n")) {
            if (line.startsWith("ingredients=")) {
                ingredients = line.substring("ingredients=".length()).trim();
                break;
            }
        }

        String ingredientsEn = IngredientTranslator.translateIngredients(ingredients);
        System.out.println("RecipeSearchAgent: traducción ES→EN: " + ingredients + " → " + ingredientsEn);
        System.out.println("RecipeSearchAgent: buscando en Spoonacular: " + ingredientsEn);

        try {
            String encoded = URLEncoder.encode(ingredientsEn, StandardCharsets.UTF_8);
            String url = SPOONACULAR_URL + encoded + "&apiKey=" + apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url)).GET().build();
            HttpResponse<String> httpResponse =
                    httpClient.send(httpRequest, BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonArray meals = gson.fromJson(httpResponse.body(), JsonArray.class);
                JsonArray recipesArray = new JsonArray();

                for (JsonElement el : meals) {
                    JsonObject meal = el.getAsJsonObject();
                    JsonObject rec  = new JsonObject();
                    rec.addProperty("id",   meal.get("id").getAsInt());
                    rec.addProperty("name", meal.get("title").getAsString());
                    recipesArray.add(rec);
                }

                JsonObject result = new JsonObject();
                result.addProperty("userIngredients", ingredientsEn);
                result.add("recipes", recipesArray);

                // Reenviar al pipeline: TextMiningAgent escucha RECIPE_SEARCH_RESULT
                ACLMessage forward = new ACLMessage(ACLMessage.INFORM);
                forward.addReceiver(new AID("TextMiningAgent", AID.ISLOCALNAME));
                forward.setConversationId("RECIPE_SEARCH_RESULT");
                forward.setContent(result.toString());
                myAgent.send(forward);

                System.out.println("RecipeSearchAgent: " + recipesArray.size()
                        + " recetas encontradas, enviadas a TextMiningAgent.");

                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("Búsqueda completada: " + recipesArray.size() + " recetas.");

            } else {
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setContent("{\"error\": \"Spoonacular respondió con estado: "
                        + httpResponse.statusCode() + "\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setContent("{\"error\": \"Error comunicando con Spoonacular.\"}");
        }

        return reply;
    }
}
