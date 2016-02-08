package org.unbiquitous.unbihealth.avatar.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Auxiliary class that helps to (JSON) serialize skeletons.
 * <p>
 * This bean holds, for each bone in an hierarchy, its unique id, its (required) sensor id and its parent id (which may
 * be null, indicating the root node).
 *
 * @author Luciano Santos
 * @see AvatarBone
 * @see AvatarSkeleton
 */
public class BoneData {
    @JsonProperty(required = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String id;

    @JsonProperty(required = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String sensorId;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String parendId;

    public BoneData() {
    }

    public BoneData(String id, String sensorId) {
        this(id, sensorId, null);
    }

    public BoneData(String id, String sensorId, String parendId) {
        setId(id);
        setSensorId(sensorId);
        setParendId(parendId);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public String getParendId() {
        return parendId;
    }

    public void setParendId(String parendId) {
        this.parendId = parendId;
    }
}
