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
import org.springframework.util.PathMatcher;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;

/**
 *
 */
abstract class InternalResolver<T> {
    private final PathMatcher matcher;

    InternalResolver(final PathMatcher matcher) {
        this.matcher = matcher;
    }

    abstract Collection<T> listAllResources(Bundle bundle);

    abstract URL doResolveResource(final Bundle bundle, String path);

    final URL resolveResource(final Bundle bundle, final String path) {
        final URL resolvedResource = doResolveResource(bundle, path);

        // This should never happen because the resource path has been received
        // through a specified mechanism which must insure that a resource
        // exists.
        if (resolvedResource == null) {
            throw new IllegalStateException(path + " could not be resolved to an URL object!");
        }

        return resolvedResource;
    }

    /**
     *
     */
    abstract String toPath(T path, String pattern);

    final Collection<URL> resolveResources(final Bundle bundle, final String pattern)
            throws IOException {
        // Create the result set and list recursively all resources contained by
        // the directory specified.
        final Collection<URL> foundResources = new LinkedList<>();
        final Collection<T> resourcePaths = listAllResources(bundle);

        if (resourcePaths != null && !resourcePaths.isEmpty()) {
            for (final T resourcePath : resourcePaths) {
                // Check whether we need to resolve and include the current path
                // into the search result. Ignore directories!
                final String resourcePathAsString = toPath(resourcePath, pattern);
                if (matcher.match(pattern, resourcePathAsString) && !resourcePathAsString.endsWith("/")) {
                    foundResources.add(resolveResource(bundle, resourcePathAsString));
                }
            }
        }

        return foundResources;
    }
}
