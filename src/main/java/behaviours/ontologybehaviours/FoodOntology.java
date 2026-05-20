package behaviours.ontologybehaviours;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.*;

public class FoodOntology {

    private static OWLOntology ontology;
    private static OWLOntologyManager manager;
    private static boolean loaded = false;

    private static final Map<String, List<String>> substituteCache = new HashMap<>();
    private static final Map<String, FoodCategory> categoryCache   = new HashMap<>();

    private static final Map<String, List<String>> FALLBACK_SUBSTITUTIONS = new HashMap<>();

    static {
        loadOntology();

        FALLBACK_SUBSTITUTIONS.put("cream",       Arrays.asList("milk", "yogurt"));
        FALLBACK_SUBSTITUTIONS.put("butter",      Arrays.asList("olive oil", "oil"));
        FALLBACK_SUBSTITUTIONS.put("chicken",     Arrays.asList("turkey", "tofu"));
        FALLBACK_SUBSTITUTIONS.put("beef",        Arrays.asList("pork", "lamb"));
        FALLBACK_SUBSTITUTIONS.put("pasta",       Arrays.asList("noodles", "rice"));
        FALLBACK_SUBSTITUTIONS.put("milk",        Arrays.asList("yogurt", "cream"));
        FALLBACK_SUBSTITUTIONS.put("parmesan",    Arrays.asList("cheese", "pecorino"));
        FALLBACK_SUBSTITUTIONS.put("mozzarella",  Arrays.asList("cheese", "ricotta"));
        FALLBACK_SUBSTITUTIONS.put("lemon",       Arrays.asList("lime", "vinegar"));
        FALLBACK_SUBSTITUTIONS.put("egg",         Arrays.asList("flax egg", "chia egg"));
        FALLBACK_SUBSTITUTIONS.put("flour",       Arrays.asList("almond flour", "oat flour"));
        FALLBACK_SUBSTITUTIONS.put("sugar",       Arrays.asList("honey", "maple syrup"));
        FALLBACK_SUBSTITUTIONS.put("soy sauce",   Arrays.asList("tamari", "coconut aminos"));
        FALLBACK_SUBSTITUTIONS.put("wine",        Arrays.asList("broth", "grape juice"));
        FALLBACK_SUBSTITUTIONS.put("breadcrumbs", Arrays.asList("oats", "crushed crackers"));
    }

    private static void loadOntology() {
        new Thread(() -> {
            try {
                System.out.println("FoodOntology: cargando foodon.owl...");
                manager = OWLManager.createOWLOntologyManager();
                File file = new File("src/main/java/utils/foodon.owl");
                ontology = manager.loadOntologyFromOntologyDocument(file);
                loaded = true;
                System.out.println("FoodOntology: ontología cargada con "
                        + ontology.getClassesInSignature().size() + " clases.");
            } catch (Exception e) {
                System.err.println("FoodOntology: no se pudo cargar foodon.owl — "
                        + "usando fallback. Error: " + e.getMessage());
                loaded = false;
            }
        }, "foodon-loader").start();
    }

    // ── API pública ─────────────────────────────────────────────────────────

    public static FoodCategory getCategory(String ingredient) {
        String key = ingredient.toLowerCase().trim();
        if (categoryCache.containsKey(key)) return categoryCache.get(key);

        FoodCategory category = FoodCategory.OTHER;
        if (loaded) {
            category = searchCategoryInOntology(key);
        }

        categoryCache.put(key, category);
        return category;
    }

    public static List<String> findSubstitutes(
            String missingIngredient, List<String> userIngredients) {

        String key = missingIngredient.toLowerCase().trim();

        List<String> candidates = new ArrayList<>();
        if (loaded) {
            candidates = findSubstitutesInOntology(key);
        }

        if (candidates.isEmpty()) {
            candidates = FALLBACK_SUBSTITUTIONS.getOrDefault(key, new ArrayList<>());
        }

        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (userIngredients.contains(candidate.toLowerCase().trim())) {
                result.add(candidate);
            }
        }
        return result;
    }

    public static List<SubstitutionRule> getSubstitutionRules(String ingredient) {
        List<SubstitutionRule> rules = new ArrayList<>();
        List<String> substitutes = FALLBACK_SUBSTITUTIONS.getOrDefault(
                ingredient.toLowerCase().trim(), new ArrayList<>());
        for (String sub : substitutes) {
            rules.add(new SubstitutionRule(ingredient, sub, 0.7));
        }
        return rules;
    }

    // ── Búsqueda en FoodOn ──────────────────────────────────────────────────

    private static FoodCategory searchCategoryInOntology(String ingredient) {
        if (ontology == null) return FoodCategory.OTHER;

        String query = ingredient.toLowerCase();

        for (OWLClass cls : ontology.getClassesInSignature()) {
            String label = getLabel(cls).toLowerCase();
            if (label.equals(query) || label.contains(query)) {
                return inferCategory(cls);
            }
        }
        return FoodCategory.OTHER;
    }

    private static List<String> findSubstitutesInOntology(String ingredient) {
        List<String> result = new ArrayList<>();
        if (ontology == null) return result;

        OWLClass targetClass = findClass(ingredient);
        if (targetClass == null) return result;

        Set<OWLClassExpression> superClasses = new HashSet<>();
        for (OWLSubClassOfAxiom ax : ontology.getSubClassAxiomsForSubClass(targetClass)) {
            superClasses.add(ax.getSuperClass());
        }

        for (OWLClassExpression superClass : superClasses) {
            if (superClass.isAnonymous()) continue;
            OWLClass parent = superClass.asOWLClass();
            for (OWLSubClassOfAxiom ax : ontology.getSubClassAxiomsForSuperClass(parent)) {
                OWLClassExpression sibling = ax.getSubClass();
                if (sibling.isAnonymous()) continue;
                OWLClass siblingClass = sibling.asOWLClass();
                if (siblingClass.equals(targetClass)) continue;
                String label = getLabel(siblingClass);
                if (!label.isEmpty() && !label.equalsIgnoreCase(ingredient)) {
                    result.add(label.toLowerCase());
                    if (result.size() >= 5) return result;
                }
            }
        }
        return result;
    }

    private static OWLClass findClass(String ingredient) {
        String query = ingredient.toLowerCase();
        for (OWLClass cls : ontology.getClassesInSignature()) {
            String label = getLabel(cls).toLowerCase();
            if (label.equals(query)) return cls;
        }
        for (OWLClass cls : ontology.getClassesInSignature()) {
            String label = getLabel(cls).toLowerCase();
            if (label.contains(query)) return cls;
        }
        return null;
    }

    private static String getLabel(OWLClass cls) {
        if (ontology == null) return "";

        for (OWLAnnotationAssertionAxiom ax : ontology.getAnnotationAssertionAxioms(cls.getIRI())) {
            if (ax.getProperty().isLabel()) {
                OWLAnnotationValue val = ax.getValue();
                if (val instanceof OWLLiteral) {
                    return ((OWLLiteral) val).getLiteral();
                }
            }
        }
        String iri = cls.getIRI().getFragment();
        return iri != null ? iri.replace("_", " ") : "";
    }

    private static FoodCategory inferCategory(OWLClass cls) {
        String label    = getLabel(cls).toLowerCase();
        String iri      = cls.getIRI().toString().toLowerCase();
        String combined = label + " " + iri;

        if (combined.contains("meat") || combined.contains("fish")
                || combined.contains("egg") || combined.contains("legume")
                || combined.contains("protein") || combined.contains("poultry")) {
            return FoodCategory.PROTEIN;
        }
        if (combined.contains("dairy") || combined.contains("milk")
                || combined.contains("cheese") || combined.contains("cream")) {
            return FoodCategory.DAIRY;
        }
        if (combined.contains("cereal") || combined.contains("grain")
                || combined.contains("bread") || combined.contains("pasta")
                || combined.contains("rice") || combined.contains("flour")) {
            return FoodCategory.CARBOHYDRATE;
        }
        if (combined.contains("vegetable") || combined.contains("herb")
                || combined.contains("mushroom")) {
            return FoodCategory.VEGETABLE;
        }
        if (combined.contains("fruit") || combined.contains("berry")) {
            return FoodCategory.FRUIT;
        }
        if (combined.contains("oil") || combined.contains("fat")
                || combined.contains("butter")) {
            return FoodCategory.FAT;
        }
        if (combined.contains("spice") || combined.contains("seasoning")
                || combined.contains("condiment")) {
            return FoodCategory.SPICE;
        }
        return FoodCategory.OTHER;
    }
}