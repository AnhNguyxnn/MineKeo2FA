package com.anhnguyen.minekeo.utils;

import com.anhnguyen.minekeo.MineKeo2FA;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class LogManager {
    private static Logger logger;
    private static FileHandler fileHandler;
    private static SimpleFormatter formatter;
    
    public static void initialize(MineKeo2FA plugin) {
        try {
            // Create logs directory if not exists
            File logsDir = new File(plugin.getDataFolder(), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            // Create log file with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String timestamp = sdf.format(new Date());
            File logFile = new File(logsDir, "MineKeo2FA_" + timestamp + ".log");
            
            // Initialize logger
            logger = Logger.getLogger("MineKeo2FA");
            logger.setLevel(Level.ALL);
            
            // Create file handler
            fileHandler = new FileHandler(logFile.getAbsolutePath(), true);
            fileHandler.setLevel(Level.ALL);
            
            // Create custom formatter
            formatter = new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String timestamp = timeFormat.format(new Date(record.getMillis()));
                    
                    String level = record.getLevel().getName();
                    String message = record.getMessage();
                    
                    // Color coding for different log levels
                    String levelColor;
                    switch (level) {
                        case "SEVERE":
                            levelColor = "🔴";
                            break;
                        case "WARNING":
                            levelColor = "🟡";
                            break;
                        case "INFO":
                            levelColor = "🔵";
                            break;
                        case "FINE":
                            levelColor = "🟢";
                            break;
                        default:
                            levelColor = "⚪";
                    }
                    
                    return String.format("[%s] %s %s: %s\n", 
                        timestamp, levelColor, level, message);
                }
            };
            
            fileHandler.setFormatter(formatter);
            logger.addHandler(fileHandler);
            
            // Log startup message
            logger.info("=== MineKeo2FA Logging System Started ===");
            logger.info("Log file: " + logFile.getAbsolutePath());
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to initialize custom logging system: " + e.getMessage());
        }
    }
    
    public static void info(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }
    
    public static void warning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
    
    public static void severe(String message) {
        if (logger != null) {
            logger.severe(message);
        }
    }
    
    public static void fine(String message) {
        if (logger != null) {
            logger.fine(message);
        }
    }
    
    public static void close() {
        if (fileHandler != null) {
            fileHandler.close();
        }
    }
} 