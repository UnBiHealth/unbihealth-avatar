package org.unbiquitous.unbihealth.avatar.data;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.math3.complex.Quaternion;
import org.unbiquitous.unbihealth.avatar.Bone;

import java.util.*;

/**
 * Holds data for an Avatar's element, i.e., a bone, associated an independent movement sensor (IMU), that
 * may have child bones.
 *
 * @author Luciano Santos
 */
public class AvatarBone implements Bone {
    private String id;

    private String sensorId;

    private Map<String, AvatarBone> children;

    private AvatarBone parent;

    private Quaternion rotation = Quaternion.IDENTITY;

    public AvatarBone(String id, String sensorId) {
        this(id, sensorId, null);
    }

    public AvatarBone(String id, String sensorId, Set<AvatarBone> children) {
        if (id == null)
            throw new NullPointerException("id must not be null");
        if (sensorId == null)
            throw new NullPointerException("sensorId must not be null");

        this.id = id;
        this.sensorId = sensorId;

        this.children = new HashMap<>();
        if (children != null)
            for (AvatarBone child : children) {
                if (child.parent != null)
                    child.parent.children.remove(child.getId());
                this.children.put(child.getId(), child);
                child.parent = this;
            }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Map<String, AvatarBone> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    @Override
    public AvatarBone getParent() {
        return parent;
    }

    /**
     * Sets this bone's sensor id.
     *
     * @param id The new id.
     * @throws NullPointerException If <code>id<code/> is null.
     */
    public void setSensorId(String id) {
        if (id == null)
            throw new NullPointerException("id");
        this.sensorId = id;
    }

    /**
     * @return This bone's sensor id.
     */
    public String getSensorId() {
        return sensorId;
    }

    /**
     * Given the bone's absolute rotation, sets its rotation relative to its parent (if root, identity parent
     * rotation is used).
     *
     * @param rotation The new absolute rotation.
     * @throws NullPointerException If <code>rotation<code/> is null.
     * @see #getParent()
     */
    public void setRotation(Quaternion rotation) {
        if (rotation == null)
            throw new NullPointerException("rotation");
        Quaternion parent = getParent() == null ? Quaternion.IDENTITY : getParent().getRotation();
        this.rotation = fromTo(rotation, parent);
    }

    @Override
    public Quaternion getRotation() {
        return rotation;
    }

    private static Quaternion fromTo(Quaternion to, Quaternion from) {
        return Quaternion.multiply(to, from.getInverse());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof AvatarBone))
            return false;
        AvatarBone other = (AvatarBone) obj;
        return new EqualsBuilder()
                .append(id, other.getId())
                .append(sensorId, other.getSensorId())
                .append(children, other.getChildren()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(id)
                .append(sensorId)
                .append(children).toHashCode();
    }

    @Override
    public String toString() {
        return toString(new StringBuilder(), "").toString();
    }

    private StringBuilder toString(StringBuilder sb, String prefix) {
        sb.append(prefix);
        sb.append(id);
        sb.append(":");
        sb.append(sensorId);
        if (!children.isEmpty()) {
            sb.append(" {\n");
            Iterator<AvatarBone> i = children.values().iterator();
            do {
                i.next().toString(sb, prefix + "  ");
                if (i.hasNext())
                    sb.append(",");
                sb.append("\n");
            } while (i.hasNext());
            sb.append(prefix);
            sb.append("}");
        }
        return sb;
    }
}
