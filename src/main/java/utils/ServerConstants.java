package utils;

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
    public static final String NO_CONTENT = "HTTP/1.1 204 NO CONTENT\r\n";
    public static final String SERVER_ERROR= "HTTP/1.1 500 Internal authorization.Server Error\r\n";
    public static final String ERROR400= "HTTP/1.1 400 Not Found\r\n";



    private ServerConstants(){

    }
}
