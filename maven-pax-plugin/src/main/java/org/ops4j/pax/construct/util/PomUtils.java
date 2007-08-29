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

public class PomUtils
{
    public static class PomException extends RuntimeException
    {
        static final long serialVersionUID = 1L;

        public PomException( String message )
        {
            super( message );
        }

        public PomException( String message, Throwable cause )
        {
            super( message, cause );
        }
    }

    public interface Pom
    {
        public String getGroupId();

        public String getArtifactId();

        public String getVersion();

        public void setParent( Pom pom, String relativePath, boolean overwrite )
            throws PomException;

        public void setParent( MavenProject project, String relativePath, boolean overwrite )
            throws PomException;

        public void adjustRelativePath( int offset );

        public void addRepository( Repository repository, boolean overwrite )
            throws PomException;

        public void addModule( String module, boolean overwrite )
            throws PomException;

        public void removeModule( String module )
            throws PomException;

        public void addDependency( MavenProject project, boolean overwrite )
            throws PomException;

        public void addDependency( Dependency dependency, boolean overwrite )
            throws PomException;

        public void removeDependency( MavenProject project )
            throws PomException;

        public void removeDependency( Dependency dependency )
            throws PomException;

        public void write()
            throws PomException;
    }

    public static Pom readPom( File here )
    {
        if( here.isDirectory() )
        {
            here = new File( here, "pom.xml" );
        }

        return new XppPom( here );
    }

    public static Pom createPom( File here, String groupId, String artifactId )
        throws PomException
    {
        if( here.isDirectory() )
        {
            here = new File( here, "pom.xml" );
        }

        if( here.exists() )
        {
            return new XppPom( here );
        }
        else
        {
            return new XppPom( here, groupId, artifactId );
        }
    }

    public static boolean isBundleProject( MavenProject project )
    {
        return project.getPackaging().indexOf( "bundle" ) >= 0;
    }
}
