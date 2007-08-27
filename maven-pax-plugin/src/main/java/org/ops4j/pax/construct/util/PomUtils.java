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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

public final class PomUtils
{
    public interface Pom
    {
        public void setParent( MavenProject project, String relativePath, boolean overwrite )
            throws MojoExecutionException;

        public void adjustRelativePath( int offset );

        public void addRepository( Repository repository, boolean overwrite );

        public void addModule( String module, boolean overwrite );

        public void removeModule( String module );

        public void addDependency( MavenProject project, boolean overwrite );

        public void addDependency( Dependency dependency, boolean overwrite );

        public void removeDependency( MavenProject project );

        public void removeDependency( Dependency dependency );

        public void write()
            throws MojoExecutionException;
    }

    public static Pom readPom( File pomFile )
        throws MojoExecutionException
    {
        return new XppPom( pomFile );
    }

    public static boolean isBundleProject( MavenProject project )
    {
        return project.getPackaging().indexOf( "bundle" ) >= 0;
    }

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
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public static String calculateRelativePath( File baseDir, File targetDir )
    {
        try
        {
            baseDir = baseDir.getCanonicalFile();
            targetDir = targetDir.getCanonicalFile();
        }
        catch( Exception e )
        {
            // ignore, assume original paths will be ok
        }

        String dottedPath = "";
        String descentPath = "";
        while( baseDir != null && targetDir != null && !baseDir.equals( targetDir ) )
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
        }

        String relativePath = null;
        if( baseDir != null && targetDir != null )
        {
            relativePath = dottedPath + descentPath;
        }

        return relativePath;
    }

    protected static boolean contains( File baseDir, File targetDir )
    {
        for( ; targetDir != null; targetDir = targetDir.getParentFile() )
        {
            if( targetDir.equals( baseDir ) )
            {
                return true;
            }
        }
        return false;
    }
}
