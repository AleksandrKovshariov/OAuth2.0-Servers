package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static utils.ServerConstants.*;
import static utils.ServerConstants.NEW_LINE;

public class Http {

    private Http(){

    }
    public static Map<String, String> readHeaderByte(InputStream rawI) throws IOException {

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

    public static Map<String, String> parseUrlParams(String url){
        Map<String, String> map = new HashMap<>();

        if(url.contains("?")){
            String[] params = url.substring(url.indexOf('?') + 1).split("&");
            for(String s : params){
                String[] keyVals = s.split("=");
                if(keyVals.length == 2)
                    map.put(keyVals[0], keyVals[1]);
            }
        }
        return map;
    }

    public static Path getPathFromUrl(String url){
        if(url.contains("?"))
            return Paths.get(url.substring(url.indexOf('?') + 1));
        return Paths.get(url);
    }

    public static Map<String, String> parseJSONBody(byte[] bytes){
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


    public static void writeJSONResponse(Writer writer, String json) throws IOException{
        writer.write(CONTENT_TYPE + "application/json" + NEW_LINE);
        writer.write(CONTENT_LENGTH + json.getBytes(StandardCharsets.UTF_8).length + NEW_LINE + NEW_LINE);
        writer.write(json);
        writer.flush();
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

    public static void writeHeader(Writer writer, long contentLength, String contentType) throws IOException {
        writer.write(OK);
        writer.write(CONTENT_LENGTH + contentLength + NEW_LINE);
        writer.write(CONTENT_TYPE + contentType + NEW_LINE + NEW_LINE);
    }

    public static String readLine(InputStream inputStream) throws IOException{
        StringBuilder sb = new StringBuilder();
        while(true){
            int c = inputStream.read();
            if(c == '\n' || c == -1)
                break;

            sb.append((char)c);
        }
        return sb.toString();
    }
}
