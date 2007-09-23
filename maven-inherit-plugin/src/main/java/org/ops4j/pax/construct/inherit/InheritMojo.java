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
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

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
    static final String GOAL = "goal";

    static final String EXTENDS_PLUGIN = "extendsPlugin";
    static final String EXTENDS_GOAL = "extendsGoal";

    /**
     * @parameter expression="${project}"
     */
    private MavenProject m_project;

    /**
     * @parameter expression="${project.build.outputDirectory}"
     */
    private File m_outputDirectory;

    /**
     * @component role="org.codehaus.plexus.archiver.manager.ArchiverManager"
     */
    private ArchiverManager m_archiverManager;

    public void execute()
        throws MojoExecutionException
    {
        PluginXml targetPlugin = loadPluginMetadata( m_outputDirectory );

        Map dependentPluginsByName = loadDependentPluginMetaData();

        JavaDocBuilder builder = new JavaDocBuilder();
        for( Iterator i = m_project.getCompileSourceRoots().iterator(); i.hasNext(); )
        {
            builder.addSourceTree( new File( (String) i.next() ) );
        }

        JavaSource[] javaSources = builder.getSources();
        for( int i = 0; i < javaSources.length; i++ )
        {
            JavaClass mojoClass = javaSources[i].getClasses()[0];

            DocletTag extendsTag = mojoClass.getTagByName( EXTENDS_PLUGIN );
            if( null != extendsTag )
            {
                String pluginName = extendsTag.getValue();
                getLog().info( "Extending " + pluginName + " plugin" );

                PluginXml basePlugin = (PluginXml) dependentPluginsByName.get( pluginName );
                if( null == basePlugin )
                {
                    getLog().warn( pluginName + " plugin is not a dependency" );
                }
                else
                {
                    mergePluginMojo( mojoClass, targetPlugin, basePlugin );
                }
            }
        }

        try
        {
            targetPlugin.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "cannot update local plugin metadata", e );
        }
    }

    PluginXml loadPluginMetadata( File pluginRoot )
        throws MojoExecutionException
    {
        File metadata = new File( pluginRoot, "META-INF/maven/plugin.xml" );

        try
        {
            return new PluginXml( metadata );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "cannot read plugin metadata " + metadata, e );
        }
        catch( XmlPullParserException e )
        {
            throw new MojoExecutionException( "cannot parse plugin metadata " + metadata, e );
        }
    }

    Map loadDependentPluginMetaData()
        throws MojoExecutionException
    {
        String buildArea = m_project.getBuild().getDirectory();

        Map pluginsByName = new HashMap();
        for( Iterator i = m_project.getDependencyArtifacts().iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            if( "maven-plugin".equals( artifact.getType() ) )
            {
                File unpackDir = new File( buildArea, artifact.getArtifactId() );
                unpackPlugin( artifact, unpackDir );

                String name = artifact.getArtifactId().replaceAll( "(?:maven-)?(\\w+)(?:-maven)?-plugin", "$1" );

                PluginXml dependentPlugin = loadPluginMetadata( unpackDir );
                pluginsByName.put( name, dependentPlugin );
            }
        }

        return pluginsByName;
    }

    void unpackPlugin( Artifact artifact, File unpackDir )
        throws MojoExecutionException
    {
        File pluginFile = artifact.getFile();
        unpackDir.mkdirs();

        try
        {
            UnArchiver unArchiver = m_archiverManager.getUnArchiver( pluginFile );
            unArchiver.setDestDirectory( unpackDir );
            unArchiver.setSourceFile( pluginFile );
            unArchiver.extract();
        }
        catch( NoSuchArchiverException e )
        {
            throw new MojoExecutionException( "cannot find unarchiver for " + pluginFile, e );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "problem reading file " + pluginFile, e );
        }
        catch( ArchiverException e )
        {
            throw new MojoExecutionException( "problem unpacking file " + pluginFile, e );
        }
    }

    void mergePluginMojo( JavaClass mojoClass, PluginXml targetPlugin, PluginXml basePlugin )
    {
        DocletTag goalTag = mojoClass.getTagByName( GOAL );
        if( null == goalTag )
        {
            return;
        }

        DocletTag baseGoalTag = mojoClass.getTagByName( EXTENDS_GOAL );
        if( null == baseGoalTag )
        {
            baseGoalTag = goalTag;
        }

        String goal = goalTag.getValue();
        String baseGoal = baseGoalTag.getValue();

        getLog().info( baseGoal + " => " + goal );

        Xpp3Dom targetMojoXml = targetPlugin.findMojo( goal );
        Xpp3Dom baseMojoXml = basePlugin.findMojo( baseGoal );

        PluginXml.mergeMojo( targetMojoXml, baseMojoXml );
    }
}
