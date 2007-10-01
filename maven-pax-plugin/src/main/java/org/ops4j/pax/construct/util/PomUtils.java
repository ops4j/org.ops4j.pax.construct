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
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;

public final class PomUtils
{
    /**
     * Hide constructor for utility class
     */
    private PomUtils()
    {
    }

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
        return isBundleProject( project, null, null, null, false );
    }

    public static boolean isBundleProject( MavenProject project, ArtifactResolver resolver, List remoteRepos,
        ArtifactRepository localRepo, boolean testMetadata )
    {
        String packaging = project.getPackaging();
        if( packaging != null && packaging.indexOf( "bundle" ) >= 0 )
        {
            return true;
        }
        else
        {
            return isBundleArtifact( project.getArtifact(), resolver, remoteRepos, localRepo, testMetadata );
        }
    }

    public static boolean isBundleArtifact( Artifact artifact, ArtifactResolver resolver, List remoteRepos,
        ArtifactRepository localRepo, boolean testMetadata )
    {
        String type = artifact.getType();
        if( null != type && type.indexOf( "bundle" ) >= 0 )
        {
            return true;
        }
        else if( !testMetadata )
        {
            return false;
        }

        try
        {
            if( artifact.getFile() == null || !artifact.getFile().exists() )
            {
                resolver.resolve( artifact, remoteRepos, localRepo );
            }

            JarFile jarFile = new JarFile( artifact.getFile() );
            Manifest manifest = jarFile.getManifest();

            Attributes mainAttributes = manifest.getMainAttributes();

            return mainAttributes.getValue( "Bundle-SymbolicName" ) != null
                || mainAttributes.getValue( "Bundle-Name" ) != null;
        }
        catch( Exception e )
        {
            return false;
        }
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

    public static String getMetaVersion( Artifact artifact )
    {
        try
        {
            // use metaversion if available (ie. 1.0-SNAPSHOT)
            return artifact.getSelectedVersion().toString();
        }
        catch( Exception e )
        {
            return artifact.getVersion();
        }
    }
}
