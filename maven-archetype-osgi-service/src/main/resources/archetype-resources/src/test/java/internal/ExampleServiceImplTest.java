package ${package}.internal;

import junit.framework.TestCase;
import ${package}.ExampleService;

public class ExampleServiceImplTest extends TestCase
{
    public void testExampleServiceScramble()
    {
        ExampleService anExampleService = new ExampleServiceImpl();

        String in = "Hello world";
        String out = anExampleService.scramble( in );

        System.out.println( '\'' + in + "' => '" + out + '\'' );
    }
}
