/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.ws.jms.internal;

import com.google.common.cache.LoadingCache;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;

import javax.jms.Connection;
import java.util.Set;

class WSJmsModule extends PrivateModule {
    private final Set<WSJmsMessageListener> wsJmsMessageListeners;
    private final LoadingCache<SoapJmsUri, Connection> connectionCache;

    WSJmsModule(Set<WSJmsMessageListener> wsJmsMessageListeners, LoadingCache<SoapJmsUri, Connection> connectionCache) {
        this.wsJmsMessageListeners = wsJmsMessageListeners;
        this.connectionCache = connectionCache;
    }

    @Override
    protected void configure() {
        install(new FactoryModuleBuilder().build(WSJmsTransportFactory.class));

        bind(new TypeLiteral<LoadingCache<SoapJmsUri, Connection>>() {
        }).toInstance(connectionCache);

        for (WSJmsMessageListener wsJmsMessageListener : wsJmsMessageListeners) {
            requestInjection(wsJmsMessageListener);
        }

        requestStaticInjection(JmsTransportTubeFactory.class);
    }
}
