import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class Server implements Runnable {

    private ArrayList<ConnectionHandler> connections;
    private ServerSocket server;
    private boolean done;
    private Random random;
    private Set<String> takenUsernames;
    private String[] colorCodes;

    public Server(){
        connections = new ArrayList<>();
        done = false;
        random = new Random();
        takenUsernames = new HashSet<>();
        colorCodes = generateColorCodes();
    }

    private String[] generateColorCodes() {
        String[] codes = new String[256];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = "\u001B[38;5;" + i + "m";
        }
        return codes;
    }

    private String getRandomColorCode() {
        return colorCodes[random.nextInt(colorCodes.length)];
    }

    @Override
    public void run() {
        try {
            server = new ServerSocket(1194);
            while (!done) {
                Socket client = server.accept();
                ConnectionHandler handler = new ConnectionHandler(client);
                connections.add(handler);
                new Thread(handler).start();
            }
        } catch (Exception e) {
            shutdown();
        }
    }

    public void broadcast(String message) {
        for(ConnectionHandler ch : connections) {
            if(ch != null) {
                ch.sendMessage(message);
            }
        }
    }

    public void shutdown() {
        try {
            done = true;
            if (!server.isClosed()) {
                server.close();
            }
            for(ConnectionHandler ch : connections) {
                ch.shutdown();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    class ConnectionHandler implements Runnable {

        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;
        private String colorCode;
        private String boldRedCode;
        private String boldYellowCode;

        private String requestedName;

        public ConnectionHandler(Socket client) {
            this.client = client;
            this.colorCode = getRandomColorCode();
            this.boldRedCode = "\u001B[1;31m";
            this.boldYellowCode = "\u001B[1;33m";
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("Enter Nickname: ");
                requestedName = in.readLine();

                if (System.getProperty("os.name").contains("Windows")) {
                    new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                } else {
                    new ProcessBuilder("bash", "-c", "clear").inheritIO().start().waitFor();
                }

                nickname = requestedName;
                int suffix = 1;
                while(isUsernameTaken(nickname)) {
                    String randomSuffix = String.valueOf(random.nextInt(1000));
                    nickname = requestedName + randomSuffix;
                }
                takenUsernames.add(nickname);
                InetAddress clientAddress = client.getInetAddress();
                String clientIP = clientAddress instanceof Inet4Address ? clientAddress.getHostAddress() : "IPv6";
                System.out.println(nickname + " connected from " + clientIP);
                broadcast("\u001B[1;32m" + nickname + " Joined" + "\u001B[0m");
                out.println("\u001B[1;37mHey, thanks for using our ChatTerm use \u001B[1;33m/help\u001B[1;37m to see all commands\u001B[0m");

                String message;
                while((message = in.readLine()) != null) {
                    if(message.startsWith("/leave")) {
                        broadcast("\u001B[1;31m" + nickname + " left the chat" + "\u001B[0m");
                        shutdown();
                    } else if (message.startsWith("/help")) {
                        out.println("\u001B[1;37mThis is our \u001B[1;32mHelplist\u001B[1;37m!\n" +
                                "\u001B[1;36m/help \u001B[1;37m(to see the help list.)\n" +
                                "\u001B[1;36m/nickname {new_nickname} \u001B[1;37m(to change the nickname.)\n" +
                                "\u001B[1;36m/leave \u001B[1;37m(to leave the chat.)\n" +
                                "\u001B[1;36m/list \u001B[1;37m(to see all users on the server.)\u001B[0m\n");
                    } else if (message.startsWith("/list")) {
                        listUsers(this);
                    } else {
                        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
                        Date date = new Date();
                        String time = "\u001B[1m" + formatter.format(date) + "\u001B[0m";
                        broadcast("> " + time + " | " + colorCode + nickname + "\u001B[0m" + ": " + message);
                    }
                }
            } catch (IOException | InterruptedException e) {
                shutdown();
            }

        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void shutdown() {
            try {
                in.close();
                out.close();
                if (!client.isClosed()) {
                    client.close();
                }
                takenUsernames.remove(nickname);
                removeConnection(this);
            } catch(IOException e) {
                // ignore
            }
        }

        private void changeNickname(String newNickname) {
            if (!isUsernameTaken(newNickname)) {
                System.out.println(nickname + " changed their nickname to " + newNickname);
                takenUsernames.remove(nickname);
                nickname = newNickname;
                takenUsernames.add(nickname);
                out.println("Successfully changed name to " + nickname);
            } else {
                if (nickname.equals(requestedName)) {
                    out.println("\u001B[1;31m" + "Sorry! The username " + requestedName + " is already taken. You can change your username with " + "\u001B[1;33m" + "/nickname" + "\u001B[1;31m" + "." + "\u001B[0m");
                } else {
                    out.println("\u001B[1;31m" + "Sorry! The username " + newNickname + " is already taken." + "\u001B[0m");
                }
            }
        }

        public String getColorCode() {
            return colorCode;
        }

        public String getNickname() {
            return nickname;
        }

        public long getPing() {
            // Implement ping logic here
            return 0;
        }
    }

    private boolean isUsernameTaken(String username) {
        return takenUsernames.contains(username);
    }

    private void removeConnection(ConnectionHandler ch) {
        connections.remove(ch);
    }

    private void listUsers(ConnectionHandler ch) {
        for(ConnectionHandler handler : connections) {
            if(handler != null) {
                long ping = handler.getPing();
                String colorCode = handler.getColorCode();
                String username = handler.getNickname();
                String message = String.format("(%d) | %s%s", ping, colorCode, username);
                ch.sendMessage(message);
            }
        }
    }

    private String convertIPv6ToIPv4Style(String ipv6Address) {
        String[] parts = ipv6Address.split(":");
        String lastPart = parts[parts.length - 1];
        int lastPartInt = Integer.parseInt(lastPart, 16);
        return "124.72." + (lastPartInt >> 8) + "." + (lastPartInt & 0xFF);
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }
}
