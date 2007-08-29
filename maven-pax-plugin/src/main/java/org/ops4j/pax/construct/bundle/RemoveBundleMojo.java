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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Removes a local bundle and any references to it from the project.
 * 
 * @goal remove-bundle
 */
public final class RemoveBundleMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * The local project name of the bundle to be removed.
     * 
     * @parameter expression="${bundleName}"
     * @required
     */
    private String bundleName;

    /**
     * Records the Maven project for the removed bundle.
     */
    private static MavenProject bundleProject;

    public void execute()
        throws MojoExecutionException
    {
        if( null == bundleProject )
        {
            bundleProject = DirUtils.findModule( project, bundleName );
            if( null == bundleProject )
            {
                throw new MojoExecutionException( "Cannot find bundle " + bundleName );
            }

            if( !PomUtils.isBundleProject( bundleProject ) )
            {
                throw new MojoExecutionException( "Sub-project " + bundleName + " is not a bundle" );
            }
        }

        if( !project.getId().equals( bundleProject.getId() ) )
        {
            Pom pom = PomUtils.readPom( project.getFile() );

            Dependency dependency = new Dependency();
            dependency.setGroupId( bundleProject.getGroupId() );
            dependency.setArtifactId( bundleProject.getArtifactId() );
            pom.removeDependency( dependency );

            pom.removeModule( bundleName );
            pom.write();
        }
        else
        {
            try
            {
                FileSet bundleFiles = new FileSet();

                File bundleFolder = bundleProject.getBasedir();
                bundleFiles.setDirectory( bundleFolder.getParent() );
                bundleFiles.addInclude( bundleFolder.getName() );

                new FileSetManager( getLog(), true ).delete( bundleFiles );
            }
            catch( Exception e )
            {
                getLog().warn( "Unable to remove the requested bundle", e );
            }
        }
    }
}
