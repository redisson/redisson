/**
 * Copyright (c) 2013-2024 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.tomcat;

import org.apache.catalina.*;
import org.apache.catalina.session.ManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.config.Config;
import org.redisson.pubsub.PublishSubscribeService;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * Redisson Session Manager for Apache Tomcat
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonSessionManager extends ManagerBase {

    public enum ReadMode {REDIS, MEMORY}
    public enum UpdateMode {DEFAULT, AFTER_REQUEST}
    
    private final Log log = LogFactory.getLog(RedissonSessionManager.class);
    
    protected RedissonClient redisson;
    private String configPath;
    private Config config;
    
    private ReadMode readMode = ReadMode.REDIS;
    private UpdateMode updateMode = UpdateMode.DEFAULT;

    protected String keyPrefix = "";
    private boolean broadcastSessionEvents = false;
    private boolean broadcastSessionUpdates = true;

    private final String nodeId = UUID.randomUUID().toString();

    private MessageListener messageListener;
    
    private Codec codecToUse;

    public String getNodeId() { return nodeId; }

    public String getUpdateMode() {
        return updateMode.toString();
    }

    public void setUpdateMode(String updateMode) {
        this.updateMode = UpdateMode.valueOf(updateMode);
    }

    public boolean isBroadcastSessionEvents() {
        return broadcastSessionEvents;
    }
    
    public void setBroadcastSessionEvents(boolean replicateSessionEvents) {
        this.broadcastSessionEvents = replicateSessionEvents;
    }

    public boolean isBroadcastSessionUpdates() {
        return broadcastSessionUpdates;
    }

    public void setBroadcastSessionUpdates(boolean broadcastSessionUpdates) {
        this.broadcastSessionUpdates = broadcastSessionUpdates;
    }

    public String getReadMode() {
        return readMode.toString();
    }

    public void setReadMode(String readMode) {
        this.readMode = ReadMode.valueOf(readMode);
    }
    
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }
    
    public String getConfigPath() {
        return configPath;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String getName() {
        return RedissonSessionManager.class.getSimpleName();
    }
    
    @Override
    public void load() throws ClassNotFoundException, IOException {
    }

    @Override
    public void unload() throws IOException {
    }

    @Override
    public Session createSession(String sessionId) {
        Session session = super.createSession(sessionId);
        
        if (broadcastSessionEvents) {
            getTopic().publish(new SessionCreatedMessage(getNodeId(), session.getId()));
        }
        return session;
    }

    public RSet<String> getNotifiedNodes(String sessionId) {
        String separator = keyPrefix == null || keyPrefix.isEmpty() ? "" : ":";
        String name = keyPrefix + separator + "redisson:tomcat_notified_nodes:" + sessionId;
        return redisson.getSet(name, StringCodec.INSTANCE);
    }

    public String getTomcatSessionKeyName(String sessionId) {
        String separator = keyPrefix == null || keyPrefix.isEmpty() ? "" : ":";
        return keyPrefix + separator + "redisson:tomcat_session:" + sessionId;
    }

    public RMap<String, Object> getMap(String sessionId) {
        String name = getTomcatSessionKeyName(sessionId);
        return redisson.getMap(name, new CompositeCodec(StringCodec.INSTANCE, codecToUse, codecToUse));
    }

    public RTopic getTopic() {
        String separator = keyPrefix == null || keyPrefix.isEmpty() ? "" : ":";
        String name = keyPrefix + separator + "redisson:tomcat_session_updates:" + getContainer().getName();
        PublishSubscribeService ss = ((Redisson) redisson).getCommandExecutor().getConnectionManager().getSubscribeService();
        if (ss.isShardingSupported()) {
            return redisson.getShardedTopic(name);
        }
        return redisson.getTopic(name);
    }
    
    @Override
    public Session findSession(String id) throws IOException {
        return findSession(id, true);
    }
    
    private Session findSession(String id, boolean notify) throws IOException {
        RedissonSession result = (RedissonSession) super.findSession(id);
        if (result == null) {
            if (id != null) {
                Map<String, Object> attrs = new HashMap<String, Object>();
                try {
                    attrs = getMap(id).getAll(RedissonSession.ATTRS);
                } catch (Exception e) {
                    log.error("Can't read session object by id: " + id, e);
                }

                if (attrs.isEmpty() || (broadcastSessionEvents && getNotifiedNodes(id).contains(nodeId))) {
                    log.debug("Session " + id + " can't be found");
                    return null;    
                }
                
                RedissonSession session = (RedissonSession) createEmptySession();
                session.load(attrs);
                session.setId(id, notify);
                
                session.superAccess();
                session.endAccess();
                return session;
            }
            return null;
        }

        result.superAccess();
        result.endAccess();
        
        return result;
    }

    @Override
    public Session createEmptySession() {
        Session session = new RedissonSession(this, readMode, updateMode, broadcastSessionEvents, this.broadcastSessionUpdates);

        if (broadcastSessionEvents) {
            session.addSessionListener(event -> {
                if (event.getType().equals(Session.SESSION_DESTROYED_EVENT)) {
                    getTopic().publish(new SessionDestroyedMessage(getNodeId(), session.getId()));
                }
            });
        }
        return session;
    }
    
    public void superRemove(Session session) {
        super.remove(session, false);
    }

    @Override
    public void remove(Session session, boolean update) {
        super.remove(session, update);

        if (session.getIdInternal() != null
                && !redisson.isShuttingDown()) {
            ((RedissonSession)session).delete();
        }
    }
    
    public void superAdd(Session session) {
        super.add(session);
    }

    @Override
    public void add(Session session) {
        super.add(session);
        ((RedissonSession)session).save();
    }
    
    public RedissonClient getRedisson() {
        return redisson;
    }
    
    @Override
    protected void startInternal() throws LifecycleException {
        super.startInternal();
        redisson = buildClient();
        
        final ClassLoader applicationClassLoader;
        if (getContainer().getLoader().getClassLoader() != null) {
            applicationClassLoader = getContainer().getLoader().getClassLoader();
        } else if (Thread.currentThread().getContextClassLoader() != null) {
            applicationClassLoader = Thread.currentThread().getContextClassLoader();
        } else {
            applicationClassLoader = getClass().getClassLoader();
        }
        
        Codec codec = redisson.getConfig().getCodec();
        try {
            codecToUse = codec.getClass()
                    .getConstructor(ClassLoader.class, codec.getClass())
                    .newInstance(applicationClassLoader, codec);
        } catch (Exception e) {
            throw new LifecycleException(e);
        }
        
        Pipeline pipeline = getContainer().getPipeline();
        synchronized (pipeline) {
            tryInitSsoValve();
            if (readMode == ReadMode.REDIS) {
                Optional<Valve> res = Arrays.stream(pipeline.getValves()).filter(v -> v.getClass() == UsageValve.class).findAny();
                if (res.isPresent()) {
                    ((UsageValve)res.get()).incUsage();
                } else {
                    pipeline.addValve(new UsageValve());
                }
            }
            if (updateMode == UpdateMode.AFTER_REQUEST) {
                Optional<Valve> res = Arrays.stream(pipeline.getValves()).filter(v -> v.getClass() == UpdateValve.class).findAny();
                if (res.isPresent()) {
                    ((UpdateValve)res.get()).incUsage();
                } else {
                    pipeline.addValve(new UpdateValve());
                }
            }
        }
        
        if (readMode == ReadMode.MEMORY && this.broadcastSessionUpdates || broadcastSessionEvents) {
            RTopic updatesTopic = getTopic();
            messageListener = new MessageListener<AttributeMessage>() {
                
                @Override
                public void onMessage(CharSequence channel, AttributeMessage msg) {
                    try {
                        if (msg.getNodeId().equals(nodeId)) {
                            return;
                        }

                        RedissonSession session = (RedissonSession) RedissonSessionManager.super.findSession(msg.getSessionId());
                        if (session != null) {
                            if (msg instanceof SessionDestroyedMessage) {
                                session.expire();
                            }
                            
                            if (msg instanceof AttributeRemoveMessage) {
                                for (String name : ((AttributeRemoveMessage)msg).getNames()) {
                                    session.superRemoveAttributeInternal(name, true);
                                }
                            }

                            if (msg instanceof AttributesClearMessage) {
                                RedissonSessionManager.super.remove(session, false);
                            }
                            
                            if (msg instanceof AttributesPutAllMessage) {
                                AttributesPutAllMessage m = (AttributesPutAllMessage) msg;
                                Map<String, Object> attrs = m.getAttrs(codecToUse.getMapValueDecoder());
                                session.load(attrs);
                            }
                            
                            if (msg instanceof AttributeUpdateMessage) {
                                AttributeUpdateMessage m = (AttributeUpdateMessage)msg;
                                Map<String, Object> attrs = new HashMap<>();
                                attrs.put(m.getName(), m.getValue(codecToUse.getMapValueDecoder()));
                                session.load(attrs);
                            }
                        } else {
                            if (msg instanceof SessionCreatedMessage) {
                                findSession(msg.getSessionId());
                            }
                            
                            if (msg instanceof SessionDestroyedMessage) {
                                Session s = findSession(msg.getSessionId(), false);
                                if (s != null) {
                                    s.expire();
                                }
                                RSet<String> set = getNotifiedNodes(msg.getSessionId());
                                set.add(nodeId);
                                set.expire(Duration.ofSeconds(60));
                            }
                            
                        }

                    } catch (Exception e) {
                        log.error("Unable to handle topic message", e);
                    }
                }
            };
            
            updatesTopic.addListener(AttributeMessage.class, messageListener);
        }
        
        setState(LifecycleState.STARTING);
    }

    protected RedissonClient buildClient() throws LifecycleException {
        if (config == null) {
            try {
                config = Config.fromYAML(new File(configPath), getClass().getClassLoader());
            } catch (Exception e) {
                throw new LifecycleException("Can't parse yaml config " + configPath, e);
            }
        }

        try {
            return Redisson.create(config);
        } catch (Exception e) {
            throw new LifecycleException(e);
        }
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        super.stopInternal();
        
        setState(LifecycleState.STOPPING);
        
        Pipeline pipeline = getContainer().getPipeline();
        synchronized (pipeline) {
            if (readMode == ReadMode.REDIS) {
                Arrays.stream(pipeline.getValves()).filter(v -> v.getClass() == UsageValve.class).forEach(v -> {
                    if (((UsageValve)v).decUsage() == 0){
                        pipeline.removeValve(v);
                    }
                });
            }
            if (updateMode == UpdateMode.AFTER_REQUEST) {
                Arrays.stream(pipeline.getValves()).filter(v -> v.getClass() == UpdateValve.class).forEach(v -> {
                    if (((UpdateValve)v).decUsage() == 0){
                        pipeline.removeValve(v);
                    }
                });
            }
        }
        
        if (messageListener != null) {
             RTopic updatesTopic = getTopic();
             updatesTopic.removeListener(messageListener);
        }

        codecToUse = null;

        try {
            shutdownRedisson();
        } catch (Exception e) {
            throw new LifecycleException(e);
        }
        
    }

    protected void shutdownRedisson() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    public void store(HttpSession session) throws IOException {
        if (session == null) {
            return;
        }
        
        RedissonSession sess = (RedissonSession) super.findSession(session.getId());
        if (sess != null) {
            sess.superAccess();
            sess.superEndAccess();
            sess.save();
        }
    }

    private void tryInitSsoValve() {
        Container c = getContainer();
        // SSO valve has to be in defined in Host
        // it won't be picked up by Catalina from within Context
        while (c != null && !(c instanceof org.apache.catalina.Host)) {
            c = c.getParent();
        }
        if (c == null) {
            log.warn("No Catalina Host found for current context. Can't configure Redisson SSO.");
            return;
        }
        for (Valve valve : ((Host) c).getPipeline().getValves()) {
            if (valve instanceof RedissonSingleSignOn) {
                log.debug("Found SSO valve, passing RedissionSessionManager to it.");
                ((RedissonSingleSignOn) valve).setSessionManager(this);
                return;
            }
        }
        log.trace("No Redisson SSO valve found. Redisson SSO is not configured.");
    }

}
