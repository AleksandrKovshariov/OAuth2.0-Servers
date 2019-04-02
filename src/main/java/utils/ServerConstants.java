package utils;

import org.json.JSONObject;

public class ServerConstants {

    public static final String OK = "HTTP/1.1 200 OK\r\n";
    public static final String FORBIDDEN = "HTTP/1.1 403 Forbidden\r\n";
    public static final String UNAUTHORIZED = "HTTP/1.1 401 Unauthorized\r\n";
    public static final String OPERATION_NOT_SUPPORTED = "HTTP/1.1 505 HTTP Version Not Supported\r\n";
    public static final String NOT_IMPLEMENTED = "HTTP/1.1 501 Not Implemented\r\n";
    public static final String NEW_LINE = "\r\n";
    public static final String CONTENT_HTML = "Content-Type: text/html \r\n";
    public static final String CONTENT_LENGTH = "Content-Length: ";
    public static final String CONTENT_TYPE = "Content-Type: ";
    public static final String COOKIE = "Set-Cookie: ";
    public static final String MOVED_PERMANENTLY = "HTTP/1.1 301 Moved Permanently\r\n";
    public static final String LOCATION = "Location: ";
    public static final String NOT_FOUND = "HTTP/1.1 404 Not Found\r\n";
    public static final String NO_CONTENT = "HTTP/1.1 204 No Content\r\n";
    public static final String SERVER_ERROR= "HTTP/1.1 500 Internal authorization.Server Error\r\n";
    public static final String ERROR400= "HTTP/1.1 400 Not Found\r\n";

    public static final String USER_HAS_NO_ACCESSED = new JSONObject().put("error", "User has no accesses").toString();
    public static final String ACCESS_DENIED = new JSONObject().put("error", "Access denied").toString();
    public static final String ACCESSTYPE_TOKEN_NOT_EXIST = new JSONObject().put("error", "AccessType token does not exist.").toString();
    public static final String ACCESSTYPE_TOKEN_INVALID = new JSONObject().put("error", "AccessType token is invalid.").toString();
    public static final String BAD_REQUEST = new JSONObject().put("error", "Bad request").toString();
    public static final String FILE_NOT_FOUND = new JSONObject().put("error", "File not found").toString();
    public static final String DIR_NOT_EMPTY = new JSONObject().put("error", "Folder must be empty to delete").toString();




    private ServerConstants(){

    }
}
