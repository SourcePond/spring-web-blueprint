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

import ch.sourcepond.spring.web.blueprint.BlueprintBeanFactory;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.*;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.springframework.beans.factory.BeanDefinitionStoreException;

import java.util.concurrent.atomic.AtomicReference;

import static ch.sourcepond.spring.web.blueprint.BlueprintBeanFactory.BLUEPRINT_CONTAINER_CONTAINER_HAS_BEEN_SHUTDOWN;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.osgi.framework.ServiceEvent.REGISTERED;
import static org.osgi.framework.ServiceEvent.UNREGISTERING;
import static org.osgi.framework.Version.valueOf;

/**
 *
 */
public class BlueprintBeanFactoryTest {
    private static final String ANY_SYMBOLIC_NAME = "anySymbolicName";
    private static final String ANY_VERSION = "1.2.3";
    private static final String ANY_NAME = "anyName";
    private static final Object ANY_BEAN = new Object();
    private static final String FILTER = "(&(objectClass=org.osgi.service.blueprint.container.BlueprintContainer)(osgi.blueprint.container.symbolicname=anySymbolicName)(osgi.blueprint.container.version=1.2.3))";
    private final Bundle bundle = mock(Bundle.class);
    private final BundleContext bundleContext = mock(BundleContext.class);
    private final ServiceReference<BlueprintContainer> containerRef = mock(ServiceReference.class);
    private final BlueprintContainer container = mock(BlueprintContainer.class);
    private BlueprintBeanFactory factory;

    @Before
    public void setup() {
        when(bundle.getSymbolicName()).thenReturn(ANY_SYMBOLIC_NAME);
        when(bundle.getVersion()).thenReturn(valueOf(ANY_VERSION));
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundleContext.getService(containerRef)).thenReturn(container);
        when(container.getComponentInstance(ANY_NAME)).thenReturn(ANY_BEAN);
        factory = new BlueprintBeanFactory(bundleContext);
    }

    @Test
    public void getFilter() {
        assertEquals(FILTER, factory.getFilter());
    }

    @Test(timeout = 500)
    public void findExistingBlueprintContainer() throws Exception {
        final ServiceReference<?>[] containerRefs = new ServiceReference<?>[]{containerRef};
        when(bundleContext.getServiceReferences((String) null, FILTER)).thenReturn(containerRefs);
        when(bundleContext.getService(containerRef)).thenReturn(container);
        assertSame(ANY_BEAN, factory.getBean(ANY_NAME));
    }

    @Test(expected = IllegalStateException.class)
    public void findExistingBlueprintContainerIllegalSyntax() throws Exception {
        doThrow(InvalidSyntaxException.class).when(bundleContext).getServiceReferences((String)any(), any());
        factory.getBean(ANY_NAME);
    }

    @Test(timeout = 3000)
    public void waitForBlueprintContainer() throws Exception {
        final AtomicReference<Object> ref = new AtomicReference<>();
        final Thread th = new Thread(() -> ref.set(factory.getBean(ANY_NAME)));
        th.start();
        sleep(1000);
        factory.serviceChanged(new ServiceEvent(REGISTERED, containerRef));
        th.join();
        assertSame(ANY_BEAN, ref.get());
    }

    @Test(timeout = 3000, expected = BeanDefinitionStoreException.class)
    public void waitForBlueprintContainerInterrupted() throws Exception {
        currentThread().interrupt();
        factory.getBean(ANY_NAME);
    }

    @Test(timeout = 3000)
    public void blueprintContainerDestroyedWhileWaiting() throws Exception {
        final AtomicReference<BeanDefinitionStoreException> ref = new AtomicReference<>();
        final Thread th = new Thread(() -> {
            try {
                factory.getBean(ANY_NAME);
            } catch (final BeanDefinitionStoreException expected) {
                ref.set(expected);
            }
        });
        th.start();
        sleep(1000);
        factory.serviceChanged(new ServiceEvent(UNREGISTERING, containerRef));
        th.join();
        assertNotNull(ref.get());
        assertSame(BLUEPRINT_CONTAINER_CONTAINER_HAS_BEEN_SHUTDOWN, ref.get().getMessage());
    }
}
