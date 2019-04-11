package resource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Resource{
    private boolean idDir;
    private AccessType[] accessType;
    private Path path;
    private String username;

    public boolean isDir() {
        return idDir;
    }

    public Path getPath() {
        return path;
    }

    public String getUsername() {
        return username;
    }

    public Resource(boolean idDir, Path path, String username, AccessType... accessType) {
        this.idDir = idDir;
        this.accessType = accessType;
        this.path = path;
        this.username = username;
    }

    public AccessType[] getAccessTypes(){
        return accessType;
    }

    @Override
    public String toString() {
        return "Is dir: " + idDir + " , Path: " + path + " , Username: " + username
                + " , AccessTypes: " + Arrays.toString(accessType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resource)) return false;
        Resource resource = (Resource) o;
        return idDir == resource.idDir &&
                path.equals(resource.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idDir, path);
    }
}
