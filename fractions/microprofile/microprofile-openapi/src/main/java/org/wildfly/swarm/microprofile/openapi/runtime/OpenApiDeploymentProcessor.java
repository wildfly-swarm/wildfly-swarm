/**
 * Copyright 2018 Red Hat, Inc, and individual contributors.
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

package org.wildfly.swarm.microprofile.openapi.runtime;

import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.OASModelReader;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
import org.wildfly.swarm.microprofile.openapi.OpenApiConstants;
import org.wildfly.swarm.microprofile.openapi.io.OpenApiParser;
import org.wildfly.swarm.microprofile.openapi.io.OpenApiSerializer.Format;
import org.wildfly.swarm.microprofile.openapi.models.OpenAPIImpl;
import org.wildfly.swarm.microprofile.openapi.models.PathsImpl;
import org.wildfly.swarm.microprofile.openapi.models.info.InfoImpl;
import org.wildfly.swarm.microprofile.openapi.util.FilterUtil;
import org.wildfly.swarm.microprofile.openapi.util.MergeUtil;
import org.wildfly.swarm.microprofile.openapi.util.ServersUtil;
import org.wildfly.swarm.spi.api.DeploymentProcessor;
import org.wildfly.swarm.spi.runtime.annotations.DeploymentScoped;

/**
 * @author eric.wittmann@gmail.com
 */
@SuppressWarnings("rawtypes")
@DeploymentScoped
public class OpenApiDeploymentProcessor implements DeploymentProcessor {

    private final OpenApiConfig config;
    private final Archive archive;

    /**
     * Constructor.
     * @param config
     * @param archive
     */
    @Inject
    public OpenApiDeploymentProcessor(OpenApiConfig config, Archive archive) {
        this.config = config;
        this.archive = archive;
    }

    /**
     * Process the deployment in order to produce an OpenAPI document.
     *
     * @see org.wildfly.swarm.spi.api.DeploymentProcessor#process()
     */
    @Override
    public void process() throws Exception {
        // Phase 1:  Call OASModelReader
        OpenAPIImpl model = modelFromReader();

        // Phase 2:  Fetch any static OpenAPI file packaged in the app
        model = MergeUtil.merge(model, modelFromStaticFile());

        // Phase 3:  Process annotations
        model = MergeUtil.merge(model, modelFromAnnotations());

        // Phase 4:  Filter model via OASFilter
        model = filterModel(model);

        // Phase 5:  Default empty document if model == null
        if (model == null) {
            model = new OpenAPIImpl();
            model.setOpenapi(OpenApiConstants.OPEN_API_VERSION);
        }

        // Phase 6:  Provide missing required elements
        if (model.getPaths() == null) {
            model.setPaths(new PathsImpl());
        }
        if (model.getInfo() == null) {
            model.setInfo(new InfoImpl());
        }
        if (model.getInfo().getTitle() == null) {
            model.getInfo().setTitle((archive.getName() == null ? "Generated" : archive.getName()) + " API");
        }
        if (model.getInfo().getVersion() == null) {
            model.getInfo().setVersion("1.0");
        }

        // Phase 7:  Use Config values to add Servers (global, pathItem, operation)
        ServersUtil.configureServers(config, model);

        OpenApiDocumentHolder.document = model;
    }

    /**
     * Instantiate the configured {@link OASModelReader} and invoke it.  If no reader is configured,
     * then return null.  If a class is configured but there is an error either instantiating or
     * invokig it, a {@link RuntimeException} is thrown.
     */
    private OpenAPIImpl modelFromReader() {
        String readerClassName = config.modelReader();
        if (readerClassName == null) {
            return null;
        }
        try {
            Class c = Class.forName(readerClassName);
            OASModelReader reader = (OASModelReader) c.newInstance();
            return (OpenAPIImpl) reader.buildModel();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find a static file located in the deployment and, if it exists, parse it and
     * return the resulting model.  If no static file is found, returns null.  If an
     * error is encountered while parsing the file then a runtime exception is
     * thrown.
     */
    private OpenAPIImpl modelFromStaticFile() {
        Format format = Format.YAML;

        // Check for the file in both META-INF and WEB-INF/classes/META-INF
        Node node = archive.get("/META-INF/openapi.yaml");
        if (node == null) {
            node = archive.get("/WEB-INF/classes/META-INF/openapi.yml");
        }
        if (node == null) {
            node = archive.get("/META-INF/openapi.yml");
        }
        if (node == null) {
            node = archive.get("/WEB-INF/classes/META-INF/openapi.yml");
        }
        if (node == null) {
            node = archive.get("/META-INF/openapi.json");
            format = Format.JSON;
        }
        if (node == null) {
            node = archive.get("/WEB-INF/classes/META-INF/openapi.json");
            format = Format.JSON;
        }

        if (node == null) {
            return null;
        }

        try (InputStream stream = node.getAsset().openStream()) {
            return OpenApiParser.parse(stream, format);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create an {@link OpenAPI} model by scanning the deployment for relevant JAX-RS and
     * OpenAPI annotations.  If scanning is disabled, this method returns null.  If scanning
     * is enabled but no relevant annotations are found, an empty OpenAPI model is returned.
     */
    private OpenAPIImpl modelFromAnnotations() {
        if (this.config.scanDisable()) {
            return null;
        }

        OpenApiAnnotationScanner scanner = new OpenApiAnnotationScanner(config, archive);
        return scanner.scan();
    }

    /**
     * Filter the final model by instantiating a {@link OASFilter} configured by the app.  If no
     * filter has been configured, this will simply return the model unchanged.
     * @param model
     */
    private OpenAPIImpl filterModel(OpenAPIImpl model) {
        if (model == null) {
            return null;
        }

        String filterClassName = config.filter();
        if (filterClassName == null) {
            return model;
        }

        System.out.println("Filtering OpenAPI model.");

        try {
            Class c = Class.forName(filterClassName);
            OASFilter filter = (OASFilter) c.newInstance();
            return (OpenAPIImpl) FilterUtil.applyFilter(filter, model);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}