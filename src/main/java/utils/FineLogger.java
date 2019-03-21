package utils;

import sun.rmi.runtime.Log;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FineLogger{

    private FineLogger(){

    }
    public static Logger getLogger(String name){
        Logger logger = Logger.getLogger(name);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.FINE);
        logger.setUseParentHandlers(false);
        return logger;
    }
}
