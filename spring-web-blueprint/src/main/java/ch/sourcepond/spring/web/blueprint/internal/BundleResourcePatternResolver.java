/*Copyright (C) 2017 Roland Hauser, <sourcepond@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/
package ch.sourcepond.spring.web.blueprint.internal;

import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static ch.sourcepond.spring.web.blueprint.internal.ResourceFinderClassLoader.getBundleClassLoader;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 *
 */
public class BundleResourcePatternResolver implements ResourcePatternResolver {
    private static final Logger LOG = getLogger(BundleResourcePatternResolver.class);

    /**
     * Constant for <em>classpath:</em> URL prefix
     */
    static final String CLASSPATH_URL_PREFIX = "classpath:";

    /**
     * Constant for <em>classpath*:</em> URL prefix; will be handled exactly the
     * same way as {@link #CLASSPATH_URL_PREFIX}.
     */
    static final String CLASSPATHS_URL_PREFIX = "classpath*:";

    /**
     * Constant for <em>osgibundle:</em> URL prefix.
     */
    static final String OSGI_BUNDLE_URL_PREFIX = "osgibundle:";

    /**
     * Constant for unspecified URL prefix; will handled exactly the same way as
     * {@link #OSGI_BUNDLE_URL_PREFIX}.
     */
    static final String PREFIX_UNSPECIFIED = "";

    static final char PROTOCOL_SEPARATOR = ':';
    private final Map<String, InternalResolver> accessors = new HashMap<>();
    private final ResourcePatternResolver patternResolver;
    private volatile Bundle bundle;

    // Constructor for testing
    public BundleResourcePatternResolver(final ResourcePatternResolver patternResolver) {
        this(patternResolver, new ClasspathResolver(new AntPathMatcher()),
                new BundleSpaceResolver(new AntPathMatcher()));
    }

    // Constructor for testing
    BundleResourcePatternResolver(final ResourcePatternResolver patternResolver,
                                  final InternalResolver classpathResolver,
                                  final InternalResolver bundlespaceResolver) {
        this.patternResolver = patternResolver;
        accessors.put(CLASSPATH_URL_PREFIX, classpathResolver);
        accessors.put(CLASSPATHS_URL_PREFIX, classpathResolver);
        accessors.put(OSGI_BUNDLE_URL_PREFIX, bundlespaceResolver);
        accessors.put(PREFIX_UNSPECIFIED, bundlespaceResolver);
    }

    public void setBundle(final Bundle bundle) {
        this.bundle = requireNonNull(bundle, "Bundle cannot be null");
    }

    /**
     * @return
     */
    private InternalResolver getResolverOrNull(final String pProtocolPrefix) {
        return accessors.get(pProtocolPrefix);
    }

    /**
     * @param pLocationPattern
     * @return
     */
    private String extractProtocol(final String pLocationPattern) {
        // Index where the protocol name is separated from the path
        final int protocolSeparatorIdx = pLocationPattern
                .indexOf(PROTOCOL_SEPARATOR) + 1;
        return pLocationPattern.substring(0, protocolSeparatorIdx);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.core.io.ResourceLoader#getResource(java.lang.String)
     */
    @Override
    public final Resource getResource(final String path) {
        Resource foundResource = null;
        if (bundle == null) {
            LOG.warn("No resource determined for {} because no bundle is set", path);
        } else {
            final String protocol = extractProtocol(path);
            final String normalizedLocationPattern = path.substring(protocol
                    .length());
            final InternalResolver resolver = getResolverOrNull(protocol);

            if (resolver == null) {
                foundResource = patternResolver.getResource(path);
            } else {
                final URL resourceUrl = resolver.resolveResource(bundle, normalizedLocationPattern);

                if (resourceUrl != null) {
                    foundResource = new UrlResource(resourceUrl);
                }
            }
            LOG.debug("Resource for protocol {} and normalized location pattern {} : {}",
                    protocol, normalizedLocationPattern, foundResource);
        }

        return foundResource;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.core.io.ResourceLoader#getClassLoader()
     */
    @Override
    public final ClassLoader getClassLoader() {
        if (bundle == null) {
            LOG.warn("Using system classloader because no bundle is set");
            return ClassLoader.getSystemClassLoader();
        }
        return getBundleClassLoader(bundle);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.core.io.support.ResourcePatternResolver#getResources
     * (java.lang.String)
     */
    @Override
    public final Resource[] getResources(final String pattern)
            throws IOException {
        final Resource[] foundResources;
        if (bundle == null) {
            foundResources = new Resource[0];
            LOG.warn("No resources determined for {} because no bundle is set", pattern);
        } else {
            final String protocol = extractProtocol(pattern);
            final String normalizedPathPattern = pattern
                    .substring(protocol.length());

            final InternalResolver resolver = getResolverOrNull(protocol);

            // No resolver found, call delegate pattern resolver
            if (resolver == null) {
                foundResources = patternResolver.getResources(pattern);
            } else {
                final Collection<URL> foundResourceUrls = resolver
                        .resolveResources(bundle, normalizedPathPattern);

                if (foundResourceUrls.isEmpty()) {
                    // No result, let delegate pattern resolver try to find matching resources
                    foundResources = patternResolver.getResources(pattern);
                } else {
                    foundResources = new Resource[foundResourceUrls.size()];
                    int i = 0;
                    for (final URL foundResourceUrl : foundResourceUrls) {
                        foundResources[i++] = new UrlResource(foundResourceUrl);
                    }
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Resources for protocol {} and normalized location pattern {} : {}",
                        protocol, normalizedPathPattern, asList(foundResources));
            }
        }
        return foundResources;
    }
}
