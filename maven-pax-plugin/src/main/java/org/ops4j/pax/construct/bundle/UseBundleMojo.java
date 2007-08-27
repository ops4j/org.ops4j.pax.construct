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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Use a local bundle inside another bundle sub-project (adds dependency to the compilation classpath)
 * 
 * Note: imported (non-local) bundles are available to local bundles via the top-level provision pom.
 * 
 * @goal use-bundle
 */
public final class UseBundleMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * The local project name of the bundle to be used.
     * 
     * @parameter expression="${bundleName}"
     * @required
     */
    private String bundleName;

    /**
     * Should we attempt to overwrite entries?
     * 
     * @parameter expression="${overwrite}"
     */
    private boolean overwrite;

    /**
     * Records the Maven project for the used bundle.
     */
    private static MavenProject bundleProject;

    public void execute()
        throws MojoExecutionException
    {
        // Nothing to be done for non-bundle projects...
        if( !PomUtils.isBundleProject( project ) )
        {
            return;
        }

        if( null == bundleProject )
        {
            bundleProject = PomUtils.findModule( project, bundleName );
            if( null == bundleProject )
            {
                throw new MojoExecutionException( "Cannot find bundle " + bundleName );
            }

            if( !PomUtils.isBundleProject( bundleProject ) )
            {
                throw new MojoExecutionException( "Sub-project " + bundleName + " is not a bundle" );
            }
        }

        // make sure we don't add a bundle dependency to itself!
        if( !project.getId().equals( bundleProject.getId() ) )
        {
            Pom pom = PomUtils.readPom( project.getFile() );
            pom.addDependency( bundleProject, overwrite );
            pom.write();
        }
    }
}
