package authorization;

import static utils.ServerConstants.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import utils.FineLogger;
import utils.Http;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AuthorizationServ implements Runnable{
    private static Logger logger = FineLogger.getLogger(AuthorizationServ.class.getName());
    private static final String INVALID_REQUEST = new JSONObject().put("error", "invalid_request").toString();
    private static final String UNAUTHORIZED = new JSONObject().put("error", "access_denied").toString();
    private static final Path PRIVATE_KEY_PATH
            = Paths.get("src", "main", "java", "authorization", "private_pcks8");
    private static PrivateKey privateKey = initPrivateKey();
    private Socket client;


    private static PrivateKey initPrivateKey(){
        try {
            String privateKeyString;
            privateKeyString = new String(Files.readAllBytes(PRIVATE_KEY_PATH), StandardCharsets.UTF_8);
            privateKeyString = privateKeyString.replaceAll("-----BEGIN PRIVATE KEY-----\n", "");
            privateKeyString = privateKeyString.replaceAll("-----END PRIVATE KEY-----\n", "");
            privateKeyString = privateKeyString.replaceAll("\\s", "");


            byte[] encodedKey = Base64.decodeBase64(privateKeyString);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedKey);

            try {

                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePrivate(keySpec);
            }catch (Exception e){
                logger.log(Level.WARNING, "Key exception", e);
            }

        }catch (IOException e){
            logger.log(Level.WARNING, "Can't load private key", e);
        }
        return null;
    }

    public AuthorizationServ(Socket client){
        this.client = client;
    }


    private static boolean containsAll(Map<String, String> map){
        return map.containsKey("password") && map.containsKey("username") && map.containsKey("grant_type")
                && map.containsKey("client_secret") && map.containsKey("client_id");
    }

    private boolean authenticateUser(String userName, String password){
        return true;
    }

    private String generateToken(String username){
        Claims claims = Jwts.claims();
        claims.put("iss", "sample-auth-server");
        claims.put("username", username);

        return  Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.RS256, privateKey).compact();
    }

    private void auth(Writer writer, InputStream input) throws IOException{
        Map<String, String> header = Http.readHeaderByte(input);

        //validate
        String content = header.get("Content-Length");
        //catch ex
        int contentLength = Integer.parseInt(content);

        byte[] bytes = Http.readBodyBytes(input, contentLength);
        Map<String, String> map = Http.parseJSONBody(bytes);

        if(!containsAll(map)){
            logger.log(Level.CONFIG, "Invalid request");
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, INVALID_REQUEST);
        }else if(!authenticateUser(map.get("username"), map.get("password"))){
            logger.log(Level.FINE, "Authentication failed");
            writer.write(UNAUTHORIZED);
            Http.writeJSONResponse(writer, UNAUTHORIZED);
        }else{
            logger.log(Level.FINER, "Sent OK");
            String token = generateToken(map.get("username"));
            writer.write(OK);
            Http.writeJSONResponse(writer,
                    new JSONObject().put("access_token", token)
                    .put("token_type", "bearer").toString());

        }


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

            if(tokens[0].equals("POST")){
                auth(writer, rawI);
            }else
                writer.write(NOT_IMPLEMENTED);
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
}
