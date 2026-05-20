package behaviours.recommendationbehaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import model.RecipeScore;

import java.util.*;
import java.util.stream.Collectors;

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

        // Primera pasada: parsear metadatos de tiempo y etiquetas
        Map<String, Integer>     recipeTimes = new HashMap<>();
        Map<String, Set<String>> recipeTags  = new HashMap<>();

        for (String line : graphResult.split("\n")) {

            if (line.startsWith("recipeTimes=")) {
                for (String entry : line.replace("recipeTimes=", "").split(";")) {
                    String[] kv = entry.split(":");
                    if (kv.length == 2) {
                        try { recipeTimes.put(kv[0].trim(), Integer.parseInt(kv[1].trim())); }
                        catch (NumberFormatException ignored) {}
                    }
                }

            } else if (line.startsWith("recipeTags=")) {
                for (String entry : line.replace("recipeTags=", "").split(";")) {
                    String[] kv = entry.split(":", 2);
                    if (kv.length == 2) {
                        Set<String> tags = Arrays.stream(kv[1].split(","))
                                .map(String::trim)
                                .filter(t -> !t.isEmpty())
                                .collect(Collectors.toSet());
                        recipeTags.put(kv[0].trim(), tags);
                    }
                }
            }
        }

        // Segunda pasada: procesar líneas de recetas
        List<RecipeScore> results = new ArrayList<>();

        for (String line : graphResult.split("\n")) {

            if (line.startsWith("graphResults=")
                    || line.startsWith("recipeTimes=")
                    || line.startsWith("recipeServings=")
                    || line.startsWith("recipeTags=")
                    || line.trim().isEmpty()) {
                continue;
            }

            String recipeName = line.split(";")[0];
            double graphScore = extractGraphScore(line);
            int    timeMinutes = recipeTimes.getOrDefault(recipeName, -1);
            Set<String> tags  = recipeTags.getOrDefault(recipeName, Collections.emptySet());

            double finalScore = calculateFinalScore(graphScore, timeMinutes, tags);

            results.add(new RecipeScore(recipeName, graphScore, finalScore));
        }

        results.sort(Comparator.comparingDouble(RecipeScore::getFinalScore).reversed());

        return results;
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

    /**
     * Score final ponderado:
     *   45% graphScore       — coincidencia de ingredientes (dato real del grafo)
     *   20% quantity         — sin datos propagados aún → constante 0.80
     *   15% preference       — sin datos propagados aún → constante 0.70
     *   10% timeScore        — basado en readyInMinutes real de Spoonacular
     *   10% nutritionScore   — basado en etiquetas dietéticas reales (vegan, glutenFree…)
     */
    private double calculateFinalScore(double graphScore, int timeMinutes, Set<String> tags) {

        // 1.0 si ≤ 20 min, decrece linealmente hasta 0.0 a los 120 min
        double timeScore = (timeMinutes > 0)
                ? Math.max(0.0, 1.0 - (timeMinutes - 20.0) / 100.0)
                : 0.70;

        // Cada etiqueta dietética suma 0.25 (vegan, vegetarian, glutenFree, dairyFree)
        double nutritionScore = Math.min(1.0, 0.25 + tags.size() * 0.25);

        return 0.45 * graphScore
                + 0.20 * 0.80
                + 0.15 * 0.70
                + 0.10 * timeScore
                + 0.10 * nutritionScore;
    }

    /**
     * Formato: RANK|nombre|graphScore|finalScore|explicacion
     * InterfaceAgent parsea las columnas 0-3 separadas por '|'.
     */
    private String buildRankingMessage(List<RecipeScore> ranking) {

        StringBuilder sb = new StringBuilder();

        sb.append("recommendationResults=\n");

        int position = 1;

        for (RecipeScore recipe : ranking) {

            sb.append(position)
                    .append("|")
                    .append(recipe.getRecipeName())
                    .append("|")
                    .append(String.format(Locale.US, "%.2f", recipe.getGraphScore()))
                    .append("|")
                    .append(String.format(Locale.US, "%.2f", recipe.getFinalScore()))
                    .append("|")
                    .append(buildExplanation(recipe))
                    .append("\n");

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