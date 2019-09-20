package utils;


import java.io.IOException;
import java.util.logging.*;

public class FineLogger{

    private FineLogger(){

    }
    public static Logger getLogger(String name){
        Logger logger = Logger.getLogger(name);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.FINER);
        logger.setUseParentHandlers(false);
        return logger;
    }

    public static Logger getLogger(String name, String fileName){
        Logger log = getLogger(name);
        try {
            FileHandler fout = new FileHandler(fileName);
            fout.setFormatter(new SimpleFormatter());
            fout.setLevel(Level.ALL);
            log.addHandler(fout);
        }catch (IOException e){
            System.err.println("Error logging");
        }
        return log;
    }
}
