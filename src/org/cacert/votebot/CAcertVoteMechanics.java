package org.cacert.votebot;

import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Reprenents the voting-automata for voting in IRC chanenls.
 */
public class CAcertVoteMechanics {

    public static enum VoteType {
        AYE, NAYE, ABSTAIN
    }

    public static enum State {
        RUNNING, IDLE
    }

    State state = State.IDLE;

    String voteCaller;

    String topic;

    private String vote(String voter, String actor, VoteType type) {
        votes.put(voter, type);

        if (voter.equals(actor)) {
            return "Thanks " + actor + " I count your vote as " + type;
        } else {
            return "Thanks " + actor + " I count your vote for " + voter + " as " + type;
        }
    }

    HashMap<String, VoteType> votes = new HashMap<>();

    private String voteError(String actor) {
        return "Sorry " + actor + ", I did not understand your vote, your current vote state remains unchanged!";
    }

    /**
     * Adds a vote to the current topic. This interprets proxies.
     *
     * @param actor
     *            the person that sent this vote
     * @param txt
     *            the text that the person sent.
     * @return A message to
     *
     *         <pre>
     * actor
     * </pre>
     *
     *         indicating tha result of his action.
     */
    public synchronized String evaluateVote(String actor, String txt) {
        if (state != State.RUNNING) {
            return "Sorry " + actor + ", but currently no vote is running.";
        }

        String voter = actor;
        String value = null;

        if (txt.toLowerCase().matches("^\\s*proxy\\s.*")) {
            String[] parts = txt.split("\\s+");
            if (parts.length == 3) {
                voter = parts[1];
                value = parts[2];
            }
        } else {
            value = txt.replaceAll("^\\s*|\\s*$", "");
        }

        if (value == null) {
            return voteError(actor);
        } else {
            value = value.toLowerCase();

            switch (value) {
            case "aye":
            case "yes":
            case "oui":
            case "ja": {
                return vote(voter, actor, VoteType.AYE);
            }
            case "naye":
            case "nay":
            case "no":
            case "non":
            case "nein": {
                return vote(voter, actor, VoteType.NAYE);
            }
            case "abstain":
            case "enthaltung":
            case "enthalten":
            case "abs": {
                return vote(voter, actor, VoteType.ABSTAIN);
            }
            }
        }

        return voteError(actor);
    }

    /**
     * A new vote begins.
     * 
     * @param from
     *            the nick that called the vote
     * @param topic
     *            the topic of the vote
     * @return A response to
     * 
     *         <pre>
     * from
     * </pre>
     * 
     *         indicating success or failure.
     */
    public synchronized String callVote(String from, String topic) {
        if (state != State.IDLE) {
            return "Sorry, a vote is already running";
        }

        voteCaller = from;
        this.topic = topic;
        votes.clear();

        state = State.RUNNING;

        return "Vote started.";
    }

    /**
     * Ends a vote.
     * 
     * @return An array of Strings containing result status messages.
     */
    public synchronized String[] closeVote() {
        int[] res = new int[VoteType.values().length];

        for (Entry<String, VoteType> i : votes.entrySet()) {
            res[i.getValue().ordinal()]++;
        }

        String[] results = new String[VoteType.values().length];

        for (int i = 0; i < res.length; i++) {
            results[i] = (VoteType.values()[i] + ": " + res[i]);
        }

        votes.clear();
        voteCaller = null;
        state = State.IDLE;

        return results;
    }

    public String getTopic() {
        return topic;
    }

    public State getState() {
        return state;
    }

    public String getCurrentResult() {
        return votes.toString();
    }

}
