package resource;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.json.JSONException;
import org.json.JSONObject;
import utils.FineLogger;
import utils.Http;

import static utils.ServerConstants.*;

import java.io.*;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.fileupload.MultipartStream;

public class ResourceServ implements Runnable{
    private Socket client;
    private Access<String, Resource> access;
    private String currentUsername = null;
    private static Logger logger = FineLogger.getLogger(ResourceServ.class.getName(), "logs/Resource.txt");
    private static final Path PATH_TO_KEY = Paths.get("src", "main", "java", "resource", "public_der");
    private static final PublicKey PUBLIC_KEY = loadPublicKey();
    private static final Algorithm ALGORITHM = Algorithm.RSA256((RSAPublicKey)PUBLIC_KEY, null);
    private static final String baseHeader = "Access-Control-Allow-Origin:*" + NEW_LINE;

    private static PublicKey loadPublicKey() {
        try {
            byte[] keyBytes = Files.readAllBytes(PATH_TO_KEY);
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
        this.access = accessVerifier;
    }

    private JSONObject formJsonResource(Resource resource){
        long lastModified = 0;
        long size = 0;
        Path path = resource.getPath();
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
                .put("accessType", resource.getAccessTypes());
    }

    private void sendDirectoryStructure(Writer writer, Resource resource, Map<String, String> urlParams) throws IOException{
        JSONObject jsonObject = new JSONObject();
        Path path = resource.getPath();
        urlParams.put("path", unixLikePath(path.toString()) + "/");

        System.out.println("HERERERERERERE");
        System.out.println(urlParams.get("path"));

        List<Resource> accessibleResources
                = new ArrayList<>(access.getUserAccess(resource.getUsername(), urlParams));
        accessibleResources.stream().filter(x -> Files.exists(x.getPath()))
                .forEach(x -> jsonObject.append("files", formJsonResource(x).toString()));

        writer.write(OK);
        writer.write(baseHeader);
        writer.write("Type: directory" + NEW_LINE);
        Http.writeJSONResponse(writer, jsonObject.toString());

    }

    private void send(Writer writer, OutputStream output, Resource resource, Map<String,String> urlParams)
            throws IOException{
        Path path = resource.getPath();

        if(Files.isDirectory(path)){
            sendDirectoryStructure(writer, resource, urlParams);
        }else{
            String contentType = URLConnection.getFileNameMap().getContentTypeFor(path.getFileName().toString());
            sendFile(writer, output, contentType, path);
        }
    }

    public static String unixLikePath(String path){
        return path.replaceAll("\\\\", "/");
    }


    private JSONObject getAccess(Map<String, String> urlParams)
    {
        List<Resource> resources = access.getUserAccess(currentUsername, urlParams);
        JSONObject accesses = new JSONObject();
        for(Resource r : resources){
            Path p = r.getPath();
            if(Files.exists(p)){
                String path = Files.isDirectory(p) ? p + "/" : p.toString();
                path = unixLikePath(path).replaceFirst("resource/","");
                accesses.append("access", new JSONObject().put("path", path)
                        .put("isDir", Files.isDirectory(p)).put("accessType", Arrays.toString(r.getAccessTypes())));
            }
        }

        return accesses;
    }

    private void sendUserAccesses(Writer writer, Map<String, String> urlParams) throws IOException{
        JSONObject accesses;
        accesses = getAccess(urlParams);
        if(accesses.isEmpty()) {
            writer.write(NOT_FOUND);
            Http.writeJSONResponse(writer, USER_HAS_NO_ACCESSED);
            return;
        }
        logger.log(Level.FINE, "Sending user accesses");
        writer.write(OK);
        writer.write(baseHeader);
        Http.writeJSONResponse(writer, accesses.toString());
    }

    private void doGet(Writer writer, OutputStream output, String request) throws IOException{
        Path path = Http.getPathFromUrl(request);
        Resource resource = new Resource(path, currentUsername, AccessType.READ);
        Map<String, String> urlParams = Http.parseUrlParams(request);
        if((request.startsWith("resource"))) {
            if (!verifyAccess(resource)) {
                logger.log(Level.FINE, "Access denied");
                writer.write(UNAUTHORIZED);
                Http.writeJSONResponse(writer, ACCESS_DENIED);
            }else {
                send(writer, output, resource, urlParams);
            }
        }
        else if(request.startsWith("access")) {
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

        //Setting username here is really bad design
        setCurrentUsername(decodedJWT.getClaim("username").asString());

        return true;
    }

    private void setCurrentUsername(String username){
        currentUsername = username;
    }

    private boolean verifyAccess(Resource resource){
        return access.hasAccess(resource);
    }

    private void sendBytes(OutputStream rawO, Path file) throws IOException{
        logger.finer("Sending file");
        try(InputStream fin = new BufferedInputStream(new FileInputStream(file.toString()))){
            byte[] bytes = new byte[1024];
            while (fin.read(bytes) != -1){
                rawO.write(bytes);
            }
            logger.finer("Flushing bytes...");
            rawO.flush();
        }
    }

    private void sendFile(Writer writer, OutputStream rawO, String contentType, Path file) throws IOException{
        if(Files.exists(file) && Files.isReadable(file)) {
            System.out.println(contentType);
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

    private void deleteFile(Path path) throws IOException{
        try {
            Files.delete(path);
        }catch (NoSuchFileException e){
            logger.log(Level.CONFIG, "Deleting file not found", e);
        }
    }
    private void deleteResource(Writer writer, Resource resource) throws IOException{
        Path path = resource.getPath();
        try{
            deleteFile(path);
        }catch (DirectoryNotEmptyException e){
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, DIR_NOT_EMPTY);
            return;
        }
        try {
            access.deleteAccess(resource);
            writer.write(OK);
            writer.write(baseHeader);
            writer.flush();
        }catch (Exception e){
            logger.log(Level.WARNING, "Something bad happened deleting a file", e);
            writer.write(ERROR400);
            writer.write(baseHeader);
            Http.writeJSONResponse(writer, ERROR_DELETING);
        }
    }

    private void doDelete(Writer writer, String request) throws IOException{
        Path path = Http.getPathFromUrl(request);
        Resource resource = new Resource(path, currentUsername, AccessType.DELETE);
        if((request.startsWith("resource"))) {
            if (!verifyAccess(resource)) {
                logger.log(Level.WARNING, "Access denied");
                writer.write(UNAUTHORIZED);
                Http.writeJSONResponse(writer, ACCESS_DENIED);
            }else{
                deleteResource(writer, resource);
            }
        }

    }
    @Override
    public void run() {

        try {
            OutputStream rawO = new BufferedOutputStream(client.getOutputStream());
            Writer writer = new OutputStreamWriter(rawO, StandardCharsets.UTF_8);
            InputStream rawI = new BufferedInputStream(client.getInputStream());

            String requestLine = Http.readLine(rawI);
            System.out.println("Request = " + requestLine);
            String[] tokens = requestLine.split("\\s+" );
            //Must be some checking of array exception
            String path = tokens[1].substring(1);
            String requestType = tokens[0];

            Map<String, String> header = Http.readHeaderByte(rawI);
            String token = getToken(header);

            if(verifyToken(writer, token)) {
                logger.finer("Token exist");
                switch (requestType) {
                    case "GET":
                        logger.finer("Doing Get...");
                        doGet(writer, rawO, path);
                        break;
                    case "POST":
                        logger.finer("Doing POST...");
                        doPost(writer, rawI, path, header);
                        break;
                    case "DELETE":
                        logger.finer("Doing DELETE...");
                        doDelete(writer, path);
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
            int bytesRead = 0;
            byte[] bytes = new byte[2048];
            try(OutputStream fout = new BufferedOutputStream(new FileOutputStream(path.toString()))) {
                while (bytesRead < size) {
                    int result = rawI.read(bytes);
                    if (result == -1) break;
                    fout.write(bytes, 0, result);
                    bytesRead += result;
                }
                fout.flush();
            }
            writer.write(OK);
            writer.write(baseHeader);
            writer.flush();
            logger.fine("Saved file to: " + path);
        }
        catch (IOException e){
            logger.log(Level.WARNING,"Error writing file");
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, BAD_REQUEST);
        }
    }

    @SuppressWarnings("deprecated")
    //For refactoring
    private void writeMultipart(Writer writer, String content, InputStream rawI) throws IOException{
        logger.finer("Parsing multipart");

        String boundary = content.substring(content.indexOf("boundary=") + 9);
        System.out.println("Boundary = " + boundary);
        byte[] boundaryBytes = boundary.getBytes();

        MultipartStream multipartStream = new MultipartStream(rawI, boundaryBytes);
        boolean nextPart = multipartStream.skipPreamble();
        boolean wasDir       = false;
        boolean exitNormally = true;
        String toDir = null;
        List<String> wroteFiles = new ArrayList<>();

        while (nextPart){
            String head = multipartStream.readHeaders();
            int nameIndex = head.indexOf("name=") + 6;
            String name = head.substring(nameIndex, head.indexOf("\"", nameIndex + 1));
            System.out.println("Name = " + name);

            if(wasDir && name.equalsIgnoreCase("file")){
                int fileIndex = head.indexOf("filename=") + 10;
                String fileName = ("resource/" + toDir + "/" +
                        head.substring(fileIndex, head.indexOf("\"", fileIndex)))
                                .replaceAll("[^a-zA-Z0-9_./-]", "");

                wroteFiles.add(fileName.substring(9));

                System.out.println("File name = " + fileName);
                Path path = Paths.get(fileName);
                if(!access.hasAccess(new Resource(path.getParent(), currentUsername, AccessType.WRITE))){
                    writer.write(UNAUTHORIZED);
                    Http.writeJSONResponse(writer, ACCESS_DENIED);
                    return;
                }
                try(FileOutputStream fout = new FileOutputStream(fileName)){
                    multipartStream.readBodyData(fout);
                }catch (IOException e){
                    exitNormally = false;
                    logger.log(Level.WARNING, "Can't save file", e);
                }
                try {
                    access.addAccess(new Resource(Paths.get(fileName), currentUsername, AccessType.values()));
                }catch (SQLIntegrityConstraintViolationException e){
                    logger.log(Level.WARNING, "File was overriden", e);
                    writer.write(ERROR400);
                    Http.writeJSONResponse(writer, FILE_EXIST);
                }catch (Exception e){
                    logger.log(Level.WARNING, "Error adding access", e);
                    writer.write(ERROR400);
                    Http.writeJSONResponse(writer, ADDING_ACCESS_ERR);
                    exitNormally = false;
                    break;
                }
            }else if(name.equalsIgnoreCase("to_dir")){
                ByteArrayOutputStream dirStream = new ByteArrayOutputStream();
                multipartStream.readBodyData(dirStream);
                toDir = dirStream.toString();
                System.out.println("To_dir = " + toDir);
                wasDir = true;
            }
            else {
                exitNormally = false;
                break;
            }
            nextPart = multipartStream.readBoundary();
        }
        if(exitNormally) {
            writer.write(OK);
            JSONObject jsonObject = new JSONObject();
            wroteFiles.forEach(x -> jsonObject.append("saved", x));
            Http.writeJSONResponse(writer, jsonObject.toString());
        }else{
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, BAD_REQUEST);
        }
        System.out.println("Success");
    }

    private void writeResource(Writer writer, Map<String, String> header, Path path, InputStream rawI)
            throws IOException{
        try{
            String sizeStr = header.get("Content-Length");
            int size = Integer.parseInt(sizeStr);
            System.out.println("Size = " + sizeStr);
            trySaveFile(writer, path, size, rawI);
            try {
                access.addAccess(new Resource(path, currentUsername, AccessType.values()));
            }catch (SQLIntegrityConstraintViolationException e){
                logger.log(Level.WARNING, "File already exist", e);
                writer.write(ERROR400);
                Http.writeJSONResponse(writer, FILE_EXIST);
            } catch (Exception e){
                logger.log(Level.WARNING, "Error adding access to file", e);
                writer.write(ERROR400);
                Http.writeJSONResponse(writer, ADDING_ACCESS_ERR);
            }
            System.out.println("Success");
        }catch (NumberFormatException | NullPointerException e){
            logger.log(Level.WARNING, BAD_REQUEST);
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, BAD_REQUEST);
        }
    }

    private void saveResource(Writer writer, InputStream rawI, String request, Map<String, String> header) throws IOException{
        String content = header.get("Content-Type");
        if (content != null && content.startsWith("multipart/form-data") && content.contains("boundary=")) {
            writeMultipart(writer, content, rawI);
        } else {
            //Processing simple POST with a file in body
            Path path = Http.getPathFromUrl(request);
            Resource resource = new Resource(path.getParent(), currentUsername, AccessType.WRITE);
            if (!verifyAccess(resource)) {
                logger.log(Level.WARNING, "Rights violated");
                writer.write(UNAUTHORIZED);
                Http.writeJSONResponse(writer, ACCESS_DENIED);
            } else {
                writeResource(writer, header, path, rawI);
            }
        }
    }

    private void addAccess(Writer writer, InputStream rawI, Map<String, String> header) throws IOException{
        try {
            String strLength = header.get("Content-Length");
            int length = Integer.parseInt(strLength);
            String str = new String(Http.readBodyBytes(rawI, length), StandardCharsets.UTF_8);
            try {
                JSONObject jsonObject = new JSONObject(str);
                String toUser = jsonObject.get("to_user").toString();
                String path = "resource/" + jsonObject.get("path").toString();
                String[] accesstypeStr = jsonObject.get("access_type").toString().split(",");
                AccessType[] accessTypes = new AccessType[accesstypeStr.length];
                for (int i = 0; i < accesstypeStr.length; i++)
                    accessTypes[i] = AccessType.valueOf(accesstypeStr[i]);

                Path resPath = Paths.get(path);
                Resource resource = new Resource(resPath, currentUsername, AccessType.GRANT);
                System.out.println(resource);
                if(!verifyAccess(resource)){
                    writer.write(UNAUTHORIZED);
                    Http.writeJSONResponse(writer, ACCESS_DENIED);
                }else{
                    System.out.println("Access is right, adding new access to user");
                    Resource granted = new Resource(resPath, toUser, accessTypes);
                    try{
                        access.addAccess(granted);
                        writer.write(OK);
                        Http.writeJSONResponse(writer, new JSONObject().put("to_user", toUser)
                                .put("path", path).put("access_types", Arrays.toString(accessTypes)).toString());
                    }catch (Exception e){
                        if(verifyAccess(granted)){
                            writer.write(OK);
                            Http.writeJSONResponse(writer, USER_HAS_ACCESS);
                        }else {
                            writer.write(ERROR400);
                            Http.writeJSONResponse(writer, ERROR_ADDING_ACC);
                        }
                    }
                }

            }
            catch (JSONException e){
                logger.log(Level.CONFIG, "Bad request", e);
                writer.write(ERROR400);
                Http.writeJSONResponse(writer, BAD_REQUEST);
            }

        }catch (NullPointerException | NumberFormatException | IOException e){
            writer.write(ERROR400);
            Http.writeJSONResponse(writer, BAD_REQUEST);
            logger.log(Level.CONFIG, "404 addAccess", e);
        }

    }
    private void doPost(Writer writer, InputStream rawI, String request, Map<String, String> header)
            throws IOException{

        if(request.startsWith("resource")) {
            System.out.println("Saving resource");
            saveResource(writer, rawI, request, header);
        }else if(request.startsWith("access")){
            System.out.println("Adding access");
            addAccess(writer, rawI, header);
        }else{
            writer.write(FORBIDDEN);
            Http.writeJSONResponse(writer, ACC_FORBIDDEN);
        }
    }

    private static String getToken(Map<String, String> header) {
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
