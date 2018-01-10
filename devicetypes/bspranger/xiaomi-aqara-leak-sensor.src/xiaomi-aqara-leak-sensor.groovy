/**
 *  Xiaomi Aqara Leak Sensor
 *
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
 *  Based on original DH by Eric Maycock 2015 and Rave from Lazcad
 *
 *  Change log:
 *  Added DH Colours
 *  Added 100% battery max
 *  Fixed battery parsing problem
 *  Added lastcheckin attribute and tile
 *  Added extra tile to show when last opened
 *  Colours to confirm to new smartthings standards
 *  Added ability to force override current state to Open or Closed.
 *  Added experimental health check as worked out by rolled54.Why
 *  bspranger - Adding Aqara Support
 *  Rinkelk - added date-attribute support for Webcore
 *  Rinkelk - Changed battery percentage with code from cancrusher
 *  Rinkelk - Changed battery icon according to Mobile785
 *  sulee - Added endpointId copied from GvnCampbell's DH - Detects sensor when adding
 *  sulee - Track battery as average of min and max over time
 *  sulee - Clean up some of the code
 *  bspranger - renamed to bspranger to remove confusion of a4refillpad
 *  veeceeoh - added battery parse on button press
 */

metadata {
    definition (name: "Xiaomi Aqara Leak Sensor", namespace: "bspranger", author: "bspranger") {
        capability "Configuration"
        capability "Sensor"
        capability "Water Sensor"
        capability "Refresh"
        capability "Battery"
        capability "Health Check"

        attribute "lastCheckin", "String"
        attribute "lastOpened", "String"
        attribute "lastOpenedDate", "Date"
        attribute "lastCheckinDate", "Date"
        
        fingerprint endpointId: "01", profileId: "0104", deviceId: "0402", inClusters: "0000,0003,0001", outClusters: "0019", manufacturer: "LUMI", model: "lumi.sensor_wleak.aq1", deviceJoinName: "Xiaomi Leak Sensor"

        command "Refresh"
    }

    simulator {
        status "closed": "on/off: 0"
        status "open": "on/off: 1"
    }

   tiles(scale: 2) {
        multiAttributeTile(name:"water", type: "generic", width: 6, height: 4){
            tileAttribute ("device.water", key: "PRIMARY_CONTROL") {
                attributeState "dry", label:"Dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff"
                attributeState "wet", label:"Wet", icon:"st.alarm.water.wet", backgroundColor:"#00A0DC"
            }
        }
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
            state "default", label:'${currentValue}%', unit:"",
            backgroundColors: [
                [value: 10, color: "#bc2323"],
                [value: 26, color: "#f1d801"],
                [value: 51, color: "#44b621"] 
            ]
        }
        valueTile("lastcheckin", "device.lastCheckin", decoration: "flat", inactiveLabel: false, width: 4, height: 1) {
            state "default", label:'Last Checkin:\n${currentValue}'
        }
        valueTile("lastopened", "device.lastOpened", decoration: "flat", inactiveLabel: false, width: 4, height: 1) {
            state "default", label:'Last Wet:\n${currentValue}'
        }
        standardTile("resetClosed", "device.resetClosed", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
            state "default", action:"resetClosed", label: "Override Close", icon:"st.contact.contact.closed"
        }
        standardTile("resetOpen", "device.resetOpen", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
            state "default", action:"resetOpen", label: "Override Open", icon:"st.contact.contact.open"
        }
        standardTile("refresh", "command.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
            state "default", label:'refresh', action:"refresh.refresh", icon:"st.secondary.refresh-icon"
        }

        main (["water"])
        details(["water","battery","lastcheckin","lastopened","resetClosed","resetOpen","refresh"])
   }
}

def parse(String description) {
    def linkText = getLinkText(device)
    log.debug "${linkText} Description:${description}"
    
    // send event for heartbeat
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    def nowDate = new Date(now).getTime()
    sendEvent(name: "lastCheckin", value: now)
    sendEvent(name: "lastCheckinDate", value: nowDate)

    Map map = [:]

    if (description?.startsWith('zone status')) {
        map = parseZoneStatusMessage(description)
        if (map.value == "closed") {
            sendEvent(name: "lastOpened", value: now)
            sendEvent(name: "lastOpenedDate", value: nowDate) 
        }
    } else if (description?.startsWith('catchall:')) {
        map = parseCatchAllMessage(description)
    } else if (description?.startsWith('read attr - raw:')) {
        map = parseReadAttr(description)
    }

    log.debug "${linkText}: Parse returned ${map}"
    def results = map ? createEvent(map) : null
    return results
}

private Map parseZoneStatusMessage(String description) {
    def linkText = getLinkText(device)
    def result = [
        name: 'water',
        value: value,
        descriptionText: 'water contact'
    ]
    if (description?.startsWith('zone status')) {
        if (description?.startsWith('zone status 0x0001')) { // detected water
            result.value = "wet"
            result.descriptionText = "${linkText} has detected water"
        } else if (description?.startsWith('zone status 0x0000')) { // did not detect water
            result.value = "dry"
            result.descriptionText = "${linkText} is dry"
        }
        return result
    }
    
    return [:]
}

private Map getBatteryResult(rawValue) {
    def linkText = getLinkText(device)
    def result = [
        name: 'battery',
        value: '--',
        unit: "%",
        translatable: true
    ]

    def rawVolts = rawValue / 1000
    //def maxBattery = state.maxBattery ?: 0
    //def minBattery = state.minBattery ?: 0

    //if (maxBattery == 0 || rawVolts > minBattery)
    //    state.maxBattery = maxBattery = rawVolts

    //if (minBattery == 0 || rawVolts < minBattery)
    //    state.minBattery = minBattery = rawVolts

    //def volts = (maxBattery + minBattery) / 2

    def minVolts = 2.7
    def maxVolts = (state.maxBatteryVoltage > 3.0)?state.maxBatteryVoltage:3.0
    def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
    def roundedPct = Math.round(pct * 100)
    result.value = Math.min(100, roundedPct)
    result.descriptionText = "${linkText}: raw battery is ${rawVolts}v" //, state: ${volts}v, ${minBattery}v - ${maxBattery}v"
    return result
}

private Map parseCatchAllMessage(String description) {
    def linkText = getLinkText(device)
    Map resultMap = [:]
    def cluster = zigbee.parse(description)
    log.debug "${linkText}: Parsing CatchAll: '${cluster}'"

    if (cluster) {
        switch(cluster.clusterId) {
            case 0x0000:
                if ((cluster.data.get(4) == 1) && (cluster.data.get(5) == 0x21)) // Check CMD and Data Type
                    resultMap = getBatteryResult((cluster.data.get(7)<<8) + cluster.data.get(6))
            break
        }
    }
    return resultMap
}

// Parse raw data on reset button press to retrieve reported battery voltage
private Map parseReadAttr(String description) {
    def buttonRaw = (description - "read attr - raw:")
    Map resultMap = [:]

    def cluster = description.split(",").find {it.split(":")[0].trim() == "cluster"}?.split(":")[1].trim()
    def attrId = description.split(",").find {it.split(":")[0].trim() == "attrId"}?.split(":")[1].trim()
    def value = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    def model = value.split("01FF")[0]
    def data = value.split("01FF")[1]
    //log.debug "cluster: ${cluster}, attrId: ${attrId}, value: ${value}, model:${model}, data:${data}"
    
    if (data[4..7] == "0121") {
    	def MaxBatteryVoltage = (Integer.parseInt((data[10..11] + data[8..9]),16))/1000
        state.maxBatteryVoltage = MaxBatteryVoltage
    }

    if (cluster == "0000" && attrId == "0005")  {
        resultMap.name = 'Model'
        resultMap.value = ""
        resultMap.descriptionText = "device model"
        // Parsing the model
        for (int i = 0; i < model.length(); i+=2) 
        {
            def str = model.substring(i, i+2);
            def NextChar = (char)Integer.parseInt(str, 16);
            resultMap.value = resultMap.value + NextChar
        }
        return resultMap
    }
    
    return [:]    
}

def configure() {
    def linkText = getLinkText(device)
    log.debug "${linkText}: configuring"
    return zigbee.readAttribute(0x0001, 0x0021) + zigbee.configureReporting(0x0001, 0x0021, 0x20, 600, 21600, 0x01)
}

def refresh() {
    def linkText = getLinkText(device)
    log.debug "${linkText}: refreshing"
    return zigbee.readAttribute(0x0001, 0x0021) + zigbee.configureReporting(0x0001, 0x0021, 0x20, 600, 21600, 0x01)
}

def resetClosed() {
	sendEvent(name:"contact", value:"closed")
} 

def resetOpen() {
	sendEvent(name:"contact", value:"open")
}

def installed() {
    checkIntervalEvent("installed");
}

def updated() {
    checkIntervalEvent("updated");
}

private checkIntervalEvent(text) {
    // Device wakes up every 1 hours, this interval allows us to miss one wakeup notification before marking offline
    def linkText = getLinkText(device)
    log.debug "${linkText}: Configured health checkInterval when ${text}()"
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}
