package authorization;

import java.io.IOException;
import java.net.InetAddress;

public class ServerMain {

    private static boolean isPortCorrect(int port){
        return port >= 0 && port < 65536;
    }

    private static int parsePort(String portString){
        int port;

        try {
            port = Integer.parseInt(portString);
        }catch (NumberFormatException e){
            return  -1;
        }

        return isPortCorrect(port) ? port : -1;
    }

    public static void main(String[] args) throws IOException
    {
        int port = 0;

        if(args.length == 0)
            port = 6789;
        else if(args.length == 1)
            port = parsePort(args[0]);

        if(port == -1 || args.length > 1){
            System.out.println("USAGE: authorization.Server [port_number]");
            System.exit(-1);
        }

        Server server = new Server(port, 0, InetAddress.getLoopbackAddress());
        server.start();
    }
}
