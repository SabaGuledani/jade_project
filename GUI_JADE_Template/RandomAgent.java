import com.sun.tools.javac.Main;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.PrintStream;
import java.util.Random;

public class RandomAgent extends Agent {

    private State state;
    private AID mainAgent;
    private int myId, opponentId;
    private int N, S;
    private double F;
    private String[] moves_list = {"C","D"};
    private ACLMessage msg;



    protected void setup() {
        state = State.s0NoConfig;



        //Register in the yellow pages as a player
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Player");
        sd.setName("Game");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new Play());
        System.out.println("RandomAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown() {
        //Deregister from the yellow pages
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("RandomPlayer " + getAID().getName() + " terminating.");
    }

    private enum State {
        s0NoConfig, s1AwaitingGame, s2Round, s3AwaitingResult
    }

    private class Play extends CyclicBehaviour {
        Random random = new Random();

        @Override
        public void action() {
            System.out.println(getAID().getName() + ":" + state.name());
            msg = blockingReceive();
            if (msg != null) {
//                System.out.println("mivige shetyobineba stateshi = " + state);
//                System.out.println(getAID().getName() + " received " + msg.getContent() + " from " + msg.getSender().getName()); //DELETEME
                //-------- Agent logic
                switch (state) {
                    case s0NoConfig:
                        //If INFORM Id#_#_,_,_,_ PROCESS SETUP --> go to state 1
                        //Else ERROR
                        if (msg.getContent().startsWith("Id#") && msg.getPerformative() == ACLMessage.INFORM) {
                            boolean parametersUpdated = false;
                            try {
                                parametersUpdated = validateSetupMessage(msg);
                            } catch (NumberFormatException e) {
                                System.out.println(getAID().getName() + ":" + state.name() + " - Bad message");
                            }
                            if (parametersUpdated) state = State.s1AwaitingGame;

                        } else {
                            System.out.println(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
                    case s1AwaitingGame:
//                        System.out.println(getAID().getName() + " aman miigo es ################ " + msg.getContent() + "es tipi:" + msg.getPerformative());
                        //If INFORM NEWGAME#_,_ PROCESS NEWGAME --> go to state 2
                        //If INFORM Id#_#_,_,_,_ PROCESS SETUP --> stay at s1
                        //Else ERROR
                        //TODO I probably should check if the new game message comes from the main agent who sent the parameters
                        if (msg.getPerformative() == ACLMessage.INFORM) {
                            if (msg.getContent().startsWith("Id#")) { //Game settings updated
                                try {
                                    validateSetupMessage(msg);
                                } catch (NumberFormatException e) {
                                    System.out.println(getAID().getName() + ":" + state.name() + " - Bad message");
                                }
                            } else if (msg.getContent().startsWith("NewGame#")) {
                                boolean gameStarted = false;
                                try {
                                    gameStarted = validateNewGame(msg.getContent());
                                } catch (NumberFormatException e) {
                                    System.out.println(getAID().getName() + ":" + state.name() + " - Bad message");
                                }
                                if (gameStarted) state = State.s2Round;
                            }else if(msg.getContent().startsWith("Accounting#")){
                                System.out.println(msg.getContent());
                            }
                        }else if (msg.getPerformative() == ACLMessage.REQUEST){
                            roundOverStockOperations(msg);
                        }else {
                            System.out.println(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
//                        TODO: davamato aq Accounting#4#190#38.15 mesijis migeba
                    case s2Round:
                        //If REQUEST Action --> INFORM Action --> go to state 3
                        //If INFORM CHANGED stay at state 2
                        //If INFORM ENDGAME go to state 1
                        //Else error
                        if (msg.getPerformative() == ACLMessage.REQUEST /*&& msg.getContent().startsWith("Action")*/) {
                            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                            msg.addReceiver(mainAgent);
                            int randomIndex = random.nextInt(moves_list.length);
                            String randomElement = moves_list[randomIndex];
                            msg.setContent("Action#" + randomElement);
                            System.out.println(getAID().getName() + " sent " + msg.getContent());
                            send(msg);
                            state = State.s3AwaitingResult;
                        }  else if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("EndGame")) {
                            state = State.s1AwaitingGame;
                        } else {
                            System.out.println(getAID().getName() + ":" + state.name() + " - Unexpected message:" + msg.getContent());
                        }
                        break;
                    case s3AwaitingResult:
                        //If INFORM RESULTS --> go to state 2
                        //Else error
                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Results#")) {

                            state = State.s2Round;

                        } else {
                            System.out.println(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
                }
            }
        }

        /**
         * Validates and extracts the parameters from the setup message
         *
         * @param msg ACLMessage to process
         * @return true on success, false on failure
         */
        private boolean validateSetupMessage(ACLMessage msg) throws NumberFormatException {
            int tN, tS, tMyId;
            double tF;
            String msgContent = msg.getContent();

            String[] contentSplit = msgContent.split("#");
            if (contentSplit.length != 3) return false;
            if (!contentSplit[0].equals("Id")) return false;
            tMyId = Integer.parseInt(contentSplit[1]);

            String[] parametersSplit = contentSplit[2].split(",");
            if (parametersSplit.length != 3) return false;
            tN = Integer.parseInt(parametersSplit[0]);
            tS = Integer.parseInt(parametersSplit[1]);
            tF = Double.parseDouble(parametersSplit[2]);

            //At this point everything should be fine, updating class variables
            mainAgent = msg.getSender();
            N = tN;
            S = tS;
            F = tF;
            myId = tMyId;
            return true;
        }
        private double processResults(String msgContent){
            String[] contentSplit = msgContent.split("#");
            String[] amountsSplit = contentSplit[3].split(",");
            double amount_won = Double.parseDouble(amountsSplit[1]);

            return amount_won;
        }


        private void roundOverStockOperations(ACLMessage msgRecevied){

            if (msgRecevied.getContent().startsWith("RoundOver")){


                String decision;
                double truncatedAmount;
                double maxToOperate;
                double randomDouble = Math.random();
                if (randomDouble > 0.5){
                    decision = "Buy";
                }else{
                    decision = "Sell";
                }
                if (decision.equals("Buy")){
                    maxToOperate = Double.parseDouble(msgRecevied.getContent().split("#")[3]);
                }else{
                    maxToOperate = Double.parseDouble(msgRecevied.getContent().split("#")[5]);
                }
                if (maxToOperate != 0.0){
                    maxToOperate = random.nextDouble(0,maxToOperate);

                }

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(mainAgent);
                msg.setContent(decision + "#" + maxToOperate);
                System.out.println(getAID().getName() + " decided to " + msg.getContent());
                send(msg);
            }
        }

        /**
         * Processes the contents of the New Game message
         * @param msgContent Content of the message
         * @return true if the message is valid
         */
        public boolean validateNewGame(String msgContent) {
            int msgId0, msgId1;
            String[] contentSplit = msgContent.split("#");
            if (contentSplit.length != 2) return false;
            if (!contentSplit[0].equals("NewGame")) return false;
            String[] idSplit = contentSplit[1].split(",");
            if (idSplit.length != 2) return false;
            msgId0 = Integer.parseInt(idSplit[0]);
            msgId1 = Integer.parseInt(idSplit[1]);
            if (myId == msgId0) {
                opponentId = msgId1;
                return true;
            } else if (myId == msgId1) {
                opponentId = msgId0;
                return true;
            }
            return false;
        }
    }
}
