package org.ops4j.pax.construct.lifecycle;

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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.eclipse.writers.EclipseClasspathWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseProjectWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseWriterConfig;
import org.apache.maven.plugin.ide.IdeDependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;

/**
 * Extend maven-eclipse-plugin to process multiple OSGi dependencies.
 * 
 * @goal eclipse-dependencies
 */
public class EclipseDependenciesMojo extends EclipseMojo
{
    /**
     * @component role="org.apache.maven.project.MavenProjectBuilder"
     * @required
     * @readonly
     */
    protected MavenProjectBuilder mavenProjectBuilder;

    /**
     * @parameter expression="${excludeTransitive}"
     */
    private boolean excludeTransitive;

    private MavenProject thisProject;

    public boolean setup()
        throws MojoExecutionException
    {
        if( null != thisProject )
        {
            return super.setup();
        }

        thisProject = getExecutedProject();
        setResolveDependencies( false );

        try
        {
            Set artifacts = thisProject.createArtifacts( artifactFactory, null, null );

            if( excludeTransitive )
            {
                for( Iterator i = artifacts.iterator(); i.hasNext(); )
                {
                    artifactResolver.resolve( (Artifact) i.next(), remoteArtifactRepositories, localRepository );
                }
            }
            else if( artifacts.size() > 0 )
            {
                ArtifactResolutionResult result = artifactResolver.resolveTransitively( artifacts, thisProject
                    .getArtifact(), remoteArtifactRepositories, localRepository, artifactMetadataSource );

                artifacts = result.getArtifacts();
            }

            for( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                if( !artifact.isOptional() && "provided".equals( artifact.getScope() ) )
                {
                    String groupId = artifact.getGroupId();
                    String artifactId = artifact.getArtifactId();
                    String version;

                    try
                    {
                        // use symbolic version if available (ie. 1.0.0-SNAPSHOT)
                        version = artifact.getSelectedVersion().toString();
                    }
                    catch( Exception e )
                    {
                        version = artifact.getVersion();
                    }

                    Artifact pomArtifact = artifactFactory.createProjectArtifact( groupId, artifactId, version );

                    MavenProject dependencyProject = mavenProjectBuilder.buildFromRepository( pomArtifact,
                        remoteArtifactRepositories, localRepository );

                    File projectDir = new File( thisProject.getBasedir(), "target/" + groupId );
                    File localDir = new File( projectDir, artifactId + "/" + version );
                    localDir.mkdirs();

                    File pomFile = new File( localDir, "pom.xml" );

                    Writer writer = new FileWriter( pomFile );
                    dependencyProject.writeModel( writer );
                    dependencyProject.setFile( pomFile );
                    writer.close();

                    setBuildOutputDirectory( new File( localDir, ".ignore" ) );
                    setEclipseProjectDir( localDir );

                    setProject( dependencyProject );
                    setExecutedProject( dependencyProject );

                    unpackBundle( artifact.getFile(), "." );

                    super.execute();
                }
            }
        }
        catch( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }

        return false;
    }

    public void writeConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        try
        {
            EclipseWriterConfig config = createEclipseWriterConfig( new IdeDependency[0] );

            config.setEclipseProjectName( getEclipseProjectName( executedProject, true ) );
            config.getEclipseProjectDirectory().mkdirs();

            new EclipseClasspathWriter().init( getLog(), config ).write();
            new EclipseProjectWriter().init( getLog(), config ).write();
        }
        catch( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }

        try
        {
            List remoteRepos = downloadSources ? remoteArtifactRepositories : Collections.EMPTY_LIST;

            Artifact artifact = artifactFactory.createArtifactWithClassifier( executedProject.getGroupId(),
                executedProject.getArtifactId(), executedProject.getVersion(), "java-source", "sources" );

            artifactResolver.resolve( artifact, remoteRepos, localRepository );

            attachSource( artifact.getFile().getPath() );
        }
        catch( Exception e )
        {
            // ignore missing sources
        }
    }

    protected void attachSource( String sourcePath )
    {
        try
        {
            File classPathFile = new File( executedProject.getBasedir(), ".classpath" );
            Xpp3Dom classPathXML = Xpp3DomBuilder.build( new FileReader( classPathFile ) );

            Xpp3Dom classPathEntry = new Xpp3Dom( "classpathentry" );
            classPathEntry.setAttribute( "exported", "true" );
            classPathEntry.setAttribute( "kind", "lib" );
            classPathEntry.setAttribute( "path", "." );
            classPathEntry.setAttribute( "sourcepath", sourcePath );
            classPathXML.addChild( classPathEntry );

            FileWriter writer = new FileWriter( classPathFile );
            Xpp3DomWriter.write( new PrettyPrintXMLWriter( writer ), classPathXML );
            IOUtil.close( writer );
        }
        catch( Exception e )
        {
            // nice to have source, but ignore errors if we can't
        }
    }
}
