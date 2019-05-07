package com.robi.logger.kslogger;

import java.util.Random;

public class TestB {

    private static final KsLogger _ksLogger = KsLogger.getLogger(
        "TestB_logger",                             // Logger ID
        new KsLoggerConfig(
            KsLoggerConfig.LOGGER_MODE_ASYNC,       // Sync/Async mode
            KsLoggerConfig.LOGGER_OUTPUT_FILE,      // Sysout/File
            null,                                   // Default charset
            "./rb_logs",                            // Directory
            "rb",                                   // Prefix
            "yyyyMMdd",                             // Midfix
            "log"                                   // Suffix
        )
    );
    
    private static Random Rand = new Random();

    public static void log(int logType, Object... logObjs) {
        /*
        String wtId = "000";
		StackTraceElement[] stkTraceElem = new Throwable().getStackTrace();
		Object[] newLogObjs = new Object[logObjs.length + 2];
		int lastStkIdx = 1;

		for (int i = 2; i < newLogObjs.length; ++i) {
			newLogObjs[i] = logObjs[i - 2];
		}

		// 로그 헤더1 생성
		String logHeader1 = String.format("[%s]", wtId);

		// 로그 헤더2 생성
        StringBuilder logHeaderSb2 = new StringBuilder();
		int maxStkLimit = Math.min(stkTraceElem.length, 1);
        
        logHeaderSb2.append('[');

		if (logType != KsLogger.INFO) {
            logHeaderSb2.append(stkTraceElem[lastStkIdx].getFileName()).append("(Ln:")
                        .append(stkTraceElem[lastStkIdx].getLineNumber()).append(").");
			maxStkLimit = Math.min(stkTraceElem.length, 3); // 콜스택 3단계까지 출력
		}

		for (int j = maxStkLimit; j >= lastStkIdx; --j) {
			logHeaderSb2.append(stkTraceElem[j].getMethodName()).append("().");
		}

		logHeaderSb2.setLength(logHeaderSb2.length() - 1); // 마지막 '.' 제거
		logHeaderSb2.append("] ---");

		// 생성 해더 1,2를 대입
		newLogObjs[0] = logHeader1;
		newLogObjs[1] = logHeaderSb2.toString();
		logHeaderSb2.setLength(0);*/

        // 로그 출력
        /* old version logger
        _logger.log(logType, newLogObjs);
        */

        /* ks logger */
        if (!_ksLogger.log(logType, logObjs)) {
            System.out.println("Logger return false!");
        }
    }

    public static void ex1() {
        int sum = 3;
        for (int i = 3; i > -1; --i) {
            sum /= i;
        }
    }

    public static void ex2() {
        int[] ary = new int[3];
        int sum = 0;
        for (int i = 0; i < 4; ++i) {
            sum += ary[i];
        }
    }
    
    public static void eventCaller(String tId, int evtRemainCnt) throws Exception {
        eventFunc(tId, evtRemainCnt);
    }

    public static void eventFunc(String tId, int evtRemainCnt) throws Exception {
        int rand = Rand.nextInt(100);
        String logHeader = "[" + "tId:" + tId + "/wc:" + evtRemainCnt + "/ts:" + System.currentTimeMillis() + "]";
        
        if (rand == 0) { // DivZero Exception
            ex1();
        }
        else if (rand == 1) { // ArrayIdx Exception
            ex2();
        }
        else if (rand == 2) { // NullPtr Exception
            String str = null;
            str.length();
        }
        else if (rand == 3) { // Custom Exception
            throw new Exception("Custom EXCEPTION.");
        }
        else if (rand == 5) { // Null Info log            
            log(KsLogger.WARNING, logHeader, (Object)null);
        }
        else if (rand > 10 && rand < 16) { // Warn
            log(KsLogger.WARNING, logHeader, "Warning Log!");
        }
        else if (rand > 15 && rand < 21) { // Fatal
            log(KsLogger.FATAL, logHeader, "Fatal Log!");
        }
        else if (rand > 20 && rand < 26) { // Debug
            log(KsLogger.DEBUG, logHeader, "Debug Log!");
        }
        else { // Info
            log(KsLogger.INFO, logHeader, "Info Log!");
        }
    }

    public static class TestThread implements Runnable {
        private String tId;

        public TestThread(int tId) {
            this.tId = String.valueOf(tId);
        }

        @Override
        public void run() {
            int evtRemainCnt = TestWorkCnt;

            while (evtRemainCnt-- > 0) {
                try {
                    eventCaller(tId, evtRemainCnt);
                    
                    if (TestThreadDelay > 0) {
                        Thread.sleep(TestThreadDelay);
                    }
                }
                catch (Exception e) {
                    String logHeader = "[" + "tId:" + tId + "/wc:" + evtRemainCnt + "/ts:" + System.currentTimeMillis() + "]";
                    log(KsLogger.INFO, logHeader, "Exception Caused!", e, "eventFunc() Exception!");
                }
            }
        }
    }

    private static int TestWorkCnt = 2000;
    private static int TestThreadCnt = 500;
    private static int TestThreadDelay = 0;
    
    public static void main(String[] args) {
        Thread[] threadAry = new Thread[TestThreadCnt];

        System.out.println("- Total Thread Cnt : " + TestThreadCnt);
        System.out.println("- Each Thread Work Cnt : " + TestWorkCnt);
        System.out.println("- Total Work Cnt : " + (TestThreadCnt * TestWorkCnt));

        while (true) {
            System.out.println("- Initializing Threads...");

            for (int i = 0; i < threadAry.length; ++i) {
                threadAry[i] = new Thread(new TestThread(i));
            }
            
            System.out.println("- Working...");
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < threadAry.length; ++i) {
                threadAry[i].start();
            }

            for (int i = 0; i < threadAry.length; ++i) {
                try {
                    threadAry[i].join();
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            System.out.println("- Thread Works Done!");
            System.out.println("> Time Elapsed : " + (elapsedTime / 1000.0) + "ms");
            System.out.println("> Log per Sec : " + (TestThreadCnt * TestWorkCnt) / (elapsedTime / 1000.0) + "/sec");

            while (_ksLogger.isLoggerWorking()) {}

            elapsedTime = System.currentTimeMillis() - startTime;
            System.out.println("- Logger Done!");
            System.out.println("> Time Elapsed : " + (elapsedTime / 1000.0) + "ms");
            System.out.println("> Log per Sec : " + (TestThreadCnt * TestWorkCnt) / (elapsedTime / 1000.0) + "/sec");
            break;
            // try { Thread.sleep(10); } catch (Exception e) { e.printStackTrace(); }
        }
    }
}
