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
package bitronix.tm.mock;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.mock.resource.jdbc.MockitoXADataSource;
import bitronix.tm.recovery.RecoveryException;
import bitronix.tm.resource.ResourceConfigurationException;
import bitronix.tm.resource.common.XAPool;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import jakarta.transaction.TransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ludovic Orban
 */
public class JdbcPoolTest {


    private final static Logger log = LoggerFactory.getLogger(JdbcPoolTest.class);
    private PoolingDataSource pds;

    @BeforeEach
    protected void setUp() throws Exception {
        TransactionManagerServices.getConfiguration().setJournal("null").setGracefulShutdownInterval(Duration.ofSeconds(2L));
        TransactionManagerServices.getTransactionManager();

        MockitoXADataSource.setStaticCloseXAConnectionException(null);
        MockitoXADataSource.setStaticGetXAConnectionException(null);

        pds = new PoolingDataSource();
        pds.setMinPoolSize(1);
        pds.setMaxPoolSize(2);
        pds.setMaxIdleTime(1);
        pds.setClassName(MockitoXADataSource.class.getName());
        pds.setUniqueName("pds");
        pds.setAllowLocalTransactions(true);
        pds.setAcquisitionTimeout(1);
        pds.init();
    }

    @AfterEach
    protected void tearDown() throws Exception {
        pds.close();

        TransactionManagerServices.getTransactionManager().shutdown();
    }

    @Test
    public void testObjectProperties() throws Exception {
        pds.close();

        pds = new PoolingDataSource();
        pds.setUniqueName("pds");
        pds.setClassName(MockitoXADataSource.class.getName());
        pds.setMinPoolSize(1);
        pds.setMaxPoolSize(1);
        pds.getDriverProperties().put("uselessThing", new Object());
        pds.init();

        assertNotNull(pds.getDriverProperties().get("uselessThing"));
    }

    @Test
    public void testInitFailure() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testInitFailure");
        }
        pds.close();

        pds = new PoolingDataSource();
        pds.setMinPoolSize(0);
        pds.setMaxPoolSize(2);
        pds.setMaxIdleTime(1);
        pds.setClassName(MockitoXADataSource.class.getName());
        pds.setUniqueName("pds");
        pds.setAllowLocalTransactions(true);
        pds.setAcquisitionTimeout(1);

        TransactionManagerServices.getTransactionManager().begin();

        MockitoXADataSource.setStaticGetXAConnectionException(new SQLException("not yet started"));
        try {
            pds.init();
            fail("expected ResourceConfigurationException");
        } catch (ResourceConfigurationException ex) {
            Throwable rootCause = ex.getCause().getCause();
            assertEquals(SQLException.class, rootCause.getClass());
            assertEquals("not yet started", rootCause.getMessage());
        }

        MockitoXADataSource.setStaticGetXAConnectionException(null);
        pds.init();

        pds.getConnection().prepareStatement("");

        TransactionManagerServices.getTransactionManager().commit();
    }

    @Test
    public void testReEnteringRecovery() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testReEnteringRecovery");
        }
        pds.startRecovery();
        try {
            pds.startRecovery();
            fail("expected RecoveryException");
        } catch (RecoveryException ex) {
            assertEquals("recovery already in progress on a PoolingDataSource containing an XAPool of resource pds with 1 connection(s) (0 still available)", ex.getMessage());
        }

        // make sure startRecovery() can be called again once endRecovery() has been called
        pds.endRecovery();
        pds.startRecovery();
        pds.endRecovery();
    }

    @Test
    public void testPoolGrowth() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testPoolGrowth");
        }
        Field poolField = pds.getClass().getDeclaredField("pool");
        poolField.setAccessible(true);
        XAPool pool = (XAPool) poolField.get(pds);

        assertEquals(1, pool.inPoolSize());
        assertEquals(1, pool.totalPoolSize());

        Connection c1 = pds.getConnection();
        assertEquals(0, pool.inPoolSize());
        assertEquals(1, pool.totalPoolSize());

        Connection c2 = pds.getConnection();
        assertEquals(0, pool.inPoolSize());
        assertEquals(2, pool.totalPoolSize());

        try {
            pds.getConnection();
            fail("should not be able to get a 3rd connection");
        } catch (SQLException ex) {
            assertEquals("unable to get a connection from pool of a PoolingDataSource containing an XAPool of resource pds with 2 connection(s) (0 still available)", ex.getMessage());
        }

        c1.close();
        c2.close();
        assertEquals(2, pool.inPoolSize());
        assertEquals(2, pool.totalPoolSize());
    }

    @Test
    public void testPoolShrink() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testPoolShrink");
        }
        Field poolField = pds.getClass().getDeclaredField("pool");
        poolField.setAccessible(true);
        XAPool pool = (XAPool) poolField.get(pds);

        assertEquals(1, pool.inPoolSize());
        assertEquals(1, pool.totalPoolSize());

        Connection c1 = pds.getConnection();
        assertEquals(0, pool.inPoolSize());
        assertEquals(1, pool.totalPoolSize());

        Connection c2 = pds.getConnection();
        assertEquals(0, pool.inPoolSize());
        assertEquals(2, pool.totalPoolSize());

        c1.close();
        c2.close();

        Thread.sleep(1100); // leave enough time for the idle connections to expire
        TransactionManagerServices.getTaskScheduler().interrupt(); // wake up the task scheduler
        Thread.sleep(1200); // leave enough time for the scheduled shrinking task to do its work

        if (log.isDebugEnabled()) {
            log.debug("*** checking pool sizes");
        }
        assertEquals(1, pool.inPoolSize());
        assertEquals(1, pool.totalPoolSize());
    }

    @Test
    public void testPoolShrinkErrorHandling() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testPoolShrinkErrorHandling");
        }

        Field poolField = pds.getClass().getDeclaredField("pool");
        poolField.setAccessible(true);
        XAPool pool = (XAPool) poolField.get(pds);

        pds.setMinPoolSize(0);
        pds.reset();
        pds.setMinPoolSize(1);
        MockitoXADataSource.setStaticCloseXAConnectionException(new SQLException("close fails because datasource broken"));
        pds.reset();

        // the pool is now loaded with one connection which will throw an exception when closed
        Thread.sleep(1100); // leave enough time for the ide connections to expire
        TransactionManagerServices.getTaskScheduler().interrupt(); // wake up the task scheduler
        Thread.sleep(100); // leave enough time for the scheduled shrinking task to do its work
        assertEquals(1, pool.inPoolSize());

        MockitoXADataSource.setStaticGetXAConnectionException(new SQLException("getXAConnection fails because datasource broken"));
        Thread.sleep(1100); // leave enough time for the ide connections to expire
        TransactionManagerServices.getTaskScheduler().interrupt(); // wake up the task scheduler
        Thread.sleep(100); // leave enough time for the scheduled shrinking task to do its work
        assertEquals(0, pool.inPoolSize());

        MockitoXADataSource.setStaticGetXAConnectionException(null);
        Thread.sleep(1100); // leave enough time for the ide connections to expire
        TransactionManagerServices.getTaskScheduler().interrupt(); // wake up the task scheduler
        Thread.sleep(100); // leave enough time for the scheduled shrinking task to do its work
        assertEquals(1, pool.inPoolSize());
    }

    @Test
    public void testPoolReset() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testPoolReset");
        }

        Field poolField = pds.getClass().getDeclaredField("pool");
        poolField.setAccessible(true);
        XAPool pool = (XAPool) poolField.get(pds);

        assertEquals(1, pool.inPoolSize());
        assertEquals(1, pool.totalPoolSize());

        Connection c1 = pds.getConnection();
        assertEquals(0, pool.inPoolSize());
        assertEquals(1, pool.totalPoolSize());

        Connection c2 = pds.getConnection();
        assertEquals(0, pool.inPoolSize());
        assertEquals(2, pool.totalPoolSize());

        c1.close();
        c2.close();

        pds.reset();

        assertEquals(1, pool.inPoolSize());
        assertEquals(1, pool.totalPoolSize());
    }

    @Test
    public void testPoolResetErrorHandling() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testPoolResetErrorHandling");
        }
        Field poolField = pds.getClass().getDeclaredField("pool");
        poolField.setAccessible(true);
        XAPool pool = (XAPool) poolField.get(pds);

        pds.setMinPoolSize(0);
        pds.reset();
        pds.setMinPoolSize(1);
        MockitoXADataSource.setStaticCloseXAConnectionException(new SQLException("close fails because datasource broken"));
        pds.reset();

        // the pool is now loaded with one connection which will throw an exception when closed
        pds.reset();

        try {
            MockitoXADataSource.setStaticGetXAConnectionException(new SQLException("getXAConnection fails because datasource broken"));
            pds.reset();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("getXAConnection fails because datasource broken", ex.getMessage());
            assertEquals(0, pool.inPoolSize());
        }

        MockitoXADataSource.setStaticGetXAConnectionException(null);
        pds.reset();
        assertEquals(1, pool.inPoolSize());
    }

    @Test
    public void testCloseLocalContext() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testCloseLocalContext");
        }
        Connection c = pds.getConnection();
        Statement stmt = c.createStatement();
        stmt.close();
        c.close();
        assertTrue(c.isClosed());

        try {
            c.createStatement();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("connection handle already closed", ex.getMessage());
        }
    }

    @Test
    public void testCloseGlobalContextRecycle() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testCloseGlobalContextRecycle");
        }
        TransactionManager tm = TransactionManagerServices.getTransactionManager();
        tm.begin();

        Connection c1 = pds.getConnection();
        c1.createStatement();
        c1.close();
        assertTrue(c1.isClosed());

        try {
            c1.createStatement();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("connection handle already closed", ex.getMessage());
        }

        Connection c2 = pds.getConnection();
        c2.createStatement();

        try {
            c2.commit();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("cannot commit a resource enlisted in a global transaction", ex.getMessage());
        }

        tm.commit();
        assertFalse(c2.isClosed());

        c2.close();
        assertTrue(c2.isClosed());

        try {
            c2.createStatement();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("connection handle already closed", ex.getMessage());
        }

        try {
            c2.commit();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("connection handle already closed", ex.getMessage());
        }
    }

    @Test
    public void testCloseGlobalContextNoRecycle() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testCloseGlobalContextNoRecycle");
        }
        TransactionManager tm = TransactionManagerServices.getTransactionManager();
        tm.begin();

        Connection c1 = pds.getConnection();
        Connection c2 = pds.getConnection();
        c1.createStatement();
        c1.close();
        assertTrue(c1.isClosed());

        try {
            c1.createStatement();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("connection handle already closed", ex.getMessage());
        }

        c2.createStatement();

        try {
            c2.commit();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("cannot commit a resource enlisted in a global transaction", ex.getMessage());
        }

        tm.commit();
        assertFalse(c2.isClosed());

        c2.close();
        assertTrue(c2.isClosed());

        try {
            c2.createStatement();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("connection handle already closed", ex.getMessage());
        }

        try {
            c2.commit();
            fail("expected SQLException");
        } catch (SQLException ex) {
            assertEquals("connection handle already closed", ex.getMessage());
        }
    }

    @Test
    public void testPoolNotStartingTransactionManager() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testPoolNotStartingTransactionManager");
        }
        // make sure TM is not running
        TransactionManagerServices.getTransactionManager().shutdown();

        PoolingDataSource pds = new PoolingDataSource();
        pds.setMinPoolSize(1);
        pds.setMaxPoolSize(2);
        pds.setMaxIdleTime(1);
        pds.setClassName(MockitoXADataSource.class.getName());
        pds.setUniqueName("pds2");
        pds.setAllowLocalTransactions(true);
        pds.setAcquisitionTimeout(1);
        pds.init();

        assertFalse(TransactionManagerServices.isTransactionManagerRunning());

        Connection c = pds.getConnection();
        Statement stmt = c.createStatement();
        stmt.close();
        c.close();

        assertFalse(TransactionManagerServices.isTransactionManagerRunning());

        pds.close();

        assertFalse(TransactionManagerServices.isTransactionManagerRunning());
    }

    @Test
    public void testWrappers() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("*** Starting testWrappers");
        }

        // XADataSource
        assertTrue(pds.isWrapperFor(XADataSource.class));
        assertFalse(pds.isWrapperFor(DataSource.class));
        XADataSource unwrappedXads = pds.unwrap(XADataSource.class);
        assertEquals(MockitoXADataSource.class.getName(), unwrappedXads.getClass().getName());

        // Connection
        Connection c = pds.getConnection();
        assertTrue(isWrapperFor(c, Connection.class));
        Connection unwrappedConnection = (Connection) unwrap(c, Connection.class);
        log.info("unwrappedConnection: {}", unwrappedConnection.toString());
        Class<?> clazz = unwrappedConnection.getClass();
        log.info("unwrappedConnection class: {}", clazz);
        String name = clazz.getName();
        log.info("unwrappedConnection class name: {}", name);
        assertTrue(unwrappedConnection.getClass().getName().contains("org.mockito.codegen.Connection"));

        // Statement
        Statement stmt = c.createStatement();
        assertTrue(isWrapperFor(stmt, Statement.class));
        Statement unwrappedStmt = (Statement) unwrap(stmt, Statement.class);
        assertTrue(unwrappedStmt.getClass().getName().contains("org.mockito.codegen.Statement"));

        // PreparedStatement
        PreparedStatement pstmt = c.prepareStatement("mock sql");
        assertTrue(isWrapperFor(pstmt, PreparedStatement.class));
        Statement unwrappedPStmt = (Statement) unwrap(pstmt, PreparedStatement.class);
        assertTrue(unwrappedPStmt.getClass().getName().contains("org.mockito.codegen.PreparedStatement"));

        // CallableStatement
        CallableStatement cstmt = c.prepareCall("mock stored proc");
        assertTrue(isWrapperFor(cstmt, CallableStatement.class));
        Statement unwrappedCStmt = (Statement) unwrap(cstmt, CallableStatement.class);
        assertTrue(unwrappedCStmt.getClass().getName().contains("org.mockito.codegen.CallableStatement"));
    }

    private static boolean isWrapperFor(Object obj, Class<?> param) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method isWrapperForMethod = obj.getClass().getMethod("isWrapperFor", Class.class);
        return (Boolean) isWrapperForMethod.invoke(obj, param);
    }

    private static Object unwrap(Object obj, Class<?> param) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method unwrapMethod = obj.getClass().getMethod("unwrap", Class.class);
        return unwrapMethod.invoke(obj, param);
    }

}
