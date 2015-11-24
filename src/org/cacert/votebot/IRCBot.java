package org.cacert.votebot;

public abstract class IRCBot {

    private IRCClient c;

    public IRCBot(IRCClient c) {
        this.c = c;
    }

    public abstract void publicMessage(String from, String channel,
            String message);

    public abstract void privateMessage(String from, String message);

    public void sendPublicMessage(String channel, String message) {
        c.send(message, channel);
    }

    public void sendPrivateMessage(String to, String message) {
        c.sendPrivate(message, to);
    }

    public abstract void part(String cleanReferent, String chn);

    public abstract void join(String cleanReferent, String chn);

}
