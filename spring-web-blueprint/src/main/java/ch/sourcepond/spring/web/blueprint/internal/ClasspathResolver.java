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
import org.osgi.framework.wiring.BundleWiring;
import org.springframework.util.PathMatcher;

import java.net.URL;
import java.util.Collection;

import static org.osgi.framework.wiring.BundleWiring.LISTRESOURCES_RECURSE;

/**
 *
 */
class ClasspathResolver extends InternalResolver<String> {

    ClasspathResolver(final PathMatcher matcher) {
        super(matcher);
    }

    private BundleWiring bundleWiring(final Bundle bundle) {
        return bundle.adapt(BundleWiring.class);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * ch.bechtle.osgi.springmvc.blueprint.adapter.resolver.BaseResourceAccessor
     * #listResourcePaths(java.lang.String)
     */
    @Override
    protected Collection<String> listAllResources(final Bundle bundle) {
        return bundleWiring(bundle).listResources("/", "*", LISTRESOURCES_RECURSE);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * ch.bechtle.osgi.springmvc.blueprint.adapter.resolver.BaseResourceAccessor
     * #resolveResource(java.lang.String)
     */
    @Override
    protected URL doResolveResource(final Bundle bundle, final String path) {
        return bundleWiring(bundle).getClassLoader().getResource(path);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * ch.bechtle.osgi.springmvc.blueprint.adapter.resolver.BaseResourceAccessor
     * #toPath(java.lang.Object)
     */
    @Override
    protected String toPath(final String path, final String pattern) {
        return path;
    }
}
