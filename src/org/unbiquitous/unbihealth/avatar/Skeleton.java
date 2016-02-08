package org.unbiquitous.unbihealth.avatar;

/**
 * The {@link AvatarDriver} operates over a skeleton (hierarchy) that models the human body.
 * <p>
 * Each bone has a rotation relative to its parent. The unique root node's rotation is absolute.
 *
 * @author Luciano Santos
 */
public interface Skeleton {
    /**
     * Retrieves this AvatarSkeleton's root bone.
     *
     * @return The root bone.
     */
    Bone getRoot();

    /**
     * Retrieves a bone by its id.
     *
     * @param id The id of the bone to be found.
     * @return The bone, or null, if there's no bone with such id.
     */
    Bone getBone(String id);
}
