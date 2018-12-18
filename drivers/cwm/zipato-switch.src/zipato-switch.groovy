/**
 *  Zipato Switch
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A Hubitat driver which wraps a switch on a Zipabox.
 *  It can be used to interact with devices on a Zipabox, such as LightwaveRF
 *  which is not directly compatible with a Hubitat Elevation hub.
 *
 *  ---
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
  definition (name: 'Zipato Switch', namespace: 'cwm', author: 'Neil Cumpstey', vid: 'generic-switch') {
    capability 'Actuator'
    capability 'Switch'

    command 'refresh'
  }

  preferences {
  }
}

//#region Methods called by parent app

/**
 * Stores the configured log level in state.
 *
 * @param logLevel  Configured log level.
 */
void setLogLevel(Integer logLevel) {
  state.logLevel = logLevel
}

/**
 * Stores the Zipato id of this switch in state.
 *
 * @param zipatoId  Id of the switch device within Zipato.
 */
void setZipatoId(String zipatoId) {
  log.debug ("setting zipato id ${zipatoId}")
  state.zipatoId = zipatoId
}

/**
 * Returns the Zipato id of this room.
 */
String getZipatoId() {
  return "${state.zipatoId}"
}

/**
 * Returns the type of this device.
 */
String getZipatoType() {
  return 'switch'
}

/**
 * Updates the state of the switch.
 *
 * @param values  Map of attribute names and values.
 */
void updateState(Map values) {
  logger "${device.label}: updateState: ${values}"

  if (values?.containsKey('switchState')) {
    sendEvent(name: 'switch', value: (values.switchState ? 'on' : 'off'))
  }
}

//#endregion Methods called by parent app

//#region Actions

/**
 * Not used in this device handler.
 */
def parse(String description) {
}

/**
 * Turn off the switch.
 */
def off() {
  logger "${device.label}: off", 'trace'

  sendEvent(name: 'switch', value: 'turningOff', isStateChange: true)

  parent.pushSwitchState(state.zipatoId, false)
}

/**
 * Turn on the switch.
 */
def on() {
  logger "${device.label}: on", 'trace'

  sendEvent(name: 'switch', value: 'turningOn', isStateChange: true)

  parent.pushSwitchState(state.zipatoId, true)
}

/**
 * Refresh all devices.
 */
def refresh() {
  logger "${device.label}: refresh", 'trace'

  parent.refresh()
}

//#endregion Actions

//#region Helpers

void logger(msg, level = 'debug') {
  switch (level) {
    case 'error':
      if (state.logLevel >= 1) log.error msg
      break
    case 'warn':
      if (state.logLevel >= 2) log.warn msg
      break
    case 'info':
      if (state.logLevel >= 3) log.info msg
      break
    case 'debug':
      if (state.logLevel >= 4) log.debug msg
      break
    case 'trace':
      if (state.logLevel >= 5) log.trace msg
      break
    default:
      log.debug msg
      break
  }
}

//#endregion Helpers
