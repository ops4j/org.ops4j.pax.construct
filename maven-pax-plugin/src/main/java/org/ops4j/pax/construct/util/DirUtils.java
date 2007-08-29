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
import java.io.IOException;

import org.apache.maven.project.MavenProject;
import org.ops4j.pax.construct.util.PomUtils.Pom;

public class DirUtils
{
    public static MavenProject findPom( MavenProject project, String artifactId )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public static MavenProject findModule( MavenProject project, String module )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
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
            parentPom.addModule( descentPath.substring( i, j ), true );
            parentPom.write();

            File subDir = new File( commonDir, descentPath.substring( 0, j ) );

            // FIXME: make compoundName method global and use it here
            String groupId = parentPom.getGroupId() + "." + parentPom.getArtifactId();
            childPom = PomUtils.createPom( subDir, groupId, subDir.getName() );
            childPom.setParent( parentPom, null, true );
            childPom.write();

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
        catch( IOException e )
        {
            return null;
        }

        String dottedPath = "";
        String descentPath = "";

        while( !baseDir.equals( targetDir ) )
        {
            if( baseDir.getPath().length() < targetDir.getPath().length() )
            {
                descentPath = targetDir.getName() + "/" + descentPath;
                targetDir = targetDir.getParentFile();
            }
            else
            {
                dottedPath = "../" + dottedPath;
                baseDir = baseDir.getParentFile();
            }

            if( null == baseDir || null == targetDir )
            {
                return null;
            }
        }

        return new String[]
        {
            dottedPath, targetDir.getPath(), descentPath
        };
    }
}
