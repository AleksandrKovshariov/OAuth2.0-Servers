package resource;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.json.JSONObject;
import utils.FileSaver;
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
import java.sql.ResultSet;
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
                .put("size", isDir ? " " : size)
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

    private void send(Writer writer, OutputStream output, Resource resource) throws IOException{
        Path path = resource.getPath();
        if(resource.isDir()){
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
        for(Resource r : resources){
            Path p = r.getPath();
            if(Files.exists(p)){
                String path = Files.isDirectory(p) ? p + "/" : p.toString();
                path = unixLikePath(path).replaceFirst("resource/","");
                accesses.append("access", new JSONObject().put("path", path)
                        .put("isDir", r.isDir()).put("accessType", Arrays.toString(r.getAccessTypes())));
            }
        }

        return accesses;
    }

    private void sendUserAccesses(Writer writer, Map<String, String> urlParams) throws IOException{
        try {
            JSONObject accesses;
            if(urlParams == null){
                accesses = getAccess();
            }else {
                String[] keyVals
                        = urlParams.keySet().stream().map(x -> x + "=" + urlParams.get(x)).toArray(String[]::new);
                accesses = getAccess(keyVals);
            }
            if(accesses.isEmpty()) {
                writer.write(NOT_FOUND);
                Http.writeJSONResponse(writer, USER_HAS_NO_ACCESSED);
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
        Resource resource = new Resource(Files.isDirectory(path), path, currentUsername, AccessType.READ);
        if((request.startsWith("resource"))) {
            if (!verifyAccess(resource)) {
                logger.log(Level.FINE, "Access denied");
                writer.write(UNAUTHORIZED);
                Http.writeJSONResponse(writer, ACCESS_DENIED);
            }else {
                send(writer, output, resource);
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
            Http.writeJSONResponse(writer, ACCESSTYPE_TOKEN_NOT_EXIST);
            return false;
        }
        DecodedJWT decodedJWT = JWT.decode(token);

        if(!tokenIsValid(decodedJWT)){
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, ACCESSTYPE_TOKEN_INVALID);
            return false;
        }
        setCurrentUsername(decodedJWT.getClaim("username").asString());

        return true;
    }

    private void setCurrentUsername(String username){
        currentUsername = username;
    }

    private boolean verifyAccess(Resource resource){
        return accessVerifier.hasAccess(resource);
    }

    private void sendBytes(OutputStream rawO, Path file) throws IOException{
        System.out.println("Sendding file...");
        try(InputStream fin = new BufferedInputStream(new FileInputStream(file.toString()))){
            byte[] bytes = new byte[1024];
            while (fin.read(bytes) != -1){
                System.out.println("Writing bytes");
                rawO.write(bytes);
            }
            System.out.println("Flushing...");
            rawO.flush();
        }
    }

    private void sendFile(Writer writer, OutputStream rawO, String contentType, Path file) throws IOException{
        if(Files.exists(file) && Files.isReadable(file)) {
            Http.writeHeader(writer, Files.size(file), contentType);
            writer.flush();
            try{
                sendBytes(rawO, file);
            }catch (IOException e){
                logger.log(Level.WARNING, "Error sending file", e);
            }
            logger.fine("Sent " + file.toAbsolutePath() + " to client " + client.getInetAddress());
        }else {
            writer.write(NOT_FOUND);
            Http.writeJSONResponse(writer, FILE_NOT_FOUND);
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
                System.out.println("Token exist");
                switch (requestType) {
                    case "GET":
                        System.out.println("Doing doGet...");
                        doGet(writer, rawO, path);
                        break;
                    case "POST":
                        System.out.println("Doing doPost...");
                        doPost(writer, rawI, path, header);
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

    private void trySaveFile(Writer writer, Path path, int size, InputStream rawI) throws IOException {
        try{
            byte[] bytes = new byte[4096];
            int bytesRead = 0;
            try(OutputStream fout = new BufferedOutputStream(new FileOutputStream(path.toString()))) {
                while (bytesRead < size) {
                    int result = rawI.read(bytes, 0, 4096);
                    fout.write(bytes);
                    if (result == -1) break;
                    bytesRead += result;
                }
                fout.flush();
            }
            writer.write(OK);
            writer.flush();
            logger.fine("Saved file to: " + path);
        }catch (IOException e){
            logger.log(Level.WARNING,"Error writing file");
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, BAD_REQUEST);
        }
    }

    private void doPost(Writer writer, InputStream rawI, String request, Map<String, String> header)
            throws IOException{

        System.out.println(request);

        Path path = Http.getPathFromUrl(request);
        System.out.println("Parent " + path.getParent());
        Resource resource = new Resource(true, path.getParent(), currentUsername, AccessType.WRITE);
        if(!verifyAccess(resource)){
            logger.log(Level.WARNING, "Rights violated");
            writer.write(UNAUTHORIZED);
            Http.writeJSONResponse(writer, ACCESS_DENIED);
        }else{
            String sizeStr = header.get("Content-Length");
            try{
                int size = Integer.parseInt(sizeStr);
                trySaveFile(writer, path, size, rawI);
                System.out.println("Success");
                accessVerifier.addAccess(new Resource(false, path, currentUsername, AccessType.values()));
            }catch (NumberFormatException | NullPointerException e){
                logger.log(Level.WARNING, BAD_REQUEST);
                writer.write(ERROR400);
                Http.writeJSONResponse(writer, BAD_REQUEST);
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
