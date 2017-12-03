/*
 * pm-station-usb
 * 2017 (C) Copyright - https://github.com/rjaros87/pm-station-usb
 * License: GPL 3.0
 */
package pmstation.configuration;

import java.io.File;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.BuilderParameters;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.HomeDirectoryLocationStrategy;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {
    
    public enum Entry {
        
        INTERVAL("interval"),
        AUTOSTART("autostart.measurements"),
        ALWAYS_ON_TOP("always.on.top"),
        POS_X("pos.x"),
        POS_Y("pos.y"),
        POS_WIDTH("pos.width"),
        POS_HEIGHT("pos.height"),
        SCREEN("screen.name"),
        SCREEN_POS_X("screen.pos.x"),
        SCREEN_POS_Y("screen.pos.y"),
        SCREEN_POS_WIDTH("screen.pos.width"),
        SCREEN_POS_HEIGHT("screen.pos.height");
        
        
        private String key;
        
        Entry(String key) {
            this.key = key;
        }
        
        public String key() {
            return key;
        }
        
        @Override
        public String toString() {
            return key();
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static final String CONFIG_NAME = SystemUtils.IS_OS_WINDOWS ? "pmstationusb.properties" : ".pmstationusbconfig";
    
    private static Config instance = null;
    private static final Object LOCK = new Object();
    
    private Configuration config = null;
    private static FileBasedConfigurationBuilder<FileBasedConfiguration> builder;
    
    private Config() {

        // https://issues.apache.org/jira/browse/CONFIGURATION-677
        /*BuilderParameters params = new Parameters().properties()
            .setFileName(CONFIG_NAME)
            .setLocationStrategy(new HomeDirectoryLocationStrategy())
            .setEncoding("UTF-8");*/
        
        // workaround:
        HomeDirectoryLocationStrategy location = new HomeDirectoryLocationStrategy();
        File configFile = new File(location.getHomeDirectory(), CONFIG_NAME);
        BuilderParameters params = new Parameters().properties()
                .setFile(configFile)
                .setEncoding("UTF-8");
        
        builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(
                PropertiesConfiguration.class, params.getParameters(), true);
        builder.setAutoSave(true);
        
        try {
            config = builder.getConfiguration();
            logger.info("User configuration is now loaded from: {}", configFile);
        } catch(ConfigurationException cex) {
            logger.error("Error loading configuration", cex);
            throw new IllegalStateException("Error loading/creating configuration");
        }
    }
    
    public Configuration to() {
        return config;
    }
    
    public static final Config instance() {
        if (instance != null) {
            return instance;
        }
        
        synchronized(LOCK) {
            if (instance != null) {
                return instance;
            }
            instance = new Config();
            return instance;
        }
        
    }
    
}