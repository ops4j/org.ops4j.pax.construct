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

import org.apache.maven.archetype.Copier;
import org.apache.maven.archetype.FileUtils;
import org.apache.maven.archetype.FilteringCopier;
import org.apache.maven.archetype.StandardCopier;
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
import org.ops4j.pax.construct.util.DirUtils;

/**
 * Support creation of simple archetype fragments
 */
public class ArchetypeFragment
{
    /**
     * Simple counter to keep fragments unique
     */
    private static int m_fragmentCount;

    /**
     * Current archetype model
     */
    private ArchetypeModel m_model;

    /**
     * Primary namespace for the archetype
     */
    private String m_namespace;

    /**
     * Filter to replace primary namespace with property
     */
    private Copier m_filter;

    /**
     * Temporary directory for this fragment
     */
    private File m_tempDir;

    /**
     * Create a new archetype fragment
     * 
     * @param tempDir some temporary directory
     * @param namespace primary namespace, may be null
     */
    public ArchetypeFragment( File tempDir, String namespace )
    {
        // always allow partial use
        m_model = new ArchetypeModel();
        m_model.setAllowPartial( true );

        m_namespace = namespace;
        if( null == m_namespace )
        {
            // nothing to replace
            m_filter = new StandardCopier();
        }
        else
        {
            m_filter = new FilteringCopier( m_namespace, "${package}" );
        }

        // unique scratch directory for the fragment assembly
        m_tempDir = new File( tempDir, "fragment" + ( m_fragmentCount++ ) );
    }

    /**
     * Add primary Maven project POM
     * 
     * @param projectDir project directory
     */
    public void addPom( File projectDir )
    {
        File to = new File( m_tempDir, "archetype-resources" );

        try
        {
            // relocate to 'classic' archetype location
            FileUtils.copyFile( new File( projectDir, "pom.xml" ), new File( to, "pom.xml" ), m_filter );
        }
        catch( IOException e )
        {
            System.err.println( "I/O error copying " + projectDir + "/pom.xml" );
        }
    }

    /**
     * Add collection of source files
     * 
     * @param projectDir project directory
     * @param path source location
     * @param isTest true if these are test sources, otherwise false
     */
    public void addSources( File projectDir, String path, boolean isTest )
    {
        // make relative to base
        String sourcePath = path;
        String[] pivot = DirUtils.calculateRelativePath( projectDir, new File( path ) );
        if( null != pivot )
        {
            sourcePath = pivot[2];
        }

        // primary source location
        String packagePath = sourcePath + m_namespace.replace( '.', '/' ) + '/';

        File to = new File( m_tempDir, "archetype-resources" );
        String[] sources = getFilenames( projectDir, sourcePath );
        for( int i = 0; i < sources.length; i++ )
        {
            try
            {
                // relocate to 'classic' archetype location (primary package gets trimmed)
                String target = sources[i].replaceFirst( packagePath, sourcePath );

                FileUtils.copyFile( new File( projectDir, sources[i] ), new File( to, target ), m_filter );
                if( target.equals( sources[i] ) )
                {
                    // not primary package, so treat as resource (otherwise would end up with bogus package prefix)
                    addResourceEntry( target, isTest );
                }
                else
                {
                    addSourceEntry( target, isTest );
                }
            }
            catch( IOException e )
            {
                System.err.println( "I/O error copying source " + sources[i] );
            }
        }
    }

    /**
     * Add collection of resource files
     * 
     * @param projectDir project directory
     * @param path resource location
     * @param isTest true if these are test resources, otherwise false
     */
    public void addResources( File projectDir, String path, boolean isTest )
    {
        // make relative to base
        String resourcePath = path;
        String[] pivot = DirUtils.calculateRelativePath( projectDir, new File( path ) );
        if( null != pivot )
        {
            resourcePath = pivot[2];
        }

        File to = new File( m_tempDir, "archetype-resources" );
        String[] resources = getFilenames( projectDir, resourcePath );
        for( int i = 0; i < resources.length; i++ )
        {
            try
            {
                // special case for Bnd instructions
                String target = resources[i];
                if( target.endsWith( ".bnd" ) || target.endsWith( "osgi.bundle" ) )
                {
                    target = "osgi.bnd";
                }

                // relocate to 'classic' archetype location
                FileUtils.copyFile( new File( projectDir, resources[i] ), new File( to, target ), m_filter );
                addResourceEntry( target, isTest );
            }
            catch( IOException e )
            {
                System.err.println( "I/O error copying resource " + resources[i] );
            }
        }
    }

    /**
     * Get all filenames for a given location, ignoring Maven build output directories
     * 
     * @param dir project directory
     * @param path location in project
     * @return array of filenames
     */
    private static String[] getFilenames( File dir, String path )
    {
        DirectoryScanner scanner = new DirectoryScanner();

        String[] pathExclude = new String[1];
        String[] pathInclude = new String[1];

        // exclude standard output dir
        pathExclude[0] = "**/target/";

        // include location files
        pathInclude[0] = path + "**";

        scanner.setExcludes( pathExclude );
        scanner.setIncludes( pathInclude );
        scanner.setFollowSymlinks( false );
        scanner.addDefaultExcludes();
        scanner.setBasedir( dir );

        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * Add a source file to this fragment
     * 
     * @param entry source file entry
     * @param isTest true if this is a test source, otherwise false
     */
    private void addSourceEntry( String entry, boolean isTest )
    {
        if( isTest )
        {
            m_model.addTestSource( entry );
        }
        else
        {
            m_model.addSource( entry );
        }
    }

    /**
     * Add a resource file to this fragment
     * 
     * @param entry resource file entry
     * @param isTest true if this is a test resource, otherwise false
     */
    private void addResourceEntry( String entry, boolean isTest )
    {
        if( isTest )
        {
            m_model.addTestResource( entry );
        }
        else
        {
            m_model.addResource( entry );
        }
    }

    /**
     * Install archetype fragment to the local repository under the given build artifact
     * 
     * @param artifact build artifact
     * @param archiver Jar archiver
     * @param installer Maven artifact installer
     * @param repo local Maven repository
     * @throws MojoExecutionException
     */
    public void install( Artifact artifact, Archiver archiver, ArtifactInstaller installer, ArtifactRepository repo )
        throws MojoExecutionException
    {
        m_model.setId( artifact.getGroupId() + '.' + artifact.getArtifactId() );
        File modelFile = new File( m_tempDir, "META-INF/archetype.xml" );
        modelFile.getParentFile().mkdirs();

        try
        {
            // write 'classic' archetype model
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
            // archive fragment files
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
            // install fragment to local repository
            installer.install( jarFile, artifact, repo );
            jarFile.delete();
        }
        catch( ArtifactInstallationException e )
        {
            throw new MojoExecutionException( "Unable to install archetype in local repository", e );
        }

        try
        {
            // clean-up after ourselves...
            FileUtils.deleteDirectory( m_tempDir );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Unable to remove archetype files", e );
        }
    }
}
