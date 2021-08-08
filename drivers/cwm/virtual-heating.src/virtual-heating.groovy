/**
 *  Genius Hub Room
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A Hubitat driver which wraps a room zone on a Genius Hub.
 *
 *  ---
 *  Disclaimer:
 *  This driver and the associated app are in no way sanctioned or supported by Genius Hub.
 *  All work is based on an unpublished api, which may change at any point, causing this driver or the
 *  app to break. I am in no way responsible for breakage due to such changes.
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
  definition (name: 'Virtual Heating', namespace: 'cwm', author: 'Neil Cumpstey') {
    capability 'Actuator'
    capability 'Refresh'
    capability 'Sensor'
    capability 'Temperature Measurement'
    capability 'Thermostat'

    command 'setTemperature', ['number']
    command 'setThermostatOperatingState', ['string']
  }

  preferences {
    section('General') {
      input 'logging', 'bool', title: 'Debug logging', description: 'Enable logging of debug messages.'
    }
  }
}

//#region Device event handlers

def installed() {
  // Only support heating
  sendEvent(name: 'supportedThermostatFanModes', value: [])
  sendEvent(name: 'supportedThermostatModes', value: ['auto', 'heat', 'off'])

  // TODO: don't know why, but these don't get set
  // TODO: convert to F if required
  sendEvent(name: 'heatingSetpointRange', value: [4, 28])
  sendEvent(name: 'thermostatSetpointRange', value: [4, 28])

  // Set default values
  sendEvent(name: 'temperature', value: 21)
  sendEvent(name: 'thermostatMode', value: 'off')
  sendEvent(name: 'thermostatOperatingState', value: 'idle')
}

def updated() {
  logger "updated", 'trace'

  state.logLevel = settings.logging ? 5 : 2
}

//#endregion Device event handlers

//#region Thermostat commands

def auto() {
  logger 'auto', 'trace'

  sendEvent(name: 'thermostatMode', value: 'auto')
}

def cool() {
  // Don't support cooling
}

def emergencyHeat() {
  heat()
}

def fanAuto() {
  // Don't support fan
}

def fanCirculate() {
  // Don't support fan
}

def fanOn() {
  // Don't support fan
}

def heat() {
  logger 'heat', 'trace'

  sendEvent(name: 'thermostatMode', value: 'heat')
}

def off() {
  logger 'off', 'trace'

  sendEvent(name: 'thermostatMode', value: 'off')
}

def setCoolingSetpoint(Double value) {
  // Don't support cooling
}

def setHeatingSetpoint(Double value) {
  logger "setHeatingSetpoint: ${value}", 'trace'

  // Ensure the set value is within the defined range
  def range = device.currentValue('heatingSetpointRange')
  log.debug("Defined range: ${range}")

  def realValue = range
    ? Math.max(Math.min(value, range[1]), range[0])
    : value

  sendEvent(name: 'heatingSetpoint', value: realValue, unit: "°${temperatureScale}")
  sendEvent(name: 'thermostatSetpoint', value: realValue, unit: "°${temperatureScale}", displayed: false)
}

def setSchedule(schedule) {
  // Don't support schedule
}

def setThermostatFanMode(String mode) {
  // Don't support fan
}

def setThermostatMode(String mode) {
  switch (mode) {
    case 'auto': auto(); break;
    case 'cool': cool(); break;
    case 'emergency heat': emergencyHeat(); break;
    case 'heat': heat(); break;
    case 'off': off(); break;
    default:
      logger "Unrecognised thermostat mode set: ${mode}"
      break;
  }
}

//#endregion Thermostat commands

//#region Custom commands

def setTemperature(value) {
  logger "setTemperature: ${value}", 'trace'

  sendEvent(name: 'temperature', unit: "°${temperatureScale}", value: value.toDouble())
}

def setThermostatOperatingState(value) {
  logger "setThermostatOperatingState: ${value}", 'trace'

  // Only support heating-related states
  if (['heating', 'idle', 'pending heat'].contains(value)) {
    sendEvent(name: 'thermostatOperatingState', value: value)
  }
}

//#endregion Custom commands

//#region Helpers

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

//#endregion Helpers
