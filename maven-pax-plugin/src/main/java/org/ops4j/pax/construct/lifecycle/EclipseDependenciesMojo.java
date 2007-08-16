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
import java.util.Iterator;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
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
            setEclipseProjectDir( new File( thisProject.getBuild().getDirectory(), getProject().getGroupId() ) );

            super.setup();

            // FIXME: avoid linked resources...
            setBuildOutputDirectory( new File( getEclipseProjectDir(), "target/classes" ) );
            getProject().setFile( new File( getEclipseProjectDir(), "pom.xml" ) );

            return true;
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

                    dependencyProject.setFile( thisProject.getFile() );
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
}
