/**
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 * Modified from the Smartthings Generic Z-Wave Device Handler
 *
 */
 
metadata {
	definition (name: "Heiman Z-Wave Smoke Detector", namespace: "Scruffy-sjb", author: "Scruffy-sjb and SmartThings", genericHandler: "Z-Wave") {
		capability "Smoke Detector"
		capability "Sensor"
		capability "Battery"
		
		command "resetToClear"
		command "resetBatteryReplacedDate"
		
		attribute "smoke", "string"
        attribute "batteryLastReplaced", "String"

		fingerprint deviceId: "0x1000", mfr: "0260", inClusters: "0x5E,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x9F,0x71,0x84,0x6C", deviceJoinName: "Heiman Z-Wave Smoke Detector HSISA-Z"
		fingerprint mfr: "0138", prod: "0001", model: "0001", deviceJoinName: "First Alert Smoke Detector" //First Alert Smoke Detector
		fingerprint mfr: "026F", prod: "0001", model: "0001", deviceJoinName: "FireAngel Smoke Detector" //FireAngel Thermoptek Smoke Alarm
		fingerprint mfr: "013C", prod: "0002", model: "001E", deviceJoinName: "Philio Smoke Detector" //Philio Smoke Alarm PSG01
		fingerprint mfr: "0154", prod: "0004", model: "0010", deviceJoinName: "POPP Smoke Detector" //POPP 10Year Smoke Sensor
		fingerprint mfr: "0154", prod: "0100", model: "0201", deviceJoinName: "POPP Smoke Detector" //POPP Smoke Detector with Siren
	}
}

def installed() {
	def cmds = []
  //This interval allows us to miss one check-in notification before marking offline
	cmds << createEvent(name: "checkInterval", value: checkInterval * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
	createSmokeEvents("smokeClear", cmds)
	cmds.each { cmd -> sendEvent(cmd) }
	
	if (!device.currentState('batteryLastReplaced')?.value)
		resetBatteryReplacedDate(true)
		
	response(initialPoll())
}

def getCheckInterval() {
	def checkIntervalValue
	switch (zwaveInfo.mfr) {
		case "0138": checkIntervalValue = 2  //First Alert checks in every hour
			break
		default: checkIntervalValue = 8
	}
	return checkIntervalValue
}


def updated() {
  //This interval allows us to miss one check-in notification before marking offline
	sendEvent(name: "checkInterval", value: checkInterval * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def getCommandClassVersions() {
	[
			0x71: 3, // Alarm
			0x72: 1, // Manufacturer Specific
			0x80: 1, // Battery
			0x84: 1, // Wake Up
	]
}


def parse(String description) {
	def results = []
	if (description.startsWith("Err")) {
	    results << createEvent(descriptionText:description, displayed:true)
	} else {
		def cmd = zwave.parse(description, commandClassVersions)
		if (cmd) {
			zwaveEvent(cmd, results)
		}
	}
	log.debug "'$description' parsed to ${results.inspect()}"
	return results
}

def createSmokeEvents(name, results) {
	def text = null
	
	switch (name) {
		case "smoke":
			text = "$device.displayName smoke was detected!"
			// these are displayed:false because the composite event is the one we want to see in the app
			results << createEvent(name: "smoke", value: "detected", descriptionText: text)
			log.info text
			break
		case "tested":
			text = "$device.displayName was tested"
			results << createEvent(name: "smoke", value: "tested", descriptionText: text)
			log.info text
			break
		case "smokeClear":
			text = "$device.displayName smoke is clear"
			results << createEvent(name: "smoke", value: "clear", descriptionText: text)
			name = "clear"
			log.info text
			break
		case "testClear":
			text = "$device.displayName test cleared"
			results << createEvent(name: "smoke", value: "clear", descriptionText: text)
			name = "clear"
			log.info text
			break
	}
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd, results) {
	if (cmd.notificationType == 0x01) {  // Smoke Alarm
		switch (cmd.event) {
			case 0x00:
			case 0xFE:
				createSmokeEvents("smokeClear", results)
				break
			case 0x01:
			case 0x02:
				createSmokeEvents("smoke", results)
				break
			case 0x03:
				createSmokeEvents("tested", results)
				break
		}
	} else switch (cmd.v1AlarmType) {
	
		case 1:
			createSmokeEvents(cmd.v1AlarmLevel ? "smoke" : "smokeClear", results)
			break
		case 12:  // test button pressed
			createSmokeEvents(cmd.v1AlarmLevel ? "tested" : "testClear", results)
			break
		case 13:  // sent every hour -- not sure what this means, just a wake up notification?
			if (cmd.v1AlarmLevel == 255) {
				results << createEvent(descriptionText: "$device.displayName checked in", isStateChange: false)
			} else {
				results << createEvent(descriptionText: "$device.displayName code 13 is $cmd.v1AlarmLevel", isStateChange: true, displayed: false)
			}

			// Clear smoke in case they pulled batteries and we missed the clear msg
			if (device.currentValue("smoke") != "clear") {
				createSmokeEvents("smokeClear", results)
			}

			// Check battery if we don't have a recent battery event
			if (!state.lastbatt || (now() - state.lastbatt) >= 48 * 60 * 60 * 1000) {
				results << response(zwave.batteryV1.batteryGet())
			}
			break
		default:
			results << createEvent(displayed: true, descriptionText: "Alarm $cmd.v1AlarmType ${cmd.v1AlarmLevel == 255 ? 'activated' : cmd.v1AlarmLevel ?: 'deactivated'}".toString())
			break
	}
}

// SensorBinary and SensorAlarm aren't tested, but included to preemptively support future smoke alarms
def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd, results) {
	if (cmd.sensorType == hubitat.zwave.commandclasses.SensorBinaryV2.SENSOR_TYPE_SMOKE) {
		createSmokeEvents(cmd.sensorValue ? "smoke" : "smokeClear", results)
	}
}

def zwaveEvent(hubitat.zwave.commands.sensoralarmv1.SensorAlarmReport cmd, results) {
	if (cmd.sensorType == 1) {
		createSmokeEvents(cmd.sensorState ? "smoke" : "smokeClear", results)
	}
	
}

def zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd, results) {
	results << createEvent(descriptionText: "$device.displayName woke up", isStateChange: false)
	if (!state.lastbatt || (now() - state.lastbatt) >= 56*60*60*1000) {
		results << response([
				zwave.batteryV1.batteryGet().format(),
				"delay 2000",
				zwave.wakeUpV1.wakeUpNoMoreInformation().format()
			])
	} else {
		results << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
	}
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd, results) {
	def map = [ name: "battery", unit: "%", isStateChange: true ]
	state.lastbatt = now()
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "$device.displayName battery is low!"
	} else {
		map.value = cmd.batteryLevel
	}
	results << createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd, results) {
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	state.sec = 1
	log.debug "encapsulated: ${encapsulatedCommand}"
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand, results)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
		results << createEvent(descriptionText: cmd.toString())
	}
}

def zwaveEvent(hubitat.zwave.Command cmd, results) {
	def event = [ displayed: false ]
	event.linkText = device.label ?: device.name
	event.descriptionText = "$event.linkText: $cmd"
	results << createEvent(event)
}

private command(hubitat.zwave.Command cmd) {
	if (zwaveInfo?.zw?.contains("s")) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay = 200) {
	delayBetween(commands.collect { command(it) }, delay)
}

def initialPoll() {
	def request = []
	// check initial battery and smoke sensor state
	request << zwave.batteryV1.batteryGet()
	request << zwave.sensorBinaryV2.sensorBinaryGet(sensorType: zwave.sensorBinaryV2.SENSOR_TYPE_SMOKE)
	commands(request, 500) + ["delay 6000", command(zwave.wakeUpV1.wakeUpNoMoreInformation())]
}

def resetBatteryReplacedDate(paired) {
	def newlyPaired = paired ? " for newly paired sensor" : ""
	sendEvent(name: "batteryLastReplaced", value: new Date())
	log.debug "Setting Battery Last Replaced to current date${newlyPaired}"
}

def resetToClear() {
	sendEvent(name:"smoke", value:"clear")
    log.debug "Resetting to Clear..."
}

/**
 *  Default event handler -  Called for all unhandled events
 */
def zwaveEvent(hubitat.zwave.Command cmd) {
    if (state.debug) {
	log.debug "Unhandled in this driver: $cmd"
	createEvent(descriptionText: "${device.displayName}: ${cmd}")
    }
    else {
	[:]
    }
}
