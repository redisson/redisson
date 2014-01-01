// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis;

import com.lambdaworks.redis.protocol.CommandArgs;

import java.util.*;

import static com.lambdaworks.redis.protocol.CommandKeyword.*;

/**
 * Argument list builder for the redis <a href="http://redis.io/commands/zunionstore">ZUNIONSTORE</a>
 * and <a href="http://redis.io/commands/zinterstore">ZINTERSTORE</a> commands. Static import the
 * methods from {@link Builder} and chain the method calls: <code>weights(1, 2).max()</code>.
 *
 * @author Will Glozer
 */
public class ZStoreArgs {
    private static enum Aggregate { SUM, MIN, MAX }

    private List<Long> weights;
    private Aggregate aggregate;

    /**
     * Static builder methods.
     */
    public static class Builder {
        public static ZStoreArgs weights(long... weights) {
            return new ZStoreArgs().weights(weights);
        }

        public static ZStoreArgs sum() {
            return new ZStoreArgs().sum();
        }

        public static ZStoreArgs min() {
            return new ZStoreArgs().min();
        }

        public static ZStoreArgs max() {
            return new ZStoreArgs().max();
        }
    }

    public ZStoreArgs weights(long... weights) {
        this.weights = new ArrayList<Long>(weights.length);
        for (long weight : weights) {
            this.weights.add(weight);
        }
        return this;
    }

    public ZStoreArgs sum() {
        aggregate = Aggregate.SUM;
        return this;
    }

    public ZStoreArgs min() {
        aggregate = Aggregate.MIN;
        return this;
    }

    public ZStoreArgs max() {
        aggregate = Aggregate.MAX;
        return this;
    }

    <K, V> void build(CommandArgs<K, V> args) {
        if (weights != null) {
            args.add(WEIGHTS);
            for (long weight : weights) {
                args.add(weight);
            }
        }

        if (aggregate != null) {
            args.add(AGGREGATE);
            switch (aggregate) {
                case SUM:
                    args.add(SUM);
                    break;
                case MIN:
                    args.add(MIN);
                    break;
                case MAX:
                    args.add(MAX);
                    break;
            }
        }
    }
}
