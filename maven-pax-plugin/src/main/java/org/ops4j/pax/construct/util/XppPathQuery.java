package org.ops4j.pax.construct.util;

/*
 * Copyright 2007 Stuart McCulloch, Alin Dreghiciu
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.xml.Xpp3Dom;

public class XppPathQuery
{
    static final String NODE = "\\w+";

    static final String PARENT = NODE + "(?:/" + NODE + ")*";

    static final String TEST = "(.|" + NODE + ")='(.*)'";

    static final String BIN_OP = "(?:and|or)";

    static final String PREDICATE = TEST + "(?:\\s+" + BIN_OP + "\\s+" + TEST + ")*";

    static final String XPATH = "/?(" + PARENT + ")/(" + NODE + ")\\[\\s*(" + PREDICATE + ")\\s*\\]";

    final Matcher m_xpathParser;

    public XppPathQuery( String xpath )
        throws IllegalArgumentException
    {
        m_xpathParser = Pattern.compile( XPATH ).matcher( xpath );
        if( !m_xpathParser.matches() )
        {
            throw new IllegalArgumentException( "Unsupported XPATH syntax: " + xpath );
        }
    }

    public Xpp3Dom queryParent( Xpp3Dom dom )
    {
        String[] nodes = m_xpathParser.group( 1 ).split( "/" );

        Xpp3Dom parent = dom;
        for( int i = 0; parent != null && i < nodes.length; i++ )
        {
            parent = parent.getChild( nodes[i] );
        }

        return parent;
    }

    public int[] queryChildren( Xpp3Dom parent )
    {
        String nodeName = m_xpathParser.group( 2 );

        Pattern testPattern = Pattern.compile( TEST );
        String[] testClauses = m_xpathParser.group( 3 ).split( "\\s+" );

        List candidates = new ArrayList( Arrays.asList( parent.getChildren() ) );

        Set results = new HashSet();
        for( int i = -1; i < testClauses.length; i += 2 )
        {
            Matcher matcher = testPattern.matcher( testClauses[i + 1] );
            matcher.matches();

            Set selection = filter( candidates, nodeName, matcher.group( 1 ), matcher.group( 2 ) );

            if( i > 0 && "and".equals( testClauses[i] ) )
            {
                results.retainAll( selection );
            }
            else
            {
                results.addAll( selection );
            }
        }

        int[] indices = new int[results.size()];

        int n = 0;
        for( Iterator i = results.iterator(); i.hasNext(); )
        {
            indices[n++] = candidates.indexOf( i.next() );
        }

        return indices;
    }

    Set filter( List candidates, String nodeName, String keyName, String keyValue )
    {
        Set results = new HashSet();

        for( Iterator i = candidates.iterator(); i.hasNext(); )
        {
            Xpp3Dom node = (Xpp3Dom) i.next();
            Xpp3Dom test = node;

            if( !keyName.startsWith( "." ) )
            {
                test = node.getChild( keyName );
            }

            if( nodeName.equals( node.getName() ) && keyValue.equals( test.getValue() ) )
            {
                results.add( node );
            }
        }

        return results;
    }
}
