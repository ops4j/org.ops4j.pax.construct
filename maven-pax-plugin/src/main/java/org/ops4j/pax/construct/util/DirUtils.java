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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.ops4j.pax.construct.util.PomUtils.Pom;

public class DirUtils
{
    public static Pom findPom( File baseDir, String pomId )
    {
        if( null == pomId || pomId.length() == 0 )
        {
            return null;
        }

        Pom pom = PomUtils.readPom( baseDir );

        Set visited = new HashSet();
        visited.add( pom.getId() );

        int groupMarker = pomId.indexOf( ':' );

        final String groupId = groupMarker > 0 ? pomId.substring( 0, groupMarker ) : null;
        final String artifactId = pomId.substring( groupMarker + 1 );

        depthFirst: while( null != pom )
        {
            boolean sameName = artifactId.equals( pom.getArtifactId() )
                || artifactId.equals( pom.getBundleSymbolicName() );

            boolean sameGroup = (null == groupId) || groupId.equals( pom.getGroupId() );

            if( sameName && sameGroup )
            {
                return pom;
            }

            for( Iterator i = pom.getModules().iterator(); i.hasNext(); )
            {
                Pom subPom = pom.getModulePom( (String) i.next() );

                if( visited.add( subPom.getId() ) )
                {
                    pom = subPom;
                    continue depthFirst;
                }
            }

            pom = pom.getContainingPom();
        }

        return null;
    }

    public static Pom createModuleTree( File baseDir, File targetDir )
    {
        File pomFile = new File( targetDir, "pom.xml" );
        if( pomFile.exists() )
        {
            return PomUtils.readPom( pomFile );
        }

        String[] pivot = calculateRelativePath( baseDir, targetDir );
        if( null == pivot )
        {
            return null;
        }

        File commonDir = new File( pivot[1] );
        String descentPath = pivot[2];

        Pom parentPom = PomUtils.readPom( commonDir );
        Pom childPom = parentPom;

        for( int i = 0, j = descentPath.indexOf( '/' ); j >= 0; i = j + 1, j = descentPath.indexOf( '/', i ) )
        {
            pomFile = new File( commonDir, descentPath.substring( 0, j ) + "/pom.xml" );

            if( pomFile.exists() )
            {
                childPom = PomUtils.readPom( pomFile );
            }
            else
            {
                String module = descentPath.substring( i, j );

                parentPom.addModule( module, true );
                parentPom.write();

                String groupId = PomUtils.getCompoundName( parentPom.getGroupId(), parentPom.getArtifactId() );

                childPom = PomUtils.createPom( pomFile, groupId, module );
                childPom.setParent( parentPom, null, true );
                childPom.write();
            }

            parentPom = childPom;
        }

        return childPom;
    }

    public static String[] calculateRelativePath( File baseDir, File targetDir )
    {
        try
        {
            baseDir = baseDir.getCanonicalFile();
            targetDir = targetDir.getCanonicalFile();
        }
        catch( Exception e )
        {
            return null;
        }

        StringBuffer dottedPath = new StringBuffer();
        StringBuffer descentPath = new StringBuffer();

        while( !baseDir.equals( targetDir ) )
        {
            if( baseDir.getPath().length() < targetDir.getPath().length() )
            {
                descentPath.insert( 0, targetDir.getName() + '/' );
                targetDir = targetDir.getParentFile();
            }
            else
            {
                dottedPath.append( "../" );
                baseDir = baseDir.getParentFile();
            }

            if( null == baseDir || null == targetDir )
            {
                return null;
            }
        }

        return new String[]
        {
            dottedPath.toString(), targetDir.getPath(), descentPath.toString()
        };
    }

    public static String rebasePaths( String path, String baseDir, char pathSeparator )
    {
        String[] entries = path.split( Character.toString( pathSeparator ) );

        StringBuffer rebasedPath = new StringBuffer();
        for( int i = 0; i < entries.length; i++ )
        {
            if( i > 0 )
            {
                rebasedPath.append( pathSeparator );
            }

            if( ".".equals( entries[i] ) )
            {
                rebasedPath.append( baseDir );
            }
            else
            {
                rebasedPath.append( baseDir );
                rebasedPath.append( '/' );
                rebasedPath.append( entries[i] );
            }
        }

        return rebasedPath.toString();
    }

    public static List expandBundleClassPath( List classpath, ArchiverManager archiverManager, File tempDir )
    {
        List expandedElements = new ArrayList();

        for( Iterator i = classpath.iterator(); i.hasNext(); )
        {
            File element = new File( (String) i.next() );
            expandedElements.add( element.getPath() );
            Manifest manifest;

            try
            {
                Pom reactorPom = PomUtils.readPom( element.getParentFile().getParentFile() );
                if( reactorPom.isBundleProject() )
                {
                    element = reactorPom.getPackagedBundle();
                }

                JarFile jar = new JarFile( element );
                manifest = jar.getManifest();
            }
            catch( Exception e )
            {
                continue;
            }

            Attributes mainAttributes = manifest.getMainAttributes();
            String bundleClassPath = mainAttributes.getValue( "Bundle-ClassPath" );

            if( null != bundleClassPath && bundleClassPath.length() > 1 )
            {
                File here = new File( tempDir, element.getName() );

                try
                {
                    UnArchiver unArchiver = archiverManager.getUnArchiver( element );

                    here.mkdirs();
                    unArchiver.setDestDirectory( here );
                    unArchiver.setSourceFile( element );
                    unArchiver.extract();
                }
                catch( Exception e )
                {
                    continue;
                }

                String rebasedClassPath = DirUtils.rebasePaths( bundleClassPath, here.getPath(), ',' );
                expandedElements.addAll( Arrays.asList( rebasedClassPath.split( "," ) ) );
            }
        }

        return expandedElements;
    }
}
