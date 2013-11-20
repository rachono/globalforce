package us.globalforce.services;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.httpclient.HttpClient;
import org.cometd.bayeux.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.globalforce.model.Credential;
import us.globalforce.salesforce.client.PushTopic;
import us.globalforce.salesforce.client.SObject;
import us.globalforce.salesforce.client.SalesforceClient;
import us.globalforce.salesforce.client.StreamingClient;
import us.globalforce.salesforce.client.oauth.OAuthClient;
import us.globalforce.salesforce.client.oauth.OAuthToken;

import com.google.common.collect.Maps;

@Singleton
public class SalesforceHooks {
    private static final Logger log = LoggerFactory.getLogger(SalesforceHooks.class);

    final Map<String, SalesforceHook> hooks = Maps.newHashMap();

    public static final String HOOK_KEY = "HookCases";
    public static final String HOOK_QUERY = "SELECT Id FROM Case";

    @Inject
    OAuthClient oauthClient;

    @Inject
    HttpClient httpClient;

    final Executor executor = Executors.newCachedThreadPool();

    public class SalesforceHook {
        final Credential credential;

        StreamingClient listener;

        public SalesforceHook(Credential credential) {
            this.credential = credential;
        }

        public synchronized void start() throws IOException {
            if (listener != null) {
                return;
            }
            OAuthToken token = oauthClient.refreshToken(credential.refreshToken);
            SalesforceClient client = new SalesforceClient(httpClient, token);
            SObject o = PushTopic.find(client, HOOK_KEY);
            if (o == null) {
                log.info("Push topic not found; creating");
                PushTopic.create(client, HOOK_KEY, HOOK_QUERY, true, false, false, false);
            }

            StreamingClient listener = new StreamingClient(token, HOOK_KEY) {
                @Override
                public void onFailure() {
                    synchronized (this) {
                        SalesforceHook.this.listener = null;
                    }
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                start();
                            } catch (Exception e) {
                                log.error("Error trying to restart streaming listener", e);
                            }
                        }
                    });
                }

                @Override
                protected void onSalesforceMessage(Message message) {
                    log.info("Got salesforce message {}", message);
                }
            };
            if (listener.start()) {
                this.listener = listener;
            }
        }
    }

    public SalesforceHook ensureHooked(Credential credential) throws IOException {
        SalesforceHook hook;

        String organizationId = credential.organization;

        synchronized (hooks) {
            hook = hooks.get(organizationId);
            if (hook == null) {
                hook = new SalesforceHook(credential);
                hooks.put(organizationId, hook);
            }
        }
        hook.start();
        return hook;
    }
}
