package agents;

/**
 * Tests para OntologyProcessor (sin JADE).
 * Ejecutar: java -cp out agents.OntologyAgentTest
 */
public class OntologyAgentTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        // ── stem() ──
        testStem();

        // ── plural matching (Problema 1) ──
        testEggEqualsEggs();
        testTomatoEqualsтомatoes();
        testMushroomEqualsMushrooms();

        // ── sustituciones originales ──
        testFullMessagePassthrough();
        testSubstitutionChickenToTurkey();
        testSubstitutionButterToOliveOil();
        testSubstitutionCreamsToMilk();
        testNoSubstitutionWhenUserHasIngredient();
        testNoSubstitutionWhenUserHasIngredientPlural();
        testNoSubstitutionWhenUserLacksAlternative();
        testMultipleRecipes();
        testUserIngredientsPresentInOutput();
        testOtherLinesPassthrough();
        testInstructionsLinePassthrough();
        testTfIdfScoresPassthrough();
        testRecipeIngredientsPassthrough();

        System.out.println("\n============================");
        System.out.println("PASSED: " + passed + " / FAILED: " + failed);
        if (failed > 0) System.exit(1);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static OntologyProcessor proc() {
        return new OntologyProcessor();
    }

    private static void ok(String name) {
        passed++;
        System.out.println("[OK]   " + name);
    }

    private static void fail(String name, String msg) {
        failed++;
        System.out.println("[FAIL] " + name + " -> " + msg);
    }

    private static void assertTrue(String name, boolean condition) {
        if (condition) ok(name); else fail(name, "expected true");
    }

    private static void assertContains(String name, String haystack, String needle) {
        if (haystack.contains(needle)) ok(name);
        else fail(name, "expected to contain [" + needle + "] in:\n" + haystack);
    }

    private static void assertNotContains(String name, String haystack, String needle) {
        if (!haystack.contains(needle)) ok(name);
        else fail(name, "expected NOT to contain [" + needle + "] in:\n" + haystack);
    }

    private static void assertEqual(String name, String expected, String actual) {
        if (expected.equals(actual)) ok(name);
        else fail(name, "\n  expected: [" + expected + "]\n  actual  : [" + actual + "]");
    }

    // ── simulación del mensaje completo de TextMiningBehaviour ─────────────────

    /**
     * Construye el mensaje de 11 líneas que TextMiningBehaviour envía a OntologyAgent.
     * Reproduce el formato exacto del protocolo.
     */
    private static String buildFullMessage(
            String userIngredients,
            String recipes,                  // "RecipeA:ing1,ing2;RecipeB:ing3"
            String recipeIngredients,        // "RecipeA:ing1|200|g,ing2|3|cloves"
            String recipeTimes,              // "RecipeA:30"
            String recipeServings,           // "RecipeA:4"
            String recipeTags,               // "RecipeA:vegetarian"
            String recipeCuisines,           // "RecipeA:italian"
            String recipeDishTypes,          // "RecipeA:lunch"
            String recipeHealthScores,       // "RecipeA:72"
            String recipeTfIdfScores,        // "RecipeA:0.8500"
            String recipeInstructions        // "RecipeA:Boil pasta, add garlic"
    ) {
        return "userIngredients=" + userIngredients + "\n"
             + "recipes=" + recipes + "\n"
             + "recipeIngredients=" + recipeIngredients + "\n"
             + "recipeTimes=" + recipeTimes + "\n"
             + "recipeServings=" + recipeServings + "\n"
             + "recipeTags=" + recipeTags + "\n"
             + "recipeCuisines=" + recipeCuisines + "\n"
             + "recipeDishTypes=" + recipeDishTypes + "\n"
             + "recipeHealthScores=" + recipeHealthScores + "\n"
             + "recipeTfIdfScores=" + recipeTfIdfScores + "\n"
             + "recipeInstructions=" + recipeInstructions + "\n";
    }

    // ── tests ──────────────────────────────────────────────────────────────────

    private static void testFullMessagePassthrough() {
        String input = buildFullMessage(
                "egg,pasta,garlic",
                "Pasta Aglio:pasta,garlic,parmesan",
                "Pasta Aglio:pasta|200|g,garlic|3|cloves,parmesan|50|g",
                "Pasta Aglio:20",
                "Pasta Aglio:2",
                "Pasta Aglio:vegetarian",
                "Pasta Aglio:italian",
                "Pasta Aglio:lunch",
                "Pasta Aglio:72",
                "Pasta Aglio:0.8500",
                "Pasta Aglio:Boil pasta, add garlic and parmesan"
        );

        String output = proc().process(input);

        assertContains("fullMsg_userIngredients", output, "userIngredients=egg,pasta,garlic");
        assertContains("fullMsg_recipes_line", output, "recipes=");
        assertContains("fullMsg_recipeIngredients", output, "recipeIngredients=Pasta Aglio:pasta|200|g");
        assertContains("fullMsg_recipeTimes", output, "recipeTimes=Pasta Aglio:20");
        assertContains("fullMsg_recipeServings", output, "recipeServings=Pasta Aglio:2");
        assertContains("fullMsg_recipeTags", output, "recipeTags=Pasta Aglio:vegetarian");
        assertContains("fullMsg_recipeCuisines", output, "recipeCuisines=Pasta Aglio:italian");
        assertContains("fullMsg_recipeDishTypes", output, "recipeDishTypes=Pasta Aglio:lunch");
        assertContains("fullMsg_recipeHealthScores", output, "recipeHealthScores=Pasta Aglio:72");
        assertContains("fullMsg_recipeTfIdfScores", output, "recipeTfIdfScores=Pasta Aglio:0.8500");
        assertContains("fullMsg_recipeInstructions", output, "recipeInstructions=Pasta Aglio:Boil pasta");
    }

    private static void testSubstitutionChickenToTurkey() {
        // User has turkey; recipe needs chicken → turkey should be added as substitute
        String input = buildFullMessage(
                "turkey,garlic,onion",
                "Chicken Stir Fry:chicken,garlic,onion",
                "Chicken Stir Fry:chicken|300|g,garlic|2|cloves,onion|1|unit",
                "Chicken Stir Fry:25", "Chicken Stir Fry:4", "Chicken Stir Fry:",
                "Chicken Stir Fry:", "Chicken Stir Fry:", "Chicken Stir Fry:65",
                "Chicken Stir Fry:0.7200",
                "Chicken Stir Fry:Cook chicken with garlic and onion"
        );

        String output = proc().process(input);
        // chicken stays, turkey added next to it
        assertContains("chickenToTurkey_chicken_kept", output, "chicken");
        assertContains("chickenToTurkey_turkey_added", output, "turkey");
    }

    private static void testSubstitutionButterToOliveOil() {
        // User has olive oil; recipe needs butter → olive oil should be added as substitute
        String input = buildFullMessage(
                "olive oil,flour,egg",
                "Butter Cake:butter,flour,egg",
                "Butter Cake:butter|100|g,flour|200|g,egg|2|unit",
                "Butter Cake:60", "Butter Cake:8", "Butter Cake:vegetarian",
                "Butter Cake:", "Butter Cake:dessert", "Butter Cake:40",
                "Butter Cake:0.5500",
                "Butter Cake:Mix butter with flour and eggs"
        );

        String output = proc().process(input);
        assertContains("butterToOil_butter_kept", output, "butter");
        assertContains("butterToOil_oliveoil_added", output, "olive oil");
    }

    private static void testSubstitutionCreamsToMilk() {
        // User has milk; recipe needs cream → milk should be added as substitute
        String input = buildFullMessage(
                "milk,garlic,pasta",
                "Cream Pasta:pasta,cream,garlic",
                "Cream Pasta:pasta|200|g,cream|100|ml,garlic|2|cloves",
                "Cream Pasta:20", "Cream Pasta:2", "Cream Pasta:vegetarian",
                "Cream Pasta:italian", "Cream Pasta:lunch", "Cream Pasta:55",
                "Cream Pasta:0.6800",
                "Cream Pasta:Cook pasta, add cream and garlic"
        );

        String output = proc().process(input);
        assertContains("creamToMilk_cream_kept", output, "cream");
        assertContains("creamToMilk_milk_added", output, "milk");
    }

    private static void testNoSubstitutionWhenUserHasIngredient() {
        // User already has chicken — no substitute should be added
        String input = buildFullMessage(
                "chicken,garlic",
                "Garlic Chicken:chicken,garlic",
                "Garlic Chicken:chicken|300|g,garlic|3|cloves",
                "Garlic Chicken:30", "Garlic Chicken:4", "Garlic Chicken:",
                "Garlic Chicken:", "Garlic Chicken:", "Garlic Chicken:70",
                "Garlic Chicken:0.9000",
                "Garlic Chicken:Season chicken, cook with garlic"
        );

        String output = proc().process(input);

        // Extract just the recipes= line to count occurrences of "chicken"
        String recipesLine = "";
        for (String line : output.split("\n")) {
            if (line.startsWith("recipes=")) { recipesLine = line; break; }
        }

        // chicken should appear only once (not duplicated with substitute)
        int count = 0;
        int idx = 0;
        while ((idx = recipesLine.indexOf("chicken", idx)) != -1) { count++; idx++; }
        assertTrue("noSubstitute_chicken_not_duplicated", count == 1);
    }

    private static void testNoSubstitutionWhenUserLacksAlternative() {
        // User doesn't have turkey or tofu, so no substitute for chicken
        String input = buildFullMessage(
                "garlic,onion",
                "Chicken Soup:chicken,garlic,onion",
                "Chicken Soup:chicken|400|g,garlic|2|cloves,onion|1|unit",
                "Chicken Soup:45", "Chicken Soup:6", "Chicken Soup:",
                "Chicken Soup:", "Chicken Soup:", "Chicken Soup:60",
                "Chicken Soup:0.6000",
                "Chicken Soup:Simmer chicken with vegetables"
        );

        String output = proc().process(input);

        String recipesLine = "";
        for (String line : output.split("\n")) {
            if (line.startsWith("recipes=")) { recipesLine = line; break; }
        }

        // chicken present, turkey absent
        assertContains("noAlternative_chicken_present", recipesLine, "chicken");
        assertNotContains("noAlternative_turkey_absent", recipesLine, "turkey");
        assertNotContains("noAlternative_tofu_absent", recipesLine, "tofu");
    }

    private static void testMultipleRecipes() {
        // Two recipes, substitution applies only where user lacks ingredient
        // user has: turkey, olive oil, garlic
        // RecipeA needs chicken (sub→turkey), RecipeB needs garlic (user has it, no sub)
        String input = buildFullMessage(
                "turkey,olive oil,garlic",
                "RecipeA:chicken,garlic;RecipeB:beef,garlic",
                "RecipeA:chicken|300|g,garlic|2|cloves;RecipeB:beef|300|g,garlic|2|cloves",
                "RecipeA:30;RecipeB:40",
                "RecipeA:4;RecipeB:4",
                "RecipeA:;RecipeB:",
                "RecipeA:;RecipeB:",
                "RecipeA:;RecipeB:",
                "RecipeA:60;RecipeB:55",
                "RecipeA:0.8000;RecipeB:0.7000",
                "RecipeA:Cook chicken;RecipeB:Cook beef"
        );

        String output = proc().process(input);
        String recipesLine = "";
        for (String line : output.split("\n")) {
            if (line.startsWith("recipes=")) { recipesLine = line; break; }
        }

        assertContains("multiRecipe_recipeA_turkey_added", recipesLine, "turkey");
        assertContains("multiRecipe_recipeA_chicken_present", recipesLine, "chicken");
        // beef has substitute pork, but user doesn't have pork
        assertNotContains("multiRecipe_recipeB_no_pork", recipesLine, "pork");
    }

    private static void testUserIngredientsPresentInOutput() {
        String input = buildFullMessage(
                "egg,pasta,garlic",
                "Pasta:pasta,garlic,egg",
                "Pasta:pasta|200|g,garlic|2|cloves,egg|2|unit",
                "Pasta:20", "Pasta:2", "Pasta:vegetarian",
                "Pasta:italian", "Pasta:lunch", "Pasta:70",
                "Pasta:0.9000",
                "Pasta:Boil and mix"
        );

        String output = proc().process(input);
        assertContains("userIngr_in_output", output, "userIngredients=egg,pasta,garlic");
    }

    private static void testOtherLinesPassthrough() {
        // Lines that are not userIngredients= or recipes= must pass through unchanged
        String input = buildFullMessage(
                "egg,pasta",
                "Pasta:pasta,egg",
                "Pasta:pasta|200|g,egg|2|unit",
                "Pasta:15", "Pasta:2", "Pasta:vegetarian",
                "Pasta:italian", "Pasta:lunch", "Pasta:65",
                "Pasta:0.8800",
                "Pasta:Boil pasta and add egg"
        );

        String output = proc().process(input);
        assertContains("passthrough_recipeTimes", output, "recipeTimes=Pasta:15");
        assertContains("passthrough_recipeServings", output, "recipeServings=Pasta:2");
        assertContains("passthrough_recipeTags", output, "recipeTags=Pasta:vegetarian");
        assertContains("passthrough_recipeCuisines", output, "recipeCuisines=Pasta:italian");
        assertContains("passthrough_recipeDishTypes", output, "recipeDishTypes=Pasta:lunch");
        assertContains("passthrough_recipeHealthScores", output, "recipeHealthScores=Pasta:65");
    }

    private static void testInstructionsLinePassthrough() {
        String instructions = "Pasta:Boil pasta for 8 minutes, drain and mix with egg";
        String input = buildFullMessage(
                "egg,pasta",
                "Pasta:pasta,egg",
                "Pasta:pasta|200|g,egg|2|unit",
                "Pasta:15", "Pasta:2", "Pasta:",
                "Pasta:", "Pasta:", "Pasta:65",
                "Pasta:0.8800",
                instructions
        );

        String output = proc().process(input);
        assertContains("instructions_passthrough", output, "recipeInstructions=" + instructions);
    }

    private static void testTfIdfScoresPassthrough() {
        String input = buildFullMessage(
                "egg,pasta",
                "Pasta:pasta,egg",
                "Pasta:pasta|200|g,egg|2|unit",
                "Pasta:15", "Pasta:2", "Pasta:",
                "Pasta:", "Pasta:", "Pasta:65",
                "Pasta:0.7543",
                "Pasta:Boil pasta"
        );

        String output = proc().process(input);
        assertContains("tfidf_passthrough", output, "recipeTfIdfScores=Pasta:0.7543");
    }

    // ── stem() ───────────────────────────────────────────────────────────────

    private static void testStem() {
        // plurales regulares (-s)
        assertEqual("stem eggs→egg",          "egg",       OntologyProcessor.stem("eggs"));
        assertEqual("stem mushrooms→mushroom","mushroom",  OntologyProcessor.stem("mushrooms"));
        assertEqual("stem onions→onion",      "onion",     OntologyProcessor.stem("onions"));
        assertEqual("stem carrots→carrot",    "carrot",    OntologyProcessor.stem("carrots"));
        assertEqual("stem cloves→clove",      "clove",     OntologyProcessor.stem("cloves"));
        // -oes → -o
        assertEqual("stem tomatoes→tomato",   "tomato",    OntologyProcessor.stem("tomatoes"));
        assertEqual("stem potatoes→potato",   "potato",    OntologyProcessor.stem("potatoes"));
        // -ies → -y
        assertEqual("stem berries→berry",     "berry",     OntologyProcessor.stem("berries"));
        assertEqual("stem cherries→cherry",   "cherry",    OntologyProcessor.stem("cherries"));
        // protegidos
        assertEqual("stem peas stays peas",   "peas",      OntologyProcessor.stem("peas"));
        assertEqual("stem asparagus unchanged","asparagus", OntologyProcessor.stem("asparagus"));
        assertEqual("stem bass unchanged",    "bass",      OntologyProcessor.stem("bass"));
        // ya en singular → sin cambios
        assertEqual("stem egg unchanged",     "egg",       OntologyProcessor.stem("egg"));
        assertEqual("stem tomato unchanged",  "tomato",    OntologyProcessor.stem("tomato"));
        // compuesto → sin cambios
        assertEqual("stem olive oil unchanged","olive oil", OntologyProcessor.stem("olive oil"));
        assertEqual("stem soy sauce unchanged","soy sauce", OntologyProcessor.stem("soy sauce"));
        // mayúsculas normalizadas
        assertEqual("stem Eggs→egg",          "egg",       OntologyProcessor.stem("Eggs"));
        assertEqual("stem GARLIC→garlic",     "garlic",    OntologyProcessor.stem("GARLIC"));
    }

    // ── plural matching (Problema 1) ─────────────────────────────────────────

    private static void testEggEqualsEggs() {
        // Usuario tiene "egg", receta pide "eggs" → no se busca sustituto, se reconoce como disponible
        String input = buildFullMessage(
                "egg,pasta",
                "Carbonara:eggs,pasta",
                "Carbonara:eggs|2|unit,pasta|200|g",
                "Carbonara:20", "Carbonara:2", "Carbonara:vegetarian",
                "Carbonara:italian", "Carbonara:lunch", "Carbonara:68",
                "Carbonara:0.8500",
                "Carbonara:Mix eggs with pasta"
        );
        String output = proc().process(input);
        String recipesLine = extractLine(output, "recipes=");
        // eggs debe estar, pero NO debe haber sustitutos añadidos (usuario ya tiene el ingrediente)
        assertContains("eggEggs_eggs_present", recipesLine, "eggs");
        // El único ingrediente que podría ser sustituto de eggs es otro
        // Lo importante: la lista no debe crecer más de lo esperado
        // eggs y pasta → no hay sustitución → sigue siendo eggs,pasta (sin añadidos)
        assertEqual("eggEggs_no_extra_ingredient",
                "recipes=Carbonara:eggs,pasta", recipesLine);
    }

    private static void testTomatoEqualsтомatoes() {
        // Usuario tiene "tomato", receta pide "tomatoes" → match por stem
        String input = buildFullMessage(
                "tomato,garlic",
                "Sauce:tomatoes,garlic",
                "Sauce:tomatoes|3|unit,garlic|2|cloves",
                "Sauce:15", "Sauce:4", "Sauce:vegan",
                "Sauce:", "Sauce:", "Sauce:80",
                "Sauce:0.9000",
                "Sauce:Cook tomatoes with garlic"
        );
        String output = proc().process(input);
        String recipesLine = extractLine(output, "recipes=");
        assertEqual("tomatoTomatoes_no_extra_ingredient",
                "recipes=Sauce:tomatoes,garlic", recipesLine);
    }

    private static void testMushroomEqualsMushrooms() {
        // Usuario tiene "mushroom", receta pide "mushrooms" → match por stem
        String input = buildFullMessage(
                "mushroom,garlic",
                "Stir Fry:mushrooms,garlic",
                "Stir Fry:mushrooms|200|g,garlic|2|cloves",
                "Stir Fry:15", "Stir Fry:2", "Stir Fry:vegan",
                "Stir Fry:", "Stir Fry:", "Stir Fry:75",
                "Stir Fry:0.8000",
                "Stir Fry:Fry mushrooms with garlic"
        );
        String output = proc().process(input);
        String recipesLine = extractLine(output, "recipes=");
        assertEqual("mushroomMushrooms_no_extra_ingredient",
                "recipes=Stir Fry:mushrooms,garlic", recipesLine);
    }

    private static void testNoSubstitutionWhenUserHasIngredientPlural() {
        // Usuario tiene "eggs" (plural), receta pide "egg" (singular) → también debe reconocerse
        String input = buildFullMessage(
                "eggs,flour",
                "Crepe:egg,flour",
                "Crepe:egg|2|unit,flour|100|g",
                "Crepe:15", "Crepe:2", "Crepe:vegetarian",
                "Crepe:", "Crepe:", "Crepe:60",
                "Crepe:0.7000",
                "Crepe:Mix egg and flour"
        );
        String output = proc().process(input);
        String recipesLine = extractLine(output, "recipes=");
        assertEqual("pluralUser_singularRecipe_no_extra",
                "recipes=Crepe:egg,flour", recipesLine);
    }

    // ── helper: extraer una línea del output ─────────────────────────────────
    private static String extractLine(String output, String prefix) {
        for (String line : output.split("\n")) {
            if (line.startsWith(prefix)) return line;
        }
        return "";
    }

    private static void testRecipeIngredientsPassthrough() {
        // recipeIngredients= line with amounts and units must pass through unchanged
        String detailLine = "Pasta:pasta|200|g,garlic|3|cloves,egg|2|unit";
        String input = buildFullMessage(
                "egg,pasta,garlic",
                "Pasta:pasta,garlic,egg",
                detailLine,
                "Pasta:15", "Pasta:2", "Pasta:",
                "Pasta:", "Pasta:", "Pasta:70",
                "Pasta:0.9100",
                "Pasta:Boil pasta and add egg"
        );

        String output = proc().process(input);
        assertContains("recipeIngr_passthrough", output, "recipeIngredients=" + detailLine);
    }
}
