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

import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;

/**
 * Embeds a jarfile inside the bundle project.
 * 
 * @goal embed-jar
 */
public final class EmbedJarMojo extends AbstractMojo
{
    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

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
            throw new MojoExecutionException( "Can only embed jars inside a compiled bundle sub-project" );
        }

        try
        {
            Document pom = readPom( project.getFile() );

            Element projectElem = pom.getElement( null, "project" );

            // TODO: embed jarfile

            writePom( project.getFile(), pom );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to embed the requested jar artifact", e );
        }
    }

}
