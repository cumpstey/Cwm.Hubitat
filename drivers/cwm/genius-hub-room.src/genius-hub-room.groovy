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
  definition (name: 'Genius Hub Room', namespace: 'cwm', author: 'Neil Cumpstey') {
    capability 'Actuator'
    capability 'Battery'
    capability 'Illuminance Measurement'
    capability 'Refresh'
    capability 'Sensor'
    capability 'Temperature Measurement'
    capability 'Thermostat'
    // capability 'Thermostat Heating Setpoint'

    // command 'extraHour'
    // command 'refresh'
    // command 'revert'

    attribute 'operatingState', 'enum', ['off', 'timer', 'override', 'footprint']
    attribute 'overrideEndTime', 'date'
  }

  preferences {
  }
}

//#region Thermostat commands

def auto() {
  revert()
}

def cool() {
  // Don't support cooling
}

def emergencyHeat() {
  logger "${device.label}: emergencyHeat", 'trace'

  def setpoint = device.currentValue('heatingSetpoint')
  def valueInCelsius = convertHubScaleToCelsius(setpoint)
  parent.pushRoomTemperature(state.geniusId, valueInCelsius)
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
  emergencyHeat()
}

def off() {
  // TODO: override off
}

def setCoolingSetpoint(Double setpoint) {
  // Don't support cooling
}

def setHeatingSetpoint(Double setpoint) {
  logger "${device.label}: setHeatingSetpoint: ${value}", 'trace'

  sendEvent(name: 'heatingSetpoint', value: value, unit: "°${temperatureScale}")
  sendEvent(name: 'thermostatSetpoint', value: value, unit: "°${temperatureScale}", displayed: false)

  // TODO: need to send this to Genius Hub, at least in override mode
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
  }
}

//#endregion Thermostat commands

def installed() {
  sendEvent(name: 'supportedThermostatFanModes', value: [])
  sendEvent(name: 'supportedThermostatModes', value: ['auto', 'emergency heat', 'off'])
  sendEvent(name: 'thermostatMode', value: 'off')

  // TODO: we could pull this back from Genius
  sendEvent(name: 'thermostatOperatingState', value: 'idle')
}

//#region Methods called by parent app

/**
 * Stores the Genius Hub id of this room in state.
 *
 * @param geniusId  Id of the room zone within the Genius Hub.
 */
void setGeniusId(Integer geniusId) {
  state.geniusId = geniusId
}

/**
 * Stores the configured log level in state.
 *
 * @param logLevel  Configured log level.
 */
void setLogLevel(Integer logLevel) {
  state.logLevel = logLevel
}

/**
 * Returns the Genius Hub id of this room.
 */
Integer getGeniusId() {
  return state.geniusId
}

/**
 * Returns the type of this device.
 */
String getGeniusType() {
  return 'room'
}

/**
 * Updates the state of the room.
 *
 * @param values  Map of attribute names and values.
 */
void updateState(Map values) {
  logger "${device.label}: updateState: ${values}", 'trace'

  def operatingState = device.currentValue('operatingState')
  
  if (values?.containsKey('operatingState')) {
    // Evidently there's some lag in values being set, so if operatingState is
    // retrieved lower down in this method we get the original value, not the
    // updated value. Ensure we get the latest value in this way.
    operatingState = values.operatingState
    sendEvent(name: 'operatingState', value: values.operatingState)

    switch (operatingState) {
      case 'off':
        sendEvent(name: 'thermostatMode', value: 'off')
        break;
      case 'override':
        sendEvent(name: 'thermostatMode', value: 'emergency heat')
        break;
      case 'timer':
      case 'footprint':
        sendEvent(name: 'thermostatMode', value: 'auto')
        break;
    }
  }

  if (values?.containsKey('sensorTemperature')) {
    def value = convertCelsiusToHubScale(values.sensorTemperature)
    sendEvent(name: 'temperature', value: value, unit: "°${temperatureScale}")
  }

  if (values?.containsKey('minBattery')) {
    sendEvent(name: 'battery', value: values.minBattery, unit: '%')
  }

  if (values?.containsKey('illuminance')) {
    sendEvent(name: 'illuminance', value: values.illuminance, unit: 'lux')
  }
  
  if (operatingState == 'override') {
    if (values?.containsKey('overrideEndTime')) {
      def overrideEndTime = new Date(values.overrideEndTime)
      sendEvent(name: 'overrideEndTime', value: overrideEndTime, displayed: false)
      sendEvent(name: 'overrideEndTimeDisplay', value: "Override ends ${overrideEndTime.format('HH:mm')}", displayed: false)
    }
  } else {
    def currentTemperature = device.currentValue('temperature')
    sendEvent(name: 'overrideEndTime', value: null, displayed: false)
    sendEvent(name: 'overrideEndTimeDisplay', value: null, displayed: false)
    sendEvent(name: 'heatingSetpoint', value: currentTemperature, unit: "°${temperatureScale}", displayed: false)
    sendEvent(name: 'thermostatSetpoint', value: currentTemperature, unit: "°${temperatureScale}", displayed: false)
    // sendEvent(name: 'heatingSetpointRange', value: [currentTemperature, currentTemperature], unit: "°${temperatureScale}", displayed: false)
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
 * Extend the override by an hour.
 */
def extraHour() {
  logger "${device.label}: refresh", 'trace'

  if (device.currentValue('operatingState') == 'override') {
    def overrideEndTime = device.currentValue('overrideEndTime')
    def period = 3600
    if (overrideEndTime) {
      period = (int)(((overrideEndTime.getTime() + 3600 * 1000) - now()) / 1000)
    }

    parent.pushOverridePeriod(state.geniusId, period)
  }
}

/**
 * Refresh all devices.
 */
def refresh() {
  logger "${device.label}: refresh", 'trace'

  parent.refresh()
}

/**
 * Revert the operating mode to the default.
 */
def revert() {
  logger "${device.label}: revert", 'trace'
  
  if (device.currentValue('operatingState') == 'override') {
    parent.revert(state.geniusId)
  }
}

/**
 * Sets the operating mode to override and the target temperature to the specified value.
 *
 * @param value  Target temperature, in either Celsius or Fahrenheit as defined by the SmartThings hub settings.
 */
// def setHeatingSetpoint(Double value) {
//   logger "${device.label}: setHeatingSetpoint: ${value}", 'trace'

//   sendEvent(name: 'heatingSetpoint', value: value, unit: "°${temperatureScale}")
//   sendEvent(name: 'thermostatSetpoint', value: value, unit: "°${temperatureScale}", displayed: false)
//   // sendEvent(name: 'heatingSetpointRange', value: [value, value], unit: "°${temperatureScale}")

//   def valueInCelsius = convertHubScaleToCelsius(value)
//   parent.pushRoomTemperature(state.geniusId, valueInCelsius)
// }

//#endregion Actions

//#region Helpers

/**
 * Converts a Celsius temperature value to the scale defined in the SmartThings hub settings.
 *
 * @param valueInCelsius  Temperature in Celsius.
 */
private Double convertCelsiusToHubScale(Double valueInCelsius) {
  def value = (temperatureScale == "F") ? ((valueInCelsius * 1.8) + 32) : valueInCelsius
  return value.round(1)
}

/**
 * Converts a temperature value on the scale defined in the SmartThings hub settings to Celsius.
 *
 * @param valueInHubScale  Temperature in the unit defined in the SmartThings hub settings.
 */
private Double convertHubScaleToCelsius(Double valueInHubScale) {
  def value = (temperatureScale == "C") ? valueInHubScale : ((valueInHubScale - 32) / 1.8)
  return value.round(1)
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

//#endregion Helpers
