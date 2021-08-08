/**
*  Copyright 2018 Neil Cumpstey
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
  definition (name: 'Met Office Weather', namespace: 'cwm', author: 'Neil Cumpstey') {
    // capability 'Illuminance Measurement'
    capability 'Polling'
    capability 'Refresh'
    capability 'Relative Humidity Measurement'
    capability 'Sensor'
    capability 'Temperature Measurement'

    attribute 'sunrise', 'date'
    attribute 'sunset', 'date'
    attribute 'localSunrise', 'string'
    attribute 'localSunset', 'string'
    attribute 'city', 'string'
    attribute 'latitude', 'number'
    attribute 'longitude', 'number'
    attribute 'weather', 'string'
    attribute 'wind', 'number'
    attribute 'winddirection', 'string'
    attribute 'wind_gust', 'number'
    attribute 'uv_index', 'string'
    attribute 'weatherIcon', 'string'
    attribute 'forecastIcon', 'string'
    attribute 'feelsLike', 'string'
    attribute 'percentPrecip', 'string'
    attribute 'visibility', 'string'
    attribute 'moonPhase', 'string'
    attribute 'lastSTupdate', 'string'
  }

  preferences {
    section "Met Office", {
      input 'metOfficeApiKey', 'text', title: 'Met Office API key', required: true, displayDuringSetup: true
    }
    section 'Location', {
      input 'locationName', 'text', title: 'Location name', required: false
      input 'locationLatitude', 'number', title: 'Location latitude', required: false
      input 'locationLongitude', 'number', title: 'Location longitude', required: false
    }
    // section 'Units', {
    //   input "pressureUnit", "enum", title: "Pressure units", defaultValue: 'mb',
    //     options: [
    //       'in': 'inches',
    //       'mb': 'millibars',
    //     ]
    //   input "distanceUnit", "enum", title: "Distance units", defaultValue: 'km',
    //     options: [
    //       'mi': 'Miles',
    //       'km': 'Kilometers'
    //     ]
    //   input "heightUnit", "enum", title: "Height units", defaultValue: 'mm',
    //     options: [
    //       "in": "inches",
    //       "mm": "millimeters"
    //     ]
    //   input 'speedUnit', "enum", title: "Speed units", defaultValue: 'kph', 
    //     options: [
    //       "mph": "Miles per hour",
    //       "kph": "Kilometers per hour"
    //     ]
    // }
  }
}

//#region Device event handlers

def installed() {
	runEvery30Minutes('poll')
}

def uninstalled() {
	unschedule()
}

def updated() {
	unschedule()

  state.locationLatitude = settings.locationLatitude ?: location.latitude
  state.locationLongitude = settings.locationLongitude ?: location.longitude

  findNearestMetOfficeSite()

  runEvery30Minutes('poll')
}

//#endregion Device event handlers

//#region Synchronous API requests

/**
 * Make a request to the api for the locations of all sites.
 */
private fetchSites() {
  logger "${device.label}: fetchSites", 'trace'
  
  def requestParams = [
    uri: 'http://datapoint.metoffice.gov.uk/public/data/',
    path: 'val/wxfcs/all/json/sitelist',
    query: [
      'key': settings.metOfficeApiKey,
    ],
    contentType: 'application/json',
  ]

  try {
    httpGet(requestParams) { response ->
      if (response.status == 200) {
        return response.data.Locations.Location.collect {[
          id: it.id,
          name: it.name,
          latitude: it.latitude.toDouble(),
          longitude: it.longitude.toDouble(),
        ]}
      }
      else {
        logger "Api error: ${response.status}: ${response.data}", 'error'
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    logger "Api error: ${e.statusCode}: ${e}", 'error'
  }
}

//#endregion Synchronous API requests

//#region Asynchronous API requests

/**
 * Refresh the data from the API.
 */
private void fetchForecastAsync() {
  logger "${device.label}: fetchForecastAsync", 'trace'

  def requestParams = [
    uri: 'http://datapoint.metoffice.gov.uk/public/data/',
    path: "val/wxfcs/all/json/${state.metOfficeSite.id}",
    query: [
      'res': '3hourly',
      'key': settings.metOfficeApiKey,
    ],
    contentType: 'application/json',
  ]

  asynchttpGet('updateForecastResponseHandler', requestParams)
}

//#endregion Asynchronous API requests

//#region API response handlers

/**
 * Handles an api response containing data about all zones.
 *
 * @param response  Response.
 * @param data  Additional data passed from the calling method.
 */
private void updateForecastResponseHandler(response, data) {
  if (response.hasError()) {
    logger "API error: ${response.status}: ${response.errorData}", 'error'
    return
  }

  logger "${device.label}: updateForecastResponseHandler", 'trace'

  def currentHour = Calendar.instance.get(Calendar.HOUR_OF_DAY)

  def units = response.json.SiteRep.Wx.Param
  def forecastNow = response.json.SiteRep.DV.Location.Period[0].Rep.find{it.$ >= (currentHour * 60).toString()} ?:
                    response.json.SiteRep.DV.Location.Period[1].Rep[0]

  sendEvent(name: 'humidity', value: forecastNow.H, unit: units.find({it.name == 'H'}).units)

  sendEvent(name: 'percentPrecip', value: forecastNow.Pp, unit: units.find({it.name == 'Pp'}).units)

  sendEvent(name: 'feelsLike', value: forecastNow.F, unit: units.find({it.name == 'F'}).units)
  sendEvent(name: 'temperature', value: forecastNow.T, unit: units.find({it.name == 'T'}).units)

  sendEvent(name: 'uv_index', value: forecastNow.U)
  sendEvent(name: 'visibility', value: mapVisibility(forecastNow.V))

  def weather = mapWeatherType(forecastNow.W)
  sendEvent(name: 'weather', value: weather.description)
  sendEvent(name: 'weatherIcon', value: weather.icon)
  sendEvent(name: 'forecastIcon', value: weather.icon)

  sendEvent(name: 'wind', value: forecastNow.S, unit: units.find({it.name == 'S'}).units)
  sendEvent(name: 'winddirection', value: forecastNow.D)
  sendEvent(name: 'wind_gust', value: forecastNow.G, unit: units.find({it.name == 'G'}).units)

  def now = new Date().format('HH:mm:ss M.d.yyyy',location.timeZone)
  sendEvent(name: "lastSTupdate", value: now)
}

//#endregion API response handlers

//#region Commands

def poll() {
  logger "${device.label}: poll", 'trace'

  sendEvent(name: 'city', value: settings.locationName ?: state.metOfficeSite.name)

  // TODO: Calculate for the given location, don't just assume hub values are ok
  sendEvent(name: 'sunrise', value: location.sunrise)
  sendEvent(name: 'sunset', value: location.sunset)
  sendEvent(name: 'localSunrise', value: location.sunrise.format('HH:mm'))
  sendEvent(name: 'localSunset', value: location.sunset.format('HH:mm'))

  def moonData = calculateMoonPhase()
  sendEvent(name: 'moonPhase', value: moonData.phase)

  fetchForecastAsync()
}

def refresh() {
  logger "${device.label}: refresh", 'trace'

  poll()
}

//#endregion Commands

//#region Helpers

/**
 * Find the Met Office site closest to the defined latitude/longitude location.
 */
private void findNearestMetOfficeSite() {
  def sites = fetchSites()
  
  def nearest = sites.min{ calculateDistance(it.latitude, it.longitude, state.locationLatitude, state.locationLongitude) }
  state.metOfficeSite = nearest
}

/**
 * Calculate distance in km between two points.
 * Uses the Vincenty formula to calculate the great circle distance.
 *
 * @param lat1  Latitude of point 1
 * @param long1  Longitude of point 1
 * @param lat2  Latitude of point 2
 * @param long1  Longitude of point 2
 */
private Double calculateDistance(Double lat1, Double long1, Double lat2, Double long2) {
  def r = 6371.01 // Radius of the earth in km
  def radian = Math.PI / 180

  def diffLong = long1 - long2;
  def cosDiffLong = Math.cos(diffLong);
  def sinDiffLong = Math.sin(diffLong);

  def cosLat1 = Math.cos(lat1 * radian)
  def sinLat1 = Math.sin(lat1 * radian)
  def cosLat2 = Math.cos(lat2 * radian)
  def sinLat2 = Math.sin(lat2 * radian)

  // Central angle
  def dca = Math.atan2(Math.sqrt(Math.pow(cosLat2 * sinDiffLong, 2) + Math.pow(cosLat1 * sinLat2 - sinLat1 * cosLat2 * cosDiffLong, 2)), sinLat1 * sinLat2 + cosLat1 * cosLat2 * cosDiffLong);

  // Distance is radius times central angle
  return r * dca;
}

/**
 * Calculate the phase and position of the moon on the given date.
 *
 * @param forDate  Date for which to calculate the moon data
 */
def Map calculateMoonPhase(java.time.LocalDate forDate = java.time.LocalDate.now()) {
  // Normalize values to range 0...1    
  def normalize = { v ->
    v = v - Math.floor(v);
    if (v < 0) {
      v = v + 1
    }

    return v;
  }

  def calculateMoonPosition = { Integer y, Integer m, Integer d ->
    def Double age = 0;       // Moon's age
    def Double distance = 0;  // Moon's distance in earth radii
    def Double latitude = 0;  // Moon's ecliptic latitude
    def Double longitude = 0; // Moon's ecliptic longitude
    def String phase = '';

    // Calculate the Julian date at 12h UT
    def yy = y - Math.floor((12 - m) / 10)
    def mm = m + 9
    if (mm >= 12) { mm = mm - 12 }
    
    def k1 = Math.floor(365.25 * (yy + 4712))
    def k2 = Math.floor(30.6 * mm + 0.5)
    def k3 = Math.floor(Math.floor((yy / 100) + 49) * 0.75) - 38
    
    def jd = k1 + k2 + d + 59                 // for dates in Julian calendar
    if (jd > 2299160) { jd = jd - k3 }        // for Gregorian calendar
        
    // Calculate moon's age in days
    def ip = normalize((jd - 2451550.1) / 29.530588853)
    age = ip * 29.53
    
    // TODO: Tweak these to get a better representaion of precise new and full moon times.
    if (     age <  1.84566) { phase = 'New moon' }
    else if (age <  5.53699) { phase = 'Evening crescent' }
    else if (age <  9.22831) { phase = 'First quarter' }
    else if (age < 12.91963) { phase = 'Waxing gibbous' }
    else if (age < 16.61096) { phase = 'Full moon' }
    else if (age < 20.30228) { phase = 'Waning gibbous' }
    else if (age < 23.99361) { phase = 'Third quarter' }
    else if (age < 27.68493) { phase = 'Morning crescent' }
    else                     { phase = 'New moon' }

    // Convert phase to radians
    ip = ip * 2 * Math.PI

    // Calculate moon's distance
    def dp = 2 * Math.PI * normalize((jd - 2451562.2) / 27.55454988)
    distance = 60.4 - 3.3 * Math.cos(dp) - 0.6 * Math.cos(2 * ip - dp) - 0.5 * Math.cos(2 * ip)

    // Calculate moon's ecliptic latitude
    def np = 2 * Math.PI * normalize((jd - 2451565.2) / 27.212220817)
    latitude = 5.1 * Math.sin(np)

    // Calculate moon's ecliptic longitude
    def rp = normalize((jd - 2451555.8) / 27.321582241)
    longitude = 360 * rp + 6.3 * Math.sin(dp) + 1.3 * Math.sin(2 * ip - dp) + 0.7 * Math.sin(2 * ip)

    // Correcting if longitude is greater than 360!
    if (lo > 360) lo = lo - 360

    return [
      'phase': phase,
      'age': age,
      'distance': distance,
      'latitude': latitude,
      'longitude': longitude,
    ]
  }

  def data = calculateMoonPosition(forDate.getYear(), forDate.getMonthValue(), forDate.getDayOfMonth())
  return data
}

/**
 * Map the weather code returned by the Met Office to a human-readable
 * description and an icon name.
 */
private Map mapWeatherType(String code) {
  return [
    'NA': [description: 'Not available', icon: ''],
    '0': [description: 'Clear night', icon: 'nt_clear'],
    '1': [description: 'Sunny day', icon: 'sunny'],
    '2': [description: 'Partly cloudy (night)', icon: 'nt_partlycloudy'],
    '3': [description: 'Partly cloudy (day)', icon: 'partlycloudy'],
    '5': [description: 'Mist', icon: 'hazy'],
    '6': [description: 'Fog', icon: 'fog'],
    '7': [description: 'Cloudy', icon: 'cloudy'],
    '8': [description: 'Overcast', icon: 'cloudy'],
    '9': [description: 'Light rain shower (night)', icon: 'nt_chancerain'],
    '10': [description: 'Light rain shower (day)', icon: 'chancerain'],
    '11': [description: 'Drizzle', icon: 'rain'],
    '12': [description: 'Light rain', icon: 'rain'],
    '13': [description: 'Heavy rain shower (night)', icon: 'nt_rain'],
    '14': [description: 'Heavy rain shower (day)', icon: 'rain'],
    '15': [description: 'Heavy rain', icon: 'rain'],
    '16': [description: 'Sleet shower (night)', icon: 'nt_chancesleet'],
    '17': [description: 'Sleet shower (day)', icon: 'chancesleet'],
    '18': [description: 'Sleet', icon: 'sleet'],
    '19': [description: 'Hail shower (night)', icon: 'nt_chancerain'],
    '20': [description: 'Hail shower (day)', icon: 'chancerain'],
    '21': [description: 'Hail', icon: 'rain'],
    '22': [description: 'Light snow shower (night)', icon: 'nt_chanceflurries'],
    '23': [description: 'Light snow shower (day)', icon: 'chanceflurries'],
    '24': [description: 'Light snow', icon: 'flurries'],
    '25': [description: 'Heavy snow shower (night)', icon: 'nt_chancesnow'],
    '26': [description: 'Heavy snow shower (day)', icon: 'chancesnow'],
    '27': [description: 'Heavy snow', icon: 'snow'],
    '28': [description: 'Thunder shower (night)', icon: 'nt_chancetstorms'],
    '29': [description: 'Thunder shower (day)', icon: 'chancetstorms'],
    '30': [description: 'Thunder', icon: 'tstorms'],
  ][code] ?: [description: 'Unknown', icon: '']
}

/**
 * Map the visibility code returned by the Met Office to a human-readable
 * description.
 */
private String mapVisibility(String code) {
  return [
    'UN': 'Unknown',
    'VP': 'Very poor - Less than 1 km',
    'PO': 'Poor - Between 1-4 km',
    'MO': 'Moderate - Between 4-10 km',
    'GO': 'Good - Between 10-20 km',
    'VG': 'Very good - Between 20-40 km',
    'EX': 'Excellent - More than 40 km',
  ][code] ?: 'Unknown'
}

/**
 * Log message if logging is configured for the specified level.
 */
private void logger(message, String level = 'debug') {
  state.logLevel = 5
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
      if (state.logLevel >= 4) log.debug message
      break
  }
}

//#endregion Helpers
