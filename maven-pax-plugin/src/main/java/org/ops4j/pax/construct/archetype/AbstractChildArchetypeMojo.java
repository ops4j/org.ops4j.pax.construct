package org.ops4j.pax.construct.archetype;

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
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Foundation for all OSGi sub-project goals that use archetypes.
 */
public abstract class AbstractChildArchetypeMojo extends AbstractArchetypeMojo
{
    private static boolean seenRootProject = false;

    /**
     * Should we attempt to overwrite entries?
     * 
     * @parameter expression="${overwrite}"
     */
    protected boolean overwrite;

    /**
     * The newly generated POM file - this is set in the _root_ project execution
     */
    protected static File childPomFile;

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        // only create files under primary root project
        if( seenRootProject || project.getParent() != null )
        {
            return false;
        }

        seenRootProject = true;
        return true;
    }

    protected void setChildProjectName( final String childProjectName )
        throws MojoExecutionException
    {
        // This somehow forces Maven to keep POM formatting & XSD
        File dir = new File( targetDirectory, childProjectName );
        dir.mkdirs();

        childPomFile = new File( dir, "pom.xml" );

        if( overwrite )
        {
            // force an update
            childPomFile.delete();
        }

        // update parent modules
        linkParentToChild();
    }

    protected void linkParentToChild()
        throws MojoExecutionException
    {
        try
        {
            final String childName = childPomFile.getParentFile().getName();

            Pom parentPom = DirUtils.createModuleTree( project.getBasedir(), targetDirectory );
            if( null == parentPom )
            {
                throw new MojoExecutionException( "targetDirectory is outside of this project" );
            }

            parentPom.addModule( childName, overwrite );
            parentPom.write();
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to link parent pom to child", e );
        }
    }

    protected void linkChildToParent( List initialDependencies )
        throws MojoExecutionException
    {
        try
        {
            Pom childPom = PomUtils.readPom( childPomFile );

            File sourcePath = childPomFile.getParentFile();
            File targetPath = project.getBasedir();

            String relativePath = null;
            String[] pivot = DirUtils.calculateRelativePath( sourcePath, targetPath );
            if( null != pivot )
            {
                relativePath = pivot[0] + pivot[2];
            }

            childPom.setParent( project, relativePath, overwrite );

            for( Iterator i = initialDependencies.iterator(); i.hasNext(); )
            {
                childPom.addDependency( (Dependency) i.next(), overwrite );
            }

            childPom.write();
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to link child pom to parent", e );
        }
    }
}
