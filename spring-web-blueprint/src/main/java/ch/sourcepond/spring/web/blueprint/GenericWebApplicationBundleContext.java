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

import ch.sourcepond.spring.web.blueprint.internal.BundleResourcePatternResolver;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.web.context.support.GenericWebApplicationContext;

import javax.servlet.ServletContext;

import static ch.sourcepond.spring.web.blueprint.BlueprintServletContainerInitializer.getBundle;

/**
 *
 */
public class GenericWebApplicationBundleContext extends GenericWebApplicationContext {

    private BundleResourcePatternResolver resolver;

    public GenericWebApplicationBundleContext() {
    }

    public GenericWebApplicationBundleContext(final ServletContext servletContext) {
        super(servletContext);
    }

    public GenericWebApplicationBundleContext(final DefaultListableBeanFactory beanFactory) {
        super(beanFactory);
    }

    public GenericWebApplicationBundleContext(final DefaultListableBeanFactory beanFactory, final ServletContext servletContext) {
        super(beanFactory, servletContext);
    }

    /**
     *
     */
    @Override
    protected ResourcePatternResolver getResourcePatternResolver() {
        if (resolver == null) {
            resolver = new BundleResourcePatternResolver(super.getResourcePatternResolver());
        }
        return resolver;
    }

    /**
     *
     */
    protected MessageSource getInternalParentMessageSource() {
        try {
            return getBeanFactory().getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
        } catch (final NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    @Override
    public void setServletContext(final ServletContext servletContext) {
        resolver.setBundle(getBundle(servletContext));
        super.setServletContext(servletContext);
    }
}
