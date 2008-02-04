package org.ops4j.pax.construct.util;

/*
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

import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Copied from plexus-utils 1.4.2 Xpp3Dom.java (last version which appended children correctly)
 */
public class Xpp3DomHelper
{
    /**
     * Merges one DOM into another, given a specific algorithm and possible override points for that algorithm.
     * The algorithm is as follows:
     * 
     * 1. if the recessive DOM is null, there is nothing to do...return.
     * 
     * 2. Determine whether the dominant node will suppress the recessive one (flag=mergeSelf).
     * 
     *    A. retrieve the 'combine.self' attribute on the dominant node, and try to match against 'override'...
     *       if it matches 'override', then set mergeSelf == false...the dominant node suppresses the recessive
     *       one completely.
     *       
     *    B. otherwise, use the default value for mergeSelf, which is true...this is the same as specifying
     *       'combine.self' == 'merge' as an attribute of the dominant root node.
     *       
     * 3. If mergeSelf == true
     * 
     *    A. if the dominant root node's value is empty, set it to the recessive root node's value
     *    
     *    B. For each attribute in the recessive root node which is not set in the dominant root node, set it.
     *    
     *    C. Determine whether children from the recessive DOM will be merged or appended to the dominant 
     *       DOM as siblings (flag=mergeChildren).
     *       
     *       i.   if childMergeOverride is set (non-null), use that value (true/false)
     *       
     *       ii.  retrieve the 'combine.children' attribute on the dominant node, and try to match against
     *            'append'...if it matches 'append', then set mergeChildren == false...the recessive children
     *            will be appended as siblings of the dominant children.
     *           
     *       iii. otherwise, use the default value for mergeChildren, which is true...this is the same as
     *            specifying 'combine.children' == 'merge' as an attribute on the dominant root node.
     *    
     *    D. Iterate through the recessive children, and:
     *    
     *       i.   if mergeChildren == true and there is a corresponding dominant child (matched by element name),
     *            merge the two.
     *            
     *       ii.  otherwise, add the recessive child as a new child on the dominant root node.
     */
    private static void mergeIntoXpp3Dom( Xpp3Dom dominant, Xpp3Dom recessive, Boolean childMergeOverride )
    {
        // TODO: share this as some sort of assembler, implement a walk interface?
        if ( recessive == null )
        {
            return;
        }
        
        boolean mergeSelf = true;
        
        String selfMergeMode = dominant.getAttribute( Xpp3Dom.SELF_COMBINATION_MODE_ATTRIBUTE );
        
        if ( isNotEmpty( selfMergeMode ) && Xpp3Dom.SELF_COMBINATION_OVERRIDE.equals( selfMergeMode ) )
        {
            mergeSelf = false;
        }
        
        if ( mergeSelf )
        {
            if ( isEmpty( dominant.getValue() ) )
            {
                dominant.setValue( recessive.getValue() );
            }
            
            String[] recessiveAttrs = recessive.getAttributeNames();
            for ( int i = 0; i < recessiveAttrs.length; i++ )
            {
                String attr = recessiveAttrs[i];
                
                if ( isEmpty( dominant.getAttribute( attr ) ) )
                {
                    dominant.setAttribute( attr, recessive.getAttribute( attr ) ); 
                }
            }
            
            boolean mergeChildren = true;
            
            if ( childMergeOverride != null )
            {
                mergeChildren = childMergeOverride.booleanValue();
            }
            else
            {
                String childMergeMode = dominant.getAttribute( Xpp3Dom.CHILDREN_COMBINATION_MODE_ATTRIBUTE );
                
                if ( isNotEmpty( childMergeMode ) && Xpp3Dom.CHILDREN_COMBINATION_APPEND.equals( childMergeMode ) )
                {
                    mergeChildren = false;
                }
            }
            
            Xpp3Dom[] children = recessive.getChildren();
            for ( int i = 0; i < children.length; i++ )
            {
                Xpp3Dom child = children[i];
                Xpp3Dom childDom = dominant.getChild( child.getName() );
                if ( mergeChildren && childDom != null )
                {
                    mergeIntoXpp3Dom( childDom, child, childMergeOverride );
                }
                else
                {
                    dominant.addChild( new Xpp3Dom( child ) );
                }
            }
        }
    }
    
    /**
     * Merge two DOMs, with one having dominance in the case of collision.
     * 
     * @see #CHILDREN_COMBINATION_MODE_ATTRIBUTE
     * @see #SELF_COMBINATION_MODE_ATTRIBUTE
     * 
     * @param dominant The dominant DOM into which the recessive value/attributes/children will be merged
     * @param recessive The recessive DOM, which will be merged into the dominant DOM
     * @param childMergeOverride Overrides attribute flags to force merging or appending of child elements
     *        into the dominant DOM
     */
    public static Xpp3Dom mergeXpp3Dom( Xpp3Dom dominant, Xpp3Dom recessive, Boolean childMergeOverride )
    {
        if ( dominant != null )
        {
            mergeIntoXpp3Dom( dominant, recessive, childMergeOverride );
            return dominant;
        }
        return recessive;
    }

    /**
     * Merge two DOMs, with one having dominance in the case of collision.
     * Merge mechanisms (vs. override for nodes, or vs. append for children) is determined by
     * attributes of the dominant root node.
     * 
     * @see #CHILDREN_COMBINATION_MODE_ATTRIBUTE
     * @see #SELF_COMBINATION_MODE_ATTRIBUTE
     * 
     * @param dominant The dominant DOM into which the recessive value/attributes/children will be merged
     * @param recessive The recessive DOM, which will be merged into the dominant DOM
     */
    public static Xpp3Dom mergeXpp3Dom( Xpp3Dom dominant, Xpp3Dom recessive )
    {
        if ( dominant != null )
        {
            mergeIntoXpp3Dom( dominant, recessive, null );
            return dominant;
        }
        return recessive;
    }

    public static boolean isNotEmpty( String str )
    {
        return ( str != null && str.length() > 0 );
    }

    public static boolean isEmpty( String str )
    {
        return ( str == null || str.trim().length() == 0 );
    }
}
