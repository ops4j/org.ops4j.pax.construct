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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.PropertyUtils;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.ops4j.pax.construct.util.PomUtils;

/**
 * Embeds a jarfile inside a local bundle project.
 * 
 * @goal embed-jar
 */
public final class EmbedJarMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * The groupId of the jarfile to wrap.
     * 
     * @parameter expression="${groupId}"
     * @required
     */
    private String groupId;

    /**
     * The artifactId of the jarfile to wrap.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version of the jarfile to wrap.
     * 
     * @parameter expression="${version}"
     * @required
     */
    private String version;

    /**
     * Should the jar be unpacked inside the bundle?
     * 
     * @parameter expression="${unpack}"
     */
    private boolean unpack;

    /**
     * @parameter expression="${exportContents}"
     */
    private String exportContents;

    /**
     * Should we attempt to overwrite entries?
     * 
     * @parameter expression="${overwrite}"
     */
    private boolean overwrite;

    public void execute()
        throws MojoExecutionException
    {
        // Nothing to be done for non-bundle projects...
        if( !PomUtils.isBundleProject( project.getModel() ) )
        {
            return;
        }

        Document pom = PomUtils.readPom( project.getFile() );

        // all compiled dependencies are automatically embedded
        Element projectElem = pom.getElement( null, "project" );
        Dependency dependency = new Dependency();
        dependency.setGroupId( groupId );
        dependency.setArtifactId( artifactId );
        dependency.setVersion( version );
        dependency.setScope( "compile" );

        // limit transitive nature
        dependency.setOptional( true );

        PomUtils.addDependency( projectElem, dependency, overwrite );
        PomUtils.writePom( project.getFile(), pom );

        if( unpack || exportContents != null )
        {
            File bndConfig = new File( project.getBasedir(), "src/main/resources/META-INF/details.bnd" );

            if( !bndConfig.exists() )
            {
                try
                {
                    bndConfig.getParentFile().mkdirs();
                    bndConfig.createNewFile();
                }
                catch( Exception e )
                {
                    throw new MojoExecutionException( "Unable to create BND tool config file", e );
                }
            }

            Properties properties = PropertyUtils.loadProperties( bndConfig );
            boolean propertiesChanged = false;

            if( unpack )
            {
                // Use FELIX-308 to inline selected artifacts
                String embedDependency = properties.getProperty( "Embed-Dependency", "*;scope=compile|runtime" );
                String inlineClause = artifactId + ";groupId=" + groupId + ";inline=true";

                // check to see if its already inlined...
                if( embedDependency.indexOf( inlineClause ) < 0 )
                {
                    embedDependency += "," + inlineClause;
                    properties.setProperty( "Embed-Dependency", embedDependency );
                    propertiesChanged = true;
                }
            }

            if( exportContents != null )
            {
                properties.setProperty( "-exportcontents", exportContents );
                propertiesChanged = true;
            }

            if( propertiesChanged )
            {
                OutputStream propertyStream = null;

                try
                {
                    propertyStream = new BufferedOutputStream( new FileOutputStream( bndConfig ) );
                    properties.store( propertyStream, null );
                }
                catch( Exception e )
                {
                    throw new MojoExecutionException( "Unable to save the new BND tool instructions", e );
                }
                finally
                {
                    IOUtil.close( propertyStream );
                }
            }
        }
    }
}
