/**
 * Copyright 2015 Red Hat, Inc, and individual contributors.
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
package org.wildfly.swarm.bootstrap.modules;

import org.jboss.modules.Environment;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ModuleXmlParserBridge;
import org.jboss.modules.ResourceLoader;
import org.wildfly.swarm.bootstrap.util.Layout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/** Used only for loading dependencies of org.wildfly.bootstrap:main from its own jar.
 *
 * @author Bob McWhirter
 */
public class BootstrapClasspathModuleFinder implements ModuleFinder {

    @Override
    public ModuleSpec findModule(ModuleIdentifier identifier, ModuleLoader delegateLoader) throws ModuleLoadException {
        final String path = "modules/" + identifier.getName().replace('.', '/') + "/" + identifier.getSlot() + "/module.xml";

        ClassLoader cl = BootstrapClasspathModuleFinder.class.getClassLoader();
        URL url = cl.getResource(path);

        if (url == null) {
            return null;
        }

        //System.err.println( "BootstrapClasspathModuleFinder: " + identifier );

        ModuleSpec moduleSpec = null;
        InputStream in = null;
        try {
            final URL base = new URL( url, "./" );
            in = url.openStream();
            moduleSpec = ModuleXmlParserBridge.parseModuleXml(new ModuleXmlParserBridge.ResourceRootFactoryBridge() {
                @Override
                public ResourceLoader createResourceLoader(final String rootPath, final String loaderPath, final String loaderName) throws IOException {
                    //return Environment.getModuleResourceLoader(rootPath, loaderPath, loaderName);
                    return NestedJarResourceLoader.loaderFor( base, rootPath, loaderPath, loaderName );
                }
            }, "/", in, path.toString(), delegateLoader, identifier);

        } catch (IOException e) {
            e.printStackTrace();
            throw new ModuleLoadException(e);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            try {
                if ( in != null ) {
                    in.close();
                }
            } catch (IOException e) {
                throw new ModuleLoadException(e);
            }
        }
        return moduleSpec;

    }
}
