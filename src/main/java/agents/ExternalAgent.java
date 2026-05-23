package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import utils.DFUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ExternalAgent
 *
 * Agente de testing/depuracion con GUI Swing que:
 *  1. Consulta el DF para descubrir todos los agentes registrados.
 *  2. Muestra su tipo de servicio y descripcion (almacenada localmente).
 *  3. Permite enviar mensajes de prueba a cada agente y ver la respuesta.
 *
 * Las descripciones y formatos de entrada estan en DESCRIPTIONS / INPUT_FORMATS.
 * Si se anaden nuevos agentes al sistema, basta con aniadirlos en esos mapas.
 *
 * Protocolo por tipo de servicio:
 *   recipe-search / nutrition-analysis  -> REQUEST (FIPA_REQUEST), respuesta directa.
 *   text-mining-service   -> INFORM conv=RECIPE_SEARCH_RESULT  + replyTo=ExternalAgent
 *   ontology-service      -> INFORM conv=TEXT_MINING_RESULT    + replyTo=ExternalAgent
 *   graph-service         -> INFORM conv=ONTOLOGY_RESULT       + replyTo=ExternalAgent
 *   recommendation-service-> INFORM conv=GRAPH_RESULT          + replyTo=ExternalAgent
 */
public class ExternalAgent extends Agent {

    // =========================================================================
    // Conocimiento local sobre cada tipo de servicio
    // =========================================================================

    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();
    private static final Map<String, String> INPUT_FORMATS = new HashMap<>();

    static {
        DESCRIPTIONS.put("recipe-search",
                "Busca recetas en Spoonacular dado un conjunto de ingredientes del usuario.");
        DESCRIPTIONS.put("nutrition-analysis",
                "Obtiene informacion nutricional (calorias, proteinas, grasas, carbohidratos) de una receta mediante su ID de Spoonacular.");
        DESCRIPTIONS.put("text-mining-service",
                "Extrae y enriquece los ingredientes de las recetas encontradas llamando a la API de Spoonacular.");
        DESCRIPTIONS.put("ontology-service",
                "Enriquece los ingredientes de cada receta aplicando sustituciones segun la ontologia de alimentos.");
        DESCRIPTIONS.put("graph-service",
                "Calcula la puntuacion de coincidencia entre los ingredientes del usuario y los de cada receta mediante un grafo.");
        DESCRIPTIONS.put("recommendation-service",
                "Genera el ranking final de recetas ponderando coincidencia de ingredientes, tiempo de preparacion y valor nutricional.");
        DESCRIPTIONS.put("interface-agent",
                "Interfaz grafica principal para que el usuario introduzca ingredientes y reciba recetas recomendadas.");
        DESCRIPTIONS.put("external-agent",
                "Panel de pruebas para testear cada agente del sistema de forma independiente.");

        INPUT_FORMATS.put("recipe-search",
                "ingredients=arroz,pollo,huevo\nquantities=arroz 200g,pollo 300g,huevo 2 unidades\npersons=2\nmaxTime=30\nrestrictions=\npreferences=\nmealType=cualquiera");
        INPUT_FORMATS.put("nutrition-analysis",
                "{\"id\": 716429, \"name\": \"Pasta with Garlic and Oil\"}");
        INPUT_FORMATS.put("text-mining-service",
                "{\"userIngredients\": \"egg,rice,tomato\", \"recipes\": [{\"id\": 716429, \"name\": \"Pasta with Garlic and Oil\"}]}");
        INPUT_FORMATS.put("ontology-service",
                "userIngredients=egg,rice,tomato\nrecipes=PastaGarlic:garlic,pasta,egg,tomato\nrecipeTimes=PastaGarlic:20\nrecipeServings=PastaGarlic:2\nrecipeTags=PastaGarlic:vegetarian\n");
        INPUT_FORMATS.put("graph-service",
                "userIngredients=egg,rice,tomato\nrecipes=PastaGarlic:garlic,pasta,egg,tomato\nrecipeTimes=PastaGarlic:20\nrecipeServings=PastaGarlic:2\nrecipeTags=PastaGarlic:vegetarian\n");
        INPUT_FORMATS.put("recommendation-service",
                "graphResults=\nPastaGarlic;ingredients=[garlic, pasta, egg, tomato];matched=[egg, tomato];missing=[garlic, pasta];matches=2;total=4;graphScore=0.50\nrecipeTimes=PastaGarlic:20\nrecipeServings=PastaGarlic:2\nrecipeTags=PastaGarlic:vegetarian\n");
    }

    // =========================================================================
    // GUI
    // =========================================================================

    private JFrame frame;
    private DefaultListModel<AgentInfo> listModel;
    private JList<AgentInfo> agentList;
    private JLabel lblAgentName;
    private JLabel lblServiceType;
    private JTextArea taDescription;
    private JTextArea taInputHint;
    private JTextArea taInput;
    private JTextArea taResponse;
    private JButton btnSend;
    private JButton btnRefresh;
    private JLabel lblStatus;

    // =========================================================================
    // Ciclo de vida del agente
    // =========================================================================

    @Override
    protected void setup() {
        System.out.println("ExternalAgent iniciado: " + getAID().getName());
        DFUtils.registerService(this, "external-agent");
        addBehaviour(new ResponseListenerBehaviour());
        SwingUtilities.invokeLater(this::buildGUI);
    }

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        if (frame != null) frame.dispose();
        System.out.println("ExternalAgent finalizado.");
    }

    // =========================================================================
    // DF
    // =========================================================================

    private void refreshDFList() {
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                List<AgentInfo> agents = queryDF();
                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    for (AgentInfo info : agents) listModel.addElement(info);
                    setStatus("DF consultado: " + agents.size() + " agentes encontrados.");
                });
            }
        });
    }

    private List<AgentInfo> queryDF() {
        List<AgentInfo> result = new ArrayList<>();
        try {
            DFAgentDescription[] found = DFService.search(this, new DFAgentDescription());
            for (DFAgentDescription dfd : found) {
                AID aid = dfd.getName();
                Iterator<?> services = dfd.getAllServices();
                while (services.hasNext()) {
                    ServiceDescription sd = (ServiceDescription) services.next();
                    String type = sd.getType() != null ? sd.getType() : "unknown";
                    String desc = DESCRIPTIONS.getOrDefault(type, "(sin descripcion)");
                    String fmt  = INPUT_FORMATS.getOrDefault(type, "");
                    result.add(new AgentInfo(aid, type, desc, fmt));
                }
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return result;
    }

    // =========================================================================
    // Construccion de la GUI
    // =========================================================================

    private void buildGUI() {
        frame = new JFrame("ExternalAgent - Panel de Pruebas");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { doDelete(); }
        });
        frame.setSize(1060, 740);
        frame.setLocationRelativeTo(null);

        // ── Panel izquierdo: lista de agentes ─────────────────────────────────
        listModel = new DefaultListModel<>();
        agentList = new JList<>(listModel);
        agentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        agentList.setFixedCellHeight(52);
        agentList.setCellRenderer(new AgentCellRenderer());
        agentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onAgentSelected();
        });

        JScrollPane listScroll = new JScrollPane(agentList);
        listScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 180), 1, true),
                "Agentes en el DF", TitledBorder.LEFT, TitledBorder.TOP));

        btnRefresh = new JButton("Refrescar DF");
        btnRefresh.addActionListener(e -> refreshDFList());

        JPanel leftPanel = new JPanel(new BorderLayout(4, 6));
        leftPanel.setPreferredSize(new Dimension(230, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        leftPanel.add(listScroll, BorderLayout.CENTER);
        leftPanel.add(btnRefresh, BorderLayout.SOUTH);

        // ── Panel derecho ─────────────────────────────────────────────────────
        lblAgentName = new JLabel("Selecciona un agente");
        lblAgentName.setFont(lblAgentName.getFont().deriveFont(Font.BOLD, 15f));

        lblServiceType = new JLabel("");
        lblServiceType.setFont(lblServiceType.getFont().deriveFont(Font.ITALIC, 12f));
        lblServiceType.setForeground(new Color(70, 70, 160));

        taDescription = new JTextArea(3, 40);
        taDescription.setEditable(false);
        taDescription.setLineWrap(true);
        taDescription.setWrapStyleWord(true);
        taDescription.setBackground(new Color(252, 252, 242));
        taDescription.setFont(taDescription.getFont().deriveFont(12f));
        JScrollPane descScroll = new JScrollPane(taDescription);
        descScroll.setBorder(BorderFactory.createTitledBorder("Descripcion"));

        taInputHint = new JTextArea(4, 40);
        taInputHint.setEditable(false);
        taInputHint.setLineWrap(true);
        taInputHint.setFont(new Font("Monospaced", Font.PLAIN, 11));
        taInputHint.setBackground(new Color(235, 245, 255));
        JScrollPane hintScroll = new JScrollPane(taInputHint);
        hintScroll.setBorder(BorderFactory.createTitledBorder("Formato de entrada esperado (solo lectura)"));

        taInput = new JTextArea(7, 40);
        taInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        taInput.setLineWrap(true);
        JScrollPane inputScroll = new JScrollPane(taInput);
        inputScroll.setBorder(BorderFactory.createTitledBorder("Input (edita aqui y pulsa Enviar)"));

        btnSend = new JButton("Enviar al agente");
        btnSend.setEnabled(false);
        btnSend.setBackground(new Color(45, 135, 45));
        btnSend.setForeground(Color.WHITE);
        btnSend.setOpaque(true);
        btnSend.setBorderPainted(false);
        btnSend.setFont(btnSend.getFont().deriveFont(Font.BOLD, 13f));
        btnSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSend.addActionListener(e -> onSendClicked());

        taResponse = new JTextArea(9, 40);
        taResponse.setEditable(false);
        taResponse.setFont(new Font("Monospaced", Font.PLAIN, 11));
        taResponse.setLineWrap(true);
        JScrollPane responseScroll = new JScrollPane(taResponse);
        responseScroll.setBorder(BorderFactory.createTitledBorder("Respuesta recibida"));

        lblStatus = new JLabel("Listo. Haz clic en 'Refrescar DF' para cargar los agentes.");
        lblStatus.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.ITALIC, 11f));

        // Ensamblaje
        JPanel nameTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        nameTypePanel.add(lblAgentName);
        nameTypePanel.add(new JLabel("  -  "));
        nameTypePanel.add(lblServiceType);

        JPanel infoBox = new JPanel();
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        infoBox.add(nameTypePanel);
        infoBox.add(descScroll);
        infoBox.add(hintScroll);

        JPanel sendBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        sendBar.add(btnSend);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.add(sendBar,        BorderLayout.NORTH);
        bottomPanel.add(responseScroll, BorderLayout.CENTER);
        bottomPanel.add(lblStatus,      BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 6));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));
        rightPanel.add(infoBox,     BorderLayout.NORTH);
        rightPanel.add(inputScroll, BorderLayout.CENTER);
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(238);

        frame.add(split);
        frame.setVisible(true);

        refreshDFList();
    }

    // =========================================================================
    // Logica de la GUI
    // =========================================================================

    private void onAgentSelected() {
        AgentInfo info = agentList.getSelectedValue();
        if (info == null) return;

        lblAgentName.setText(info.aid.getLocalName());
        lblServiceType.setText("Tipo: " + info.serviceType);
        taDescription.setText(info.description);
        taInputHint.setText(info.inputFormat.isEmpty() ? "(sin formato especificado)" : info.inputFormat);
        taInput.setText(info.inputFormat);
        taResponse.setText("");

        boolean testable = !info.serviceType.equals("external-agent")
                        && !info.serviceType.equals("interface-agent");
        btnSend.setEnabled(testable);
        setStatus("Seleccionado: " + info.aid.getLocalName()
                + (testable ? "" : "  (no testeable directamente)"));
    }

    private void onSendClicked() {
        AgentInfo info = agentList.getSelectedValue();
        if (info == null) return;
        String input = taInput.getText().trim();
        if (input.isEmpty()) { setStatus("El input esta vacio."); return; }
        btnSend.setEnabled(false);
        taResponse.setText("Esperando respuesta de " + info.aid.getLocalName() + "...");
        setStatus("Mensaje enviado a " + info.aid.getLocalName() + "...");
        addBehaviour(new SendBehaviour(info, input));
    }

    private void setStatus(String msg) { lblStatus.setText(msg); }

    // =========================================================================
    // Inner: datos de un agente del DF
    // =========================================================================

    static class AgentInfo {
        final AID    aid;
        final String serviceType;
        final String description;
        final String inputFormat;

        AgentInfo(AID aid, String serviceType, String description, String inputFormat) {
            this.aid         = aid;
            this.serviceType = serviceType;
            this.description = description;
            this.inputFormat = inputFormat;
        }

        @Override public String toString() { return aid.getLocalName(); }
    }

    static class AgentCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof AgentInfo) {
                AgentInfo info = (AgentInfo) value;
                setText("<html><b>" + info.aid.getLocalName()
                        + "</b><br><font color='#444488'><small>"
                        + info.serviceType + "</small></font></html>");
                setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            }
            return this;
        }
    }

    // =========================================================================
    // Behaviour: enviar mensaje al agente seleccionado
    // =========================================================================

    private class SendBehaviour extends OneShotBehaviour {
        private final AgentInfo target;
        private final String    input;

        SendBehaviour(AgentInfo target, String input) {
            super(ExternalAgent.this);
            this.target = target;
            this.input  = input;
        }

        @Override
        public void action() {
            switch (target.serviceType) {
                case "recipe-search":
                case "nutrition-analysis":
                    sendRequest();
                    break;
                case "text-mining-service":
                    sendInform("RECIPE_SEARCH_RESULT");
                    break;
                case "ontology-service":
                    sendInform("TEXT_MINING_RESULT");
                    break;
                case "graph-service":
                    sendInform("ONTOLOGY_RESULT");
                    break;
                case "recommendation-service":
                    sendInform("GRAPH_RESULT");
                    break;
                default:
                    SwingUtilities.invokeLater(() -> {
                        taResponse.setText("Este agente no admite mensajes de prueba directos.");
                        btnSend.setEnabled(true);
                    });
            }
        }

        private void sendRequest() {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(target.aid);
            msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            msg.setContent(input);
            myAgent.send(msg);
        }

        private void sendInform(String conversationId) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(target.aid);
            msg.setConversationId(conversationId);
            msg.addReplyTo(myAgent.getAID());
            msg.setContent(input);
            myAgent.send(msg);
        }
    }

    // =========================================================================
    // Behaviour: escuchar respuestas
    // =========================================================================

    private class ResponseListenerBehaviour extends CyclicBehaviour {

        private final MessageTemplate MT = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchPerformative(ACLMessage.FAILURE)
        );

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MT);
            if (msg != null) {
                String perf   = msg.getPerformative() == ACLMessage.INFORM ? "INFORM" : "FAILURE";
                String sender = msg.getSender().getLocalName();
                String convId = msg.getConversationId() != null
                        ? "  [conv: " + msg.getConversationId() + "]" : "";
                String display = "[" + perf + " de " + sender + convId + "]\n\n"
                        + msg.getContent();
                SwingUtilities.invokeLater(() -> {
                    taResponse.setText(display);
                    taResponse.setCaretPosition(0);
                    btnSend.setEnabled(true);
                    setStatus("Respuesta recibida de " + sender);
                });
            } else {
                block();
            }
        }
    }
}
