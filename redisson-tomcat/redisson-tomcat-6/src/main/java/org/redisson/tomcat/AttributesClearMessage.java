// Copyright (c) 2018. Engie-Electrabel. All rights reserved.
//
// Engie-Electrabel n.v./s.a., Simon Bolivarlaan 34 Boulevard Simón Bolivar, BTW BE 0403.107.701 - 1000 Brussel/Bruxelles, Belgium.
//
// Proprietary Notice:
// This software is the confidential and proprietary information of Engie-Electrabel s.a./n.v. and/or its licensors.
// You shall not disclose this Confidential Information to any third parties and any use thereof shall be subject to the terms and conditions of use, as agreed upon with Engie-Electrabel in writing.
//
package org.redisson.tomcat;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AttributesClearMessage extends AttributeMessage {

    public AttributesClearMessage() {
    }

    public AttributesClearMessage(String sessionId) {
        super(sessionId);
    }

    public AttributesClearMessage(String nodeId, String sessionId) {
        super(nodeId, sessionId);
    }

}
