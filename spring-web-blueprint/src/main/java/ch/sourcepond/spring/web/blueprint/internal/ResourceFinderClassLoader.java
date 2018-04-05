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

import java.io.IOException;
import java.net.URL;
import java.util.*;

import static java.util.Collections.enumeration;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ResourceFinderClassLoader extends ClassLoader {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ResourceFinderClassLoader.class);
    private final BundleContext context;

    public ResourceFinderClassLoader(final BundleContext context) {
        super(getBundleClassLoader(context.getBundle()));
        this.context = context;
    }

    static ClassLoader getBundleClassLoader(final Bundle bundle) {
        return bundle.adapt(BundleWiring.class).getClassLoader();
    }

    @Override
    protected Enumeration<URL> findResources(final String name) throws IOException {
        final List<URL> resources = new LinkedList<>();
        for (final Bundle b : context.getBundles()) {
            final ClassLoader cl = getBundleClassLoader(b);
            if (cl != null) {
                final Enumeration<URL> e = cl.getResources(name);
                while (e.hasMoreElements()) {
                    resources.add(e.nextElement());
                }
            }
        }
        return enumeration(resources);
    }

  @Override
  protected URL findResource(String name){
    for (final Bundle b : context.getBundles()) {
        final ClassLoader cl = getBundleClassLoader(b);
        if (cl != null) {
            URL url = cl.getResource(name);
            if(url != null){
                return url;
            }
        }
    }
    return null;
  }
    
    
}
