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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;
import org.ops4j.pax.construct.util.StreamFactory;

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

        // primary Java package
        m_namespace = namespace;

        // unique scratch directory for the fragment assembly
        m_tempDir = new File( tempDir, "fragment" + ( m_fragmentCount++ ) );
    }

    /**
     * Add primary Maven project POM
     * 
     * @param projectDir project directory
     * @param pom customized Maven project model
     */
    public void addPom( File projectDir, Pom pom )
    {
        File to = new File( m_tempDir, "archetype-resources" );

        File pomFile;
        if( null == pom )
        {
            pomFile = new File( projectDir, "pom.xml" );
        }
        else
        {
            pomFile = pom.getFile();
        }

        // relocate to 'classic' archetype location
        translateFile( pomFile, new File( to, "pom.xml" ) );
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
        String[] pivot = DirUtils.calculateRelativePath( projectDir, new File( path ) );
        if( null == pivot || pivot[2].length() == 0 )
        {
            return;
        }

        // use relative path in search
        String sourcePath = pivot[2];

        // primary source location
        String packagePath = sourcePath + m_namespace.replace( '.', '/' ) + '/';

        File to = new File( m_tempDir, "archetype-resources" );
        for( Iterator i = getFilenames( projectDir, sourcePath, null, null ).iterator(); i.hasNext(); )
        {
            String filename = (String) i.next();

            // relocate to 'classic' archetype location (primary package gets trimmed)
            String target = StringUtils.replace( filename, packagePath, sourcePath );
            translateFile( new File( projectDir, filename ), new File( to, target ) );

            if( target.equals( filename ) )
            {
                // not primary package, so treat as resource (otherwise would end up with bogus package prefix)
                addResourceEntry( target, isTest );
            }
            else
            {
                addSourceEntry( target, isTest );
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
        addResources( projectDir, path, null, null, isTest );
    }

    /**
     * Add collection of resource files
     * 
     * @param projectDir project directory
     * @param path resource location
     * @param includes list of included names
     * @param excludes list of excluded names
     * @param isTest true if these are test resources, otherwise false
     */
    public void addResources( File projectDir, String path, List includes, List excludes, boolean isTest )
    {
        String[] pivot = DirUtils.calculateRelativePath( projectDir, new File( path ) );
        if( null == pivot )
        {
            return;
        }

        // use relative path in search
        String resourcePath = pivot[2];

        File to = new File( m_tempDir, "archetype-resources" );
        for( Iterator i = getFilenames( projectDir, resourcePath, includes, excludes ).iterator(); i.hasNext(); )
        {
            String filename = (String) i.next();

            // special case for Bnd instructions
            String target = filename;
            if( target.endsWith( ".bnd" ) || target.endsWith( "osgi.bundle" ) )
            {
                target = "osgi.bnd";
            }

            // relocate to 'classic' archetype location
            translateFile( new File( projectDir, filename ), new File( to, target ) );
            addResourceEntry( target, isTest );
        }
    }

    /**
     * Get all filenames for a given location, ignoring Maven build output directories
     * 
     * @param dir project directory
     * @param path location in project
     * @param includes list of included names
     * @param excludes list of excluded names
     * @return list of filenames
     */
    private static List getFilenames( File dir, String path, List includes, List excludes )
    {
        DirectoryScanner scanner = new DirectoryScanner();

        String[] pathExclude = parseFilter( path, excludes, "**/target/" );
        String[] pathInclude = parseFilter( path, includes, "**" );

        scanner.setExcludes( pathExclude );
        scanner.setIncludes( pathInclude );
        scanner.setFollowSymlinks( false );
        scanner.addDefaultExcludes();
        scanner.setBasedir( dir );

        scanner.scan();

        List filenames = new ArrayList();

        String[] includedFiles = scanner.getIncludedFiles();
        for( int i = 0; i < includedFiles.length; i++ )
        {
            // normalize to use the standard Maven file separator (same as UNIX)
            filenames.add( includedFiles[i].replace( File.separatorChar, '/' ) );
        }

        return filenames;
    }

    /**
     * @param path directory path
     * @param filters list of ant-style filters
     * @param defaultFilter default filter
     * @return array of filters for the directory path
     */
    private static String[] parseFilter( String path, List filters, String defaultFilter )
    {
        String[] filterArray;

        if( null == filters || filters.size() == 0 )
        {
            filterArray = new String[1];
            filterArray[0] = path + defaultFilter;
        }
        else
        {
            filterArray = (String[]) filters.toArray( new String[filters.size()] );
            for( int i = 0; i < filterArray.length; i++ )
            {
                filterArray[i] = path + filterArray[i];
            }
        }

        return filterArray;
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
            Writer writer = StreamFactory.newXmlWriter( modelFile );
            new ArchetypeXpp3Writer().write( writer, m_model );
            IOUtil.close( writer );

            repairModel( modelFile );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error saving archetype model", e );
        }

        // mimic directory structure in local repository
        File jarFile = new File( m_tempDir.getParentFile(), repo.pathOf( artifact ) );
        jarFile.getParentFile().mkdirs();

        try
        {
            // archive fragment files
            archiver.setDestFile( jarFile );
            archiver.setIncludeEmptyDirs( false );
            archiver.addDirectory( m_tempDir );
            archiver.createArchive();

            // in case we want to deploy
            artifact.setFile( jarFile );
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

    /**
     * Translate file content to work with Pax-Construct v2 archetype processing
     * 
     * @param from source file
     * @param to destination file
     */
    private void translateFile( File from, File to )
    {
        try
        {
            FileInputStream in = new FileInputStream( from );
            String content = IOUtil.toString( in );
            IOUtil.close( in );

            // protect existing variables from accidental replacement
            content = StringUtils.replace( content, "$", "${dollar}" );
            content = "#set( $dollar = '$' )" + System.getProperty( "line.separator" ) + content;

            // standard archetype translation
            content = StringUtils.replace( content, m_namespace, "${package}" );

            // Pax-Construct v1 => v2 translation
            content = StringUtils.replace( content, "bundle.package", "bundle.namespace" );
            content = StringUtils.replace( content, "jar.groupId", "wrapped.groupId" );
            content = StringUtils.replace( content, "jar.artifactId", "wrapped.artifactId" );
            content = StringUtils.replace( content, "jar.version", "wrapped.version" );

            to.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream( to );
            IOUtil.copy( content, out );
            IOUtil.close( out );
        }
        catch( IOException e1 )
        {
            System.err.println( "WARNING: problem translating " + from );
            try
            {
                // fall-back to basic copy
                FileUtils.copyFile( from, to );
            }
            catch( IOException e2 )
            {
                System.err.println( "WARNING: problem copying " + from );
            }
        }
    }

    /**
     * Repair archetype model, so it works with the old archetype system
     * 
     * @param modelFile model file
     */
    private void repairModel( File modelFile )
    {
        try
        {
            FileInputStream in = new FileInputStream( modelFile );
            String content = IOUtil.toString( in );
            IOUtil.close( in );

            content = StringUtils.replace( content, "<testSource>", "<source>" );
            content = StringUtils.replace( content, "</testSource>", "</source>" );
            content = StringUtils.replace( content, "<testResource>", "<resource>" );
            content = StringUtils.replace( content, "</testResource>", "</resource>" );

            FileOutputStream out = new FileOutputStream( modelFile );
            IOUtil.copy( content, out );
            IOUtil.close( out );
        }
        catch( IOException e )
        {
            System.err.println( "WARNING: problem repairing " + modelFile );
        }
    }
}
