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
import java.io.FileReader;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.ops4j.pax.construct.util.PomUtils;

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
     * @parameter expression="${name}"
     * @required
     */
    private String bundleName;

    /**
     * Should we attempt to overwrite entries?
     * 
     * @parameter expression="${overwrite}" default-value="false"
     */
    private boolean overwrite;

    /**
     * Records the Maven model for the used bundle.
     */
    private static Model usedBundleModel;

    public void execute()
        throws MojoExecutionException
    {
        // Nothing to be done for non-bundle projects...
        if( !PomUtils.isBundleProject( project.getModel() ) )
        {
            return;
        }

        if( null == usedBundleModel )
        {
            // search from the top of the project
            MavenProject rootProject = project;
            while( rootProject.getParent() != null )
            {
                rootProject = rootProject.getParent();
            }

            File usedPomFile = PomUtils.findBundlePom( rootProject.getBasedir(), bundleName );
            if( null == usedPomFile || !usedPomFile.exists() )
            {
                throw new MojoExecutionException( "Cannot find bundle " + bundleName );
            }

            try
            {
                // cache Maven information about the used bundle
                FileReader input = new FileReader( usedPomFile );
                MavenXpp3Reader modelReader = new MavenXpp3Reader();
                usedBundleModel = modelReader.read( input );
            }
            catch( Exception e )
            {
                throw new MojoExecutionException( "Unable to open sub-project " + bundleName, e );
            }

            if( !PomUtils.isBundleProject( usedBundleModel ) )
            {
                throw new MojoExecutionException( "Sub-project " + bundleName + " is not a bundle" );
            }
        }

        // make sure we don't add a bundle dependency to itself!
        if( project.getId().equals( usedBundleModel.getId() ) )
        {
            return;
        }

        Document pom = PomUtils.readPom( project.getFile() );

        Element projectElem = pom.getElement( null, "project" );
        Dependency dependency = PomUtils.getBundleDependency( usedBundleModel );
        PomUtils.addDependency( projectElem, dependency, overwrite );

        PomUtils.writePom( project.getFile(), pom );
    }
}
