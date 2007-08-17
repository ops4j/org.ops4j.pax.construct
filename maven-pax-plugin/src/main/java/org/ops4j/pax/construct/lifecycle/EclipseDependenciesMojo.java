package org.ops4j.pax.construct.lifecycle;

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
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Iterator;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.ide.AbstractIdeSupportMojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

/**
 * Extend maven-eclipse-plugin to process multiple OSGi dependencies.
 * 
 * @goal eclipse-dependencies
 */
public class EclipseDependenciesMojo extends EclipseMojo
{
    /**
     * @component role="org.apache.maven.project.MavenProjectBuilder"
     * @required
     * @readonly
     */
    protected MavenProjectBuilder mavenProjectBuilder;

    private MavenProject thisProject;

    public boolean setup()
        throws MojoExecutionException
    {
        if( null != thisProject )
        {
            setEclipseProjectDir( null );
            clearDependencyCache();
            return super.setup();
        }

        thisProject = getProject();

        try
        {
            for( Iterator i = thisProject.getDependencies().iterator(); i.hasNext(); )
            {
                Dependency dependency = (Dependency) i.next();
                if( !dependency.isOptional() && "provided".equals( dependency.getScope() ) )
                {
                    VersionRange versionRange = VersionRange.createFromVersionSpec( dependency.getVersion() );

                    Artifact artifact = artifactFactory.createDependencyArtifact( dependency.getGroupId(), dependency
                        .getArtifactId(), versionRange, "pom", null, "provided", null, false );

                    MavenProject dependencyProject = mavenProjectBuilder.buildFromRepository( artifact,
                        getRemoteArtifactRepositories(), getLocalRepository() );

                    File groupDir = new File( "target/" + dependency.getGroupId() );
                    File dependencyDir = new File( groupDir, artifact.getArtifactId() );
                    dependencyDir.mkdirs();

                    File pomFile = new File( dependencyDir, "pom.xml" );

                    Writer writer = new FileWriter( pomFile );
                    dependencyProject.writeModel( writer );
                    dependencyProject.setFile( pomFile );
                    writer.close();

                    setBuildOutputDirectory( new File( dependencyDir, ".ignore" ) );

                    setExecutedProject( dependencyProject );
                    setProject( dependencyProject );

                    super.execute();
                }
            }
        }
        catch( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }

        return false;
    }

    protected void clearDependencyCache()
    {
        try
        {
            // Attempt to bypass normal private field protection
            Field f = AbstractIdeSupportMojo.class.getDeclaredField( "ideDeps" );

            f.setAccessible( true );
            f.set( this, null );
        }
        catch( Exception e )
        {
        }
    }
}
