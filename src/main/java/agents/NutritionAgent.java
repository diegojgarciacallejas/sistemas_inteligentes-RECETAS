// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package agents;

import behaviours.nutritionbehaviours.NutritionBehaviour;
import com.google.gson.Gson;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.MessageTemplate;
import java.io.PrintStream;
import java.net.http.HttpClient;

public class NutritionAgent extends Agent {
    private HttpClient httpClient;
    private Gson gson;

    public NutritionAgent() {
    }

    protected void setup() {
        System.out.println("NutritionAgent " + this.getAID().getName() + " is ready.");
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        DFAgentDescription var1 = new DFAgentDescription();
        var1.setName(this.getAID());
        ServiceDescription var2 = new ServiceDescription();
        var2.setType("nutrition-analysis");
        var2.setName("JADE-nutrition");
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
        this.addBehaviour(new NutritionBehaviour(this, var3, this.httpClient, this.gson));
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException var2) {
            var2.printStackTrace();
        }

    }
}
