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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchBehaviour extends AchieveREResponder {

    /**
     * Traducciones español → inglés para los ingredientes más comunes.
     * Spoonacular es una API en inglés: sin traducción el graphScore siempre es 0.0
     * porque compara "arroz" (ES) contra "rice" (EN).
     */
    private static final Map<String, String> ES_TO_EN = Map.ofEntries(
            Map.entry("arroz",          "rice"),
            Map.entry("pollo",          "chicken"),
            Map.entry("huevo",          "egg"),
            Map.entry("huevos",         "eggs"),
            Map.entry("tomate",         "tomato"),
            Map.entry("tomates",        "tomatoes"),
            Map.entry("cebolla",        "onion"),
            Map.entry("ajo",            "garlic"),
            Map.entry("aceite",         "oil"),
            Map.entry("aceite de oliva","olive oil"),
            Map.entry("sal",            "salt"),
            Map.entry("pimienta",       "pepper"),
            Map.entry("leche",          "milk"),
            Map.entry("queso",          "cheese"),
            Map.entry("mantequilla",    "butter"),
            Map.entry("harina",         "flour"),
            Map.entry("azucar",         "sugar"),
            Map.entry("azúcar",         "sugar"),
            Map.entry("agua",           "water"),
            Map.entry("zanahoria",      "carrot"),
            Map.entry("papa",           "potato"),
            Map.entry("patata",         "potato"),
            Map.entry("patatas",        "potatoes"),
            Map.entry("lechuga",        "lettuce"),
            Map.entry("pepino",         "cucumber"),
            Map.entry("pimiento",       "bell pepper"),
            Map.entry("limon",          "lemon"),
            Map.entry("limón",          "lemon"),
            Map.entry("naranja",        "orange"),
            Map.entry("manzana",        "apple"),
            Map.entry("carne",          "meat"),
            Map.entry("carne de res",   "beef"),
            Map.entry("cerdo",          "pork"),
            Map.entry("pescado",        "fish"),
            Map.entry("atun",           "tuna"),
            Map.entry("atún",           "tuna"),
            Map.entry("salmon",         "salmon"),
            Map.entry("salmón",         "salmon"),
            Map.entry("camaron",        "shrimp"),
            Map.entry("camarón",        "shrimp"),
            Map.entry("gambas",         "shrimp"),
            Map.entry("jamon",          "ham"),
            Map.entry("jamón",          "ham"),
            Map.entry("tocino",         "bacon"),
            Map.entry("yogur",          "yogurt"),
            Map.entry("crema",          "cream"),
            Map.entry("pasta",          "pasta"),
            Map.entry("pan",            "bread"),
            Map.entry("lentejas",       "lentils"),
            Map.entry("frijoles",       "beans"),
            Map.entry("judias",         "beans"),
            Map.entry("garbanzo",       "chickpea"),
            Map.entry("garbanzos",      "chickpeas"),
            Map.entry("maiz",           "corn"),
            Map.entry("maíz",           "corn"),
            Map.entry("brocoli",        "broccoli"),
            Map.entry("brócoli",        "broccoli"),
            Map.entry("espinaca",       "spinach"),
            Map.entry("espinacas",      "spinach"),
            Map.entry("champinon",      "mushroom"),
            Map.entry("champiñon",      "mushroom"),
            Map.entry("champiñones",    "mushrooms"),
            Map.entry("aguacate",       "avocado"),
            Map.entry("platano",        "banana"),
            Map.entry("plátano",        "banana")
    );

    /**
     * Traduce una lista de ingredientes en español al inglés.
     * Los ingredientes que no están en el mapa se dejan tal cual
     * (pueden ya estar en inglés).
     */
    static String translateIngredients(String spanishList) {
        return Arrays.stream(spanishList.split(","))
                .map(ing -> {
                    String normalized = ing.trim().toLowerCase();
                    return ES_TO_EN.getOrDefault(normalized, normalized);
                })
                .collect(Collectors.joining(","));
    }

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public SearchBehaviour(
            Agent a,
            MessageTemplate mt,
            HttpClient httpClient,
            Gson gson,
            String apiKey
    ) {
        super(a, mt);
        this.httpClient = httpClient;
        this.gson = gson;
        this.apiKey = apiKey;
    }

    @Override
    protected ACLMessage handleRequest(ACLMessage request) {

        System.out.println(
                "RecipeSearchAgent: Received request to search for: "
                        + request.getContent()
        );

        ACLMessage agree = request.createReply();
        agree.setPerformative(ACLMessage.AGREE);

        return agree;
    }

    @Override
    protected ACLMessage prepareResultNotification(
            ACLMessage request,
            ACLMessage response
    ) {

        String rawContent = request.getContent().trim();
        String rawIngredients = rawContent; // fallback si no hay formato clave=valor
        for (String line : rawContent.split("\n")) {
            if (line.startsWith("ingredients=")) {
                rawIngredients = line.replace("ingredients=", "").trim();
                break;
            }
        }

        // Traducir al inglés para que Spoonacular y el matching del grafo sean consistentes
        String ingredients = translateIngredients(rawIngredients);

        System.out.println(
                "RecipeSearchAgent: ingredientes originales: " + rawIngredients
        );
        System.out.println(
                "RecipeSearchAgent: Searching Spoonacular for (EN): " + ingredients
        );

        String resultContent;

        try {

            String encodedIngredients =
                    URLEncoder.encode(
                            ingredients,
                            StandardCharsets.UTF_8.toString()
                    );

            String url =
                    "https://api.spoonacular.com/recipes/findByIngredients?ingredients="
                            + encodedIngredients
                            + "&number=3&apiKey="
                            + apiKey;

            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .build();

            HttpResponse<String> httpResponse =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (httpResponse.statusCode() == 200) {

                JsonArray meals =
                        gson.fromJson(
                                httpResponse.body(),
                                JsonArray.class
                        );

                JsonArray resultRecipes = new JsonArray();

                for (JsonElement element : meals) {

                    JsonObject meal = element.getAsJsonObject();

                    JsonObject recipeInfo = new JsonObject();

                    recipeInfo.addProperty(
                            "id",
                            meal.get("id").getAsInt()
                    );

                    recipeInfo.addProperty(
                            "name",
                            meal.get("title").getAsString()
                    );

                    resultRecipes.add(recipeInfo);
                }

                JsonObject resultData = new JsonObject();

                resultData.addProperty(
                        "userIngredients",
                        ingredients
                );

                resultData.add(
                        "recipes",
                        resultRecipes
                );

                resultContent = resultData.toString();

                sendToTextMining(resultContent);

                ACLMessage replyToInterface = request.createReply();
                replyToInterface.setPerformative(ACLMessage.INFORM);
                replyToInterface.setContent(
                        "RecipeSearchAgent ha enviado resultados a TextMiningAgent"
                );

                return replyToInterface;

            } else {

                resultContent =
                        "{\"error\": \"Failed to retrieve recipes. Status: "
                                + httpResponse.statusCode()
                                + "\"}";

                ACLMessage failure = request.createReply();
                failure.setPerformative(ACLMessage.FAILURE);
                failure.setContent(resultContent);

                return failure;
            }

        } catch (Exception e) {

            e.printStackTrace();

            resultContent =
                    "{\"error\": \"Error communicating with Spoonacular API.\"}";

            ACLMessage failure = request.createReply();
            failure.setPerformative(ACLMessage.FAILURE);
            failure.setContent(resultContent);

            return failure;
        }
    }

    private void sendToTextMining(String content) {

        ACLMessage message = new ACLMessage(ACLMessage.INFORM);

        message.addReceiver(
                new AID("TextMiningAgent", AID.ISLOCALNAME)
        );

        message.setConversationId("RECIPE_SEARCH_RESULT");

        message.setContent(content);

        myAgent.send(message);

        System.out.println("RecipeSearchAgent -> envia a TextMiningAgent:");
        System.out.println(content);
    }
}