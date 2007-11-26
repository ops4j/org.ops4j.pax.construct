package org.ops4j.pax.construct.clone;

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
import java.io.Writer;

import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.ops4j.pax.construct.util.StreamFactory;

/**
 * Replacement for the Maven archetype model, to support non-filtered files
 */
public class ArchetypeModel
{
    /**
     * Current XML document
     */
    private Xpp3Dom m_dom;

    /**
     * Create a new archetype model
     */
    public ArchetypeModel()
    {
        m_dom = new Xpp3Dom( "archetype" );
    }

    /**
     * @param node XML node
     * @param name child name
     * @return existing child node (created if it doesn't already exist)
     */
    private static Xpp3Dom getNode( Xpp3Dom node, String name )
    {
        Xpp3Dom tag = node.getChild( name );
        if( null == tag )
        {
            tag = new Xpp3Dom( name );
            node.addChild( tag );
        }
        return tag;
    }

    /**
     * @param id archetype id
     */
    public void setId( String id )
    {
        Xpp3Dom tag = getNode( m_dom, "id" );
        tag.setValue( id );
    }

    /**
     * @param allowPartial true if this is a partial archetype, otherwise false
     */
    public void setAllowPartial( boolean allowPartial )
    {
        Xpp3Dom tag = getNode( m_dom, "allowPartial" );
        tag.setValue( Boolean.toString( allowPartial ) );
    }

    /**
     * @param entry source file
     */
    public void addSource( String entry )
    {
        Xpp3Dom sources = getNode( m_dom, "sources" );
        Xpp3Dom tag = new Xpp3Dom( "source" );
        tag.setValue( entry );
        sources.addChild( tag );
    }

    /**
     * @param entry test source file
     */
    public void addTestSource( String entry )
    {
        Xpp3Dom testSources = getNode( m_dom, "testSources" );
        Xpp3Dom tag = new Xpp3Dom( "source" );
        tag.setValue( entry );
        testSources.addChild( tag );
    }

    /**
     * @param entry resource file
     * @param isFiltered true if the file should be filtered, otherwise false
     */
    public void addResource( String entry, boolean isFiltered )
    {
        Xpp3Dom resources = getNode( m_dom, "resources" );
        Xpp3Dom tag = new Xpp3Dom( "resource" );
        tag.setValue( entry );
        if( !isFiltered )
        {
            tag.setAttribute( "filtered", "false" );
        }
        resources.addChild( tag );
    }

    /**
     * @param entry test resource file
     * @param isFiltered true if the file should be filtered, otherwise false
     */
    public void addTestResource( String entry, boolean isFiltered )
    {
        Xpp3Dom testResources = getNode( m_dom, "testResources" );
        Xpp3Dom tag = new Xpp3Dom( "resource" );
        tag.setValue( entry );
        if( !isFiltered )
        {
            tag.setAttribute( "filtered", "false" );
        }
        testResources.addChild( tag );
    }

    /**
     * @param file where to save the archetype model
     * @throws IOException
     */
    public void write( File file )
        throws IOException
    {
        Writer writer = StreamFactory.newXmlWriter( file );
        Xpp3DomWriter.write( writer, m_dom );
        IOUtil.close( writer );
    }
}
