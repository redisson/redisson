// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis;

import com.lambdaworks.redis.protocol.CommandArgs;
import com.lambdaworks.redis.protocol.CommandKeyword;

import java.util.ArrayList;
import java.util.List;

import static com.lambdaworks.redis.protocol.CommandKeyword.*;
import static com.lambdaworks.redis.protocol.CommandType.GET;

/**
 * Argument list builder for the redis <a href="http://redis.io/commands/sort">SORT</a>
 * command. Static import the methods from {@link Builder} and chain the method calls:
 * <code>by("weight_*").desc().limit(0, 2)</code>.
 *
 * @author Will Glozer
 */
public class SortArgs {
    private String by;
    private Long offset, count;
    private List<String> get;
    private CommandKeyword order;
    private boolean alpha;

    /**
     * Static builder methods.
     */
    public static class Builder {
        public static SortArgs by(String pattern) {
            return new SortArgs().by(pattern);
        }

        public static SortArgs limit(long offset, long count) {
            return new SortArgs().limit(offset, count);
        }

        public static SortArgs get(String pattern) {
            return new SortArgs().get(pattern);
        }

        public static SortArgs asc() {
            return new SortArgs().asc();
        }

        public static SortArgs desc() {
            return new SortArgs().desc();
        }

        public static SortArgs alpha() {
            return new SortArgs().alpha();
        }
    }

    public SortArgs by(String pattern) {
        by = pattern;
        return this;
    }

    public SortArgs limit(long offset, long count) {
        this.offset = offset;
        this.count  = count;
        return this;
    }

    public SortArgs get(String pattern) {
        if (get == null) {
            get = new ArrayList<String>();
        }
        get.add(pattern);
        return this;
    }

    public SortArgs asc() {
        order = ASC;
        return this;
    }

    public SortArgs desc() {
        order = DESC;
        return this;
    }

    public SortArgs alpha() {
        alpha = true;
        return this;
    }

    <K, V> void build(CommandArgs<K, V> args, K store) {

        if (by != null) {
            args.add(BY);
            args.add(by);
        }

        if (get != null) {
            for (String pattern : get) {
                args.add(GET);
                args.add(pattern);
            }
        }

        if (offset != null) {
            args.add(LIMIT);
            args.add(offset);
            args.add(count);
        }

        if (order != null) {
            args.add(order);
        }

        if (alpha) {
            args.add(ALPHA);
        }

        if (store != null) {
            args.add(STORE);
            args.addKey(store);
        }
    }
}
