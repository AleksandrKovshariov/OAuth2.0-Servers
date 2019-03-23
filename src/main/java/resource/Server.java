package resource;

import authorization.AuthorizationServ;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {

    private final int port;
    private final int query;
    private final InetAddress address;
    private static Logger errorLog = Logger.getLogger("Error");
    private static Logger requests = Logger.getLogger("Requests");


    public String url = "jdbc:mysql://localhost:3306/mydb";
    public String username = "resource";
    public String password = "cfif";
    public static final int NUMBER_OF_THREADS = 10;

    static{
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        errorLog.setLevel(Level.ALL);
        requests.setLevel(Level.ALL);
        errorLog.addHandler(handler);
        requests.addHandler(handler);
    }


    public Server(int port, int query, InetAddress address) {
        this.port = port;
        this.query = query;
        this.address = address;
    }


    public void start(){
        ExecutorService service = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

        Connection connection = null;
        try {
           connection = DriverManager.getConnection(url, username, password);
        }catch (SQLException ex){
            errorLog.log(Level.SEVERE, "Database connection error", ex);
        }

        try(ServerSocket serverSocket = new ServerSocket(port, query, address)){

            requests.fine(ResourceServ.class.getName() + " started on port " +
                    + serverSocket.getLocalPort() + " address: " + serverSocket.getInetAddress());

            while (true){
                try {
                    Socket client = serverSocket.accept();
                    requests.fine("Client " + client.getInetAddress() + " connected");
                    Runnable requestFile = new ResourceServ(client, connection);
                    service.submit(requestFile);
                }catch (IOException ex){
                    requests.log(Level.CONFIG, "Client disconnected", ex);
                }
            }

        }catch (IOException ex){
            errorLog.log(Level.SEVERE, "Error init server socket", ex);
        }
    }
}
