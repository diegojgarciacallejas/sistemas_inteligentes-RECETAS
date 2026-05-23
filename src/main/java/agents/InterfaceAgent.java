package agents;

import behaviours.interfacebehaviours.InterfaceAgentBehaviours;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.logging.Logger;

/**
 * InterfaceAgent
 *
 * Responsabilidades:
 *  1. Mostrar una GUI (Swing) para que el usuario introduzca sus datos.
 *  2. Enviar la solicitud al RecipeSearchAgent con conversationId = USER_REQUEST.
 *  3. Esperar (filtro bloqueante) la respuesta del RecommendationAgent
 *     con conversationId = RECOMMENDATION_RESULT.
 *  4. Mostrar el ranking de recetas recomendadas.
 */
public class InterfaceAgent extends Agent {

    private static final Logger log = Logger.getLogger(InterfaceAgent.class.getName());

    // ── Conversation IDs acordados con el equipo ──────────────────────────────
    public static final String CONV_USER_REQUEST   = "USER_REQUEST";
    public static final String CONV_RECOMMENDATION = "RECOMMENDATION_RESULT";

    // ── 100 ingredientes más comunes (desplegable) ────────────────────────────
    static final String[] COMMON_INGREDIENTS = {
        "Aceite de coco", "Aceite de oliva", "Aceite vegetal", "Aguacate",
        "Ajo", "Albahaca", "Alubias", "Arroz", "Arroz integral", "Atún",
        "Avena", "Azúcar", "Bacalao", "Bacon", "Berenjena", "Brócoli",
        "Cacahuetes", "Calabacín", "Calabaza", "Caldo de pollo",
        "Caldo de verduras", "Canela", "Carne de cerdo", "Carne de res",
        "Carne picada", "Cebolla", "Cebolla morada", "Champiñones",
        "Chorizo", "Cilantro", "Coliflor", "Comino", "Curry en polvo",
        "Cuscús", "Espárragos", "Espinacas", "Fideos", "Fresas",
        "Gambas", "Garbanzos", "Guisantes", "Harina", "Huevo",
        "Jamón", "Jengibre", "Judías verdes", "Laurel", "Leche",
        "Leche de coco", "Lentejas", "Lima", "Limón", "Maíz",
        "Mango", "Mantequilla", "Manzana", "Mayonesa", "Melocotón",
        "Miel", "Mostaza", "Mozzarella", "Naranja", "Nata",
        "Nueces", "Orégano", "Pan", "Pan rallado", "Parmesano",
        "Pasta", "Patata", "Patata dulce", "Pavo", "Pepino",
        "Perejil", "Pimienta negra", "Pimentón", "Pimiento rojo",
        "Pimiento verde", "Piña", "Plátano", "Pollo", "Puerro",
        "Quinoa", "Remolacha", "Romero", "Sal", "Salmón",
        "Salsa de soja", "Salsa de tomate", "Sésamo", "Ternera",
        "Tofu", "Tomate", "Tomate en lata", "Tomillo", "Uvas",
        "Vinagre", "Yogur", "Zanahoria"
    };

    // ── Campos de la GUI ──────────────────────────────────────────────────────
    JFrame             frame;
    // Cada fila: Object[]{JComboBox<String> nombre, JTextField cantidad}
    private final java.util.List<Object[]> ingredientRows = new java.util.ArrayList<>();
    JSpinner           spPersons;       // 1-10
    JSpinner           spMaxTime;       // minutos
    JTextField         tfRestrictions;  // "vegetariano, sin gluten"
    JTextField         tfPreferences;   // "rápido, saludable, sin horno"
    JComboBox<String>  cbMealType;      // comida, cena, desayuno, cualquiera
    JButton            btnSearch;
    JTextArea          taResults;
    JLabel             lblStatus;

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    protected void setup() {
        log.info("InterfaceAgent iniciado: " + getAID().getName());

        // 1. Registrar en el DF
        registerInDF();

        // 2. Construir y mostrar la GUI en el hilo de Swing
        SwingUtilities.invokeLater(this::buildGUI);

        // 3. Añadir comportamiento para recibir el ranking final (bloqueante)
        addBehaviour(new InterfaceAgentBehaviours.WaitForRecommendationBehaviour(this));
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        if (frame != null) frame.dispose();
        log.info("InterfaceAgent finalizado.");
        System.out.println(getLocalName() + " finalizado.");
    }

    // =========================================================================
    // Directory Facilitator
    // =========================================================================

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("interface-agent");
        sd.setName("RecipeInterfaceService");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            log.info("InterfaceAgent registrado en el DF.");
        } catch (FIPAException e) {
            log.severe("Error al registrar en el DF: " + e.getMessage());
        }
    }

    /**
     * Busca el AID del RecipeSearchAgent en el DF.
     * Devuelve null si no está disponible.
     */
    public AID findRecipeSearchAgent() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("recipe-search");
        template.addServices(sd);

        try {
            DFAgentDescription[] results = DFService.search(this, template);
            log.warning("DF search returned " + results.length + " results for recipe-search");
            if (results.length > 0) {
                return results[0].getName();
            }
        } catch (FIPAException e) {
            log.warning("Error buscando RecipeSearchAgent en el DF: " + e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // GUI
    // =========================================================================

    private void buildGUI() {
        frame = new JFrame("NUTRIAGENT");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { doDelete(); }
        });
        frame.setSize(750, 850);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));

        // ── Panel de entrada ──────────────────────────────────────────────────
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 160, 80), 1, true),
                "Datos del usuario", TitledBorder.LEFT, TitledBorder.TOP));
        inputPanel.setBackground(new Color(235, 245, 235));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(6, 8, 6, 8);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        JPanel ingredientsPanel = new JPanel();
        ingredientsPanel.setLayout(new BoxLayout(ingredientsPanel, BoxLayout.Y_AXIS));
        ingredientsPanel.setBackground(new Color(235, 245, 235));

        // Cabecera de columnas
        JPanel header = new JPanel(new GridLayout(1, 3, 6, 0));
        header.setBackground(new Color(235, 245, 235));
        header.add(new JLabel("Ingrediente  ▾  (puedes escribir uno personalizado)"));
        header.add(new JLabel("Cantidad  (ej: 200g, 3 unidades)"));
        header.add(new JLabel(""));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        ingredientsPanel.add(header);

        // Botón añadir
        JButton btnAdd = new JButton("+ Añadir ingrediente");
        btnAdd.addActionListener(e -> {
            addIngredientRow(ingredientsPanel, btnAdd, null, null);
            ingredientsPanel.revalidate();
            ingredientsPanel.repaint();
        });

        ingredientsPanel.add(btnAdd);

        // Filas iniciales con valores por defecto
        addIngredientRow(ingredientsPanel, btnAdd, "arroz",  "200g");
        addIngredientRow(ingredientsPanel, btnAdd, "pollo",  "300g");
        addIngredientRow(ingredientsPanel, btnAdd, "huevo",  "2 unidades");
        addIngredientRow(ingredientsPanel, btnAdd, "tomate", "1");



        // Añádelo al inputPanel ocupando las 2 columnas
        gbc.gridy     = row++;
        gbc.gridx     = 0;
        gbc.gridwidth = 2;
        gbc.fill      = GridBagConstraints.HORIZONTAL;
        inputPanel.add(ingredientsPanel, gbc);
        gbc.gridwidth = 1; // restaurar

        spPersons = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        spMaxTime = new JSpinner(new SpinnerNumberModel(30, 5, 180, 5));

        JPanel timePersonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        timePersonsPanel.setBackground(new Color(235, 245, 235));
        timePersonsPanel.add(new JLabel("Nº personas:"));
        timePersonsPanel.add(spPersons);
        timePersonsPanel.add(Box.createHorizontalStrut(60));
        timePersonsPanel.add(new JLabel("Tiempo máximo (min):"));
        timePersonsPanel.add(spMaxTime);

        gbc.gridy     = row++;
        gbc.gridx     = 0;
        gbc.gridwidth = 2;
        gbc.fill      = GridBagConstraints.HORIZONTAL;
        inputPanel.add(timePersonsPanel, gbc);
        gbc.gridwidth = 1;

        tfRestrictions = new JTextField("vegetariano, sin gluten");
        addRow(inputPanel, gbc, row++, "Restricciones alimentarias:", tfRestrictions);

        tfPreferences = new JTextField("rápido, saludable, sin horno");
        addRow(inputPanel, gbc, row++, "Preferencias:", tfPreferences);

        cbMealType = new JComboBox<>(new String[]{"cualquiera", "desayuno", "comida", "cena", "snack"});
        addRow(inputPanel, gbc, row++, "Tipo de comida (opcional):", cbMealType);

        btnSearch = new JButton("🔍 Buscar recetas");
        btnSearch.setBackground(new Color(80, 160, 80));
        btnSearch.setForeground(Color.WHITE);
        btnSearch.setOpaque(true);
        btnSearch.setBorderPainted(false);
        btnSearch.setForeground(Color.BLACK);
        btnSearch.setFont(btnSearch.getFont().deriveFont(Font.BOLD, 13f));
        btnSearch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSearch.addActionListener(e -> onSearchClicked());

        gbc.gridy     = row;
        gbc.gridx     = 0;
        gbc.gridwidth = 2;
        gbc.fill      = GridBagConstraints.NONE;
        gbc.anchor    = GridBagConstraints.CENTER;
        inputPanel.add(btnSearch, gbc);

        // ── Panel de resultados ───────────────────────────────────────────────
        taResults = new JTextArea(12, 50);
        taResults.setEditable(false);
        taResults.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(taResults);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 160, 80), 1, true),
                "Resultados"));

        lblStatus = new JLabel("Listo.");
        lblStatus.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.ITALIC));

        // ── Ensamblaje ────────────────────────────────────────────────────────
        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(lblStatus,  BorderLayout.SOUTH);
        frame.setVisible(true);
        frame.setTitle("NUTRIAGENT");
    }

    /** Añade una fila etiqueta + componente al GridBagLayout. */
    private void addRow(JPanel panel, GridBagConstraints gbc, int row,
                        String label, JComponent field) {
        gbc.gridy     = row;
        gbc.gridwidth = 1;
        gbc.fill      = GridBagConstraints.NONE;
        gbc.anchor    = GridBagConstraints.WEST;
        gbc.weightx   = 0;
        gbc.gridx     = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx   = 1;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
    }

    // =========================================================================
    // Acción del botón Buscar
    // =========================================================================

    private void onSearchClicked() {
        btnSearch.setEnabled(false);
        setStatus("Buscando recetas...");
        taResults.setText("Procesando solicitud, por favor espera...");

        String content = buildRequestContent();
        log.info("Solicitud construida:\n" + content);

        // Delegar en el behaviour para no bloquear la GUI
        addBehaviour(new InterfaceAgentBehaviours.SendRequestBehaviour(this, content));
    }

    /**
     * Serializa los datos del formulario en formato clave=valor.
     *
     * Formato:
     *   ingredients=arroz,pollo,huevo,tomate
     *   quantities=arroz 200g,huevos 2,pollo 300g
     *   persons=2
     *   maxTime=30
     *   restrictions=vegetariano,sin gluten
     *   preferences=rápido,saludable,sin horno
     *   mealType=comida
     */
    String buildRequestContent() {
        StringBuilder ingredients = new StringBuilder();
        StringBuilder quantities  = new StringBuilder();
        for (Object[] pair : ingredientRows) {
            @SuppressWarnings("unchecked")
            JComboBox<String> cb = (JComboBox<String>) pair[0];
            JTextField        tf = (JTextField)        pair[1];
            Object sel = cb.getSelectedItem();
            String n   = (sel != null ? sel.toString() : "").trim();
            String q   = tf.getText().trim();
            if (!n.isEmpty()) {
                if (ingredients.length() > 0) { ingredients.append(","); quantities.append(","); }
                ingredients.append(n);
                quantities.append(n).append(" ").append(q);
            }
        }
        return "ingredients=" + ingredients                    + "\n"
                + "quantities="  + quantities                     + "\n"
                + "persons="     + spPersons.getValue()           + "\n"
                + "maxTime="     + spMaxTime.getValue()            + "\n"
                + "restrictions="+ tfRestrictions.getText().trim() + "\n"
                + "preferences=" + tfPreferences.getText().trim()  + "\n"
                + "mealType="    + cbMealType.getSelectedItem();
    }

    // =========================================================================
    // Mostrar resultados  (llamado desde los behaviours)
    // =========================================================================

    /**
     * Parsea y muestra el ranking recibido del RecommendationAgent.
     *
     * Formato esperado:
     *   RANK|recipeName|graphScore|finalScore
     *   1|Arroz con pollo|0.85|0.78
     *   2|Tortilla española|0.70|0.65
     *   ...
     */
    public void displayResults(String content) {
        btnSearch.setEnabled(true);

        if (content == null || content.isBlank()) {
            taResults.setText("No se recibieron resultados.");
            setStatus("Sin resultados.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("          🏆  RECETAS RECOMENDADAS PARA TI         \n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.matches("\\d+\\|.*")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String rank       = parts[0];
                    String name       = parts[1];
                    double graphScore = Double.parseDouble(parts[2]);
                    double finalScore = Double.parseDouble(parts[3]);

                    sb.append(String.format("  %s. %-35s\n", rank, name));
                    sb.append(String.format("     Coincidencia ingredientes : %.0f%%\n", graphScore * 100));
                    sb.append(String.format("     Puntuación final          : %.0f%%\n", finalScore * 100));
                    sb.append("     ").append(scoreBar(finalScore)).append("\n\n");
                } else {
                    sb.append("  ").append(line).append("\n");
                }
            } else {
                sb.append(line).append("\n");
            }
        }

        sb.append("\n═══════════════════════════════════════════════════\n");
        taResults.setText(sb.toString());
        taResults.setCaretPosition(0);
        setStatus("✅ " + lines.length + " recetas recibidas.");
    }

    /** Genera una barra visual de puntuación. Ejemplo: ████████░░ 80% */
    private String scoreBar(double score) {
        int filled = (int) Math.round(score * 10);
        return "█".repeat(filled) + "░".repeat(10 - filled)
                + String.format(" %.0f%%", score * 100);
    }

    private void addIngredientRow(JPanel panel, JButton btnAdd,
                                  String name, String qty) {
        // ── Desplegable de ingredientes (editable: permite escribir uno personalizado) ──
        JComboBox<String> cbName = new JComboBox<>(COMMON_INGREDIENTS);
        cbName.setEditable(true);   // el usuario puede escribir un ingrediente que no esté en la lista
        cbName.setFont(cbName.getFont().deriveFont(12f));
        cbName.setBackground(Color.WHITE);
        if (name != null && !name.isEmpty()) {
            // Busca coincidencia en la lista (case-insensitive)
            boolean found = false;
            for (int i = 0; i < cbName.getItemCount(); i++) {
                if (cbName.getItemAt(i).equalsIgnoreCase(name)) {
                    cbName.setSelectedIndex(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                // No está en la lista: lo escribe en el campo editable
                cbName.setSelectedItem(name);
            }
        }

        JTextField tfQty = new JTextField(qty != null ? qty : "");
        Object[]   pair  = {cbName, tfQty};
        ingredientRows.add(pair);

        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        row.setBackground(new Color(235, 245, 235));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JButton btnRemove = new JButton("✕");
        btnRemove.setBackground(new Color(200, 50, 50));
        btnRemove.setForeground(Color.WHITE);
        btnRemove.setOpaque(true);
        btnRemove.setBorder(BorderFactory.createLineBorder(new Color(160, 30, 30), 1, true));
        btnRemove.setFocusPainted(false);
        btnRemove.setFont(btnRemove.getFont().deriveFont(Font.BOLD, 12f));
        btnRemove.setMargin(new Insets(2, 6, 2, 6));
        btnRemove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRemove.addActionListener(e -> {
            ingredientRows.remove(pair);
            panel.remove(row);
            panel.revalidate();
            panel.repaint();
        });

        row.add(cbName);
        row.add(tfQty);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnPanel.setBackground(new Color(235, 245, 235));
        Dimension btnSize = new Dimension(33, 24);
        btnRemove.setPreferredSize(btnSize);
        btnRemove.setMinimumSize(btnSize);
        btnRemove.setMaximumSize(btnSize);
        btnPanel.add(btnRemove);
        row.add(btnPanel);

        // Insertar antes del botón "Añadir"
        int idx = panel.getComponentCount() - 1;
        panel.add(row, idx);
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    public void setStatus(String msg) {
        lblStatus.setText(msg);
    }

    public void enableSearch() {
        btnSearch.setEnabled(true);
    }
}
