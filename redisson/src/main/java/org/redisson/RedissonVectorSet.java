package org.redisson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RFuture;
import org.redisson.api.RVectorSet;
import org.redisson.api.vector.*;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.redisson.command.CommandAsyncExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonVectorSet extends RedissonExpirable implements RVectorSet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedissonVectorSet(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public boolean add(VectorAddArgs args) {
        return get(addAsync(args));
    }

    public RFuture<Boolean> addAsync(VectorAddArgs vargs) {
        VectorAddParams params = (VectorAddParams) vargs;

        List<Object> args = new ArrayList<>();
        args.add(getName());

        if (params.getReduce() != null) {
            args.add("REDUCE");
            args.add(params.getReduce());
        }

        if (params.getVectorBytes() != null) {
            args.add("FP32");
            args.add(params.getVectorBytes());
        } else if (params.getVectorDoubles() != null) {
            args.add("VALUES");
            args.add(params.getVectorDoubles().length);
            Collections.addAll(args, params.getVectorDoubles());
        }

        args.add(params.getElement());

        if (params.isUseCheckAndSet()) {
            args.add("CAS");
        }

        if (params.getQuantizationType() != null) {
            args.add(params.getQuantizationType().name());
        }

        if (params.getEffort() != null) {
            args.add("EF");
            args.add(params.getEffort());
        }

        if (params.getAttributes() != null) {
            args.add("SETATTR");
            args.add(serializeToJson(params.getAttributes()));
        }

        if (params.getMaxConnections() != null) {
            args.add("M");
            args.add(params.getMaxConnections());
        }

        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.VADD, args.toArray());
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    public RFuture<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VCARD, getName());
    }

    @Override
    public int dimensions() {
        return get(dimensionsAsync());
    }

    public RFuture<Integer> dimensionsAsync() {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VDIM, getName());
    }

    @Override
    public List<Double> getVector(String element) {
        return get(getVectorAsync(element));
    }

    public RFuture<List<Double>> getVectorAsync(String element) {
        return commandExecutor.readAsync(getName(), DoubleCodec.INSTANCE, RedisCommands.VEMB, getName(), element);
    }

    @Override
    public List<Object> getRawVector(String element) {
        return get(getRawVectorAsync(element));
    }

    public RFuture<List<Object>> getRawVectorAsync(String element) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VEMB_RAW, getName(), element, "RAW");
    }

    @Override
    public <T> T getAttributes(String element, Class<T> clazz) {
        return get(getAttributesAsync(element, clazz));
    }

    public <T> RFuture<T> getAttributesAsync(String element, Class<T> clazz) {
        return commandExecutor.readAsync(getName(), new TypedJsonJacksonCodec(clazz), RedisCommands.VGETATTR, getName(), element);
    }

    @Override
    public VectorInfo getInfo() {
        return get(getInfoAsync());
    }

    public RFuture<VectorInfo> getInfoAsync() {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VINFO, getName());
    }

    @Override
    public List<String> getNeighbors(String element) {
        return get(getNeighborsAsync(element));
    }

    public RFuture<List<String>> getNeighborsAsync(String element) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VLINKS, getName(), element);
    }

    @Override
    public List<ScoredEntry<String>> getNeighborEntries(String element) {
        return get(getNeighborEntriesAsync(element));
    }

    public RFuture<List<ScoredEntry<String>>> getNeighborEntriesAsync(String element) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VLINKS_WITHSCORES, getName(), element, "WITHSCORES");
    }

    @Override
    public String random() {
        return get(randomAsync());
    }

    public RFuture<String> randomAsync() {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VRANDMEMBER, getName());
    }

    @Override
    public List<String> random(int count) {
        return get(randomAsync(count));
    }

    public RFuture<List<String>> randomAsync(int count) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VRANDMEMBER_MULTI, getName(), count);
    }

    @Override
    public boolean remove(String element) {
        return get(removeAsync(element));
    }

    public RFuture<Boolean> removeAsync(String element) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.VREM, getName(), element);
    }

    @Override
    public boolean setAttributes(String element, Object attributes) {
        return get(setAttributesAsync(element, attributes));
    }

    public RFuture<Boolean> setAttributesAsync(String element, Object attributes) {
        String json = serializeToJson(attributes);
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.VSETATTR, getName(), element, json);
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public List<String> getSimilar(VectorSimilarArgs args) {
        return get(getSimilarAsync(args));
    }

    public RFuture<List<String>> getSimilarAsync(VectorSimilarArgs vargs) {
        VectorSimilarParams prms = (VectorSimilarParams) vargs;

        List<Object> args = createArgs(prms, false);

        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VSIM, args.toArray());
    }

    @Override
    public List<ScoredEntry<String>> getSimilarEntries(VectorSimilarArgs args) {
        return get(getSimilarEntriesAsync(args));
    }

    public RFuture<List<ScoredEntry<String>>> getSimilarEntriesAsync(VectorSimilarArgs vargs) {
        VectorSimilarParams prms = (VectorSimilarParams) vargs;

        List<Object> args = createArgs(prms, true);

        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.VSIM_WITHSCORES, args.toArray());
    }

    private List<Object> createArgs(VectorSimilarParams prms, boolean withscores) {
        List<Object> args = new ArrayList<>();
        args.add(getName());

        if (prms.getElement() != null) {
            args.add("ELE");
            args.add(prms.getElement());
        } else if (prms.getVectorBytes() != null) {
            args.add("FP32");
            args.add(prms.getVectorBytes());
        } else if (prms.getVectorDoubles() != null) {
            args.add("VALUES");
            args.add(prms.getVectorDoubles().length);
            args.addAll(Arrays.asList(prms.getVectorDoubles()));
        }

        if (withscores) {
            args.add("WITHSCORES");
        }

        if (prms.getCount() != null) {
            args.add("COUNT");
            args.add(prms.getCount());
        }

        if (prms.getEffort() != null) {
            args.add("EF");
            args.add(prms.getEffort());
        }

        if (prms.getFilter() != null) {
            args.add("FILTER");
            args.add(prms.getFilter());
        }

        if (prms.getFilterEffort() != null) {
            args.add("FILTER-EF");
            args.add(prms.getFilterEffort());
        }

        if (prms.isUseLinearScan()) {
            args.add("TRUTH");
        }

        if (prms.isUseMainThread()) {
            args.add("NOTHREAD");
        }

        return args;
    }
}
