/*
 *  Copyright 2015 SmartThings
 *  Copyright 2016 S.Çağlar Onur
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
 */
definition(
    name: "Left It Open",
    namespace: "caglar10ur",
    author: "S.Çağlar Onur",
    description: "Watch contact sensor and notify if left open",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
    section("Monitor this door or window") {
        input "contact", "capability.contactSensor", title: "Sensor to monitor", required: true
    }
    section("And notify me if it's open for more than this many minutes (default 10)") {
        input "openThreshold", "number", title: "Number of minutes", defaultValue: 10, required: false
    }

    section("Delay between notifications (default 10 minutes") {
        input "frequency", "number", title: "Number of minutes", defaultValue: 10, required: false
    }
    section("Via text message at this number (or via push notification if not specified") {
        input("recipients", "contact", title: "Send notifications to") {
            input "phone", "phone", title: "Phone number (optional)", required: false
        }
    }
}

def installed() {
    log.trace "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.trace "Updated with settings: ${settings}"

    unsubscribe()
    // unschedule all tasks
    unschedule()

    initialize()
}

def initialize() {
    subscribe(contact, "contact.open", doorOpen)
    subscribe(contact, "contact.closed", doorClosed)
}

def doorOpen(evt) {
    log.trace "doorOpen($evt.name: $evt.value)"

    def delay = (openThreshold != null && openThreshold != "") ? openThreshold * 60 : 600
    runIn(delay, doorOpenTooLong, [overwrite: false])
}

def doorClosed(evt) {
    log.trace "doorClosed($evt.name: $evt.value)"
}

def doorOpenTooLong() {
    log.trace "doorOpenTooLong()"

    def contactState = contact.currentState("contact")
    if (contactState.value == "open") {
        def elapsed = now() - contactState.rawDateCreated.time
        def threshold = (openThreshold != null && openThreshold != "") ? openThreshold : 10
        // elapsed time is in milliseconds, so the threshold must be converted to milliseconds too
        if (elapsed >= (threshold * 60000) - 1000) {
            def freq = (frequency != null && frequency != "") ? frequency * 60 : 600
            // schedule the next notification
            runIn(freq, doorOpenTooLong, [overwrite: false])

            notify("${contact.displayName} has been left open for ${threshold} minutes.")
        } else {
            log.debug "Contact has not stayed open long enough since last check ($elapsed ms): doing nothing"
        }
    } else {
        log.debug "doorOpenTooLong() called but contact is closed: doing nothing"
    }
}

private notify(msg) {
    log.trace "sendMessage(${msg})"

    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients)
    } else {
        if (phone) {
            sendSms phone, msg
        } else {
            sendPush msg
        }
    }
}
