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

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.Set;

import static java.lang.String.format;

/**
 *
 */
public class BlueprintServletContainerInitializer implements ServletContainerInitializer {

    /**
     * Attribute name to get the bundle context of the corresponding WAB through
     * {@link ServletContext#getAttribute(String)}. See OSGi Enterprise
     * specification R5 (page 457, section 128.6.1).
     */
    public static final String OSGI_BUNDLECONTEXT = "osgi-bundlecontext";

    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) throws ServletException {
        final BundleContext bundleContext = (BundleContext) ctx.getAttribute(OSGI_BUNDLECONTEXT);
        if (bundleContext == null) {
            throw new ServletException(format("Bundle-Context missing because attribute %s is not set", OSGI_BUNDLECONTEXT));
        }

        final BlueprintBeanFactory blueprintBeanFactory = new BlueprintBeanFactory(bundleContext);
        try {
            bundleContext.addServiceListener(blueprintBeanFactory, blueprintBeanFactory.getFilter());
        } catch (final InvalidSyntaxException e) {
            // This should never happen
            throw new ServletException(e.getMessage(), e);
        }

        final DefaultListableBeanFactory defaultListableBeanFactory = new DefaultListableBeanFactory(blueprintBeanFactory);
        GenericWebApplicationContext context = new GenericWebApplicationContext(defaultListableBeanFactory);
        ctx.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
    }
}
