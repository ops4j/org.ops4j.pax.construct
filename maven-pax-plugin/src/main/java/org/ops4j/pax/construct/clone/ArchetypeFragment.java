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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Support creation of simple archetype fragments
 */
public class ArchetypeFragment
{
    private static final int NO_SUCH_FILE = -1;
    private static final int BINARY_FILE = 0;
    private static final int TEXT_FILE = 1;

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
     * Sequence of included filenames
     */
    private List m_includedFiles;

    /**
     * When true, comment out the 'poms' module from any non-root Maven POMs
     */
    private boolean m_unify;

    /**
     * Create a new archetype fragment
     * 
     * @param tempDir some temporary directory
     * @param namespace primary namespace, may be null
     * @param unify set true when unifying Maven projects
     */
    public ArchetypeFragment( File tempDir, String namespace, boolean unify )
    {
        // always allow partial use
        m_model = new ArchetypeModel();
        m_model.setAllowPartial( true );

        // primary Java package
        m_namespace = namespace;

        // unique scratch directory for the fragment assembly
        m_tempDir = new File( tempDir, "fragment" + ( m_fragmentCount++ ) );
        m_includedFiles = new ArrayList();

        m_unify = unify;
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

        File baseDir;
        if( null == pom )
        {
            baseDir = projectDir;
        }
        else
        {
            baseDir = pom.getBasedir();
        }

        // relocate to 'classic' archetype location
        translateFile( baseDir, "pom.xml", to, "pom.xml" );
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
        if( null == pivot || pivot[0].length() > 0 || pivot[2].length() == 0 )
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
            m_includedFiles.add( filename );

            // relocate to 'classic' archetype location (primary package gets trimmed)
            String target = StringUtils.replace( filename, packagePath, sourcePath );
            int status = translateFile( projectDir, filename, to, target );
            if( NO_SUCH_FILE == status )
            {
                continue;
            }
            else if( target.equals( filename ) || BINARY_FILE == status )
            {
                // either no relocation (or no filtering) is required
                addResourceEntry( filename, isTest, TEXT_FILE == status );
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
        if( null == pivot || pivot[0].length() > 0 ) // can handle pivot[2] zero-length
        {
            return;
        }

        // use relative path in search
        String resourcePath = pivot[2];

        File to = new File( m_tempDir, "archetype-resources" );
        for( Iterator i = getFilenames( projectDir, resourcePath, includes, excludes ).iterator(); i.hasNext(); )
        {
            String filename = (String) i.next();
            m_includedFiles.add( filename );

            // special case for Bnd instructions
            String target = filename;
            if( target.endsWith( "details.bnd" ) || target.endsWith( "osgi.bundle" ) )
            {
                target = "osgi.bnd";
            }

            // relocate to 'classic' archetype location
            int status = translateFile( projectDir, filename, to, target );
            if( NO_SUCH_FILE != status )
            {
                addResourceEntry( target, isTest, TEXT_FILE == status );
            }
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

        String[] pathExclude = parseFilter( path, excludes, null );
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
        String[] filterArray = null;

        if( null != filters )
        {
            filterArray = (String[]) filters.toArray( new String[filters.size()] );
            for( int i = 0; i < filterArray.length; i++ )
            {
                filterArray[i] = path + filterArray[i];
            }
        }
        else if( null != defaultFilter )
        {
            filterArray = new String[1];
            filterArray[0] = path + defaultFilter;
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
     * @param isFiltered true if resource shuld be filtered, otherwise false
     */
    private void addResourceEntry( String entry, boolean isTest, boolean isFiltered )
    {
        if( isTest )
        {
            m_model.addTestResource( entry, isFiltered );
        }
        else
        {
            m_model.addResource( entry, isFiltered );
        }
    }

    /**
     * Create archive of archetype fragment
     * 
     * @param fragmentId unique archetype identifier
     * @param archiver Jar archiver
     * @throws MojoExecutionException
     */
    public void createArchive( String fragmentId, Archiver archiver )
        throws MojoExecutionException
    {
        File modelFile = new File( m_tempDir, "META-INF/archetype.xml" );

        try
        {
            m_model.setId( fragmentId );
            m_model.write( modelFile );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error saving archetype model", e );
        }

        File jarFile = new File( m_tempDir.getParentFile(), fragmentId + ".jar" );

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
     * @param text original text
     * @param path mapped file path
     * @return translated text
     */
    private String translateTextFile( String text, String path )
    {
        String content = text;

        // protect special content from accidental replacement
        content = StringUtils.replace( content, "$", "${dollar}" );
        content = StringUtils.replace( content, "#", "${hash}" );

        content = "#set( $dollar = '$' )" + System.getProperty( "line.separator" ) + content;
        content = "#set( $hash = '#' )" + System.getProperty( "line.separator" ) + content;

        // standard archetype translation
        content = StringUtils.replace( content, m_namespace, "${package}" );

        // Pax-Construct v1 => v2 translation
        content = StringUtils.replace( content, "bundle.package", "bundle.namespace" );
        content = StringUtils.replace( content, "jar.groupId", "wrapped.groupId" );
        content = StringUtils.replace( content, "jar.artifactId", "wrapped.artifactId" );
        content = StringUtils.replace( content, "jar.version", "wrapped.version" );

        if( m_unify && path.endsWith( "/pom.xml" ) )
        {
            // when unifying projects we need to comment out the 'poms' modules from contained projects
            content = StringUtils.replace( content, "module>poms</module", "!-- module>poms</module --" );
        }

        return content;
    }

    /**
     * Translate file content to work with Pax-Construct v2 archetype processing
     * 
     * @param fromDir original base directory
     * @param originalPath original path
     * @param toDir target base directory
     * @param mappedPath mapped path
     * @return NO_SUCH_FILE, BINARY_FILE or TEXT_FILE
     */
    private int translateFile( File fromDir, String originalPath, File toDir, String mappedPath )
    {
        File from = new File( fromDir, originalPath );
        FileOutputStream out = null;

        try
        {
            FileInputStream in = new FileInputStream( from );
            byte[] raw = IOUtil.toByteArray( in );
            IOUtil.close( in );

            String text;
            try
            {
                text = IOUtil.toString( raw );
            }
            catch( IOException e )
            {
                text = "";
            }

            if( Arrays.equals( text.getBytes(), raw ) )
            {
                // text files can be mapped to new paths
                File file = new File( toDir, mappedPath );
                file.getParentFile().mkdirs();

                out = new FileOutputStream( file );
                IOUtil.copy( translateTextFile( text, mappedPath ), out );

                return TEXT_FILE;
            }

            // binary files cannot be mapped to new paths
            File file = new File( toDir, originalPath );
            file.getParentFile().mkdirs();

            out = new FileOutputStream( file );
            IOUtil.copy( raw, out );

            return BINARY_FILE;
        }
        catch( IOException e )
        {
            System.err.println( "WARNING: unable to clone " + from );
            return NO_SUCH_FILE;
        }
        finally
        {
            IOUtil.close( out );
        }
    }

    /**
     * @return list of filenames included in this fragment
     */
    public List getIncludedFiles()
    {
        return Collections.unmodifiableList( m_includedFiles );
    }
}
