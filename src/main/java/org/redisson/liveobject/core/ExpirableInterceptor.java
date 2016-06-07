package org.redisson.liveobject.core;

import io.netty.util.concurrent.Future;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.redisson.client.codec.Codec;
import org.redisson.liveobject.RLiveObject;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class ExpirableInterceptor {

    @RuntimeType
    public boolean expire(@This Object me, long timeToLive, TimeUnit timeUnit) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public boolean expireAt(@This Object me, long timestamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public boolean expireAt(@This Object me, Date timestamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public boolean clearExpire(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public long remainTimeToLive(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public void migrate(@This Object me, String host, int port, int database) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public boolean move(@This Object me, int database) {
        ((RLiveObject) me).getLiveObjectLiveMap().move(database);
        return true;
    }

    @RuntimeType
    public String getName(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public boolean delete(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public void rename(@This Object me, String newName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public boolean renamenx(@This Object me, String newName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public boolean isExists(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Codec getCodec(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Void> migrateAsync(@This Object me, String host, int port, int database) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Boolean> moveAsync(@This Object me, int database) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Boolean> deleteAsync(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Void> renameAsync(@This Object me, String newName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Boolean> renamenxAsync(@This Object me, String newName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Boolean> isExistsAsync(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Boolean> expireAsync(@This Object me, long timeToLive, TimeUnit timeUnit) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Boolean> expireAtAsync(@This Object me, Date timestamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Boolean> expireAtAsync(@This Object me, long timestamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Boolean> clearExpireAsync(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @RuntimeType
    public Future<Long> remainTimeToLiveAsync(@This Object me) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
