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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.xml.Xpp3Dom;

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
        throws MojoExecutionException
    {
        PluginXml localXml;

        try
        {
            localXml = new PluginXml( new File( outputDirectory, "META-INF/maven/plugin.xml" ) );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "cannot read local plugin metadata", e );
        }

        Map plugins = new HashMap();

        for( Iterator i = project.getDependencyArtifacts().iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            if( "maven-plugin".equals( artifact.getType() ) )
            {
                String name = artifact.getArtifactId().replaceAll( "(?:maven-)?(\\w+)(?:-maven)?-plugin", "$1" );

                File here = new File( project.getBuild().getDirectory(), artifact.getArtifactId() );
                here.mkdirs();

                try
                {
                    UnArchiver unArchiver = archiverManager.getUnArchiver( artifact.getFile() );
                    unArchiver.setDestDirectory( here );
                    unArchiver.setSourceFile( artifact.getFile() );
                    unArchiver.extract();

                    plugins.put( name, new PluginXml( new File( here, "META-INF/maven/plugin.xml" ) ) );
                }
                catch( Exception e )
                {
                    throw new MojoExecutionException( "problem unpacking inherited plugin: " + name, e );
                }
            }
        }

        Xpp3Dom[] localMojos = localXml.getMojos();
        for( int i = 0; i < localMojos.length; i++ )
        {
            String goal = localMojos[i].getChild( "goal" ).getValue();

            int offset = goal.indexOf( ':' );
            if( offset > 0 )
            {
                getLog().info( "[importing " + goal.replaceAll( "=", " as " ) + "]" );

                String plugin = goal.substring( 0, offset );
                goal = goal.substring( offset + 1 ).replaceFirst( "=.*", "" );

                Xpp3Dom inheritedMojo = ((PluginXml) plugins.get( plugin )).findMojo( goal );

                if( null == inheritedMojo )
                {
                    getLog().warn( "cannot find inherited goal: " + goal + " in plugin: " + plugin );
                }
                else
                {
                    PluginXml.mergeMojo( localMojos[i], new Xpp3Dom( inheritedMojo ) );
                }
            }
        }

        try
        {
            localXml.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "cannot update local plugin metadata", e );
        }
    }
}
