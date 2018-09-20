// Copyright (c) 2018. Engie-Electrabel. All rights reserved.
//
// Engie-Electrabel n.v./s.a., Simon Bolivarlaan 34 Boulevard Simón Bolivar, BTW BE 0403.107.701 - 1000 Brussel/Bruxelles, Belgium.
//
// Proprietary Notice:
// This software is the confidential and proprietary information of Engie-Electrabel s.a./n.v. and/or its licensors.
// You shall not disclose this Confidential Information to any third parties and any use thereof shall be subject to the terms and conditions of use, as agreed upon with Engie-Electrabel in writing.
//
package org.redisson.tomcat;

import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AttributesPutAllMessage extends AttributeMessage {

    private Map<String, Object> attrs;
    
    public AttributesPutAllMessage() {
    }

    public AttributesPutAllMessage(String sessionId, Map<String, Object> attrs) {
        super(sessionId);
        this.attrs = attrs;
    }

    public AttributesPutAllMessage(String nodeId, String sessionId, Map<String, Object> attrs) {
        super(nodeId, sessionId);
        this.attrs = attrs;
    }

    public Map<String, Object> getAttrs() {
        return attrs;
    }

}
