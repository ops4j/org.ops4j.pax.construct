package ${package}.internal;

import ${package}.ExampleBean;
import org.springframework.test.AbstractDependencyInjectionSpringContextTests;

/**
 * Integration test the bundle locally (outside of OSGi).
 * Use AbstractOsgiTests and a separate integration test project
 * for testing inside of OSGi.
 */
public class ExampleBeanIntegrationTest extends AbstractDependencyInjectionSpringContextTests
{
    private ExampleBean myBean;

    protected String[] getConfigLocations()
    {
        return new String[] { "META-INF/spring/bundle-context.xml" };
    }

    public void setBean( ExampleBean bean )
    {
        this.myBean = bean;
    }

    public void testBeanIsABean()
    {
        assertTrue( this.myBean.isABean() );
    }
}
