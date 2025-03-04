/**
 * XADataSource emulator using Last Resource Commit on an underlying non-XA DataSource.
 * Note that if you use the classes of this package you have accepted the heuristic hazard. A crash
 * during commit of a connection returned by this datasource could lead to an inconsistent global state.
 * This is a limitation of the Last Resource Commit technique, not of BTM.
 */
package bitronix.tm.resource.jdbc.lrc;