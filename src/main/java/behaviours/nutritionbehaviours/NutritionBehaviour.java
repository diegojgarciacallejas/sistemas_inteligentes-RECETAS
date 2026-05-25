package behaviours.nutritionbehaviours;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Obtiene información nutricional de Spoonacular para una receta concreta.
 *
 * Escucha mensajes INFORM con conversationId "NUTRITION_RESULT_REQUEST".
 * Formato de entrada: JSON {"id": <recipeId>, "name": "<recipeName>"}
 * Formato de salida:  JSON {"recipe":"...", "calories":"...", "carbs":"...",
 *                          "fat":"...", "protein":"..."}
 *
 * Responde al remitente con conversationId "NUTRITION_RESULT" y setInReplyTo
 * con el replyWith original para que RecommendationBehaviour lo recoja.
 *
 * Endpoint: https://api.spoonacular.com/recipes/{id}/nutritionWidget.json
 */
public class NutritionBehaviour extends CyclicBehaviour {

    private static final String CONV_IN      = "NUTRITION_RESULT_REQUEST";
    private static final String CONV_PREFETCH = "NUTRITION_PREFETCH";
    private static final String CONV_OUT     = "NUTRITION_RESULT";

    private final HttpClient              httpClient;
    private final Gson                    gson;
    private final String                  apiKey;
    // Caché rellenado por TextMiningAgent con datos de /information?includeNutrition=true
    private final Map<Integer, JsonObject> nutritionCache = new HashMap<>();

    public NutritionBehaviour(Agent a, HttpClient httpClient, Gson gson, String apiKey) {
        super(a);
        this.httpClient = httpClient;
        this.gson       = gson;
        this.apiKey     = apiKey;
    }

    @Override
    public void action() {
        MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.or(
                        MessageTemplate.MatchConversationId(CONV_IN),
                        MessageTemplate.MatchConversationId(CONV_PREFETCH)
                )
        );
        ACLMessage msg = myAgent.receive(mt);

        if (msg == null) {
            block();
            return;
        }

        if (CONV_PREFETCH.equals(msg.getConversationId())) {
            // TextMiningAgent nos manda la nutrición ya extraída → guardamos en caché
            try {
                JsonObject data = gson.fromJson(msg.getContent(), JsonObject.class);
                int id = data.get("id").getAsInt();
                nutritionCache.put(id, data);
                System.out.println("NutritionAgent: nutrición cacheada para id=" + id
                        + " (" + safeString(data, "name") + ")");
            } catch (Exception e) {
                System.err.println("NutritionAgent: error cacheando prefetch: " + e.getMessage());
            }
            return;
        }

        // CONV_IN = "NUTRITION_RESULT_REQUEST" — petición de RecommendationAgent
        System.out.println("NutritionAgent: solicitud recibida: " + msg.getContent());

        ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
        reply.addReceiver(msg.getSender());
        reply.setConversationId(CONV_OUT);
        if (msg.getReplyWith() != null && !msg.getReplyWith().isEmpty()) {
            reply.setInReplyTo(msg.getReplyWith());
        }

        try {
            JsonObject requestData = gson.fromJson(msg.getContent(), JsonObject.class);
            int    recipeId   = requestData.get("id").getAsInt();
            String recipeName = requestData.get("name").getAsString();

            // Primero miramos la caché (la rellena TextMiningAgent antes de llegar aquí)
            JsonObject cached = nutritionCache.get(recipeId);
            if (cached != null) {
                JsonObject result = new JsonObject();
                result.addProperty("recipe",   recipeName);
                result.addProperty("calories", safeString(cached, "calories"));
                result.addProperty("carbs",    safeString(cached, "carbs"));
                result.addProperty("fat",      safeString(cached, "fat"));
                result.addProperty("protein",  safeString(cached, "protein"));
                reply.setContent(result.toString());
                System.out.println("NutritionAgent: nutrición desde caché para '" + recipeName + "'");
                myAgent.send(reply);
                return;
            }

            // Si no hay caché, consultamos la API directamente
            System.out.println("NutritionAgent: caché vacío, consultando API para id=" + recipeId);
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
                result.addProperty("calories", safeString(nutritionData, "calories"));
                result.addProperty("carbs",    safeString(nutritionData, "carbs"));
                result.addProperty("fat",      safeString(nutritionData, "fat"));
                result.addProperty("protein",  safeString(nutritionData, "protein"));
                reply.setContent(result.toString());
                System.out.println("NutritionAgent: datos obtenidos de API para '" + recipeName + "'");
            } else {
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setContent("{\"error\": \"Spoonacular devolvió " + httpResponse.statusCode() + "\"}");
                System.err.println("NutritionAgent: Spoonacular error "
                        + httpResponse.statusCode() + " para id=" + recipeId);
            }

        } catch (Exception e) {
            System.err.println("NutritionAgent: error: " + e.getMessage());
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setContent("{\"error\": \"" + e.getMessage() + "\"}");
        }

        myAgent.send(reply);
    }

    private String safeString(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString()
                : "?";
    }
}
