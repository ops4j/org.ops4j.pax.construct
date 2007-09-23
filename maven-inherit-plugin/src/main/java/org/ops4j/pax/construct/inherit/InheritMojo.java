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

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.DocletTag;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaSource;

/**
 * @goal inherit
 * @phase compile
 * @requiresDependencyResolution compile
 */
public class InheritMojo extends AbstractMojo
{
    final static String GOAL = "goal";

    final static String INHERIT_MOJO = "inheritMojo";
    final static String INHERIT_GOAL = "inheritGoal";

    /**
     * @parameter default-value="${project}"
     */
    MavenProject project;

    /**
     * @parameter default-value="${project.build.outputDirectory}"
     */
    File outputDirectory;

    /**
     * @component
     */
    ArchiverManager archiverManager;

    public void execute()
        throws MojoExecutionException
    {
        PluginXml targetPluginXml;

        try
        {
            targetPluginXml = new PluginXml( new File( outputDirectory, "META-INF/maven/plugin.xml" ) );
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

        JavaDocBuilder builder = new JavaDocBuilder();
        for( Iterator i = project.getCompileSourceRoots().iterator(); i.hasNext(); )
        {
            builder.addSourceTree( new File( (String) i.next() ) );
        }

        JavaSource[] javaSources = builder.getSources();
        for( int i = 0; i < javaSources.length; i++ )
        {
            JavaClass primaryClass = javaSources[i].getClasses()[0];

            DocletTag targetGoalTag = primaryClass.getTagByName( GOAL );
            if( null == targetGoalTag )
            {
                continue;
            }

            DocletTag inheritMojoTag = primaryClass.getTagByName( INHERIT_MOJO );
            if( null == inheritMojoTag )
            {
                continue;
            }

            DocletTag inheritGoalTag = primaryClass.getTagByName( INHERIT_GOAL );
            if( null == inheritGoalTag )
            {
                inheritGoalTag = targetGoalTag;
            }

            String targetGoal = targetGoalTag.getValue();
            String inheritedMojo = inheritMojoTag.getValue();
            String inheritedGoal = inheritGoalTag.getValue();

            PluginXml inheritedPluginXml = (PluginXml) plugins.get( inheritedMojo );

            Xpp3Dom targetMojoXml = targetPluginXml.findMojo( targetGoal );
            Xpp3Dom inheritedMojoXml = inheritedPluginXml.findMojo( inheritedGoal );

            getLog().info( "[importing " + inheritedMojo + ':' + inheritedGoal + " as " + targetGoal + ']' );

            PluginXml.mergeMojo( targetMojoXml, inheritedMojoXml );
        }

        try
        {
            targetPluginXml.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "cannot update local plugin metadata", e );
        }
    }
}
