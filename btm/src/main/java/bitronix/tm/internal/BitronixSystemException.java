/*
 * Copyright (C) 2006-2013 Bitronix Software (http://www.bitronix.be)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bitronix.tm.internal;

import jakarta.transaction.SystemException;

/**
 * Subclass of {@link jakarta.transaction.SystemException} supporting nested {@link Throwable}s.
 *
 * @author Ludovic Orban
 */
public class BitronixSystemException extends SystemException {

    public BitronixSystemException(int errorCode) {
        super(errorCode);
    }

    public BitronixSystemException(String string) {
        super(string);
    }

    public BitronixSystemException(String string, Throwable t) {
        super(string);
        initCause(t);
    }

}
