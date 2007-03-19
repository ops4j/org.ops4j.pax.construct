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
        m_service = new ServiceImpl();

        Dictionary props = new Hashtable<String, String>();

        bc.registerService( Service.class.getName(), m_service, props );
    }

    public void stop( BundleContext bc )
        throws Exception
    {
    }
}

