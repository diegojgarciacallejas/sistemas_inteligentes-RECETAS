package model;

import java.util.Locale;

public class RecipeScore {

    private String recipeName;
    private double graphScore;
    private double finalScore;

    public RecipeScore(
            String recipeName,
            double graphScore,
            double finalScore
    ) {
        this.recipeName = recipeName;
        this.graphScore = graphScore;
        this.finalScore = finalScore;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public double getGraphScore() {
        return graphScore;
    }

    public double getFinalScore() {
        return finalScore;
    }

    @Override
    public String toString() {

        return recipeName
                + " | graphScore="
                + String.format(Locale.US, "%.2f", graphScore)
                + " | finalScore="
                + String.format(Locale.US, "%.2f", finalScore);
    }
}