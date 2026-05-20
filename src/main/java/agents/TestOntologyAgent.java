package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

public class TestOntologyAgent extends Agent {

    @Override
    protected void setup() {

        System.out.println(getLocalName() + " iniciado");

        addBehaviour(new OneShotBehaviour() {

            @Override
            public void action() {

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

                msg.addReceiver(new AID("GraphAgent", AID.ISLOCALNAME));

                msg.setConversationId("ONTOLOGY_RESULT");

                msg.setContent(
                        "userIngredients=huevo,arroz,pollo\n" +
                                "recipes=arroz con pollo:arroz,pollo,tomate;" +
                                "tortilla:huevo,patata,cebolla;" +
                                "pizza:harina,queso,tomate"
                );

                myAgent.send(msg);

                System.out.println(
                        "TestOntologyAgent envió mensaje"
                );
            }
        });
    }
}