package behaviours.interfacebehaviours;

import agents.InterfaceAgent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.FIPANames;

import javax.swing.SwingUtilities;
import java.util.logging.Logger;

/**
 * InterfaceAgentBehaviours
 *
 * Contiene todos los behaviours JADE del InterfaceAgent:
 *  - WaitForRecommendationBehaviour : filtro bloqueante, espera el ranking final.
 *  - SendRequestBehaviour           : one-shot, envía la petición al RecipeSearchAgent.
 */
public class InterfaceAgentBehaviours {

    private static final Logger log = Logger.getLogger(InterfaceAgentBehaviours.class.getName());

    // Construcción prohibida — clase de utilidad estática
    private InterfaceAgentBehaviours() {}

    // =========================================================================
    // Behaviour 1: Esperar el ranking del RecommendationAgent  (BLOQUEANTE)
    // =========================================================================

    /**
     * Comportamiento cíclico con filtro de mensajes en modo BLOQUEANTE.
     * Espera mensajes ACL con conversationId = RECOMMENDATION_RESULT
     * y delega la visualización en InterfaceAgent#displayResults.
     */
    public static class WaitForRecommendationBehaviour extends CyclicBehaviour {

        private final InterfaceAgent agent;

        // Filtro: solo mensajes con el conversationId correcto
        private final MessageTemplate MT =
                MessageTemplate.MatchConversationId(InterfaceAgent.CONV_RECOMMENDATION);

        public WaitForRecommendationBehaviour(InterfaceAgent agent) {
            super(agent);
            this.agent = agent;
        }

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MT);

            if (msg != null) {
                log.info("RECOMMENDATION_RESULT recibido de " + msg.getSender().getName());
                String ranking = msg.getContent();
                // Actualizar la GUI siempre desde el hilo de Swing
                SwingUtilities.invokeLater(() -> agent.displayResults(ranking));
            } else {
                // Modo bloqueante: el agente se suspende hasta recibir un mensaje
                block();
            }
        }
    }

    // =========================================================================
    // Behaviour 2: Enviar la solicitud al RecipeSearchAgent  (ONE-SHOT)
    // =========================================================================

    /**
     * Comportamiento one-shot que busca el RecipeSearchAgent en el DF
     * y le envía el mensaje USER_REQUEST con el contenido del formulario.
     * Se crea cada vez que el usuario pulsa "Buscar recetas".
     */
    public static class SendRequestBehaviour extends OneShotBehaviour {

        private final InterfaceAgent agent;
        private final String content;

        public SendRequestBehaviour(InterfaceAgent agent, String content) {
            super(agent);
            this.agent   = agent;
            this.content = content;
        }

        @Override
        public void action() {
            AID recipeSearchAgent = agent.findRecipeSearchAgent();

            if (recipeSearchAgent == null) {
                SwingUtilities.invokeLater(() -> {
                    agent.setStatus("❌ RecipeSearchAgent no encontrado en el DF.");
                    agent.displayResults(null);   // limpia el área y reactiva el botón
                    agent.enableSearch();
                });
                log.warning("RecipeSearchAgent no disponible en el DF.");
                return;
            }

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(recipeSearchAgent);
            msg.setConversationId(InterfaceAgent.CONV_USER_REQUEST);
            msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            msg.setContent(content);
            msg.setLanguage("UTF-8");
            myAgent.send(msg);

            log.info("USER_REQUEST enviado a " + recipeSearchAgent.getName());
            SwingUtilities.invokeLater(() ->
                    agent.setStatus("✅ Solicitud enviada. Esperando resultados..."));
        }
    }
}
