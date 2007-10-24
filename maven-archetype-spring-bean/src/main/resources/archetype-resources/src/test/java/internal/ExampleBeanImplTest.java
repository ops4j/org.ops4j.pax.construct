package ${package}.internal;

import junit.framework.TestCase;
import ${package}.ExampleBean;

public class ExampleBeanImplTest extends TestCase
{
    public void testExampleBeanIsABean()
    {
        ExampleBean anExampleBean = new ExampleBeanImpl();
        assertTrue( anExampleBean.isABean() );
    }
}
