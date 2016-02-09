package org.unbiquitous.unbihealth.avatar;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.unbiquitous.unbihealth.avatar.data.AvatarBone;
import org.unbiquitous.unbihealth.avatar.data.AvatarSkeleton;
import org.unbiquitous.unbihealth.imu.IMUDriver;
import org.unbiquitous.unbihealth.imu.SensorData;
import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.UOSLogging;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;
import org.unbiquitous.uos.core.adaptabitilyEngine.NotifyException;
import org.unbiquitous.uos.core.adaptabitilyEngine.ServiceCallException;
import org.unbiquitous.uos.core.adaptabitilyEngine.UosEventListener;
import org.unbiquitous.uos.core.applicationManager.CallContext;
import org.unbiquitous.uos.core.driverManager.DriverData;
import org.unbiquitous.uos.core.driverManager.UosDriver;
import org.unbiquitous.uos.core.driverManager.UosEventDriver;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDevice;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDriver;
import org.unbiquitous.uos.core.messageEngine.dataType.UpNetworkInterface;
import org.unbiquitous.uos.core.messageEngine.dataType.UpService;
import org.unbiquitous.uos.core.messageEngine.messages.Call;
import org.unbiquitous.uos.core.messageEngine.messages.Notify;
import org.unbiquitous.uos.core.messageEngine.messages.Response;
import org.unbiquitous.uos.core.network.model.NetworkDevice;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An UOS driver that models an hierarchy of objects that can rotate relative to their parent (just like the human body).
 *
 * @author Luciano Santos
 */
public class AvatarDriver implements UosEventDriver, UosEventListener {
    public static final String DRIVER_NAME = "org.unbiquitous.ubihealth.AvatarDriver";
    public static final String CHANGE_EVENT_NAME = "change";
    public static final String CHANGE_NEW_DATA_PARAM_NAME = "newData";
    public static final String SKELETON_KEY = "avatardriver.skeleton";
    public static final String DEFAULT_SKELETON = "[{\"id\":\"root\",\"sensorId\":\"root\"}]";

    private static final UpDriver _driver = new UpDriver(DRIVER_NAME) {
        {
            addEvent(CHANGE_EVENT_NAME)
                    .addParameter(CHANGE_NEW_DATA_PARAM_NAME, UpService.ParameterType.MANDATORY);
        }
    };
    private static Logger logger = UOSLogging.getLogger();
    private static ObjectMapper mapper = new ObjectMapper();

    private Gateway gateway;
    private String instanceId;
    private Map<UpNetworkInterface, UpDevice> listeners = new ConcurrentHashMap<UpNetworkInterface, UpDevice>();
    private Map<DriverData, Set<String>> driverToSensor = new HashMap<>();
    private Map<String, DriverData> sensorToDriver = new HashMap<>();
    private AvatarSkeleton skeleton;

    public String getInstanceId() {
        return instanceId;
    }

    public Skeleton getSkeleton() {
        return skeleton;
    }

    /**
     * Associates a bone, given its id, with given sensor id.
     * <p>
     * If {@link DriverData} is provided, tries to register to listen to it.
     *
     * @param boneId   The id of the bone to be associated.
     * @param sensorId The new sensor id to use.
     * @param driver   The driver data to use, or null, if no driver should be listened to.
     * @throws NullPointerException     If either boneId or sensorId is null.
     * @throws IllegalArgumentException If bone is unknown, the sensor id is invalid or it's being used by a different
     *                                  bone.
     */
    public synchronized void setSensor(String boneId, String sensorId, DriverData driver) throws ServiceCallException, IOException, NotifyException {
        // Update local reference
        String previous = skeleton.setSensorId(boneId, sensorId);

        // Must I register for remote events?
        if (driver != null) {
            try {
                // Validate driver data.
                if (!IMUDriver.getDriverStatic().equals(driver.getDriver()))
                    throw new IllegalArgumentException("Driver is not IMUDriver.");
                driver = new DriverData(IMUDriver.getDriverStatic(), driver.getDevice(), driver.getInstanceID());

                // Retrieves acceptable sensor id list.
                Response response = gateway.callService(driver.getDevice(), IMUDriver.LIST_IDS_NAME, IMUDriver.DRIVER_NAME, driver.getInstanceID(), null, null);
                Object idsObj = response.getResponseData(IMUDriver.IDS_PARAM_NAME);
                if (idsObj == null)
                    throw new ServiceCallException("Failed to retrieve sensor id list from device.");
                JavaType listType = mapper.getTypeFactory().constructParametrizedType(List.class, List.class, String.class);
                List<String> ids = (idsObj instanceof String) ? mapper.readValue((String) idsObj, listType) : mapper.convertValue(idsObj, listType);
                if (!ids.contains(sensorId))
                    throw new IllegalArgumentException("Unknown sensor id for target device.");

                // Is there any driver associated to this sensor id?
                DriverData sensorDriver = sensorToDriver.get(sensorId);
                if (sensorDriver != null) {
                    // If it's the same driver, return.
                    if (sensorDriver.equals(driver))
                        return;
                    // If it's a different driver, first dissociates it.
                    removeSensorDriver(sensorId, sensorDriver);
                }

                // Must I call register?
                Set<String> driverSensors = driverToSensor.get(driver);
                if (driverSensors == null) {
                    gateway.register(this, driver.getDevice(), IMUDriver.DRIVER_NAME, IMUDriver.CHANGE_EVENT_NAME);
                    driverSensors = new HashSet<>();
                }
                driverSensors.add(sensorId);
                driverToSensor.put(driver, driverSensors);
                sensorToDriver.put(sensorId, driver);
            } catch (Throwable t) {
                skeleton.setSensorId(boneId, previous);
                throw t;
            }
        }

        // If necessary, unregisters the previous sensor id.
        if (!previous.equals(sensorId)) {
            DriverData sensorDriver = sensorToDriver.get(previous);
            if (sensorDriver != null)
                removeSensorDriver(previous, sensorDriver);
        }
    }

    private void removeSensorDriver(String sensorId, DriverData sensorDriver) {
        sensorToDriver.remove(sensorId);
        Set<String> driverSensors = driverToSensor.get(sensorDriver);
        driverSensors.remove(sensorId);
        if (driverSensors.isEmpty()) {
            try {
                gateway.unregister(this, sensorDriver.getDevice(), IMUDriver.DRIVER_NAME, sensorDriver.getInstanceID(), IMUDriver.CHANGE_EVENT_NAME);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to unregister to IMUDriver.", t);
            }
            driverToSensor.remove(sensorDriver);
        }
    }

    @Override
    public UpDriver getDriver() {
        return _driver;
    }

    @Override
    public List<UpDriver> getParent() {
        return null;
    }

    /**
     * User UOS init properties field {@link #SKELETON_KEY} to set the (json) skeleton description.
     *
     * @see UosDriver#init(Gateway, InitialProperties, String)
     * @see AvatarSkeleton
     */
    @Override
    public void init(Gateway gateway, InitialProperties props, String id) {
        this.gateway = gateway;
        this.instanceId = id;
        String skeletonJson = props.getString(SKELETON_KEY, DEFAULT_SKELETON);
        try {
            this.skeleton = AvatarSkeleton.parse(skeletonJson);
        } catch (IOException e) {
            logger.log(Level.SEVERE, DRIVER_NAME + ": failed to parse skeleton.", e);
            throw new RuntimeException(e);
        }

        logger.info(DRIVER_NAME + ": init instance [" + id + "].");
    }

    @Override
    public void destroy() {
        listeners.clear();
        logger.info(DRIVER_NAME + ": destroy instance [" + instanceId + "]. Bye!");
    }

    @Override
    public synchronized void registerListener(Call call, Response response, CallContext context) {
        logger.info(DRIVER_NAME + ": registerListener.");
        UpNetworkInterface uni = getNetworkInterface(context);
        if (!listeners.containsKey(uni))
            listeners.put(uni, context.getCallerDevice());
    }

    @Override
    public void unregisterListener(Call call, Response response, CallContext context) {
        logger.info(DRIVER_NAME + ": unregisterListener.");
        listeners.remove(getNetworkInterface(context));
    }

    private void doNotify(Notify n) throws NotifyException {
        logger.fine(DRIVER_NAME + ": notify -> " + n.toString());
        for (UpDevice device : listeners.values())
            gateway.notify(n, device);
    }

    private static UpNetworkInterface getNetworkInterface(CallContext context) {
        NetworkDevice networkDevice = context.getCallerNetworkDevice();
        String host = networkDevice.getNetworkDeviceName().split(":")[1];
        return new UpNetworkInterface(networkDevice.getNetworkDeviceType(), host);
    }

    @Override
    public void handleEvent(Notify event) {
        SensorData sensorId = null;
        try {
            sensorId = IMUDriver.validate(event);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to recover IMUDriver data.", t);
            throw new RuntimeException(t);
        }
        AvatarBone bone = skeleton.getBoneBySensorId(sensorId.getId());
        if (bone == null)
            return;
        bone.setRotation(sensorId.getQuaternion());
    }
}
