package org.unbiquitous.unbihealth.avatar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.unbiquitous.unbihealth.avatar.data.AvatarBone;
import org.unbiquitous.unbihealth.avatar.data.AvatarSkeleton;
import org.unbiquitous.unbihealth.imu.IMUDriver;
import org.unbiquitous.unbihealth.imu.SensorData;
import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.UOSLogging;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;
import org.unbiquitous.uos.core.adaptabitilyEngine.NotifyException;
import org.unbiquitous.uos.core.adaptabitilyEngine.SmartSpaceGateway;
import org.unbiquitous.uos.core.adaptabitilyEngine.UosEventListener;
import org.unbiquitous.uos.core.applicationManager.CallContext;
import org.unbiquitous.uos.core.deviceManager.DeviceListener;
import org.unbiquitous.uos.core.deviceManager.DeviceManager;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An UOS driver that models an hierarchy of objects that can rotate relative to their parent (just like the human body).
 *
 * @author Luciano Santos
 */
public class AvatarDriver implements UosEventDriver, DeviceListener, UosEventListener {
    public static final String DRIVER_NAME = "org.unbiquitous.ubihealth.AvatarDriver";
    public static final String CHANGE_EVENT_NAME = "change";
    public static final String CHANGE_NEW_DATA_PARAM_NAME = "newData";
    public static final String SKELETON_KEY = "avatardriver.skeleton";
    public static final String DEFAULT_SKELETON = "[{\"id\":\"root\",\"sensorId\":\"root\"}]";

    private static final UpDriver _driver = new UpDriver(DRIVER_NAME) {
        {
            addEvent(CHANGE_EVENT_NAME).addParameter(CHANGE_NEW_DATA_PARAM_NAME, UpService.ParameterType.MANDATORY);
        }
    };
    private static Logger logger = UOSLogging.getLogger();
    private static ObjectMapper mapper = new ObjectMapper();

    private Gateway gateway;
    private DeviceManager deviceManager;
    private String instanceId;
    private ConcurrentHashMap<UpNetworkInterface, UpDevice> listeners = new ConcurrentHashMap<UpNetworkInterface, UpDevice>();
    private AvatarSkeleton skeleton;

    public String getInstanceId() {
        return instanceId;
    }

    public Skeleton getSkeleton() {
        return skeleton;
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

        try {
            deviceManager = ((SmartSpaceGateway) gateway).getDeviceManager();
            deviceManager.addDeviceListener(this);
        } catch (ClassCastException e) {
            logger.log(Level.SEVERE, DRIVER_NAME + ": a SmartSpaceGateway is expected.", e);
            throw e;
        }

        logger.info(DRIVER_NAME + ": init instance [" + id + "].");
    }

    @Override
    public void destroy() {
        listeners.clear();
        deviceManager.removeDeviceListener(this);
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
    public void deviceRegistered(UpDevice device) {

    }

    @Override
    public void deviceUnregistered(UpDevice device) {

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
