package behaviours.recommendationbehaviours;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

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

            String ranking = calculateRanking(msg.getContent());

            System.out.println("Ranking final calculado:");
            System.out.println(ranking);

        } else {
            block();
        }
    }

    private String calculateRanking(String graphResult) {

        List<RecipeResult> results = new ArrayList<>();

        String[] lines = graphResult.split("\n");

        for (String line : lines) {

            if (line.startsWith("graphResults=") || line.trim().isEmpty()) {
                continue;
            }

            String recipeName = line.split(";")[0];

            double graphScore = extractGraphScore(line);

            double finalScore = calculateFinalScore(graphScore);

            results.add(new RecipeResult(recipeName, graphScore, finalScore));
        }

        results.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));

        StringBuilder sb = new StringBuilder();

        int position = 1;

        for (RecipeResult result : results) {
            sb.append(position)
                    .append(". ")
                    .append(result.recipeName)
                    .append(" | graphScore=")
                    .append(String.format(Locale.US, "%.2f", result.graphScore))
                    .append(" | finalScore=")
                    .append(String.format(Locale.US, "%.2f", result.finalScore))
                    .append("\n");

            position++;
        }

        return sb.toString();
    }

    private double extractGraphScore(String line) {

        String[] parts = line.split(";");

        for (String part : parts) {

            if (part.startsWith("graphScore=")) {

                String value = part.replace("graphScore=", "");

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

    private static class RecipeResult {

        String recipeName;
        double graphScore;
        double finalScore;

        RecipeResult(String recipeName, double graphScore, double finalScore) {
            this.recipeName = recipeName;
            this.graphScore = graphScore;
            this.finalScore = finalScore;
        }
    }
}