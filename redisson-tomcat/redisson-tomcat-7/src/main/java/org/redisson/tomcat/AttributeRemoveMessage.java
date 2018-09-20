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
public class AttributeRemoveMessage extends AttributeMessage {

    private String name;
    
    public AttributeRemoveMessage() {
        super();
    }

    public AttributeRemoveMessage(String sessionId, String name) {
        super(sessionId);
        this.name = name;
    }

    public AttributeRemoveMessage(String nodeId, String sessionId, String name) {
        super(nodeId, sessionId);
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
}
