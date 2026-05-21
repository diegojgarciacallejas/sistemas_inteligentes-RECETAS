package behaviours.miningbehaviours;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * TextMiningBehaviourTest
 *
 * Test puro Java. Solo necesita gson.jar y jade.jar en el classpath.
 *
 * Compilar (desde la raiz del proyecto):
 *   javac -cp jade.jar;gson.jar -d out \
 *         behaviours/textminingbehaviours/TextMiningBehaviour.java \
 *         behaviours/textminingbehaviours/TextMiningBehaviourTest.java
 *
 * Ejecutar:
 *   java -cp out;jade.jar;gson.jar \
 *        behaviours.textminingbehaviours.TextMiningBehaviourTest
 */
public class TextMiningBehaviourTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("======================================");
        System.out.println("  TextMiningBehaviour - Tests");
        System.out.println("======================================\n");

        runIsValidIngredientNameTests();
        runNormalizeTests();
        runSanitizeNameTests();
        runExtractRecipeDataTests();
        runExtractIngredientAmountsTests();
        runExtractCuisinesAndDishTypesTests();
        runStripHtmlTests();
        runTokenizeTests();
        runTfIdfTests();
        runCosineSimilarityTests();
        runComputeTfIdfScoresTests();
        runBuildOutputMessageTests();
        runProcessInputTests();
        runFetchAndExtractTests();
        runGraphBehaviourCompatibilityTest();

        System.out.println("\n======================================");
        System.out.printf("  Resultado: %d OK  |  %d FALLO%n", passed, failed);
        System.out.println("======================================");

        if (failed > 0) System.exit(1);
    }

    // ════════════════════════════════════════════════════════════════════════
    // isValidIngredientName()
    // ════════════════════════════════════════════════════════════════════════
    static void runIsValidIngredientNameTests() {
        section("isValidIngredientName()");
        TextMiningBehaviour b = makeBehaviour();

        // Nombres válidos de ingredientes
        assertTrue(b.isValidIngredientName("garlic"),              "garlic ok");
        assertTrue(b.isValidIngredientName("chicken breast"),      "chicken breast ok");
        assertTrue(b.isValidIngredientName("olive oil"),           "olive oil ok");
        assertTrue(b.isValidIngredientName("soy sauce"),           "soy sauce ok");
        assertTrue(b.isValidIngredientName("fresh ginger"),        "fresh ginger ok");
        assertTrue(b.isValidIngredientName("red bell pepper"),     "red bell pepper ok (3 palabras)");
        assertTrue(b.isValidIngredientName("boneless chicken thighs"), "5 palabras ok");

        // Instrucciones de receta que se colaron (Problema 2)
        assertFalse(b.isValidIngredientName("in a soup pot over medium heat"),
                "instrucción 'in a soup pot over medium heat' rechazada");
        assertFalse(b.isValidIngredientName("add ginger and onion"),
                "instrucción 'add ginger...' rechazada");
        assertFalse(b.isValidIngredientName("heat oil in a pan"),
                "instrucción 'heat oil...' rechazada");
        assertFalse(b.isValidIngredientName("cook until golden brown"),
                "instrucción 'cook until...' rechazada");
        assertFalse(b.isValidIngredientName("stir fry for two minutes in wok over high flame"),
                ">5 palabras rechazada");
        assertFalse(b.isValidIngredientName("over medium heat"),
                "empieza con 'over' rechazada");
        assertFalse(b.isValidIngredientName("at high temperature in the oven"),
                "empieza con 'at' rechazada");

        // Casos borde
        assertFalse(b.isValidIngredientName(""),   "vacío rechazado");
        assertFalse(b.isValidIngredientName(null), "null rechazado");
        assertFalse(b.isValidIngredientName("a".repeat(51)), ">50 chars rechazado");
    }

    // ════════════════════════════════════════════════════════════════════════
    // normalize()
    // ════════════════════════════════════════════════════════════════════════
    static void runNormalizeTests() {
        section("normalize()");
        TextMiningBehaviour b = makeBehaviour();

        assertEquals("garlic",          b.normalize("  Garlic  "),        "trim y lowercase");
        assertEquals("olive oil",       b.normalize("Olive Oil"),          "espacios internos");
        assertEquals("sea salt",        b.normalize("sea-salt"),           "guion a espacio");
        assertEquals("parmesan cheese", b.normalize("Parmesan Cheese!"),   "exclamacion");
        assertEquals("freshgarlic",     b.normalize("fresh;garlic"),       "punto y coma");
        assertEquals("chickenbreast",   b.normalize("chicken:breast"),     "dos puntos");
        assertEquals("",                b.normalize("   "),                "solo espacios");
        assertEquals("",                b.normalize(";;;:::"),             "solo separadores");
    }

    // ════════════════════════════════════════════════════════════════════════
    // sanitizeName()
    // ════════════════════════════════════════════════════════════════════════
    static void runSanitizeNameTests() {
        section("sanitizeName()");
        TextMiningBehaviour b = makeBehaviour();

        assertEquals("Pasta Aglio e Olio", b.sanitizeName("Pasta:Aglio e Olio"), "elimina :");
        assertEquals("Soup and Salad",     b.sanitizeName("Soup;and Salad"),      "elimina ;");
        assertEquals("Chicken Rice Bowl",  b.sanitizeName("Chicken Rice Bowl"),   "limpio");
        assertEquals("A B",                b.sanitizeName("A:B"),                 "sin doble espacio");
    }

    // ════════════════════════════════════════════════════════════════════════
    // extractRecipeData() — campos basicos
    // ════════════════════════════════════════════════════════════════════════
    static void runExtractRecipeDataTests() {
        section("extractRecipeData() - campos basicos");
        TextMiningBehaviour b = makeBehaviour();

        String jsonCompleto = "{"
                + "\"extendedIngredients\": ["
                + "  {\"name\": \"Garlic\",    \"amount\": 3.0,   \"unit\": \"cloves\"},"
                + "  {\"name\": \"Olive Oil\", \"amount\": 2.0,   \"unit\": \"tbsp\"},"
                + "  {\"name\": \"Pasta\",     \"amount\": 200.0, \"unit\": \"g\"}"
                + "],"
                + "\"readyInMinutes\": 25, \"servings\": 4,"
                + "\"vegan\": false, \"vegetarian\": true,"
                + "\"glutenFree\": false, \"dairyFree\": true,"
                + "\"cuisines\": [\"Italian\"], \"dishTypes\": [\"lunch\"],"
                + "\"healthScore\": 72,"
                + "\"summary\": \"A classic pasta dish.\","
                + "\"instructions\": \"Boil pasta. Add garlic.\""
                + "}";
        TextMiningBehaviour.RecipeData data = b.extractRecipeData("Pasta al Aglio", jsonCompleto);

        assertEquals("Pasta al Aglio", data.name,               "nombre");
        assertEquals(3,                data.ingredients.size(), "3 ingredientes");
        assertTrue(data.ingredients.contains("garlic"),    "garlic");
        assertTrue(data.ingredients.contains("olive oil"), "olive oil");
        assertTrue(data.ingredients.contains("pasta"),     "pasta");
        assertEquals(25,   data.readyInMinutes, "tiempo");
        assertEquals(4,    data.servings,       "raciones");
        assertFalse(data.vegan,      "vegan=false");
        assertTrue(data.vegetarian,  "vegetarian=true");
        assertFalse(data.glutenFree, "glutenFree=false");
        assertTrue(data.dairyFree,   "dairyFree=true");
        assertTrue(data.rawText.contains("classic pasta"), "rawText contiene summary");
        assertTrue(data.rawText.contains("Boil pasta"),    "rawText contiene instructions");

        String jsonMinimo = "{\"extendedIngredients\": [{\"name\": \"Egg\"}]}";
        TextMiningBehaviour.RecipeData dataMin = b.extractRecipeData("Boiled Egg", jsonMinimo);
        assertEquals(1,  dataMin.ingredients.size(), "1 ingrediente");
        assertEquals(-1, dataMin.readyInMinutes,     "tiempo ausente = -1");
        assertEquals(-1, dataMin.servings,           "raciones ausentes = -1");
        assertFalse(dataMin.vegan, "vegan ausente = false");
        assertEquals("", dataMin.rawText, "rawText ausente = cadena vacia");

        String jsonNullIng = "{\"extendedIngredients\": [{\"name\": null}, {\"name\": \"Tomato\"}]}";
        TextMiningBehaviour.RecipeData dataNullIng = b.extractRecipeData("Tomato Dish", jsonNullIng);
        assertEquals(1,       dataNullIng.ingredients.size(),    "null name se omite");
        assertEquals("tomato", dataNullIng.ingredients.get(0),   "tomato normalizado");

        String jsonSpecial = "{\"extendedIngredients\": [{\"name\": \";;;\"},{\"name\": \"Chicken\"}]}";
        assertEquals(1, b.extractRecipeData("Chicken Dish", jsonSpecial).ingredients.size(),
                "solo especiales se omite");
    }

    // ════════════════════════════════════════════════════════════════════════
    // extractRecipeData() — cantidades
    // ════════════════════════════════════════════════════════════════════════
    static void runExtractIngredientAmountsTests() {
        section("extractRecipeData() - cantidades de ingredientes");
        TextMiningBehaviour b = makeBehaviour();

        String json = "{"
                + "\"extendedIngredients\": ["
                + "  {\"name\": \"Garlic\",    \"amount\": 3.0,   \"unit\": \"cloves\"},"
                + "  {\"name\": \"Olive Oil\", \"amount\": 2.5,   \"unit\": \"tbsp\"},"
                + "  {\"name\": \"Salt\",      \"amount\": 0.5,   \"unit\": \"tsp\"},"
                + "  {\"name\": \"Pasta\",     \"amount\": 200.0, \"unit\": \"g\"},"
                + "  {\"name\": \"Egg\",       \"amount\": 2.0,   \"unit\": \"\"}"
                + "],"
                + "\"readyInMinutes\": 20, \"servings\": 2,"
                + "\"vegan\": false, \"vegetarian\": true,"
                + "\"glutenFree\": false, \"dairyFree\": false,"
                + "\"cuisines\": [], \"dishTypes\": [], \"healthScore\": 60,"
                + "\"summary\": \"\", \"instructions\": \"\""
                + "}";

        TextMiningBehaviour.RecipeData data = b.extractRecipeData("Pasta Carbonara", json);

        assertEquals(5, data.ingredientDetails.size(), "5 ingredientDetails");

        TextMiningBehaviour.IngredientInfo garlic = data.ingredientDetails.get(0);
        assertEquals("garlic", garlic.name, "nombre garlic");
        assertDoubleEquals(3.0,   garlic.amount, 0.001, "cantidad garlic");
        assertEquals("cloves",   garlic.unit,          "unidad garlic");

        assertDoubleEquals(2.5,   data.ingredientDetails.get(1).amount, 0.001, "aceite 2.5");
        assertDoubleEquals(200.0, data.ingredientDetails.get(3).amount, 0.001, "pasta 200");
        assertEquals("",          data.ingredientDetails.get(4).unit,          "egg sin unidad");

        assertEquals(data.ingredients.size(), data.ingredientDetails.size(), "tamaños iguales");
        for (int i = 0; i < data.ingredients.size(); i++) {
            assertEquals(data.ingredients.get(i), data.ingredientDetails.get(i).name,
                    "nombre coincide en pos " + i);
        }

        String jsonSin = "{\"extendedIngredients\": [{\"name\": \"Sugar\"}]}";
        TextMiningBehaviour.RecipeData dataSin = b.extractRecipeData("Cake", jsonSin);
        assertDoubleEquals(0.0, dataSin.ingredientDetails.get(0).amount, 0.001, "amount ausente=0");
        assertEquals("",        dataSin.ingredientDetails.get(0).unit,          "unit ausente=vacio");
    }

    // ════════════════════════════════════════════════════════════════════════
    // extractRecipeData() — cuisines, dishTypes, healthScore
    // ════════════════════════════════════════════════════════════════════════
    static void runExtractCuisinesAndDishTypesTests() {
        section("extractRecipeData() - cuisines, dishTypes, healthScore");
        TextMiningBehaviour b = makeBehaviour();

        String json = "{"
                + "\"extendedIngredients\": [{\"name\": \"Tomato\", \"amount\": 2.0, \"unit\": \"piece\"}],"
                + "\"readyInMinutes\": 15, \"servings\": 2,"
                + "\"vegan\": true, \"vegetarian\": true,"
                + "\"glutenFree\": true, \"dairyFree\": true,"
                + "\"cuisines\": [\"Mediterranean\", \"Italian\"],"
                + "\"dishTypes\": [\"lunch\", \"side dish\"],"
                + "\"healthScore\": 90,"
                + "\"summary\": \"\", \"instructions\": \"\""
                + "}";

        TextMiningBehaviour.RecipeData data = b.extractRecipeData("Tomato Salad", json);

        assertEquals(2, data.cuisines.size(), "2 cocinas");
        assertTrue(data.cuisines.contains("mediterranean"), "mediterranean");
        assertTrue(data.cuisines.contains("italian"),       "italian");
        assertEquals(2, data.dishTypes.size(), "2 tipos");
        assertTrue(data.dishTypes.contains("lunch"),      "lunch");
        assertTrue(data.dishTypes.contains("side dish"),  "side dish");
        assertEquals(90, data.healthScore, "healthScore=90");

        String jsonSin = "{\"extendedIngredients\": [{\"name\": \"Egg\"}]}";
        TextMiningBehaviour.RecipeData dataSin = b.extractRecipeData("Egg", jsonSin);
        assertTrue(dataSin.cuisines.isEmpty(),  "cuisines ausente=vacio");
        assertTrue(dataSin.dishTypes.isEmpty(), "dishTypes ausente=vacio");
        assertEquals(-1, dataSin.healthScore,  "healthScore ausente=-1");
    }

    // ════════════════════════════════════════════════════════════════════════
    // stripHtml()
    // ════════════════════════════════════════════════════════════════════════
    static void runStripHtmlTests() {
        section("stripHtml()");
        TextMiningBehaviour b = makeBehaviour();

        assertEquals("pasta with garlic",
                b.stripHtml("<b>pasta</b> with <i>garlic</i>"),
                "elimina etiquetas HTML");
        assertEquals("A vegan recipe",
                b.stripHtml("A <b>vegan</b> recipe"),
                "conserva texto");
        assertEquals("price $1.23 per serving",
                b.stripHtml("price <b>$1.23</b> per serving"),
                "elimina etiqueta con contenido especial");
        assertEquals("",
                b.stripHtml(""),
                "cadena vacia");
        assertEquals("plain text",
                b.stripHtml("plain text"),
                "texto sin HTML no cambia");
    }

    // ════════════════════════════════════════════════════════════════════════
    // tokenize()
    // ════════════════════════════════════════════════════════════════════════
    static void runTokenizeTests() {
        section("tokenize()");
        TextMiningBehaviour b = makeBehaviour();

        List<String> tokens = b.tokenize("Add fresh garlic and olive oil to the pan");
        assertTrue(tokens.contains("fresh"),  "conserva 'fresh'");
        assertTrue(tokens.contains("garlic"), "conserva 'garlic'");
        assertTrue(tokens.contains("olive"),  "conserva 'olive'");
        assertFalse(tokens.contains("and"),   "elimina stopword 'and'");
        assertFalse(tokens.contains("the"),   "elimina stopword 'the'");
        assertFalse(tokens.contains("to"),    "elimina stopword 'to'");

        // Tokens cortos (<3 chars) se descartan
        List<String> tokensCortos = b.tokenize("a an be do go");
        assertTrue(tokensCortos.isEmpty(), "tokens cortos se descartan");

        // Texto vacio
        assertTrue(b.tokenize("").isEmpty(),   "vacio -> lista vacia");
        assertTrue(b.tokenize(null).isEmpty(), "null -> lista vacia");

        // Mayusculas y puntuacion se normalizan
        List<String> tokensNorm = b.tokenize("Garlic! PASTA, olive-oil.");
        assertTrue(tokensNorm.contains("garlic"),    "garlic normalizado");
        assertTrue(tokensNorm.contains("pasta"),     "pasta normalizado");
        assertTrue(tokensNorm.contains("olive"),     "olive de olive-oil");
    }

    // ════════════════════════════════════════════════════════════════════════
    // TF e IDF
    // ════════════════════════════════════════════════════════════════════════
    static void runTfIdfTests() {
        section("computeTf() y computeIdf()");
        TextMiningBehaviour b = makeBehaviour();

        // TF: cada término = frecuencia / total
        List<String> doc = Arrays.asList("garlic", "pasta", "garlic", "garlic");
        Map<String, Double> tf = b.computeTf(doc);
        assertDoubleEquals(3.0 / 4.0, tf.get("garlic"), 0.001, "TF garlic = 3/4");
        assertDoubleEquals(1.0 / 4.0, tf.get("pasta"),  0.001, "TF pasta = 1/4");

        // TF de lista vacia
        assertTrue(b.computeTf(new ArrayList<>()).isEmpty(), "TF vacio -> mapa vacio");

        // IDF: termino raro (en 1 de 3 docs) tiene mayor IDF que termino comun (en todos)
        List<List<String>> corpus = Arrays.asList(
                Arrays.asList("garlic", "pasta", "truffle"),
                Arrays.asList("garlic", "rice",  "truffle"),
                Arrays.asList("garlic", "egg",   "truffle")
        );
        Map<String, Double> idf = b.computeIdf(corpus);

        // garlic y truffle aparecen en los 3 docs -> IDF bajo
        // pasta, rice, egg aparecen en 1 doc -> IDF alto
        assertTrue(idf.get("pasta") > idf.get("garlic"),
                "termino raro (pasta, 1/3) tiene IDF > termino comun (garlic, 3/3)");
        assertTrue(idf.get("rice") > idf.get("truffle"),
                "termino raro (rice, 1/3) tiene IDF > termino comun (truffle, 3/3)");

        // Todos los terminos del corpus deben estar en el IDF
        assertTrue(idf.containsKey("garlic"),  "garlic en IDF");
        assertTrue(idf.containsKey("truffle"), "truffle en IDF");
        assertTrue(idf.containsKey("pasta"),   "pasta en IDF");
    }

    // ════════════════════════════════════════════════════════════════════════
    // cosineSimilarity()
    // ════════════════════════════════════════════════════════════════════════
    static void runCosineSimilarityTests() {
        section("cosineSimilarity()");
        TextMiningBehaviour b = makeBehaviour();

        // Vectores identicos -> similitud = 1.0
        Map<String, Double> v1 = new HashMap<>();
        v1.put("garlic", 0.5); v1.put("pasta", 0.8);
        Map<String, Double> v2 = new HashMap<>(v1);
        assertDoubleEquals(1.0, b.cosineSimilarity(v1, v2), 0.001,
                "vectores identicos -> 1.0");

        // Vectores ortogonales (sin terminos comunes) -> similitud = 0.0
        Map<String, Double> v3 = new HashMap<>();
        v3.put("chicken", 0.5); v3.put("rice", 0.8);
        assertDoubleEquals(0.0, b.cosineSimilarity(v1, v3), 0.001,
                "vectores ortogonales -> 0.0");

        // Vector vacio -> 0.0 (sin division por cero)
        assertDoubleEquals(0.0, b.cosineSimilarity(new HashMap<>(), v1), 0.001,
                "vector vacio izquierda -> 0.0");
        assertDoubleEquals(0.0, b.cosineSimilarity(v1, new HashMap<>()), 0.001,
                "vector vacio derecha -> 0.0");

        // Similitud parcial: un termino comun de tres
        Map<String, Double> vA = new HashMap<>();
        vA.put("garlic", 1.0); vA.put("pasta", 1.0); vA.put("egg", 1.0);
        Map<String, Double> vB = new HashMap<>();
        vB.put("garlic", 1.0); vB.put("chicken", 1.0); vB.put("rice", 1.0);
        double sim = b.cosineSimilarity(vA, vB);
        assertTrue(sim > 0.0 && sim < 1.0, "similitud parcial entre 0 y 1");
    }

    // ════════════════════════════════════════════════════════════════════════
    // computeTfIdfScores() — integración completa del pipeline TF-IDF
    // ════════════════════════════════════════════════════════════════════════
    static void runComputeTfIdfScoresTests() {
        section("computeTfIdfScores() - pipeline TF-IDF completo");
        TextMiningBehaviour b = makeBehaviour();

        // Receta RELEVANTE: su texto menciona los ingredientes del usuario
        String rawRelevant   = "A delicious pasta dish with garlic and egg. "
                             + "Boil the pasta, fry garlic with egg until golden.";
        // Receta IRRELEVANTE: texto sin relacion con los ingredientes del usuario
        String rawIrrelevant = "Fresh salmon fillet seasoned with lemon juice and dill. "
                             + "Grill salmon for ten minutes until cooked through.";

        Map<String, TextMiningBehaviour.RecipeData> map = new LinkedHashMap<>();
        map.put("Pasta Dish",  makeData("Pasta Dish",  new ArrayList<>(), new ArrayList<>(),
                20, 2, false, false, false, false,
                new ArrayList<>(), new ArrayList<>(), 70, rawRelevant));
        map.put("Salmon Dish", makeData("Salmon Dish", new ArrayList<>(), new ArrayList<>(),
                15, 1, false, false, true, true,
                new ArrayList<>(), new ArrayList<>(), 90, rawIrrelevant));

        Map<String, Double> scores = b.computeTfIdfScores("pasta,garlic,egg", map);

        assertEquals(2, scores.size(), "scores tiene 2 entradas");
        assertTrue(scores.containsKey("Pasta Dish"),  "score para Pasta Dish");
        assertTrue(scores.containsKey("Salmon Dish"), "score para Salmon Dish");

        double pastScore   = scores.get("Pasta Dish");
        double salmonScore = scores.get("Salmon Dish");

        assertTrue(pastScore > 0.0,
                "Pasta Dish (texto relevante) tiene score > 0");
        assertTrue(pastScore > salmonScore,
                "Pasta Dish puntua mas que Salmon Dish (ingredientes del usuario en su texto)");

        // Scores deben estar entre 0 y 1
        assertTrue(pastScore   >= 0.0 && pastScore   <= 1.0, "score Pasta en [0,1]");
        assertTrue(salmonScore >= 0.0 && salmonScore <= 1.0, "score Salmon en [0,1]");

        // Corpus vacio de recetas -> mapa vacio (sin excepcion)
        Map<String, Double> empty = b.computeTfIdfScores("egg", new LinkedHashMap<>());
        assertTrue(empty.isEmpty(), "sin recetas -> scores vacio");
    }

    // ════════════════════════════════════════════════════════════════════════
    // buildOutputMessage()
    // ════════════════════════════════════════════════════════════════════════
    static void runBuildOutputMessageTests() {
        section("buildOutputMessage()");
        TextMiningBehaviour b = makeBehaviour();

        Map<String, TextMiningBehaviour.RecipeData> map = new LinkedHashMap<>();
        map.put("Pasta", makeData("Pasta",
                Arrays.asList("garlic", "pasta", "olive oil"),
                makeDetails(
                    new String[]{"garlic", "pasta", "olive oil"},
                    new double[]{3.0, 200.0, 2.0},
                    new String[]{"cloves", "g", "tbsp"}),
                20, 2, false, false, false, false,
                new ArrayList<>(), Arrays.asList("lunch"), 55, "pasta with garlic"));
        map.put("Salad", makeData("Salad",
                Arrays.asList("tomato", "lettuce", "olive oil"),
                makeDetails(
                    new String[]{"tomato", "lettuce", "olive oil"},
                    new double[]{3.0, 1.0, 1.5},
                    new String[]{"piece", "head", "tbsp"}),
                10, 1, true, true, true, true,
                Arrays.asList("mediterranean"), Arrays.asList("side dish", "salad"), 88,
                "fresh tomato salad with lettuce"));

        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("Pasta", 0.75);
        scores.put("Salad", 0.30);

        String output = b.buildOutputMessage("egg,tomato,pasta", map, scores);
        String[] lines = output.split("\n");

        assertEquals(11, lines.length, "exactamente 11 lineas");
        assertTrue(lines[0].startsWith("userIngredients="),    "linea 1: userIngredients");
        assertTrue(lines[1].startsWith("recipes="),            "linea 2: recipes");
        assertTrue(lines[2].startsWith("recipeIngredients="),  "linea 3: recipeIngredients");
        assertTrue(lines[3].startsWith("recipeTimes="),        "linea 4: recipeTimes");
        assertTrue(lines[4].startsWith("recipeServings="),     "linea 5: recipeServings");
        assertTrue(lines[5].startsWith("recipeTags="),         "linea 6: recipeTags");
        assertTrue(lines[6].startsWith("recipeCuisines="),     "linea 7: recipeCuisines");
        assertTrue(lines[7].startsWith("recipeDishTypes="),    "linea 8: recipeDishTypes");
        assertTrue(lines[8].startsWith("recipeHealthScores="), "linea 9: recipeHealthScores");
        assertTrue(lines[9].startsWith("recipeTfIdfScores="),  "linea 10: recipeTfIdfScores");
        assertTrue(lines[10].startsWith("recipeInstructions="),"linea 11: recipeInstructions");

        // recipes= (solo nombres, retro-compatible)
        assertTrue(output.contains("Pasta:garlic,pasta,olive oil"), "ingredientes Pasta");
        assertTrue(output.contains("Salad:tomato,lettuce,olive oil"), "ingredientes Salad");

        // recipeIngredients= (con cantidades)
        assertTrue(output.contains("garlic|3|cloves"),   "garlic 3 cloves");
        assertTrue(output.contains("pasta|200|g"),        "pasta 200g");
        assertTrue(output.contains("olive oil|1.5|tbsp"), "olive oil 1.5tbsp");

        // recipeTfIdfScores=
        assertTrue(output.contains("Pasta:0.7500"),  "score Pasta=0.7500");
        assertTrue(output.contains("Salad:0.3000"),  "score Salad=0.3000");

        // Mapa vacio
        String outputVacio = b.buildOutputMessage("egg", new LinkedHashMap<>(), new LinkedHashMap<>());
        assertFalse(outputVacio.split("\n")[1].contains(";"), "sin recetas no hay ';'");
    }

    // ════════════════════════════════════════════════════════════════════════
    // processInput()
    // ════════════════════════════════════════════════════════════════════════
    static void runProcessInputTests() {
        section("processInput()");
        TextMiningBehaviour b = new TextMiningBehaviour(null, id -> {
            throw new RuntimeException("No debia llamarse al fetcher");
        });

        String outputVacio = b.processInput(
                "{\"userIngredients\": \"egg,rice\", \"recipes\": []}");
        assertFalse(outputVacio.startsWith("error="), "lista vacia no produce error");
        assertTrue(outputVacio.contains("userIngredients=egg,rice"), "userIngredients conservado");
        assertTrue(outputVacio.contains("recipeTfIdfScores="), "contiene recipeTfIdfScores");

        String outputSinUser = b.processInput("{\"recipes\": []}");
        assertFalse(outputSinUser.startsWith("error="), "sin userIngredients no produce error");

        String outputMal = b.processInput("esto no es json {{{");
        assertTrue(outputMal.startsWith("error="), "JSON invalido produce error");
    }

    // ════════════════════════════════════════════════════════════════════════
    // fetchAndExtract() con RecipeFetcher inyectado
    // ════════════════════════════════════════════════════════════════════════
    static void runFetchAndExtractTests() {
        section("fetchAndExtract() con fetcher inyectado");

        String fakeBody = "{"
                + "\"extendedIngredients\": ["
                + "  {\"name\": \"Chicken\", \"amount\": 500.0, \"unit\": \"g\"},"
                + "  {\"name\": \"Rice\",    \"amount\": 2.0,   \"unit\": \"cups\"}"
                + "],"
                + "\"readyInMinutes\": 40, \"servings\": 4,"
                + "\"vegan\": false, \"vegetarian\": false,"
                + "\"glutenFree\": true, \"dairyFree\": true,"
                + "\"cuisines\": [\"Asian\"], \"dishTypes\": [\"main course\"],"
                + "\"healthScore\": 78,"
                + "\"summary\": \"A healthy chicken and rice dish from Asia.\","
                + "\"instructions\": \"Cook rice. Season chicken with soy sauce and grill.\""
                + "}";

        TextMiningBehaviour bOk = new TextMiningBehaviour(null, id -> fakeBody);
        TextMiningBehaviour.RecipeData dataOk = bOk.fetchAndExtract(12345, "Chicken Rice");

        assertEquals(2, dataOk.ingredients.size(),       "2 ingredientes");
        assertTrue(dataOk.ingredients.contains("chicken"), "chicken");
        assertEquals(40,   dataOk.readyInMinutes,         "tiempo=40");
        assertTrue(dataOk.glutenFree,                     "glutenFree=true");
        assertDoubleEquals(500.0, dataOk.ingredientDetails.get(0).amount, 0.001, "chicken 500g");
        assertEquals(1, dataOk.cuisines.size(),           "1 cuisine");
        assertTrue(dataOk.cuisines.contains("asian"),     "cuisine asian");
        assertEquals(78, dataOk.healthScore,              "healthScore=78");
        assertTrue(dataOk.rawText.contains("chicken"),    "rawText contiene chicken");
        assertTrue(dataOk.rawText.contains("rice"),       "rawText contiene rice");

        // Fetcher null -> RecipeData vacio con rawText=""
        TextMiningBehaviour bNull = new TextMiningBehaviour(null, id -> null);
        TextMiningBehaviour.RecipeData dataNull = bNull.fetchAndExtract(99999, "Fallo");
        assertTrue(dataNull.ingredients.isEmpty(), "null -> ingredientes vacios");
        assertEquals("", dataNull.rawText,         "null -> rawText vacio");
        assertEquals(-1, dataNull.healthScore,     "null -> healthScore=-1");

        // Flujo completo con TF-IDF
        String fakeBodyPasta = "{"
                + "\"extendedIngredients\": ["
                + "  {\"name\": \"egg\",   \"amount\": 2.0, \"unit\": \"piece\"},"
                + "  {\"name\": \"flour\", \"amount\": 100.0, \"unit\": \"g\"}"
                + "],"
                + "\"readyInMinutes\": 15, \"servings\": 2,"
                + "\"vegan\": false, \"vegetarian\": true,"
                + "\"glutenFree\": false, \"dairyFree\": false,"
                + "\"cuisines\": [\"French\"], \"dishTypes\": [\"breakfast\"],"
                + "\"healthScore\": 65,"
                + "\"summary\": \"Delicious French crepes made with egg and flour.\","
                + "\"instructions\": \"Mix egg and flour with milk. Cook on a hot pan.\""
                + "}";
        TextMiningBehaviour bFull = new TextMiningBehaviour(null, id -> fakeBodyPasta);
        String inputJson = "{\"userIngredients\": \"egg,flour,butter\","
                + "\"recipes\": [{\"id\": 1001, \"name\": \"Crepes\"}]}";
        String outputFull = bFull.processInput(inputJson);

        assertTrue(outputFull.contains("recipes=Crepes:egg,flour"),
                "flujo completo: recipes=");
        assertTrue(outputFull.contains("recipeIngredients=Crepes:egg|2|piece,flour|100|g"),
                "flujo completo: recipeIngredients=");
        assertTrue(outputFull.contains("recipeCuisines=Crepes:french"),
                "flujo completo: recipeCuisines=");
        assertTrue(outputFull.contains("recipeHealthScores=Crepes:65"),
                "flujo completo: recipeHealthScores=");
        assertTrue(outputFull.contains("recipeTfIdfScores=Crepes:"),
                "flujo completo: recipeTfIdfScores existe");

        // Score TF-IDF debe ser > 0 porque el texto menciona egg y flour
        String tfidfLine = "";
        for (String line : outputFull.split("\n")) {
            if (line.startsWith("recipeTfIdfScores=")) tfidfLine = line;
        }
        String scoreStr = tfidfLine.replace("recipeTfIdfScores=Crepes:", "");
        double score = Double.parseDouble(scoreStr);
        assertTrue(score > 0.0, "TF-IDF score de Crepes > 0 (texto menciona egg y flour)");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Compatibilidad con GraphBehaviour
    // ════════════════════════════════════════════════════════════════════════
    static void runGraphBehaviourCompatibilityTest() {
        section("Compatibilidad con GraphBehaviour");
        TextMiningBehaviour b = makeBehaviour();

        Map<String, TextMiningBehaviour.RecipeData> map = new LinkedHashMap<>();
        map.put("Chicken Rice", makeData("Chicken Rice",
                Arrays.asList("chicken", "rice", "soy sauce"),
                makeDetails(new String[]{"chicken","rice","soy sauce"},
                            new double[]{500.0, 200.0, 30.0},
                            new String[]{"g","g","ml"}),
                30, 2, false, false, true, false,
                Arrays.asList("asian"), Arrays.asList("main course"), 75,
                "chicken and rice with soy sauce"));
        map.put("Pasta Aglio", makeData("Pasta Aglio",
                Arrays.asList("pasta", "garlic", "olive oil"),
                makeDetails(new String[]{"pasta","garlic","olive oil"},
                            new double[]{200.0, 3.0, 2.0},
                            new String[]{"g","cloves","tbsp"}),
                20, 4, false, true, false, false,
                Arrays.asList("italian"), Arrays.asList("lunch"), 68,
                "pasta with garlic and olive oil"));

        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("Chicken Rice", 0.65);
        scores.put("Pasta Aglio",  0.45);

        String output = b.buildOutputMessage("chicken,rice,egg,pasta", map, scores);

        // Simular lo que hace GraphBehaviour
        List<String> userIngredients = new ArrayList<>();
        Map<String, List<String>> recipeGraph = new LinkedHashMap<>();

        for (String line : output.split("\n")) {
            if (line.startsWith("userIngredients=")) {
                for (String p : line.replace("userIngredients=", "").split(","))
                    userIngredients.add(p.trim().toLowerCase());
            } else if (line.startsWith("recipes=")) {
                for (String part : line.replace("recipes=", "").split(";")) {
                    String[] d = part.split(":");
                    if (d.length == 2) {
                        List<String> ings = new ArrayList<>();
                        for (String i : d[1].split(",")) ings.add(i.trim().toLowerCase());
                        recipeGraph.put(d[0].trim(), ings);
                    }
                }
            }
        }

        assertEquals(4, userIngredients.size(), "4 user ingredients");
        assertEquals(2, recipeGraph.size(),      "2 recetas en el grafo");

        List<String> crIngs = recipeGraph.get("Chicken Rice");
        long matches = crIngs.stream().filter(userIngredients::contains).count();
        assertDoubleEquals(2.0 / 3.0, (double) matches / crIngs.size(), 0.001,
                "graphScore Chicken Rice = 2/3");

        // recipes= no debe contener '|' (separador de cantidades)
        String recipesLine = null;
        for (String line : output.split("\n")) {
            if (line.startsWith("recipes=")) { recipesLine = line; break; }
        }
        assertFalse(recipesLine.contains("|"), "recipes= no contiene '|'");

        // recipeTfIdfScores= debe estar presente
        assertTrue(output.contains("recipeTfIdfScores="), "recipeTfIdfScores presente");
        assertTrue(output.contains("Chicken Rice:0.6500"), "score Chicken Rice formateado");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    private static void section(String name) {
        System.out.println("\n--- " + name + " ---");
    }

    private static void assertEquals(Object expected, Object actual, String desc) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            System.out.printf("  FALLO  [%s]%n    esperado: %s%n    obtenido: %s%n",
                    desc, expected, actual);
            failed++;
        } else {
            System.out.printf("  OK     [%s]%n", desc);
            passed++;
        }
    }

    private static void assertTrue(boolean condition, String desc) {
        if (!condition) { System.out.printf("  FALLO  [%s] (esperaba true)%n", desc); failed++; }
        else            { System.out.printf("  OK     [%s]%n", desc); passed++; }
    }

    private static void assertFalse(boolean condition, String desc) {
        assertTrue(!condition, desc);
    }

    private static void assertDoubleEquals(double expected, double actual,
                                           double delta, String desc) {
        if (Math.abs(expected - actual) > delta) {
            System.out.printf("  FALLO  [%s]%n    esperado: %.4f%n    obtenido: %.4f%n",
                    desc, expected, actual);
            failed++;
        } else {
            System.out.printf("  OK     [%s]%n", desc);
            passed++;
        }
    }

    private static TextMiningBehaviour makeBehaviour() {
        return new TextMiningBehaviour(null, id -> null);
    }

    private static List<TextMiningBehaviour.IngredientInfo> makeDetails(
            String[] names, double[] amounts, String[] units) {
        List<TextMiningBehaviour.IngredientInfo> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++)
            list.add(new TextMiningBehaviour.IngredientInfo(names[i], amounts[i], units[i]));
        return list;
    }

    private static TextMiningBehaviour.RecipeData makeData(
            String name,
            List<String> ingredients,
            List<TextMiningBehaviour.IngredientInfo> ingredientDetails,
            int minutes, int servings,
            boolean vegan, boolean vegetarian, boolean glutenFree, boolean dairyFree,
            List<String> cuisines, List<String> dishTypes, int healthScore,
            String rawText) {
        return makeData(name, ingredients, ingredientDetails,
                minutes, servings, vegan, vegetarian, glutenFree, dairyFree,
                cuisines, dishTypes, healthScore, rawText, "");
    }

    private static TextMiningBehaviour.RecipeData makeData(
            String name,
            List<String> ingredients,
            List<TextMiningBehaviour.IngredientInfo> ingredientDetails,
            int minutes, int servings,
            boolean vegan, boolean vegetarian, boolean glutenFree, boolean dairyFree,
            List<String> cuisines, List<String> dishTypes, int healthScore,
            String rawText, String instructions) {
        return new TextMiningBehaviour.RecipeData(
                name, ingredients, ingredientDetails,
                minutes, servings, vegan, vegetarian, glutenFree, dairyFree,
                cuisines, dishTypes, healthScore, rawText, instructions);
    }
}
