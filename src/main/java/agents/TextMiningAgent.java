package agents;

import behaviours.miningbehaviours.TextMiningBehaviour;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

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

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("text-mining-service");
        sd.setName(getLocalName() + "-text-mining");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registrado en DF como text-mining-service");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new TextMiningBehaviour(this));
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + " finalizado.");
    }
}