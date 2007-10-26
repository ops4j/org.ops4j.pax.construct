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

import org.apache.maven.archetype.FileUtils;
import org.apache.maven.archetype.FilteringCopier;
import org.apache.maven.archetype.model.ArchetypeModel;
import org.apache.maven.archetype.model.io.xpp3.ArchetypeXpp3Writer;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.XmlStreamWriter;

/**
 * Support creation of simple archetype fragments consisting of just source code or resources
 */
public class ArchetypeFragment
{
    private ArchetypeModel m_model;

    private File m_tempDir;

    public ArchetypeFragment( File tempDir )
    {
        m_model = new ArchetypeModel();
        m_model.setAllowPartial( true );

        m_tempDir = tempDir;
    }

    static String[] getFilenames( File dir )
    {
        DirectoryScanner scanner = new DirectoryScanner();

        scanner.addDefaultExcludes();
        scanner.setFollowSymlinks( false );
        scanner.setBasedir( dir );
        scanner.scan();

        return scanner.getIncludedFiles();
    }

    public void addSources( File projectDir, String sourceDir, String namespace, boolean isTest )
        throws MojoExecutionException
    {
        File from = new File( new File( projectDir, sourceDir ), namespace.replace( '.', '/' ) );
        File to = new File( m_tempDir, "archetype-resources/" + sourceDir );

        try
        {
            FileUtils.copyDirectoryStructure( from, to, new FilteringCopier( namespace, "${package}" ) );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error copying sources", e );
        }

        String[] sourceFiles = getFilenames( to );
        for( int i = 0; i < sourceFiles.length; i++ )
        {
            if( isTest )
            {
                m_model.addTestSource( sourceFiles[i] );
            }
            else
            {
                m_model.addSource( sourceFiles[i] );
            }
        }
    }

    public void addResources( File projectDir, String resourceDir, String namespace, boolean isTest )
        throws MojoExecutionException
    {
        File from = new File( projectDir, resourceDir );
        File to = new File( m_tempDir, "archetype-resources/" + resourceDir );

        try
        {
            FileUtils.copyDirectoryStructure( from, to, new FilteringCopier( namespace, "${package}" ) );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error copying resources", e );
        }

        String[] resourceFiles = getFilenames( to );
        for( int i = 0; i < resourceFiles.length; i++ )
        {
            if( isTest )
            {
                m_model.addTestResource( resourceFiles[i] );
            }
            else
            {
                m_model.addResource( resourceFiles[i] );
            }
        }
    }

    public void install( Artifact artifact, Archiver archiver, ArtifactInstaller installer, ArtifactRepository repo )
        throws MojoExecutionException
    {
        m_model.setId( artifact.getGroupId() + '.' + artifact.getArtifactId() );
        File modelFile = new File( m_tempDir, "META-INF/archetype.xml" );
        modelFile.getParentFile().mkdirs();

        try
        {
            XmlStreamWriter writer = WriterFactory.newXmlWriter( modelFile );
            new ArchetypeXpp3Writer().write( writer, m_model );
            IOUtil.close( writer );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error saving archetype model", e );
        }

        File jarFile = new File( m_tempDir.getParentFile(), m_tempDir.getName() + ".jar" );

        try
        {
            archiver.setDestFile( jarFile );
            archiver.setIncludeEmptyDirs( false );
            archiver.addDirectory( m_tempDir );
            archiver.createArchive();
        }
        catch( ArchiverException e )
        {
            throw new MojoExecutionException( "Unable to archive archetype directory", e );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error archiving archetype directory", e );
        }

        try
        {
            installer.install( jarFile, artifact, repo );
            jarFile.delete();
        }
        catch( ArtifactInstallationException e )
        {
            throw new MojoExecutionException( "Unable to install archetype in local repository", e );
        }

        try
        {
            FileUtils.deleteDirectory( m_tempDir );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Unable to remove archetype files", e );
        }
    }
}
