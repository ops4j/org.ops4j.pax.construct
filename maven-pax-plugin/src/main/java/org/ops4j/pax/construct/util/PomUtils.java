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
import java.util.List;

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
        public String getId();

        public String getGroupId();

        public String getArtifactId();

        public String getVersion();

        public String getPackaging();

        public List getModules();

        public Pom getContainingPom();

        public Pom getModulePom( String name );

        public File getFile();

        public File getBasedir();

        public boolean isBundleProject();

        public String getBundleSymbolicName();

        public File getPackagedBundle();

        public void setParent( Pom pom, String relativePath, boolean overwrite );

        public void setParent( MavenProject project, String relativePath, boolean overwrite );

        public void adjustRelativePath( int offset );

        public void addRepository( Repository repository, boolean overwrite );

        public void addModule( String module, boolean overwrite );

        public boolean removeModule( String module );

        public void addDependency( Dependency dependency, boolean overwrite );

        public boolean removeDependency( Dependency dependency );

        public void write();
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
    {
        if( here.isDirectory() )
        {
            here = new File( here, "pom.xml" );
        }

        return new XppPom( here, groupId, artifactId );
    }

    public static boolean isBundleProject( MavenProject project )
    {
        return project.getPackaging().indexOf( "bundle" ) >= 0;
    }

    public static String getCompoundName( String groupId, String artifactId )
    {
        if( artifactId.startsWith( groupId + '.' ) || artifactId.equals( groupId ) )
        {
            return artifactId;
        }
        else if( groupId.endsWith( '.' + artifactId ) )
        {
            return groupId;
        }

        return groupId + '.' + artifactId;
    }
}
