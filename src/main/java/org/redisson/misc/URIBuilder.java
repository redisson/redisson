package org.redisson.misc;

import java.net.URI;

public class URIBuilder {

    public static URI create(String uri) {
        String[] parts = uri.split(":");
        if (parts.length-1 >= 3) {
            String port = parts[parts.length-1];
            uri = "[" + uri.replace(":" + port, "") + "]:" + port;
        }

        return URI.create("//" + uri);
    }
    
}
