package com.robi.logger.kslogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class KsLogStorage {
    // - Initailized
    private boolean initialized;
    // - Logger config
    private KsLoggerConfig loggerConfig;
    // - Blocking queue for log storaging
    private List<PriorityBlockingQueue<KsLogMsg>> storageList;

    // [Public methods]
    // Initalizer
    public boolean initialize(KsLoggerConfig customConfig) {
        // -Logger config
        this.loggerConfig = customConfig;

        // - Blocking 'priority queue'(heap) for log storaging
        storageList = new ArrayList<PriorityBlockingQueue<KsLogMsg>>();

        for (int i = 0; i < 2; ++i) {
            storageList.add(new PriorityBlockingQueue<KsLogMsg>(512));
        }

        return (this.initialized = true);
    }

    // Destroy storage
    public void destroy() {
        this.initialized = false;
        this.loggerConfig = null;

        int listSize = this.storageList.size();

        for (int i = 0; i < listSize; ++i) {
            this.storageList.get(i).clear();
        }

        this.storageList.clear();
        this.storageList = null;
    }

    // Get storage stored log count
    public int getStoredLogCount() {
        int storeListSize = this.storageList.size();
        int totalCount = 0;

        for (int i = 0; i < storeListSize; ++i) {
            totalCount += this.storageList.get(i).size();
        }

        return totalCount;
    }

    // Insert log message into storage
    public boolean putIntoStorage(KsLogMsg logMsg) {
        if (!this.initialized) {
            System.out.println("LogStorage : LogStorage NOT initialized!");
            return false;
        }

        if (logMsg == null) {
            System.out.println("LogStorage : 'logMsg' is null!");
            return false;
        }
    
        PriorityBlockingQueue<KsLogMsg> targetQueue = null;
        PriorityBlockingQueue<KsLogMsg> tempQueue = null;
        int listSize = this.storageList.size();
        int curMinSize = Integer.MAX_VALUE;
        int curSize = 0;

        for (int i = 0; i < listSize; ++i) {
            tempQueue = this.storageList.get(i);
            curSize = tempQueue.size();
            
            if (curMinSize > curSize) {
                targetQueue = tempQueue;
                curMinSize = curSize;
            }
        }

        int offerTryRemainCnt = 3;

        try {
            while ((offerTryRemainCnt--) > 0) { 
                if (targetQueue != null) {
                    if (!targetQueue.offer(logMsg, 1000L, TimeUnit.MILLISECONDS)) { // bottlenect
                        Thread.sleep(1);
                        continue;
                    }
                }

                return true; // only way to return true
            }
        }
        catch (InterruptedException e) {
            System.out.println("LogStorage : Interrupted Exception!");
            e.printStackTrace();
            return false;
        }

        return false;
    }

    // Get saved message from storage
    public KsLogMsg getLogFromStorage() {
        if (!this.initialized) {
            System.out.println("LogStorage : LogStorage NOT initialized!");
            return null;
        }

        PriorityBlockingQueue<KsLogMsg> targetQueue = null;
        PriorityBlockingQueue<KsLogMsg> tempQueue = null;
        int listSize = this.storageList.size();
        int curMaxSize = 0;
        int curSize = 0;

        for (int i = 0; i < listSize; ++i) {
            tempQueue = storageList.get(i);
            curSize = tempQueue.size();
            
            if (curMaxSize < curSize) {
                targetQueue = tempQueue;
                curMaxSize = curSize;
            }
        }

        if (curMaxSize == 0) {
            return null;
        }

        int pollTryRemainCnt = 3;
        KsLogMsg rtMsg = null;

        try {
            while ((pollTryRemainCnt--) > 0) {
                if (targetQueue != null) {
                    if ((rtMsg = targetQueue.poll(1000L, TimeUnit.MILLISECONDS)) == null) {
                        Thread.sleep(1);
                        continue;
                    }

                    return rtMsg; // only way to return LogMsg object
                }
            }
        }
        catch (InterruptedException e) {
            System.out.println("LogStorage : Interrupted Exception!");
            e.printStackTrace();
            return null;
        }

        return null;
    }
}