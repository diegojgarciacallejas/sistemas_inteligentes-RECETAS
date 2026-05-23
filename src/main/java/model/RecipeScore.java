package model;

import java.util.Locale;

public class RecipeScore {

    private final String recipeName;
    private final double graphScore;
    private final double coverageScore;
    private final double utilizationScore;
    private final double tfidfScore;
    private final double finalScore;
    private final String instructions;

    public RecipeScore(String recipeName,
                       double graphScore,
                       double coverageScore,
                       double utilizationScore,
                       double tfidfScore,
                       double finalScore,
                       String instructions) {
        this.recipeName       = recipeName;
        this.graphScore       = graphScore;
        this.coverageScore    = coverageScore;
        this.utilizationScore = utilizationScore;
        this.tfidfScore       = tfidfScore;
        this.finalScore       = finalScore;
        this.instructions     = instructions != null ? instructions : "";
    }

    public String getRecipeName()       { return recipeName; }
    public double getGraphScore()       { return graphScore; }
    public double getCoverageScore()    { return coverageScore; }
    public double getUtilizationScore() { return utilizationScore; }
    public double getTfidfScore()       { return tfidfScore; }
    public double getFinalScore()       { return finalScore; }
    public String getInstructions()     { return instructions; }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "%s | graph=%.2f | coverage=%.2f | utilization=%.2f | tfidf=%.4f | final=%.2f",
                recipeName, graphScore, coverageScore, utilizationScore, tfidfScore, finalScore);
    }
}
