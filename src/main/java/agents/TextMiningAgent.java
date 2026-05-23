package agents;

import behaviours.miningbehaviours.TextMiningBehaviour;
import jade.core.Agent;
import utils.DFUtils;

/**
 * TextMiningAgent
 *
 * Recibe recetas de RecipeSearchAgent (conversationId "RECIPE_SEARCH_RESULT"),
 * extrae ingredientes y metadatos llamando a Spoonacular, y envía el resultado
 * a OntologyAgent (conversationId "TEXT_MINING_RESULT").
 *
 * CAMBIO REQUERIDO EN RecipeSearchAgent:
 *   El JSON de respuesta debe incluir "userIngredients" con los ingredientes
 *   que el agente recibio como entrada. Cambio minimo en prepareResultNotification:
 *
 *     JsonObject resultData = new JsonObject();
 *     resultData.addProperty("userIngredients", ingredients); // <- anadir
 *     resultData.add("recipes", resultRecipes);
 */
public class TextMiningAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " iniciado.");

        DFUtils.registerService(this, "text-mining-service");

        addBehaviour(new TextMiningBehaviour(this));
    }

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        System.out.println(getLocalName() + " finalizado.");
    }
}