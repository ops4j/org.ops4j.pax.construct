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

/**
 * Iterate over all POMs in a Maven project tree using depth-first and backtracking search (non-recursive)
 */
public class PomIterator
    implements Iterator
{
    /**
     * Current POM
     */
    private Pom m_pom;

    /**
     * Next POM - either a module of the current POM, or the POM above it
     */
    private Pom m_nextPom;

    /**
     * All the POMs seen so far
     */
    private Set m_visited;

    /**
     * @param here a directory somewhere in the project tree
     */
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

    /**
     * {@inheritDoc}
     */
    public Object next()
    {
        // check hasNext, in case it's not been called
        if( null == m_nextPom && !hasNext() )
        {
            throw new NoSuchElementException();
        }

        m_pom = m_nextPom;
        m_nextPom = null;

        return m_pom;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNext()
    {
        // iterative search for next POM...
        while( null == m_nextPom && null != m_pom )
        {
            if( m_visited.add( m_pom ) )
            {
                // cache result
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

    /**
     * @return next POM in the search space - may have already been visited or may be null
     */
    private Pom nextModule()
    {
        for( Iterator i = m_pom.getModuleNames().iterator(); i.hasNext(); )
        {
            Pom subPom = m_pom.getModulePom( (String) i.next() );
            if( !m_visited.contains( subPom ) )
            {
                // visit module
                return subPom;
            }
        }

        // backtrack to search siblings
        return m_pom.getContainingPom();
    }

    /**
     * {@inheritDoc}
     */
    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
