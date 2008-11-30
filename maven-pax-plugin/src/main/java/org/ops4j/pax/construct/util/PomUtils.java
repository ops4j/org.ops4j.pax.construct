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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Provide API {@link Pom} and factory for editing Maven project files
 */
public final class PomUtils
{
    /**
     * Hide constructor for utility class
     */
    private PomUtils()
    {
        /*
         * nothing to do
         */
    }

    /**
     * API for editing Maven project files
     */
    public interface Pom
    {
        /**
         * @return unique project identifier
         */
        String getId();

        /**
         * @return parents' unique project identifier
         */
        String getParentId();

        /**
         * @return project group id
         */
        String getGroupId();

        /**
         * @return project artifact id
         */
        String getArtifactId();

        /**
         * @return project version
         */
        String getVersion();

        /**
         * @return project packaging
         */
        String getPackaging();

        /**
         * @return names of modules contained in this project
         */
        List getModuleNames();

        /**
         * @return the physical parent project
         */
        Pom getContainingPom();

        /**
         * @param module name of a module in this project
         * @return the module POM, null if it doesn't exist
         */
        Pom getModulePom( String module );

        /**
         * @return the underlying Maven POM file
         */
        File getFile();

        /**
         * @return the directory containing this Maven project
         */
        File getBasedir();

        /**
         * @return true if this is an OSGi bundle project, otherwise false
         */
        boolean isBundleProject();

        /**
         * @return the symbolic name for this project, null if it doesn't define one
         */
        String getBundleSymbolicName();

        /**
         * @param pom the new logical parent project
         * @param relativePath the relative path from this POM to its new parent
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        void setParent( Pom pom, String relativePath, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param project the new logical parent project
         * @param relativePath the relative path from this POM to the parent
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        void setParent( MavenProject project, String relativePath, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param newGroupId the new project group id
         */
        void setGroupId( String newGroupId );

        /**
         * @param newVersion the new project version
         */
        void setVersion( String newVersion );

        /**
         * @param repository a Maven repository
         * @param snapshots enable snapshots for this repository
         * @param releases enable releases for this repository
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @param pluginRepo treat as plugin repository if true, otherwise assume normal repository
         * @throws ExistingElementException
         */
        void addRepository( Repository repository, boolean snapshots, boolean releases, boolean overwrite,
            boolean pluginRepo )
            throws ExistingElementException;

        /**
         * @param module module name
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        void addModule( String module, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param module module name
         * @return true if module was removed from the project, otherwise false
         */
        boolean removeModule( String module );

        /**
         * @param dependency project dependency
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        void addDependency( Dependency dependency, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param dependency project dependency
         * @param newGroupId updated dependency group id
         * @return true if the dependency was updated
         */
        boolean updateDependencyGroup( Dependency dependency, String newGroupId );

        /**
         * @param dependency project dependency
         * @return true if dependency was removed from the project, otherwise false
         */
        boolean removeDependency( Dependency dependency );

        /**
         * @param groupId dependency exclusion group id
         * @param artifactId dependency exclusion artifact id
         * @param overwrite overwrite element if true, otherwise throw {@link ExistingElementException}
         * @throws ExistingElementException
         */
        void addExclusion( String groupId, String artifactId, boolean overwrite )
            throws ExistingElementException;

        /**
         * @param groupId dependency exclusion group id
         * @param artifactId dependency exclusion artifact id
         * @return true if dependency exclusion was removed from the project, otherwise false
         */
        boolean removeExclusion( String groupId, String artifactId );

        /**
         * @return properties defined by the current project
         */
        Properties getProperties();

        /**
         * @param key property key
         * @param value property value
         */
        void setProperty( String key, String value );

        /**
         * @param groupId plugin group id
         * @param artifactId plugin artifact id
         * @param newVersion new plugin version
         * @return true if the plugin was updated
         */
        boolean updatePluginVersion( String groupId, String artifactId, String newVersion );

        /**
         * Merge a section of XML from another Maven project POM
         * 
         * @param pom another Maven project
         * @param fromSection path to XML section to merge from
         * @param toSection path to XML section to merge into
         * @param append when true, append instead of merging
         */
        void mergeSection( Pom pom, String fromSection, String toSection, boolean append );

        /**
         * Overlay POM template with detail from another Maven project POM
         * 
         * @param pom another Maven project
         */
        void overlayDetails( Pom pom );

        /**
         * @throws IOException
         */
        void write()
            throws IOException;
    }

    /**
     * Thrown when a POM element already exists and can't be overwritten {@link Pom}
     */
    public static class ExistingElementException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        /**
         * @param element name of the existing POM element
         */
        public ExistingElementException( String element )
        {
            super( "Project already has a <" + element + "> which matches, use -Doverwrite or -o to replace it" );
        }

        /**
         * {@inheritDoc}
         */
        public synchronized Throwable fillInStackTrace()
        {
            return this;
        }

        /**
         * {@inheritDoc}
         */
        public String toString()
        {
            return "[INFO] not available";
        }
    }

    /**
     * Factory method that provides an editor for an existing Maven project file
     * 
     * @param here a Maven POM, or a directory containing a file named 'pom.xml'
     * @return simple Maven project editor
     * @throws IOException
     */
    public static Pom readPom( File here )
        throws IOException
    {
        File candidate = here;

        if( null == here )
        {
            throw new IOException( "null location" );
        }
        else if( here.isDirectory() )
        {
            candidate = new File( here, "pom.xml" );
        }

        return new XppPom( candidate );
    }

    /**
     * Factory method that provides an editor for a new Maven project file
     * 
     * @param here the file, or directory for the new Maven project
     * @param groupId project group id
     * @param artifactId project artifact id
     * @return simple Maven project editor
     * @throws IOException
     */
    public static Pom createModulePom( File here, String groupId, String artifactId )
        throws IOException
    {
        File candidate = here;

        if( null == here )
        {
            throw new IOException( "null location" );
        }
        else if( here.isDirectory() )
        {
            candidate = new File( here, "pom.xml" );
        }

        return new XppPom( candidate, groupId, artifactId );
    }

    /**
     * @param project Maven project
     * @return true if this is an OSGi bundle project, otherwise false
     */
    public static boolean isBundleProject( MavenProject project )
    {
        return isBundleProject( project, null, null, null, false );
    }

    /**
     * @param project Maven project
     * @param resolver artifact resolver
     * @param remoteRepos sequence of remote repositories
     * @param localRepo local Maven repository
     * @param testMetadata check jar manifest for OSGi attributes if true
     * @return true if this is an OSGi bundle project, otherwise false
     */
    public static boolean isBundleProject( MavenProject project, ArtifactResolver resolver, List remoteRepos,
        ArtifactRepository localRepo, boolean testMetadata )
    {
        String packaging = project.getPackaging();
        if( packaging != null && packaging.indexOf( "bundle" ) >= 0 )
        {
            return true;
        }

        return isBundleArtifact( project.getArtifact(), resolver, remoteRepos, localRepo, testMetadata );
    }

    /**
     * @param artifact Maven artifact
     * @param resolver artifact resolver
     * @param remoteRepos sequence of remote repositories
     * @param localRepo local Maven repository
     * @param testMetadata check jar manifest for OSGi attributes if true
     * @return true if this is an OSGi bundle artifact, otherwise false
     */
    public static boolean isBundleArtifact( Artifact artifact, ArtifactResolver resolver, List remoteRepos,
        ArtifactRepository localRepo, boolean testMetadata )
    {
        if( null == artifact )
        {
            return false;
        }

        String type = artifact.getType();
        if( null != type && type.indexOf( "bundle" ) >= 0 )
        {
            return true;
        }
        else if( !testMetadata || !downloadFile( artifact, resolver, remoteRepos, localRepo ) )
        {
            return false;
        }

        try
        {
            return isBundleArtifact( new JarFile( artifact.getFile() ).getManifest() );
        }
        catch( IOException e )
        {
            return false;
        }
    }

    /**
     * @param manifest jar manifest, possibly null
     * @return true if this is an OSGi bundle artifact, otherwise false
     */
    private static boolean isBundleArtifact( Manifest manifest )
    {
        if( null == manifest )
        {
            return false;
        }

        Attributes mainAttributes = manifest.getMainAttributes();

        return mainAttributes.getValue( "Bundle-SymbolicName" ) != null
            || mainAttributes.getValue( "Bundle-Name" ) != null;
    }

    /**
     * Look for the artifact in local Maven repository
     * 
     * @param artifact Maven artifact
     * @param resolver artifact resolver
     * @param localRepo local Maven repository
     * @return true if the artifact is available, otherwise false
     */
    public static boolean getFile( Artifact artifact, ArtifactResolver resolver, ArtifactRepository localRepo )
    {
        return downloadFile( artifact, resolver, Collections.EMPTY_LIST, localRepo );
    }

    /**
     * Look for the artifact in local and remote Maven repositories
     * 
     * @param artifact Maven artifact
     * @param resolver artifact resolver
     * @param remoteRepos sequence of remote repositories
     * @param localRepo local Maven repository
     * @return true if the artifact is available, otherwise false
     */
    public static boolean downloadFile( Artifact artifact, ArtifactResolver resolver, List remoteRepos,
        ArtifactRepository localRepo )
    {
        if( artifact.getFile() == null || !artifact.getFile().exists() )
        {
            try
            {
                resolver.resolve( artifact, remoteRepos, localRepo );
            }
            catch( AbstractArtifactResolutionException e )
            {
                return false;
            }
            catch( NullPointerException e )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Try to combine overlapping group and artifact identifiers to remove duplicate elements
     * 
     * @param groupId project group id
     * @param artifactId project artifact id
     * @return the combined group and artifact sequence
     */
    public static String getCompoundId( String groupId, String artifactId )
    {
        // treat any dashes like dots
        String lhs = groupId.replace( '-', '.' );
        String rhs = artifactId.replace( '-', '.' );

        // simple common prefix check
        if( rhs.equals( lhs ) || rhs.startsWith( lhs + '.' ) )
        {
            return artifactId;
        }

        rhs = '.' + rhs; // optimization when testing for overlap

        // check for overlapping segments by repeated chopping of artifactId
        for( int i = rhs.length(); i > 0; i = rhs.lastIndexOf( '.', i - 1 ) )
        {
            if( lhs.endsWith( rhs.substring( 0, i ) ) )
            {
                if( rhs.length() == i )
                {
                    return groupId;
                }

                return groupId + '.' + artifactId.substring( i );
            }
        }

        // no common segments, so append
        return groupId + '.' + artifactId;
    }

    /**
     * Find the symbolic (meta) Maven version, such as 1.0-SNAPSHOT
     * 
     * @param artifact Maven artifact
     * @return meta version for the artifact
     */
    public static String getMetaVersion( Artifact artifact )
    {
        if( artifact.isSnapshot() )
        {
            try
            {
                return artifact.getSelectedVersion().toString();
            }
            catch( OverConstrainedVersionException e )
            {
                return artifact.getVersion();
            }
            catch( NullPointerException e )
            {
                return artifact.getVersion();
            }
        }

        return artifact.getVersion();
    }

    /**
     * @param version project version
     * @return true if we need to resolve this version
     */
    public static boolean needReleaseVersion( String version )
    {
        return isEmpty( version ) || "RELEASE".equals( version ) || "LATEST".equals( version );
    }

    /**
     * @param artifact Maven artifact
     * @param source metadata source
     * @param remoteRepos sequence of remote repositories
     * @param localRepo local Maven repository
     * @param range acceptable versions
     * @return the release version if available, otherwise throws {@link MojoExecutionException}
     * @throws MojoExecutionException
     */
    public static String getReleaseVersion( Artifact artifact, ArtifactMetadataSource source, List remoteRepos,
        ArtifactRepository localRepo, VersionRange range )
        throws MojoExecutionException
    {
        try
        {
            List versions = source.retrieveAvailableVersions( artifact, localRepo, remoteRepos );
            ArtifactVersion releaseVersion = getLatestReleaseInRange( versions, range );
            if( null == releaseVersion )
            {
                throw new MojoExecutionException( "Unable to find release version for " + artifact );
            }
            return releaseVersion.toString();
        }
        catch( ArtifactMetadataRetrievalException e )
        {
            throw new MojoExecutionException( "Unable to find artifact " + artifact );
        }
    }

    /**
     * @param versions list of available versions
     * @param range acceptable range of versions
     * @return latest acceptable release, otherwise null
     */
    private static ArtifactVersion getLatestReleaseInRange( List versions, VersionRange range )
    {
        final ArtifactVersion baseline = new DefaultArtifactVersion( "0" );

        ArtifactVersion releaseVersion = baseline;
        for( Iterator i = versions.iterator(); i.hasNext(); )
        {
            ArtifactVersion v = (ArtifactVersion) i.next();
            if( isCompatible( range, v ) && releaseVersion.compareTo( v ) <= 0 )
            {
                releaseVersion = v;
            }
        }

        // no compatible version found
        if( baseline == releaseVersion )
        {
            return null;
        }

        return releaseVersion;
    }

    /**
     * @param range compatible range
     * @param version candidate version
     * @return true if this version is compatible, otherwise false
     */
    private static boolean isCompatible( VersionRange range, ArtifactVersion version )
    {
        if( version.getMajorVersion() > 10000000 || ArtifactUtils.isSnapshot( version.toString() ) )
        {
            return false; // ignore snapshots and possible timestamped releases
        }
        else if( range != null && !range.containsVersion( version ) )
        {
            return false; // ignore this version as it's not compatible
        }

        return true;
    }

    /**
     * @param param Maven plugin parameter
     * @return true if parameter is empty, otherwise false
     */
    public static boolean isEmpty( String param )
    {
        return null == param || param.trim().length() == 0;
    }

    /**
     * @param param Maven plugin parameter
     * @return false if parameter is empty, otherwise true
     */
    public static boolean isNotEmpty( String param )
    {
        return null != param && param.trim().length() > 0;
    }
}
