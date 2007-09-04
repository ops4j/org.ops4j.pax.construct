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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.ops4j.pax.construct.util.PomUtils.Pom;

public class DirUtils
{
    public static Pom findPom( File baseDir, String artifactId )
    {
        if( null == artifactId || artifactId.trim().length() == 0 )
        {
            return null;
        }

        Set visited = new HashSet();

        Pom pom = PomUtils.readPom( baseDir );
        visited.add( pom.getId() );

        depthFirst: while( null != pom )
        {
            if( artifactId.equals( pom.getArtifactId() ) )
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
        if( null == pivot || pivot[2].length() == 0 )
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
}
