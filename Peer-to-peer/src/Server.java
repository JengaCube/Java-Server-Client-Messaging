import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;

// Driver Class
public class Server {
    // Main Function
    private ServerSocket serverSocket;

    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void startServer() {
        try {

            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                System.out.println("New Client Joined");
                ClientHandler clientHandler = new ClientHandler(socket);
                Thread thread  = new Thread(clientHandler);
                thread.start();

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void  main(String[] args) throws IOException {
        ServerSocket serversocket = new ServerSocket(1234);
        Server server = new Server(serversocket);
        server.startServer();
    }
}