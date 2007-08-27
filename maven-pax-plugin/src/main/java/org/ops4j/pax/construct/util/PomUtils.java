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
import org.apache.maven.project.MavenProject;

public final class PomUtils
{
    public interface Pom
    {
        public void setParent( MavenProject parent, String relativePath, boolean overwrite );

        public void adjustRelativePath( int offset );

        public void addRepository( Repository repository, boolean overwrite );

        public void addModule( String module, boolean overwrite );

        public void removeModule( String module );

        public void addDependency( MavenProject project, boolean overwrite );

        public void addDependency( Dependency dependency, boolean overwrite );

        public void removeDependency( MavenProject project );

        public void removeDependency( Dependency dependency );

        public void write();
    }

    public static boolean isBundleProject( MavenProject project )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public static Pom readPom( File pomFile )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
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
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }
}
