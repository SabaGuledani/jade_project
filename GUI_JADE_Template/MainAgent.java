import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.PrintStream;
import java.util.ArrayList;

public class MainAgent extends Agent {

    public GUI gui;
    private AID[] playerAgents;
    private GameParametersStruct parameters = new GameParametersStruct();
    private int[] payoff;
    private String result;
    

    @Override
    protected void setup() {
        gui = new GUI(this);
        System.setOut(new PrintStream(gui.getLoggingOutputStream()));

        updatePlayers();
        gui.logLine("Agent " + getAID().getName() + " is ready.");
        gui.setLeftPanelExtraInformation(parameters.R, parameters.F);


    }


    public int updatePlayers() {
        gui.logLine("Updating player list");
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Player");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                gui.logLine("Found " + result.length + " players");
            }
            playerAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                playerAgents[i] = result[i].getName();
            }
        } catch (FIPAException fe) {
            gui.logLine(fe.getMessage());
        }
        //Provisional
        String[] playerNames = new String[playerAgents.length];
        for (int i = 0; i < playerAgents.length; i++) {
            playerNames[i] = playerAgents[i].getName();
            gui.addRow(playerAgents[i].getName(), "0","0","0", "0");
        }
        gui.setPlayersUI(playerNames);

        return 0;
    }

    public int newGame() {
        addBehaviour(new GameManager());
        return 0;
    }

    /**
     * In this behavior this agent manages the course of a match during all the
     * rounds.
     */
    private class GameManager extends SimpleBehaviour {

        @Override
        public void action() {
            //Assign the IDs
            ArrayList<PlayerInformation> players = new ArrayList<>();
            int lastId = 0;
            for (AID a : playerAgents) {
                players.add(new PlayerInformation(a, lastId++));
            }

            //Initialize (inform ID)
            for (PlayerInformation player : players) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setContent("Id#" + player.id + "#" + parameters.N + "," + parameters.R + "," + parameters.F);
                msg.addReceiver(player.aid);
                send(msg);
            }
            //Organize the matches
            for(int current_round = 1; current_round < parameters.R; current_round++){
                for (int i = 0; i < players.size(); i++) {
                    for (int j = i + 1; j < players.size(); j++) { //too lazy to think, let's see if it works or it breaks
                        playGame(players.get(i), players.get(j));
                    }
                }
                for (int i = 0; i < players.size(); i++){
//                    gui.logLine(players.get(i).aid);
                    String final_decision;
                    roundOver(players.get(i));
                    ACLMessage decision = blockingReceive();
                    processBuyOrSell(decision);

                }

            }

        }
        private void processBuyOrSell(ACLMessage decisionMessage){

            gui.logLine("Main Received decision " + decisionMessage.getContent() + " from " + decisionMessage.getSender().getName());
            String[] decisionSplit = decisionMessage.getContent().split("#");
            if
        }

        public static int[] getPayoff(String player1, String player2) {
            // Define payoffs as per the given rules
            int[][] payoffMatrix = {
                    {2, 0}, // C vs C and C vs D
                    {4, 0}  // D vs C and D vs D
            };

            if (player1.equals("C") && player2.equals("C")) {
                return new int[]{2, 2}; // C, C -> 2,2
            } else if (player1.equals("C") && player2.equals("D")) {
                return new int[]{0, 4}; // C, D -> 0,4
            } else if (player1.equals("D") && player2.equals("C")) {
                return new int[]{4, 0}; // D, C -> 4,0
            } else if (player1.equals("D") && player2.equals("D")) {
                return new int[]{0, 0}; // D, D -> 0,0
            }

            throw new IllegalArgumentException("Invalid input: Only 'C' or 'D' allowed for each player.");
        }

        private void playGame(PlayerInformation player1, PlayerInformation player2) {
            //Assuming player1.id < player2.id

            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(player1.aid);
            msg.addReceiver(player2.aid);
            msg.setContent("NewGame#" + player1.id + "," + player2.id);
            send(msg);

            String pos1, pos2;

            msg = new ACLMessage(ACLMessage.REQUEST);
            msg.setContent("Action");
            msg.addReceiver(player1.aid);
            send(msg);

            gui.logLine("Main Waiting for movement");
            ACLMessage move1 = blockingReceive();
            gui.logLine("Main Received " + move1.getContent() + " from " + move1.getSender().getName());
            pos1 = move1.getContent().split("#")[1];

            msg = new ACLMessage(ACLMessage.REQUEST);
            msg.setContent("Action");
            msg.addReceiver(player2.aid);
            send(msg);

            gui.logLine("Main Waiting for movement");
            ACLMessage move2 = blockingReceive();
            gui.logLine("Main Received " + move2.getContent() + " from " + move2.getSender().getName());
            pos2 = move2.getContent().split("#")[1];

            payoff = getPayoff(pos1,pos2);

            msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(player1.aid);
            msg.addReceiver(player2.aid);
            result = "Results#"+player1.id+","+player2.id+"#"+pos1+","+pos2+"#"+payoff[0]+","+payoff[1];
            msg.setContent(result) ;
            gui.logLine(result);
            send(msg);
            msg.setContent("EndGame");
            send(msg);

            gui.updateRow(player1.id, player1.aid.getName(), payoff[0], pos1, "0");
            gui.updateRow(player2.id, player2.aid.getName(), payoff[1], pos2, "1");

        }

        private void roundOver(PlayerInformation player){
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(player.aid);
            String roundOverMessage = "RoundOver#" + player.id + "#" + player.aid.getName();
            gui.logLine(roundOverMessage);
            msg.setContent(roundOverMessage);
            send(msg);

        }
        @Override
        public boolean done() {
            return true;
        }
    }

    public class PlayerInformation {

        AID aid;
        int id;

        public PlayerInformation(AID a, int i) {
            aid = a;
            id = i;
        }

        @Override
        public boolean equals(Object o) {
            return aid.equals(o);
        }
    }

    public class GameParametersStruct {

        int N;
        int R;
        double F;

        public GameParametersStruct() {
            N = 2;
            R = 4;
            F = 0.05;
        }
    }
}
