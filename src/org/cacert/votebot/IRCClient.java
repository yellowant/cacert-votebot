package org.cacert.votebot;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Semaphore;

import javax.net.ssl.SSLSocketFactory;

public class IRCClient {

    private Semaphore loggedin = new Semaphore(0);

    private PrintWriter out;

    private Socket s;

    class ServerReader implements Runnable {

        private BufferedReader br;

        public ServerReader(BufferedReader br) {
            this.br = br;

            new Thread(this).start();
        }

        @Override
        public void run() {
            String l;

            try {
                while ((l = br.readLine()) != null) {
                    String fullline = l;
                    // System.out.println(l);

                    if (l.startsWith("PING ")) {
                        System.out.println("PONG");
                        out.println("PONG " + l.substring(5));
                    }

                    String referent = "";

                    if (l.startsWith(":")) {
                        String[] parts = l.split(" ", 2);
                        referent = parts[0];
                        l = parts[1];
                    }

                    String[] command = l.split(" ", 3);

                    if (command[0].equals("001")) {
                        loggedin.release();
                    }

                    if (command[0].equals("PRIVMSG")) {
                        String msg = command[2].substring(1);
                        String chnl = command[1];

                        if ( !chnl.startsWith("#")) {
                            handlePrivMsg(referent, msg);
                        } else {
                            handleMsg(referent, chnl, msg);
                        }

                        log(chnl, fullline);
                    } else if (command[0].equals("JOIN")) {
                        String chn = command[1].substring(1);
                        targetBot.join(cleanReferent(referent), chn.substring(1));
                        log(chn, fullline);
                    } else if (command[0].equals("PART")) {
                        String chn = command[1];
                        targetBot.part(cleanReferent(referent), chn);
                        log(chn, fullline);
                    } else {
                        System.out.println("unknown line: ");
                        System.out.println(l);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private String cleanReferent(String referent) {
            String[] parts = referent.split("!");

            if ( !parts[0].startsWith(":")) {
                System.err.println("invalid public message");
                return "unknown";
            }

            return parts[0];
        }

        HashMap<String, PrintWriter> logs = new HashMap<String, PrintWriter>();

        private void log(String chn, String l) throws IOException {
            PrintWriter log = logs.get(chn);

            if (log == null) {
                log = new PrintWriter(new FileOutputStream("irc/log_" + chn), true);
                logs.put(chn, log);
            }

            log.println(l);
        }

        private void handlePrivMsg(String referent, String msg) {
            String[] parts = referent.split("!");

            if ( !parts[0].startsWith(":")) {
                System.err.println("invalid private message");
                return;
            }

            if (targetBot == null) {
                System.out.println("dropping message");
                return;
            }

            targetBot.privateMessage(parts[0].substring(1), msg);
        }

        private void handleMsg(String referent, String chnl, String msg) {
            String[] parts = referent.split("!");

            if ( !parts[0].startsWith(":")) {
                System.err.println("invalid public message");
                return;
            }

            if (targetBot == null) {
                System.out.println("dropping message");
                return;
            }

            if ( !chnl.startsWith("#")) {
                System.err.println("invalid public message (chnl)");
                return;
            }

            targetBot.publicMessage(parts[0].substring(1), chnl.substring(1), msg);
        }

    }

    public IRCClient(String nick, String server, int port, boolean ssl) throws IOException, InterruptedException {
        if ( !nick.matches("[a-zA-Z0-9_-]+")) {
            throw new Error("malformed");
        }

        if (ssl) {
            s = SSLSocketFactory.getDefault().createSocket(server, port);//default-ssl = 7000
            // default-ssl = 7000
        } else {
            s = new Socket(server, port);
            // default-plain = 6667
        }

        out = new PrintWriter(s.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

        new ServerReader(in);

        out.println("USER " + nick + " 0 * :unknown");
        out.println("NICK " + nick);

        loggedin.acquire();
    }

    HashSet<String> joined = new HashSet<String>();

    private IRCBot targetBot;

    public void join(String channel) {
        if ( !channel.matches("[a-zA-Z0-9_-]+")) {
            return;
        }

        if (joined.add(channel)) {
            out.println("JOIN #" + channel);
        }
    }

    public void leave(String channel) {
        if ( !channel.matches("[a-zA-Z0-9_-]+")) {
            return;
        }

        if (joined.remove(channel)) {
            out.println("PART #" + channel);
        }
    }

    public void send(String msg, String channel) {
        if ( !channel.matches("[a-zA-Z0-9_-]+")) {
            return;
        }

        out.println("PRIVMSG #" + channel + " :" + msg);
    }

    public void sendPrivate(String msg, String to) {
        if ( !to.matches("[a-zA-Z0-9_-]+")) {
            return;
        }

        out.println("PRIVMSG " + to + " :" + msg);
    }

    public void setBot(IRCBot bot) {
        this.targetBot = bot;
    }

    public static IRCClient parseCommandLine(String[] commandLine, String nick) throws IOException, InterruptedException {
        String host = null;
        int port = 7000;
        boolean ssl = true;

        for (int i = 0; i < commandLine.length; i++) {
            String cmd = commandLine[i];

            if (cmd.equals("--no-ssl") || cmd.equals("-u")) {
                ssl = false;
            } else if (cmd.equals("--ssl") || cmd.equals("-s")) {
                ssl = true;
            } else if (cmd.equals("--host") || cmd.equals("-h")) {
                host = commandLine[++i];
            } else if (cmd.equals("--port") || cmd.equals("-p")) {
                port = Integer.parseInt(commandLine[++i]);
            } else if (cmd.equals("--nick") || cmd.equals("-n")) {
                nick = commandLine[++i];
            } else if (cmd.equals("--help") || cmd.equals("-h")) {
                System.out.println("Options: [--no-ssl|-u|--ssl|-s|[--host|-h] <host>|[--port|-p] <port>|[--nick|-n] <nick>]*");
                System.out.println("Requires the -host argument, --ssl is default, last argument of a kind is significant.");
                throw new Error("Operation caneled");
            } else {
                throw new Error("Invalid option (usage with --help): " + cmd);
            }
        }

        if (host == null) {
            throw new Error("--host <host> is missing");
        }

        return new IRCClient(nick, host, port, ssl);
    }

}
