package com.robi.logger.kslogger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 *  < 1. Async mode >
 * 
 * -----------+ [RawLog] +-----------------+   [Console]  +------------+
 * ...        | -------> | Watcher Thread  |  +---------> |[12] Hello! |
 *            | <------- |-----------------|  |           +------------+
 *    ...     | [ReqLog] | LogFormatter    |  |
 *            |          | tryLogWriting() |  | [File]    +------------+
 *      ...   |          | writeLog()      | -+---------> |[12] Hello! |
 * -----------+          +-----------------+              +------------+
 * [LogStorage]          [    LogWriter    ]              [ OutputLogs ]
 * 
 * 
 *  < 2. Sync mode >
 * 
 * -----------+          +-----------------+   [Console]  +------------+
 * ...        | -------> | tryLogWriting() |  +---------> |[12] Hello! |
 *    ...     |   [Log]  | ...             |  |           +------------+
 *  Log       |          |                 |  |
 *  Formatter |          | writeLog()      |  | [File]    +------------+
 *      ...   |          | ...             | -+---------> |[12] Hello! |
 * -----------+          +-----------------+              +------------+
 *  [ Logger ]           [    LogWriter    ]              [ OutputLogs ]
 * 
 */

public class KsLogWriter {
    // [Class private variables]
    // - Initailized
    private boolean initialized;
    // - Logger config
    private KsLoggerConfig loggerConfig;
    // - Log formatter
    private KsLogFormatter logFormatter;
    // - Log writer stream
    private boolean requireWriterRefresh = true;
    private File logDir = null;
    private File logFile = null;
    private BufferedWriter logBufWriter = null;
    private OutputStreamWriter logOsWriter = null;
    private FileOutputStream fileOutputStream = null;
    // - Aync mode watcher class
    private KsLogStorage logStorage = null;
    private KsLogWriterWatcher watcherRunnable = null;
    private Thread watcherThread = null;

    private class KsLogWriterWatcher implements Runnable {

        public boolean running;
        public long lastWriteTime;

        public KsLogWriterWatcher() {
            this.running = false;
            this.lastWriteTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            this.running = true;

            while (this.running) {
                long curTime = System.currentTimeMillis();
                KsLogMsg logMsg = null;

                try {
                    if ((logMsg = logStorage.getLogFromStorage()) == null) {
                        long idleTime = curTime - this.lastWriteTime;

                        if (idleTime > 10000L) {
                            this.running = false; // 10sec no log -> destory watcher thread
                            continue;
                        }
                        else if (idleTime > 1000L) {
                            Thread.sleep(1); // prevent watcher thread's overdrive
                            continue;
                        }
                    }

                    this.lastWriteTime = curTime;
                    tryLogWriting(logMsg);
                }
                catch (Exception e) {
                    System.out.println("LogWriterWatcher : Exception caused!");
                    e.printStackTrace();
                }
            }
        }
    }

    // [Constructor]
    public KsLogWriter() {}

    // [Public methods]
    // Initalizer
    public boolean initialize(KsLoggerConfig customConfig, KsLogStorage logStorage,
                                KsLogFormatter logFormatter) {
        // - Logger config
        this.loggerConfig = customConfig;
        // - Log Storage
        this.logStorage = logStorage;
        // - Log Formatter
        this.logFormatter = logFormatter;
        // - Log writer stream
        this.requireWriterRefresh = true;
        // - Log Watcher
        if (customConfig.getLoggerSyncMode() == KsLoggerConfig.LOGGER_MODE_ASYNC) {
            this.watcherRunnable = new KsLogWriterWatcher();
            this.watcherThread = new Thread(watcherRunnable);
            this.watcherThread.start();
        }

        return (this.initialized = true);
    }

    // Destroy Writter
    public void destroy() {
        this.initialized = false;
        this.loggerConfig = null;
        this.logDir = null;
        this.logFile = null;
        closeWriterResoruces();
        this.logStorage = null;
        this.logFormatter = null;
        if (this.watcherRunnable != null && this.watcherRunnable.running) {
            this.watcherRunnable.running = false;
        }
        this.watcherRunnable = null;
    }

    // This method is ONLY way to write log
    public synchronized boolean tryLogWriting(KsLogMsg logMsg) {
        if (logMsg == null) {
            return true;
        }

        // If log message not formatted, do formatting
        if (logMsg.getMessage() == null) {
            logMsg.setMessage(this.logFormatter.makeFormattedMessage(logMsg));
        }

        // Writing and return result
        return writeLog(logMsg.getMessage());
    }
    
    // [Private methods]
    // Refresh file class and buffered writer with close old resources
    private synchronized boolean writerRefresh() {
        // Close old resoruces
        if (!closeWriterResoruces()) {
            System.out.println("LogWriter : FAIL to close old resoruces!");
            return false;
        }

        // Open new resources
        try {
            // Directory
            String logDirStr = this.loggerConfig.getOutputFileDir();

            this.logDir = new File(logDirStr);

            if (!this.logDir.exists()) {
                if (!this.logDir.mkdirs()) {
                    System.out.println("LogWriter : FAIL to make log directory '" +
                                        logDirStr + "'. Check permission or disk state!");
                    return false;
                }
            }

            // File
            String logPathStr = this.loggerConfig.getOutputFilePath(); // Path = fileDir + fileName

            this.logFile = new File(logPathStr);

            if (!this.logFile.exists()) {
                if (!this.logFile.createNewFile()) {
                    System.out.println("LogWriter : FAIL to make new log file '" +
                                        logPathStr + "'. Check permission or disk state!");
                    return false;
                }
            }

            // Buffered writer
            this.fileOutputStream = new FileOutputStream(this.logFile, true); // true: appending mode
            this.logOsWriter = new OutputStreamWriter(this.fileOutputStream,
                                                        this.loggerConfig.getLoggerOutputCharset());
            this.logBufWriter = new BufferedWriter(this.logOsWriter);

            // Require writer refresh switch
            requireWriterRefresh = false;
        }
        catch (SecurityException e) {
            System.out.println("LogWriter : SecurityException caused while refreshing writer!");
            e.printStackTrace();
            return false;
        }
        catch (IOException e) {
            System.out.println("LogWriter : IOException caused while refreshing writer!");
            e.printStackTrace();
            return false;
        }
        
        return true;
    }

    // Close writer resoruces
    private synchronized boolean closeWriterResoruces() {
        boolean rtResult = true;

        // Close old resources
        try {
            if (this.logBufWriter != null) {
                this.logBufWriter.close();
            }

            if (this.logOsWriter != null) {
                this.logOsWriter.close();
            }

            if (this.fileOutputStream != null) {
                this.fileOutputStream.close();
            }
        }
        catch (IOException e) {
            System.out.println("LogWriter : Exception caused while close old resoruces!");
            e.printStackTrace();
            rtResult = false;
        }
        finally {
            this.logBufWriter = null;
            this.logOsWriter = null;
            this.fileOutputStream = null;
        }

        return rtResult;
    }

    // Write log into sysout or file - log writting work must be synchorinized method
    private synchronized boolean writeLog(String logStr) {
        try {            
            int outputLogMode = this.loggerConfig.getOutputMode();

            if (outputLogMode == KsLoggerConfig.LOGGER_OUTPUT_SYSOUT) {
                System.out.println(logStr);
                return true;
            }
            else if (outputLogMode == KsLoggerConfig.LOGGER_OUTPUT_FILE) {
                int writeTryRemainCnt = 3;

                while ((--writeTryRemainCnt) > -1) {
                    if (this.requireWriterRefresh) {
                        if (!writerRefresh()) {
                            System.out.println("LogWriter : writerRefresh FAILED! the log message '" +
                                                logStr + "' will be lose!");
                            return false;
                        }
                    }

                    try {
                        this.logBufWriter.write(logStr);
                        this.logBufWriter.newLine();
                        this.logBufWriter.flush();
                        return true; // Only way to return 'true'
                    }
                    catch (IOException e) {
                        // Prevent file and directory move, delete... something potential problems
                        System.out.println("LogWriter : IOException! writeLog() FAILED! Retry refresh... (writeTryRemainCnt:" +
                                            writeTryRemainCnt + ")");
                        e.printStackTrace();
                        this.requireWriterRefresh = true;
                        Thread.sleep(1); // When sync mode, could bring work thread bottleneck here...
                        continue;
                    }
                }

                System.out.println("LogWriter : Critical problem has occured. The log message '" +
                                    logStr + "' will be lose.");
                return false;
            }
            else {
                System.out.println("LogWriter : Undefined 'outputLogMode(" + outputLogMode + ")'!");
                return false;
            }
        }
        catch (Exception e) {
            System.out.println("LogWriter : Exception while wirteLog! The log msg '" +
                                logStr + "' will be lose!");
            e.printStackTrace();
            return false;
        }
    }

    // [Get/Set/Overrides]
    public boolean isInitialized() {
        return this.initialized;
    }

    public void setInitailized(boolean initialized) {
        this.initialized = initialized;
    }
}
