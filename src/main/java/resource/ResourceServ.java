package resource;

import authorization.AuthorizationServ;
import org.json.JSONObject;
import utils.FineLogger;
import utils.Http;
import static utils.ServerConstants.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static utils.ServerConstants.NOT_IMPLEMENTED;

public class ResourceServ implements Runnable{
    private Socket client;
    private static Logger logger = FineLogger.getLogger(AuthorizationServ.class.getName());

    public ResourceServ(Socket client) {
        this.client = client;
    }


    private void doGet(Writer writer, OutputStream output) throws IOException{
        System.out.println("GET handler");
        writer.write(OK);
        Http.writeJSONResponse(writer, new JSONObject().put("results",
                new JSONObject().put("username", "JO").put("email", "asdf").toString())
                .toString());
    }
    @Override
    public void run() {

        try {
            OutputStream rawO = new BufferedOutputStream(client.getOutputStream());
            Writer writer = new OutputStreamWriter(rawO, StandardCharsets.UTF_8);
            InputStream rawI = client.getInputStream();

            String requestLine = Http.readLine(rawI);
            System.out.println("Request: " + requestLine);
            String[] tokens = requestLine.split("\\s+" );

            String token = getToken(rawI);
            if(token == null){
                writer.write(ERROR400);
                Http.writeJSONResponse(writer,
                        new JSONObject().put("error", "Access token does not exist.").toString());
            }

            if(tokens[0].equals("GET")){
                doGet(writer, rawO);
            }
            writer.close();
            rawO.close();
            rawI.close();

        }catch (RuntimeException e){
            logger.log(Level.SEVERE, "RUNTIME EXCEPTION", e);
        }
        catch (IOException ex) {
            logger.log(Level.SEVERE, "Error talking to " + client.getInetAddress(), ex);
        }finally {
            try {
                client.close();
                logger.fine("Closed connection for client " + client.getInetAddress());
            }catch (IOException ex){
                logger.log(Level.WARNING, "Can't close socket for " + client.getInetAddress(), ex);
            }
        }
    }

    private String getToken(InputStream rawI) throws IOException {
        Map<String, String> header = Http.readHeaderByte(rawI);
        System.out.println(header);
        if(header.containsKey("Authorization")){
            return header.get("Authorization").replace("Bearer", "")
                    .replaceAll("\\s", "");
        }
        return null;
    }

    private boolean tokenIsValid() {
        return true;
    }
}
