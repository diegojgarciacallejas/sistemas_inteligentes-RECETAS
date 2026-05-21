package behaviours.graphbehaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import model.GraphNode;

import java.util.*;

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

            String fullInput = msg.getContent();
            List<GraphNode> graphNodes = processGraph(fullInput);

            // Extraer líneas extra (instrucciones, TF-IDF scores) para pasarlas al RecommendationAgent
            String instructionsLine   = extractPassthroughLine(fullInput, "recipeInstructions=");
            String tfidfLine          = extractPassthroughLine(fullInput, "recipeTfIdfScores=");

            String result = buildOutputMessage(graphNodes, instructionsLine, tfidfLine);

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

    private List<GraphNode> processGraph(String input) {

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

        return calculateGraphNodes(userIngredients, recipeGraph);
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

    private List<GraphNode> calculateGraphNodes(
            List<String> userIngredients,
            Map<String, List<String>> recipeGraph
    ) {

        List<GraphNode> graphNodes = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : recipeGraph.entrySet()) {

            String recipeName = entry.getKey();
            List<String> recipeIngredients = entry.getValue();

            List<String> matchedIngredients = new ArrayList<>();
            List<String> missingIngredients = new ArrayList<>();

            for (String ingredient : recipeIngredients) {

                if (userIngredients.contains(ingredient)) {
                    matchedIngredients.add(ingredient);
                } else {
                    missingIngredients.add(ingredient);
                }
            }

            int matches = matchedIngredients.size();

            double graphScore = 0.0;

            if (!recipeIngredients.isEmpty()) {
                graphScore = (double) matches / recipeIngredients.size();
            }

            GraphNode node = new GraphNode(
                    recipeName,
                    recipeIngredients,
                    matchedIngredients,
                    missingIngredients,
                    matches,
                    recipeIngredients.size(),
                    graphScore
            );

            graphNodes.add(node);
        }

        return graphNodes;
    }

    private String buildOutputMessage(List<GraphNode> graphNodes,
                                      String instructionsLine,
                                      String tfidfLine) {
        StringBuilder result = new StringBuilder();
        result.append("graphResults=\n");
        for (GraphNode node : graphNodes) {
            result.append(node.toMessageFormat()).append("\n");
        }
        // Pasar las líneas de instrucciones y TF-IDF al RecommendationAgent
        if (!instructionsLine.isEmpty()) result.append(instructionsLine).append("\n");
        if (!tfidfLine.isEmpty())        result.append(tfidfLine).append("\n");
        return result.toString();
    }

    /** Extrae la primera línea del mensaje que empiece con el prefijo dado. */
    private String extractPassthroughLine(String input, String prefix) {
        for (String line : input.split("\n")) {
            if (line.startsWith(prefix)) return line;
        }
        return "";
    }

    private String normalize(String text) {
        return text.trim().toLowerCase();
    }
}