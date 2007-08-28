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
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;
import org.ops4j.pax.construct.util.PomUtils.Pom;

public final class XppPom
    implements Pom
{
    private File file;
    private Xpp3Dom pom;

    public XppPom( File pomFile )
        throws MojoExecutionException
    {
        file = pomFile;

        try
        {
            XmlPullParser parser = RoundTripXml.createParser();
            parser.setInput( new FileReader( file ) );
            pom = Xpp3DomBuilder.build( parser, false );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to parse POM " + file, e );
        }
    }

    public void setParent( MavenProject project, String relativePath, boolean overwrite )
        throws MojoExecutionException
    {
        if( pom.getChild( "parent" ) != null && !overwrite )
        {
            throw new MojoExecutionException( "Keeping existing data, use -Doverwrite to replace it" );
        }

        Xpp3DomMap parent = new Xpp3DomMap( "parent" );
        parent.putValue( "relativePath", relativePath );
        parent.putValue( "groupId", project.getGroupId() );
        parent.putValue( "artifactId", project.getArtifactId() );
        parent.putValue( "version", project.getVersion() );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( parent );

        pom = Xpp3Dom.mergeXpp3Dom( newPom, pom );
    }

    public void adjustRelativePath( int offset )
    {
        Xpp3Dom node = pom.getChild( "parent" ).getChild( "relativePath" );

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
    {
        // TBD: CHECK FOR EXISTING DATA

        Xpp3DomMap repo = new Xpp3DomMap( "repository" );
        repo.putValue( "id", repository.getId() );
        repo.putValue( "url", repository.getUrl() );

        Xpp3Dom list = new Xpp3DomList( "repositories" );
        list.addChild( repo );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( pom, newPom );
    }

    public void addModule( String module, boolean overwrite )
    {
        // TBD: CHECK FOR EXISTING DATA

        Xpp3Dom mod = new Xpp3Dom( "module" );
        mod.setValue( module );

        Xpp3Dom list = new Xpp3DomList( "modules" );
        list.addChild( mod );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( pom, newPom );
    }

    public void removeModule( String module )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void addDependency( MavenProject project, boolean overwrite )
    {
        // TBD: CHECK FOR EXISTING DATA

        Xpp3DomMap dep = new Xpp3DomMap( "dependency" );
        dep.putValue( "groupId", project.getGroupId() );
        dep.putValue( "artifactId", project.getArtifactId() );
        dep.putValue( "version", project.getVersion() );
        dep.putValue( "scope", Artifact.SCOPE_PROVIDED );

        Xpp3Dom list = new Xpp3DomList( "dependencies" );
        list.addChild( dep );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( pom, newPom );
    }

    public void addDependency( Dependency dependency, boolean overwrite )
    {
        // TBD: CHECK FOR EXISTING DATA + OPTIONAL FIELDS

        Xpp3DomMap dep = new Xpp3DomMap( "dependency" );
        dep.putValue( "groupId", dependency.getGroupId() );
        dep.putValue( "artifactId", dependency.getArtifactId() );
        dep.putValue( "version", dependency.getVersion() );
        dep.putValue( "scope", dependency.getScope() );
        dep.putValue( "type", dependency.getType() );
        dep.putValue( "optional", "" + dependency.isOptional() );

        Xpp3Dom list = new Xpp3DomList( "dependencies" );
        list.addChild( dep );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( pom, newPom );
    }

    public void removeDependency( MavenProject project )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void removeDependency( Dependency dependency )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void write()
        throws MojoExecutionException
    {
        try
        {
            FileWriter writer = new FileWriter( file );

            XmlSerializer serializer = RoundTripXml.createSerializer();

            serializer.setOutput( writer );
            serializer.startDocument( writer.getEncoding(), null );
            pom.writeToSerializer( null, serializer );
            serializer.endDocument();
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to serialize POM " + file, e );
        }
    }

    protected static class Xpp3DomMap extends Xpp3Dom
    {
        public Xpp3DomMap( String name )
        {
            super( name );
        }

        public void putValue( String name, String value )
        {
            Xpp3Dom child = new Xpp3Dom( name );
            child.setValue( value );
            addChild( child );
        }
    }

    protected static class Xpp3DomList extends Xpp3Dom
    {
        public Xpp3DomList( String name )
        {
            super( name );

            setAttribute( CHILDREN_COMBINATION_MODE_ATTRIBUTE, CHILDREN_COMBINATION_APPEND );
        }
    }
}
