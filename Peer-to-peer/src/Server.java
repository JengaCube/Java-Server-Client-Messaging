import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    public static ArrayList<String> clients = new ArrayList<>();
    public static ArrayList<String> messageLogs = new ArrayList<>();
    public static String admin;

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String clientUsername;
    private volatile boolean isRunning = true;

    public ClientHandler(Socket socket) {
        try {
            this.socket = socket;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Get username
            this.clientUsername = bufferedReader.readLine();
            while (clients.contains(clientUsername) || clientUsername == null || clientUsername.trim().isEmpty()) {
                bufferedWriter.write("❗ Username already taken or invalid. Please enter a unique username:");
                bufferedWriter.newLine();
                bufferedWriter.flush();
                this.clientUsername = bufferedReader.readLine();
            }

            if (admin == null) {
                admin = this.clientUsername;
                logMessage("🏆 Coordinator selected: " + admin);
            }

            sendMessage("👋 Welcome " + clientUsername + "! Coordinator is: " + admin, clientUsername);
            clientHandlers.add(this);
            clients.add(clientUsername);

            broadcastMessage("🟢 SERVER: " + clientUsername + " has joined the chat");

            startHeartbeatThread();

        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    private void startHeartbeatThread() {
        Thread heartbeatThread = new Thread(() -> {
            while (isRunning) {
                try {
                    ArrayList<ClientHandler> toRemove = new ArrayList<>();

                    for (ClientHandler handler : clientHandlers) {
                        if (!handler.socket.isConnected()) {
                            toRemove.add(handler);
                        } else {
                            try {
                                handler.bufferedWriter.write("PING");
                                handler.bufferedWriter.newLine();
                                handler.bufferedWriter.flush();
                            } catch (IOException e) {
                                toRemove.add(handler);
                            }
                        }
                    }

                    for (ClientHandler handler : toRemove) {
                        handler.closeEverything(handler.socket, handler.bufferedReader, handler.bufferedWriter);
                    }
                    Thread.sleep(20000); // Every 20 seconds
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void logMessage(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String fullLog = "[" + timestamp + "] " + message;
        messageLogs.add(fullLog);
        System.out.println("📝 " + fullLog);
    }

    public static boolean isUnique(String username) {
        return !clients.contains(username);
    }

    public int commandCheck(String message) {
        String[] command = message.split(" ");
        if (Objects.equals(command[0], "/whisper")) return 1;
        if (Objects.equals(command[0], "/members")) return 2;
        return 0;
    }

    @Override
    public void run() {
        try {
            String messageFromClient;

            while (isRunning && socket.isConnected()) {
                try {
                    messageFromClient = bufferedReader.readLine();

                    if (messageFromClient == null) {
                        logMessage("🔴 " + clientUsername + " disconnected.");
                        break;
                    }

                    if (messageFromClient.startsWith("/")) {
                        handleCommand(messageFromClient);
                    } else {
                        broadcastMessage("💬 " + clientUsername + ": " + messageFromClient);
                    }

                } catch (SocketTimeoutException e) {
                    logMessage("⌛ Timeout: " + clientUsername + " not responding.");
                    break;
                } catch (IOException e) {
                    logMessage("❌ IOException from " + clientUsername + ": " + e.getMessage());
                    break;
                }
            }
        } finally {
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    private void handleCommand(String message) throws IOException {
        String[] parameters = message.split(" ");

        switch (commandCheck(message)) {
            case 1: // /whisper <user> <msg>
                if (parameters.length < 3) {
                    sendMessage("⚠️ Usage: /whisper <username> <message>", clientUsername);
                    return;
                }

                String targetUser = parameters[1];
                if (!clients.contains(targetUser)) {
                    sendMessage("⚠️ User '" + targetUser + "' not found.", clientUsername);
                    return;
                }

                String privateMsg = message.replaceFirst((parameters[0] + " " + targetUser + " "), "");
                sendMessage("🤫 (private) " + clientUsername + ": " + privateMsg, targetUser);
                sendMessage("📤 You whispered to " + targetUser + ": " + privateMsg, clientUsername);
                break;

            case 2: // /members
                sendMessage("📋 Active Members (ID, IP:Port):", clientUsername);
                for (ClientHandler handler : clientHandlers) {
                    String label = handler.clientUsername.equals(admin) ? " (admin)" : "";
                    String ip = handler.socket.getInetAddress().getHostAddress();
                    int port = handler.socket.getPort();
                    sendMessage("👤 " + handler.clientUsername + label + " - " + ip + ":" + port, clientUsername);
                }
                break;

            default:
                sendMessage("❗ Unknown command. Try /members or /whisper", clientUsername);
                break;
        }
    }

    public void sendMessage(String messageToSend, String receiver) {
        for (ClientHandler clientHandler : clientHandlers) {
            if (clientHandler.clientUsername.equals(receiver)) {
                try {
                    clientHandler.bufferedWriter.write(messageToSend);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                    logMessage("✉️ Private from " + clientUsername + " to " + receiver + ": " + messageToSend);
                } catch (IOException e) {
                    clientHandler.closeEverything(clientHandler.socket, clientHandler.bufferedReader, clientHandler.bufferedWriter);
                }
                break;
            }
        }
    }

    public void broadcastMessage(String messageToSend) {
        logMessage("📢 Broadcast from " + clientUsername + ": " + messageToSend);

        boolean isJoinMessage = messageToSend.contains("has joined the chat");

        for (ClientHandler clientHandler : clientHandlers) {
            try {
                clientHandler.bufferedWriter.write(messageToSend);
                clientHandler.bufferedWriter.newLine();
                clientHandler.bufferedWriter.flush();

                // 🔁 Sync whisper list
                if (isJoinMessage) {
                    if (!clientHandler.clientUsername.equals(clientUsername)) {
                        // Older clients get the new user's name
                        clientHandler.bufferedWriter.write("/whisper:add:" + clientUsername);
                        clientHandler.bufferedWriter.newLine();
                        clientHandler.bufferedWriter.flush();
                    } else {
                        // New user gets all existing users
                        for (ClientHandler other : clientHandlers) {
                            if (!other.clientUsername.equals(clientUsername)) {
                                clientHandler.bufferedWriter.write("/whisper:add:" + other.clientUsername);
                                clientHandler.bufferedWriter.newLine();
                                clientHandler.bufferedWriter.flush();
                            }
                        }
                    }
                }


            } catch (IOException e) {
                clientHandler.closeEverything(clientHandler.socket, clientHandler.bufferedReader, clientHandler.bufferedWriter);
            }
        }
    }

    public void removeClientHandler() {
        clientHandlers.remove(this);
        clients.remove(clientUsername);

        // 👇 Notify all clients to remove this user from their whisper list
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                if (clientHandler.bufferedWriter != null) {  // ✅ prevent NPE
                    clientHandler.bufferedWriter.write("/whisper:remove:" + clientUsername);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                }
            } catch (IOException ignored) {}
        }

        // Notify others
        broadcastMessage("🚪 SERVER: " + clientUsername + " has left the chat");
    }


    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        isRunning = false;
        removeClientHandler();

        // Handle admin reassignment
        if (admin.equals(clientUsername)) {
            if (!clients.isEmpty()) {
                admin = clients.get(0);
                broadcastMessage("🏆 SERVER: " + admin + " is now the Coordinator (admin).");
                logMessage("🛠️ Coordinator switched to: " + admin);
            } else {
                admin = null;
                logMessage("⚠️ No users left. Coordinator cleared.");
            }
        }

        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("⚠️ Error closing resources for " + clientUsername);
        }
    }

    public static void saveLogsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("chat_logs.txt", true))) {
            for (String log : messageLogs) {
                writer.write(log);
                writer.newLine();
            }
            writer.write("----- Server Shutdown at " + new java.util.Date() + " -----");
            writer.newLine();
        } catch (IOException e) {
            System.out.println("❌ Failed to save message logs.");
        }
    }

    //for testing
    protected void setClientUsername(String username) {
        this.clientUsername = username;
    }

    protected ClientHandler(String username, boolean testMode) {
        this.clientUsername = username;

        if (!testMode) {
            // original logic for real runtime
        }
    }

    protected String getClientUsername() {
        return clientUsername;
    }


}
