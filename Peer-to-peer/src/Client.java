import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;

    public Client(Socket socket, String username) {
        try {
            this.socket = socket;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = username;
        } catch (IOException e) {
            System.out.println("❌ Error setting up I/O streams.");
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    public void sendMessage() {
        try {
            // Send the username to the server
            bufferedWriter.write(username);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            System.out.println("\n✅ Connected as: " + username);
            System.out.println("💬 Type your message and press Enter to send.");
            System.out.println("📜 Commands: /members, /whisper <user> <msg>, /quit");
            System.out.println("--------------------------------------------------");

            Scanner scanner = new Scanner(System.in);

            while (socket.isConnected()) {
                String messageToSend = scanner.nextLine();
                if (messageToSend.equalsIgnoreCase("/quit")) {
                    System.out.println("👋 You have left the chat. Goodbye!");
                    closeEverything(socket, bufferedReader, bufferedWriter);
                    break;
                }
                bufferedWriter.write(messageToSend);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }
        } catch (IOException e) {
            System.out.println("❌ Connection lost while sending message.");
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    public void listenForMessage() {
        new Thread(() -> {
            String msgFromGroupChat;
            while (socket.isConnected()) {
                try {
                    msgFromGroupChat = bufferedReader.readLine();
                    if (msgFromGroupChat == null) {
                        System.out.println("❌ Server has closed the connection.");
                        break;
                    }

                    if (!msgFromGroupChat.equals("PING")) { // Ignore heartbeat pings
                        System.out.println(msgFromGroupChat);
                    }

                } catch (IOException e) {
                    System.out.println("❌ Error reading message from server.");
                    closeEverything(socket, bufferedReader, bufferedWriter);
                    break;
                }
            }
        }).start();
    }

    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("⚠️ Error closing resources.");
        }
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.println("🌐 Enter server IP address:");
            String ipAddress = scanner.nextLine();

            System.out.println("🔌 Enter port number:");
            int port = Integer.parseInt(scanner.nextLine());

            Socket socket = new Socket(ipAddress, port);

            System.out.println("🧑 Enter your username:");
            String username = scanner.nextLine();

            // Optional: Simulate uniqueness check with placeholder
            // You'd ideally want to handle this logic server-side
            while (username.trim().isEmpty()) {
                System.out.println("❗ Username cannot be empty. Please enter a valid username:");
                username = scanner.nextLine();
            }

            Client client = new Client(socket, username);
            client.listenForMessage();
            client.sendMessage();

        } catch (IOException e) {
            System.out.println("❌ Could not connect to server. Please check IP and port.");
        } catch (NumberFormatException e) {
            System.out.println("❗ Invalid port number. Please enter a valid integer.");
        }
    }
}
