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

import bitronix.tm.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuration repository of the transaction manager. You can set configurable values either via the properties file
 * or by setting properties of the {@link Configuration} object.
 * Once the transaction manager has started it is not possible to change the configuration: all calls to setters will
 * throw a {@link IllegalStateException}.
 * <p>The configuration filename must be specified with the <code>bitronix.tm.configuration</code> system property.</p>
 * <p>The default settings are good enough for running in a test environment but certainly not for production usage.
 * Also, all properties are reset to their default value after the transaction manager has shut down.</p>
 * <p>All those properties can refer to other defined ones or to system properties using the Ant notation:
 * <code>${some.property.name}</code>.</p>
 *
 * @author Ludovic Orban
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Configuration implements Service {

    private static final Logger log = LoggerFactory.getLogger(Configuration.class);

    private static final int MAX_SERVER_ID_LENGTH = 51;
    private static final Charset SERVER_ID_CHARSET = StandardCharsets.US_ASCII;

    private volatile String serverId;
    private final AtomicReference<byte[]> serverIdArray = new AtomicReference<>();
    private volatile String logPart1Filename;
    private volatile String logPart2Filename;
    private volatile boolean forcedWriteEnabled;
    private volatile boolean forceBatchingEnabled;
    private volatile int maxLogSizeInMb;
    private volatile boolean filterLogStatus;
    private volatile boolean skipCorruptedLogs;
    private volatile boolean asynchronous2Pc;
    private volatile boolean asynchronous2PcUseVirtualThread;
    private volatile boolean warnAboutZeroResourceTransaction;
    private volatile boolean debugZeroResourceTransaction;
    private volatile Duration defaultTransactionTimeout;
    private volatile Duration gracefulShutdownInterval;
    private volatile Duration backgroundRecoveryInterval;
    private volatile boolean disableJmx;
    private volatile boolean synchronousJmxRegistration;
    private volatile String jndiUserTransactionName;
    private volatile String jndiTransactionSynchronizationRegistryName;
    private volatile String journal;
    private volatile String exceptionAnalyzer;
    private volatile boolean currentNodeOnlyRecovery;
    private volatile boolean allowMultipleLrc;
    private volatile String resourceConfigurationFilename;
    private volatile boolean conservativeJournaling;
    private volatile String jdbcProxyFactoryClass;

    protected Configuration() {
        try {
            InputStream in = null;
            Properties properties;
            try {
                String configurationFilename = System.getProperty("bitronix.tm.configuration");
                if (configurationFilename != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("loading configuration file {}", configurationFilename);
                    }
                    in = new FileInputStream(configurationFilename);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("loading default configuration");
                    }
                    in = ClassLoaderUtils.getResourceAsStream("bitronix-default-config.properties");
                }
                properties = new Properties();
                if (in != null) {
                    properties.load(in);
                } else if (log.isDebugEnabled()) {
                    log.debug("no configuration file found, using default settings");
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }

            serverId = getString(properties, "bitronix.tm.serverId", null);
            logPart1Filename = getString(properties, "bitronix.tm.journal.disk.logPart1Filename", "btm1.tlog");
            logPart2Filename = getString(properties, "bitronix.tm.journal.disk.logPart2Filename", "btm2.tlog");
            forcedWriteEnabled = getBoolean(properties, "bitronix.tm.journal.disk.forcedWriteEnabled", true);
            forceBatchingEnabled = getBoolean(properties, "bitronix.tm.journal.disk.forceBatchingEnabled", true);
            maxLogSizeInMb = getInt(properties, "bitronix.tm.journal.disk.maxLogSize", 2);
            filterLogStatus = getBoolean(properties, "bitronix.tm.journal.disk.filterLogStatus", false);
            skipCorruptedLogs = getBoolean(properties, "bitronix.tm.journal.disk.skipCorruptedLogs", false);
            asynchronous2Pc = getBoolean(properties, "bitronix.tm.2pc.async", false);
            asynchronous2PcUseVirtualThread = getBoolean(properties, "bitronix.tm.2pc.async.virtualThread", false);
            warnAboutZeroResourceTransaction = getBoolean(properties, "bitronix.tm.2pc.warnAboutZeroResourceTransactions", true);
            debugZeroResourceTransaction = getBoolean(properties, "bitronix.tm.2pc.debugZeroResourceTransactions", false);
            defaultTransactionTimeout = getDuration(properties, "bitronix.tm.timer.defaultTransactionTimeout", Duration.ofSeconds(60L));
            gracefulShutdownInterval = getDuration(properties, "bitronix.tm.timer.gracefulShutdownInterval", Duration.ofSeconds(60L));
            backgroundRecoveryInterval = getDuration(properties, "bitronix.tm.timer.backgroundRecoveryInterval", Duration.ofSeconds(60L));
            disableJmx = getBoolean(properties, "bitronix.tm.disableJmx", false);
            synchronousJmxRegistration = getBoolean(properties, "bitronix.tm.jmx.sync", false);
            jndiUserTransactionName = getString(properties, "bitronix.tm.jndi.userTransactionName", "java:comp/UserTransaction");
            jndiTransactionSynchronizationRegistryName = getString(properties, "bitronix.tm.jndi.transactionSynchronizationRegistryName", "java:comp/TransactionSynchronizationRegistry");
            journal = getString(properties, "bitronix.tm.journal", "disk");
            exceptionAnalyzer = getString(properties, "bitronix.tm.exceptionAnalyzer", null);
            currentNodeOnlyRecovery = getBoolean(properties, "bitronix.tm.currentNodeOnlyRecovery", true);
            allowMultipleLrc = getBoolean(properties, "bitronix.tm.allowMultipleLrc", false);
            resourceConfigurationFilename = getString(properties, "bitronix.tm.resource.configuration", null);
            conservativeJournaling = getBoolean(properties, "bitronix.tm.conservativeJournaling", false);
            jdbcProxyFactoryClass = getString(properties, "bitronix.tm.jdbcProxyFactoryClass", "auto");
        } catch (IOException ex) {
            throw new InitializationException("error loading configuration", ex);
        }
    }


    /**
     * ASCII ID that must uniquely identify this TM instance. It must not exceed 51 characters or it will be truncated.
     * <p>Property name:<br><b>bitronix.tm.serverId -</b> <i>(defaults to server's IP address but that's unsafe for
     * production use)</i></p>
     *
     * @return the unique ID of this TM instance.
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Set the ASCII ID that must uniquely identify this TM instance. It must not exceed 51 characters or it will be
     * truncated.
     *
     * @param serverId the unique ID of this TM instance.
     * @return this.
     * @see #getServerId()
     */
    public Configuration setServerId(String serverId) {
        checkNotStarted();
        this.serverId = serverId;
        return this;
    }

    /**
     * Get the journal fragment file 1 name.
     * <p>Property name:<br><b>bitronix.tm.journal.disk.logPart1Filename -</b> <i>(defaults to btm1.tlog)</i></p>
     *
     * @return the journal fragment file 1 name.
     */
    public String getLogPart1Filename() {
        return logPart1Filename;
    }

    /**
     * Set the journal fragment file 1 name.
     *
     * @param logPart1Filename the journal fragment file 1 name.
     * @return this.
     * @see #getLogPart1Filename()
     */
    public Configuration setLogPart1Filename(String logPart1Filename) {
        checkNotStarted();
        this.logPart1Filename = logPart1Filename;
        return this;
    }

    /**
     * Get the journal fragment file 2 name.
     * <p>Property name:<br><b>bitronix.tm.journal.disk.logPart2Filename -</b> <i>(defaults to btm2.tlog)</i></p>
     *
     * @return the journal fragment file 2 name.
     */
    public String getLogPart2Filename() {
        return logPart2Filename;
    }

    /**
     * Set the journal fragment file 2 name.
     *
     * @param logPart2Filename the journal fragment file 2 name.
     * @return this.
     * @see #getLogPart2Filename()
     */
    public Configuration setLogPart2Filename(String logPart2Filename) {
        checkNotStarted();
        this.logPart2Filename = logPart2Filename;
        return this;
    }

    /**
     * Are logs forced to disk?  Do not set to false in production since without disk force, integrity is not
     * guaranteed.
     * <p>Property name:<br><b>bitronix.tm.journal.disk.forcedWriteEnabled -</b> <i>(defaults to true)</i></p>
     *
     * @return true if logs are forced to disk, false otherwise.
     */
    public boolean isForcedWriteEnabled() {
        return forcedWriteEnabled;
    }

    /**
     * Set if logs are forced to disk.  Do not set to false in production since without disk force, integrity is not
     * guaranteed.
     *
     * @param forcedWriteEnabled true if logs should be forced to disk, false otherwise.
     * @return this.
     * @see #isForcedWriteEnabled()
     */
    public Configuration setForcedWriteEnabled(boolean forcedWriteEnabled) {
        checkNotStarted();
        this.forcedWriteEnabled = forcedWriteEnabled;
        return this;
    }

    /**
     * Are disk forces batched? Disabling batching can seriously lower the transaction manager's throughput.
     * <p>Property name:<br><b>bitronix.tm.journal.disk.forceBatchingEnabled -</b> <i>(defaults to true)</i></p>
     *
     * @return true if disk forces are batched, false otherwise.
     */
    public boolean isForceBatchingEnabled() {
        return forceBatchingEnabled;
    }

    /**
     * Set if disk forces are batched. Disabling batching can seriously lower the transaction manager's throughput.
     *
     * @param forceBatchingEnabled true if disk forces are batched, false otherwise.
     * @return this.
     * @see #isForceBatchingEnabled()
     */
    public Configuration setForceBatchingEnabled(boolean forceBatchingEnabled) {
        checkNotStarted();
        log.warn("forceBatchingEnabled is not longer used");
        this.forceBatchingEnabled = forceBatchingEnabled;
        return this;
    }

    /**
     * Maximum size in megabytes of the journal fragments. Larger logs allow transactions to stay longer in-doubt but
     * the TM pauses longer when a fragment is full.
     * <p>Property name:<br><b>bitronix.tm.journal.disk.maxLogSize -</b> <i>(defaults to 2)</i></p>
     *
     * @return the maximum size in megabytes of the journal fragments.
     */
    public int getMaxLogSizeInMb() {
        return maxLogSizeInMb;
    }

    /**
     * Set the Maximum size in megabytes of the journal fragments. Larger logs allow transactions to stay longer
     * in-doubt but the TM pauses longer when a fragment is full.
     *
     * @param maxLogSizeInMb the maximum size in megabytes of the journal fragments.
     * @return this.
     * @see #getMaxLogSizeInMb()
     */
    public Configuration setMaxLogSizeInMb(int maxLogSizeInMb) {
        checkNotStarted();
        this.maxLogSizeInMb = maxLogSizeInMb;
        return this;
    }

    /**
     * Should only mandatory logs be written? Enabling this parameter lowers space usage of the fragments but makes
     * debugging more complex.
     * <p>Property name:<br><b>bitronix.tm.journal.disk.filterLogStatus -</b> <i>(defaults to false)</i></p>
     *
     * @return true if only mandatory logs should be written.
     */
    public boolean isFilterLogStatus() {
        return filterLogStatus;
    }

    /**
     * Set if only mandatory logs should be written. Enabling this parameter lowers space usage of the fragments but
     * makes debugging more complex.
     *
     * @param filterLogStatus true if only mandatory logs should be written.
     * @return this.
     * @see #isFilterLogStatus()
     */
    public Configuration setFilterLogStatus(boolean filterLogStatus) {
        checkNotStarted();
        this.filterLogStatus = filterLogStatus;
        return this;
    }

    /**
     * Should corrupted logs be skipped?
     * <p>Property name:<br><b>bitronix.tm.journal.disk.skipCorruptedLogs -</b> <i>(defaults to false)</i></p>
     *
     * @return true if corrupted logs should be skipped.
     */
    public boolean isSkipCorruptedLogs() {
        return skipCorruptedLogs;
    }

    /**
     * Set if corrupted logs should be skipped.
     *
     * @param skipCorruptedLogs true if corrupted logs should be skipped.
     * @return this.
     * @see #isSkipCorruptedLogs()
     */
    public Configuration setSkipCorruptedLogs(boolean skipCorruptedLogs) {
        checkNotStarted();
        this.skipCorruptedLogs = skipCorruptedLogs;
        return this;
    }

    /**
     * Should two phase commit be executed asynchronously? Asynchronous two phase commit can improve performance when
     * there are many resources enlisted in transactions but is more CPU intensive due to the dynamic thread spawning
     * requirements. It also makes debugging more complex.
     * <p>Property name:<br><b>bitronix.tm.2pc.async -</b> <i>(defaults to false)</i></p>
     *
     * @return true if two phase commit should be executed asynchronously.
     */
    public boolean isAsynchronous2Pc() {
        return asynchronous2Pc;
    }

    /**
     * Set if two phase commit should be executed asynchronously. Asynchronous two phase commit can improve performance
     * when there are many resources enlisted in transactions but is more CPU intensive due to the dynamic thread
     * spawning requirements. It also makes debugging more complex.
     *
     * @param asynchronous2Pc true if two phase commit should be executed asynchronously.
     * @return this.
     * @see #isAsynchronous2Pc()
     */
    public Configuration setAsynchronous2Pc(boolean asynchronous2Pc) {
        checkNotStarted();
        this.asynchronous2Pc = asynchronous2Pc;
        return this;
    }

    /**
     * Should asynchronous two phase commit use virtual thread ?
     * <p>Property name:<br><b>bitronix.tm.2pc.async.virtualThread -</b> <i>(defaults to false)</i></p>
     *
     * @return true if asynchronous two phase commit to use virtual thread.
     */
    public boolean isAsynchronous2PcUseVirtualThread() {
        return asynchronous2PcUseVirtualThread;
    }

    /**
     * Set if two phase commit should be executed asynchronously using virtual thread.
     *
     * @param asynchronous2PcUseVirtualThread true if two phase commit should be executed asynchronously using virtual thread.
     * @return this.
     * @see #isAsynchronous2Pc()
     */
    public Configuration isAsynchronous2PcUseVirtualThread(boolean asynchronous2PcUseVirtualThread) {
        checkNotStarted();
        this.asynchronous2PcUseVirtualThread = asynchronous2PcUseVirtualThread;
        return this;
    }

    /**
     * Should transactions executed without a single enlisted resource result in a warning or not? Most of the time
     * transactions executed with no enlisted resource reflect a bug or a mis-configuration somewhere.
     * <p>Property name:<br><b>bitronix.tm.2pc.warnAboutZeroResourceTransactions -</b> <i>(defaults to true)</i></p>
     *
     * @return true if transactions executed without a single enlisted resource should result in a warning.
     */
    public boolean isWarnAboutZeroResourceTransaction() {
        return warnAboutZeroResourceTransaction;
    }

    /**
     * Set if transactions executed without a single enlisted resource should result in a warning or not. Most of the
     * time transactions executed with no enlisted resource reflect a bug or a mis-configuration somewhere.
     *
     * @param warnAboutZeroResourceTransaction true if transactions executed without a single enlisted resource should
     *                                         result in a warning.
     * @return this.
     * @see #isWarnAboutZeroResourceTransaction()
     */
    public Configuration setWarnAboutZeroResourceTransaction(boolean warnAboutZeroResourceTransaction) {
        checkNotStarted();
        this.warnAboutZeroResourceTransaction = warnAboutZeroResourceTransaction;
        return this;
    }

    /**
     * Should creation and commit call stacks of transactions executed without a single enlisted tracked and logged
     * or not?
     * <p>Property name:<br><b>bitronix.tm.2pc.debugZeroResourceTransactions -</b> <i>(defaults to false)</i></p>
     *
     * @return true if creation and commit call stacks of transactions executed without a single enlisted resource
     * should be tracked and logged.
     */
    public boolean isDebugZeroResourceTransaction() {
        return debugZeroResourceTransaction;
    }

    /**
     * Set if creation and commit call stacks of transactions executed without a single enlisted resource should be
     * tracked and logged.
     *
     * @param debugZeroResourceTransaction true if the creation and commit call stacks of transaction executed without
     *                                     a single enlisted resource should be tracked and logged.
     * @return this.
     * @see #isDebugZeroResourceTransaction()
     * @see #isWarnAboutZeroResourceTransaction()
     */
    public Configuration setDebugZeroResourceTransaction(boolean debugZeroResourceTransaction) {
        checkNotStarted();
        this.debugZeroResourceTransaction = debugZeroResourceTransaction;
        return this;
    }

    /**
     * Default transaction timeout in seconds.
     * <p>Property name:<br><b>bitronix.tm.timer.defaultTransactionTimeout -</b> <i>(defaults to 60)</i></p>
     *
     * @return the default transaction timeout in seconds.
     */
    public Duration getDefaultTransactionTimeout() {
        return defaultTransactionTimeout;
    }

    /**
     * Set the default transaction timeout in seconds.
     *
     * @param defaultTransactionTimeout the default transaction timeout in seconds.
     * @return this.
     * @see #getDefaultTransactionTimeout()
     */
    public Configuration setDefaultTransactionTimeout(Duration defaultTransactionTimeout) {
        checkNotStarted();
        this.defaultTransactionTimeout = defaultTransactionTimeout;
        return this;
    }

    /**
     * Maximum amount of seconds the TM will wait for transactions to get done before aborting them at shutdown time.
     * <p>Property name:<br><b>bitronix.tm.timer.gracefulShutdownInterval -</b> <i>(defaults to 60)</i></p>
     *
     * @return the maximum amount of time in seconds.
     */
    public Duration getGracefulShutdownInterval() {
        return gracefulShutdownInterval;
    }

    /**
     * Set the maximum amount of seconds the TM will wait for transactions to get done before aborting them at shutdown
     * time.
     *
     * @param gracefulShutdownInterval the maximum amount of time in seconds.
     * @return this.
     * @see #getGracefulShutdownInterval()
     */
    public Configuration setGracefulShutdownInterval(Duration gracefulShutdownInterval) {
        checkNotStarted();
        this.gracefulShutdownInterval = gracefulShutdownInterval;
        return this;
    }

    /**
     * Interval in minutes at which to run the recovery process in the background. Disabled when set to 0.
     * <p>Property name:<br><b>bitronix.tm.timer.backgroundRecoveryInterval -</b> <i>(defaults to 0)</i></p>
     *
     * @return the interval in minutes.
     */
    public Duration getBackgroundRecoveryInterval() {
        return backgroundRecoveryInterval;
    }

    /**
     * Set the interval in minutes at which to run the recovery process in the background. Disabled when set to 0.
     *
     * @param backgroundRecoveryInterval the interval in minutes.
     * @return this.
     * @see #getBackgroundRecoveryInterval()
     */
    public Configuration setBackgroundRecoveryInterval(Duration backgroundRecoveryInterval) {
        checkNotStarted();
        this.backgroundRecoveryInterval = backgroundRecoveryInterval;
        return this;
    }

    /**
     * Should JMX Mbeans not be registered even if a JMX MBean server is detected?
     * <p>Property name:<br><b>bitronix.tm.disableJmx -</b> <i>(defaults to false)</i></p>
     *
     * @return true if JMX MBeans should never be registered.
     */
    public boolean isDisableJmx() {
        return disableJmx;
    }

    /**
     * Set to true if JMX Mbeans should not be registered even if a JMX MBean server is detected.
     *
     * @param disableJmx true if JMX MBeans should never be registered.
     * @return this.
     * @see #isDisableJmx()
     */
    public Configuration setDisableJmx(boolean disableJmx) {
        checkNotStarted();
        this.disableJmx = disableJmx;
        return this;
    }

    /**
     * Should JMX registrations and un-registrations be done in a synchronous / blocking way.
     * <p>
     * By default all JMX registrations are done asynchronously. Registrations and un-registrations
     * are combined to avoid the registration of short lived instances and increase the overall throughput.
     *
     * @return true if the caller should be blocked when MBeans are registered (defaults to false).
     */
    public boolean isSynchronousJmxRegistration() {
        return synchronousJmxRegistration;
    }

    /**
     * Toggles synchronous and asynchronous JMX registration mode.
     *
     * @param synchronousJmxRegistration true if the caller should be blocked when MBeans are registered
     *                                   (defaults to false).
     * @return this.
     */
    public Configuration setSynchronousJmxRegistration(boolean synchronousJmxRegistration) {
        checkNotStarted();
        this.synchronousJmxRegistration = synchronousJmxRegistration;
        return this;
    }

    /**
     * Get the name the {@link jakarta.transaction.UserTransaction} should be bound under in the
     * {@link bitronix.tm.jndi.BitronixContext}.
     *
     * @return the name the {@link jakarta.transaction.UserTransaction} should
     * be bound under in the {@link bitronix.tm.jndi.BitronixContext}.
     */
    public String getJndiUserTransactionName() {
        return jndiUserTransactionName;
    }

    /**
     * Set the name the {@link jakarta.transaction.UserTransaction} should be bound under in the
     * {@link bitronix.tm.jndi.BitronixContext}.
     *
     * @param jndiUserTransactionName the name the {@link jakarta.transaction.UserTransaction} should
     *                                be bound under in the {@link bitronix.tm.jndi.BitronixContext}.
     * @return this.
     * @see #getJndiUserTransactionName()
     */
    public Configuration setJndiUserTransactionName(String jndiUserTransactionName) {
        checkNotStarted();
        this.jndiUserTransactionName = jndiUserTransactionName;
        return this;
    }

    /**
     * Get the name the {@link jakarta.transaction.TransactionSynchronizationRegistry} should be bound under in the
     * {@link bitronix.tm.jndi.BitronixContext}.
     *
     * @return the name the {@link jakarta.transaction.TransactionSynchronizationRegistry} should
     * be bound under in the {@link bitronix.tm.jndi.BitronixContext}.
     */
    public String getJndiTransactionSynchronizationRegistryName() {
        return jndiTransactionSynchronizationRegistryName;
    }

    /**
     * Set the name the {@link jakarta.transaction.TransactionSynchronizationRegistry} should be bound under in the
     * {@link bitronix.tm.jndi.BitronixContext}.
     *
     * @param jndiTransactionSynchronizationRegistryName the name the {@link jakarta.transaction.TransactionSynchronizationRegistry} should
     *                                                   be bound under in the {@link bitronix.tm.jndi.BitronixContext}.
     * @return this.
     * @see #getJndiUserTransactionName()
     */
    public Configuration setJndiTransactionSynchronizationRegistryName(String jndiTransactionSynchronizationRegistryName) {
        checkNotStarted();
        this.jndiTransactionSynchronizationRegistryName = jndiTransactionSynchronizationRegistryName;
        return this;
    }

    /**
     * Get the journal implementation. Can be <code>disk</code>, <code>null</code> or a class name.
     *
     * @return the journal name.
     */
    public String getJournal() {
        return journal;
    }

    /**
     * Set the journal name. Can be <code>disk</code>, <code>null</code> or a class name.
     *
     * @param journal the journal name.
     * @return this.
     * @see #getJournal()
     */
    public Configuration setJournal(String journal) {
        checkNotStarted();
        this.journal = journal;
        return this;
    }

    /**
     * Get the exception analyzer implementation. Can be <code>null</code> for the default one or a class name.
     *
     * @return the exception analyzer name.
     */
    public String getExceptionAnalyzer() {
        return exceptionAnalyzer;
    }

    /**
     * Set the exception analyzer implementation. Can be <code>null</code> for the default one or a class name.
     *
     * @param exceptionAnalyzer the exception analyzer name.
     * @return this.
     * @see #getExceptionAnalyzer()
     */
    public Configuration setExceptionAnalyzer(String exceptionAnalyzer) {
        checkNotStarted();
        this.exceptionAnalyzer = exceptionAnalyzer;
        return this;
    }

    /**
     * Should the recovery process <b>not</b> recover XIDs generated with another JVM unique ID? Setting this property to true
     * is useful in clustered environments where multiple instances of BTM are running on different nodes.
     *
     * @return true if recovery should filter out recovered XIDs that do not contain this JVM's unique ID, false otherwise.
     * @see #getServerId() contains the value used as the JVM unique ID.
     */
    public boolean isCurrentNodeOnlyRecovery() {
        return currentNodeOnlyRecovery;
    }

    /**
     * Set to true if recovery should filter out recovered XIDs that do not contain this JVM's unique ID, false otherwise.
     *
     * @param currentNodeOnlyRecovery true if recovery should filter out recovered XIDs that do not contain this JVM's unique ID, false otherwise.
     * @return this.
     * @see #isCurrentNodeOnlyRecovery()
     */
    public Configuration setCurrentNodeOnlyRecovery(boolean currentNodeOnlyRecovery) {
        checkNotStarted();
        this.currentNodeOnlyRecovery = currentNodeOnlyRecovery;
        return this;
    }

    /**
     * Should the transaction manager allow enlistment of multiple LRC resources in a single transaction?
     * This is highly unsafe but could be useful for testing.
     *
     * @return true if the transaction manager should allow enlistment of multiple LRC resources in a single transaction, false otherwise.
     */
    public boolean isAllowMultipleLrc() {
        return allowMultipleLrc;
    }

    /**
     * Set to true if the transaction manager should allow enlistment of multiple LRC resources in a single transaction.
     *
     * @param allowMultipleLrc true if the transaction manager should allow enlistment of multiple LRC resources in a single transaction, false otherwise.
     * @return this
     */
    public Configuration setAllowMultipleLrc(boolean allowMultipleLrc) {
        checkNotStarted();
        this.allowMultipleLrc = allowMultipleLrc;
        return this;
    }

    /**
     * Should the Disk Journal follow a conservative (sequential write) policy?
     *
     * @return true if the Disk Journal should serialize writes to the transaction log, false otherwise.
     */
    public boolean isConservativeJournaling() {
        return conservativeJournaling;
    }

    /**
     * Set to true if the Disk Journal should follow a conservative (sequential write) policy.
     *
     * @param conservativeJournaling true if the Disk Journal should follow a conservative (sequential write) policy
     * @return this
     */
    public Configuration setConservativeJournaling(boolean conservativeJournaling) {
        checkNotStarted();
        this.conservativeJournaling = conservativeJournaling;
        return this;
    }

    /**
     * Get the factory class for creating JDBC proxy instances.
     *
     * @return the name of the factory class
     */
    public String getJdbcProxyFactoryClass() {
        return jdbcProxyFactoryClass;
    }


    /**
     * Set the name of the factory class for creating JDBC proxy instances.
     *
     * @param jdbcProxyFactoryClass the name of the proxy class
     */
    public void setJdbcProxyFactoryClass(String jdbcProxyFactoryClass) {
        this.jdbcProxyFactoryClass = jdbcProxyFactoryClass;
    }


    /**
     * {@link bitronix.tm.resource.ResourceLoader} configuration file name. {@link bitronix.tm.resource.ResourceLoader}
     * will be disabled if this value is null.
     * <p>Property name:<br><b>bitronix.tm.resource.configuration -</b> <i>(defaults to null)</i></p>
     *
     * @return the filename of the resources configuration file or null if not configured.
     */
    public String getResourceConfigurationFilename() {
        return resourceConfigurationFilename;
    }

    /**
     * Set the {@link bitronix.tm.resource.ResourceLoader} configuration file name.
     *
     * @param resourceConfigurationFilename the filename of the resources configuration file or null you do not want to
     *                                      use the {@link bitronix.tm.resource.ResourceLoader}.
     * @return this.
     * @see #getResourceConfigurationFilename()
     */
    public Configuration setResourceConfigurationFilename(String resourceConfigurationFilename) {
        checkNotStarted();
        this.resourceConfigurationFilename = resourceConfigurationFilename;
        return this;
    }

    /**
     * Build the server ID byte array that will be prepended in generated UIDs. Once built, the value is cached for the duration of the JVM lifespan.
     *
     * @return the server ID.
     */
    public byte[] buildServerIdArray() {
        byte[] id = serverIdArray.get();
        if (id == null) {
            // DCL is not a problem here, we just want to avoid multiple concurrent creations of the same array as it would look ugly in the logs.
            // More important is to avoid contended synchronizations when accessing this array as it is part of Uid creation happening when a TX is opened.
            synchronized (this) {
                while ((id = serverIdArray.get()) == null) {
                    try {
                        id = serverId.getBytes(SERVER_ID_CHARSET);

                        String transcodedId = new String(id, SERVER_ID_CHARSET);
                        if (!transcodedId.equals(serverId)) {
                            log.warn("The given server ID '" + serverId + "' is not compatible with the ID charset '" + SERVER_ID_CHARSET.displayName() + "' as it transcodes to '" + transcodedId + "'. " +
                                    "It is highly recommended that you specify a compatible server ID using only characters that are allowed in the ID charset.");
                        }
                    } catch (Exception ex) {
                        log.warn("Cannot get the unique server ID for this JVM ('bitronix.tm.serverId'). Make sure it is configured and you use only " + SERVER_ID_CHARSET.displayName() + " characters. " +
                                "Will use IP address instead (unsafe for production usage!).");
                        try {
                            id = InetAddress.getLocalHost().getHostAddress().getBytes(SERVER_ID_CHARSET);
                        } catch (Exception ex2) {
                            final String unknownServerId = "unknown-server-id";
                            log.warn("Cannot get the local IP address. Please verify your network configuration. Will use the constant '" + unknownServerId + "' as server ID (highly unsafe!).", ex2);
                            id = unknownServerId.getBytes();
                        }
                    }

                    if (id.length > MAX_SERVER_ID_LENGTH) {
                        byte[] truncatedServerId = new byte[MAX_SERVER_ID_LENGTH];
                        System.arraycopy(id, 0, truncatedServerId, 0, MAX_SERVER_ID_LENGTH);
                        log.warn("The applied server ID '" + new String(id) + "' has to be truncated to " + MAX_SERVER_ID_LENGTH +
                                " chars (builtin hard limit) resulting in " + new String(truncatedServerId) + ". This may be highly unsafe if IDs differ with suffixes only!");
                        id = truncatedServerId;
                    }

                    if (serverIdArray.compareAndSet(null, id)) {
                        String idAsString = new String(id, SERVER_ID_CHARSET);
                        if (serverId == null) {
                            serverId = idAsString;
                        }

                        log.info("JVM unique ID: <" + idAsString + "> - Using this server ID to ensure uniqueness of transaction IDs across the network.");
                    }
                }
            }
        }
        return id;
    }

    @Override
    public void shutdown() {
        serverIdArray.set(null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("a Configuration with [");

        try {
            sb.append(PropertyUtils.propertiesToString(this));
        } catch (PropertyException ex) {
            sb.append("???");
            if (log.isDebugEnabled()) {
                log.debug("error accessing properties of Configuration object", ex);
            }
        }

        sb.append("]");
        return sb.toString();
    }

    /*
     * Internal implementation
     */

    private void checkNotStarted() {
        if (TransactionManagerServices.isTransactionManagerRunning()) {
            throw new IllegalStateException("cannot change the configuration while the transaction manager is running");
        }
    }

    static String getString(Properties properties, String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            value = properties.getProperty(key);
            if (value == null) {
                return defaultValue;
            }
        }
        return evaluate(properties, value);
    }

    static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        return Boolean.parseBoolean(getString(properties, key, "" + defaultValue));
    }

    static int getInt(Properties properties, String key, int defaultValue) {
        return Integer.parseInt(getString(properties, key, "" + defaultValue));
    }

    /**
     * A more readable format where the value and the unit are coupled.
     * TODO support value like 1000ms, 1s, 1m etc. Inspired by enum DurationStyle in Spring Boot
     *
     * @param properties   properties file
     * @param key          key to get the value
     * @param defaultValue the default unit is milliseconds
     * @return Duration value
     */
    static Duration getDuration(Properties properties, String key, Duration defaultValue) {
        return Duration.parse(getString(properties, key, defaultValue.toString()));
    }

    private static String evaluate(Properties properties, String value) {
        String result = value;

        int startIndex = value.indexOf('$');
        if (startIndex > -1 && value.length() > startIndex + 1 && value.charAt(startIndex + 1) == '{') {
            int endIndex = value.indexOf('}');
            if (startIndex + 2 == endIndex) {
                throw new IllegalArgumentException("property ref cannot refer to an empty name: ${}");
            }
            if (endIndex == -1) {
                throw new IllegalArgumentException("unclosed property ref: ${" + value.substring(startIndex + 2));
            }

            String subPropertyKey = value.substring(startIndex + 2, endIndex);
            String subPropertyValue = getString(properties, subPropertyKey, null);

            result = result.substring(0, startIndex) + subPropertyValue + result.substring(endIndex + 1);
            return evaluate(properties, result);
        }

        return result;
    }

}
