package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import behaviours.ontologybehaviours.FoodOntology;
import utils.DFUtils;

public class OntologyAgent extends Agent {

    private final OntologyProcessor processor = new OntologyProcessor();

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

                    String output = processor.process(msg.getContent());

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

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        System.out.println(getLocalName() + " finalizado.");
    }
}