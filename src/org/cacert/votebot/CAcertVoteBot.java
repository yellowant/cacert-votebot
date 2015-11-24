package org.cacert.votebot;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

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

    HashMap<String, HashSet<String>> tmpProxies = new HashMap<>();

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
        } else if (message.startsWith("proxy ")) {
            String[] parts = message.split(" ", 4);
            if (parts.length != 3) {
                sendPrivateMessage(from, "Sorry, your proxy was syntactically incorrect.");
                return;
            }
            HashSet<String> tmp = tmpProxies.get(from);
            if (tmp == null) {
                tmp = new HashSet<>();
                tmpProxies.put(from, tmp);
            }
            tmp.add(parts[1] + " " + parts[2]);
        } else if (message.equals("myproxies")) {
            HashSet<String> px = tmpProxies.get(from);
            if (px == null) {
                sendPrivateMessage(from, "no proxies are set");
            } else {
                for (String e : px) {
                    sendPrivateMessage(from, "proxy " + e);
                }
            }
        } else if (message.equals("setproxies")) {
            HashSet<String> px = tmpProxies.get(from);
            if (px == null) {
                px = new HashSet<>();
            }
            if (mech.setProxies(px)) {
                anounce("PROXY LIST:");
                for (String e : px) {
                    anounce("proxy " + e);
                }
                anounce("END PROXY LIST.");
                sendPrivateMessage(from, "Success, your proxies were set and anounced.");
                tmpProxies.put(from, null);
            } else {
                sendPrivateMessage(from, "Error, your proxies were not set.");
            }
        } else if (message.equals("clearproxies")) {
            if (mech.setProxies(null)) {
                anounce("PROXY UNRESTRICTED.");
                sendPrivateMessage(from, "Success, proxies were cleared.");
            } else {
                sendPrivateMessage(from, "Error, proxies were not cleared.");
            }

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
        if (chn.equals(voteAuxChn)) {
            mech.joinedVote(cleanReferent);
        }
    }

    @Override
    public void renamed(String source, String target) {
        mech.renamed(source, target);
    }

    @Override
    public synchronized void part(String cleanReferent, String chn) {
        if (chn.equals(voteAuxChn)) {
            mech.joinedVote(cleanReferent);
        }

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        IRCClient ic = IRCClient.parseCommandLine(args, "dogcraft_de");
        ic.setBot(new CAcertVoteBot(ic));
    }

}
