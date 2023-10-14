import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class Client implements Runnable {

    private Socket client;
    private BufferedReader in;
    private PrintWriter out;
    private boolean done;
    private long lastMessageTime;
    private boolean spamProtectionActive;
    private Set<String> mutedUsers;

    public Client() {
        done = false;
        lastMessageTime = System.currentTimeMillis();
        spamProtectionActive = false;
        mutedUsers = new HashSet<>();
    }

    @Override
    public void run() {
        try {
            client = new Socket("5.tcp.eu.ngrok.io", 11250);
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            InputHandler inHandler = new InputHandler();
            Thread t = new Thread(inHandler);
            t.start();

            String inMessage;
            while ((inMessage = in.readLine()) != null) {
                System.out.println(inMessage);
            }
        } catch(IOException e) {
            shutdown();
        }
    }

    public void shutdown() {
        done = true;
        try {
            in.close();
            out.close();
            if(!client.isClosed()) {
                client.close();
            }
        } catch(IOException e) {
            // ignore
        }
    }

    class InputHandler implements Runnable {

        @Override
        public void run() {
            try {
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));

                while(!done) {
                    String message = inReader.readLine();
                    if(message.equals("/leave")) {
                        out.println(message);
                        inReader.close();
                        shutdown();
                    } else if (message.equals("/clear")) {
                        clearConsole();
                    } else if (message.startsWith("/mute ")) {
                        String[] parts = message.split(" ", 2);
                        if (parts.length == 2) {
                            muteUser(parts[1]);
                        }
                    } else if (message.startsWith("/unmute ")) {
                        String[] parts = message.split(" ", 2);
                        if (parts.length == 2) {
                            unmuteUser(parts[1]);
                        }
                    } else if (System.currentTimeMillis() - lastMessageTime < 1500 && spamProtectionActive) {
                        System.out.println("\u001B[1;31m\u001B[1mPlease do not spam!\u001B[0m");
                    } else {
                        out.println(message);
                        lastMessageTime = System.currentTimeMillis();
                    }
                    System.out.print("\033[A\033[2K"); // Clear the input line and print the prompt
                }
            } catch(IOException e) {
                shutdown();
            }
        }
    }

    public void muteUser(String username) {
        mutedUsers.add(username);
        System.out.println("\u001B[1;31m\u001B[1mYou have muted " + username + " for yourself\u001B[0m");
    }

    public void unmuteUser(String username) {
        mutedUsers.remove(username);
        System.out.println("\u001B[1;32m\u001B[1mYou have unmuted " + username + " for yourself\u001B[0m");
    }

    public static void main(String[] args) {
        Client client = new Client();

        // Clear console
        System.out.print("\033[H\033[2J");
        System.out.flush();

        Thread clientThread = new Thread(client);
        clientThread.start();

        // Activate spam protection after 5 seconds
        try {
            Thread.sleep(5000);
            client.spamProtectionActive = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
