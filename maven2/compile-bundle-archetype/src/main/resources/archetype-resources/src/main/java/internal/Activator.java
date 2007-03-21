package ${package}.internal;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import ${package}.Service;

public final class Activator
    implements BundleActivator
{
    private Service m_service;

    public void start( BundleContext bc )
        throws Exception
    {
        System.out.println( "STARTING ${package}" ); // TODO: use logging service :)

        m_service = new ServiceImpl();

        Dictionary props = new Hashtable<String, String>();

        System.out.println( "REGISTER ${package}.Service" );

        bc.registerService( Service.class.getName(), m_service, props );
    }

    public void stop( BundleContext bc )
        throws Exception
    {
        System.out.println( "STOPPING ${package}" );
    }
}

