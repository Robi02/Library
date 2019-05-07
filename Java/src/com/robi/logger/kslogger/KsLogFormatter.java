package com.robi.logger.kslogger;

import java.text.SimpleDateFormat;

public class KsLogFormatter {
    // [Class private constants]
    private static final String STR_BRACKET_L = "[", STR_BRACE_L = "{", STR_SBRACE_L = "(";
    private static final String STR_BRACKET_R = "]", STR_BRACE_R = "}", STR_SBRACE_R = ")";
    private static final String STR_AT = " at ";
    private static final String STR_SLASH = " / ";
    private static final String STR_BLANK = " ";
    private static final String STR_NULL = "null";
    private static final String[] STR_ARY_LEVEL = { "SYS  ", "DEBUG", "INFO ", "TRACE",
                                                    "WARN ", "ERROR", "FATAL" };

    // [Class private variables]
    // - Initalized
    private boolean initialized = false;
    // - Log formatter
    private SimpleDateFormat simpleDateFmt = null;
    private String cachedDateStr = null;
    private int lastSec = -1;

    // [Constructor]
    public KsLogFormatter() {}

    // [Public methods]
    // Initalizer
    public boolean initialize(KsLoggerConfig customConfig) {
        if (this.simpleDateFmt == null) {
            this.simpleDateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }

        this.lastSec = -1;

        return (this.initialized = true);
    }

    // Destroy Formatter
    public void destroy() {
        this.simpleDateFmt = null;
        this.initialized = false;
    }

    // Formatter
    public String makeFormattedMessage(KsLogMsg logMsg) {
        if (!this.initialized) {
            System.out.println("LogFormatter : Formatter NOT Initialized!");
            return null;
        }

        // TimestampStr
        long timeStamp = logMsg.getTimeStamp();
        String timeStampStr = null;
        
        if ((timeStampStr = makeTimestampStr(timeStamp)) == null) { // synchornized work
            timeStampStr = String.valueOf(timeStamp);
        }
        
        // LogLevelStr
        String levelStr = makeLevelStr(logMsg.getLevel());
        
        // Make 'msgStr' (Object[] to String)
        String msgStr = makeObjectAryToString(logMsg.getLogObjAry());

        // Fmt: [yyyy-MM-hh HH:mm:ss] [levelStr] [ThreadName] (msgStr)
        // Ex): [2019-05-03 13:18:10] [INFO ] [Thread-0] Hello Logger!
        StringBuilder msgSb = new StringBuilder();
        return msgSb.append(STR_BRACKET_L).append(timeStampStr).append(STR_BRACKET_R).append(STR_BLANK)
                    .append(STR_BRACKET_L).append(levelStr).append(STR_BRACKET_R).append(STR_BLANK)
                    .append(STR_BRACKET_L).append(Thread.currentThread().getName()).append(STR_BRACKET_R).append(STR_BLANK)
                    .append(msgStr).toString();
    }

    // [Private methods]
    // Make timestamp string from long value (internally using cached string for best performance)
    private String makeTimestampStr(long timeStamp) {
        int timeSec = (int)(timeStamp / 1000L) % 60;

        synchronized (this) {
            // when exactly 60sec blokcing happend, that could make 'past timestamp' return issue
            // ex) Correct: "yyyy-MM-dd HH:11:00" -> Return: "yyyy-MM-dd HH:10:00"
            if (timeSec != lastSec || cachedDateStr == null) {
                cachedDateStr = simpleDateFmt.format(timeStamp); // SimpleDateFormat is NOT thread-safe!
                lastSec = timeSec;
            }
            
            return cachedDateStr;
        }
    }

    // Make level string from int value
    private String makeLevelStr(int level) {
        try {
            return STR_ARY_LEVEL[level];
        }
        catch (ArrayIndexOutOfBoundsException e) {
            return STR_ARY_LEVEL[0];
        }
    }

    // Make log message string from object array (handle null and exceptions)
    private String makeObjectAryToString(Object... logObjs) {
        if (logObjs == null) {
            return STR_NULL;
        }

        StringBuilder msgSb = new StringBuilder();
        Object logObj = null;

        for (int i = 0; i < logObjs.length; ++i) {
            logObj = logObjs[i];

            if (logObj == null) {
                msgSb.append(STR_NULL);
            }
            else if (logObj instanceof Throwable) {
                // "Exception(Message) at { StackTrace / StackTrace / StackTrace }"
                Throwable throwObj = (Throwable)logObj;

				// "Exception(Message) at { "
                msgSb.append(throwObj.toString()).append(STR_SBRACE_L).append(throwObj.getMessage()).append(STR_SBRACE_R)
                     .append(STR_AT).append(STR_BRACE_L).append(STR_BLANK);
	
				for (StackTraceElement stkElem : throwObj.getStackTrace()) { 
                    // "StackTrace / StackTrace / ..."
					msgSb.append(stkElem.toString()).append(STR_SLASH);
				}

				msgSb.setLength(msgSb.length() - STR_SLASH.length()); // remove last " / " string
				msgSb.append(STR_BLANK).append(STR_BRACE_R); // " }"
            }
            else {
                msgSb.append(logObj.toString());
            }

            msgSb.append(STR_BLANK); // each log objs's divider " "
        }

        return msgSb.toString();
    }
}