package utils;

import java.io.*;
import java.nio.file.Path;

public class FileSaver {

    private FileSaver(){

    }

    public static void save(Path path, byte[] bytes) throws IOException {
        try(OutputStream fout = new BufferedOutputStream(new FileOutputStream(path.toString()))){
            fout.write(bytes);
            fout.flush();
        }
    }
}
