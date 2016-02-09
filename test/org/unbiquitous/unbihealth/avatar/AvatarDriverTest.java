package org.unbiquitous.unbihealth.avatar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.unbiquitous.unbihealth.avatar.data.AvatarBone;
import org.unbiquitous.unbihealth.avatar.data.AvatarSkeleton;
import org.unbiquitous.unbihealth.avatar.data.BoneData;
import org.unbiquitous.unbihealth.imu.IMUDriver;
import org.unbiquitous.unbihealth.imu.SensorData;
import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.adaptabitilyEngine.ServiceCallException;
import org.unbiquitous.uos.core.adaptabitilyEngine.SmartSpaceGateway;
import org.unbiquitous.uos.core.deviceManager.DeviceManager;
import org.unbiquitous.uos.core.driverManager.DriverData;
import org.unbiquitous.uos.core.messageEngine.dataType.UpService;
import org.unbiquitous.uos.core.messageEngine.messages.Notify;
import org.unbiquitous.uos.core.messageEngine.messages.Response;

import java.io.IOException;
import java.util.*;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Test for {@link AvatarDriver}.
 *
 * @author Luciano Santos
 */
public class AvatarDriverTest {
    private static final double EPSILON = 0.000000000000001;

    static final ObjectMapper mapper = new ObjectMapper();
    SmartSpaceGateway gateway;
    DeviceManager deviceManager;
    AvatarDriver instance;
    InitialProperties props;
    DriverData imuDriverData;

    @Rule
    public ExpectedException expectEx = ExpectedException.none();

    @Before
    public void setUp() {
        gateway = mock(SmartSpaceGateway.class);
        deviceManager = mock(DeviceManager.class);
        when(gateway.getDeviceManager()).thenReturn(deviceManager);
        instance = new AvatarDriver();
        props = new InitialProperties();
        imuDriverData = new DriverData(IMUDriver.getDriverStatic(), null, null);
    }

    @Test
    public void declareTheInterfaceProperly() {
        assertThat(instance.getDriver().getName()).isEqualTo("org.unbiquitous.ubihealth.AvatarDriver");
    }

    @Test
    public void declareChange() {
        assertThat(instance.getDriver().getEvents()).contains(
                new UpService("change").addParameter("newData", UpService.ParameterType.MANDATORY)
        );
    }

    @Test
    public void usesDefaultSkeleton() throws Exception {
        instance.init(gateway, props, null);
        assertThat(instance.getSkeleton().getRoot()).isEqualTo(new AvatarBone("root", "root"));
    }

    @Test
    public void shouldThrowOnNoRoot() throws Exception {
        BoneData root = new BoneData();
        root.setParendId("parent");
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{root}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("No root");
        instance.init(gateway, props, null);
    }

    @Test
    public void shouldThrowOnMultipleRoots() throws Exception {
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{new BoneData(), new BoneData()}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("More than");
        instance.init(gateway, props, null);
    }

    @Test
    public void shouldThrowOnNullId() throws Exception {
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{new BoneData()}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("id empty");
        instance.init(gateway, props, null);
    }

    @Test
    public void shouldThrowOnEmptyId() throws Exception {
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{new BoneData("", null)}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("id empty");
        instance.init(gateway, props, null);
    }

    @Test
    public void shouldUseIdOnNullSensorId() throws Exception {
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{new BoneData("root", null)}));
        instance.init(gateway, props, null);
        AvatarBone b = (AvatarBone) instance.getSkeleton().getRoot();
        assertThat(b.getId()).isEqualTo(b.getSensorId());
    }

    @Test
    public void shouldUseIdOnEmptySensorId() throws Exception {
        props.put(
                "avatardriver.skeleton",
                mapper.writeValueAsString(new BoneData[]{new BoneData("root", "")}));
        instance.init(gateway, props, null);
        AvatarBone b = (AvatarBone) instance.getSkeleton().getRoot();
        assertThat(b.getId()).isEqualTo(b.getSensorId());
    }

    @Test
    public void shouldThrowOnDuplicateIdParent() throws Exception {
        BoneData b1 = new BoneData("root", "sensor");
        BoneData b2 = new BoneData("root", "sensor", "root");
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{b1, b2}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("Duplicate id 'root'");
        instance.init(gateway, props, null);
    }

    @Test
    public void shouldThrowOnDuplicateIdSiblings() throws Exception {
        BoneData parent = new BoneData("root", "root-sensor");
        BoneData b1 = new BoneData("child", "child-sensor", "root");
        BoneData b2 = new BoneData("child", "child-sensor", "root");
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{parent, b1, b2}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("Duplicate id 'child'");
        instance.init(gateway, props, null);
    }

    @Test
    public void shouldThrowOnDuplicateSensorIdParent() throws Exception {
        BoneData b1 = new BoneData("root", "sensor");
        BoneData b2 = new BoneData("child", "sensor", "root");
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{b1, b2}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("Duplicate sensorId 'sensor'");
        instance.init(gateway, props, null);
    }

    @Test
    public void shouldThrowOnDuplicateSensorIdSiblings() throws Exception {
        BoneData parent = new BoneData("root", "root-sensor");
        BoneData b1 = new BoneData("child1", "child-sensor", "root");
        BoneData b2 = new BoneData("child2", "child-sensor", "root");
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{parent, b1, b2}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("Duplicate sensorId 'child-sensor'");
        instance.init(gateway, props, null);
    }

    @Test
    public void shouldThrowOnIsolatedSubgraphs() throws Exception {
        BoneData root = new BoneData("root", "root-sensor");
        BoneData b1 = new BoneData("parent", "parent-sensor", "child");
        BoneData b2 = new BoneData("child", "child-sensor", "parent");
        props.put("avatardriver.skeleton", mapper.writeValueAsString(new BoneData[]{root, b1, b2}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("outside the root hierarchy");
        instance.init(gateway, props, null);
    }

    @Test
    public void complexHierarchy() throws Exception {
        props.put("avatardriver.skeleton", createComplexHierarchyJSON());
        instance.init(gateway, props, null);
        assertThat(instance.getSkeleton().getRoot()).isEqualTo(createComplexHierarchy());
        assertThat(instance.getSkeleton().toString()).isEqualTo(getComplexHierarchyString());
        assertThat(instance.getSkeleton().getRoot().getParent()).isNull();

        // Deep first traverses all the nodes verifying their data.
        Skeleton skeleton = instance.getSkeleton();
        Stack<Bone> path = new Stack<>();
        path.push(skeleton.getRoot());
        while (!path.isEmpty()) {
            Bone b = path.pop();
            assertThat(skeleton.getBone(b.getId()).getRotation()).isEqualTo(Quaternion.IDENTITY);
            for (Bone child : b.getChildren().values()) {
                assertThat(child.getParent()).isEqualTo(b);
                path.push(child);
            }
        }
    }

    @Test
    public void shouldSetParentsCorrectly() throws Exception {
        props.put("avatardriver.skeleton", createComplexHierarchyJSON());
        instance.init(gateway, props, null);

        assertThat(instance.getSkeleton().getRoot().getParent()).isNull();
        // Deep first traverses all the nodes verifying their parents.
        Stack<Bone> path = new Stack<>();
        path.push(instance.getSkeleton().getRoot());
        while (!path.isEmpty()) {
            Bone b = path.pop();
            for (Bone child : b.getChildren().values()) {
                assertThat(child.getParent()).isEqualTo(b);
                path.push(child);
            }
        }
    }

    @Test
    public void shouldSwapParentsCorrectly() throws Exception {
        AvatarBone a = createComplexHierarchy();
        Map<String, AvatarBone> childrenA = a.getChildren();
        AvatarBone ab0 = childrenA.get("ab0");
        Set<AvatarBone> childrenB = new HashSet<>();
        childrenB.add(ab0);
        AvatarBone b = new AvatarBone("b", "b-sensor", childrenB);
        assertThat(b.getChildren()).containsKey("ab0");
        assertThat(b.getChildren().get("ab0")).isEqualTo(ab0);
        assertThat(ab0.getParent()).isEqualTo(b);
        assertThat(childrenA).doesNotContainKey("ab0");
        assertThat(childrenA).doesNotContainValue(ab0);
    }

    @Test
    public void shouldGetAKnownBone() throws Exception {
        props.put("avatardriver.skeleton", createComplexHierarchyJSON());
        instance.init(gateway, props, null);
        Skeleton s = instance.getSkeleton();
        assertThat(s.getBone("ab3c2d0e0")).isEqualTo(new AvatarBone("ab3c2d0e0", "ab3c2d0e0-sensor"));
        assertThat(s.getBone("a")).isEqualTo(createComplexHierarchy());
    }

    @Test
    public void shouldNotGetAnUnknownBone() throws Exception {
        props.put("avatardriver.skeleton", createComplexHierarchyJSON());
        instance.init(gateway, props, null);
        Skeleton s = instance.getSkeleton();
        assertThat(s.getBone("n")).isNull();
    }

    @Test
    public void listenShouldThrowOnNullBoneId() throws Exception {
        expectEx.expect(NullPointerException.class);
        expectEx.expectMessage("bone id");
        instance.init(gateway, props, null);
        instance.setSensor(null, null, null);
    }

    @Test
    public void listenShouldThrowOnUnknownBoneId() throws Exception {
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("Unknown bone id");
        instance.init(gateway, props, null);
        instance.setSensor("unknown", "sensor", null);
    }

    @Test
    public void listenShouldThrowOnNullSensorId() throws Exception {
        expectEx.expect(NullPointerException.class);
        expectEx.expectMessage("sensor id");
        instance.init(gateway, props, null);
        instance.setSensor("root", null, null);
    }

    @Test
    public void listenShouldThrowOnAlreadyUsedSensorId() throws Exception {
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("already in use by bone 'ab0'");
        props.put("avatardriver.skeleton", createComplexHierarchyJSON());
        instance.init(gateway, props, null);
        instance.setSensor("a", "ab0-sensor", null);
    }

    @Test
    public void listenShouldNotThrownOnSameBoneUsingSensorId() throws Exception {
        props.put("avatardriver.skeleton", createComplexHierarchyJSON());
        instance.init(gateway, props, null);
        instance.setSensor("ab0", "ab0-sensor", null);
        AvatarSkeleton skeleton = (AvatarSkeleton) instance.getSkeleton();
        assertThat(skeleton.getBone("ab0")).isEqualTo(skeleton.getBoneBySensorId("ab0-sensor"));
    }

    @Test
    public void listenShouldThrowOnNotIMUDriver() throws Exception {
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("not IMUDriver");
        instance.init(gateway, props, null);
        instance.setSensor("root", "root", mock(DriverData.class));
    }

    @Test
    public void listenShouldThrowOnInvalidResponse() throws Exception {
        when(gateway.callService(null, IMUDriver.LIST_IDS_NAME, IMUDriver.DRIVER_NAME, null, null, null))
                .thenReturn(new Response());
        expectEx.expect(ServiceCallException.class);
        expectEx.expectMessage("sensor id list");
        instance.init(gateway, props, null);
        instance.setSensor("root", "sensor", imuDriverData);
    }

    @Test
    public void listenShouldThrowOnUnknownSensorId() throws Exception {
        when(gateway.callService(null, IMUDriver.LIST_IDS_NAME, IMUDriver.DRIVER_NAME, null, null, null))
                .thenReturn(new Response().addParameter(IMUDriver.IDS_PARAM_NAME, new String[]{"sensor"}));
        expectEx.expect(IllegalArgumentException.class);
        expectEx.expectMessage("Unknown sensor");
        instance.init(gateway, props, null);
        instance.setSensor("root", "unknown", imuDriverData);
    }

    @Test
    public void shouldRegisterKnownSensor() throws Exception {
        when(gateway.callService(null, IMUDriver.LIST_IDS_NAME, IMUDriver.DRIVER_NAME, null, null, null))
                .thenReturn(new Response().addParameter(IMUDriver.IDS_PARAM_NAME, new String[]{"sensor"}));
        instance.init(gateway, props, null);
        instance.setSensor("root", "sensor", imuDriverData);
        verify(gateway, times(1)).register(instance, null, IMUDriver.DRIVER_NAME, IMUDriver.CHANGE_EVENT_NAME);
    }

    @Test
    public void shouldUnregisterWhenNoSensorIsAssociatedAnymore() throws Exception {
        DriverData driver1 = new DriverData(IMUDriver.getDriverStatic(), null, "driver1");
        DriverData driver2 = new DriverData(IMUDriver.getDriverStatic(), null, "driver2");
        when(gateway.callService(null, IMUDriver.LIST_IDS_NAME, IMUDriver.DRIVER_NAME, "driver1", null, null))
                .thenReturn(new Response().addParameter(IMUDriver.IDS_PARAM_NAME, new String[]{"sensor1"}));
        when(gateway.callService(null, IMUDriver.LIST_IDS_NAME, IMUDriver.DRIVER_NAME, "driver2", null, null))
                .thenReturn(new Response().addParameter(IMUDriver.IDS_PARAM_NAME, new String[]{"sensor2", "sensor3"}));
        props.put("avatardriver.skeleton", createComplexHierarchyJSON());
        instance.init(gateway, props, null);
        instance.setSensor("a", "sensor1", driver1);
        instance.setSensor("ab0", "sensor2", driver2);
        instance.setSensor("ab1", "sensor3", driver2);
        instance.setSensor("ab0", "ab0-sensor", null);
        instance.setSensor("ab1", "ab1-sensor", null);
        verify(gateway, times(2)).register(instance, null, IMUDriver.DRIVER_NAME, IMUDriver.CHANGE_EVENT_NAME);
        verify(gateway, times(1)).unregister(instance, null, IMUDriver.DRIVER_NAME, "driver2", IMUDriver.CHANGE_EVENT_NAME);
    }

    @Test
    public void shouldUpdateBonesValues() throws Exception {
        BoneData[] bones = new BoneData[]{new BoneData("arm", "1"), new BoneData("forearm", "2", "arm")};
        props.put("avatardriver.skeleton", mapper.writeValueAsString(bones));
        instance.init(gateway, props, null);
        instance.handleEvent(createNotify("1", angleAxis(Vector3D.PLUS_I, FastMath.PI / 4)));
        assertThat(instance.getSkeleton().getBone("arm").getRotation()).isEqualTo(angleAxis(Vector3D.PLUS_I, FastMath.PI / 4));
        instance.handleEvent(createNotify("2", angleAxis(Vector3D.PLUS_I, FastMath.PI / 2)));
        assertTrue(instance.getSkeleton().getBone("forearm").getRotation().equals(angleAxis(Vector3D.PLUS_I, FastMath.PI / 4), EPSILON));
    }

    private static Quaternion angleAxis(Vector3D axis, double angle) {
        Rotation rot = new Rotation(axis, angle);
        return new Quaternion(rot.getQ0(), rot.getQ1(), rot.getQ2(), rot.getQ3());
    }

    private static Notify createNotify(String sensor, Quaternion value) {
        SensorData sensorData = new SensorData();
        sensorData.setId(sensor);
        sensorData.setQuaternion(value);
        sensorData.setTimestamp(System.currentTimeMillis());
        return new Notify(IMUDriver.CHANGE_EVENT_NAME, IMUDriver.DRIVER_NAME)
                .addParameter(IMUDriver.CHANGE_NEW_DATA_PARAM_NAME, sensorData);
    }

    private static String createComplexHierarchyJSON() throws IOException {
        List<BoneData> bones = new ArrayList<>();
        bones.add(new BoneData("a", "a-sensor"));
        bones.add(new BoneData("ab0", "ab0-sensor", "a"));
        bones.add(new BoneData("ab1", "ab1-sensor", "a"));
        bones.add(new BoneData("ab1c0", "ab1c0-sensor", "ab1"));
        bones.add(new BoneData("ab2", "ab2-sensor", "a"));
        bones.add(new BoneData("ab2c0", "ab2c0-sensor", "ab2"));
        bones.add(new BoneData("ab2c1", "ab2c1-sensor", "ab2"));
        bones.add(new BoneData("ab3", "ab3-sensor", "a"));
        bones.add(new BoneData("ab3c0", "ab3c0-sensor", "ab3"));
        bones.add(new BoneData("ab3c1", "ab3c1-sensor", "ab3"));
        bones.add(new BoneData("ab3c2", "ab3c2-sensor", "ab3"));
        bones.add(new BoneData("ab3c2d0", "ab3c2d0-sensor", "ab3c2"));
        bones.add(new BoneData("ab3c2d0e0", "ab3c2d0e0-sensor", "ab3c2d0"));
        return mapper.writeValueAsString(bones);
    }

    private static AvatarBone createComplexHierarchy() {
        Set<AvatarBone> children = new HashSet<>();
        children.add(new AvatarBone("ab3c2d0e0", "ab3c2d0e0-sensor"));
        AvatarBone ab3c2d0 = new AvatarBone("ab3c2d0", "ab3c2d0-sensor", children);

        children = new HashSet<>();
        children.add(ab3c2d0);
        AvatarBone ab3c2 = new AvatarBone("ab3c2", "ab3c2-sensor", children);

        children = new HashSet<>();
        children.add(ab3c2);
        children.add(new AvatarBone("ab3c1", "ab3c1-sensor"));
        children.add(new AvatarBone("ab3c0", "ab3c0-sensor"));
        AvatarBone ab3 = new AvatarBone("ab3", "ab3-sensor", children);

        children = new HashSet<>();
        children.add(new AvatarBone("ab2c1", "ab2c1-sensor"));
        children.add(new AvatarBone("ab2c0", "ab2c0-sensor"));
        AvatarBone ab2 = new AvatarBone("ab2", "ab2-sensor", children);

        children = new HashSet<>();
        children.add(new AvatarBone("ab1c0", "ab1c0-sensor"));
        AvatarBone ab1 = new AvatarBone("ab1", "ab1-sensor", children);

        children = new HashSet<>();
        children.add(ab3);
        children.add(ab2);
        children.add(ab1);
        children.add(new AvatarBone("ab0", "ab0-sensor"));
        return new AvatarBone("a", "a-sensor", children);
    }

    private static String getComplexHierarchyString() {
        final String result = "a:a-sensor {\n" +
                "  ab2:ab2-sensor {\n" +
                "    ab2c0:ab2c0-sensor,\n" +
                "    ab2c1:ab2c1-sensor\n" +
                "  },\n" +
                "  ab1:ab1-sensor {\n" +
                "    ab1c0:ab1c0-sensor\n" +
                "  },\n" +
                "  ab3:ab3-sensor {\n" +
                "    ab3c1:ab3c1-sensor,\n" +
                "    ab3c2:ab3c2-sensor {\n" +
                "      ab3c2d0:ab3c2d0-sensor {\n" +
                "        ab3c2d0e0:ab3c2d0e0-sensor\n" +
                "      }\n" +
                "    },\n" +
                "    ab3c0:ab3c0-sensor\n" +
                "  },\n" +
                "  ab0:ab0-sensor\n" +
                "}";
        return result;
    }
}
