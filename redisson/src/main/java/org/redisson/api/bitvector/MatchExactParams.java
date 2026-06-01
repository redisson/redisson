package org.redisson.api.bitvector;

import java.time.Duration;

public final class MatchExactParams implements MatchExactArgs, MatchTargetArgs {

    long mask;
    long target;
    int chunkSize = 10;
    Duration chunkFetchTTL = Duration.ofMinutes(5);

    MatchExactParams(long mask) {
        this.mask = mask;
    }

    @Override
    public MatchExactArgs chunkSize(int value) {
        this.chunkSize = value;
        return this;
    }

    @Override
    public MatchExactArgs chunkFetchTTL(Duration value) {
        this.chunkFetchTTL = value;
        return this;
    }

    @Override
    public MatchExactArgs target(long value) {
        this.target = value;
        return this;
    }

    public long getMask() {
        return mask;
    }

    public long getTarget() {
        return target;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public Duration getChunkFetchTTL() {
        return chunkFetchTTL;
    }
}
