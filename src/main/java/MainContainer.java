import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class MainContainer {

    public static void main(String[] args) throws Exception {

        Runtime rt = Runtime.instance();

        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN, "true");
        profile.setParameter(Profile.GUI, "true");
        profile.setParameter(Profile.LOCAL_PORT, "1100");

        AgentContainer container = rt.createMainContainer(profile);

        AgentController interfaceAgent      = container.createNewAgent("InterfaceAgent",      "agents.InterfaceAgent",      new Object[]{});
        AgentController recipeSearchAgent   = container.createNewAgent("RecipeSearchAgent",   "agents.RecipeSearchAgent",   new Object[]{});
        AgentController textMiningAgent     = container.createNewAgent("TextMiningAgent",     "agents.TextMiningAgent",     new Object[]{});
        AgentController ontologyAgent       = container.createNewAgent("OntologyAgent",       "agents.OntologyAgent",       new Object[]{});
        AgentController graphAgent          = container.createNewAgent("GraphAgent",          "agents.GraphAgent",          new Object[]{});
        AgentController recommendationAgent = container.createNewAgent("RecommendationAgent", "agents.RecommendationAgent", new Object[]{});

        interfaceAgent.start();
        recipeSearchAgent.start();
        textMiningAgent.start();
        ontologyAgent.start();
        graphAgent.start();
        recommendationAgent.start();
    }
}
