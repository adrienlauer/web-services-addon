/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.ws.jms.internal;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.nuun.kernel.api.plugin.InitState;
import io.nuun.kernel.api.plugin.PluginException;
import io.nuun.kernel.api.plugin.context.InitContext;
import io.nuun.kernel.core.AbstractPlugin;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.seedstack.jms.internal.JmsPlugin;
import org.seedstack.jms.spi.ConnectionDefinition;
import org.seedstack.jms.spi.JmsFactory;
import org.seedstack.jms.spi.MessageListenerInstanceDefinition;
import org.seedstack.jms.spi.MessagePoller;
import org.seedstack.seed.SeedException;
import org.seedstack.seed.core.spi.configuration.ConfigurationProvider;
import org.seedstack.ws.internal.EndpointDefinition;
import org.seedstack.ws.internal.WSPlugin;

import javax.jms.*;
import javax.naming.NamingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This plugin provides JMS transport integration for WS support.
 *
 * @author adrien.lauer@mpsa.com
 */
public class WSJmsPlugin extends AbstractPlugin {
    public static final List<String> SUPPORTED_BINDINGS = ImmutableList.of("http://www.w3.org/2010/soapjms/");

    public static final int DEFAULT_CACHE_CONCURRENCY = 4;
    public static final int DEFAULT_CACHE_SIZE = 16;
    public static final String LISTENER_NAME_PATTERN = "ws-%s-listener";
    public static final String ANONYMOUS_CONNECTION_PATTERN = "ws-anon-connection-%d";

    private final Set<WSJmsMessageListener> wsJmsMessageListeners = new HashSet<WSJmsMessageListener>();

    private LoadingCache<SoapJmsUri, Connection> connectionCache;

    private JmsPlugin jmsPlugin;
    private WSPlugin wsPlugin;

    @Override
    public String name() {
        return "ws-jms";
    }

    @Override
    public InitState init(InitContext initContext) {
        jmsPlugin = initContext.dependency(JmsPlugin.class);
        wsPlugin = initContext.dependency(WSPlugin.class);
        Configuration wsConfiguration = initContext.dependency(ConfigurationProvider.class)
                .getConfiguration().subset(WSPlugin.CONFIGURATION_PREFIX);

        int cacheSize = wsConfiguration.getInt("jms.transport-cache.max-size", DEFAULT_CACHE_SIZE);
        final Configuration finalWsConfiguration = wsConfiguration;
        connectionCache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .concurrencyLevel(wsConfiguration.getInt("transport-cache.concurrency", DEFAULT_CACHE_CONCURRENCY))
                .initialCapacity(wsConfiguration.getInt("transport-cache.initial-size", cacheSize / 4))
                .build(new CacheLoader<SoapJmsUri, Connection>() {
                    private AtomicInteger atomicInteger = new AtomicInteger(0);

                    @Override
                    public Connection load(SoapJmsUri soapJmsUri) throws NamingException, JMSException {
                        String lookupVariant = soapJmsUri.getLookupVariant();
                        JmsFactory jmsFactory = jmsPlugin.getJmsFactory();
                        Connection connection;

                        if (SoapJmsUri.JNDI_LOOKUP_VARIANT.equals(lookupVariant)) {
                            String jndiConnectionFactoryName = soapJmsUri.getParameter("jndiConnectionFactoryName");
                            if (StringUtils.isBlank(jndiConnectionFactoryName)) {
                                throw new IllegalArgumentException("Missing jndiConnectionFactoryName parameter for JMS URI " + soapJmsUri.toString());
                            }

                            String connectionName = soapJmsUri.getConnectionName();
                            if (connectionName == null) {
                                connectionName = String.format(ANONYMOUS_CONNECTION_PATTERN, atomicInteger.getAndIncrement());
                            }

                            ConnectionDefinition connectionDefinition = jmsFactory.createConnectionDefinition(
                                    connectionName,
                                    soapJmsUri.getConfiguration(finalWsConfiguration),
                                    (ConnectionFactory) SoapJmsUri.getContext(soapJmsUri).lookup(jndiConnectionFactoryName)
                            );

                            connection = jmsFactory.createConnection(connectionDefinition);
                            jmsPlugin.registerConnection(connection, connectionDefinition);
                        } else if (SoapJmsUri.SEED_QUEUE_LOOKUP_VARIANT.equals(lookupVariant) || SoapJmsUri.SEED_TOPIC_LOOKUP_VARIANT.equals(lookupVariant)) {
                            String connectionName = soapJmsUri.getConnectionName();

                            if (StringUtils.isBlank(connectionName)) {
                                throw new IllegalArgumentException("Missing connectionName parameter for JMS URI " + soapJmsUri.toString());
                            }

                            connection = jmsPlugin.getConnection(connectionName);
                        } else {
                            throw new IllegalArgumentException("Unsupported lookup variant " + lookupVariant + " for JMS URI " + soapJmsUri.toString());
                        }

                        if (connection == null) {
                            throw new PluginException("Unable to resolve connection for JMS URI " + soapJmsUri.toString());
                        }

                        return connection;
                    }
                });

        for (Map.Entry<String, EndpointDefinition> endpointEntry : wsPlugin.getEndpointDefinitions(SUPPORTED_BINDINGS).entrySet()) {
            EndpointDefinition endpointDefinition = endpointEntry.getValue();
            String endpointName = endpointEntry.getKey();
            String serviceName = endpointDefinition.getServiceName().getLocalPart();
            String portName = endpointDefinition.getPortName().getLocalPart();
            String serviceNameAndServicePort = serviceName + "-" + portName;

            SoapJmsUri uri;
            try {
                uri = SoapJmsUri.parse(new URI(endpointDefinition.getUrl()));
                uri.setEndpointName(endpointName);
            } catch (URISyntaxException e) {
                throw new PluginException("Unable to parse endpoint URI", e);
            }

            Configuration endpointConfiguration = uri.getConfiguration(wsConfiguration);
            Connection connection;
            try {
                connection = connectionCache.get(uri);
            } catch (Exception e) {
                throw new PluginException("Unable to create JMS connection for WS " + serviceNameAndServicePort, e);
            }

            Session session;
            try {
                session = connection.createSession(endpointConfiguration.getBoolean("transactional", true), Session.AUTO_ACKNOWLEDGE);
            } catch (JMSException e) {
                throw new PluginException("Unable to create JMS session for WS " + serviceNameAndServicePort, e);
            }

            Destination destination;
            try {
                destination = SoapJmsUri.getDestination(uri, session);
            } catch (Exception e) {
                throw new PluginException("Unable to create JMS destination for WS " + serviceNameAndServicePort, e);
            }

            WSJmsMessageListener messageListener = new WSJmsMessageListener(uri, new JmsAdapter(wsPlugin.createWSEndpoint(endpointDefinition, null)), session);

            String messageListenerName = String.format(LISTENER_NAME_PATTERN, endpointName);
            try {
                Class<? extends MessagePoller> poller = getPoller(endpointConfiguration);

                jmsPlugin.registerMessageListener(
                        new MessageListenerInstanceDefinition(
                                messageListenerName,
                                uri.getConnectionName(),
                                session,
                                destination,
                                endpointConfiguration.getString("selector"),
                                messageListener,
                                poller
                        )
                );
            } catch (Exception e) {
                throw SeedException.wrap(e, WSJmsErrorCodes.UNABLE_TO_REGISTER_MESSAGE_LISTENER).put("messageListenerName", messageListenerName);
            }
            wsJmsMessageListeners.add(messageListener);
        }

        return InitState.INITIALIZED;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends MessagePoller> getPoller(Configuration endpointConfiguration) throws ClassNotFoundException {
        String pollerClassName = endpointConfiguration.getString("poller");
        if (pollerClassName != null) {
            return (Class<? extends MessagePoller>) Class.forName(pollerClassName);
        }
        return null;
    }

    @Override
    public void stop() {
        if (connectionCache != null) {
            connectionCache.invalidateAll();
            connectionCache.cleanUp();
        }
    }

    @Override
    public Collection<Class<?>> requiredPlugins() {
        return Lists.<Class<?>>newArrayList(WSPlugin.class, ConfigurationProvider.class, JmsPlugin.class);
    }

    @Override
    public Object nativeUnitModule() {
        return new WSJmsModule(wsJmsMessageListeners, connectionCache);
    }
}
