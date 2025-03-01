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

import bitronix.tm.utils.Uid;

import javax.transaction.xa.Xid;

/**
 * Implementation of {@link javax.transaction.xa.Xid}.
 * <p>A XID is divided in two parts: globalTransactionId (GTRID) and branchQualifier (BQUAL). The first one uniquely
 * identifies the global transaction while the latter uniquely identifies the transaction branch, or the local part of
 * the global transaction inside a resource.</p>
 * <p>Technically in the Bitronix implementation, GTRID and BQUAL have the same format as described by Mike Spille.
 * Each {@link bitronix.tm.BitronixTransaction} get assigned a GTRID at creation time and full XIDs are created and
 * assigned to every {@link bitronix.tm.internal.XAResourceHolderState} when enlisted in the transaction's
 * {@link bitronix.tm.internal.XAResourceManager}. Both GTRID and XIDs are generated
 * by the {@link bitronix.tm.utils.UidGenerator}.</p>
 *
 * @author Ludovic Orban
 * @see bitronix.tm.utils.UidGenerator
 * @see bitronix.tm.BitronixTransaction
 * @see bitronix.tm.internal.XAResourceManager
 * @see <a href="http://jroller.com/page/pyrasun?entry=xa_exposed_part_iii_the">XA Exposed, Part III: The Implementor's Notebook</a>
 */
public class BitronixXid implements Xid {

    /**
     * int-encoded "Btnx" string. This is used as the globally unique ID to discriminate BTM XIDs.
     */
    public static final int FORMAT_ID = 0x42746e78;

    private final Uid globalTransactionId;
    private final Uid branchQualifier;
    private final int hashCodeValue;
    private final String toStringValue;

    /**
     * Create a new XID using the specified GTRID and BQUAL.
     *
     * @param globalTransactionId the GTRID.
     * @param branchQualifier     the BQUAL.
     */
    public BitronixXid(Uid globalTransactionId, Uid branchQualifier) {
        this.globalTransactionId = globalTransactionId;
        this.branchQualifier = branchQualifier;
        this.toStringValue = precalculateToString();
        this.hashCodeValue = precalculateHashCode();
    }

    public BitronixXid(Xid xid) {
        this.globalTransactionId = new Uid(xid.getGlobalTransactionId());
        this.branchQualifier = new Uid(xid.getBranchQualifier());
        this.toStringValue = precalculateToString();
        this.hashCodeValue = precalculateHashCode();
    }

    /**
     * Get Bitronix XID format ID. Defined by {@link BitronixXid#FORMAT_ID}.
     *
     * @return the Bitronix XID format ID.
     */
    @Override
    public int getFormatId() {
        return FORMAT_ID;
    }

    /**
     * Get the BQUAL of the XID.
     *
     * @return the XID branch qualifier.
     */
    @Override
    public byte[] getBranchQualifier() {
        return branchQualifier.getArray();
    }

    public Uid getBranchQualifierUid() {
        return branchQualifier;
    }

    /**
     * Get the GTRID of the XID.
     *
     * @return the XID global transaction ID.
     */
    @Override
    public byte[] getGlobalTransactionId() {
        return globalTransactionId.getArray();
    }

    public Uid getGlobalTransactionIdUid() {
        return globalTransactionId;
    }

    /**
     * Get a human-readable string representation of the XID.
     *
     * @return a human-readable string representation.
     */
    @Override
    public String toString() {
        return toStringValue;
    }

    private String precalculateToString() {
        return "a Bitronix XID [" +
                globalTransactionId.toString() +
                " : " +
                branchQualifier.toString() +
                "]";
    }

    /**
     * Compare two XIDs for equality.
     *
     * @param obj the XID to compare to.
     * @return true if both XIDs have the same format ID and contain exactly the same GTRID and BQUAL.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BitronixXid otherXid)) {
            return false;
        }

        return FORMAT_ID == otherXid.getFormatId() &&
                globalTransactionId.equals(otherXid.getGlobalTransactionIdUid()) &&
                branchQualifier.equals(otherXid.getBranchQualifierUid());
    }

    /**
     * Get an integer hash for the XID.
     *
     * @return a constant hash value.
     */
    @Override
    public int hashCode() {
        return hashCodeValue;
    }

    private int precalculateHashCode() {
        int hashCode = FORMAT_ID;
        if (globalTransactionId != null) {
            hashCode += globalTransactionId.hashCode();
        }
        if (branchQualifier != null) {
            hashCode += branchQualifier.hashCode();
        }
        return hashCode;
    }

}
