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
 *    |--------------+             |  R     +------+ |  |   |         V         | | |           +-[File]-------------+
 *  | | < Thread B > |             |  A  -> | .... | | -+   | +---------------+ | | | [FileMod] |[ymd hms] [I] - ABC |
 *  | | Log("123");  | -[RawLog]-> |  G     +------+ |      | | Log FORMATTER |-|-|-+---[Log]-> |[ymd hms] [I] - 123 |
 *  V | Log("...");  | -[RawLog]-> |  E     (N-Heap) |      | +---------------+ | +-----[Rst]-- |[ymd hms] [I] - abc |
 *    +--------------+             +-----------------+      +-------------------+               +--------------------+
 *    [ Work Threads ]             [                  KsLogger                  ]               [ Output Log Message ]
 * 
 * - Work thread will add log message into 'Log Storage' and move immediately to next logic
 * - Writer has 'own thread'(as named as watcher) to read log message from 'LogStorage' then write into file
 * - Log messag can be lossed when logger has faced critical problem like 'system shutdown', 'overdrive'
 *   , 'file I/O problem' and 'thread starvation/livelock' (etc...)
 * - The time trying to write log is always correct but, the 'real time' written into file can be different
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
 * V  |--------------+            |   |  M  |           |    |  T  |           +-[File]-------------+
 *    | < Thread B > | -[RawLog]--|-> |  A  | --[Log]---+    |  E  | [FileMod] |[ymd hms] [I] - ABC |
 * +- | Log("123");  |            |   |  T  |                |  R  | --[Log]-> |[ymd hms] [I] - 123 |
 * |- | Log("...");  | <-[Result]-+   | TER |                |     | <-[Rst]-- |                    |
 * V  +--------------+                +-----+                +-----+           +--------------------+
 *    [ Work Threads ]                [          KsLogger          ]           [ Output Log Message ]
 * 
 * - Log message will add directly into 'Log Writter' then write info file (in this case, 'Log Writer' doesn't have own thread)
 * - Work thread will be blocked until log writing be totally completed and return write result
 * - When log requests are extreamly overdrive, all logic will be halted because system spend almost time to write log file
 * 
 */

/**
 *  [ Test Case #1 (50-Thread, 1000000-Log) ]
 *  - async mode (1-Heap)
 *   > Insertion time (async): 2.342s / 426,985LPS (Log Per Second)
 *   > Write end time (async): 22.181s / 43,896LPS
 *  - sync mode
 *   > Insertion/Write end time (sync): 29.584s / 33,802LPS
 * 
 *  *  [ Test Case #2 (50-Thread, 1000000-Log) ]
 *  - async mode (5-Heap)
 *   > Insertion time (async): 2.17s / 460,829LPS
 *   > Write end time (async): 22.92s / 43,630LPS
 * 
 *  *  [ Test Case #3 (50-Thread, 1000000-Log) ]
 *  - async mode (2-Heap)
 *   > Insertion time (async): 3.07s / 324,675LPS
 *   > Write end time (async): 19.765s / 50,594LPS
 */

 // @@ 다음: Writer의 Watcher 스래드 미사용시 죽여놓는 로직을 추가합시다.
 // @@ 로그출력 파일명 작성되는 부분 수정합시다
 // @@ 코드 날로짠부분 정리합시다.

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
        if (loggerManagementMap == null) {
            loggerManagementMap = new ConcurrentHashMap<String, KsLogger>();
        }
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