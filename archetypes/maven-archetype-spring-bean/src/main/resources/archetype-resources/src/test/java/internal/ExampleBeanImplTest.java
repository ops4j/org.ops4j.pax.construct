package ${package}.internal;

import junit.framework.TestCase;
import ${package}.ExampleBean;

public class ExampleBeanImplTest extends TestCase
{
    public void testBeanIsABean()
    {
        ExampleBean aBean = new ExampleBeanImpl();
        assertTrue( aBean.isABean() );
    }
}
