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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.ops4j.pax.construct.util.PomUtils.Pom;

public class PomIterator
    implements Iterator
{
    Pom m_pom;
    Pom m_nextPom;
    Set m_visited;

    public PomIterator( File here )
    {
        m_visited = new HashSet();

        try
        {
            m_pom = PomUtils.readPom( here );
        }
        catch( IOException e )
        {
            m_pom = null;
        }
    }

    public Object next()
    {
        if( null == m_nextPom && !hasNext() )
        {
            throw new NoSuchElementException();
        }

        m_pom = m_nextPom;
        m_nextPom = null;

        return m_pom;
    }

    public boolean hasNext()
    {
        while( null == m_nextPom && null != m_pom )
        {
            if( m_visited.add( m_pom ) )
            {
                m_nextPom = m_pom;
                m_pom = null;
            }
            else
            {
                m_pom = nextModule();
            }
        }
        return null != m_nextPom;
    }

    private Pom nextModule()
    {
        for( Iterator i = m_pom.getModuleNames().iterator(); i.hasNext(); )
        {
            Pom subPom = m_pom.getModulePom( (String) i.next() );
            if( !m_visited.contains( subPom ) )
            {
                return subPom;
            }
        }
        return m_pom.getContainingPom();
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
