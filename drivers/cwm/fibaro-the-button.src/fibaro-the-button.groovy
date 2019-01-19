/**
 *  Fibaro The Button
 *
 *  Notes
 *  -----
 *  I've not used the DoubleTapable capability, because with the possibility of up to 5 taps per button and the
 *  lack of any consistent way of representing more than 2 taps while using DoubleTapable, the button number
 *  workaround seemed easier to understand.
 *
 *  ---
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
  definition (name: 'Fibaro The Button', namespace: 'cwm', author: 'Neil Cumpstey') {
    capability 'Actuator'
    capability 'Battery'
    capability 'Configuration'
    capability 'HoldableButton' 
    capability 'PushableButton'
    capability 'ReleasableButton'

    fingerprint mfr: '010F', prod: '0F01'
    fingerprint deviceId: '0x1801', inClusters: '0x5E,0x59,0x80,0x73,0x56,0x98,0x7A,0x5B,0x85,0x84,0x5A,0x86,0x72,0x71,0x70,0x8E,0x9C'
    fingerprint deviceId: '0x1801', inClusters: '0x5E,0x59,0x80,0x73,0x56,0x7A,0x5B,0x85,0x84,0x5A,0x86,0x72,0x71,0x70,0x8E,0x9C'
    fingerprint deviceId: '0x1000', inClusters: '0x5E,0x86,0x72,0x5B,0x5A,0x59,0x85,0x73,0x84,0x80,0x71,0x56,0x70,0x8E,0x7A,0x98,0x9C'
  }

  preferences {
    section('General') {
      input 'logging', 'bool', title: 'Debug logging', description: 'Enable logging of debug messages.'
    }
  }
}

//#region Device event handlers

def installed() {
  sendEvent(name: 'numberOfButtons', value: 5)
}

def updated() {
  logger 'updated', 'trace'

  state.logLevel = settings.logging ? 5 : 2

  sendEvent(name: 'numberOfButtons', value: 5)
  state.lastUpdated = now()
}

//#endregion Device event handlers

//#region Commands

/**
 * Configures the device.
 * Actually just requests the battery level, as there's nothing to configure.
 */
def configure() {
  logger "configure", 'trace'

  def cmds = []
  encap(zwave.batteryV1.batteryGet())
}

//#endregion Commands

//#region Z-wave event handling

/**
 * Parse and handle information sent by the device.
 *
 * @param description  Information sent by the device.
 */
def parse(String description) {
  logger "parse: ${description}", 'trace'

  def result = []
  if (description.startsWith("Err 106")) {
    result = createEvent(
      descriptionText: "Failed to complete the network security key exchange. If you are unable to receive data from it, you must remove it from your network and add it again.",
      eventType: "ALERT",
      name: "secureInclusion",
      value: "failed",
      displayed: true,
    )
  } else if (description == "updated") {
    return null
  } else {
    def cmd = zwave.parse(description, cmdVersions())
    logger "Parsed command: ${cmd}"
    if (cmd) {
      zwaveEvent(cmd)
    }
  }
}

def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  logger "Wake up notification: ${cmd}", 'info'

  [response(encap(zwave.batteryV1.batteryGet()))]
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  logger "Configuration report: ${cmd}", 'info'
}

def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
  logger "Application rejected request: ${cmd}", 'warn'
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  logger "Battery report: ${cmd}", 'info'

  sendEvent(name: 'battery', value: cmd.batteryLevel, unit: '%', displayed: true)
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
  logger "Central scene notification: ${cmd}", 'info'

  switch (cmd.keyAttributes as Integer) {
    case 0:
      sendEvent(name: 'pushed', value: 1, isStateChange: true)
      break
    case 1:
      sendEvent(name: 'released', value: 1, isStateChange: true)
      break
    case 2:
      sendEvent(name: 'held', value: 1, isStateChange: true)
      break
    case 3:
      sendEvent(name: 'pushed', value: 2, isStateChange: true)
      break
    case 4:
      sendEvent(name: 'pushed', value: 3, isStateChange: true)
      break
    case 5:
      sendEvent(name: 'pushed', value: 4, isStateChange: true)
      break
    case 6:
      sendEvent(name: 'pushed', value: 5, isStateChange: true)
      break
  }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  logger "zwaveEvent(SecurityMessageEncapsulation): ${cmd}", 'trace'

  def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions())
  if (encapsulatedCommand) {
    logger "Parsed command: ${encapsulatedCommand}"
    zwaveEvent(encapsulatedCommand)
  } else {
    logger "Could not extract secure command from ${cmd}", 'warn'
  }
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
  logger "zwaveEvent(Crc16Encap): ${cmd}", 'trace'

  def version = cmdVersions()[cmd.commandClass as Integer]
  def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, version)
  if (encapsulatedCommand) {
    logger "Parsed command: ${encapsulatedCommand}"
    zwaveEvent(encapsulatedCommand)
  } else {
    logger "Could not extract CRC16 command from ${cmd}", 'warn'
  }
}

//#endregion Z-wave event handling

//#region Helpers

/**
 * Encapsulate a command with secure encapsulation.
 *
 * @param cmds  Command
 */
private secEncap(hubitat.zwave.Command cmd) {
  logger "secEncap: ${cmd}", 'trace'

  zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

/**
 * Encapsulate a command with CRC16 encapsulation.
 *
 * @param cmds  Command
 */
private crcEncap(hubitat.zwave.Command cmd) {
  logger "crcEncap: ${cmd}", 'trace'

  zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format()
}

/**
 * Encapsulate a command.
 *
 * @param cmds  Command
 */
private encap(hubitat.zwave.Command cmd) {
  logger "encap: ${cmd}", 'trace'

  if (zwaveInfo.zw.contains("s")) {
    secEncap(cmd)
  } else if (zwaveInfo.cc.contains("56")){
    crcEncap(cmd)
  } else {
    logger "No encapsulation supported for command: ${cmd}", 'warn'

    cmd.format()
  }
}

/**
 * Encapsulate a sequence of commands.
 *
 * @param cmds  Commands
 * @param delay  Interval between each command.
 */
private encapSequence(cmds, Integer delay = 250) {
  logger "encapSequence: ${cmd}, ${delay}", 'trace'

  delayBetween(cmds.collect{ encap(it) }, delay)
}

private List intToParam(Long value, Integer size = 1) {
  def result = []
  size.times {
    result = result.plus(0, (value & 0xFF) as Short)
    value = (value >> 8)
  }
 
  return result
}

/**
 * Log message if logging is configured for the specified level.
 */
private void logger(message, String level = 'debug') {
  switch (level) {
    case 'error':
      if (state.logLevel >= 1) log.error message
      break
    case 'warn':
      if (state.logLevel >= 2) log.warn message
      break
    case 'info':
      if (state.logLevel >= 3) log.info message
      break
    case 'debug':
      if (state.logLevel >= 4) log.debug message
      break
    case 'trace':
      if (state.logLevel >= 5) log.trace message
      break
    default:
      log.debug message
      break
  }
}

/**
 * Returns the implemented versions of the various z-wave commands.
 */
private Map cmdVersions() {
  [
    0x5A: 1,
    0x56: 1, // CRC16 Encapsulation
    0x59: 1, // Association Group Info
    0x5B: 1,
    0x5E: 2,
    0x70: 2, // Configuration
    0x71: 1, // Alarm (Notification)
    0x72: 2, // Manufacturer Specific
    0x73: 1, // Powerlevel
    0x7A: 3,
    0x80: 1,
    0x84: 2, // Wake Up
    0x85: 2, // Association
    0x86: 2, // Version
    0x8E: 2, // Multi Channel Association
    0x98: 1, // Security
    0x9C: 1
  ]
}

//#endregion Helpers
