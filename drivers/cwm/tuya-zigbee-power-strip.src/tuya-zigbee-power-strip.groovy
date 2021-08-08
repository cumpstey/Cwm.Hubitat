import java.lang.Math
import groovy.json.JsonOutput

metadata {
  definition(name: "Tuya ZigBee Power Strip", namespace: "cwm", author: "Neil Cumpstey") {
    capability "Actuator"
    capability "Refresh"

    command "childOn", ["string"]
    command "childOff", ["string"]
    command "allOn"
    command "allOff"

    // Tuya power strip with USB
    fingerprint endpointId: "01", profileId: "0104", deviceId: "0009", inClusters: "0000, 000A, 0004, 0005, 0006", outClusters: "0019", manufacturer: "_TYZB01_vkwryfdr", model: "TS0115", deviceJoinName: "Tuya Zigbee Power Strip"
    fingerprint endpointId: "01", profileId: "0104", deviceId: "0009", inClusters: "0000, 000A, 0004, 0005, 0006", outClusters: "0019", manufacturer: "_TZ3000_o005nuxx", model: "TS011F", deviceJoinName: "Tuya Zigbee Power Strip"
  }

  preferences {
    input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: true
  }
}

def installed() {
  logger 'trace', "installed"

  createChildDevices()
  refresh()
}

def updated() {
  logger 'trace', "updated"

  createChildDevices()
  refresh()
}

def parse(String description) {
  logger 'trace', "parse: ${description}"

  Map eventMap = zigbee.getEvent(description)
  Map eventDescMap = zigbee.parseDescriptionAsMap(description)

  if (!eventMap && eventDescMap) {
    eventMap = [:]
    if (eventDescMap?.clusterId == zigbee.ON_OFF_CLUSTER) {
      eventMap[name] = "switch"
      eventMap[value] = eventDescMap?.value
    }
  }

  if (eventMap) {
    logger 'debug', "eventMap ${eventMap}; eventDescMap ${eventDescMap}"

    def endpointId = device.endpointId
    eventMap[displayed] = true

    // There seem to be duplicate messages sent by the TS0115, one with 'sourceEndpoint'
    // and one with 'endpoint' to identify the socket being reported, when it's switched
    // remotely. When it's switched on the device, only the latter is sent.
    if (eventDescMap?.sourceEndpoint == endpointId) {
      logger 'debug', "parse: sendEvent parent ${eventDescMap.sourceEndpoint}"
      sendEvent(eventMap)
    } else if (eventDescMap?.sourceEndpoint) {
      def childNetworkId = "${device.deviceNetworkId}:${eventDescMap.sourceEndpoint}"
      def childDevice = childDevices.find {
        it.deviceNetworkId == childNetworkId
      }
      if (childDevice) {
        log.debug "parse: sendEvent child ${eventDescMap.sourceEndpoint}"
        childDevice.sendEvent(eventMap)
      } else {
        logger 'warn', "Child device: ${childNetworkId} not found"
      }
    } else if (eventDescMap?.endpoint) {
      def childNetworkId = "${device.deviceNetworkId}:${eventDescMap.endpoint}"
      def childDevice = childDevices.find {
        it.deviceNetworkId == childNetworkId
      }
      if (childDevice) {
        logger 'debug', "parse: sendEvent child ${eventDescMap.endpoint}"
        childDevice.sendEvent(eventMap)
      } else {
        logger 'warn', "Child device: ${childNetworkId} not found"
      } 
    }
  }
}

private void createChildDevices() {
  logger 'trace', "createChildDevices"

  def model = device.getDataValue('model')
  def sockets
  switch (model) {
    case 'TS0115':
    case 'TS011F':
      sockets = 4
      break
    default:
      logger 'warn', "Unrecognised model ${model}. Don't know how many child devices to create."
      return
  }
  
  for (i in 1..sockets) {
    def endpointHexString = zigbee.convertToHexString(i, 2).toUpperCase()
    createChildDevice(endpointHexString, "${device.displayName} socket ${i}", "s$i", "Socket $i")
  }

  switch (model) {
    case 'TS0115':
      createChildDevice('07', "${device.displayName} USB", 'usb', 'USB')
      break
    case 'TS011F':
      createChildDevice('05', "${device.displayName} USB", 'usb', 'USB')
      break
  }
}

private void createChildDevice(String endpointHexString, String deviceLabel, String componentName, String componentLabel) {
  def networkId = "${device.deviceNetworkId}:${endpointHexString}"
  def childDevice = childDevices.find {
    it.deviceNetworkId == networkId
  }
  if (!childDevice) {
    logger 'info', "Creating child device: ${networkId}"
    addChildDevice('cwm', 'Child Switch', networkId,
                   [completedSetup: true, label: deviceLabel,
                    isComponent: true, componentName: componentName, componentLabel: componentLabel]
                  )
  } else {
    logger 'debug', "Child device ${networkId} already exists"
  }
}

private getChildEndpoint(String networkId) {
  if (networkId.isNumber()) {
    return networkId
  }

  networkId.split(":")[-1] as String
}

def allOn() {
  logger 'trace', "allOn"

  childDevices.each {
    if (it.currentValue("switch") != "on") {
      it.on()
    }
  }
}

def allOff() {
  logger 'trace', "allOff"

  childDevices.each {
    if (it.currentValue("switch") != "off") {
      it.off()
    }
  }
}

def childOn(String networkId) {
  logger 'trace', "childOn: ${networkId}"

  def childEndpoint = getChildEndpoint(networkId)
  def endpointInt = zigbee.convertHexToInt(childEndpoint)

  "he cmd 0x${device.deviceNetworkId} 0x${childEndpoint} 0x0006 1 { }"
}

def childOff(String networkId) {
  logger 'trace', "childOff ${networkId}"

  def childEndpoint = getChildEndpoint(networkId)
  def endpointInt = zigbee.convertHexToInt(childEndpoint)

  "he cmd 0x${device.deviceNetworkId} 0x${childEndpoint} 0x0006 0 { }"
}

def ping() {
  return refresh()
}

def refresh() {
  logger 'trace', 'refresh'

  def cmds = zigbee.onOffRefresh()
  childDevices.each {
    def childEndpoint = getChildEndpoint(it.deviceNetworkId)
    def endpointInt = zigbee.convertHexToInt(childEndpoint)
    cmds += zigbee.readAttribute(zigbee.ON_OFF_CLUSTER, 0x0000, [destEndpoint: endpointInt])
  }

  return cmds
}

def poll() {
  refresh()
}

private void logger(String level, message) {
  switch (level) {
    case 'error':
    case 'warn':
    case 'info':
      log."$level" message
      break
    case 'debug':
    case 'trace':
      if (settings.debugLogging) log."$level" message
      break
    default:
      log.info message
      break
  }
}
