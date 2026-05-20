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
    private Gson gson;

    public RecipeSearchAgent() {
    }

    protected void setup() {
        System.out.println("RecipeSearchAgent " + this.getAID().getName() + " is ready.");
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        DFAgentDescription var1 = new DFAgentDescription();
        var1.setName(this.getAID());
        ServiceDescription var2 = new ServiceDescription();
        var2.setType("recipe-search");
        var2.setName("JADE-recipe-search");
        var1.addServices(var2);

        try {
            DFService.register(this, var1);
            PrintStream var10000 = System.out;
            String var10001 = this.getLocalName();
            var10000.println(var10001 + " registrado en DF como " + var2.getType());
        } catch (FIPAException var4) {
            var4.printStackTrace();
        }

        MessageTemplate var3 = MessageTemplate.and(MessageTemplate.MatchProtocol("fipa-request"), MessageTemplate.MatchPerformative(16));
        this.addBehaviour(new SearchBehaviour(this, var3, this.httpClient, this.gson));
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException var2) {
            var2.printStackTrace();
        }

    }
}
