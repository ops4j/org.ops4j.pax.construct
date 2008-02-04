package org.ops4j.pax.construct.util;

/*
 * Copyright 2007 Stuart McCulloch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;

/**
 * Provide access to private inherited mojo fields
 */
public final class ReflectMojo
{
    /**
     * Maven mojo instance
     */
    private final AbstractMojo m_mojo;

    /**
     * Inherited super-mojo
     */
    private final Class m_clazz;

    /**
     * @param mojo maven mojo instance
     * @param clazz inherited super-mojo
     */
    public ReflectMojo( AbstractMojo mojo, Class clazz )
    {
        m_mojo = mojo;
        m_clazz = clazz;
    }

    /**
     * @param name field name
     * @return reflected field
     * @throws NoSuchFieldException
     */
    Field getMojoField( String name )
        throws NoSuchFieldException
    {
        return m_clazz.getDeclaredField( name );
    }

    /**
     * @return mojo instance
     */
    AbstractMojo getMojoInstance()
    {
        return m_mojo;
    }

    /**
     * @return mojo logger
     */
    Log getMojoLogger()
    {
        return m_mojo.getLog();
    }

    /**
     * @param name name of the field member
     * @return true if the field exists, otherwise false
     */
    public boolean hasField( final String name )
    {
        return null != AccessController.doPrivileged( new PrivilegedAction()
        {
            public Object run()
            {
                try
                {
                    return getMojoField( name );
                }
                catch( NoSuchFieldException e )
                {
                    return null;
                }
                catch( SecurityException e )
                {
                    return null;
                }
            }
        } );
    }

    /**
     * @param name name of the field member
     * @param value the new value for the field
     */
    public void setField( final String name, final Object value )
    {
        AccessController.doPrivileged( new PrivilegedAction()
        {
            public Object run()
            {
                try
                {
                    final Object safeValue;
                    Field f = getMojoField( name );

                    if( boolean.class.equals( f.getType() ) )
                    {
                        safeValue = Boolean.valueOf( value.toString() );
                    }
                    else
                    {
                        safeValue = value;
                    }

                    f.setAccessible( true );
                    f.set( getMojoInstance(), safeValue );
                }
                catch( NoSuchFieldException e )
                {
                    getMojoLogger().error( "Unknown field " + name, e );
                }
                catch( IllegalAccessException e )
                {
                    getMojoLogger().error( "Cannot set field " + name, e );
                }

                return null;
            }
        } );
    }

    /**
     * @param name name of the field member
     * @return the current value in the field
     */
    public Object getField( final String name )
    {
        return AccessController.doPrivileged( new PrivilegedAction()
        {
            public Object run()
            {
                try
                {
                    Field f = getMojoField( name );
                    f.setAccessible( true );
                    return f.get( getMojoInstance() );
                }
                catch( NoSuchFieldException e )
                {
                    getMojoLogger().error( "Unknown field " + name, e );
                }
                catch( IllegalAccessException e )
                {
                    getMojoLogger().error( "Cannot get field " + name, e );
                }

                return null;
            }
        } );
    }
}
