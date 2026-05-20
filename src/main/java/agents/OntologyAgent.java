package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import behaviours.ontologybehaviours.FoodOntology;
import behaviours.ontologybehaviours.FoodCategory;
import utils.DFUtils;

import java.util.*;

public class OntologyAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " iniciado.");
        DFUtils.registerService(this, "ontology-service");

        // Forzamos la carga de FoodOn al arrancar el agente
        System.out.println("OntologyAgent: iniciando carga de FoodOn...");
        FoodOntology.getCategory("tomato");

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
        String userIngredients = "";
        String recipesValue    = "";
        List<String> otherLines = new ArrayList<>();

        for (String line : input.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (line.startsWith("userIngredients=")) {
                userIngredients = line.replace("userIngredients=", "").trim();
            } else if (line.startsWith("recipes=")) {
                recipesValue = line.replace("recipes=", "").trim();
            } else {
                otherLines.add(line);
            }
        }

        // Lista de ingredientes del usuario normalizados
        List<String> userIngList = new ArrayList<>();
        for (String ing : userIngredients.split(",")) {
            String normalized = ing.trim().toLowerCase();
            if (!normalized.isEmpty()) userIngList.add(normalized);
        }

        // Enriquecer los ingredientes de cada receta con sustitutos de FoodOn
        StringBuilder enrichedRecipes = new StringBuilder("recipes=");
        boolean firstRecipe = true;

        if (!recipesValue.isEmpty()) {
            for (String entry : recipesValue.split(";")) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;

                String   recipeName        = parts[0].trim();
                String[] recipeIngredients = parts[1].split(",");

                List<String> enrichedIngredients = new ArrayList<>();
                for (String ing : recipeIngredients) {
                    String normalized = ing.trim().toLowerCase();
                    if (normalized.isEmpty()) continue;

                    enrichedIngredients.add(normalized);

                    // Si el usuario no tiene este ingrediente buscamos sustitutos en FoodOn
                    if (!userIngList.contains(normalized)) {
                        List<String> substitutes = FoodOntology.findSubstitutes(
                                normalized, userIngList);
                        if (!substitutes.isEmpty()) {
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

        // Reconstruimos el mensaje manteniendo el formato que espera GraphAgent
        StringBuilder output = new StringBuilder();
        output.append("userIngredients=").append(userIngredients).append("\n");
        output.append(enrichedRecipes).append("\n");
        for (String line : otherLines) {
            output.append(line).append("\n");
        }
        return output.toString();
    }

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        System.out.println(getLocalName() + " finalizado.");
    }
}