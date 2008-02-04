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

/**
 * Simple singleton to help bridge between Velocity templates and the archetype mojos
 */
public final class VelocityBridge
{
    /**
     * Current archetype mojo
     */
    private static AbstractPaxArchetypeMojo m_mojo;

    /**
     * Need public constructor to allow access from Velocity templates (only seems to work with object instances)
     */
    public VelocityBridge()
    {
        /*
         * nothing to do
         */
    }

    /**
     * @param mojo current archetype mojo
     */
    public static void setMojo( AbstractPaxArchetypeMojo mojo )
    {
        m_mojo = mojo;
    }

    /**
     * @return current archetype mojo
     */
    public AbstractPaxArchetypeMojo getMojo()
    {
        return m_mojo;
    }
}
