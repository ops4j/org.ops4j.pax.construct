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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.BndFileUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.BndFileUtils.BndFile;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * @goal embed-jar
 * @aggregator true
 */
public class EmbedJarMojo extends AbstractMojo
{
    /**
     * @parameter expression="${groupId}"
     * @required
     */
    String groupId;

    /**
     * @parameter expression="${artifactId}"
     * @required
     */
    String artifactId;

    /**
     * @parameter expression="${version}"
     * @required
     */
    String version;

    /**
     * @parameter expression="${unpack}"
     */
    boolean unpack;

    /**
     * @parameter expression="${exportContents}"
     */
    String exportContents;

    /**
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    File targetDirectory;

    /**
     * @parameter expression="${overwrite}"
     */
    boolean overwrite;

    public void execute()
        throws MojoExecutionException
    {
        Pom pom = PomUtils.readPom( targetDirectory );
        if( !pom.isBundleProject() )
        {
            getLog().warn( "Cannot embed jar inside non-bundle project" );
            return;
        }

        // all compiled dependencies are automatically embedded
        Dependency dependency = new Dependency();
        dependency.setGroupId( groupId );
        dependency.setArtifactId( artifactId );
        dependency.setVersion( version );
        dependency.setScope( Artifact.SCOPE_COMPILE );

        // limit transitive nature
        dependency.setOptional( true );

        String id = groupId + ':' + artifactId + ':' + version;
        getLog().info( "Embedding " + id + " in " + pom.getId() );

        pom.addDependency( dependency, overwrite );
        pom.write();

        BndFile bndFile = BndFileUtils.readBndFile( targetDirectory );

        final String embedKey = artifactId + ";groupId=" + groupId;
        final String embedClause = embedKey + ";inline=" + unpack;

        String embedDependency = bndFile.getInstruction( "Embed-Dependency" );
        if( null == embedDependency )
        {
            embedDependency = embedClause;
        }
        else
        {
            StringBuffer buf = new StringBuffer();

            String[] clauses = embedDependency.split( "," );
            for( int i = 0; i < clauses.length; i++ )
            {
                final String c = clauses[i].trim();
                if( c.length() > 0 && !c.startsWith( embedClause ) )
                {
                    buf.append( c );
                    buf.append( ',' );
                }
            }
            buf.append( embedClause );

            embedDependency = buf.toString();
        }

        bndFile.setInstruction( "Embed-Dependency", embedDependency, true );

        if( exportContents != null )
        {
            bndFile.setInstruction( "-exportcontents", exportContents, overwrite );
        }

        bndFile.write();
    }
}
