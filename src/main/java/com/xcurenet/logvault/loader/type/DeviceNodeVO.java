package com.xcurenet.logvault.loader.type;

import lombok.Data;

@Data
public class DeviceNodeVO {
    private String deviceType;
    private String deviceName;
    private String deviceIp;
    private int deviceOrder;
    private String useYn;
}
