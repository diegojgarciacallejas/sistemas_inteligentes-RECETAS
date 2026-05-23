package behaviours.recommendationbehaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.RecipeScore;
import utils.RestrictionChecker;

import java.util.*;

/**
 * RecommendationBehaviour — agente de puntuación y ranking final.
 *
 * Escucha GRAPH_RESULT y calcula el score final combinando:
 *   coverageScore    (0.50) — % ingredientes de la receta cubiertos por el usuario
 *   utilizationScore (0.20) — % ingredientes del usuario que usa la receta
 *   tfidfScore       (0.30) — similitud TF-IDF ingrediente↔ingrediente (normalizada)
 *
 * Aplica además:
 *   - Filtro de restricciones dietéticas (vegetariano, sin gluten…) → score = 0
 *   - Penalización por tiempo: si la receta supera el maxTime del usuario
 *
 * Salida a InterfaceAgent (RECOMMENDATION_RESULT):
 *   rank|recipeName|graphScore|finalScore    ← parseada por InterfaceAgent.displayResults()
 *   + líneas de detalle (texto libre)
 */
public class RecommendationBehaviour extends CyclicBehaviour {

    // Factor de escala para TF-IDF coseno (valores típicamente pequeños → escalar a [0,1])
    private static final double TFIDF_SCALE   = 5.0;
    // Penalización máxima por tiempo (30 % del score final)
    private static final double MAX_TIME_PEN  = 0.30;

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

            // reply-to: ExternalAgent (testing)
            Iterator<AID> replyToIt = msg.getAllReplyTo();
            if (replyToIt.hasNext()) {
                AID replyTo = replyToIt.next();
                ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                reply.addReceiver(replyTo);
                reply.setConversationId("RECOMMENDATION_RESULT");
                reply.setContent(output);
                myAgent.send(reply);
                System.out.println("RecommendationAgent -> reply-to: " + replyTo.getLocalName());
            }
        } else {
            block();
        }
    }

    // ── Cálculo del ranking ───────────────────────────────────────────────────

    private List<RecipeScore> calculateRanking(String input) {
        Map<String, Double>  tfidfScores       = parseTfidfScores(input);
        Map<String, String>  instructions      = parseInstructions(input);
        Map<String, Integer> recipeTimes       = parseRecipeTimes(input);
        Map<String, String>  recipeIngredients = parseRecipeIngredientDetails(input);
        Map<String, String>  recipeTags        = parseSimpleStringMap(input, "recipeTags=");
        Map<String, Integer> healthScores      = parseSimpleIntMap(input, "recipeHealthScores=");

        int    maxTime     = parseMaxTime(input);
        String restrictions = parseRestrictions(input);

        List<RecipeScore> results = new ArrayList<>();

        for (String line : input.split("\n")) {
            if (line.startsWith("graphResults=")
                    || line.startsWith("recipeInstructions=")
                    || line.startsWith("recipeTfIdfScores=")
                    || line.startsWith("recipeTimes=")
                    || line.startsWith("userPrefs=")
                    || line.startsWith("recipeIngredients=")
                    || line.startsWith("recipeTags=")
                    || line.startsWith("recipeHealthScores=")
                    || line.startsWith("recipeServings=")
                    || line.startsWith("recipeCuisines=")
                    || line.startsWith("recipeDishTypes=")
                    || line.trim().isEmpty()) {
                continue;
            }

            // Formato: RecipeName;graphScore=X;coverageScore=Y;utilizationScore=Z;
            //          matches=N;total=M;matched=ing1,ing2;missing=ing3,ing4
            String[] parts = line.split(";");
            if (parts.length < 3) continue;

            String recipeName       = parts[0].trim();
            double graphScore       = parseField(parts, "graphScore");
            double coverageScore    = parseField(parts, "coverageScore");
            double utilizationScore = parseField(parts, "utilizationScore");

            // ── TF-IDF ──────────────────────────────────────────────────────
            double rawTfidf  = tfidfScores.getOrDefault(recipeName, 0.0);
            double tfidfNorm = Math.min(1.0, rawTfidf * TFIDF_SCALE);

            // ── Score base ───────────────────────────────────────────────────
            double finalScore = 0.50 * coverageScore
                              + 0.20 * utilizationScore
                              + 0.30 * tfidfNorm;

            // ── Penalización por tiempo ──────────────────────────────────────
            if (maxTime > 0) {
                int recipeTime = recipeTimes.getOrDefault(recipeName, -1);
                if (recipeTime > 0 && recipeTime > maxTime) {
                    double overRatio = (double)(recipeTime - maxTime) / maxTime;
                    double penalty   = Math.min(MAX_TIME_PEN, overRatio * 0.15);
                    finalScore -= penalty;
                    System.out.printf("RecommendationAgent: penalización tiempo -%s%% en '%s' "
                            + "(%d min > límite %d min)%n",
                            String.format(Locale.US, "%.0f", penalty * 100),
                            recipeName, recipeTime, maxTime);
                }
            }

            finalScore = Math.max(0.0, finalScore);

            // ── Filtro de restricciones ──────────────────────────────────────
            if (!restrictions.isEmpty()) {
                List<String> allIngredients = parseRecipeIngredients(parts);
                if (!RestrictionChecker.isCompatible(restrictions, allIngredients)) {
                    finalScore = 0.0;   // cae al fondo del ranking
                    System.out.println("RecommendationAgent: '"
                            + recipeName + "' incompatible con '" + restrictions + "'");
                }
            }

            results.add(new RecipeScore(
                    recipeName,
                    graphScore,
                    coverageScore,
                    utilizationScore,
                    rawTfidf,
                    finalScore,
                    instructions.getOrDefault(recipeName, ""),
                    recipeIngredients.getOrDefault(recipeName, ""),
                    recipeTags.getOrDefault(recipeName, ""),
                    healthScores.getOrDefault(recipeName, -1)
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
            // Línea principal: formato pipe que InterfaceAgent.displayResults() parsea
            // Patrón: \d+\|.*  →  parts[0]=rank, [1]=nombre, [2]=graphScore, [3]=finalScore
            sb.append(pos)
              .append("|").append(r.getRecipeName())
              .append("|").append(String.format(Locale.US, "%.4f", r.getGraphScore()))
              .append("|").append(String.format(Locale.US, "%.4f", r.getFinalScore()))
              .append("\n");

            // Líneas de detalle (texto libre visible bajo el bloque de scores en la GUI)
            sb.append("  Cobertura: ")
              .append(String.format(Locale.US, "%.0f%%", r.getCoverageScore() * 100))
              .append("  |  Aprovechamiento: ")
              .append(String.format(Locale.US, "%.0f%%", r.getUtilizationScore() * 100))
              .append("  |  Similitud TF-IDF: ")
              .append(String.format(Locale.US, "%.4f", r.getTfidfScore()))
              .append("\n");

            // Etiquetas dietéticas y puntuación de salud
            String tags = r.getTags();
            int health  = r.getHealthScore();
            if ((tags != null && !tags.isBlank()) || health >= 0) {
                sb.append("  ");
                if (tags != null && !tags.isBlank()) {
                    for (String tag : tags.split(",")) {
                        switch (tag.trim()) {
                            case "vegan":       sb.append("🌱 Vegano  "); break;
                            case "vegetarian":  sb.append("🥦 Vegetariano  "); break;
                            case "glutenFree":  sb.append("🌾 Sin gluten  "); break;
                            case "dairyFree":   sb.append("🥛 Sin lácteos  "); break;
                        }
                    }
                }
                if (health >= 0) sb.append("❤ Salud: ").append(health).append("/100");
                sb.append("\n");
            }

            sb.append("  ").append(buildExplanation(r)).append("\n");

            // Ingredientes con cantidades
            String ingDetails = r.getIngredientDetails();
            if (ingDetails != null && !ingDetails.isBlank()) {
                sb.append("  Ingredientes:\n");
                for (String entry : ingDetails.split(",")) {
                    String[] parts = entry.split("\\|");
                    if (parts.length >= 3) {
                        String ingName = parts[0].trim();
                        String amount  = parts[1].trim();
                        String unit    = parts[2].trim();
                        String display = unit.isEmpty() ? amount : amount + " " + unit;
                        sb.append("    • ").append(ingName).append(": ").append(display).append("\n");
                    } else if (parts.length == 1) {
                        sb.append("    • ").append(parts[0].trim()).append("\n");
                    }
                }
            }

            String steps = r.getInstructions();
            if (steps != null && !steps.isBlank()) {
                String preview = steps.length() > 300
                        ? steps.substring(0, 300).trim() + "..."
                        : steps;
                sb.append("  Preparación: ").append(preview).append("\n");
            }

            sb.append("\n");
            pos++;
        }

        return sb.toString();
    }

    private String buildExplanation(RecipeScore r) {
        if (r.getFinalScore() == 0.0)
            return "⚠ No recomendada: la receta es incompatible con tus restricciones o supera mucho el tiempo.";
        if (r.getCoverageScore() >= 0.65)
            return "✓ Muy recomendada: cubres más del 65% de los ingredientes necesarios.";
        if (r.getCoverageScore() >= 0.30)
            return "~ Recomendación aceptable: compartes algunos ingredientes clave.";
        return "✗ Recomendación débil: faltan bastantes ingredientes de esta receta.";
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

    // ── Parsers ───────────────────────────────────────────────────────────────

    private Map<String, Double> parseTfidfScores(String input) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith("recipeTfIdfScores=")) continue;
            for (String entry : line.substring("recipeTfIdfScores=".length()).split(";")) {
                String[] kv = entry.split(":");
                if (kv.length == 2) {
                    try { scores.put(kv[0].trim(),
                            Double.parseDouble(kv[1].trim().replace(",", "."))); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return scores;
    }

    private Map<String, String> parseInstructions(String input) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith("recipeInstructions=")) continue;
            for (String entry : line.substring("recipeInstructions=".length()).split(";")) {
                int colon = entry.indexOf(":");
                if (colon > 0) {
                    map.put(entry.substring(0, colon).trim(),
                            entry.substring(colon + 1).trim());
                }
            }
        }
        return map;
    }

    /**
     * Parsea la línea recipeIngredients=RecipeName:ing1|amount|unit,ing2|amount|unit;...
     * Devuelve mapa nombreReceta → "ing1|amount|unit,ing2|amount|unit"
     */
    private Map<String, String> parseRecipeIngredientDetails(String input) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith("recipeIngredients=")) continue;
            for (String entry : line.substring("recipeIngredients=".length()).split(";")) {
                int colon = entry.indexOf(":");
                if (colon > 0) {
                    map.put(entry.substring(0, colon).trim(),
                            entry.substring(colon + 1).trim());
                }
            }
        }
        return map;
    }

    private Map<String, Integer> parseRecipeTimes(String input) {
        Map<String, Integer> times = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith("recipeTimes=")) continue;
            for (String entry : line.substring("recipeTimes=".length()).split(";")) {
                String[] kv = entry.split(":");
                if (kv.length == 2) {
                    try { times.put(kv[0].trim(), Integer.parseInt(kv[1].trim())); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return times;
    }

    private int parseMaxTime(String input) {
        for (String line : input.split("\n")) {
            if (!line.startsWith("userPrefs=")) continue;
            for (String part : line.substring("userPrefs=".length()).split(";")) {
                if (part.startsWith("maxTime:")) {
                    try { return Integer.parseInt(part.substring("maxTime:".length()).trim()); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return -1;
    }

    private String parseRestrictions(String input) {
        for (String line : input.split("\n")) {
            if (!line.startsWith("userPrefs=")) continue;
            for (String part : line.substring("userPrefs=".length()).split(";")) {
                if (part.startsWith("restrictions:")) {
                    return part.substring("restrictions:".length()).trim();
                }
            }
        }
        return "";
    }

    /** Combina matched= y missing= de la línea GraphNode para el chequeo de restricciones. */
    private List<String> parseRecipeIngredients(String[] parts) {
        List<String> ings = new ArrayList<>();
        for (String part : parts) {
            if (part.startsWith("matched=") || part.startsWith("missing=")) {
                String val = part.substring(part.indexOf('=') + 1).trim();
                if (!val.isEmpty()) {
                    for (String i : val.split(",")) {
                        String s = i.trim();
                        if (!s.isEmpty()) ings.add(s);
                    }
                }
            }
        }
        return ings;
    }

    /** Parser genérico: clave=RecipeName:valor;RecipeName2:valor2 → Map<nombre, String> */
    private Map<String, String> parseSimpleStringMap(String input, String prefix) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith(prefix)) continue;
            for (String entry : line.substring(prefix.length()).split(";")) {
                int colon = entry.indexOf(":");
                if (colon > 0)
                    map.put(entry.substring(0, colon).trim(), entry.substring(colon + 1).trim());
            }
        }
        return map;
    }

    /** Parser genérico: clave=RecipeName:intVal;... → Map<nombre, Integer> */
    private Map<String, Integer> parseSimpleIntMap(String input, String prefix) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith(prefix)) continue;
            for (String entry : line.substring(prefix.length()).split(";")) {
                int colon = entry.indexOf(":");
                if (colon > 0) {
                    try { map.put(entry.substring(0, colon).trim(),
                            Integer.parseInt(entry.substring(colon + 1).trim())); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return map;
    }

    private double parseField(String[] parts, String fieldName) {
        String prefix = fieldName + "=";
        for (String part : parts) {
            if (part.startsWith(prefix)) {
                try { return Double.parseDouble(
                        part.substring(prefix.length()).replace(",", ".")); }
                catch (NumberFormatException ignored) {}
            }
        }
        return 0.0;
    }
}
