package com.robi.logger.kslogger;

public class KsLogMsg implements Comparable<KsLogMsg> {
    // [Class private variables]
    // - log data
    private long timeStamp;
    private int level;
    private Object[] logObjAry;
    private String message;

    // [Constructors]
    public KsLogMsg() {
        this.timeStamp = -1;
        this.level = -1;
        this.logObjAry = null;
        this.message = null;
    }

    public KsLogMsg(long timeStamp, int level, Object[] logObjAry, String message) {
        this.timeStamp = timeStamp;
        this.level = level;
        this.logObjAry = logObjAry;
        this.message = message;
    }

    // [Get/Set/Overrides]
    public void set(long timeStamp, int level, Object[] logObjAry, String message) {
        this.timeStamp = timeStamp;
        this.level = level;
        this.logObjAry = logObjAry;
        this.message = message;
    }

    public long getTimeStamp() {
        return this.timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public int getLevel() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Object[] getLogObjAry() {
        return this.logObjAry;
    }

    public void setLogObjAry(Object[] logObjAry) {
        this.logObjAry = logObjAry;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public int compareTo(KsLogMsg ksLogMsg) { // Low timestamp == High priority
        return (int)(this.getTimeStamp() - ksLogMsg.getTimeStamp());
    }

    @Override
    public String toString() {
        return ("{timeStamp:" + this.timeStamp +
                ",level:" + this.level +
                ",logObjAry:" + this.logObjAry.toString() +
                ",message:" + this.message +
                "}");
    }
}
