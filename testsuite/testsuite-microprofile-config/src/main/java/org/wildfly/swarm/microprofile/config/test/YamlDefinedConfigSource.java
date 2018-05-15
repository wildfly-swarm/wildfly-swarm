/**
 * Copyright 2015-2017 Red Hat, Inc, and individual contributors.
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
package org.wildfly.swarm.microprofile.config.test;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Juan Gonzalez
 */
public class YamlDefinedConfigSource implements ConfigSource {
    public static final String PROP_FROM_CONFIG_SOURCE_FROM_YAML = "prop.from.config.source.from.yaml";

    private static final String NAME = YamlDefinedConfigSource.class.getSimpleName();

    private Map<String, String> props;

    public YamlDefinedConfigSource() {
        props = new HashMap<>();
        props.put("prop.from.config.source", NAME);
        props.put(PROP_FROM_CONFIG_SOURCE_FROM_YAML, NAME);
    }

    @Override
    public Map<String, String> getProperties() {
        return props;
    }

    @Override
    public String getValue(String propertyName) {
        return props.get(propertyName);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return 200;
    }
}
