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

public abstract class ReflectUtils
{
    public static class ReflectMojo
    {
        final AbstractMojo m_mojo;
        final Class m_clazz;

        public ReflectMojo( AbstractMojo mojo, Class clazz )
        {
            m_mojo = mojo;
            m_clazz = clazz;
        }

        public void setField( final String name, final Object value )
        {
            AccessController.doPrivileged( new PrivilegedAction()
            {
                public Object run()
                {
                    try
                    {
                        Field f = m_clazz.getDeclaredField( name );
                        f.setAccessible( true );
                        f.set( m_mojo, value );
                    }
                    catch( Exception e )
                    {
                        m_mojo.getLog().error( "Cannot set field " + name, e );
                    }
                    return null;
                }
            } );
        }

        public Object getField( final String name )
        {
            return AccessController.doPrivileged( new PrivilegedAction()
            {
                public Object run()
                {
                    try
                    {
                        Field f = m_clazz.getDeclaredField( name );
                        f.setAccessible( true );
                        return f.get( m_mojo );
                    }
                    catch( Exception e )
                    {
                        m_mojo.getLog().error( "Cannot get field " + name, e );
                    }
                    return null;
                }
            } );
        }
    }
}
