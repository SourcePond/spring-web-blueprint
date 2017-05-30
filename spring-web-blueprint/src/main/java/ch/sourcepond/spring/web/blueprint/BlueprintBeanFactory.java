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

import org.osgi.framework.*;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.NoSuchComponentException;
import org.osgi.service.blueprint.reflect.*;
import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.core.ResolvableType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static org.osgi.framework.ServiceEvent.*;
import static org.osgi.service.blueprint.reflect.BeanMetadata.SCOPE_PROTOTYPE;
import static org.osgi.service.blueprint.reflect.BeanMetadata.SCOPE_SINGLETON;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * {@link BeanFactory} implementation which adapts to {@link BlueprintContainer}.
 * An newly created instance will wait until the {@link BlueprintContainer} associated with the
 * bundle of {@link BundleContext} specified has been started and registered as service before
 * it is in operational state.
 */
class BlueprintBeanFactory implements BeanFactory, ServiceListener {
    private static final Logger LOG = getLogger(BlueprintBeanFactory.class);
    static final String BLUEPRINT_CONTAINER_CONTAINER_HAS_BEEN_SHUTDOWN = "BlueprintContainer container has been shutdown";

    /**
     * Aliases are currently not supported by the Blueprint specification.
     */
    static final String[] EMPTY_ALIASES = new String[0];

    /**
     * Service property name of the corresponding WABs symbolic-name. This
     * property is necessary in order to retrieve the WABs
     * {@link BlueprintContainer} through the OSGi service registry. See OSGi
     * Enterprise specification R5 (page 230, section 121.3.10).
     */
    String OSGI_BLUEPRINT_CONTAINER_SYMBOLIC_NAME = "osgi.blueprint.container.symbolicname";

    /**
     * Service property name of the corresponding WABs version. This property is
     * necessary in order to retrieve the WABs {@link BlueprintContainer}
     * through the OSGi service registry. See OSGi Enterprise specification R5
     * (page 230, section 121.3.10).
     */
    String OSGI_BLUEPRINT_CONTAINER_VERSION = "osgi.blueprint.container.version";

    private final BundleContext bundleContext;
    private final String filter;
    private boolean destroyed;
    private volatile BlueprintContainer container;

    /**
     * Creates a new instance of this class. The instance will wait until
     * the {@link BlueprintContainer} associated with the bundle of Bundle-Context
     * specified has been started and registered as service before it is in
     * operational state.
     *
     * @param bundleContext
     */
    BlueprintBeanFactory(final BundleContext bundleContext) {
        this.bundleContext = requireNonNull(bundleContext, "Bundle-Context is null");
        final Bundle bundle = bundleContext.getBundle();
        filter = "(&(" + Constants.OBJECTCLASS + "="
                + BlueprintContainer.class.getName() + ")("
                + OSGI_BLUEPRINT_CONTAINER_SYMBOLIC_NAME + "="
                + bundle.getSymbolicName() + ")("
                + OSGI_BLUEPRINT_CONTAINER_VERSION + "="
                + bundle.getVersion() + "))";
    }

    String getFilter() {
        return filter;
    }

    private BlueprintContainer findExistingBlueprintContainer() {
        BlueprintContainer container = null;
        try {
            final ServiceReference<?>[] refs = bundleContext.getServiceReferences((String) null, filter);
            if (refs != null) {
                container = (BlueprintContainer) bundleContext.getService(refs[0]);
            }
        } catch (final InvalidSyntaxException e) {
            // This should never happen
            throw new IllegalStateException(e);
        }
        return container;
    }

    private BlueprintContainer getContainer() {
        if (container == null) {
            synchronized (this) {
                if (container == null) {
                    container = findExistingBlueprintContainer();
                    try {
                        while (container == null && !destroyed) {
                            wait();
                        }
                    } catch (final InterruptedException e) {
                        currentThread().interrupt();
                        throw new BeanDefinitionStoreException("Wait for BlueprintContainer interrupted", e);
                    }

                    if (container == null) {
                        throw new BeanDefinitionStoreException(BLUEPRINT_CONTAINER_CONTAINER_HAS_BEEN_SHUTDOWN);
                    }
                }
            }
        }
        return container;
    }

    private synchronized void blueprintContainerRegistered(final ServiceReference<?> pReference) {
        container = (BlueprintContainer) bundleContext.getService(pReference);
        notifyAll();
    }

    private synchronized void blueprintContainerUnregistered() {
        container = null;
        destroyed = true;
        notifyAll();
    }

    @Override
    public void serviceChanged(final ServiceEvent serviceEvent) {
        switch (serviceEvent.getType()) {
            case UNREGISTERING:
            case MODIFIED_ENDMATCH: {
                blueprintContainerUnregistered();
                break;
            }
            case REGISTERED: {
                blueprintContainerRegistered(serviceEvent.getServiceReference());
                break;
            }
            default: {
                // noop
            }
        }
    }

    @Override
    public Object getBean(final String s) throws BeansException {
        try {
            return getContainer().getComponentInstance(s);
        } catch (final NoSuchComponentException e) {
            final NoSuchBeanDefinitionException nsbe = new NoSuchBeanDefinitionException(
                    s);
            nsbe.initCause(e);
            throw nsbe;
        }
    }

    @Override
    public <T> T getBean(final String s, final Class<T> aClass) throws BeansException {
        // Get instance; never null
        final Object instance = getBean(s);

        if (aClass != null
                && !aClass.isAssignableFrom(instance.getClass())) {
            throw new BeanNotOfRequiredTypeException(s, aClass,
                    instance.getClass());
        }
        return (T) instance;
    }

    @Override
    public <T> T getBean(final Class<T> aClass) throws BeansException {
        requireNonNull(aClass, "Class is null");
        final BlueprintContainer container = getContainer();
        final Set<String> ids = container.getComponentIds();
        final Map<String, Object> beans = new HashMap<>(ids.size());
        ids.forEach(id -> beans.put(id, getContainer().getComponentInstance(id)));

        for (final Iterator<Object> it = beans.values().iterator(); it.hasNext(); ) {
            if (!aClass.isAssignableFrom(it.next().getClass())) {
                it.remove();
            }
        }

        if (beans.isEmpty()) {
            throw new NoSuchBeanDefinitionException(aClass);
        }

        if (beans.size() > 1) {
            throw new NoUniqueBeanDefinitionException(aClass, beans.keySet());
        }

        return (T) beans.values().iterator().next();
    }

    @Override
    public Object getBean(final String s, final Object... objects) throws BeansException {
        // objects argument ignored because object creation lies in the
        // responsibility of the BlueprintServletContainerInitializer-container.
        return getBean(s);
    }

    @Override
    public <T> T getBean(final Class<T> aClass, final Object... objects) throws BeansException {
        // objects argument ignored because object creation lies in the
        // responsibility of the BlueprintServletContainerInitializer-container.
        return getBean(aClass);
    }

    @Override
    public boolean containsBean(final String s) {
        try {
            getContainer().getComponentInstance(s);
            return true;
        } catch (final NoSuchComponentException e) {
            // noop
            LOG.trace(e.getMessage(), e);
        }
        return false;
    }

    private boolean hasScope(final String pScope, final String s) throws NoSuchBeanDefinitionException {
        boolean singleton;
        try {
            final ComponentMetadata m = getContainer().getComponentMetadata(s);
            singleton = (m instanceof BeanMetadata) && pScope.equals(((BeanMetadata) m).getScope());
        } catch (final NoSuchComponentException e) {
            throw new NoSuchBeanDefinitionException(s);
        }
        return singleton;
    }


    @Override
    public boolean isSingleton(final String s) throws NoSuchBeanDefinitionException {
        return hasScope(SCOPE_SINGLETON, s);
    }

    @Override
    public boolean isPrototype(final String s) throws NoSuchBeanDefinitionException {
        return hasScope(SCOPE_PROTOTYPE, s);
    }

    @Override
    public boolean isTypeMatch(final String s, final ResolvableType resolvableType) throws NoSuchBeanDefinitionException {
        return resolvableType.isAssignableFrom(getBean(s).getClass());
    }

    @Override
    public boolean isTypeMatch(final String s, final Class<?> aClass) throws NoSuchBeanDefinitionException {
        return aClass.isAssignableFrom(getBean(s).getClass());
    }

    @Override
    public Class<?> getType(final String s) throws NoSuchBeanDefinitionException {
        try {
            return findType(findMetadata(s));
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            final NoSuchBeanDefinitionException ex = new NoSuchBeanDefinitionException(s);
            ex.initCause(e);
            throw ex;
        }
    }

    private ComponentMetadata findMetadata(final String id) {
        try {
            final ComponentMetadata metadata = getContainer().getComponentMetadata(id);

            if (!(metadata instanceof BeanMetadata)
                    && !(metadata instanceof ServiceReferenceMetadata)) {
                throw new NoSuchComponentException("Actual metadata-class "
                        + metadata.getClass() + " is not assignable from "
                        + BeanMetadata.class.getName() + " or "
                        + ServiceReferenceMetadata.class, id);
            }

            return metadata;
        } catch (final NoSuchComponentException e) {
            final NoSuchBeanDefinitionException nsbe = new NoSuchBeanDefinitionException(
                    id);
            nsbe.initCause(e);
            throw nsbe;
        }
    }

    private String getComponentId(final Target target) {
        final String componentId;
        if (target instanceof ComponentMetadata) {
            componentId = ((ComponentMetadata) target).getId();
        } else { // It can only be a RefMetadata
            componentId = ((RefMetadata) target).getComponentId();
        }
        return componentId;
    }

    private Class<?> findType(final ComponentMetadata metadata) throws ClassNotFoundException, NoSuchMethodException {
        assert metadata != null : "metadata cannot be null";

        Class<?> clazz = null;

        if (metadata instanceof BeanMetadata) {
            final BeanMetadata beanMetadata = (BeanMetadata) metadata;
            final String factoryMethodNameOrNull = beanMetadata
                    .getFactoryMethod();
            final String className = beanMetadata.getClassName();

            // Class name can be null when a factory component was used to construct the bean.
            if (className == null) {

                // We need a factory-method name at this point; throw an exception if not so.
                if (factoryMethodNameOrNull == null) {
                    throw new IllegalStateException(
                            "Invalid metadata: class-name nor factory-method is provided! "
                                    + metadata);
                }

                // Get the metadata of the factory component and validate it.
                final Target factoryComponent = beanMetadata
                        .getFactoryComponent();
                if (factoryComponent == null) {
                    throw new IllegalStateException(
                            "No class-name nor a factory component has been specified!");
                }

                final ComponentMetadata factoryComponentMetadata = findMetadata(getComponentId(factoryComponent));

                // The factory-component itself could also be constructed through
                // another factory. Because this, we need to call this method
                // recursively
                final Class<?> factoryComponentClass = findType(factoryComponentMetadata);

                // Almost at the end; what we finally need is the return type of the factory-method
                clazz = factoryComponentClass.getMethod(factoryMethodNameOrNull).getReturnType();
            } else if (factoryMethodNameOrNull != null) {
                // A static factory-method should be defined; get its return type.
                clazz = determineFactoryReturnType(beanMetadata);
            } else {
                // Simply load the class with the class-name specified
                clazz = loadClass(className);
            }
        } else if (metadata instanceof ServiceReferenceMetadata) {
            final ServiceReferenceMetadata referenceMetadata = (ServiceReferenceMetadata) metadata;

            // getId() can be null when the reference-element is nested; skip it
            // because we must not consider nested elements!
            if (referenceMetadata.getInterface() != null) {
                clazz = loadClass(referenceMetadata.getInterface());
            }
        } else {
            throw new CannotLoadBeanClassException(bundleContext.getBundle().toString(),
                    metadata.getId(), metadata.toString(),
                    new ClassNotFoundException());
        }

        return clazz;
    }

    private Class<?> determineFactoryReturnType(final BeanMetadata metadata) throws
            ClassNotFoundException, NoSuchMethodException {
        final Class<?> factoryClass = loadClass(metadata.getClassName());
        return factoryClass.getMethod(metadata.getFactoryMethod()).getReturnType();
    }

    private Class<?> loadClass(final String className) throws ClassNotFoundException {
        return bundleContext.getBundle().loadClass(className);
    }

    @Override
    public String[] getAliases(final String s) {
        return EMPTY_ALIASES;
    }
}
