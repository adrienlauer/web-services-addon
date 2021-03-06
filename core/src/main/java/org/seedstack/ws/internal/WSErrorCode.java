/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.ws.internal;

import org.seedstack.seed.ErrorCode;

enum WSErrorCode implements ErrorCode {
    ENDPOINT_URL_MISSING,
    IMPLEMENTATION_CLASS_MISSING,
    UNABLE_TO_LOAD_IMPLEMENTATION_CLASS,
    WSDL_LOCATION_MISSING,
    UNABLE_TO_FIND_WSDL,
    NO_WS_CONFIGURATION,
    UNABLE_TO_LOAD_REALM_AUTHENTICATION_ADAPTER_CLASS, INVALID_REALM_AUTHENTICATION_ADAPTER_CLASS, MALFORMED_ENDPOINT_URL
}
