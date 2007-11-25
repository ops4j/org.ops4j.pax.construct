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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Various utility methods for managing and refactoring directories and paths
 */
public final class DirUtils
{
    /**
     * Hide constructor for utility class
     */
    private DirUtils()
    {
    }

    /**
     * Resolve a file to its unique canonical path - null resolves to the current directory
     * 
     * @param file file, may be null
     * @param ignoreErrors ignore checked exceptions when true
     * @return file representing the canonical path
     */
    public static File resolveFile( File file, boolean ignoreErrors )
    {
        File candidate = file;
        if( null == file )
        {
            candidate = new File( "." );
        }

        try
        {
            candidate = candidate.getCanonicalFile();
        }
        catch( IOException e )
        {
            if( !ignoreErrors )
            {
                throw new RuntimeException( e );
            }
        }

        return candidate;
    }

    /**
     * Search the local project tree for a Maven POM with the given id
     * 
     * @param baseDir directory in the project tree
     * @param pomId either artifactId or groupId:artifactId
     * @return a Maven POM with the given id, null if not found
     */
    public static Pom findPom( File baseDir, String pomId )
    {
        // no searching required
        if( null == pomId || pomId.length() == 0 )
        {
            return null;
        }

        // handle groupId:artifactId:other:stuff
        String[] fields = pomId.split( ":" );

        String groupId;
        String artifactId;

        if( fields.length > 1 )
        {
            groupId = fields[0];
            artifactId = fields[1];
        }
        else
        {
            groupId = null;
            artifactId = pomId;
        }

        for( Iterator i = new PomIterator( baseDir ); i.hasNext(); )
        {
            Pom pom = (Pom) i.next();
            if( sameProject( pom, groupId, artifactId ) )
            {
                return pom;
            }
        }

        return null;
    }

    /**
     * @param pom Maven project model
     * @param groupId optional project group id
     * @param artifactId project artifact id or bundle symbolic name
     * @return true if the project has matching ids, otherwise false
     */
    private static boolean sameProject( Pom pom, String groupId, String artifactId )
    {
        if( ( artifactId.equals( pom.getArtifactId() ) || artifactId.equals( pom.getBundleSymbolicName() ) )
            && ( null == groupId || groupId.equals( pom.getGroupId() ) ) )
        {
            return true;
        }

        return false;
    }

    /**
     * Verify all Maven POMs from the base directory to the target, adding missing POMs as required
     * 
     * @param baseDir base directory
     * @param targetDir target directory
     * @return the Maven project in the target directory
     * @throws IOException
     */
    public static Pom createModuleTree( File baseDir, File targetDir )
        throws IOException
    {
        // shortcut: target directory already has a POM
        File pomFile = new File( targetDir, "pom.xml" );
        if( pomFile.exists() )
        {
            return PomUtils.readPom( pomFile );
        }

        String[] pivot = calculateRelativePath( baseDir, targetDir );
        if( null == pivot )
        {
            // unable to find common parent directory!
            return null;
        }

        File commonDir = new File( pivot[1] );
        String descentPath = pivot[2];

        Pom parentPom = null;
        Pom childPom = null;

        int i = 0;
        int j = -1;

        do
        {
            // check the next module pom...
            String pathSoFar = descentPath.substring( 0, j + 1 );
            pomFile = new File( commonDir, pathSoFar + "pom.xml" );

            if( pomFile.exists() )
            {
                // existing pom, follow it along...
                childPom = PomUtils.readPom( pomFile );
            }
            else if( parentPom != null && "pom".equals( parentPom.getPackaging() ) )
            {
                // no such pom, need to create new module pom
                String module = descentPath.substring( i, j );
                childPom = createMissingPom( parentPom, module, pomFile );
            }
            else
            {
                return null; // bad project structure: cannot add interim module
            }

            // descend to next pom
            parentPom = childPom;

            i = j + 1;
            j = descentPath.indexOf( '/', i );

        } while( j >= 0 );

        // final pom in target directory
        return childPom;
    }

    /**
     * Add missing Maven project POM and attach to the parent project
     * 
     * @param parentPom parent project
     * @param module new project module
     * @param pomFile new project file
     * @return the new Maven POM
     * @throws IOException
     */
    private static Pom createMissingPom( Pom parentPom, String module, File pomFile )
        throws IOException
    {
        // link parent to new module pom
        parentPom.addModule( module, true );
        parentPom.write();

        String groupId = PomUtils.getCompoundId( parentPom.getGroupId(), parentPom.getArtifactId() );
        if( groupId.equals( parentPom.getGroupId() ) )
        {
            groupId += '.' + module;
        }

        // create missing module pom and link back to parent
        Pom childPom = PomUtils.createPom( pomFile, groupId, module );
        childPom.setParent( parentPom, null, true );
        childPom.write();

        return childPom;
    }

    /**
     * Calculate the relative path (and common directory) to get from a base directory to a target directory
     * 
     * @param baseDir base directory
     * @param targetDir target directory
     * @return three strings: a dotted path, absolute location of the common directory, and a descent path
     */
    public static String[] calculateRelativePath( File baseDir, File targetDir )
    {
        // need canonical form for this to work
        File from = resolveFile( baseDir, true );
        File to = resolveFile( targetDir, true );

        StringBuffer dottedPath = new StringBuffer();
        StringBuffer descentPath = new StringBuffer();

        while( !from.equals( to ) )
        {
            // "from" is above "to", so need to descend...
            if( from.getPath().length() < to.getPath().length() )
            {
                descentPath.insert( 0, to.getName() + '/' );
                to = to.getParentFile();
            }
            // otherwise need to move up (ie "..")
            else
            {
                dottedPath.append( "../" );
                from = from.getParentFile();
            }

            // reached top of file-system!
            if( null == from || null == to )
            {
                return null;
            }
        }

        return new String[]
        {
            // both "from" and "to" now hold the common directory
            dottedPath.toString(), to.getPath(), descentPath.toString()
        };
    }

    /**
     * Set the logical parent for a given POM
     * 
     * @param pomFile directory or file containing the pom to update
     * @param parentId either artifactId or groupId:artifactId
     * @return the relative path to the parent, null if it wasn't found
     * @throws IOException
     */
    public static String updateLogicalParent( File pomFile, String parentId )
        throws IOException
    {
        Pom pom = PomUtils.readPom( pomFile );
        File baseDir = pom.getBasedir();

        Pom parentPom = DirUtils.findPom( baseDir, parentId );
        if( null == parentPom )
        {
            return null;
        }

        String[] pivot = DirUtils.calculateRelativePath( baseDir, parentPom.getBasedir() );
        if( null == pivot )
        {
            return null;
        }

        String relativePath = pivot[0] + pivot[2];

        // (re-)attach project to its logical parent
        pom.setParent( parentPom, relativePath, true );
        pom.write();

        return relativePath;
    }

    /**
     * Refactor path string, adding base directory to all entries
     * 
     * @param path path to refactor
     * @param baseDir base directory to add
     * @param pathSeparator path separator
     * @return the refactored path
     */
    public static String rebasePaths( String path, String baseDir, char pathSeparator )
    {
        if( null == path )
        {
            return baseDir;
        }

        String[] entries = path.split( Character.toString( pathSeparator ) );

        StringBuffer rebasedPath = new StringBuffer();
        for( int i = 0; i < entries.length; i++ )
        {
            String pathEntry = entries[i].trim();

            if( i > 0 )
            {
                rebasedPath.append( pathSeparator );
            }

            rebasedPath.append( baseDir );
            if( !".".equals( pathEntry ) )
            {
                rebasedPath.append( '/' );
                rebasedPath.append( pathEntry );
            }
        }

        return rebasedPath.toString();
    }

    /**
     * Expand any bundle entries on the classpath to include embedded jars, etc.
     * 
     * @param outputDir current output directory
     * @param path list of classpath elements
     * @param tempDir temporary directory for unpacking
     * @return expanded classpath
     */
    public static List expandOSGiClassPath( File outputDir, List path, File tempDir )
    {
        List expandedPath = new ArrayList();

        for( Iterator i = path.iterator(); i.hasNext(); )
        {
            File element = new File( (String) i.next() );
            if( element.equals( outputDir ) )
            {
                // don't expand the current project
                expandedPath.add( element.getPath() );
            }
            else
            {
                expandedPath.addAll( expandBundleClassPath( element, tempDir ) );
            }
        }

        return expandedPath;
    }

    /**
     * Expand compilatation classpath element to include extra entries for compiling against OSGi bundles
     * 
     * @param element compilatation classpath element
     * @param tempDir temporary directory for unpacking
     * @return expanded classpath elements
     */
    private static List expandBundleClassPath( File element, File tempDir )
    {
        File bundle = locateBundle( element );
        if( bundle != null && bundle.isFile() )
        {
            String bundleClassPath = extractBundleClassPath( bundle );
            File unpackDir = new File( tempDir, bundle.getName() );

            return unpackEmbeddedEntries( bundle, unpackDir, bundleClassPath );
        }

        return Collections.singletonList( element.getPath() );
    }

    /**
     * Locate the actual bundle for the given classpath element
     * 
     * @param classpathElement classpath element, may be myProject/target/classes
     * @return the final bundle, null if it hasn't been built yet
     */
    private static File locateBundle( File classpathElement )
    {
        // assume standard output directory, ie. target/classes
        String outputDir = "target" + File.separator + "classes";
        String path = classpathElement.getPath();

        if( path.endsWith( outputDir ) )
        {
            // we need the final bundle, not the output directory, so load up the project POM
            File projectDir = new File( path.substring( 0, path.length() - outputDir.length() ) );

            try
            {
                Pom reactorPom = PomUtils.readPom( projectDir );
                String artifactId = reactorPom.getArtifactId();
                String version = reactorPom.getVersion();

                return new File( projectDir, "target/" + artifactId + '-' + version + ".jar" );
            }
            catch( IOException e )
            {
                return null;
            }
        }

        // otherwise just assume it's a bundle for now (will check for manifest later)
        return classpathElement;
    }

    /**
     * @param bundle jarfile
     * @return Bundle-ClassPath
     */
    private static String extractBundleClassPath( File bundle )
    {
        String bundleClassPath = null;

        try
        {
            Manifest manifest = new JarFile( bundle ).getManifest();
            if( null != manifest )
            {
                Attributes mainAttributes = manifest.getMainAttributes();
                bundleClassPath = mainAttributes.getValue( "Bundle-ClassPath" );
            }
        }
        catch( IOException e )
        {
            System.err.println( "WARNING: unable to read jarfile " + bundle );
        }

        if( bundleClassPath != null )
        {
            return bundleClassPath;
        }
        else
        {
            return ".";
        }
    }

    /**
     * @param bundle jarfile
     * @param here unpack directory
     * @return true if bundle was successfully unpacked
     */
    public static boolean unpackBundle( File bundle, File here )
    {
        try
        {
            // improves unpacking performance
            FileUtils.deleteDirectory( here );
            unpackZip( bundle, here, null );

            return true;
        }
        catch( IOException e )
        {
            return false;
        }
    }

    /**
     * Simple Zip unpacking code, supports selected extraction of entries
     * 
     * @param bundle zipfile
     * @param here unpack directory
     * @param prefix unpack entries that start with the prefix
     * @throws IOException
     */
    private static void unpackZip( File bundle, File here, String prefix )
        throws IOException
    {
        ZipFile zipFile = new ZipFile( bundle );

        try
        {
            for( Enumeration e = zipFile.entries(); e.hasMoreElements(); )
            {
                ZipEntry entry = (ZipEntry) e.nextElement();
                String name = entry.getName();

                // don't bother with plain folders, as we always create them on-demand
                if( !entry.isDirectory() && ( prefix == null || name.startsWith( prefix ) ) )
                {
                    // place unpacked file underneath target folder
                    File file = FileUtils.resolveFile( here, name );
                    file.getParentFile().mkdirs();

                    InputStream in = zipFile.getInputStream( entry );
                    OutputStream out = new FileOutputStream( file );

                    try
                    {
                        // unpack contents
                        IOUtil.copy( in, out );
                    }
                    finally
                    {
                        IOUtil.close( out );
                        IOUtil.close( in );
                    }
                }
            }
        }
        finally
        {
            zipFile.close();
        }
    }

    /**
     * @param bundle jarfile
     * @param here unpack directory
     * @param bundleClassPath Bundle-ClassPath attribute
     * @return list of paths pointing to unpacked entries
     */
    private static List unpackEmbeddedEntries( File bundle, File here, String bundleClassPath )
    {
        try
        {
            // improves unpacking performance
            FileUtils.deleteDirectory( here );
        }
        catch( IOException e )
        {
            return Collections.singletonList( bundle.getPath() );
        }

        List pathList = new ArrayList();
        String pathPrefix = here.getPath();

        String[] entries = bundleClassPath.split( "," );
        for( int i = 0; i < entries.length; i++ )
        {
            String path = entries[i].trim();
            if( path.length() == 0 )
            {
                continue;
            }
            else if( ".".equals( path ) )
            {
                // no need to unpack, just use jar
                pathList.add( bundle.getPath() );
            }
            else
            {
                try
                {
                    // unpack embedded folder/jar
                    unpackZip( bundle, here, path );
                    pathList.add( pathPrefix + '/' + path );
                }
                catch( IOException e )
                {
                    continue;
                }
            }
        }

        return pathList;
    }

    /**
     * Recursively delete (prune) all empty directories underneath the base directory
     * 
     * @param baseDir base directory
     */
    public static void pruneEmptyFolders( File baseDir )
    {
        List candidates = new ArrayList();
        candidates.add( baseDir );

        List prunable = new ArrayList();
        while( !candidates.isEmpty() )
        {
            File f = (File) candidates.remove( 0 );
            if( f.isDirectory() )
            {
                File[] files = f.listFiles();
                candidates.addAll( Arrays.asList( files ) );
                prunable.add( f );
            }
        }

        // delete must go in reverse
        Collections.reverse( prunable );

        for( Iterator i = prunable.iterator(); i.hasNext(); )
        {
            // only deletes empty directories
            File directory = (File) i.next();
            directory.delete();
        }
    }
}
