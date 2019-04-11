package authorization;

import utils.FineLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {

    private final int port;
    private final int query;
    private final InetAddress address;
    private static Logger errorLog;
    private static Logger requests;

    public static final int NUMBER_OF_THREADS = 10;

    static{
        errorLog = FineLogger.getLogger("Error");
        requests = FineLogger.getLogger("Requests");
    }


    public Server(int port, int query, InetAddress address) {
        this.port = port;
        this.query = query;
        this.address = address;
    }


    public void start(){
        ExecutorService service = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

        try(ServerSocket serverSocket = new ServerSocket(port, query, address)){

            requests.fine("authorization.Server started on port "
                    + serverSocket.getLocalPort() + " address: " + serverSocket.getInetAddress());

            while (true){
                try {
                    Socket client = serverSocket.accept();
                    requests.fine("Client " + client.getInetAddress() + " connected");
                    Runnable requestFile = new AuthorizationServ(client);
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
