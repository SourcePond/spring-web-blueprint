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
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.currentThread;
import static org.slf4j.LoggerFactory.getLogger;

/**
 *
 */
public class BundleResourcePatternResolver implements ResourcePatternResolver {
    private static final Logger LOG = getLogger(BundleResourcePatternResolver.class);
    private static final Object MONITOR = new Object();

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
    private static volatile BundleContext bundleContext;
    private final Map<String, InternalResolver> accessors = new HashMap<>();
    private final Bundle bundle;
    private final ResourcePatternResolver patternResolver;

    // Constructor for testing
    BundleResourcePatternResolver(final Bundle bundle,
                                  final ResourcePatternResolver patternResolver) {
        this(bundle, patternResolver, new ClasspathResolver(new AntPathMatcher()),
                new BundleSpaceResolver(new AntPathMatcher()));
    }

    // Constructor for testing
    BundleResourcePatternResolver(final Bundle bundle,
                                  final ResourcePatternResolver patternResolver,
                                  final InternalResolver classpathResolver,
                                  final InternalResolver bundlespaceResolver) {
        this.bundle = bundle;
        this.patternResolver = patternResolver;
        accessors.put(CLASSPATH_URL_PREFIX, classpathResolver);
        accessors.put(CLASSPATHS_URL_PREFIX, classpathResolver);
        accessors.put(OSGI_BUNDLE_URL_PREFIX, bundlespaceResolver);
        accessors.put(PREFIX_UNSPECIFIED, bundlespaceResolver);
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
        final String protocol = extractProtocol(path);
        final String normalizedLocationPattern = path.substring(protocol
                .length());

        LOG.debug("Find resource for protocol {} and normalized location patter {}", protocol, normalizedLocationPattern);

        final InternalResolver resolver = getResolverOrNull(protocol);

        final Resource foundResource;
        if (resolver == null) {
            foundResource = patternResolver.getResource(path);
        } else {
            final URL resourceUrl = resolver.resolveResource(bundle, normalizedLocationPattern);

            if (resourceUrl == null) {
                foundResource = null;
            } else {
                foundResource = new UrlResource(resourceUrl);
            }
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
        return bundle.adapt(BundleWiring.class).getClassLoader();
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
        final String protocol = extractProtocol(pattern);
        final String normalizedPathPattern = pattern
                .substring(protocol.length());

        LOG.debug("Find resources for protocol {} and normalized location patter {}", protocol, normalizedPathPattern);

        final InternalResolver resolver = getResolverOrNull(protocol);
        final Resource[] foundResources;

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

        return foundResources;
    }

    public static void setBundleContext(final BundleContext b) {
        synchronized (MONITOR) {
            bundleContext = b;
            MONITOR.notifyAll();
        }
    }

    private static BundleContext getBundleContext() {
        if (bundleContext == null) {
            synchronized (MONITOR) {
                if (bundleContext == null) {
                    try {
                        while (bundleContext == null) {
                            MONITOR.wait();
                        }
                    } catch (final InterruptedException e) {
                        currentThread().interrupt();
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                }
            }
        }
        return bundleContext;
    }

    public static ResourcePatternResolver create(final ResourcePatternResolver delegate) {
        return new BundleResourcePatternResolver(getBundleContext().getBundle(), delegate);
    }
}
