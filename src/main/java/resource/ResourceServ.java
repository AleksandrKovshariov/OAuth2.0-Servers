package resource;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.json.JSONObject;
import utils.FineLogger;
import utils.Http;

import javax.naming.OperationNotSupportedException;

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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class ResourceServ implements Runnable{
    private Socket client;
    private Access<String, Path> accessVerifier;
    private String currentUsername = null;
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

    public ResourceServ(Socket client, Access<String, Path> accessVerifier) {
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
        boolean isDir = Files.isDirectory(path);
        String pathWithoutFirst = path.toString().replaceFirst("resource/","");
        return new JSONObject()
                .put("name", fileName)
                .put("size", size)
                .put("modified", lastModified)
                .put("isDir", isDir)
                .put("path", isDir ? (pathWithoutFirst + "/") : pathWithoutFirst)
                .toString();
    }

    private void sendDirectoryStructure(Writer writer, Path path) throws IOException{
        JSONObject jsonObject = new JSONObject();
        try(Stream<Path> paths = Files.list(path)){
            paths.forEach(x -> jsonObject.append("files", formJsonFile(x)));
        }
        writer.write(OK);
        writer.write("Type: directory" + NEW_LINE);
        Http.writeJSONResponse(writer, jsonObject.toString());

    }

    private void send(Writer writer, OutputStream output, Path path) throws IOException{
        System.out.println("Path" + path);
        if(Files.isDirectory(path)){
            sendDirectoryStructure(writer, path);
        }else{
            String contentType = URLConnection.getFileNameMap().getContentTypeFor(path.getFileName().toString());
            sendFile(writer, output, contentType, path);
        }
    }

    private void sendUserAccesses(Writer writer, OutputStream outputStream) throws IOException{
        System.out.println("Sending user accesses");
        try {
            List<Path> list = accessVerifier.getUserAccess(currentUsername);
            JSONObject accesses = new JSONObject();
            list.stream().filter(x -> Files.exists(x))
                    .forEach(x -> accesses.append("access", Files.isDirectory(x) ? (x + "/")
                            .replaceFirst("resource/", "")
                            : x.toString().replaceFirst("resource/", "")));
            writer.write(OK);
            Http.writeJSONResponse(writer, accesses.toString());
        }catch (OperationNotSupportedException e){
            logger.log(Level.CONFIG, "Can't send user accesses", e);
        }
    }

    private void doGet(Writer writer, OutputStream output, Path path) throws IOException{
        if((path.startsWith("resource/") || path.startsWith("resource"))) {
            if (!verifyAccess(path, AccessType.READ)) {
                logger.log(Level.FINE, "Access denied");
                writer.write(UNAUTHORIZED);
                writer.flush();
            }else
                send(writer, output, path);
        }
        else if(path.startsWith("access") || path.startsWith("access"))
            sendUserAccesses(writer, output);
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
        setCurrentUsername(decodedJWT.getClaim("username").asString());

        return true;
    }

    private void setCurrentUsername(String username){
        currentUsername = username;
    }

    private boolean verifyAccess(Path path, AccessType type) throws IOException{
        //token can't be null because of token verification above
        System.out.println("Got a username: " + currentUsername);
        System.out.println("Requested object: " + path);
        return accessVerifier.hasAccess(type, currentUsername, path);
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

            Map<String, String> header = Http.readHeaderByte(rawI);
            String token = getToken(header);
            if(verifyToken(writer, token)) {

                switch (requestType) {
                    case "GET":
                        doGet(writer, rawO, path);
                        break;
                    default:
                        writer.write(NOT_IMPLEMENTED);
                        writer.flush();
                }
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
