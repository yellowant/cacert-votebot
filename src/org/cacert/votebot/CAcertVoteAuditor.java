package org.cacert.votebot;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cacert.votebot.CAcertVoteMechanics.VoteType;

public class CAcertVoteAuditor extends IRCBot {

    public CAcertVoteAuditor(IRCClient c, String toAudit) {
        super(c);
        this.toAudit = toAudit;
        c.join(voteAuxChn);
    }

    String voteAuxChn = System.getProperty("auditor.target.voteChn", "vote");

    String toAudit;

    long warn = Long.parseLong(System.getProperty("voteBot.warnSecs", "90"));

    long timeout = Long.parseLong(System.getProperty("voteBot.timeoutSecs", "120"));

    CAcertVoteMechanics mech = new CAcertVoteMechanics();

    String[] capturedResults = new String[VoteType.values().length];

    int ctr = -1;

    @Override
    public synchronized void publicMessage(String from, String channel, String message) {
        if (channel.equals(voteAuxChn)) {
            if (from.equals(toAudit)) {
                if (ctr >= 0) {
                    capturedResults[ctr++] = message;

                    if (ctr == capturedResults.length) {
                        String[] reals = mech.closeVote();

                        if (Arrays.equals(reals, capturedResults)) {
                            System.out.println("Audit for vote was successful.");
                        } else {
                            System.out.println("Audit failed! Vote Bot (or Auditor) is probably broken.");
                        }

                        ctr = -1;
                    }

                    return;
                }
                if (message.startsWith("New Vote: ")) {
                    System.out.println("detected vote-start");

                    Pattern p = Pattern.compile("New Vote: (.*) has started a vote on \"(.*)\"");
                    Matcher m = p.matcher(message);

                    if ( !m.matches()) {
                        System.out.println("error: vote-start malformed");
                        return;
                    }

                    mech.callVote(m.group(1), m.group(2));
                } else if (message.startsWith("Results: ")) {
                    System.out.println("detected vote-end. Reading results");

                    ctr = 0;
                }
            } else {
                if (ctr != -1) {
                    System.out.println("Vote after end.");
                    return;
                }

                System.out.println("detected vote");
                mech.evaluateVote(from, message);
                System.out.println("Current state: " + mech.getCurrentResult());
            }
        }
    }

    @Override
    public synchronized void privateMessage(String from, String message) {

    }

    @Override
    public synchronized void join(String cleanReferent, String chn) {

    }

    @Override
    public synchronized void part(String cleanReferent, String chn) {

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        IRCClient ic = IRCClient.parseCommandLine(args, "dogcraft_de-Auditor");
        String targetNick = System.getProperty("auditor.target.nick");

        if (targetNick == null) {
            System.out.println("use -Dauditor.target.nick=TargetNick to set a target nick");
            System.exit(0);
        }

        ic.setBot(new CAcertVoteAuditor(ic, targetNick));
    }
}
