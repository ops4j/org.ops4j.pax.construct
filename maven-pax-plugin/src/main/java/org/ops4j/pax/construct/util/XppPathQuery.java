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

/**
 * Provide a very simple XPATH query implementation for XML pull-parser (Xpp) documents
 */
public class XppPathQuery
{
    /**
     * Node is a word
     */
    private static final String NODE = "\\w+";

    /**
     * Parent is a node followed by zero or more slash-separated nodes
     */
    private static final String PARENT = NODE + "(?:/" + NODE + ")*";

    /**
     * Test compares dot (ie. current node) or a node to a quoted string
     */
    private static final String TEST = "(.|" + NODE + ")='(.*)'";

    /**
     * Binary operator can be and / or
     */
    private static final String BIN_OP = "(?:and|or)";

    /**
     * Predicate is a test combined with zero or more tests using binary operators
     */
    private static final String PREDICATE = TEST + "(?:\\s+" + BIN_OP + "\\s+" + TEST + ")*";

    /**
     * XPATH is a parent followed by a pivot node and a predicate
     */
    private static final String XPATH = "/?(" + PARENT + ")/(" + NODE + ")\\[\\s*(" + PREDICATE + ")\\s*\\]";

    /**
     * Compiled XPATH expression matcher
     */
    private final Matcher m_xpathParser;

    /**
     * Create a new XPATH query object from a given string
     * 
     * @param xpath simple XPATH query
     * @throws IllegalArgumentException
     */
    public XppPathQuery( String xpath )
        throws IllegalArgumentException
    {
        m_xpathParser = Pattern.compile( XPATH ).matcher( xpath );
        if( !m_xpathParser.matches() )
        {
            throw new IllegalArgumentException( "Unsupported XPATH syntax: " + xpath );
        }
    }

    /**
     * Find the parent node for this XPATH query
     * 
     * @param dom document root
     * @return the parent node
     */
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

    /**
     * Find all children matching the XPATH predicate
     * 
     * @param parent the parent node
     * @return array of child indices
     */
    public int[] queryChildren( Xpp3Dom parent )
    {
        String pivotNode = m_xpathParser.group( 2 );

        // split into tests and binary operators
        Pattern testPattern = Pattern.compile( TEST );
        String[] testClauses = m_xpathParser.group( 3 ).split( "\\s+" );

        List children = new ArrayList( Arrays.asList( parent.getChildren() ) );

        Set results = new HashSet();
        for( int i = -1; i < testClauses.length; i += 2 )
        {
            // parse test clause (at every even index)
            Matcher matcher = testPattern.matcher( testClauses[i + 1] );
            matcher.matches();

            Set selection = filter( children, pivotNode, matcher.group( 1 ), matcher.group( 2 ) );

            if( i > 0 && "and".equals( testClauses[i] ) )
            {
                // and == intersect
                results.retainAll( selection );
            }
            else
            {
                // or == union
                results.addAll( selection );
            }
        }

        int[] indices = new int[results.size()];

        int n = 0;
        for( Iterator i = results.iterator(); i.hasNext(); )
        {
            indices[n++] = children.indexOf( i.next() );
        }

        return indices;
    }

    /**
     * @param children complete list of child nodes
     * @param pivotNode pivot node
     * @param testNode test node
     * @param testValue test value
     * @return matching child nodes
     */
    Set filter( List children, String pivotNode, String testNode, String testValue )
    {
        Set results = new HashSet();

        for( Iterator i = children.iterator(); i.hasNext(); )
        {
            Xpp3Dom node = (Xpp3Dom) i.next();
            Xpp3Dom test = node;

            if( !testNode.startsWith( "." ) )
            {
                test = node.getChild( testNode );
            }

            if( pivotNode.equals( node.getName() ) && testValue.equals( test.getValue() ) )
            {
                results.add( node );
            }
        }

        return results;
    }
}
