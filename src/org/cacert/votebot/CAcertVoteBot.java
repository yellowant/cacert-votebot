package org.cacert.votebot;

import java.io.IOException;

import org.cacert.votebot.CAcertVoteMechanics.State;

public class CAcertVoteBot extends IRCBot implements Runnable {

    public CAcertVoteBot(IRCClient c) {
        super(c);

        c.join(meetingChn);
        c.join(voteAuxChn);

        new Thread(this).start();
    }

    String meetingChn = System.getProperty("voteBot.meetingChn", "agm");

    String voteAuxChn = System.getProperty("voteBot.voteChn", "vote");

    long warn = Long.parseLong(System.getProperty("voteBot.warnSecs", "90"));

    long timeout = Long.parseLong(System.getProperty("voteBot.timeoutSecs", "120"));

    CAcertVoteMechanics mech = new CAcertVoteMechanics();

    @Override
    public synchronized void publicMessage(String from, String channel, String message) {
        if (channel.equals(voteAuxChn)) {
            sendPublicMessage(voteAuxChn, mech.evaluateVote(from, message));
        }
    }

    @Override
    public synchronized void privateMessage(String from, String message) {
        if (message.startsWith("vote ")) {
            String response = mech.callVote(from, message.substring(5));
            sendPrivateMessage(from, response);

            if (response.startsWith("Sorry,")) {
                return;
            }

            anounce("New Vote: " + from + " has started a vote on \"" + mech.getTopic() + "\"");
            sendPublicMessage(meetingChn, "Please cast your vote in #vote");
            sendPublicMessage(voteAuxChn, "Please cast your vote in the next " + timeout + " seconds.");
        }
    }

    private synchronized void anounce(String msg) {
        sendPublicMessage(meetingChn, msg);
        sendPublicMessage(voteAuxChn, msg);
    }

    public void run() {
        try {
            while (true) {
                while (mech.getState() == State.IDLE) {
                    Thread.sleep(1000);
                }

                Thread.sleep(warn * 1000);
                anounce("Voting on " + mech.getTopic() + " will end in " + (timeout - warn) + " seconds.");
                Thread.sleep((timeout - warn) * 1000);
                anounce("Voting on " + mech.getTopic() + " has closed.");
                String[] res = mech.closeVote();
                anounce("Results: for " + mech.getTopic() + ":");

                for (int i = 0; i < res.length; i++) {
                    anounce(res[i]);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void join(String cleanReferent, String chn) {

    }

    @Override
    public synchronized void part(String cleanReferent, String chn) {

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        IRCClient ic = IRCClient.parseCommandLine(args, "dogcraft_de");
        ic.setBot(new CAcertVoteBot(ic));
    }

}
