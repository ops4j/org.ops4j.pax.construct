package org.ops4j.pax.construct.clone;

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
 * Builder interface for Maven specific options
 */
public interface MavenOptionBuilder
{
    /**
     * Add a simple Maven flag, such as -Dflag
     * 
     * @param flag the flag name
     * @return builder for Maven specific options
     */
    MavenOptionBuilder flag( String flag );

    /**
     * Add a Maven property, such as -Doption=value
     * 
     * @param option the option name
     * @param value the value to use
     * @return builder for Maven specific options
     */
    MavenOptionBuilder option( String option, String value );
}
