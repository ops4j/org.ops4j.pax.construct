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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;
import org.ops4j.pax.construct.util.PomUtils.Pom;
import org.ops4j.pax.construct.util.PomUtils.PomException;

public class XppPom
    implements Pom
{
    final File m_file;
    Xpp3Dom m_pom;

    public XppPom( File pomFile )
        throws PomException
    {
        m_file = pomFile;

        try
        {
            XmlPullParser parser = RoundTripXml.createParser();
            parser.setInput( new FileReader( m_file ) );
            m_pom = Xpp3DomBuilder.build( parser, false );
        }
        catch( Exception e )
        {
            throw new PomException( "Unable to parse POM " + m_file, e );
        }
    }

    public XppPom( File pomFile, String groupId, String artifactId )
        throws PomException
    {
        m_file = pomFile;

        m_pom = new Xpp3Dom( "project" );

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

    public String getGroupId()
    {
        Xpp3Dom groupId = m_pom.getChild( "groupId" );
        if( null == groupId )
        {
            return m_pom.getChild( "parent" ).getChild( "groupId" ).getValue();
        }
        return m_pom.getChild( "groupId" ).getValue();
    }

    public String getArtifactId()
    {
        return m_pom.getChild( "artifactId" ).getValue();
    }

    public String getVersion()
    {
        Xpp3Dom version = m_pom.getChild( "version" );
        if( null == version )
        {
            return m_pom.getChild( "parent" ).getChild( "version" ).getValue();
        }
        return m_pom.getChild( "version" ).getValue();
    }

    public void setParent( Pom pom, String relativePath, boolean overwrite )
        throws PomException
    {
        MavenProject project = new MavenProject( new Model() );

        project.setGroupId( pom.getGroupId() );
        project.setArtifactId( pom.getArtifactId() );
        project.setVersion( pom.getVersion() );

        setParent( project, relativePath, overwrite );
    }

    public void setParent( MavenProject project, String relativePath, boolean overwrite )
        throws PomException
    {
        if( m_pom.getChild( "parent" ) != null && !overwrite )
        {
            throw new PomException( "Keeping existing data, use -Doverwrite to replace it" );
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

    public void adjustRelativePath( int offset )
    {
        Xpp3Dom node = m_pom.getChild( "parent" ).getChild( "relativePath" );

        String relativeText = node.getValue();

        for( int i = 0; i < offset; i++ )
        {
            relativeText = "../" + relativeText;
        }

        for( int i = 0; i > offset; i-- )
        {
            relativeText = relativeText.substring( 3 );
        }

        node.setValue( relativeText );
    }

    public void addRepository( Repository repository, boolean overwrite )
        throws PomException
    {
        String id = repository.getId();
        String url = repository.getUrl();

        String xpath = "repositories/repository[id='" + id + "' or url='" + url + "']";

        removeChildren( xpath, overwrite );

        Xpp3DomMap repo = new Xpp3DomMap( "repository" );
        repo.putValue( "id", id );
        repo.putValue( "url", url );

        Xpp3Dom list = new Xpp3DomList( "repositories" );
        list.addChild( repo );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( m_pom, newPom );
    }

    public void addModule( String module, boolean overwrite )
        throws PomException
    {
        String xpath = "modules/module[.='" + module + "']";

        removeChildren( xpath, overwrite );

        Xpp3Dom mod = new Xpp3Dom( "module" );
        mod.setValue( module );

        Xpp3Dom list = new Xpp3DomList( "modules" );
        list.addChild( mod );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( m_pom, newPom );
    }

    public void removeModule( String module )
        throws PomException
    {
        String xpath = "modules/module[.='" + module + "']";

        removeChildren( xpath, true );
    }

    public void addDependency( MavenProject project, boolean overwrite )
        throws PomException
    {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();

        String xpath = "dependencies/dependency[groupId='" + groupId + "' and artifactId='" + artifactId + "']";

        removeChildren( xpath, overwrite );

        Xpp3DomMap dep = new Xpp3DomMap( "dependency" );
        dep.putValue( "groupId", groupId );
        dep.putValue( "artifactId", artifactId );
        dep.putValue( "version", project.getVersion() );
        dep.putValue( "scope", Artifact.SCOPE_PROVIDED );

        Xpp3Dom list = new Xpp3DomList( "dependencies" );
        list.addChild( dep );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( m_pom, newPom );
    }

    public void addDependency( Dependency dependency, boolean overwrite )
        throws PomException
    {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();

        String xpath = "dependencies/dependency[groupId='" + groupId + "' and artifactId='" + artifactId + "']";

        removeChildren( xpath, overwrite );

        Xpp3DomMap dep = new Xpp3DomMap( "dependency" );
        dep.putValue( "groupId", groupId );
        dep.putValue( "artifactId", artifactId );
        dep.putValue( "version", dependency.getVersion() );
        dep.putValue( "scope", dependency.getScope() );
        dep.putValue( "type", dependency.getType() );
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

    public void removeDependency( MavenProject project )
        throws PomException
    {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();

        String xpath = "dependencies/dependency[groupId='" + groupId + "' and artifactId='" + artifactId + "']";

        removeChildren( xpath, true );
    }

    public void removeDependency( Dependency dependency )
        throws PomException
    {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();

        String xpath = "dependencies/dependency[groupId='" + groupId + "' and artifactId='" + artifactId + "']";

        removeChildren( xpath, true );
    }

    public void write()
        throws PomException
    {
        try
        {
            FileWriter writer = new FileWriter( m_file );

            XmlSerializer serializer = RoundTripXml.createSerializer();

            serializer.setOutput( writer );
            serializer.startDocument( writer.getEncoding(), null );
            m_pom.writeToSerializer( null, serializer );
            serializer.endDocument();
        }
        catch( Exception e )
        {
            throw new PomException( "Unable to serialize POM " + m_file, e );
        }
    }

    static class Xpp3DomMap extends Xpp3Dom
    {
        public Xpp3DomMap( String name )
        {
            super( name );
        }

        public void putValue( String name, String value )
        {
            putValue( this, name, value );
        }

        public static void putValue( Xpp3Dom dom, String name, String value )
        {
            if( null != value )
            {
                Xpp3Dom child = new Xpp3Dom( name );
                child.setValue( value );
                dom.addChild( child );
            }
        }
    }

    static class Xpp3DomList extends Xpp3Dom
    {
        public Xpp3DomList( String name )
        {
            super( name );

            setAttribute( CHILDREN_COMBINATION_MODE_ATTRIBUTE, CHILDREN_COMBINATION_APPEND );
        }
    }

    void removeChildren( String xpath, boolean overwrite )
        throws PomException
    {
        XppPathQuery pathQuery = new XppPathQuery( xpath );
        Xpp3Dom parent = pathQuery.queryParent( m_pom );

        if( null != parent )
        {
            int[] children = pathQuery.queryChildren( parent );

            if( children.length > 0 && !overwrite )
            {
                throw new PomException( "Keeping existing data, use -Doverwrite to replace it" );
            }

            for( int i = 0; i < children.length; i++ )
            {
                parent.removeChild( children[i] );
            }
        }
    }
}
