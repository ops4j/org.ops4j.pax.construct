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

/**
 * Provision all local and imported bundles onto the selected OSGi framework
 * 
 * <code><pre>
 *   mvn pax:run [-Dframework=felix|equinox|kf|concierge] [-Ddeploy=log,war,spring,...]
 * </pre></code>
 * 
 * @goal run
 */
public class RunMojo extends ProvisionMojo
{
    /**
     * Exactly same implementation as pax:provision
     */
}
