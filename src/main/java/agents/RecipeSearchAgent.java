// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package agents;

import behaviours.searchbehaviours.SearchBehaviour;
import com.google.gson.Gson;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.MessageTemplate;
import java.io.PrintStream;
import java.net.http.HttpClient;

public class RecipeSearchAgent extends Agent {

    private HttpClient httpClient;
    private Gson       gson;

    @Override
    protected void setup() {
        System.out.println("RecipeSearchAgent " + getAID().getName() + " iniciado (TheMealDB).");
        httpClient = HttpClient.newHttpClient();
        gson       = new Gson();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("recipe-search");
        sd.setName("JADE-recipe-search");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
        );

        addBehaviour(new SearchBehaviour(this, template, httpClient, gson));
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException var2) {
            var2.printStackTrace();
        }

    }
}
