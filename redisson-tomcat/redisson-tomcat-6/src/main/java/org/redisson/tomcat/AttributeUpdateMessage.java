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
public class AttributeUpdateMessage extends AttributeMessage {

    private String name;
    private Object value;

    public AttributeUpdateMessage() {
    }
    
    public AttributeUpdateMessage(String sessionId, String name, Object value) {
        super(sessionId);
        this.name = name;
        this.value = value;
    }

    public AttributeUpdateMessage(String nodeId, String sessionId, String name, Object value) {
        super(nodeId, sessionId);
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }
    
    public Object getValue() {
        return value;
    }
    
}
