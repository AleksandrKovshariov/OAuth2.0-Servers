package resource;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.json.JSONObject;
import utils.FineLogger;
import utils.Http;
import static utils.ServerConstants.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResourceServ implements Runnable{
    private Socket client;
    private static Logger logger = FineLogger.getLogger(ResourceServ.class.getName());
    private static final PublicKey PUBLIC_KEY = loadPublicKey();
    private static final Algorithm ALGORITHM = Algorithm.RSA256((RSAPublicKey)PUBLIC_KEY, null);

    private static PublicKey loadPublicKey() {
        try {
            byte[] keyBytes = Files.readAllBytes(Paths.get("src", "main", "java", "resource", "public_der"));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePublic(spec);
            }catch (NoSuchAlgorithmException | InvalidKeySpecException e){
                logger.log(Level.WARNING, "Wrong public key", e);
            }

        }catch (IOException e){
            logger.log(Level.WARNING, "Public key does not exist", e);
        }
        return null;
    }

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
            if(!tokenIsValid(token)){
                writer.write(ERROR400);
                Http.writeJSONResponse(writer, new JSONObject().put("error", "Access token is invalid.").toString());
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

    private boolean tokenIsValid(String token) throws IOException {
        DecodedJWT decodedJWT = JWT.decode(token);
        try {
            ALGORITHM.verify(decodedJWT);
        }catch (SignatureVerificationException e){
            return false;
        }

        return true;
    }
}
