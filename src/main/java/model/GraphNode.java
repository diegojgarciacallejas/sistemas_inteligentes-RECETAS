package model;

import java.util.List;
import java.util.Locale;

public class GraphNode {

    private String recipeName;

    private List<String> ingredients;

    private List<String> matchedIngredients;
    private List<String> missingIngredients;

    private int matches;
    private int totalIngredients;

    private double graphScore;

    public GraphNode(
            String recipeName,
            List<String> ingredients,
            List<String> matchedIngredients,
            List<String> missingIngredients,
            int matches,
            int totalIngredients,
            double graphScore
    ) {

        this.recipeName = recipeName;
        this.ingredients = ingredients;
        this.matchedIngredients = matchedIngredients;
        this.missingIngredients = missingIngredients;
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

    public List<String> getMatchedIngredients() {
        return matchedIngredients;
    }

    public List<String> getMissingIngredients() {
        return missingIngredients;
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
                + ";matched=" + matchedIngredients
                + ";missing=" + missingIngredients
                + ";matches=" + matches
                + ";total=" + totalIngredients
                + ";graphScore=" + String.format(Locale.US, "%.2f", graphScore);
    }
}