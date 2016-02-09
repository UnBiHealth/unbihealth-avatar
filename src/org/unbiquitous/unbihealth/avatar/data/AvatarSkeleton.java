package org.unbiquitous.unbihealth.avatar.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.unbiquitous.unbihealth.avatar.Skeleton;

import java.io.IOException;
import java.util.*;

/**
 * This is the implementation of {@link Skeleton}.
 * <p>
 * Each bone (i.e., each single element in the hierarchy) must have an associated movement sensor (IMU).
 *
 * @author Luciano Santos
 */
public class AvatarSkeleton implements Skeleton {
    private AvatarBone root;
    private Map<String, AvatarBone> bones;
    private Map<String, AvatarBone> sensors;

    private AvatarSkeleton() {
    }

    /**
     * Given a JSON representation of the bones, i.e., a serialized list of {@link org.unbiquitous.unbihealth.avatar.data.BoneData},
     * returns respective skeleton.
     * <p>
     * This method first finds the (required to be unique) root node, i.e, the one with no parent. Then, recursively,
     * it processes its children. Since every node must point to its parent and there must be one single root and there
     * can not be any remaining nodes after the root hierarchy is processed, this algorithm is guaranteed to generate no
     * cycles in the hierarchy (since it necessarily generates a tree). If there are any cycles outside the root's hierarchy,
     * it will never be processed (and an error will be generated for there are remaining nodes).
     *
     * @param json A JSON string containing a list (array) of serialized {@link org.unbiquitous.unbihealth.avatar.data.BoneData}.
     * @return The new skeleton.
     * @throws IOException              If any error occurs while parsing JSON string.
     * @throws IllegalArgumentException If the skeleton is invalid (duplicate ids or sensorIds, not exactly one root,
     *                                  isolated subgraphs, etc).
     */
    public static AvatarSkeleton parse(String json) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();

        // Parses the list of bones.
        List<BoneData> data = mapper.readValue(json, new TypeReference<List<BoneData>>() {
        });

        // Finds the (unique) root and recursively build the hierarchy.
        BoneData rootData = extractRoot(data);
        AvatarSkeleton s = new AvatarSkeleton();
        s.bones = new HashMap<>();
        s.sensors = new HashMap<>();
        s.root = buildBone(rootData, data, s);
        if (!data.isEmpty())
            throw new IllegalArgumentException("There are nodes outside the root hierarchy.");
        return s;
    }

    private static BoneData extractRoot(List<BoneData> data) {
        BoneData root = null;
        ListIterator<BoneData> i = data.listIterator();
        while (i.hasNext()) {
            BoneData bd = i.next();
            if (bd.getParendId() == null) {
                if (root != null)
                    throw new IllegalArgumentException("More than one root node found!");
                root = bd;
                i.remove();
            }
        }
        if (root == null)
            throw new IllegalArgumentException("No root node found!");
        return root;
    }

    private static AvatarBone buildBone(BoneData bd, List<BoneData> data, AvatarSkeleton skeleton) {
        if (StringUtils.isEmpty(bd.getId()))
            throw new IllegalArgumentException("AvatarBone id empty or null.");

        String id = bd.getId().toLowerCase();
        // Have I seen this id before?
        if (skeleton.bones.containsKey(id))
            throw new IllegalArgumentException("Duplicate id '" + id + "'.");
        skeleton.bones.put(id, null);

        String sensorId = StringUtils.defaultIfEmpty(bd.getSensorId(), id).toLowerCase();
        // Have I seen this sensorId before?
        if (skeleton.sensors.containsKey(sensorId))
            throw new IllegalArgumentException("Duplicate sensorId '" + sensorId + "'.");
        skeleton.sensors.put(sensorId, null);

        AvatarBone b = new AvatarBone(id, sensorId, buildChildren(id, data, skeleton));
        skeleton.bones.put(id, b);
        skeleton.sensors.put(sensorId, b);
        return b;
    }

    private static Set<AvatarBone> buildChildren(String id, List<BoneData> data, AvatarSkeleton skeleton) {
        // Extracts data for all children.
        List<BoneData> childrenData = new ArrayList<>();
        ListIterator<BoneData> i = data.listIterator();
        while (i.hasNext()) {
            BoneData bd = i.next();
            if (bd.getParendId().toLowerCase().equals(id)) {
                childrenData.add(bd);
                i.remove();
            }
        }

        // Builds them recursively.
        Set<AvatarBone> children = new HashSet<>();
        for (BoneData child : childrenData) {
            AvatarBone b = buildBone(child, data, skeleton);
            children.add(b);
        }

        return children;
    }

    @Override
    public AvatarBone getRoot() {
        return root;
    }

    @Override
    public AvatarBone getBone(String id) {
        return bones.get(id);
    }

    /**
     * Gets a bone given its sensor id.
     *
     * @param sensorId The bone associated with given sensor id.
     * @return The bone or null, if there's no such sensor id associated to any bone.
     */
    public AvatarBone getBoneBySensorId(String sensorId) {
        return sensors.get(sensorId);
    }

    /**
     * Associates a bone, given its id, with given sensor id.
     *
     * @param boneId   The id of the bone to be associated.
     * @param sensorId The new sensor id to use.
     * @return The previous sensor id associated to the bone.
     * @throws NullPointerException     If either boneId or sensorId is null.
     * @throws IllegalArgumentException If bone is unknown, the sensor id is invalid or it's being used by a different
     *                                  bone.
     */
    public String setSensorId(String boneId, String sensorId) {
        if (boneId == null)
            throw new NullPointerException("bone id");
        if (sensorId == null)
            throw new NullPointerException("sensor id");
        if (StringUtils.isEmpty(sensorId))
            throw new NullPointerException("invalid sensor id");

        AvatarBone b = getBone(boneId);
        if (b == null)
            throw new IllegalArgumentException("Unknown bone id.");
        AvatarBone used = getBoneBySensorId(sensorId);
        if ((used != null) && (used != b))
            throw new IllegalArgumentException("Sensor id already in use by bone '" + used.getId() + "'.");
        String previous = b.getSensorId();
        b.setSensorId(sensorId);
        sensors.put(sensorId, b);
        return previous;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Skeleton))
            return false;
        Skeleton other = (Skeleton) obj;
        return Objects.equals(root, other.getRoot());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(root).toHashCode();
    }

    @Override
    public String toString() {
        return root.toString();
    }
}
