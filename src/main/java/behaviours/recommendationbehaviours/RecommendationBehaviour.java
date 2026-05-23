package behaviours.recommendationbehaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import model.RecipeScore;

import java.util.*;
import java.util.LinkedHashMap;

public class RecommendationBehaviour extends CyclicBehaviour {

    public RecommendationBehaviour(Agent agent) {
        super(agent);
    }

    @Override
    public void action() {

        MessageTemplate template =
                MessageTemplate.MatchConversationId("GRAPH_RESULT");

        ACLMessage msg = myAgent.receive(template);

        if (msg != null) {

            System.out.println("RecommendationAgent recibió:");
            System.out.println(msg.getContent());

            List<RecipeScore> ranking =
                    calculateRanking(msg.getContent());

            String rankingMessage =
                    buildRankingMessage(ranking);

            System.out.println("Ranking final calculado:");
            System.out.println(rankingMessage);

            sendRankingToInterface(rankingMessage);

        } else {
            block();
        }
    }

    private List<RecipeScore> calculateRanking(String graphResult) {

        List<RecipeScore> results = new ArrayList<>();

        // Parsear instrucciones: recipeInstructions=RecipeName:pasos...
        Map<String, String> instructions = parseInstructions(graphResult);

        String[] lines = graphResult.split("\n");

        for (String line : lines) {

            if (line.startsWith("graphResults=")
                    || line.startsWith("recipeInstructions=")
                    || line.startsWith("recipeTfIdfScores=")
                    || line.trim().isEmpty()) {
                continue;
            }

            String recipeName = line.split(";")[0];

            double graphScore = extractGraphScore(line);

            double finalScore = calculateFinalScore(graphScore);

            RecipeScore recipeScore = new RecipeScore(
                    recipeName,
                    graphScore,
                    finalScore,
                    instructions.getOrDefault(recipeName, "")
            );

            results.add(recipeScore);
        }

        results.sort(
                Comparator.comparingDouble(
                        RecipeScore::getFinalScore
                ).reversed()
        );

        return results;
    }

    /**
     * Parsea la línea recipeInstructions=RecipeA:pasos;RecipeB:pasos
     * y devuelve un mapa recipeName → instrucciones.
     */
    private Map<String, String> parseInstructions(String input) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith("recipeInstructions=")) continue;
            String value = line.substring("recipeInstructions=".length());
            // Cada receta separada por ';', pero las instrucciones pueden contener ':'
            // Formato: RecipeName:instrucciones;OtraReceta:instrucciones
            for (String entry : value.split(";")) {
                int colon = entry.indexOf(":");
                if (colon > 0) {
                    String name  = entry.substring(0, colon).trim();
                    String steps = entry.substring(colon + 1).trim();
                    map.put(name, steps);
                }
            }
        }
        return map;
    }

    private double extractGraphScore(String line) {

        String[] parts = line.split(";");

        for (String part : parts) {

            if (part.startsWith("graphScore=")) {

                String value =
                        part.replace("graphScore=", "");

                value = value.replace(",", ".");

                return Double.parseDouble(value);
            }
        }

        return 0.0;
    }

    private double calculateFinalScore(double graphScore) {

        double ingredientScore = graphScore;
        double quantityScore = 0.80;
        double preferenceScore = 0.70;
        double timeScore = 0.90;
        double nutritionScore = 0.60;

        return 0.45 * ingredientScore
                + 0.20 * quantityScore
                + 0.15 * preferenceScore
                + 0.10 * timeScore
                + 0.10 * nutritionScore;
    }

    private String buildRankingMessage(List<RecipeScore> ranking) {

        StringBuilder sb = new StringBuilder();

        sb.append("recommendationResults=\n");

        int position = 1;

        for (RecipeScore recipe : ranking) {

            // Formato que InterfaceAgent parsea: RANK|name|graphScore|finalScore
            sb.append(position)
                    .append("|")
                    .append(recipe.getRecipeName())
                    .append("|")
                    .append(String.format(Locale.US, "%.2f", recipe.getGraphScore()))
                    .append("|")
                    .append(String.format(Locale.US, "%.2f", recipe.getFinalScore()))
                    .append("\n");

            // Añadir instrucciones si existen
            String steps = recipe.getInstructions();
            if (steps != null && !steps.isBlank()) {
                sb.append("   instrucciones=").append(steps).append("\n");
            }

            position++;
        }

        return sb.toString();
    }

    private String buildExplanation(RecipeScore recipe) {

        if (recipe.getGraphScore() >= 0.65) {
            return "Muy recomendada porque coincide con la mayoría de ingredientes disponibles.";
        }

        if (recipe.getGraphScore() >= 0.30) {
            return "Recomendación aceptable porque comparte algunos ingredientes con el usuario.";
        }

        return "Recomendación débil porque apenas coincide con los ingredientes disponibles.";
    }

    private void sendRankingToInterface(String rankingMessage) {

        ACLMessage response = new ACLMessage(ACLMessage.INFORM);

        response.addReceiver(
                new AID("InterfaceAgent", AID.ISLOCALNAME)
        );

        response.setConversationId("RECOMMENDATION_RESULT");

        response.setContent(rankingMessage);

        myAgent.send(response);

        System.out.println("RecommendationAgent envió ranking a InterfaceAgent");
    }
}