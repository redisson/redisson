package org.redisson.tomcat;

import java.io.IOException;
import java.security.Principal;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.authenticator.SingleSignOn;
import org.apache.catalina.authenticator.SingleSignOnEntry;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

/**
 * Extended implementation of Tomcat's {@code SingleSignOn} valve, which saves
 * {@code SingleSignOnEntry} to Redis. This along with
 * {@code RedissonSessionManager} allows us to cluster Tomcat without sticky
 * sessions.
 */
public class RedissonSingleSignOn extends SingleSignOn implements IRedissonClientAware {
    private static final Log LOG = LogFactory.getLog(RedissonSingleSignOn.class);

    private static final String SSO_SESSION_ENTRIES = "redisson:tomcat_sso";

    private RMap<String, Object> ssoEntriesMap;
    private RedissonClient redissonClient;

    /** {@inheritDoc} */
    @Override
    public void setRedissonClient(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        request.removeNote(Constants.REQ_SSOID_NOTE);

        // Has a valid user already been authenticated?
        LOG.debug("SSO processing request for [" + request.getRequestURI() + "]");
        if (request.getUserPrincipal() != null) {
            LOG.debug("SSO found previously authenticated Principal [" + request.getUserPrincipal().getName() + "]");
            getNext().invoke(request, response);
            return;
        }

        // Check for the single sign on cookie
        LOG.debug("SSO checking for SSO cookie");
        Cookie cookie = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie value : cookies) {
                if (this.getCookieName().equals(value.getName())) {
                    cookie = value;
                    break;
                }
            }
        }
        if (cookie == null) {
            LOG.debug("SSO did not find an SSO cookie");
            getNext().invoke(request, response);
            return;
        }

        // Look up the cached Principal associated with this cookie value
        LOG.debug("SSO looking for a cached Principal for SSO session [" + cookie.getValue() + "]");
        SingleSignOnEntry entry = this.getSSOEntry(cookie.getValue());
        if (entry != null) {
            LOG.debug("SSO found cached Principal ["
                    + ((entry.getPrincipal() != null) ? entry.getPrincipal().getName() : "")
                    + "] with authentication type [" + entry.getAuthType() + "]");
            request.setNote(Constants.REQ_SSOID_NOTE, cookie.getValue());
            // Only set security elements if reauthentication is not required
            if (!getRequireReauthentication()) {
                request.setAuthType(entry.getAuthType());
                request.setUserPrincipal(entry.getPrincipal());
            }
        } else {
            LOG.debug(
                    "SSO did not find a cached Principal. Erasing SSO cookie for session [" + cookie.getValue() + "]");
            // No need to return a valid SSO session ID
            cookie.setValue("REMOVE");
            // Age of zero will trigger removal
            cookie.setMaxAge(0);
            // Domain and path have to match the original cookie to 'replace'
            // the original cookie
            cookie.setPath("/");
            String domain = getCookieDomain();
            if (domain != null) {
                cookie.setDomain(domain);
            }
            // This is going to trigger a Set-Cookie header. While the value is
            // not security sensitive, ensure that expectations for secure and
            // httpOnly are met
            cookie.setSecure(request.isSecure());
            if (request.getServletContext().getSessionCookieConfig().isHttpOnly()
                    || request.getContext().getUseHttpOnly()) {
                cookie.setHttpOnly(true);
            }

            response.addCookie(cookie);
        }

        // Invoke the next Valve in our pipeline
        getNext().invoke(request, response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionDestroyed(String ssoId, Session session) {
        if (!getState().isAvailable()) {
            return;
        }

        // Was the session destroyed as the result of a timeout or context stop?
        // If so, we'll just remove the expired session from the SSO. If the
        // session was logged out, we'll log out of all session associated with
        // the SSO.
        if ((session.getMaxInactiveInterval() > 0
                && session.getIdleTimeInternal() >= (session.getMaxInactiveInterval() * 1000))
                || !session.getManager().getContext().getState().isAvailable()) {
            LOG.debug("SSO processing a time out for SSO session [" + ssoId + "] and application session [" + session
                    + "]");
            removeSession(ssoId, session);
        } else {
            // The session was logged out. De-register this single session id, invalidating
            // associated sessions
            LOG.debug("SSO processing a log out for SSO session [" + ssoId + "] and application session [" + session
                    + "]");
            // First remove the session that we know has expired / been logged
            // out since it has already been removed from its Manager and, if
            // we don't remove it first, deregister() will log a warning that it
            // can't be found
            removeSession(ssoId, session);
            // If the SSO session was only associated with one web app the call
            // above will have removed the SSO session from the cache
            if (this.cache.containsKey(ssoId) || this.ssoEntriesMap.containsKey(ssoId)) {
                deregister(ssoId);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean associate(String ssoId, Session session) {
        SingleSignOnEntry sso = this.getSSOEntry(ssoId);
        if (sso == null) {
            LOG.debug("SSO failed to associate application session [" + ssoId + "] since SSO session [" + session
                    + "] does not exist");
            return false;
        } else {
            LOG.debug("SSO associating application session [" + ssoId + "] with SSO session [" + session + "]");
            sso.addSession(this, ssoId, session);
            this.ssoEntriesMap.put(ssoId, sso);
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean reauthenticate(String ssoId, Realm realm, Request request) {
        if (ssoId == null || realm == null) {
            return false;
        }

        boolean reauthenticated = false;

        SingleSignOnEntry entry = this.getSSOEntry(ssoId);
        if (entry != null && entry.getCanReauthenticate()) {
            String username = entry.getUsername();
            if (username != null) {
                Principal reauthPrincipal = realm.authenticate(username, entry.getPassword());
                if (reauthPrincipal != null) {
                    reauthenticated = true;
                    // Bind the authorization credentials to the request
                    request.setAuthType(entry.getAuthType());
                    request.setUserPrincipal(reauthPrincipal);
                }
            }
        }

        return reauthenticated;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void register(String ssoId, Principal principal, String authType, String username, String password) {
        LOG.debug("SSO registering SSO session [" + ssoId + "] for user ["
                + ((principal != null) ? principal.getName() : "") + "] with authentication type [" + authType + "]");

        SingleSignOnEntry ssoEntry = new SingleSignOnEntry(principal, authType, username, password);
        this.cache.put(ssoId, ssoEntry);
        this.ssoEntriesMap.put(ssoId, ssoEntry);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void deregister(String ssoId) {
        super.deregister(ssoId);
        this.ssoEntriesMap.remove(ssoId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean update(String ssoId, Principal principal, String authType, String username, String password) {
        SingleSignOnEntry sso = this.getSSOEntry(ssoId);
        if (sso != null && !sso.getCanReauthenticate()) {
            LOG.debug("SSO updating SSO session [" + ssoId + "] to authentication type [" + authType + "]");

            sso.updateCredentials(principal, authType, username, password);
            this.ssoEntriesMap.put(ssoId, sso);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void removeSession(String ssoId, Session session) {
        LOG.debug("SSO removing application session [" + session + "] from SSO session [" + ssoId + "]");

        // Get a reference to the SingleSignOn
        SingleSignOnEntry entry = this.getSSOEntry(ssoId);
        if (entry == null) {
            return;
        }

        // Remove the inactive session from SingleSignOnEntry
        entry.removeSession(session);

        // If there are not sessions left in the SingleSignOnEntry, deregister the
        // entry.
        if (entry.findSessions().size() == 0) {
            deregister(ssoId);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();
        this.ssoEntriesMap = this.redissonClient.getMap(SSO_SESSION_ENTRIES);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        this.ssoEntriesMap = null;
    }

    /**
     * method to lookup for {@code SingleSignOnEntry} for the given SSO ID.
     * Implementation first checks it in in-memory cache, if not found looks it up
     * in Redis
     *
     * @param ssoId SSO session id
     *
     * @return matching {@code SingleSignOnEntry} instance
     */
    private SingleSignOnEntry getSSOEntry(String ssoId) {
        // Get a reference to the SingleSignOnEntry
        SingleSignOnEntry entry = this.cache.get(ssoId);
        if (entry != null) {
            return entry;
        }
        entry = (SingleSignOnEntry) this.ssoEntriesMap.get(ssoId);
        if (entry != null) {
            this.cache.put(ssoId, entry);
        }
        return entry;
    }
}
