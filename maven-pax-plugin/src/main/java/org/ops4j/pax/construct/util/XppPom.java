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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;
import org.ops4j.pax.construct.util.PomUtils.ExistingElementException;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Support round-trip editing of Maven POMs, preserving comments and formatting as much as possible
 */
public class XppPom
    implements Pom
{
    /**
     * Underlying XML file
     */
    private final File m_file;

    /**
     * Current XML document
     */
    private Xpp3Dom m_pom;

    /**
     * @param pomFile XML file containing Maven project model
     * @throws IOException
     */
    public XppPom( File pomFile )
        throws IOException
    {
        // protect against changes in working directory
        m_file = pomFile.getAbsoluteFile();

        try
        {
            XmlPullParser parser = RoundTripXml.createParser();
            FileReader reader = new FileReader( m_file );
            parser.setInput( reader );
            m_pom = Xpp3DomBuilder.build( parser, false );
            IOUtil.close( reader );
        }
        catch( XmlPullParserException e )
        {
            throw new IOException( e.getLocalizedMessage() );
        }
    }

    /**
     * @param pomFile XML file, may or may not exist
     * @param groupId project group id
     * @param artifactId project artifact id
     */
    public XppPom( File pomFile, String groupId, String artifactId )
    {
        // protect against changes in working directory
        m_file = pomFile.getAbsoluteFile();

        m_pom = new Xpp3Dom( "project" );

        // standard header cruft
        m_pom.setAttribute( "xmlns", "http://maven.apache.org/POM/4.0.0" );
        m_pom.setAttribute( "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance" );
        m_pom.setAttribute( "xsi:schemaLocation",
            "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd" );

        Xpp3DomMap.putValue( m_pom, "modelVersion", "4.0.0" );
        Xpp3DomMap.putValue( m_pom, "groupId", groupId );
        Xpp3DomMap.putValue( m_pom, "artifactId", artifactId );
        Xpp3DomMap.putValue( m_pom, "name", "" );
        Xpp3DomMap.putValue( m_pom, "packaging", "pom" );

        m_file.getParentFile().mkdirs();
    }

    /**
     * {@inheritDoc}
     */
    public String getId()
    {
        // follow the Maven standard...
        return getGroupId() + ':' + getArtifactId() + ':' + getPackaging() + ':' + getVersion();
    }

    /**
     * {@inheritDoc}
     */
    public String getParentId()
    {
        Xpp3Dom parent = m_pom.getChild( "parent" );
        if( null == parent )
        {
            return null;
        }

        Xpp3Dom groupId = parent.getChild( "groupId" );
        Xpp3Dom artifactId = parent.getChild( "artifactId" );
        Xpp3Dom version = parent.getChild( "version" );

        // assume that the parent has pom packaging (seems reasonable assumption)
        return groupId.getValue() + ':' + artifactId.getValue() + ":pom:" + version.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public String getGroupId()
    {
        Xpp3Dom groupId = m_pom.getChild( "groupId" );
        Xpp3Dom parent = m_pom.getChild( "parent" );
        if( null == groupId && null != parent )
        {
            // inherit group from parent element
            groupId = parent.getChild( "groupId" );
        }
        if( null == groupId )
        {
            return null;
        }
        return groupId.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public String getArtifactId()
    {
        return m_pom.getChild( "artifactId" ).getValue();
    }

    /**
     * {@inheritDoc}
     */
    public String getVersion()
    {
        Xpp3Dom version = m_pom.getChild( "version" );
        Xpp3Dom parent = m_pom.getChild( "parent" );
        if( null == version && null != parent )
        {
            // inherit version from parent element
            version = parent.getChild( "version" );
        }
        if( null == version )
        {
            return null;
        }
        return version.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public String getPackaging()
    {
        return m_pom.getChild( "packaging" ).getValue();
    }

    /**
     * {@inheritDoc}
     */
    public List getModuleNames()
    {
        List names = new ArrayList();

        Xpp3Dom modules = m_pom.getChild( "modules" );
        if( null != modules )
        {
            Xpp3Dom[] values = modules.getChildren( "module" );
            for( int i = 0; i < values.length; i++ )
            {
                names.add( values[i].getValue() );
            }
        }

        return names;
    }

    /**
     * {@inheritDoc}
     */
    public Pom getContainingPom()
    {
        try
        {
            File baseDir = getBasedir();

            // check it really does contain our current project
            Pom pom = PomUtils.readPom( baseDir.getParentFile() );
            if( pom.getModuleNames().contains( baseDir.getName() ) )
            {
                return pom;
            }
            return null;
        }
        catch( IOException e )
        {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Pom getModulePom( String name )
    {
        try
        {
            // check it really is a valid module
            if( getModuleNames().contains( name ) )
            {
                return PomUtils.readPom( new File( m_file.getParentFile(), name ) );
            }
            return null;
        }
        catch( IOException e )
        {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public File getFile()
    {
        return m_file;
    }

    /**
     * {@inheritDoc}
     */
    public File getBasedir()
    {
        return m_file.getParentFile();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isBundleProject()
    {
        // local project, so can use very simple test based on packaging type
        return m_pom.getChild( "packaging" ).getValue().indexOf( "bundle" ) >= 0;
    }

    /**
     * {@inheritDoc}
     */
    public String getBundleSymbolicName()
    {
        Xpp3Dom properties = m_pom.getChild( "properties" );
        if( null != properties )
        {
            Xpp3Dom symbolicName = properties.getChild( "bundle.symbolicName" );
            if( null != symbolicName )
            {
                return symbolicName.getValue();
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public File getFinalBundle()
    {
        // assume standard output location for now - finding real output folder and final name is non-trivial
        File bundle = new File( getBasedir(), "target/" + getArtifactId() + '-' + getVersion() + ".jar" );

        // has it been built?
        if( bundle.exists() )
        {
            return bundle;
        }
        else
        {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setParent( Pom pom, String relativePath, boolean overwrite )
        throws ExistingElementException
    {
        MavenProject project = new MavenProject( new Model() );

        project.setGroupId( pom.getGroupId() );
        project.setArtifactId( pom.getArtifactId() );
        project.setVersion( pom.getVersion() );

        setParent( project, relativePath, overwrite );
    }

    /**
     * {@inheritDoc}
     */
    public void setParent( MavenProject project, String relativePath, boolean overwrite )
        throws ExistingElementException
    {
        if( m_pom.getChild( "parent" ) != null && !overwrite )
        {
            throw new ExistingElementException( "parent" );
        }

        Xpp3DomMap parent = new Xpp3DomMap( "parent" );
        parent.putValue( "relativePath", relativePath );
        parent.putValue( "groupId", project.getGroupId() );
        parent.putValue( "artifactId", project.getArtifactId() );
        parent.putValue( "version", project.getVersion() );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( parent );

        m_pom = Xpp3Dom.mergeXpp3Dom( newPom, m_pom );
    }

    /**
     * {@inheritDoc}
     */
    public void setGroupId( String newGroupId )
    {
        Xpp3Dom groupId = m_pom.getChild( "groupId" );
        if( null == groupId )
        {
            groupId = new Xpp3Dom( "groupId" );
            m_pom.addChild( groupId );
        }
        groupId.setValue( newGroupId );
    }

    /**
     * {@inheritDoc}
     */
    public void addRepository( Repository repository, boolean snapshots, boolean releases, boolean overwrite )
        throws ExistingElementException
    {
        String id = repository.getId();
        String url = repository.getUrl();

        String xpath = "repositories/repository[id='" + id + "' or url='" + url + "']";

        // clear old elements when overwriting
        if( findChildren( xpath, overwrite ) && !overwrite )
        {
            throw new ExistingElementException( "repository" );
        }

        Xpp3DomMap repo = new Xpp3DomMap( "repository" );
        repo.putValue( "id", id );
        repo.putValue( "url", url );

        if( !snapshots )
        {
            Xpp3DomMap snapshotFlag = new Xpp3DomMap( "snapshots" );
            snapshotFlag.putValue( "enabled", "false" );
            repo.addChild( snapshotFlag );
        }

        if( !releases )
        {
            Xpp3DomMap releaseFlag = new Xpp3DomMap( "releases" );
            releaseFlag.putValue( "enabled", "false" );
            repo.addChild( releaseFlag );
        }

        Xpp3Dom list = new Xpp3DomList( "repositories" );
        list.addChild( repo );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( m_pom, newPom );
    }

    /**
     * {@inheritDoc}
     */
    public void addModule( String module, boolean overwrite )
        throws ExistingElementException
    {
        String xpath = "modules/module[.='" + module + "']";

        // clear old elements when overwriting
        if( findChildren( xpath, overwrite ) && !overwrite )
        {
            throw new ExistingElementException( "module" );
        }

        Xpp3Dom mod = new Xpp3Dom( "module" );
        mod.setValue( module );

        Xpp3Dom list = new Xpp3DomList( "modules" );
        list.addChild( mod );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( m_pom, newPom );
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeModule( String module )
    {
        String xpath = "modules/module[.='" + module + "']";

        return findChildren( xpath, true );
    }

    /**
     * {@inheritDoc}
     */
    public void addDependency( Dependency dependency, boolean overwrite )
        throws ExistingElementException
    {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();

        String xpath = "dependencies/dependency[groupId='" + groupId + "' and artifactId='" + artifactId + "']";

        // clear old elements when overwriting
        if( findChildren( xpath, overwrite ) && !overwrite )
        {
            throw new ExistingElementException( "dependency" );
        }

        Xpp3DomMap dep = new Xpp3DomMap( "dependency" );
        dep.putValue( "groupId", groupId );
        dep.putValue( "artifactId", artifactId );
        dep.putValue( "version", dependency.getVersion() );
        dep.putValue( "scope", dependency.getScope() );

        String type = dependency.getType();
        if( !"jar".equals( type ) )
        {
            // jar is the default type
            dep.putValue( "type", type );
        }

        if( dependency.isOptional() )
        {
            dep.putValue( "optional", "true" );
        }

        Xpp3Dom list = new Xpp3DomList( "dependencies" );
        list.addChild( dep );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( m_pom, newPom );
    }

    /**
     * {@inheritDoc}
     */
    public boolean updateDependencyGroup( Dependency dependency, String newGroupId )
    {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();

        boolean updated = false;

        String xpath1 = "dependencies/dependency[groupId='" + groupId + "' and artifactId='" + artifactId + "']";
        updated = updateGroupId( xpath1, newGroupId ) || updated;

        String xpath2 = "dependencyManagement/" + xpath1;
        updated = updateGroupId( xpath2, newGroupId ) || updated;

        return updated;
    }

    /**
     * @param xpath simple XPATH query
     * @param newGroupId new group id
     * @return true if any elements were updated, otherwise false
     */
    boolean updateGroupId( String xpath, String newGroupId )
    {
        XppPathQuery pathQuery = new XppPathQuery( xpath );
        Xpp3Dom parent = pathQuery.queryParent( m_pom );
        if( null == parent )
        {
            return false;
        }

        int[] children = pathQuery.queryChildren( parent );
        for( int i = 0; i < children.length; i++ )
        {
            Xpp3Dom group = parent.getChild( children[i] ).getChild( "groupId" );
            if( null != group )
            {
                group.setValue( newGroupId );
            }
        }
        return children.length > 0;
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeDependency( Dependency dependency )
    {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();

        boolean updated = false;

        String xpath1 = "dependencies/dependency[groupId='" + groupId + "' and artifactId='" + artifactId + "']";
        updated = findChildren( xpath1, true ) || updated;

        String xpath2 = "dependencyManagement/" + xpath1;
        updated = findChildren( xpath2, true ) || updated;

        return updated;
    }

    /**
     * {@inheritDoc}
     */
    public void write()
        throws IOException
    {
        FileWriter writer = new FileWriter( m_file );

        XmlSerializer serializer = RoundTripXml.createSerializer();

        serializer.setOutput( writer );
        serializer.startDocument( writer.getEncoding(), null );
        m_pom.writeToSerializer( null, serializer );
        serializer.endDocument();

        IOUtil.close( writer );
    }

    /**
     * Local utility class to help construct a "map" style XML fragment
     */
    static class Xpp3DomMap extends Xpp3Dom
    {
        /**
         * Create a new map fragment
         * 
         * @param name name of the map element
         */
        public Xpp3DomMap( String name )
        {
            super( name );
        }

        /**
         * Add a mapping to the map
         * 
         * @param name element name
         * @param value element value
         */
        public void putValue( String name, String value )
        {
            putValue( this, name, value );
        }

        /**
         * @param map map fragment
         * @param name element name
         * @param value element value
         */
        static void putValue( Xpp3Dom map, String name, String value )
        {
            if( null != value )
            {
                // only store non-null mapppings
                Xpp3Dom child = new Xpp3Dom( name );
                child.setValue( value );
                map.addChild( child );
            }
        }
    }

    /**
     * Private utility class to help construct a "list" style XML fragment
     */
    static class Xpp3DomList extends Xpp3Dom
    {
        /**
         * Create a new list fragment
         * 
         * @param name name of the list element
         */
        public Xpp3DomList( String name )
        {
            super( name );

            // list elements must append their children when merging with other fragments
            setAttribute( CHILDREN_COMBINATION_MODE_ATTRIBUTE, CHILDREN_COMBINATION_APPEND );
        }
    }

    /**
     * Local utility method to check for child elements based on a simple XPATH query
     * 
     * @param xpath simple XPATH query
     * @param clear remove matching elements
     * @return true if any child elements matched, otherwise false
     */
    boolean findChildren( String xpath, boolean clear )
    {
        XppPathQuery pathQuery = new XppPathQuery( xpath );
        Xpp3Dom parent = pathQuery.queryParent( m_pom );

        if( null == parent )
        {
            return false;
        }

        int[] children = pathQuery.queryChildren( parent );

        if( clear )
        {
            for( int i = 0; i < children.length; i++ )
            {
                parent.removeChild( children[i] );
            }
        }

        return children.length > 0;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals( Object obj )
    {
        if( obj instanceof XppPom )
        {
            return getId().equals( ( (XppPom) obj ).getId() );
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode()
    {
        return getId().hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        return getId();
    }
}
