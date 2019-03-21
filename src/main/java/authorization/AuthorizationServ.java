package authorization;

import static constants.ServerConstants.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;

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
    private static Logger logger = Logger.getLogger(AuthorizationServ.class.getName());
    private static final String INVALID_REQUEST = new JSONObject().put("error", "invalid_request").toString();
    private static final String UNAUTHORIZED = new JSONObject().put("error", "access_denied").toString();
    private static final Path PRIVATE_KEY_PATH
            = Paths.get("src", "main", "java", "authorization", "private_pcks8");
    private static PrivateKey privateKey;
    private Socket client;

    static{
        initLogger();
        initPrivateKey();
    }

    private static void initLogger(){
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.FINE);
        logger.setUseParentHandlers(false);
    }

    private static void initPrivateKey(){
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
                privateKey = kf.generatePrivate(keySpec);
            }catch (Exception e){
                logger.log(Level.WARNING, "Key exception", e);
            }

        }catch (IOException e){
            logger.log(Level.WARNING, "Can't load private key", e);
        }
    }

    public AuthorizationServ(Socket client){
        this.client = client;
    }

    private static void closeAll(Closeable... streams) throws IOException{
        for(Closeable i : streams)
            i.close();
    }

    public String readLine(InputStream inputStream) throws IOException{
        StringBuilder sb = new StringBuilder();
        while(true){
            int c = inputStream.read();
            if(c == '\n' || c == -1)
                break;

            sb.append((char)c);
        }
        return sb.toString();
    }

    public static byte[] readBodyBytes(InputStream rawI, int size) throws IOException{
        byte[] bytes = new byte[size];
        int bytesRead = 0;
        while(bytesRead < size) {
            int result = rawI.read(bytes, bytesRead, size - bytesRead);
            if(result == -1) break;
            bytesRead += result;
        }
        return bytes;
    }

    private static Map<String, String> parseJSONBody(byte[] bytes){
        String body = new String(bytes);

        String[] tokens = body.split("&");

        Map<String, String> mapBody = new HashMap<>();

        for (String token : tokens) {
            String[] keyVal = token.split("=");
            if(keyVal.length > 1) {
                mapBody.put(keyVal[0], keyVal[1]);
            }
        }

        return mapBody;
    }

    private static boolean containsAll(Map<String, String> map){
        return map.containsKey("password") && map.containsKey("username") && map.containsKey("grant_type")
                && map.containsKey("client_secret") && map.containsKey("client_id");
    }

    private static void writeJSONResponse(Writer writer, String json) throws IOException{
        writer.write(CONTENT_TYPE + "application/json" + NEW_LINE);
        writer.write(CONTENT_LENGTH + json.getBytes(StandardCharsets.UTF_8).length + NEW_LINE + NEW_LINE);
        writer.write(json);
        writer.flush();
    }

    private boolean authenticateUser(String userName, String password){
        return true;
    }

    private String generateToken(){
        Claims claims = Jwts.claims();
        claims.put("iss", "sample-auth-server");

        return  Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.RS256, privateKey).compact();
    }

    private void auth(Writer writer, InputStream input) throws IOException{
        Map<String, String> header = readHeaderByte(input);

        //validate
        String content = header.get("Content-Length");
        //catch ex
        int contentLength = Integer.parseInt(content);

        byte[] bytes = readBodyBytes(input, contentLength);
        Map<String, String> map = parseJSONBody(bytes);

        if(!containsAll(map)){
            logger.log(Level.CONFIG, "Invalid request");
            writer.write(ERROR400);
            writeJSONResponse(writer, INVALID_REQUEST);
        }else if(!authenticateUser(map.get("username"), map.get("password"))){
            logger.log(Level.FINE, "Authentication failed");
            writer.write(UNAUTHORIZED);
            writeJSONResponse(writer, UNAUTHORIZED);
        }else{
            logger.log(Level.FINER, "Sent OK");
            String token = generateToken();
            System.out.println(token);
            writer.write(OK);
            writeJSONResponse(writer,
                    new JSONObject().put("access_token", token)
                    .put("token_type", "bearer").toString());

        }


    }

    private static Map<String, String> readHeaderByte(InputStream rawI) throws IOException{

        Map<String, String> headerMap = new HashMap<>();
        int c;
        StringBuilder lineBuilder = new StringBuilder();

        while (true){

            while ((c = rawI.read()) != '\n'){
                if(c == -1)
                    break;
                if(c == '\r')
                    continue;
                lineBuilder.append((char)c);
            }
            String line = lineBuilder.toString();

            if(line.isEmpty())
                break;

            String[] keyVal = line.split(": ");
            if(keyVal.length > 1) {
                headerMap.put(keyVal[0], keyVal[1]);
            }
            lineBuilder.setLength(0);
        }
        return headerMap;
    }

    @Override
    public void run() {

        try {
            OutputStream rawO = new BufferedOutputStream(client.getOutputStream());
            Writer writer = new OutputStreamWriter(rawO, StandardCharsets.UTF_8);
            InputStream rawI = client.getInputStream();

            String requestLine = readLine(rawI);
            System.out.println("Request: " + requestLine);
            String[] tokens = requestLine.split("\\s+" );

            if(tokens[0].equals("POST")){
                auth(writer, rawI);
            }else
                writer.write(NOT_IMPLEMENTED);
            closeAll(writer, rawO, rawI);

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
