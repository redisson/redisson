package org.redisson.api.map;

public abstract class RetryableWriter {
    private final static int DEFAULT_RETRY_ATTEMPTS = 0;
    private final static long DEFAULT_RETRY_INTERVAL = 100;

    //max retry times
    private final int retryAttempts;
    private final long retryInterval;


    public RetryableWriter() {
        this(DEFAULT_RETRY_ATTEMPTS, DEFAULT_RETRY_INTERVAL);
    }

    public RetryableWriter(int retryAttempts) {
        this(retryAttempts, DEFAULT_RETRY_INTERVAL);
    }

    //todo add TimeUnit param
    public RetryableWriter(int retryAttempts, long retryInterval) {
        if (retryInterval < 0 || retryAttempts < 0) {
            throw new IllegalArgumentException("retryAttempts and retryInterval must be positive");
        }

        this.retryAttempts = retryAttempts;
        this.retryInterval = retryInterval;
    }

    public abstract Object getNoRetriesForWrite();

    public abstract Object getNoRetriesForDelete();

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

}
