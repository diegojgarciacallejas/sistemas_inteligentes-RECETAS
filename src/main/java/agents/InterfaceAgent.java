package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
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
    public static final String CONV_USER_REQUEST        = "USER_REQUEST";
    public static final String CONV_RECOMMENDATION      = "RECOMMENDATION_RESULT";

    // ── Campos de la GUI ──────────────────────────────────────────────────────
    private JFrame  frame;
    private JTextField tfIngredients;   // "arroz, pollo, huevo, tomate"
    private JTextField tfQuantities;    // "arroz 200g, huevos 2, pollo 300g"
    private JSpinner   spPersons;       // 1-10
    private JSpinner   spMaxTime;       // minutos
    private JTextField tfRestrictions;  // "vegetariano, sin gluten"
    private JTextField tfPreferences;   // "rápido, saludable, sin horno"
    private JComboBox<String> cbMealType; // comida, cena, desayuno, cualquiera
    private JButton    btnSearch;
    private JTextArea  taResults;
    private JLabel     lblStatus;

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    protected void setup() {
        log.info("InterfaceAgent iniciado: " + getAID().getName());

        // 1. Registrar en el DF
        registerInDF();

        // 2. Construir y mostrar la GUI en el hilo de Swing
        SwingUtilities.invokeLater(this::buildGUI);

        // 3. Añadir comportamiento para recibir el ranking final (bloqueante)
        addBehaviour(new WaitForRecommendationBehaviour());
    }

    @Override
    protected void takeDown() {
        // Desregistrar del DF y cerrar GUI al apagar el agente
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
    /*
    private AID findRecipeSearchAgent() {
        return new AID("RecipeSearchAgent", AID.ISLOCALNAME);
    }
    */
    private AID findRecipeSearchAgent() {
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
        frame = new JFrame("🍽️ Recipe Recommender – InterfaceAgent");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { doDelete(); }
        });
        frame.setSize(620, 700);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));

        // ── Panel de entrada ──────────────────────────────────────────────────
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Datos del usuario",
                TitledBorder.LEFT, TitledBorder.TOP));
        inputPanel.setBackground(new Color(250, 250, 245));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(6, 8, 6, 8);
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // Ingredientes
        tfIngredients = new JTextField("arroz, pollo, huevo, tomate");
        addRow(inputPanel, gbc, row++, "1. Ingredientes disponibles:", tfIngredients);

        // Cantidades
        tfQuantities = new JTextField("arroz 200g, huevos 2, pollo 300g");
        addRow(inputPanel, gbc, row++, "2. Cantidades disponibles:", tfQuantities);

        // Número de personas
        spPersons = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        addRow(inputPanel, gbc, row++, "3. Número de personas:", spPersons);

        // Tiempo máximo
        spMaxTime = new JSpinner(new SpinnerNumberModel(30, 5, 180, 5));
        addRow(inputPanel, gbc, row++, "4. Tiempo máximo (minutos):", spMaxTime);

        // Restricciones
        tfRestrictions = new JTextField("vegetariano, sin gluten");
        addRow(inputPanel, gbc, row++, "5. Restricciones alimentarias:", tfRestrictions);

        // Preferencias
        tfPreferences = new JTextField("rápido, saludable, sin horno");
        addRow(inputPanel, gbc, row++, "6. Preferencias:", tfPreferences);

        // Tipo de comida
        cbMealType = new JComboBox<>(new String[]{"cualquiera", "desayuno", "comida", "cena", "snack"});
        addRow(inputPanel, gbc, row++, "7. Tipo de comida (opcional):", cbMealType);

        // Botón
        btnSearch = new JButton("🔍 Buscar recetas");
        btnSearch.setBackground(new Color(70, 130, 180));
        btnSearch.setForeground(Color.WHITE);
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
        taResults.setText("Los resultados aparecerán aquí...");
        JScrollPane scrollPane = new JScrollPane(taResults);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Recetas recomendadas",
                TitledBorder.LEFT, TitledBorder.TOP));

        // ── Barra de estado ───────────────────────────────────────────────────
        lblStatus = new JLabel("Listo.");
        lblStatus.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.ITALIC));

        // ── Ensamblaje ────────────────────────────────────────────────────────
        frame.add(inputPanel,  BorderLayout.NORTH);
        frame.add(scrollPane,  BorderLayout.CENTER);
        frame.add(lblStatus,   BorderLayout.SOUTH);
        frame.setVisible(true);
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

        // Construir el contenido del mensaje con los datos del usuario
        String content = buildRequestContent();
        log.info("Solicitud construida:\n" + content);

        // Enviar en un comportamiento one-shot para no bloquear la GUI
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                AID recipeSearchAgent = findRecipeSearchAgent();

                if (recipeSearchAgent == null) {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("❌ RecipeSearchAgent no encontrado en el DF.");
                        taResults.setText("Error: RecipeSearchAgent no disponible.\n" +
                                "Asegúrate de que esté iniciado antes de buscar.");
                        btnSearch.setEnabled(true);
                    });
                    return;
                }

                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(recipeSearchAgent);
                msg.setConversationId(CONV_USER_REQUEST);
                msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                msg.setContent(content);
                msg.setLanguage("UTF-8");
                send(msg);

                log.info("USER_REQUEST enviado a " + recipeSearchAgent.getName());
                SwingUtilities.invokeLater(() -> setStatus("✅ Solicitud enviada. Esperando resultados..."));
            }
        });
    }

    /**
     * Serializa los datos del formulario en un formato clave=valor
     * que los demás agentes pueden parsear fácilmente.
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
    private String buildRequestContent() {
        return "ingredients=" + tfIngredients.getText().trim() + "\n"
                + "quantities="  + tfQuantities.getText().trim()  + "\n"
                + "persons="     + spPersons.getValue()           + "\n"
                + "maxTime="     + spMaxTime.getValue()           + "\n"
                + "restrictions="+ tfRestrictions.getText().trim()+ "\n"
                + "preferences=" + tfPreferences.getText().trim() + "\n"
                + "mealType="    + cbMealType.getSelectedItem();
    }

    // =========================================================================
    // Comportamiento bloqueante: esperar ranking del RecommendationAgent
    // =========================================================================

    /**
     * Comportamiento cíclico con filtro de mensajes en modo BLOQUEANTE.
     * Espera mensajes ACL con conversationId = RECOMMENDATION_RESULT.
     */
    private class WaitForRecommendationBehaviour extends CyclicBehaviour {

        // Filtro: solo mensajes con el conversationId correcto
        private final MessageTemplate MT = MessageTemplate.MatchConversationId(CONV_RECOMMENDATION);

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MT);

            if (msg != null) {
                log.info("RECOMMENDATION_RESULT recibido de " + msg.getSender().getName());
                String ranking = msg.getContent();
                SwingUtilities.invokeLater(() -> displayResults(ranking));
            } else {
                // Modo bloqueante: el agente se suspende hasta recibir un mensaje
                block();
            }
        }
    }

    // =========================================================================
    // Mostrar resultados
    // =========================================================================

    /**
     * Parsea y muestra el ranking recibido del RecommendationAgent.
     *
     * Formato esperado (acordado con RecommendationAgent):
     *   RANK|recipeName|graphScore|finalScore
     *   1|Arroz con pollo|0.85|0.78
     *   2|Tortilla española|0.70|0.65
     *   ...
     */
    private void displayResults(String content) {
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

            // Si la línea sigue el formato RANK|name|graphScore|finalScore
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
                    sb.append("     " + scoreBar(finalScore) + "\n\n");
                } else {
                    // Línea no parseable → mostrar tal cual
                    sb.append("  ").append(line).append("\n");
                }
            } else {
                // Cabecera u otro texto del RecommendationAgent
                sb.append(line).append("\n");
            }
        }

        sb.append("\n═══════════════════════════════════════════════════\n");
        taResults.setText(sb.toString());
        taResults.setCaretPosition(0);
        setStatus("✅ " + (lines.length) + " recetas recibidas.");
    }

    /** Genera una barra visual de puntuación. Ejemplo: ████████░░ 80% */
    private String scoreBar(double score) {
        int filled = (int) Math.round(score * 10);
        return "█".repeat(filled) + "░".repeat(10 - filled)
                + String.format(" %.0f%%", score * 100);
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    private void setStatus(String msg) {
        lblStatus.setText(msg);
    }
}