package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import behaviours.ontologybehaviours.FoodOntology;
import utils.DFUtils;

import java.util.*;

public class OntologyAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " iniciado.");
        DFUtils.registerService(this, "ontology-service");

        // Escucha mensajes de TextMiningAgent
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId("TEXT_MINING_RESULT");
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    System.out.println("OntologyAgent <- recibe de TextMiningAgent:");
                    System.out.println(msg.getContent());

                    String output = processOntology(msg.getContent());

                    ACLMessage forward = new ACLMessage(ACLMessage.INFORM);
                    forward.addReceiver(new AID("GraphAgent", AID.ISLOCALNAME));
                    forward.setConversationId("ONTOLOGY_RESULT");
                    forward.setContent(output);
                    send(forward);

                    System.out.println("OntologyAgent -> envía a GraphAgent:");
                    System.out.println(output);

                } else {
                    block();
                }
            }
        });
    }

    private String processOntology(String input) {
        // Parseamos las líneas del mensaje
        String userIngredients = "";
        String recipesLine = "";
        String timesLine = "";
        String servingsLine = "";
        String tagsLine = "";

        for (String line : input.split("\n")) {
            if (line.startsWith("userIngredients=")) {
                userIngredients = line.replace("userIngredients=", "").trim();
            } else if (line.startsWith("recipes=")) {
                recipesLine = line.replace("recipes=", "").trim();
            } else if (line.startsWith("recipeTimes=")) {
                timesLine = line;
            } else if (line.startsWith("recipeServings=")) {
                servingsLine = line;
            } else if (line.startsWith("recipeTags=")) {
                tagsLine = line;
            }
        }

        // Lista de ingredientes del usuario normalizados
        List<String> userIngList = new ArrayList<>();
        for (String ing : userIngredients.split(",")) {
            String normalized = ing.trim().toLowerCase();
            if (!normalized.isEmpty()) userIngList.add(normalized);
        }

        // Procesamos cada receta aplicando la ontología
        // Para cada ingrediente que falta, buscamos si el usuario tiene un sustituto
        StringBuilder enrichedRecipes = new StringBuilder("recipes=");
        boolean firstRecipe = true;

        if (!recipesLine.isEmpty()) {
            String[] recipeEntries = recipesLine.split(";");
            for (String entry : recipeEntries) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;

                String recipeName = parts[0].trim();
                String[] recipeIngredients = parts[1].split(",");

                // Construimos la lista enriquecida de ingredientes
                // Si falta un ingrediente pero el usuario tiene un sustituto,
                // lo añadimos como ingrediente disponible
                List<String> enrichedIngredients = new ArrayList<>();
                for (String ing : recipeIngredients) {
                    String normalized = ing.trim().toLowerCase();
                    if (normalized.isEmpty()) continue;

                    enrichedIngredients.add(normalized);

                    // Si el usuario no tiene este ingrediente, buscamos sustitutos
                    if (!userIngList.contains(normalized)) {
                        List<String> substitutes = FoodOntology.findSubstitutes(
                                normalized, userIngList);
                        if (!substitutes.isEmpty()) {
                            // Añadimos el sustituto como ingrediente disponible
                            // para que GraphAgent lo cuente como coincidencia
                            System.out.println("OntologyAgent: '"
                                    + normalized + "' puede sustituirse por '"
                                    + substitutes.get(0) + "' en " + recipeName);
                            enrichedIngredients.add(substitutes.get(0));
                        }
                    }
                }

                if (!firstRecipe) enrichedRecipes.append(";");
                firstRecipe = false;
                enrichedRecipes.append(recipeName).append(":")
                        .append(String.join(",", enrichedIngredients));
            }
        }

        // Reconstruimos el mensaje manteniendo el mismo formato
        // que espera GraphBehaviour, añadiendo los ingredientes enriquecidos
        return "userIngredients=" + userIngredients + "\n"
                + enrichedRecipes + "\n"
                + timesLine + "\n"
                + servingsLine + "\n"
                + tagsLine + "\n";
    }

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        System.out.println(getLocalName() + " finalizado.");
    }
}