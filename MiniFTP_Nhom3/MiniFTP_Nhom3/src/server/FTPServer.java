package server;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FTPServer {
    public static final int PORT = 8888; 
    public static final String SERVER_DIR = "D:\\FileTransfer\\ServerData";

    public static void main(String[] args) {
        File dir = new File(SERVER_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("=== HE THONG FTP SERVER DANG CHAY TAI PORT " + PORT + " ===");
            System.out.println("Kho luu tru tai: " + SERVER_DIR);
            System.out.println("Dang cho cac may tram ket noi...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Gọi class ClientHandler ở file riêng
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.out.println("Loi khoi dong Server: " + e.getMessage());
        }
    }
}