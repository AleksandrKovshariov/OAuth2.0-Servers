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
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.stream.Stream;

public class ResourceServ implements Runnable{
    private Socket client;
    private Access<String, String> accessVerifier;
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

    public ResourceServ(Socket client, Access<String, String> accessVerifier) {
        this.client = client;
        this.accessVerifier = accessVerifier;
    }

    private String formJsonFile(Path path){
        long lastModified = 0;
        long size = 0;
        try {
            lastModified = Files.getLastModifiedTime(path).toMillis();
            size = Files.size(path);
        }catch (IOException e){
            logger.log(Level.CONFIG, "Error getting file info", e);
        }

        String fileName = path.getFileName().toString();

        return new JSONObject()
                .put("name", fileName)
                .put("size", size)
                .put("modified", lastModified)
                .toString();
    }
    private void sendDirectoryStructure(Writer writer, Path path) throws IOException{
        JSONObject jsonObject = new JSONObject();
        try(Stream<Path> paths = Files.walk(path)){
            paths.skip(1).forEach(x -> jsonObject.append("files", formJsonFile(x)));
        }
        writer.write(OK);
        writer.write("Type: directory" + NEW_LINE);
        Http.writeJSONResponse(writer, jsonObject.toString());

    }

    private void doGet(Writer writer, OutputStream output, Path path) throws IOException{
        System.out.println(path);
        System.out.println(Files.exists(path));
        if(Files.isDirectory(path)){
           sendDirectoryStructure(writer, path);
        }else{
            String contentType = URLConnection.getFileNameMap().getContentTypeFor(path.getFileName().toString());
            sendFile(writer, output, contentType, path);
        }

    }


    private boolean verifyToken(Writer writer, String token) throws IOException{

        if(token == null){
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, new JSONObject().put("error", "AccessType token does not exist.").toString());
            return false;
        }
        DecodedJWT decodedJWT = JWT.decode(token);

        if(!tokenIsValid(decodedJWT)){
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, new JSONObject().put("error", "AccessType token is invalid.").toString());
            return false;
        }

        return true;
    }

    private boolean verifyAccess(Writer writer, InputStream inputStream, Path path, AccessType type) throws IOException{
        Map<String, String> header = Http.readHeaderByte(inputStream);
        String token = getToken(header);
        if(!verifyToken(writer, token))
            return false;
        //token can't be null because of token verification above
        String username = JWT.decode(token).getClaim("username").asString();
        System.out.println("Got a username: " + username);
        System.out.println("Requested object: " + path);
        String unixLikePath = path.toString().replaceAll("\\\\", "/");
        accessVerifier.hasAccess(type, username, unixLikePath);
        return true;
    }

    private void sendFile(Writer writer, OutputStream rawO, String contentType, Path file) throws IOException{
        if(Files.exists(file) && Files.isReadable(file)) {
            Http.writeHeader(writer, Files.size(file), contentType);
            writer.flush();
            rawO.write(Files.readAllBytes(file));
            rawO.flush();
            logger.fine("Sent " + file.toAbsolutePath() + " to client " + client.getInetAddress());
        }else {
            writer.write(NOT_FOUND);
            writer.flush();
            logger.log(Level.FINE, "File not found");
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
            Path path = Paths.get(tokens[1].substring(1));

            String requestType = tokens[0];

            switch (requestType){
                case "GET":
                    if(!verifyAccess(writer, rawI, path, AccessType.READ)){
                        writer.write(FORBIDDEN);
                        writer.flush();
                    }
                    else
                        doGet(writer, rawO, path); break;
                default:
                    writer.write(NOT_IMPLEMENTED);
                    writer.flush();
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

    private String getToken(Map<String, String> header) {
        if(header.containsKey("Authorization")){
            return header.get("Authorization").replace("Bearer", "")
                    .replaceAll("\\s", "");
        }
        return null;
    }

    private boolean tokenIsValid(DecodedJWT decodedToken){
        try {
            ALGORITHM.verify(decodedToken);
        }catch (SignatureVerificationException e){
            return false;
        }

        return decodedToken.getClaim("username").asString() != null
                && decodedToken.getClaim("iss").asString().equals("sample-auth-server");
    }
}
