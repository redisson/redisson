package org.redisson.api.bitvector;

import java.time.Duration;

public final class MatchParams implements MatchArgs {

    long mask;
    int chunkSize = 10;
    Duration chunkFetchTTL = Duration.ofMinutes(5);

    MatchParams(long mask) {
        this.mask = mask;
    }

    @Override
    public MatchArgs chunkSize(int value) {
        this.chunkSize = value;
        return this;
    }

    @Override
    public MatchArgs chunkFetchTTL(Duration value) {
        this.chunkFetchTTL = value;
        return this;
    }

    public long getMask() {
        return mask;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public Duration getChunkFetchTTL() {
        return chunkFetchTTL;
    }
}
