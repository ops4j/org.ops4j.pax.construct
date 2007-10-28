package org.ops4j.pax.construct.bundle;

/*
 * Copyright 2007 Stuart McCulloch
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
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.BndUtils;
import org.ops4j.pax.construct.util.BndUtils.Bnd;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Embed a jarfile inside a bundle project
 * 
 * <code><pre>
 *   mvn pax:embed-jar [-DgroupId=...] -DartifactId=... [-Dversion=...]
 * </pre></code>
 * 
 * @goal embed-jar
 * @aggregator true
 * 
 * @requiresProject false
 */
public class EmbedJarMojo extends AbstractMojo
{
    /**
     * Component factory for Maven artifacts
     * 
     * @component
     */
    private ArtifactFactory m_factory;

    /**
     * Component for resolving Maven metadata
     * 
     * @component
     */
    private ArtifactMetadataSource m_source;

    /**
     * List of remote Maven repositories for the containing project.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List m_remoteRepos;

    /**
     * The local Maven repository for the containing project.
     * 
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository m_localRepo;

    /**
     * The groupId of the jar to be embedded.
     * 
     * @parameter expression="${groupId}"
     */
    private String groupId;

    /**
     * The artifactId of the jar to be embedded.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version of the jar to be embedded.
     * 
     * @parameter expression="${version}"
     */
    private String version;

    /**
     * When true, unpack the jar inside the bundle.
     * 
     * @parameter expression="${unpack}"
     */
    private boolean unpack;

    /**
     * The -exportcontents directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter expression="${exportContents}"
     */
    private String exportContents;

    /**
     * The directory containing the POM to be updated.
     * 
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    private File targetDirectory;

    /**
     * When true, overwrite matching directives in the 'osgi.bnd' file.
     * 
     * @parameter expression="${overwrite}"
     */
    private boolean overwrite;

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        populateMissingFields();

        updatePomDependencies();
        updateBndInstructions();
    }

    /**
     * Populate missing fields with information from the Maven repository
     * 
     * @throws MojoExecutionException
     */
    private void populateMissingFields()
        throws MojoExecutionException
    {
        if( null == groupId || groupId.length() == 0 )
        {
            // this is a common assumption
            groupId = artifactId;
        }

        if( PomUtils.needReleaseVersion( version ) )
        {
            Artifact artifact = m_factory.createBuildArtifact( groupId, artifactId, "RELEASE", "jar" );
            version = PomUtils.getReleaseVersion( artifact, m_source, m_remoteRepos, m_localRepo );
        }
    }

    /**
     * Add compile-time dependency to get the jarfile, mark it optional so it's not included in transitive dependencies
     * 
     * @throws MojoExecutionException
     */
    private void updatePomDependencies()
        throws MojoExecutionException
    {
        Pom pom;
        try
        {
            pom = PomUtils.readPom( targetDirectory );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem reading Maven POM: " + targetDirectory );
        }

        if( !pom.isBundleProject() )
        {
            throw new MojoExecutionException( "Cannot embed jar inside non-bundle project" );
        }

        // new dependency to fetch the jarfile
        Dependency dependency = new Dependency();
        dependency.setGroupId( groupId );
        dependency.setArtifactId( artifactId );
        dependency.setVersion( version );
        dependency.setScope( Artifact.SCOPE_COMPILE );

        // limit transitive nature
        dependency.setOptional( true );

        String id = groupId + ':' + artifactId + ':' + version;
        getLog().info( "Embedding " + id + " in " + pom );

        pom.addDependency( dependency, overwrite );

        try
        {
            pom.write();
        }
        catch( IOException e1 )
        {
            throw new MojoExecutionException( "Problem writing Maven POM: " + pom.getFile() );
        }
    }

    /**
     * Add Bnd instructions to embed jarfile, and update -exportcontents directive if necessary
     * 
     * @throws MojoExecutionException
     */
    private void updateBndInstructions()
        throws MojoExecutionException
    {
        Bnd bnd;

        try
        {
            bnd = BndUtils.readBnd( targetDirectory );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem reading Bnd file: " + targetDirectory + "/osgi.bnd" );
        }

        final String embedKey = artifactId + ";groupId=" + groupId;
        final String embedClause = embedKey + ";inline=" + unpack;

        String embedDependency = bnd.getInstruction( "Embed-Dependency" );
        embedDependency = addEmbedClause( embedClause, embedDependency );

        bnd.setInstruction( "Embed-Dependency", embedDependency, true );

        if( exportContents != null )
        {
            bnd.setInstruction( "-exportcontents", exportContents, overwrite );
        }

        try
        {
            bnd.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem writing Bnd file: " + bnd.getFile() );
        }
    }

    /**
     * @param embedClause clause that will embed the jarfile
     * @param embedDependency comma separated list of clauses
     * @return updated Embed-Dependency instruction
     */
    private String addEmbedClause( String embedClause, String embedDependency )
    {
        if( null == embedDependency )
        {
            return embedClause;
        }

        StringBuffer buf = new StringBuffer();

        String[] clauses = embedDependency.split( "," );
        for( int i = 0; i < clauses.length; i++ )
        {
            final String c = clauses[i].trim();

            // remove any clauses matching the one we're adding
            if( c.length() > 0 && !c.startsWith( embedClause ) )
            {
                buf.append( c );
                buf.append( ',' );
            }
        }

        // add the new clause
        buf.append( embedClause );

        return buf.toString();
    }
}
