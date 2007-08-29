package org.ops4j.pax.construct.inherit;

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
import java.util.Iterator;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;

/**
 * @goal inherit
 * @phase compile
 */
public class InheritMojo extends AbstractMojo
{
    /**
     * @component role="org.codehaus.plexus.archiver.manager.ArchiverManager"
     * @required
     * @readonly
     */
    protected ArchiverManager archiverManager;

    /**
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    /**
     * @parameter expression="${project.build.outputDirectory}"
     */
    protected File outputDirectory;

    public void execute()
    {
        PluginXml localXml;

        try
        {
            localXml = new PluginXml( new File( outputDirectory, "META-INF/maven/plugin.xml" ) );
        }
        catch( Exception e )
        {
            getLog().error( "cannot read local plugin metadata", e );
        }

        for( Iterator i = project.getDependencyArtifacts().iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            if( "maven-plugin".equals( artifact.getType() ) )
            {
                try
                {
                    UnArchiver unArchiver = archiverManager.getUnArchiver( artifact.getFile() );
                    File here = new File( project.getBuild().getDirectory(), artifact.getArtifactId() );

                    here.mkdirs();
                    unArchiver.setDestDirectory( here );
                    unArchiver.setSourceFile( artifact.getFile() );
                    unArchiver.extract();

                    PluginXml inheritedXml = new PluginXml( new File( here, "META-INF/maven/plugin.xml" ) );
                }
                catch( Exception e )
                {
                    getLog().error( "problem unpacking inherited plugin: " + artifact.getId(), e );
                }
            }
        }

        // ======================================== TEMPORARY HACK =============================================

        File patchedPluginXml = new File( project.getBasedir(), "src/main/resources/META-INF/maven/plugin.xml" );
        File currentPluginXml = new File( project.getBasedir(), "target/classes/META-INF/maven/plugin.xml" );
        try
        {
            FileUtils.copyFile( patchedPluginXml, currentPluginXml );
        }
        catch( IOException e )
        {
            // ignore, this is only placeholder code until proper patching is in place
        }
    }
}
