package org.ops4j.pax.construct;

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

import static org.ops4j.pax.construct.PomUtils.readPom;
import static org.ops4j.pax.construct.PomUtils.writePom;

import java.io.File;
import java.io.FileReader;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;

/**
 * Use a bundle inside a bundle project.
 * 
 * @goal use-bundle
 */
public final class UseBundleMojo extends AbstractMojo
{
    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    /**
     * The name of the bundle to use.
     * 
     * @parameter expression="${name}"
     * @required
     */
    private String bundleName;

    /**
     * @parameter default-value="${reactorProjects}"
     * @required
     * @readonly
     */
    private List reactorProjects;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        // execute only if inside a compiled bundle project
        if ( reactorProjects.size() > 1 || project.getParent() == null
            || !project.getParent().getArtifactId().equals( "compile-bundle" ) )
        {
            throw new MojoExecutionException( "Can only use bundles inside a compiled bundle sub-project" );
        }

        try
        {
            File usedPomFile = new File( project.getFile().getParentFile(), "../" + bundleName + "/pom.xml" );

            if ( !usedPomFile.exists() )
            {
                throw new MojoExecutionException( "Cannot find bundle " + bundleName );
            }

            FileReader input = new FileReader( usedPomFile );
            MavenXpp3Reader modelReader = new MavenXpp3Reader();
            Model bundleModel = modelReader.read( input );

            Document pom = readPom( project.getFile() );

            Element projectElem = pom.getElement( null, "project" );
            Dependency dependency = PomUtils.getBundleDependency( bundleModel );
            PomUtils.addDependency( projectElem, dependency );

            writePom( project.getFile(), pom );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Cannot use the requested bundle", e );
        }
    }

}
