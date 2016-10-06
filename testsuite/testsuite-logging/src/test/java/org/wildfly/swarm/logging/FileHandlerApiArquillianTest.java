/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
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
package org.wildfly.swarm.logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.arquillian.CreateSwarm;
import org.wildfly.swarm.config.logging.Level;
import org.wildfly.swarm.spi.api.JARArchive;

import static org.junit.Assert.assertTrue;

/**
 * @author Charles Moulliard
 */
@RunWith(Arquillian.class)
public class FileHandlerApiArquillianTest {

    final static String logFile = System.getProperty("user.dir") + File.separator + "swarmy.log";

    @Deployment
    public static Archive createDeployment() {
        JARArchive archive = ShrinkWrap.create(JARArchive.class, "empty.jar");
        archive.addPackage(FileHandlerApiArquillianTest.class.getPackage());
        return archive;
    }

    @CreateSwarm
    public static Swarm newContainer() throws Exception {
        return new Swarm()
                .fraction(new LoggingFraction()
                                  .createDefaultLoggingFraction()  // Required for Testing reason as Arquillian Swarm looks if the deployment of the jar is done
                                  .fileHandler("FILE",f -> {
                                      HashMap<String, String> props = new HashMap<String, String>();
                                      props.put("path",logFile);
                                      f.file(props);
                                      f.level(Level.INFO);
                                      f.formatter("%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n");
                                  })
                                  .rootLogger(Level.INFO, "CONSOLE", "FILE"));
    }

    @Test
    public void doLogging() throws IOException {
        String message = "testing: " + UUID.randomUUID().toString();
        Logger logger = Logger.getLogger("org.wildfly.swarm.logging");
        logger.info(message);

        Path path = Paths.get(logFile);
        assertTrue("File not found: " + logFile, Files.exists(path));
        List<String> lines = Files.readAllLines(path);

        boolean found = false;

        for (String line : lines) {
            if (line.contains(message)) {
                found = true;
                break;
            }
        }

        assertTrue("Expected message " + message, found);
    }

}
