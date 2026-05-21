package model;

import java.util.Locale;

public class RecipeScore {

    private String recipeName;
    private double graphScore;
    private double finalScore;
    private String instructions;

    public RecipeScore(
            String recipeName,
            double graphScore,
            double finalScore
    ) {
        this(recipeName, graphScore, finalScore, "");
    }

    public RecipeScore(
            String recipeName,
            double graphScore,
            double finalScore,
            String instructions
    ) {
        this.recipeName = recipeName;
        this.graphScore = graphScore;
        this.finalScore = finalScore;
        this.instructions = instructions != null ? instructions : "";
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

    public String getInstructions() {
        return instructions;
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