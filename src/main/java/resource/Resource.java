package resource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class Resource{
    private boolean isDir;
    private AccessType[] accessType;
    private Path path;
    private String username;

    public boolean isDir() {
        return isDir;
    }

    public Path getPath() {
        return path;
    }

    public String getUsername() {
        return username;
    }

    public Resource(boolean isDir, Path path, String username, AccessType... accessType) {
        this.isDir = isDir;
        this.accessType = accessType;
        this.path = path;
        this.username = username;
    }

    public AccessType[] getAccessTypes(){
        return accessType;
    }

    @Override
    public String toString() {
        return "Is dir: " + isDir + " , Path: " + path + " , Username: " + username
                + " , AccessTypes: " + Arrays.toString(accessType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resource)) return false;
        Resource resource = (Resource) o;
        return isDir == resource.isDir &&
                path.equals(resource.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isDir, path);
    }
}
