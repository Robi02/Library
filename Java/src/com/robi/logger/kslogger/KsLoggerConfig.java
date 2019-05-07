package com.robi.logger.kslogger;

import java.nio.charset.Charset;

public class KsLoggerConfig {
    // - Logger mode (sync:default/async)
    public static final int LOGGER_MODE_DEFAULT = 0;
    public static final int LOGGER_MODE_SYNC    = 0;
    public static final int LOGGER_MODE_ASYNC   = 1;
    private int loggerSyncMode = LOGGER_MODE_DEFAULT;

    // - Logger out (sysout:default/file)
    public static final int LOGGER_OUTPUT_DEFAULT = 0;
    public static final int LOGGER_OUTPUT_SYSOUT  = 0;
    public static final int LOGGER_OUTPUT_FILE    = 1;
    private int loggerOutputMode = LOGGER_OUTPUT_DEFAULT;
    private String loggerOutputCharset = null;  // default : "SYSTEM_CHARSET"
    // - Logger out (file)
    private String outputFileDir = null;    // default : "./log"
    private String outputFilePrefix = null; // default : ""
    private String outputFileMidfix = null; // default : "{yyyyMMdd}"
    private String outputFileSuffix = null; // default : "log"
    private String outputFileName = null;   // {outputFilePrefix}.{outputFileMidfix}.{outputFileSuffix}
    private String outputFilePath = null;   // {fileOutputDir}/{outputFileName}

    // [Constructors]
    public KsLoggerConfig() {
        setLoggerSyncMode(LOGGER_MODE_DEFAULT);
        setLoggerOutputModes(LOGGER_OUTPUT_DEFAULT, null, null, null, null, null);
    }

    public KsLoggerConfig(int loggerSyncMode, int loggerOutputMode,
                            String loggerOutputCharset,
                            String outputFileDir, String outputFilePrefix, 
                            String outputFileMidfix, String outputFileSuffix) {
        setLoggerSyncMode(loggerSyncMode);
        setLoggerOutputModes(loggerOutputMode, loggerOutputCharset, outputFileDir, outputFilePrefix,
                            outputFileMidfix, outputFileSuffix);
    }

    // [Get/Set/Overrides]
    public int getLoggerSyncMode() {
        return this.loggerSyncMode;
    }

    public void setLoggerSyncMode(int loggerSyncMode) {
        if (loggerSyncMode != LOGGER_MODE_SYNC && loggerSyncMode != LOGGER_MODE_ASYNC) {
            loggerSyncMode = LOGGER_MODE_DEFAULT;
        }

        this.loggerSyncMode = loggerSyncMode;
    }

    public int getOutputMode() {
        return this.loggerOutputMode;
    }

    public String getLoggerOutputCharset() {
        return this.loggerOutputCharset;
    }

    public String getOutputFileDir() {
        return this.outputFileDir;
    }

    public String getOutputFileName() {
        return this.outputFileName;
    }

    public String getOutputFilePath() {
        return this.outputFilePath;
    }

    public void setLoggerOutputModes(int loggerOutputMode, String loggerOutputCharset,
                                        String outputFileDir, String outputFilePrefix,
                                        String outputFileMidfix, String outputFileSuffix) {
        if (loggerOutputMode != LOGGER_OUTPUT_SYSOUT && loggerOutputMode != LOGGER_OUTPUT_FILE) {
            loggerOutputMode = LOGGER_OUTPUT_DEFAULT;
        }

        this.loggerOutputMode = loggerOutputMode;

        if (loggerOutputCharset == null) {
            if (this.loggerOutputCharset == null || this.loggerOutputCharset.length() == 0) {
                loggerOutputCharset = Charset.defaultCharset().toString();
            }
        }

        this.loggerOutputCharset = loggerOutputCharset;

        if (this.loggerOutputMode == LOGGER_OUTPUT_FILE) {
            // Default : Dir:"./log", Prefix:"", Midfix:"{yyyyMMdd}", Suffix:"log"
            if (outputFileDir == null) {
                if (this.outputFileDir == null || this.outputFileDir.length() == 0) {
                    outputFileDir = "./log";
                }
                else {
                    outputFileDir = this.outputFileDir;
                }
            }

            if (outputFilePrefix == null) {
                if (this.outputFilePrefix == null) { // zero-length prefix allowed
                    outputFilePrefix = "";
                }
                else {
                    outputFilePrefix = this.outputFilePrefix;
                }
            }

            if (outputFileMidfix == null) {
                if (this.outputFileMidfix == null || this.outputFileMidfix.length() == 0) {
                    outputFileMidfix = "{yyyyMMdd}";
                }
                else {
                    outputFileMidfix = this.outputFileMidfix;
                }
            }

            if (outputFileSuffix == null) {
                if (this.outputFileSuffix == null || this.outputFileSuffix.length() == 0) {
                    outputFileSuffix = "log";
                }
                else {
                    outputFileSuffix = this.outputFileSuffix;
                }
            }
        
            this.outputFileDir = outputFileDir;
            this.outputFilePrefix = outputFilePrefix;
            this.outputFileMidfix = outputFileMidfix;
            this.outputFileSuffix = outputFileSuffix;
            
            this.outputFileName = (this.outputFilePrefix + (this.outputFilePrefix.length() == 0 ? "" : ".") +
                                   this.outputFileMidfix + (this.outputFileMidfix.length() == 0 ? "" : ".") +
                                   this.outputFileSuffix);

            this.outputFilePath = (this.outputFileDir + "/" + this.outputFileName);
        }

        System.out.println(this.loggerOutputMode);
        System.out.println(this.loggerOutputCharset);
        System.out.println(this.outputFileDir);
        System.out.println(this.outputFilePrefix);
        System.out.println(this.outputFileMidfix);
        System.out.println(this.outputFileSuffix);
        System.out.println(this.outputFileName);
        System.out.println(this.outputFilePath);
    }
}
