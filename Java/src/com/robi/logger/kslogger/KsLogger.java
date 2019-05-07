package com.robi.logger.kslogger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *  < 1. Async mode - LOW reliable vs HIGH performance >
 *                                                              +--[WriteResult]--+
 *                                                              V                 |
 *    +--------------+             +-----------------+      +-------------------+ |             +-[Console]----------+
 *  | | < Thread A > |             | Log    +------+ |      |    Log WRITER     | |  [ConMode]  |[ymd hms] [I] - ABC |
 *  | | Log("ABC");  | -[RawLog]-> |  S  -> | Heap | | -+   | +---------------+ | | +---[Log]-> |[ymd hms] [I] - 123 |
 *  | | add(a, b);   |             |  T     +------+ |  O---|>| WatcherThread | | +-|---[Rst]-- |[ymd hms] [I] - abc |
 *  V | Log("abc");  | -[RawLog]-> |  O  -> | Heap | | -+   | +---------------+ | | |           +--------------------+
 *    |--------------+             |  R     +------+ |  |   |         V         | | |           +-[pre.yyyymmdd.log]-+
 *  | | < Thread B > |             |  A  -> | .... | | -+   | +---------------+ | | | [FileMod] |[ymd hms] [I] - ABC |
 *  | | Log("123");  | -[RawLog]-> |  G     +------+ |      | | Log FORMATTER |-|-|-+---[Log]-> |[ymd hms] [I] - 123 |
 *  V | Log("...");  | -[RawLog]-> |  E     (N-Heap) |      | +---------------+ | +-----[Rst]-- |[ymd hms] [I] - abc |
 *    +--------------+             +-----------------+      +-------------------+               +--------------------+
 *    [ Work Threads ]             [                   KsLogger                   ]             [ Output Log Message ]
 * 
 * - Work thread will add log message into 'Log Storage' and move immediately to next logic
 * - Writer has 'own thread' to read log message from 'LogStorage' then write into file
 * - Log messag can be lossed when logger has faced critical problem like 'system shutdown', 'overdrive'
 *   , 'file I/O problem' and 'thread starvation/livelock' (etc...)
 * - The time trying to write log is always correct but, real time written into file can be different
 * 
 * 
 *  < 2. Sync mode - LOW performance vs HIGH reliable >
 * 
 *                                +--------[WriteResult]--------+
 *                                |                             |
 *    +--------------+            V   +-----+                +-----+           +-[Console]----------+
 *    | < Thread A > | -[RawLog]--|-> | Log | --[Log]---+    | Log | [ConMode] |[ymd hms] [I] - ABC |
 * +- | Log("ABC");  |            |   |  F  |           |    |  W  | --[Log]-> |[ymd hms] [I] - 123 |
 * |  | add(a, b);   | <-[Result]-+   |  O  |           O--> |  R  | <-[Rst]-- |                    |
 * +- | Log("abc");  |            |   |  R  |           |    |  I  |           +--------------------+
 * V  |--------------+            |   |  M  |           |    |  T  |           +-[pre.yyyymmdd.log]-+
 *    | < Thread B > | -[RawLog]--|-> |  A  | --[Log]---+    |  E  | [FileMod] |[ymd hms] [I] - ABC |
 * +- | Log("123");  |            |   |  T  |                |  R  | --[Log]-> |[ymd hms] [I] - 123 |
 * |- | Log("...");  | <-[Result]-+   | TER |  (Bottlenect)  |     | <-[Rst]-- |                    |
 * V  +--------------+                +-----+                +-----+           +--------------------+
 *    [ Work Threads ]                [          KsLogger          ]           [ Output Log Message ]
 * 
 * - Log message will add into 'Log Writter' then write info file ('Log Writer' doesn't have own thread)
 * - Work thread will be blocked until log writing be totally completed
 * - When log requests are overdrive, all logic will be halted because spend all time to write log file
 * 
 */

public class KsLogger {
    // [Class private variables]
    // - Logger Management Map
    private static ConcurrentMap<String, KsLogger> loggerManagementMap = null;
    // - Logger ID
    private String loggerId = null;
    // - Initialized
    private boolean initialized = false;
    // - Logger Config
    private KsLoggerConfig loggerConfig = null;
    // - Log Formatter
    private KsLogFormatter logFormatter = null;
    // - Log Storage
    private KsLogStorage logStorage = null;
    // - Log Writter
    private KsLogWriter logWriter = null;

    // [Static initializer]
    static {
        loggerManagementMap = new ConcurrentHashMap<String, KsLogger>();
    }

    // [Class public constants]
    // - Log levels
    public static final int DEBUG = 1, DBG = 1;
    public static final int INFO  = 2, INF = 2;
    public static final int TRACE = 3, TRC = 3;
    public static final int WARN  = 4, WAN = 4, WARNING = 4;
    public static final int ERROR = 5, ERR = 5;
    public static final int FATAL = 6, FAT = 6;

    // [Constructor]
    private KsLogger(String loggerId) {
        this.loggerId = loggerId;
        this.initialized = false;
        this.loggerConfig = null;
        this.logFormatter = null;
        this.logStorage = null;
        this.logWriter = null;
    }

    // [Public methods]
    // Get initialized instance
    public static KsLogger getLogger(String loggerId, KsLoggerConfig customConfig) {
        if (loggerId == null) {
            return null;
        }

        KsLogger rtLogger = null;

        synchronized (KsLogger.class) {
            if ((rtLogger = loggerManagementMap.get(loggerId)) == null) {
                rtLogger = new KsLogger(loggerId);
                loggerManagementMap.put(loggerId, rtLogger);
            }
        
            if (!rtLogger.initialized) {
                if (!rtLogger.initialize(customConfig)) {
                    System.out.println("Logger : FAIL to initialize logger!");
                    return null;
                }
            }
            else {
                System.out.println("Logger : Can NOT change initialized logger's config!");
            }
        }

        return rtLogger;
    }

    // Check logger is working or not (for async mode)
    public boolean isLoggerWorking() {
        if (this.loggerConfig.getLoggerSyncMode() == KsLoggerConfig.LOGGER_MODE_ASYNC) {
            if (this.logStorage.getStoredLogCount() == 0) {
                return false;
            }
        }

        return true;
    }

    // Logger destroyer
    public void destroy() {
        this.initialized = false;
        this.loggerConfig = null;
        
        if (this.logFormatter != null) {
            this.logFormatter.destroy();
        }

        if (this.logStorage != null) {
            this.logStorage.destroy();
        }

        if (this.logWriter != null) {
            this.logWriter.destroy();
        }

        loggerManagementMap.remove(this.loggerId);
        this.loggerId = null;
    }

    // Logging method
    public boolean log(int level, Object... msgObjs) {
        long curTime = System.currentTimeMillis();

        try {
            if (!this.initialized) {
                System.out.println("Logger : Logger is NOT initialized!");
                return false;
            }

            int logSyncMode = this.loggerConfig.getLoggerSyncMode();
            KsLogMsg logMsg = new KsLogMsg(curTime, level, msgObjs, null);

            if (logSyncMode == KsLoggerConfig.LOGGER_MODE_SYNC) {
                String message = null;
            
                if ((message = this.logFormatter.makeFormattedMessage(logMsg)) == null) {
                    System.out.println("Logger : Formatter returns null!");
                    return false;
                }

                // Bottleneck of sync mode (synchronized method)
                return this.logWriter.tryLogWriting(logMsg);
            }
            else if (logSyncMode == KsLoggerConfig.LOGGER_MODE_ASYNC) {
                return this.logStorage.putIntoStorage(logMsg);
            }
            else {
                System.out.println("Logger : Undefined 'logSyncMode(" + logSyncMode + ")'!");
                return false;
            }
        }
        catch (Exception e) {
            System.out.println("Logger : Exception while logging!");
            e.printStackTrace();
            return false;
        }
    }

    // [Private methods]
    // Initalizer
    private boolean initialize(KsLoggerConfig customConfig) {
        try {
            this.loggerConfig = customConfig;

            if (this.logFormatter == null) {
                this.logFormatter = new KsLogFormatter();
            }

            if (!this.logFormatter.initialize(customConfig)) {
                System.out.println("Logger : LogFormatter Initialize FAILED!");
                return false;
            }
        
            if (this.logStorage == null) {
                this.logStorage = new KsLogStorage();
            }

            if (!this.logStorage.initialize(customConfig)) {
                System.out.println("Logger : LogStorage Initialize FAILED!");
                return false;
            }

            if (this.logWriter == null) {
                this.logWriter = new KsLogWriter();
            }

            if (!this.logWriter.initialize(customConfig, this.logStorage, this.logFormatter)) {
                System.out.println("Logger : LogWriter Initialize FAILED!");
                return false;
            }
        }
        catch (Exception e) {
            System.out.println("Logger : Initialization FAILED because of Exception!");
            e.printStackTrace();
            return false;
        }

        return (this.initialized = true);
    }

    // [Get/Set/Overrides]
    public boolean isInitialized() {
        return this.initialized;
    }
}