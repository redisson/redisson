package org.redisson.spring.session;

import org.springframework.context.ApplicationListener;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;

public class SessionEventsListener implements ApplicationListener<AbstractSessionEvent> {

    private int sessionCreatedEvents;
    private int sessionDeletedEvents;
    private int sessionExpiredEvents;
    
    @Override
    public void onApplicationEvent(AbstractSessionEvent event) {
        if (event instanceof SessionCreatedEvent) {
            sessionCreatedEvents++;
        }
        if (event instanceof SessionDeletedEvent) {
            sessionDeletedEvents++;
        }
        if (event instanceof SessionExpiredEvent) {
            sessionExpiredEvents++;
        }
    }
    
    public int getSessionCreatedEvents() {
        return sessionCreatedEvents;
    }
    
    public int getSessionDeletedEvents() {
        return sessionDeletedEvents;
    }
    
    public int getSessionExpiredEvents() {
        return sessionExpiredEvents;
    }

}
