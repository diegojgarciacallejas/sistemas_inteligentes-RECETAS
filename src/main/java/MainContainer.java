import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;

import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class MainContainer {

    public static void main(String[] args) {

        try {

            Runtime runtime = Runtime.instance();

            Profile profile = new ProfileImpl();

            profile.setParameter(Profile.GUI, "true");

            AgentContainer container =
                    runtime.createMainContainer(profile);

            // =========================
            // InterfaceAgent
            // =========================

            AgentController interfaceAgent =
                    container.createNewAgent(
                            "InterfaceAgent",
                            "agents.InterfaceAgent",
                            null
                    );

            // =========================
            // RecipeSearchAgent
            // =========================

            AgentController recipeSearchAgent =
                    container.createNewAgent(
                            "RecipeSearchAgent",
                            "agents.RecipeSearchAgent",
                            null
                    );

            // =========================
            // TextMiningAgent
            // =========================

            AgentController textMiningAgent =
                    container.createNewAgent(
                            "TextMiningAgent",
                            "agents.TextMiningAgent",
                            null
                    );

            // =========================
            // OntologyAgent
            // =========================

            AgentController ontologyAgent =
                    container.createNewAgent(
                            "OntologyAgent",
                            "agents.OntologyAgent",
                            null
                    );

            // =========================
            // GraphAgent
            // =========================

            AgentController graphAgent =
                    container.createNewAgent(
                            "GraphAgent",
                            "agents.GraphAgent",
                            null
                    );

            // =========================
            // NutritionAgent
            // =========================

            AgentController nutritionAgent =
                    container.createNewAgent(
                            "NutritionAgent",
                            "agents.NutritionAgent",
                            null
                    );

            // =========================
            // RecommendationAgent
            // =========================

            AgentController recommendationAgent =
                    container.createNewAgent(
                            "RecommendationAgent",
                            "agents.RecommendationAgent",
                            null
                    );

            // =========================
            // ExternalAgent
            // =========================

            AgentController externalAgent =
                    container.createNewAgent(
                            "ExternalAgent",
                            "agents.ExternalAgent",
                            null
                    );

            // =========================
            // START AGENTS
            // =========================

            interfaceAgent.start();

            recipeSearchAgent.start();

            textMiningAgent.start();

            ontologyAgent.start();

            graphAgent.start();

            nutritionAgent.start();

            recommendationAgent.start();

            externalAgent.start();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}