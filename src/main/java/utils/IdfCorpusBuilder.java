package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * IdfCorpusBuilder
 *
 * Herramienta de preprocesamiento (se ejecuta UNA SOLA VEZ offline).
 * Lee el corpus RecipeNLG (full_dataset.csv), calcula el IDF de cada
 * término y guarda el resultado en idf_corpus.json.
 *
 * El fichero generado debe colocarse en:
 *   src/main/resources/idf_corpus.json
 *
 * Compilar (desde la raiz del proyecto):
 *   javac -cp libs/gson-2.10.1.jar -d out src/main/java/utils/IdfCorpusBuilder.java
 *
 * Ejecutar (ejemplo):
 *   java -cp out;libs/gson-2.10.1.jar utils.IdfCorpusBuilder
 *   java -cp out;libs/gson-2.10.1.jar utils.IdfCorpusBuilder full_dataset.csv idf_corpus.json
 *   java -cp out;libs/gson-2.10.1.jar utils.IdfCorpusBuilder full_dataset.csv idf_corpus.json 100000
 *
 * Argumentos (todos opcionales):
 *   args[0] = ruta al CSV  (default: full_dataset.csv)
 *   args[1] = ruta salida  (default: idf_corpus.json)
 *   args[2] = max docs     (default: sin limite)
 *
 * Formula IDF con suavizado (identica a TextMiningBehaviour.computeIdf):
 *   IDF(t) = log( (1 + N) / (1 + df(t)) ) + 1
 */
public class IdfCorpusBuilder {

    // ── Stopwords: DEBEN ser identicas a las de TextMiningBehaviour.java ────
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "it", "its", "this", "that", "i", "you", "he", "she", "we",
            "they", "as", "up", "out", "into", "over", "if", "so", "than", "then",
            "per", "also", "very", "just", "your", "about", "some", "more", "all",
            "not", "no", "can", "our", "their", "each", "both", "such", "these",
            "those", "get", "got", "one", "two", "three", "four", "five", "six"
    ));

    public static void main(String[] args) throws IOException {
        String inputPath  = args.length > 0 ? args[0] : "full_dataset.csv";
        String outputPath = args.length > 1 ? args[1] : "idf_corpus.json";
        int    maxDocs    = args.length > 2 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;

        System.out.println("IdfCorpusBuilder iniciado");
        System.out.println("  Entrada : " + inputPath);
        System.out.println("  Salida  : " + outputPath);
        if (maxDocs < Integer.MAX_VALUE) System.out.println("  Limite  : " + maxDocs + " docs");

        Map<String, Integer> df = new HashMap<>();
        long N = 0;

        try (BufferedReader br = new BufferedReader(
                new FileReader(inputPath, StandardCharsets.UTF_8))) {

            // Leer cabecera y encontrar los índices de las columnas que nos interesan
            String header = br.readLine();
            if (header == null) {
                System.err.println("CSV vacio o no encontrado: " + inputPath);
                return;
            }

            String[] cols  = parseCsvLine(header);
            int idxTitle      = findColumn(cols, "title");
            int idxDirections = findColumn(cols, "directions");

            if (idxDirections == -1) {
                System.err.println("Columna 'directions' no encontrada. Cabecera: " + header);
                return;
            }

            System.out.println("Procesando recetas...");
            String line;
            while ((line = br.readLine()) != null && N < maxDocs) {
                String[] fields = parseCsvLine(line);

                String title      = idxTitle != -1 && idxTitle < fields.length
                        ? fields[idxTitle] : "";
                String directions = idxDirections < fields.length
                        ? fields[idxDirections] : "";

                // directions es un array JSON: ["step1","step2",...]
                String directionsText = parseJsonArray(directions);
                String fullText = title + " " + directionsText;

                // Tokenizar y obtener términos únicos del documento
                Set<String> uniqueTerms = tokenize(fullText);
                for (String term : uniqueTerms) {
                    df.merge(term, 1, Integer::sum);
                }

                N++;
                if (N % 100_000 == 0) {
                    System.out.printf("  %,d documentos procesados, %,d terminos unicos%n",
                            N, df.size());
                }
            }
        }

        System.out.printf("%nCorpus procesado: %,d documentos, %,d terminos unicos%n",
                N, df.size());

        // Calcular IDF con la formula suavizada
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : df.entrySet()) {
            double val = Math.log((1.0 + N) / (1.0 + entry.getValue())) + 1.0;
            idf.put(entry.getKey(), val);
        }

        // Guardar como JSON
        Gson gson = new GsonBuilder().create();
        try (FileWriter fw = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
            gson.toJson(idf, fw);
        }

        System.out.println("IDF guardado en: " + outputPath);
        System.out.println("\nPaso siguiente:");
        System.out.println("  Copia " + outputPath
                + " a src/main/resources/idf_corpus.json");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Utilidades
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Tokenizacion identica a TextMiningBehaviour.tokenize().
     * Devuelve un Set (terminos unicos) porque para IDF solo importa
     * si el termino aparece en el documento, no cuantas veces.
     */
    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isEmpty()) return tokens;

        String clean = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        for (String token : clean.split(" ")) {
            if (token.length() >= 3 && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Parsea una linea CSV respetando campos entre comillas con comas internas.
     * RecipeNLG usa comillas dobles como delimitador de campos y
     * "" para escapar comillas dentro de un campo.
     */
    private static String[] parseCsvLine(String line) {
        // Usar una maquina de estados simple para manejar comillas
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Comilla escapada ""
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * Extrae el texto de un array JSON de strings: ["step1","step2"] → "step1 step2"
     * Si el campo no es un array JSON valido, lo devuelve tal cual.
     */
    private static String parseJsonArray(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : array) {
                if (!el.isJsonNull()) {
                    sb.append(el.getAsString()).append(" ");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return json;
        }
    }

    /** Busca el indice de una columna por nombre (insensible a mayusculas). */
    private static int findColumn(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }
}
