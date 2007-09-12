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
 * @goal remove-bundle
 */
public class RemoveBundleMojo extends AbstractMojo
{
    /**
     * @parameter default-value="${project}"
     */
    MavenProject project;

    /**
     * @parameter expression="${bundleName}"
     * @required
     */
    String bundleName;

    static Pom bundlePom;

    public void execute()
        throws MojoExecutionException
    {
        if( null == bundlePom )
        {
            File bundlePath = new File( bundleName );

            try
            {
                bundlePom = PomUtils.readPom( bundlePath );
            }
            catch( Exception e )
            {
                bundlePom = DirUtils.findPom( project.getBasedir(), bundlePath.getName() );
            }

            if( null == bundlePom )
            {
                throw new MojoExecutionException( "Cannot find bundle " + bundleName );
            }

            if( !bundlePom.isBundleProject() )
            {
                throw new MojoExecutionException( "Sub-project " + bundleName + " is not a bundle" );
            }
        }

        File bundleFolder = bundlePom.getBasedir();

        if( !project.getId().equals( bundlePom.getId() ) )
        {
            boolean needsUpdate = false;

            Pom pom = PomUtils.readPom( project.getFile() );

            Dependency dependency = new Dependency();
            dependency.setGroupId( bundlePom.getGroupId() );
            dependency.setArtifactId( bundlePom.getArtifactId() );

            needsUpdate = needsUpdate || pom.removeDependency( dependency );
            needsUpdate = needsUpdate || pom.removeModule( bundleFolder.getName() );

            if( needsUpdate )
            {
                getLog().info( "Removing " + bundlePom.getId() + " from " + pom.getId() );

                pom.write();
            }
        }
        else
        {
            getLog().info( "Removing " + bundlePom.getId() );

            try
            {
                FileSet bundleFiles = new FileSet();

                bundleFiles.setDirectory( bundleFolder.getParent() );
                bundleFiles.addInclude( bundleFolder.getName() );

                new FileSetManager( getLog(), false ).delete( bundleFiles );
            }
            catch( Exception e )
            {
                getLog().warn( "Unable to remove the requested bundle", e );
            }
        }
    }
}
