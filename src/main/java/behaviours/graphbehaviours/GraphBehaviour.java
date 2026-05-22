package behaviours.graphbehaviours;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.GraphNode;
import utils.IngredientStemmer;

import java.util.*;

/**
 * GraphBehaviour — análisis de grafo bipartito ingrediente-receta.
 *
 * Escucha ONTOLOGY_RESULT y construye un grafo bipartito donde:
 *   - Nodos izquierda : ingredientes del usuario
 *   - Nodos derecha   : ingredientes de cada receta
 *   - Aristas         : coincidencias (exactas o genérico↔específico vía IngredientStemmer)
 *
 * Métricas calculadas por receta:
 *   coverageScore    = matches / totalIngredientesReceta
 *                      (¿qué % de la receta puedo cubrir?)
 *   utilizationScore = ingredientesUsuarioUsados / totalIngredientesUsuario
 *                      (¿qué % de mis ingredientes usa esta receta?)
 *   graphScore       = 0.65 * coverageScore + 0.35 * utilizationScore
 *
 * Reenvía a RecommendationAgent con conversationId GRAPH_RESULT,
 * propagando también: recipeInstructions=, recipeTfIdfScores=,
 *                     recipeTimes=, userPrefs=
 */
public class GraphBehaviour extends CyclicBehaviour {

    public GraphBehaviour(Agent agent) {
        super(agent);
    }

    @Override
    public void action() {
        MessageTemplate template = MessageTemplate.MatchConversationId("ONTOLOGY_RESULT");
        ACLMessage msg = myAgent.receive(template);

        if (msg != null) {
            System.out.println("GraphAgent recibe:");
            System.out.println(msg.getContent());

            String fullInput = msg.getContent();

            List<String>              userIngredients      = new ArrayList<>();
            Map<String, List<String>> recipeIngredientsMap = new LinkedHashMap<>();
            String instructionsLine = "";
            String tfidfLine        = "";
            String timesLine        = "";
            String userPrefsLine    = "";

            for (String line : fullInput.split("\n")) {
                if (line.startsWith("userIngredients=")) {
                    for (String raw : line.substring("userIngredients=".length()).split(",")) {
                        String s = IngredientStemmer.stem(raw.trim().toLowerCase());
                        if (!s.isEmpty()) userIngredients.add(s);
                    }
                } else if (line.startsWith("recipes=")) {
                    parseRecipesLine(line.substring("recipes=".length()), recipeIngredientsMap);
                } else if (line.startsWith("recipeInstructions=")) {
                    instructionsLine = line;
                } else if (line.startsWith("recipeTfIdfScores=")) {
                    tfidfLine = line;
                } else if (line.startsWith("recipeTimes=")) {
                    timesLine = line;
                } else if (line.startsWith("userPrefs=")) {
                    userPrefsLine = line;
                }
                // El resto de líneas (recipeIngredients=, recipeServings=, etc.)
                // ya no son necesarias en este agente y se descartan aquí.
            }

            List<GraphNode> nodes = buildGraph(userIngredients, recipeIngredientsMap);
            logGraph(userIngredients, nodes);

            String result = buildOutputMessage(
                    nodes, instructionsLine, tfidfLine, timesLine, userPrefsLine);

            ACLMessage forward = new ACLMessage(ACLMessage.INFORM);
            forward.addReceiver(new AID("RecommendationAgent", AID.ISLOCALNAME));
            forward.setConversationId("GRAPH_RESULT");
            forward.setContent(result);
            myAgent.send(forward);

            System.out.println("GraphAgent envía a RecommendationAgent:");
            System.out.println(result);

        } else {
            block();
        }
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private void parseRecipesLine(String text, Map<String, List<String>> out) {
        for (String entry : text.split(";")) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) continue;
            String name = parts[0].trim();
            List<String> ings = new ArrayList<>();
            for (String raw : parts[1].split(",")) {
                String s = IngredientStemmer.stem(raw.trim().toLowerCase());
                if (!s.isEmpty()) ings.add(s);
            }
            out.put(name, ings);
        }
    }

    // ── Construcción del grafo bipartito ─────────────────────────────────────

    private List<GraphNode> buildGraph(List<String> userIngredients,
                                       Map<String, List<String>> recipeIngredientsMap) {
        int totalUser = Math.max(1, userIngredients.size());

        List<GraphNode> nodes = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : recipeIngredientsMap.entrySet()) {
            String       recipeName  = entry.getKey();
            List<String> recipeIngs  = entry.getValue();
            int          totalRecipe = Math.max(1, recipeIngs.size());

            List<String> matched = new ArrayList<>();
            List<String> missing = new ArrayList<>();

            for (String recipeIng : recipeIngs) {
                // IngredientStemmer.matches() cubre:
                //   exact match, plural/singular, y genérico↔específico
                //   ("chicken" ↔ "chicken breast", "pepper" ↔ "bell pepper")
                boolean found = userIngredients.stream()
                        .anyMatch(u -> IngredientStemmer.matches(u, recipeIng));
                if (found) {
                    matched.add(recipeIng);
                } else {
                    missing.add(recipeIng);
                }
            }

            // ── Métricas del grafo ──────────────────────────────────────────
            double coverageScore = (double) matched.size() / totalRecipe;

            long utilized = userIngredients.stream()
                    .filter(u -> recipeIngs.stream()
                            .anyMatch(r -> IngredientStemmer.matches(u, r)))
                    .count();
            double utilizationScore = (double) utilized / totalUser;

            double graphScore = 0.65 * coverageScore + 0.35 * utilizationScore;

            nodes.add(new GraphNode(
                    recipeName, recipeIngs, matched, missing,
                    matched.size(), recipeIngs.size(),
                    graphScore, coverageScore, utilizationScore
            ));
        }

        return nodes;
    }

    // ── Salida ───────────────────────────────────────────────────────────────

    private String buildOutputMessage(List<GraphNode> nodes,
                                      String instructionsLine,
                                      String tfidfLine,
                                      String timesLine,
                                      String userPrefsLine) {
        StringBuilder sb = new StringBuilder("graphResults=\n");
        for (GraphNode node : nodes) {
            sb.append(node.toMessageFormat()).append("\n");
        }
        if (!instructionsLine.isEmpty()) sb.append(instructionsLine).append("\n");
        if (!tfidfLine.isEmpty())        sb.append(tfidfLine).append("\n");
        if (!timesLine.isEmpty())        sb.append(timesLine).append("\n");
        if (!userPrefsLine.isEmpty())    sb.append(userPrefsLine).append("\n");
        return sb.toString();
    }

    private void logGraph(List<String> userIngredients, List<GraphNode> nodes) {
        System.out.println("GraphAgent: ingredientes usuario (stemmizados) = " + userIngredients);
        for (GraphNode n : nodes) {
            System.out.printf("  %-35s  coverage=%.2f  utilization=%.2f  graph=%.2f%n",
                    n.getRecipeName(),
                    n.getCoverageScore(),
                    n.getUtilizationScore(),
                    n.getGraphScore());
        }
    }
}
