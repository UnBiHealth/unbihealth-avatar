package org.unbiquitous.unbihealth.avatar;

import org.apache.commons.math3.complex.Quaternion;

import java.util.Map;

/**
 * Holds data for an Avatar's element, i.e., a bone, associated an independent movement sensor (IMU), that
 * may have child bones.
 *
 * @author Luciano Santos
 * @see Skeleton
 */
public interface Bone {
    /**
     * Retrieves this bone's unique id.
     *
     * @return The id.
     */
    String getId();

    /**
     * Retrieves the children of this bone.
     *
     * @return The children mapped by their ids or an empty map, if there are no children.
     */
    Map<String, ? extends Bone> getChildren();

    /**
     * Retrieves this bone's parent.
     *
     * @return The parent or null, if it's a root node.
     */
    Bone getParent();

    /**
     * Retrieves this bone's current rotation.
     *
     * @return The rotation, relative to its parent, or absolute rotation, if root.
     * @see #getParent()
     */
    Quaternion getRotation();
}
