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
package ch.sourcepond.spring.web.blueprint;

import ch.sourcepond.spring.web.blueprint.internal.BlueprintApplicationContext;
import ch.sourcepond.spring.web.blueprint.internal.ResourceFinderClassLoader;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.springframework.web.context.ConfigurableWebApplicationContext;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletException;
import java.util.Set;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 *
 */
public class BlueprintServletContainerInitializer implements ServletContainerInitializer, ServletContextAttributeListener {
    private static final Logger LOG = getLogger(BlueprintServletContainerInitializer.class);

    /**
     * Attribute name to get the bundle context of the corresponding WAB through
     * {@link ServletContext#getAttribute(String)}. See OSGi Enterprise
     * specification R5 (page 457, section 128.6.1).
     */
    static final String OSGI_BUNDLECONTEXT = "osgi-bundlecontext";

    /**
     * Init parameter name to declare a custom Blueprint/Spring context class.
     */
    static final String BLUEPRINT_CONTEXT_CLASS = "blueprintContextClass";

    /**
     * Attribute used by Spring-Web to find an existing application context on the
     * servlet context.
     */
    static final String BLUEPRINT_CONTEXT = "blueprintContext";

    /**
     * Attribute used by to specify the attribute-name to be used to find
     * an existing application context.
     */
    static final String CONTEXT_ATTRIBUTE = "contextAttribute";
    static final String CONFIG_LOCATION_PARAM = "contextConfigLocation";

    static Bundle getBundle(final ServletContext context) {
        return ((BundleContext) requireNonNull(context.getAttribute(OSGI_BUNDLECONTEXT),
                () -> OSGI_BUNDLECONTEXT + " is not set as attribute on ServletContext")).getBundle();
    }

    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) throws ServletException {
        ctx.addListener(this);
    }

    private ConfigurableWebApplicationContext createContext(final Bundle bundle, final String classNameOrNull)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ConfigurableWebApplicationContext ctx;
        if (classNameOrNull == null) {
            ctx = new XmlWebApplicationBundleContext();
        } else {
            final Class<?> cl = bundle.loadClass(classNameOrNull);
            if (!ConfigurableWebApplicationContext.class.isAssignableFrom(cl)) {
                throw new ClassCastException(format("Class %s specified by attribute %s must be assignable from %s",
                        cl.getName(), BLUEPRINT_CONTEXT_CLASS, ConfigurableWebApplicationContext.class.getName()));
            }
            ctx = (ConfigurableWebApplicationContext) cl.newInstance();
        }
        return ctx;
    }

    @Override
    public void attributeAdded(final ServletContextAttributeEvent event) {
        if (OSGI_BUNDLECONTEXT.equals(event.getName())) {
            final ServletContext sctx = event.getServletContext();
            final BundleContext bundleContext = (BundleContext) event.getValue();
            final BlueprintApplicationContext blueprintApplicationContext = new BlueprintApplicationContext(sctx, bundleContext);
            try {
                bundleContext.addServiceListener(blueprintApplicationContext, blueprintApplicationContext.getFilter());
            } catch (final InvalidSyntaxException e) {
                // This should never happen
                LOG.error(e.getMessage(), e);
            }

            final ClassLoader ldr = currentThread().getContextClassLoader();
            currentThread().setContextClassLoader(new ResourceFinderClassLoader(bundleContext));
            try {
                final ConfigurableWebApplicationContext webContext = createContext(bundleContext.getBundle(), sctx.getInitParameter(BLUEPRINT_CONTEXT_CLASS));
                webContext.setParent(blueprintApplicationContext);
                webContext.setServletContext(sctx);
                String configLocationParam = sctx.getInitParameter(CONFIG_LOCATION_PARAM);
                if (configLocationParam != null) {
                    webContext.setConfigLocation(configLocationParam);
                }

                webContext.refresh();
                sctx.setAttribute(CONTEXT_ATTRIBUTE, BLUEPRINT_CONTEXT);
                sctx.setAttribute(BLUEPRINT_CONTEXT, webContext);
            } catch (final Exception e) {
                sctx.setAttribute(BLUEPRINT_CONTEXT, e);
            } finally {
                currentThread().setContextClassLoader(ldr);
            }
        }
    }

    @Override
    public void attributeRemoved(final ServletContextAttributeEvent event) {
        // noop
    }

    @Override
    public void attributeReplaced(final ServletContextAttributeEvent event) {
        // noop
    }
}
