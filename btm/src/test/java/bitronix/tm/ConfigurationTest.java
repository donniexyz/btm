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
package bitronix.tm;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Ludovic Orban
 */
public class ConfigurationTest {

    @Test
    public void testGetString() throws Exception {
        Properties props = new Properties();
        props.setProperty("1", "one");
        props.setProperty("2", "two");
        System.setProperty("3", "three");
        props.setProperty("4", "four");
        System.setProperty("4", "four-sys");
        props.setProperty("12", "${1} ${2}");
        props.setProperty("13", "${1} ${3}");
        props.setProperty("14", "${1} ${}");
        props.setProperty("15", "${1} ${tatata");
        props.setProperty("16", "${1} ${4}");
        props.setProperty("17", "x$");
        props.setProperty("18", "x${");

        assertEquals("one", Configuration.getString(props, "1", null));
        assertEquals("two", Configuration.getString(props, "2", null));
        assertEquals("three", Configuration.getString(props, "3", null));
        assertEquals("one two", Configuration.getString(props, "12", null));
        assertEquals("one three", Configuration.getString(props, "13", null));
        assertEquals("one four-sys", Configuration.getString(props, "16", null));

        try {
            Configuration.getString(props, "14", null);
            fail("expected IllegalArgumentException: property ref cannot refer to an empty name: ${}");
        } catch (IllegalArgumentException ex) {
            assertEquals("property ref cannot refer to an empty name: ${}", ex.getMessage());
        }

        try {
            Configuration.getString(props, "15", null);
            fail("expected IllegalArgumentException: unclosed property ref: ${tatata");
        } catch (IllegalArgumentException ex) {
            assertEquals("unclosed property ref: ${tatata", ex.getMessage());
        }

        assertEquals("x$", Configuration.getString(props, "17", null));

        try {
            Configuration.getString(props, "18", null);
            fail("expected IllegalArgumentException: unclosed property ref: ${");
        } catch (IllegalArgumentException ex) {
            assertEquals("unclosed property ref: ${", ex.getMessage());
        }
    }

    @Test
    public void testGetIntBoolean() {
        Properties props = new Properties();
        props.setProperty("one", "1");
        props.setProperty("two", "2");
        System.setProperty("three", "3");
        System.setProperty("vrai", "true");
        props.setProperty("faux", "false");

        assertEquals(1, Configuration.getInt(props, "one", -1));
        assertEquals(2, Configuration.getInt(props, "two", -1));
        assertEquals(3, Configuration.getInt(props, "three", -1));
        assertEquals(10, Configuration.getInt(props, "ten", 10));

        assertTrue(Configuration.getBoolean(props, "vrai", false));
        assertFalse(Configuration.getBoolean(props, "faux", true));
        assertTrue(Configuration.getBoolean(props, "wrong", true));
    }

    @Test
    public void testToString() {
        final String expectation = "a Configuration with [allowMultipleLrc=false, asynchronous2Pc=false," +
                " backgroundRecoveryInterval=PT1M, conservativeJournaling=false, currentNodeOnlyRecovery=true," +
                " debugZeroResourceTransaction=false, defaultTransactionTimeout=PT1M, disableJmx=false," +
                " exceptionAnalyzer=null, filterLogStatus=false," +
                " forceBatchingEnabled=true, forcedWriteEnabled=true, gracefulShutdownInterval=PT10S, jdbcProxyFactoryClass=auto," +
                " jndiTransactionSynchronizationRegistryName=java:comp/TransactionSynchronizationRegistry," +
                " jndiUserTransactionName=java:comp/UserTransaction, journal=disk," +
                " logPart1Filename=target/btm1.tlog, logPart2Filename=target/btm2.tlog, maxLogSizeInMb=2," +
                " resourceConfigurationFilename=null, serverId=null, skipCorruptedLogs=false, synchronousJmxRegistration=false," +
                " warnAboutZeroResourceTransaction=true]";

        assertEquals(expectation, new Configuration().toString());
    }

}
