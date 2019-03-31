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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceServ implements Runnable{
    private Socket client;
    private Access<String, Resource> accessVerifier;
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

    public ResourceServ(Socket client, Access<String, Resource> accessVerifier) {
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
        String pathWithoutRes = unixLikePath(path.toString()).replaceFirst("resource/","");
        return new JSONObject()
                .put("name", fileName)
                .put("size", size)
                .put("modified", lastModified)
                .put("isDir", isDir)
                .put("path", isDir ? (pathWithoutRes + "/") : pathWithoutRes)
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

    public static String unixLikePath(String path){
        return path.replaceAll("\\\\", "/");
    }


    private JSONObject getAccess(String... params) throws OperationNotSupportedException{
        List<Resource> resources = accessVerifier.getUserAccess(currentUsername, params);
        JSONObject accesses = new JSONObject();
        List<String> pathes = resources.stream().map(Resource::getPath).filter(x -> Files.exists(x))
                .map(x -> Files.isDirectory(x) ? x + "/" : x.toString())
                .map(x -> unixLikePath(x).replaceFirst("resource/", ""))
                .collect(Collectors.toList());

        for (int i = 0; i < pathes.size(); i++) {
            accesses.append("access", new JSONObject().put("path", pathes.get(i))
                    .put("isDir", resources.get(i).isIdDir())
                    .put("accessType", Arrays.toString(resources.get(i).getAccessTypes())));
        }
        return accesses;
    }


    private void sendUserAccesses(Writer writer, Map<String, String> urlParams) throws IOException{
        try {
            JSONObject accesses;
            if(urlParams == null){
                accesses = getAccess();
            }else if(urlParams.containsKey("dirOnly")){
                accesses = getAccess(urlParams.get("is_dir"));
            }else{
                writer.write(ERROR400);
                writer.flush();
                return;
            }
            logger.log(Level.FINE, "Sending user accesses");
            writer.write(OK);
            Http.writeJSONResponse(writer, accesses.toString());
        }catch (OperationNotSupportedException e){
            logger.log(Level.CONFIG, "Can't send user accesses", e);
        }
    }

    private void doGet(Writer writer, OutputStream output, String request) throws IOException{
        Path path = Http.getPathFromUrl(request);
        if((request.startsWith("resource"))) {
            Resource resource = new Resource(Files.isDirectory(path), path, currentUsername, AccessType.READ);
            if (!verifyAccess(resource)) {
                logger.log(Level.FINE, "Access denied");
                writer.write(UNAUTHORIZED);
                writer.flush();
            }else {
                send(writer, output, path);
            }
        }
        else if(request.startsWith("access")) {
            Map<String, String> urlParams = Http.parseUrlParams(request);
            sendUserAccesses(writer, urlParams);
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
        setCurrentUsername(decodedJWT.getClaim("username").asString());

        return true;
    }

    private void setCurrentUsername(String username){
        currentUsername = username;
    }

    private boolean verifyAccess(Resource resource){
        System.out.println("Got a username: " + resource.getUsername());
        System.out.println("Requested object: " + resource);
        return accessVerifier.hasAccess(resource);
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
            InputStream rawI = new BufferedInputStream(client.getInputStream());

            String requestLine = Http.readLine(rawI);
            System.out.println("Request: " + requestLine);
            String[] tokens = requestLine.split("\\s+" );
            String path = tokens[1].substring(1);
            String requestType = tokens[0];

            Map<String, String> header = Http.readHeaderByte(rawI);
            String token = getToken(header);
            if(verifyToken(writer, token)) {

                switch (requestType) {
                    case "GET":
                        doGet(writer, rawO, path);
                        break;
                    case "POST":
                        doPost(writer, rawI, path);
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

    private void doPost(Writer writer, InputStream rawI, String request) throws IOException{
        System.out.println(request);
        Path path = Http.getPathFromUrl(request);
        Resource resource = new Resource(false, path.getParent(), currentUsername, AccessType.WRITE);
        if(!verifyAccess(resource)){
            writer.write(UNAUTHORIZED);
            writer.flush();
        }else{

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
