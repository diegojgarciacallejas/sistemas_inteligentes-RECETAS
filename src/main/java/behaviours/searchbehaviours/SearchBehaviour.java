// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package behaviours.searchbehaviours;

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
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

public class SearchBehaviour extends AchieveREResponder {
    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public SearchBehaviour(Agent var1, MessageTemplate var2, HttpClient var3, Gson var4, String var5) {
        super(var1, var2);
        this.httpClient = var3;
        this.gson = var4;
        this.apiKey = var5;
    }

    protected ACLMessage handleRequest(ACLMessage var1) {
        System.out.println("RecipeSearchAgent: Received request to search for: " + var1.getContent());
        ACLMessage var2 = var1.createReply();
        var2.setPerformative(1);
        return var2;
    }

    protected ACLMessage prepareResultNotification(ACLMessage var1, ACLMessage var2) {
        ACLMessage var3 = var1.createReply();
        String rawContent = var1.getContent().trim();
        String var4 = rawContent;
        for (String line : rawContent.split("\n")) {
            if (line.startsWith("ingredients=")) {
                var4 = line.substring("ingredients=".length()).trim();
                break;
            }
        }
        System.out.println("RecipeSearchAgent: Searching Spoonacular for: " + var4);

        try {
            String var5 = URLEncoder.encode(var4, StandardCharsets.UTF_8.toString());
            String var6 = "https://api.spoonacular.com/recipes/findByIngredients?ingredients=" + var5 + "&number=3&apiKey=" + this.apiKey;
            HttpRequest var7 = HttpRequest.newBuilder().uri(URI.create(var6)).GET().build();
            HttpResponse var8 = this.httpClient.send(var7, BodyHandlers.ofString());
            if (var8.statusCode() == 200) {
                JsonArray var9 = (JsonArray)this.gson.fromJson((String)var8.body(), JsonArray.class);
                JsonArray var10 = new JsonArray();

                for(JsonElement var12 : var9) {
                    JsonObject var13 = var12.getAsJsonObject();
                    JsonObject var14 = new JsonObject();
                    var14.addProperty("id", var13.get("id").getAsInt());
                    var14.addProperty("name", var13.get("title").getAsString());
                    var10.add(var14);
                }

                JsonObject var16 = new JsonObject();
                var16.addProperty("userIngredients", var4);
                var16.add("recipes", var10);
                var3.setPerformative(7);
                var3.setContent(var16.toString());
            } else {
                var3.setPerformative(6);
                var3.setContent("{\"error\": \"Failed to retrieve recipes. Status: " + var8.statusCode() + "\"}");
            }
        } catch (Exception var15) {
            var15.printStackTrace();
            var3.setPerformative(6);
            var3.setContent("{\"error\": \"Error communicating with Spoonacular API.\"}");
        }

        return var3;
    }
}
