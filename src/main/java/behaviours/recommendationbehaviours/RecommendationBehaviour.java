package behaviours.recommendationbehaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.RecipeScore;

import java.util.*;

/**
 * RecommendationBehaviour — agente de puntuación y ranking final.
 *
 * Escucha GRAPH_RESULT y calcula el score final combinando:
 *   coverageScore    (0.50) — % ingredientes de la receta cubiertos por el usuario
 *   utilizationScore (0.20) — % ingredientes del usuario que usa la receta
 *   tfidfScore       (0.30) — similitud textual TF-IDF (normalizada al rango [0,1])
 *
 * Salida a InterfaceAgent (RECOMMENDATION_RESULT):
 *   Una línea por receta en formato pipe compatible con InterfaceAgent.displayResults():
 *     rank|recipeName|graphScore|finalScore
 *   Seguida de líneas de detalle (mostradas como texto libre en la GUI).
 */
public class RecommendationBehaviour extends CyclicBehaviour {

    // Factor de escala para normalizar scores TF-IDF coseno (típicamente bajos)
    private static final double TFIDF_SCALE = 5.0;

    public RecommendationBehaviour(Agent agent) {
        super(agent);
    }

    @Override
    public void action() {
        MessageTemplate template = MessageTemplate.MatchConversationId("GRAPH_RESULT");
        ACLMessage msg = myAgent.receive(template);

        if (msg != null) {
            System.out.println("RecommendationAgent recibió:");
            System.out.println(msg.getContent());

            List<RecipeScore> ranking = calculateRanking(msg.getContent());
            String output = buildRankingMessage(ranking);

            System.out.println("Ranking final:");
            System.out.println(output);

            sendToInterface(output);
        } else {
            block();
        }
    }

    // ── Cálculo del ranking ───────────────────────────────────────────────────

    private List<RecipeScore> calculateRanking(String input) {
        Map<String, Double> tfidfScores  = parseTfidfScores(input);
        Map<String, String> instructions = parseInstructions(input);

        List<RecipeScore> results = new ArrayList<>();

        for (String line : input.split("\n")) {
            if (line.startsWith("graphResults=")
                    || line.startsWith("recipeInstructions=")
                    || line.startsWith("recipeTfIdfScores=")
                    || line.trim().isEmpty()) {
                continue;
            }

            // Formato: RecipeName;graphScore=X;coverageScore=Y;utilizationScore=Z;matches=N;total=M;...
            String[] parts = line.split(";");
            if (parts.length < 3) continue;

            String recipeName       = parts[0].trim();
            double graphScore       = parseField(parts, "graphScore");
            double coverageScore    = parseField(parts, "coverageScore");
            double utilizationScore = parseField(parts, "utilizationScore");

            double rawTfidf   = tfidfScores.getOrDefault(recipeName, 0.0);
            double tfidfNorm  = Math.min(1.0, rawTfidf * TFIDF_SCALE);

            double finalScore = 0.50 * coverageScore
                              + 0.20 * utilizationScore
                              + 0.30 * tfidfNorm;

            results.add(new RecipeScore(
                    recipeName,
                    graphScore,
                    coverageScore,
                    utilizationScore,
                    rawTfidf,
                    finalScore,
                    instructions.getOrDefault(recipeName, "")
            ));
        }

        results.sort(Comparator.comparingDouble(RecipeScore::getFinalScore).reversed());
        return results;
    }

    // ── Construcción del mensaje de salida ────────────────────────────────────

    private String buildRankingMessage(List<RecipeScore> ranking) {
        StringBuilder sb = new StringBuilder();

        int pos = 1;
        for (RecipeScore r : ranking) {
            // Línea principal: formato pipe que InterfaceAgent.displayResults() sabe parsear
            // Patrón esperado: \\d+\\|.*  con parts[0]=rank, [1]=nombre, [2]=graphScore, [3]=finalScore
            sb.append(pos)
              .append("|").append(r.getRecipeName())
              .append("|").append(String.format(Locale.US, "%.4f", r.getGraphScore()))
              .append("|").append(String.format(Locale.US, "%.4f", r.getFinalScore()))
              .append("\n");

            // Líneas de detalle (texto libre, visible en la GUI bajo el bloque de scores)
            sb.append("  Cobertura: ")
              .append(String.format(Locale.US, "%.0f%%", r.getCoverageScore() * 100))
              .append("  |  Aprovechamiento: ")
              .append(String.format(Locale.US, "%.0f%%", r.getUtilizationScore() * 100))
              .append("  |  TF-IDF: ")
              .append(String.format(Locale.US, "%.4f", r.getTfidfScore()))
              .append("\n");

            sb.append("  ").append(buildExplanation(r)).append("\n");

            String steps = r.getInstructions();
            if (steps != null && !steps.isBlank()) {
                String preview = steps.length() > 200
                        ? steps.substring(0, 200).trim() + "..."
                        : steps;
                sb.append("  Pasos: ").append(preview).append("\n");
            }

            sb.append("\n");
            pos++;
        }

        return sb.toString();
    }

    private String buildExplanation(RecipeScore r) {
        if (r.getCoverageScore() >= 0.65)
            return "Muy recomendada: cubres más del 65% de los ingredientes necesarios.";
        if (r.getCoverageScore() >= 0.30)
            return "Recomendación aceptable: compartes algunos ingredientes clave.";
        return "Recomendación débil: faltan bastantes ingredientes de esta receta.";
    }

    // ── Envío a InterfaceAgent ────────────────────────────────────────────────

    private void sendToInterface(String rankingMessage) {
        ACLMessage response = new ACLMessage(ACLMessage.INFORM);
        response.addReceiver(new AID("InterfaceAgent", AID.ISLOCALNAME));
        response.setConversationId("RECOMMENDATION_RESULT");
        response.setContent(rankingMessage);
        myAgent.send(response);
        System.out.println("RecommendationAgent: ranking enviado a InterfaceAgent.");
    }

    // ── Parsers de líneas passthrough ─────────────────────────────────────────

    private Map<String, Double> parseTfidfScores(String input) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith("recipeTfIdfScores=")) continue;
            String value = line.substring("recipeTfIdfScores=".length());
            for (String entry : value.split(";")) {
                String[] kv = entry.split(":");
                if (kv.length == 2) {
                    try {
                        scores.put(kv[0].trim(),
                                Double.parseDouble(kv[1].trim().replace(",", ".")));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return scores;
    }

    private Map<String, String> parseInstructions(String input) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith("recipeInstructions=")) continue;
            String value = line.substring("recipeInstructions=".length());
            // Instrucciones tienen ';' reemplazado por ',' (TextMiningBehaviour), así que
            // split por ';' separa recetas; luego indexOf(':') separa nombre de pasos.
            for (String entry : value.split(";")) {
                int colon = entry.indexOf(":");
                if (colon > 0) {
                    map.put(entry.substring(0, colon).trim(),
                            entry.substring(colon + 1).trim());
                }
            }
        }
        return map;
    }

    private double parseField(String[] parts, String fieldName) {
        String prefix = fieldName + "=";
        for (String part : parts) {
            if (part.startsWith(prefix)) {
                try {
                    return Double.parseDouble(
                            part.substring(prefix.length()).replace(",", "."));
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0.0;
    }
}
