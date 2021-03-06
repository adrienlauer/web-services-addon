/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.ws.jms.internal;

import org.seedstack.seed.ErrorCode;

enum WSJmsErrorCodes implements ErrorCode {
    UNABLE_TO_ROLLBACK_WS_JMS_MESSAGE,
    UNABLE_TO_REGISTER_MESSAGE_LISTENER,
    UNABLE_TO_GET_TRANSACTED_STATUS
}
