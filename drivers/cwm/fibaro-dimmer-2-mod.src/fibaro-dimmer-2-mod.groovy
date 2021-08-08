/**
 *  FIBARO Dimmer 2
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
  definition (name: 'Fibaro Dimmer 2 mod', namespace: 'erocm123', author: 'Fibar Group') {
    capability 'Switch'
    capability 'Switch Level'
    capability 'Energy Meter'
    capability 'Power Meter'
    capability 'Configuration'
    capability 'Health Check'

    command 'reset'
    command 'refresh'
    command 'clearError'
		command 'setConfiguration'

    attribute 'errorMode', 'string'

    fingerprint mfr: '010F', prod: '0102'
    fingerprint deviceId: '0x1101', inClusters:'0x5E,0x86,0x72,0x59,0x73,0x22,0x31,0x32,0x71,0x56,0x98,0x7A,0x20,0x5A,0x85,0x26,0x8E,0x60,0x70,0x75,0x27'
    fingerprint deviceId: '0x1101', inClusters:'0x5E,0x86,0x72,0x59,0x73,0x22,0x31,0x32,0x71,0x56,0x7A,0x20,0x5A,0x85,0x26,0x8E,0x60,0x70,0x75,0x27'
  }

  preferences {

    input(
      name: 'i2',
      title: 'Switch 2',
      description: 'The type of input hooked into S2.',
      type: 'enum',
      options: [
        'Disabled': 'Disabled',
        'Contact Sensor Child Device': 'Contact sensor',
      ],
      defaultValue: 'Disabled',
      required: false
    )

    parameterMap().each {
      input (
        name: it.key,
        title: "${it.num}. ${it.title}",
        description: it.descr,
        type: it.type,
        options: it.options,
        range: (it.min != null && it.max != null) ? "${it.min}..${it.max}" : null,
        defaultValue: it.def,
        required: false
      )
    }

    input 'logging', 'bool', title: 'Debug logging', description: 'Enable logging of debug messages.'
  }
}

def on() { encap(zwave.basicV1.basicSet(value: 255)) }

def off() { encap(zwave.basicV1.basicSet(value: 0)) }

def setLevel(Double level, Double rate = null ) {
  log.debug "${device.displayName} - Executing setLevel( $level, $rate )"
  if (rate == null) {
    encap(zwave.basicV1.basicSet(value: (level > 0) ? level - 1 : 0))
    } else {
    encap(zwave.switchMultilevelV3.switchMultilevelSet(value: (level > 0) ? level - 1 : 0, dimmingDuration: rate))
  }
}

def reset() {
  logging("${device.displayName} - Executing reset()", 'info')
  def cmds = []
  cmds << zwave.meterV3.meterReset()
  cmds << zwave.meterV3.meterGet(scale: 0)
  encapSequence(cmds, 1000)
}

def refresh() {
  logging("${device.displayName} - Executing refresh()", 'info')
  def cmds = []
  cmds << zwave.meterV3.meterGet(scale: 0)
  cmds << zwave.sensorMultilevelV5.sensorMultilevelGet()
  encapSequence(cmds, 1000)
}

def clearError() {
  logging("${device.displayName} - Executing clearError()", 'info')
  sendEvent(name: 'errorMode', value: 'clear')
}

def updated() {
  if ( state.lastUpdated && (now() - state.lastUpdated) < 500 ) return

  if (!childDevices) {
    createChildDevices()
  } else if (device.label != state.oldLabel) {
    childDevices.each {
      def newLabel = "${device.displayName} (i${channelNumber(it.deviceNetworkId)})"
      it.setLabel(newLabel)
    }
    state.oldLabel = device.label
  }
  
  if (childDevices) {
    def childDevice = childDevices.find { it.deviceNetworkId.endsWith('-i2') }
    if (childDevice && settings.'i2' && childDevice.typeName != settings.'i2') {
      changeChildDeviceType(childDevice, settings.'i2', 2)
    }
    childDevice = childDevices.find { it.deviceNetworkId.endsWith('-i3') }
    if (childDevice && settings.'i3' && childDevice.typeName != settings.'i3') {
      changeChildDeviceType(childDevice, settings.'i3', 3)
    }
  }

  def cmds = []
  logging("${device.displayName} - Executing updated()", 'info')

  if (device.currentValue('numberOfButtons') != 5) { sendEvent(name: 'numberOfButtons', value: 5) }

  // runIn(3, 'syncStart')
  state.lastUpdated = now()
}

/**
 * setConfigurationParams command handler that sets user selected configuration parameters on the device. 
 * In case no value is set for a specific parameter the method skips setting that parameter.
 *
 * @param void
 * @return List of Configuration Set commands that will be executed in sequence with 500 ms delay inbetween.
*/

def setConfiguration() {
	// log.debug "Qubino Flush Dimmer: setConfiguration()"
	// def configSequence = []
	// if(settings.param1 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: settings.param1.toInteger()).format()
	// }
	// if(settings.param2 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: settings.param2.toInteger()).format()
	// }
	// if(settings.param3 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: settings.param3.toInteger()).format()
	// }
	// if(settings.param4 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: settings.param4.toInteger()).format()
	// }
	// if(settings.param5 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: settings.param5.toInteger()).format()
	// }
	// if(settings.param6 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 6, size: 2, scaledConfigurationValue: settings.param6.toInteger()).format()
	// }
	// if(settings.param7 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, scaledConfigurationValue: settings.param7.toInteger()).format()
	// }
	// if(settings.param8 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 8, size: 2, scaledConfigurationValue: settings.param8.toInteger()).format()
	// }
	// if(settings.param9 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 9, size: 2, scaledConfigurationValue: settings.param9.toInteger()).format()
	// }
	// if(settings.param10 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: settings.param10.toInteger()).format()
	// }
	// if(settings.param11 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: settings.param11.toInteger()).format()
	// }
	// if(settings.param12 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: settings.param12.toInteger()).format()
	// }
	// if(settings.param13 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: settings.param13.toInteger()).format()
	// }
	// if(settings.param14 != null){
	// 	configSequence << zwave.configurationV1.configurationSet(parameterNumber: 14, size: 1, scaledConfigurationValue: settings.param14.toInteger()).format()
	// }
  def syncNeeded = syncStart()

  def configSequence = syncNext()
	if (configSequence.size() > 0){
		return delayBetween(configSequence, 500)
	}
}

private syncStart() {
  boolean syncNeeded = false
  parameterMap().each {
    if (settings."$it.key" != null) {
      if (state."$it.key" == null) {
        state."$it.key" = [value: null, state: 'synced']
      }

      if (state."$it.key".value != settings."$it.key" as Integer || state."$it.key".state in ['notSynced', 'inProgress']) {
          state."$it.key".value = settings."$it.key" as Integer
          state."$it.key".state = 'notSynced'
          syncNeeded = true
      }
    }
  }

  return syncNeeded
  // if (syncNeeded) {
  //   logging("${device.displayName} - starting sync.", 'info')
  //   multiStatusEvent('Sync in progress.', true, true)
  //   syncNext()
  // }
}

private syncNext() {
  logging("${device.displayName} - Executing syncNext()", 'info')
  def cmds = []
  for ( param in parameterMap() ) {
    if ( state."$param.key"?.value != null && state."$param.key"?.state in ['notSynced', 'inProgress'] ) {
    // if ( state."$param.key"?.value != null) {
      // multiStatusEvent("Sync in progress. (param: ${param.num})", true)
      //state."$param.key"?.state = 'inProgress'
      // cmds << response(encap(zwave.configurationV2.configurationSet(configurationValue: intToParam(state."$param.key".value, param.size), parameterNumber: param.num, size: param.size)))
      // cmds << response(encap(zwave.configurationV2.configurationGet(parameterNumber: param.num)))
      //cmds << zwave.configurationV2.configurationSet(configurationValue: intToParam(state."$param.key".value, param.size), parameterNumber: param.num, size: param.size)
      //cmds << zwave.configurationV2.configurationGet(parameterNumber: param.num)

      def stored = state."$param.key".value
      def value = stored in ArrayList ? stored[0] : stored

      log.debug "Parameter ${param.num} (${param.key}): ${stored}: ${value}"
      log.debug param
      log.debug intToParam(value, param.size)

  		cmds << response(encap(zwave.configurationV2.configurationSet(parameterNumber: param.num, size: param.size, configurationValue: intToParam(value, param.size))))
      // break
    }
  }
  // if (cmds) {
  //   runIn(10, 'syncCheck')
  //   //sendHubCommand(cmds.collect{ new hubitat.device.HubAction(it) })
  //   //commands(cmds)
  //   //cmds.add(secureCmd(zwave.configurationV1.configurationGet(parameterNumber: it)))
  //   log.warn "Don't know how to sync parameters"
  // } else {
  //   runIn(1, 'syncCheck')
  // }
  return cmds
}

private syncCheck() {
  logging("${device.displayName} - Executing syncCheck()", 'info')
  def failed = []
  def incorrect = []
  def notSynced = []
  parameterMap().each {
    if (state."$it.key"?.state == 'incorrect' ) {
      incorrect << it
    } else if ( state."$it.key"?.state == 'failed' ) {
      failed << it
    } else if ( state."$it.key"?.state in ['inProgress', 'notSynced'] ) {
      notSynced << it
    }
  }

  if (failed) {
    logging("${device.displayName} - Sync failed! Check parameter: ${failed[0].num}", 'info')
    sendEvent(name: 'syncStatus', value: 'failed')
    multiStatusEvent("Sync failed! Check parameter: ${failed[0].num}", true, true)
  } else if (incorrect) {
    logging("${device.displayName} - Sync mismatch! Check parameter: ${incorrect[0].num}", 'info')
    sendEvent(name: 'syncStatus', value: 'incomplete')
    multiStatusEvent("Sync mismatch! Check parameter: ${incorrect[0].num}", true, true)
  } else if (notSynced) {
    logging("${device.displayName} - Sync incomplete!", 'info')
    sendEvent(name: 'syncStatus', value: 'incomplete')
    multiStatusEvent('Sync incomplete! Open settings and tap Done to try again.', true, true)
  } else {
    logging("${device.displayName} - Sync Complete", 'info')
    sendEvent(name: 'syncStatus', value: 'synced')
    multiStatusEvent('Sync OK.', true, true)
  }
}

private multiStatusEvent(String statusValue, boolean force = false, boolean display = false) {
  if (!device.currentValue('multiStatus')?.contains('Sync') || device.currentValue('multiStatus') == 'Sync OK.' || force) {
    sendEvent(name: 'multiStatus', value: statusValue, descriptionText: statusValue, displayed: display)
  }
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  def paramKey = parameterMap().find( { it.num == cmd.parameterNumber } ).key
  logging("${device.displayName} - Parameter ${paramKey} value is ${cmd.scaledConfigurationValue} expected " + state."$paramKey".value, 'info')
  state."$paramKey".state = (state."$paramKey".value == cmd.scaledConfigurationValue) ? 'synced' : 'incorrect'
  // syncNext()
}

def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
  logging("${device.displayName} - rejected request!", 'warn')
  for ( param in parameterMap() ) {
    if ( state."$param.key"?.state == 'inProgress' ) {
      state."$param.key"?.state = 'failed'
      break
    }
  }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logging("${device.displayName} - BasicReport received, ignored, value: ${cmd.value}", 'info')
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
  logging("${device.displayName} - SwitchMultilevelReport received, value: ${cmd.value}", 'info')
  sendEvent(name: 'switch', value: (cmd.value > 0) ? 'on' : 'off')
  sendEvent(name: 'level', value: (cmd.value > 0) ? cmd.value + 1 : 0)
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logging("${device.displayName} - SensorMultilevelReport received, $cmd", 'info')
  if ( cmd.sensorType == 4 ) {
    sendEvent(name: 'power', value: cmd.scaledSensorValue, unit: 'W')
    multiStatusEvent("${(device.currentValue('power') ?: '0.0')} W | ${(device.currentValue('energy') ?: '0.00')} kWh")
  }
}

def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
  logging("${device.displayName} - MeterReport received, value: ${cmd.scaledMeterValue} scale: ${cmd.scale} ep: $ep", 'info')
  switch (cmd.scale) {
    case 0:
      sendEvent([name: 'energy', value: cmd.scaledMeterValue, unit: 'kWh'])
      break
    case 2:
      sendEvent([name: 'power', value: cmd.scaledMeterValue, unit: 'W'])
      break
  }
  
  multiStatusEvent("${(device.currentValue('power') ?: '0.0')} W | ${(device.currentValue('energy') ?: '0.00')} kWh")
}

def zwaveEvent(hubitat.zwave.commands.sceneactivationv1.SceneActivationSet cmd) {
  logging("${device.displayName} - SceneActivationSet received, sceneId: ${cmd.sceneId} cmd: ${cmd}", 'info')
  log.info cmd
  def String action
  def Integer button

/*
    switch (cmd.sceneId as Integer) {
        case [10,11,16]: action = "pushed"; button = 1; break
        case 14: action = "pushed"; button = 2; break
        case [20,21,26]: action = "pushed"; button = 3; break
        case 24: action = "pushed"; button = 4; break
        case 25: action = "pushed"; button = 5; break
        case 12: action = "held"; button = 1; break
        case 22: action = "held"; button = 3; break
        case 13: action = "released"; button = 1; break
        case 23: action = "released"; button = 3; break
    }
*/

  switch (cmd.sceneId as Integer) {
    case [20, 21]:
      def children = childDevices
      def childDevice = children.find { it.deviceNetworkId.endsWith('-i2') }
      switch (cmd.sceneId) {
        case 20:
          switch (settings.'i2') {
            case 'Contact Sensor Child Device':
              childDevice.sendEvent(name: 'contact', value: 'open')
              break
          }
          break
        case 21:
          switch (settings.'i2') {
            case 'Contact Sensor Child Device':
              childDevice.sendEvent(name: 'contact', value: 'closed')
              break
          }
          break
      }
      break
  }

  sendEvent(name: 'button', value: action, data: [buttonNumber: button], isStateChange: true)
}

/*
def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
  logging("${device.displayName} - CentralSceneNotification received, sceneNumber: ${cmd.sceneNumber} keyAttributes: ${cmd.keyAttributes}", 'info')
  def String action
  def Integer button
  switch (cmd.sceneNumber as Integer) {
    case [10, 11, 16]: action = 'pushed'; button = 1; break
    case 14: action = 'pushed'; button = 2; break
    case [20, 21, 26]: action = 'pushed'; button = 3; break
    case 24: action = 'pushed'; button = 4; break
    case 25: action = 'pushed'; button = 5; break
    case 12: action = 'held'; button = 1; break
    case 22: action = 'held'; button = 3; break
    case 13: action = 'released'; button = 1; break
    case 23: action = 'released'; button = 3; break
  }

  log.info "button $button $action"
  sendEvent(name: 'button', value: action, data: [buttonNumber: button], isStateChange: true)
}
*/

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
  logging("${device.displayName} - NotificationReport received for ${cmd.event}, parameter value: ${cmd.eventParameter[0]}", 'info')
  switch (cmd.notificationType) {
    case 4:
      switch (cmd.event) {
        case 0: sendEvent(name: 'errorMode', value: 'clear'); break;
        case [1, 2]: sendEvent(name: 'errorMode', value: 'overheat'); break;
      }; break;
    case 8:
      switch (cmd.event) {
        case 0: sendEvent(name: 'errorMode', value: 'clear'); break;
        case 4: sendEvent(name: 'errorMode', value: 'surge'); break;
        case 5: sendEvent(name: 'errorMode', value: 'voltageDrop'); break;
        case 6: sendEvent(name: 'errorMode', value: 'overcurrent'); break;
        case 8: sendEvent(name: 'errorMode', value: 'overload'); break;
        case 9: sendEvent(name: 'errorMode', value: 'loadError'); break;
      }; break;
    case 9:
      switch (cmd.event) {
        case 0: sendEvent(name: 'errorMode', value: 'clear'); break;
        case [1, 3]: sendEvent(name: 'errorMode', value: 'hardware'); break;
      }; break;
    default: logging("${device.displayName} - Unknown zwaveAlarmType: ${cmd.zwaveAlarmType}", 'warn')
  }
}

def parse(String description) {
  def result = []
  log.debug("${device.displayName} - Parsing: ${description}")
  if (description.startsWith('Err 106')) {
    result = createEvent(
      descriptionText: 'Failed to complete the network security key exchange. If you are unable to receive data from it, you must remove it from your network and add it again.',
      eventType: 'ALERT',
      name: 'secureInclusion',
      value: 'failed',
      displayed: true,
    )
  } else if (description == 'updated') {
    return null
  } else {
    def cmd = zwave.parse(description, cmdVersions())
    if (cmd) {
      logging("${device.displayName} - Parsed: ${cmd}")
      zwaveEvent(cmd)
    }
  }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions())
  if (encapsulatedCommand) {
    logging("${device.displayName} - Parsed SecurityMessageEncapsulation into: ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand)
  } else {
    log.warn "Unable to extract Secure command from $cmd"
  }
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
  def version = cmdVersions()[cmd.commandClass as Integer]
    // def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
    // // def ccObj = zwave.commandClass(cmd.commandClass)
    // def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
    // // def encapsulatedCommand = cmd.encapsulatedCommand(getCommandClassVersions())

  def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, version)
  if (encapsulatedCommand) {
    logging("${device.displayName} - Parsed Crc16Encap into: ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand)
  } else {
    log.warn "Unable to extract CRC16 command from $cmd"
  }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions())
  if (encapsulatedCommand) {
    logging("${device.displayName} - Parsed MultiChannelCmdEncap ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
  } else {
    log.warn "Unable to extract MultiChannel command from $cmd"
  }
}

// private getCommandClassVersions() {
//     return [0x20: 1, // Basic V1
//             0x22: 1, // Application Status V1
//             0x26: 3, // Switch Multilevel V3
//             0x27: 1, // Switch All V1
//             0x2B: 1, // Scene Activation V1
//             0x31: 4, // Sensor Multilevel V4
//             0x32: 3, // Meter V3
//             0x56: 1, // CRC16 Encapsulation V1
//             0x59: 1, // Association Group Information V1 (Not handled, as no need)
//             0x5A: 1, // Device Reset Locally V1
//             //0x5E: 2, // Z-Wave Plus Info V2 (Not supported by SmartThings)
//             0x60: 3, // Multi Channel V4 (Device supports V4, but SmartThings only supports V3)
//             0x70: 1, // Configuration V1
//             0x71: 3, // Notification V5 ((Device supports V5, but SmartThings only supports V3)
//             0x72: 2, // Manufacturer Specific V2
//             0x73: 1, // Powerlevel V1
//             0x75: 2, // Protection V2
//             0x7A: 2, // Firmware Update MD V3 (Device supports V3, but SmartThings only supports V2)
//             0x85: 2, // Association V2
//             0x86: 1, // Version V2 (Device supports V2, but SmartThings only supports V1)
//             0x8E: 2, // Multi Channel Association V3 (Device supports V3, but SmartThings only supports V2)
//             0x98: 1  // Security V1
//            ]
// }

private void createChildDevices() {
  state.oldLabel = device.label
  try {
    for (i in [2]) {
      addChildDevice('erocm123', 'Contact Sensor Child Device', "${device.deviceNetworkId}-i${i}",
        [completedSetup: true, label: "${device.displayName} (i${i})",
        isComponent: true, componentName: "i$i", componentLabel: "Input $i"]
      )
    }
  } catch (e) {
    runIn(2, 'sendAlert')
  }
}

private void changeChildDeviceType(childDevice, deviceType, i) {
  log.debug "Changing ${childDevice} to ${deviceType}"
  deleteChildDevice(childDevice.deviceNetworkId)
  if (deviceType != 'Disabled') {
    try {
      addChildDevice('erocm123', deviceType, "${childDevice.deviceNetworkId}",
        [completedSetup: true, label: "${childDevice.displayName}",
        isComponent: true, componentName: "i$i", componentLabel: "Input $i"]
      )
    } catch (e) {
      runIn(2, 'sendAlert')
    }
  }
}

private logging(text, type = 'debug') {
  if (settings.logging == 'true') {
    log."$type" text
  }
}

private secEncap(hubitat.zwave.Command cmd) {
  logging("${device.displayName} - encapsulating command using Secure Encapsulation, command: $cmd", 'info')
  zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crcEncap(hubitat.zwave.Command cmd) {
  logging("${device.displayName} - encapsulating command using CRC16 Encapsulation, command: $cmd", 'info')
  zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format()
}

private multiEncap(hubitat.zwave.Command cmd, Integer ep) {
  logging("${device.displayName} - encapsulating command using MultiChannel Encapsulation, ep: $ep command: $cmd", 'info')
  zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:ep).encapsulate(cmd)
}

private encap(hubitat.zwave.Command cmd, Integer ep) {
  encap(multiEncap(cmd, ep))
}

private encap(List encapList) {
  encap(encapList[0], encapList[1])
}

private encap(Map encapMap) {
  encap(encapMap.cmd, encapMap.ep)
}

private encap(hubitat.zwave.Command cmd) {
  // if (zwaveInfo.zw.contains("s")) {
  //     secEncap(cmd)
  // } else if (zwaveInfo.cc.contains("56")){
  // crcEncap(cmd)
  // } else {
  logging("${device.displayName} - no encapsulation supported for command: $cmd", 'info')
  cmd.format()
// }
}

private encapSequence(cmds, Integer delay=250) {
  delayBetween(cmds.collect { encap(it) }, delay)
}

private encapSequence(cmds, Integer delay, Integer ep) {
  delayBetween(cmds.collect { encap(it, ep) }, delay)
}

private List intToParam(Long value, Integer size = 1) {
  def result = []
  size.times {
        result = result.plus(0, (value & 0xFF) as Short)
        value = (value >> 8)
  }
  return result
}
private Map cmdVersions() {
  [0x5E: 1, 0x86: 1, 0x72: 2, 0x59: 1, 0x73: 1, 0x22: 1, 0x31: 5, 0x32: 3, 0x71: 3, 0x56: 1, 0x98: 1, 0x7A: 2, 0x20: 1, 0x5A: 1, 0x85: 2, 0x26: 3, 0x8E: 2, 0x60: 3, 0x70: 2, 0x75: 2, 0x27: 1]
}

private parameterMap() { [
        [key: 'autoStepTime', num: 6, size: 2, type: 'enum', options: [
                1: '10 ms',
                2: '20 ms',
                3: '30 ms',
                4: '40 ms',
                5: '50 ms',
                10: '100 ms',
                20: '200 ms'
        ], def: '1', min: 0, max: 255 , title: ' Automatic control - time of a dimming step', descr: 'This parameter defines the time of single dimming step during the automatic control.'],
        [key: 'manualStepTime', num: 8, size: 2, type: 'enum', options: [
                1: '10 ms',
                2: '20 ms',
                3: '30 ms',
                4: '40 ms',
                5: '50 ms',
                10: '100 ms',
                20: '200 ms'
        ], def: '5', min: 0, max: 255 , title: 'Manual control - time of a dimming step', descr: 'This parameter defines the time of single dimming step during the manual control.'],
        [key: 'autoOff', num: 10, size: 2, type: 'number', def: 0, min: 0, max: 32767 , title: 'Timer functionality (auto - off)',
         descr: 'This parameter allows to automatically switch off the device after specified time from switching on the light source. It may be useful when the Dimmer 2 is installed in the stairway. (1-32767 sec)'],
        [key: 'autoCalibration', num: 13, size: 1, type: 'enum', options: [
                0: 'readout',
                1: 'force auto-calibration of the load without FIBARO Bypass 2',
                2: 'force auto-calibration of the load with FIBARO Bypass 2'
        ], def: '0', min: 0, max: 2 , title: 'Force auto-calibration', descr: 'Changing value of this parameter will force the calibration process. During the calibration parameter is set to 1 or 2 and switched to 0 upon completion.'],
        [key: 'switchType', num: 20, size: 1, type: 'enum', options: [
                0: 'momentary switch',
                1: 'toggle switch',
                2: 'roller blind switch'
        ], def: '0', min: 0, max: 2 , title: 'Switch type', descr: 'Choose between momentary, toggle and roller blind switch. '],
        [key: 'threeWaySwitch', num: 26, size: 1, type: 'enum', options: [
                0: 'disabled',
                1: 'enabled'
        ], def: '0', min: 0, max: 1 , title: 'The function of 3-way switch', descr: 'Switch no. 2 controls the Dimmer 2 additionally (in 3-way switch mode). Function disabled for parameter 20 set to 2 (roller blind switch).'],
        [key: 'sceneActivation', num: 28, size: 1, type: 'enum', options: [
                0: 'disabled',
                1: 'enabled'
        ], def: '0', min: 0, max: 1 , title: 'Scene activation functionality', descr: 'SCENE ID depends on the switch type configurations.'],
        [key: 'loadControlMode', num: 30, size: 1, type: 'enum', options: [
                0: 'forced leading edge control',
                1: 'forced trailing edge control',
                2: 'control mode selected automatically (based on auto-calibration)'
        ], def: '2', min: 0, max: 2 , title: 'Load control mode', descr: 'This parameter allows to set the desired load control mode. The device automatically adjusts correct control mode, but the installer may force its change using this parameter.'],
        [key: 'levelCorrection', num: 38, size: 2, type: 'number', def: 255, min: 0, max: 255 , title: 'Brightness level correction for flickering loads',
         descr: "Correction reduces spontaneous flickering of some capacitive load (e.g. dimmable LEDs) at certain brightness levels in 2-wire installation. In countries using ripple-control, correction may cause changes in brightness. In this case it is necessary to disable correction or adjust time of correction for flickering loads. (1-254 – duration of correction in seconds. For further information please see the manual)"]
]}
