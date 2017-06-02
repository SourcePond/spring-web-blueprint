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

import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;

import static java.util.Collections.emptyList;
import static java.util.Collections.list;

/**
 *
 */
class BundleSpaceResolver extends InternalResolver<URL> {

    /**
     *
     */
    BundleSpaceResolver(final PathMatcher matcher) {
        super(matcher);
    }

    /*
     * (non-Javadoc)
     *
     * @see ch.bechtle.osgi.springmvc.blueprint.adapter.BaseResourceAccessor#
     * listAllResources(org.osgi.framework.Bundle, java.lang.String)
     */
    @Override
    protected Collection<URL> listAllResources(final Bundle bundle) {
        final Enumeration<URL> urls = bundle.findEntries("/", "*", true);
        final Collection<URL> resources;

        if (urls == null) {
            resources = emptyList();
        } else {
            resources = list(urls);
        }

        return resources;
    }

    /*
     * (non-Javadoc)
     *
     * @see ch.bechtle.osgi.springmvc.blueprint.adapter.BaseResourceAccessor#
     * doResolveResource(org.osgi.framework.Bundle, java.lang.String)
     */
    @Override
    protected URL doResolveResource(final Bundle bundle, final String path) {
        return bundle.getEntry(path);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * ch.bechtle.osgi.springmvc.blueprint.adapter.resolver.BaseResourceAccessor
     * #toPath(java.lang.Object)
     */
    @Override
    protected String toPath(final URL url, final String pattern) {
        String path = url.getPath();

        // Remove leading slash
        if (path.length() > 1 && path.charAt(0) == '/') {
            path = path.substring(1);
        }
        return path;
    }
}
