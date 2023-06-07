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
package bitronix.tm.twopc;

import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.journal.Journal;
import bitronix.tm.mock.AbstractMockJdbcTest;
import bitronix.tm.mock.events.Event;
import bitronix.tm.mock.events.EventRecorder;
import bitronix.tm.mock.events.JournalLogEvent;
import bitronix.tm.mock.events.XAResourceCommitEvent;
import bitronix.tm.mock.resource.MockJournal;
import bitronix.tm.mock.resource.MockXAResource;
import bitronix.tm.mock.resource.jdbc.MockitoXADataSource;
import bitronix.tm.resource.ResourceRegistrar;
import bitronix.tm.resource.jdbc.PooledConnectionProxy;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import oracle.jdbc.xa.OracleXAException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.XAConnection;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import javax.transaction.xa.XAException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Ludovic Orban
 */
public class OnePcFailureTest {

    private final static Logger log = LoggerFactory.getLogger(OnePcFailureTest.class);

    private PoolingDataSource poolingDataSource1;
    private BitronixTransactionManager tm;

    /**
     * Test scenario:
     *
     * XAResources: 2
     * TX timeout: 10s
     * TX resolution: heuristic mixed
     *
     * XAResource 1 resolution: successful
     * XAResource 2 resolution: commit throws exception XAException.XAER_RMERR
     *
     * Expected outcome:
     *   TM fails on resource 2 commit but does not report that via an exception
     *   as the recoverer will clean that up
     * Expected TM events:
     *  2 XAResourcePrepareEvent, 2 XAResourceCommitEvent
     * Expected journal events:
     *   ACTIVE, PREPARING, PREPARED, COMMITTING, COMMITTED
     * @throws Exception if any error happens.
     */
    @Test
    public void testExpectNoHeuristic() throws Exception {
        tm.begin();
        tm.setTransactionTimeout(10); // TX must not timeout

        Connection connection1 = poolingDataSource1.getConnection();
        PooledConnectionProxy handle = (PooledConnectionProxy) connection1;
        XAConnection xaConnection2 = (XAConnection) AbstractMockJdbcTest.getWrappedXAConnectionOf(handle.getPooledConnection());
        connection1.createStatement();

        final MockXAResource mockXAResource2 = (MockXAResource) xaConnection2.getXAResource();
        mockXAResource2.setCommitException(createXAException("resource 2 commit failed with XAER_RMERR", XAException.XAER_RMERR));

        try {
            tm.commit();
            fail("expected RollbackException");
        } catch (RollbackException ex) {
            assertTrue(ex.getMessage().matches("transaction failed during 1PC commit of a Bitronix Transaction with GTRID \\[.*\\], status=ROLLEDBACK, 1 resource\\(s\\) enlisted \\(.*\\)"));
        }

        log.info(EventRecorder.dumpToString());

        int journalUnknownEventCount = 0;
        int journalCommittingEventCount = 0;
        int journalRolledbackEventCount = 0;
        int commitEventCount = 0;
        List<? extends Event> events = EventRecorder.getOrderedEvents();
        for (Event value : events) {
            Event event = value;

            if (event instanceof XAResourceCommitEvent)
                commitEventCount++;

            if (event instanceof JournalLogEvent) {
                if (((JournalLogEvent) event).getStatus() == Status.STATUS_UNKNOWN)
                    journalUnknownEventCount++;
            }

            if (event instanceof JournalLogEvent) {
                if (((JournalLogEvent) event).getStatus() == Status.STATUS_COMMITTING)
                    journalCommittingEventCount++;
            }

            if (event instanceof JournalLogEvent) {
                if (((JournalLogEvent) event).getStatus() == Status.STATUS_ROLLEDBACK)
                    journalRolledbackEventCount++;
            }
        }
        assertEquals(1, journalCommittingEventCount, "TM should have logged a COMMITTING status");
        assertEquals(1, journalRolledbackEventCount, "TM should have logged a ROLLEDBACK status");
        assertEquals(0, journalUnknownEventCount, "TM should not have logged ant UNKNOWN status");
        assertEquals(1, commitEventCount, "TM haven't properly tried to commit");
    }

    @BeforeEach
    protected void setUp() throws Exception {
        for (String name : ResourceRegistrar.getResourcesUniqueNames()) {
            ResourceRegistrar.unregister(ResourceRegistrar.get(name));
        }

        EventRecorder.clear();

        // change disk journal into mock journal
        Field field = TransactionManagerServices.class.getDeclaredField("journalRef");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Journal> journalRef = (AtomicReference<Journal>) field.get(TransactionManagerServices.class);
        journalRef.set(new MockJournal());


        poolingDataSource1 = new PoolingDataSource();
        poolingDataSource1.setClassName(MockitoXADataSource.class.getName());
        poolingDataSource1.setUniqueName("pds1");
        poolingDataSource1.setMinPoolSize(5);
        poolingDataSource1.setMaxPoolSize(5);
        poolingDataSource1.setAutomaticEnlistingEnabled(true);
        poolingDataSource1.init();

        tm = TransactionManagerServices.getTransactionManager();
    }

    @AfterEach
    protected void tearDown() throws Exception {
        poolingDataSource1.close();
        tm.shutdown();
    }

    private XAException createXAException(String msg, int errorCode) {
        XAException prepareException = new OracleXAException(msg, 9876);
        prepareException.errorCode = errorCode;
        return prepareException;
    }

}
