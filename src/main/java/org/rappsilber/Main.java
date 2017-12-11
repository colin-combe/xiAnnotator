package org.rappsilber;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class.
 *
 */
public class Main {
    // Base URI the Grizzly HTTP server will listen on
    public static String BASE_URI = "http://localhost:8082/xiAnnotator/";

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public static HttpServer startServer() {
        // create a resource config that scans for JAX-RS resources and providers
        // in com.example package
        final ResourceConfig rc = new ResourceConfig().packages("org.rappsilber");
        //org.glassfish.grizzly.http.server.Response.getResponse().getHeaders().
        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        BASE_URI = System.getProperty("BASE_URI",BASE_URI);
        commandlineLoggerLevel();
        final HttpServer server = startServer();

        Logger.getLogger(Main.class.getName()).log(Level.INFO,String.format("Jersey app started with WADL available at "
                + "%sapplication.wadl\n", BASE_URI) );
        Runtime.getRuntime().addShutdownHook(
                new Thread() {public void run() {
                    Logger.getLogger(Main.class.getName()).log(Level.INFO,"Shuting down the server...");
                    server.stop();
                    Logger.getLogger(Main.class.getName()).log(Level.INFO,"Bye");
                }}
        );

        while (true) {
            try {
                Thread.sleep(100000);
            } catch (InterruptedException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }
    
    /**
     * setup logging levels for 
     * 
     * 
     * 
     * 
     * 
     */
    private static void commandlineLoggerLevel() {
        Logger.getGlobal().setLevel(Level.WARNING);
        HashMap<String,Level> logLevels=new HashMap<String,Level>();
        for(String name:System.getProperties().stringPropertyNames()){
            String logger="java.util.logging.loglevel:";
            if(name.startsWith(logger)){
                String loggerName=name.substring(logger.length());
                String loggerValue=System.getProperty(name);
                try {
                    Level level = Level.parse(loggerValue);
                    Logger l =  Logger.getLogger(loggerName);
                    l.setLevel(level);
                    
                    Logger lg = Logger.getGlobal().getParent();
                    while (l.getHandlers().length == 0 && l !=lg ) {
                        l=l.getParent();
                    }
                    for(Handler handler : l.getHandlers()) {
                        if (handler.getLevel().intValue() > level.intValue()) {
                            handler.setLevel(level);
                        }
                    }                    
                    Logger.getLogger(Main.class.getName()).log(Level.INFO, "Set logging for {0} to {1}({2})", new Object[]{loggerName, level.intValue(), level.getName()});
                
                } catch (IllegalArgumentException e) {
                    Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Error setting logging level for {0} to {1}", new Object[]{loggerName, loggerValue});
                }
            }
        }
    }
}

