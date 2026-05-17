package behaviours.graphbehaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;
// formato que deb madndar  ontloghyagnet userIngredients=huevo,arroz,
//pollo recipes=arroz con pollo:arroz,pollo,tomate;tortilla:huevo,patata,cebolla
public class GraphBehaviour extends CyclicBehaviour {

    public GraphBehaviour(Agent agent) {
        super(agent);
    }

    @Override
    public void action() {

        MessageTemplate template =
                MessageTemplate.MatchConversationId("ONTOLOGY_RESULT");

        ACLMessage msg = myAgent.receive(template);

        if (msg != null) {

            System.out.println("GraphAgent recibe:");
            System.out.println(msg.getContent());

            String result = processGraph(msg.getContent());

            ACLMessage message = new ACLMessage(ACLMessage.INFORM);
            message.addReceiver(new AID("RecommendationAgent", AID.ISLOCALNAME));
            message.setConversationId("GRAPH_RESULT");
            message.setContent(result);

            myAgent.send(message);

            System.out.println("GraphAgent envía a RecommendationAgent:");
            System.out.println(result);

        } else {
            block();
        }
    }

    private String processGraph(String input) {

        /*
         Formato esperado de entrada:

         userIngredients=huevo,arroz,pollo
         recipes=arroz con pollo:arroz,pollo,tomate;tortilla:huevo,patata,cebolla
        */

        List<String> userIngredients = new ArrayList<>();
        Map<String, List<String>> recipeGraph = new LinkedHashMap<>();

        String[] lines = input.split("\n");

        for (String line : lines) {

            if (line.startsWith("userIngredients=")) {

                String ingredientsText = line.replace("userIngredients=", "");
                userIngredients = parseList(ingredientsText);

            } else if (line.startsWith("recipes=")) {

                String recipesText = line.replace("recipes=", "");
                recipeGraph = parseRecipes(recipesText);
            }
        }

        return calculateGraphScores(userIngredients, recipeGraph);
    }

    private List<String> parseList(String text) {

        List<String> result = new ArrayList<>();

        String[] parts = text.split(",");

        for (String part : parts) {
            result.add(normalize(part));
        }

        return result;
    }

    private Map<String, List<String>> parseRecipes(String text) {

        Map<String, List<String>> recipes = new LinkedHashMap<>();

        String[] recipeParts = text.split(";");

        for (String recipePart : recipeParts) {

            String[] data = recipePart.split(":");

            if (data.length == 2) {

                String recipeName = data[0].trim();
                List<String> ingredients = parseList(data[1]);

                recipes.put(recipeName, ingredients);
            }
        }

        return recipes;
    }

    private String calculateGraphScores(
            List<String> userIngredients,
            Map<String, List<String>> recipeGraph
    ) {

        StringBuilder result = new StringBuilder();

        result.append("graphResults=\n");

        for (Map.Entry<String, List<String>> entry : recipeGraph.entrySet()) {

            String recipeName = entry.getKey();
            List<String> recipeIngredients = entry.getValue();

            int matches = 0;

            for (String ingredient : recipeIngredients) {
                if (userIngredients.contains(ingredient)) {
                    matches++;
                }
            }

            double graphScore = 0.0;

            if (!recipeIngredients.isEmpty()) {
                graphScore = (double) matches / recipeIngredients.size();
            }

            result.append(recipeName)
                    .append(";ingredients=")
                    .append(recipeIngredients)
                    .append(";matches=")
                    .append(matches)
                    .append(";total=")
                    .append(recipeIngredients.size())
                    .append(";graphScore=")
                    .append(String.format("%.2f", graphScore))
                    .append("\n");
        }

        return result.toString();
    }

    private String normalize(String text) {
        return text.trim().toLowerCase();
    }
}