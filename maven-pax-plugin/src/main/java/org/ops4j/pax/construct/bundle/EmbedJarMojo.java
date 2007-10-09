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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.BndFileUtils;
import org.ops4j.pax.construct.util.BndFileUtils.BndFile;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Embed a jarfile inside a bundle project
 * 
 * @goal embed-jar
 * @aggregator true
 */
public class EmbedJarMojo extends AbstractMojo
{
    /**
     * The groupId of the jar to be embedded.
     * 
     * @parameter expression="${groupId}"
     * @required
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
     * @required
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
        addDependencyToPom();

        addInstructionsToBndFile();
    }

    /**
     * Add compile-time dependency to get the jarfile, mark it optional so it's not included in transitive dependencies
     * 
     * @throws MojoExecutionException
     */
    void addDependencyToPom()
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
    void addInstructionsToBndFile()
        throws MojoExecutionException
    {
        BndFile bndFile;

        try
        {
            bndFile = BndFileUtils.readBndFile( targetDirectory );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem reading Bnd file: " + targetDirectory + "/osgi.bnd" );
        }

        final String embedKey = artifactId + ";groupId=" + groupId;
        final String embedClause = embedKey + ";inline=" + unpack;

        String embedDependency = bndFile.getInstruction( "Embed-Dependency" );
        embedDependency = addEmbedClause( embedClause, embedDependency );

        bndFile.setInstruction( "Embed-Dependency", embedDependency, true );

        if( exportContents != null )
        {
            bndFile.setInstruction( "-exportcontents", exportContents, overwrite );
        }

        try
        {
            bndFile.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem writing Bnd file: " + bndFile.getFile() );
        }
    }

    /**
     * @param embedClause clause that will embed the jarfile
     * @param embedDependency comma separated list of clauses
     * @return updated Embed-Dependency instruction
     */
    String addEmbedClause( String embedClause, String embedDependency )
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
