/**
 * Copyright (c) 2013-2019 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson;

import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Version {

    private static final Logger log = LoggerFactory.getLogger(Version.class);

    public static void logVersion() {
        try {
            Enumeration<URL> resources = Version.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                    Manifest manifest = new Manifest(resources.nextElement().openStream());
                    Attributes attrs = manifest.getMainAttributes();
                    if (attrs == null) {
                        continue;
                    }
                    String name = attrs.getValue("Bundle-Name");
                    if ("Redisson".equals(name)) {
                        log.info("Redisson " + attrs.getValue("Bundle-Version"));
                        break;
                    }
            }
        } catch (Exception E) {
            // skip it
        }
    }

}
