package model;

import java.util.List;
import java.util.Locale;

public class GraphNode {

    private String recipeName;
    private List<String> ingredients;
    private int matches;
    private int totalIngredients;
    private double graphScore;

    public GraphNode(
            String recipeName,
            List<String> ingredients,
            int matches,
            int totalIngredients,
            double graphScore
    ) {
        this.recipeName = recipeName;
        this.ingredients = ingredients;
        this.matches = matches;
        this.totalIngredients = totalIngredients;
        this.graphScore = graphScore;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public List<String> getIngredients() {
        return ingredients;
    }

    public int getMatches() {
        return matches;
    }

    public int getTotalIngredients() {
        return totalIngredients;
    }

    public double getGraphScore() {
        return graphScore;
    }

    public String toMessageFormat() {
        return recipeName
                + ";ingredients=" + ingredients
                + ";matches=" + matches
                + ";total=" + totalIngredients
                + ";graphScore=" + String.format(Locale.US, "%.2f", graphScore);
    }
}