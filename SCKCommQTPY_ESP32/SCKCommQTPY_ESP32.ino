/**
 * Sketch to echo usb to serial 1 and usb to connect to SCK-300 and TIC based SCK-300S
 * based boards. Essentially acts as an alternative to a USB TTL converter by making use
 * on an arduino board, in this case the Adafruit QT Py ESP32 Pico. I also makes use of 
 * the wifi module for control through a web interface.
 * 
 * https://www.adafruit.com/product/5395
 * https://forum.arduino.cc/t/bluetooth-classic-and-ble-in-esp32-wroom/891594/10
 * https://quadmeup.com/arduino-esp32-and-3-hardware-serial-ports/
 * https://forum.seeedstudio.com/t/how-to-use-serial1-with-xiao-esp32c3/266306/4
 * 
 * Select Board "Adafruit QT Py ESP32" in the Arduino IDE
 */
#include <Tic.h>
#include "BluetoothSerial.h"
#include <HardwareSerial.h>
#include <Adafruit_NeoPixel.h>
#include <WiFi.h>
#include "index.h" // stores the web interface html/javascript page
#include "arduino_secrets.h" // stores the wifi credentials for the home network

// variables to see if use wifi network or setup as AP. 
// uncommnent to use existing WiFi network and enter proper credentials in the arduino_secrets.h file
// usefull when developing on home network, but should be commented out for production
//#define USE_NETWORK

String device_name = "SCKCommQTPY";

// Check if Bluetooth is available
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

// Check Serial Port Profile
#if !defined(CONFIG_BT_SPP_ENABLED)
#error Serial Port Profile for Bluetooth is not available or not enabled. It is only available for the ESP32 chip.
#endif

BluetoothSerial SerialBT;

// How many internal neopixels do we have? some boards have more than one!
#define NUMPIXELS 1
Adafruit_NeoPixel pixels(NUMPIXELS, PIN_NEOPIXEL, NEO_GRB + NEO_KHZ800);

// define the hardware serial port on RX/TX pins
HardwareSerial MySerial1(1);

TicSerial tic(MySerial1);

// WiFi credentials
#ifdef USE_NETWORK
  //WiFi credentials for home network
  const char* ssid     = SECRET_SSID;
  const char* password = SECRET_PASS;
#else
  // Wifi credentials for AP mode
  const char* ssid     = device_name.c_str();
  const char* password = "12345678";
#endif

WiFiServer server(80);

// variables for motor control
bool ticSCK = false;
bool ticInitiated = false;
bool mimSCK = false;
bool blink = true;
uint32_t microStep = 4;
uint32_t motorAcc = 500; // default 500 rpms/sec
uint32_t stepsPerRev = 96;
uint32_t maxRPM = 6000;
uint32_t currentRPM = 0;
uint32_t velocity = 0;
bool moveUp = true; // move clockwise
uint32_t clksMax = (uint32_t)(maxRPM * stepsPerRev * microStep / 60.0);
uint32_t clksPrev = 0; // the previous clock speed

// variables to store desired speed of SCK-300P
uint32_t desiredRPM = 0;

// variable to store string returned from the MySerial1 connection to the SCK-300P/SCK-300S
String sckResponse = "";

// variable to hold the latest sck command
String sckCommand = "";

// variable to keep track if sck300p motor is running already
bool motorRunning = false;

// variable to keep track if the motor is accelerating
bool motorAccelerating = false;

void setup() {
  Serial.begin(19200);
  MySerial1.begin(19200, SERIAL_8N1, RX, TX);

  SerialBT.begin(device_name);  
  //SerialBT.printf("The device with name \"%s\" is started.\nNow you can pair it with Bluetooth!\n", device_name.c_str());
  Serial.printf("The device with name \"%s\" is started.\nNow you can pair it with Bluetooth!\n", device_name.c_str());

  #if defined(NEOPIXEL_POWER)
    pinMode(NEOPIXEL_POWER, OUTPUT);
    digitalWrite(NEOPIXEL_POWER, HIGH);
  #endif

  pixels.begin(); // INITIALIZE NeoPixel strip object (REQUIRED)
  pixels.setBrightness(50);

  #ifdef USE_NETWORK
    // setup wifi connection
    Serial.print("Connecting to ");
    Serial.println(ssid);
    WiFi.begin(ssid, password);

    while (WiFi.status() != WL_CONNECTED) {
      delay(500);
      Serial.print(".");
    }

    Serial.println("\nWiFi connected.");
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());
  #else
    // print the AP network name (SSID);
    Serial.print("Creating access point named: ");
    Serial.print(ssid);
    Serial.print(" : ");
    Serial.println(password);
 
    // Create open network. Change this line if you want to create an WEP network:
    WiFi.mode(WIFI_AP);
    WiFi.softAP(ssid, password);

    Serial.print("AP IP address: ");
    Serial.println(WiFi.softAPIP());
  #endif

  // Start the server
  server.begin();
  Serial.println("Server started");
}

void loop() {
  if (SerialBT.available()) {      // If anything comes in Serial BT
    if(ticSCK) {
      String svalue = SerialBT.readStringUntil('\r\n');
      svalue.trim();

      if(svalue.length() > 2) {
        // Debug to USB Serial
        Serial.print("Command/Value: ");
        Serial.println(svalue);
        
        int ind1 = svalue.indexOf(',');
        String cmd = svalue.substring(0, ind1);
        String value = svalue.substring(ind1+1);
        
        runTicCommand(cmd, value);
      } else {
        // assume we just disconnecting
        if(svalue.equals("x")) {
          ticSCK = false;
          blink = true;
          SerialBT.println("TIC MODE DISCONNECT...");
        }
      }
    } else {
      char incomingByte = SerialBT.read();

      if(incomingByte == 'x') {
        setModeSCK300S();
        SerialBT.println("TIC SCK-300S MODE ...");
      } else if(incomingByte == 'y') {
        setModeSCK300P(false);
        SerialBT.println("MIM SCK-300[P] MODE ...");
      } else {
        MySerial1.write(incomingByte);   // read it and send it out Hardware Serial RX/TX pins
      }
    }
  }
  
  // handle the web interface
  WiFiClient client = server.available();
  if (client) {
    handleClient(client);
  }
  
  // static variable to hold the incoming data from the SCK-300P/SCK-300S
  static String dataString = "";

  // check if we have data from the SCK-300P/SCK-300S
  if (MySerial1.available()) {     // If anything comes in Serial RX/TX
    char incomingByte = MySerial1.read();
    Serial.write(incomingByte);   // send it out USB Serial
    SerialBT.write(incomingByte);   // send it out SerialBT

    // add to the data string until we get a \n then reset the string after storing it
    if(incomingByte != '\n') {
      dataString += incomingByte;
    } else {
      sckResponse = dataString;
      dataString = "";
      processSCKData();
    }
  }

  // blink the led fast if we not in SCK-300S TIC or SCK-300P mode
  if(blink) {
    pixels.fill(0x0000FF); // Blue
    pixels.show();
    
    delay(100); // wait for 100 milliseconds
    
    pixels.fill(0x000000);
    pixels.show();
    delay(100);
  }

  // this must be called at least every second
  if(ticSCK) {
    resetCommandTimeout();
  }
}

// function to process the incoming data from the SCK-300P/SCK-300S
void processSCKData() {
  //Serial.println("SCK Command: " + sckCommand);
  //Serial.print("SCK Data: " + sckResponse);
  
  // check if the data is comming from the SCK-300P
  if(sckResponse.indexOf(":") >= 0) {
    int idx1 = sckResponse.indexOf(",") + 1;
    int idx2 = sckResponse.indexOf(":");
    String value = sckResponse.substring(idx1, idx2);
    
    if(sckCommand.indexOf("RPM") >= 0) {
      if(motorRunning) {
        // set the current RPM value
        currentRPM = value.toInt();
      } else {
        // set the desired RPM value
        currentRPM = 0;
      }

    }

    // reset the sck command
    sckCommand = "";
  }
}

// set the mode to SCK-300S TIC
void setModeSCK300S() {
  ticSCK = true;
  mimSCK = false;
  blink = false;

  if(!ticInitiated) {
    initTIC();
    ticInitiated = true;
    Serial.println("TIC BOARD INITIALIZED ...");
  }

  // turn on the neo pixel to green and leave it on to indicate bluetooth connected
  pixels.fill(0x00FF00); // Green
  pixels.show();
  Serial.println("TIC SCK-300S MODE ...");  
}

// set the mode to SCK-300P MiM
void setModeSCK300P(bool initialize) {
  mimSCK = true;
  ticSCK = false;
  blink = false;
  motorAcc = 0; // set the acceleration to 0 for the SCK-300P since it is not supported natively
  
  if(initialize) {
    // initialize the MiM SCK-300P
    initMIM();
    Serial.println("MIM SCK-300[P] INITIALIZED ...");
  }
  
  // turn on the neo pixel to red and leave it on to indicate bluetooth connected
  pixels.fill(0xFF0000); // Red
  pixels.show();
  Serial.println("MIM SCK-300[P] MODE ...");  
}

// initialize the TIC board
void initTIC() {
  tic.setProduct(TicProduct::T249);
  tic.haltAndSetPosition(0);

  setMicroStep(microStep);
  setAcceleration(motorAcc);
}

// initialize the SCK-300P MiM board
void initMIM() {
  // send command to get the SC-300P board version
  String command = "SetStartPWM,0\r\n";
  MySerial1.print(command);
  delay(100);

  command = "SetSlope,960\r\n";
  MySerial1.print(command);
  delay(100);

  command = "SetIntercept,500\r\n";
  MySerial1.print(command);
  delay(100);
}

// run the specific tic command
void runTicCommand(String cmd, String value) {
  // debug to USB Serial
  Serial.print("Command: " + cmd);
  Serial.println(" Value: " + value);

  if(cmd.equals("GetVersion")) { // check board connection
    if(checkBoardConnected() == 0) {
      SerialBT.println("TIC_SCK v1.0.1");
    } else {
      SerialBT.println("ERROR, NO TIC Board Fond ...");
    }
  } else if(cmd.equals("SetMicro")) { // set the excitation
    microStep = value.toInt();
    setMicroStep(microStep);
  } else if(cmd.equals("SetSPR")) { // set the steps per revolution
    stepsPerRev = value.toInt();
  } else if(cmd.equals("SetMaxRPM")) { // set the max RPM
    maxRPM = value.toInt();
    clksMax = (uint32_t)(maxRPM * stepsPerRev * microStep / 60.0);
  } else if(cmd.equals("SetRPM")) { // set the speed in RPM
    int rpm = value.toInt();
    uint32_t clks = StepperRpmToClks(rpm);
    stepSetFreqRamp(clks);
  } else if(cmd.equals("GetRPM")) { // get the speed in RPM
    int round = value.toInt();
    int rpm = StepperClksToRpm(round);
    SerialBT.println(rpm);
  } else if(cmd.equals("SetACC")) { // set the acceleration in rpm/sec
    int rpm = value.toInt();
    setAcceleration(rpm);
  } else if(cmd.equals("SetDIR")) { // set the direction
    int dir = value.toInt();
    if(dir == 0) {
      moveUp = true; // clockwise
    } else {
      moveUp = false; // counter-clockwise
    }
  } else if(cmd.equals("STEPoff")) { // turn of the stepper motor
    stepOff(); 
  } else if(cmd.equals("STEPon")) { // turn on the stepper motor
    stepOn(); 
  }  
}

// set the max acceleration and deceleration in rpms/sec
int setAcceleration(uint32_t rpm) {
  uint32_t clks_per_second = StepperRpmToClks(rpm);
  
  // correct the acceleration?
  //clks_per_second = (uint32_t)clks_per_second*0.6;

  // in units of steps per second per 100 seconds
  uint32_t accel = clks_per_second * 100;
  tic.setMaxAccel(accel);
  tic.setMaxDecel(accel); // also set the deceleration to the same value
  return 0;
}

// set the excelleration
int setMicroStep(int mstep) {
  tic.setStepMode(getStepperExcitationValue(mstep));
  return 0;
}

// get the stepper excitation value
TicStepMode getStepperExcitationValue(int exc) {
  switch (exc) {
    case 1:
      return TicStepMode::Microstep1;
    case 2:
      return TicStepMode::Microstep2;
    case 4:
      return TicStepMode::Microstep4;
    case 8:
      return TicStepMode::Microstep8;
    case 16:
      return TicStepMode::Microstep16;
    default:
      return TicStepMode::Microstep1;
  }
}

// check the board connection
int checkBoardConnected() {
  uint8_t deviceNumber;
  tic.getSetting(7, 1, &deviceNumber);

  return tic.getLastError() == 0 ? 0 : -1;
}

// turn motor on
int stepOn() {
  tic.exitSafeStart();
  tic.energize();
  return 0;
}

// turn motor off
int stepOff() {
  tic.deenergize();
  tic.enterSafeStart();
  clksPrev = 0;
  return 0;
}

int stepSetFreq(uint32_t clks) {
  velocity = clks * 10000;

  if (abs(tic.getCurrentVelocity()) > 0) {
    // motor already moving, update target
    if (moveUp) {
      stepMoveUp();
    } else {
      stepMoveDown();
    }
  }

  return 0;
}

int stepMoveUp() {
  tic.setTargetVelocity(velocity);
  moveUp = true;
  return 0;
}

int stepMoveDown() {
  tic.setTargetVelocity(-velocity);
  moveUp = false;
  return 0;
}

int stepSetFreqRamp(uint32_t clks) {
  velocity = clks * 10000;

  // set max speed so accelleration can be calculated correctly
  if (clksPrev == 0) {
    // in units of steps per 10000 seconds
    tic.setMaxSpeed(clksMax * 10000);
  } 
    
  clksPrev = clks;

  if (moveUp) {
    stepMoveUp();
  } else {
    stepMoveDown();
  }

  return 1;
}

// convert RPM to CLKS for Stepper
long StepperRpmToClks(uint32_t rpm) {
  uint32_t clks = (uint32_t)(rpm * stepsPerRev * microStep) / 60.0;
  return clks; 
}

// convert clocks to RPM
uint32_t StepperClksToRpm(uint8_t round) {
  uint32_t clks = (uint32_t)(tic.getCurrentVelocity() / 10000);
  
  uint32_t calcRpm = (uint32_t)(clks * 60 / stepsPerRev / microStep);

  // round to nearest 10
  if (round) {
    calcRpm = ((calcRpm + 5) / 10) * 10;
  }
  
  // set the current RPM value
  currentRPM = calcRpm;
  return calcRpm;
}

// Sends a "Reset command timeout" command to the Tic.  We must
// call this at least once per second, or else a command timeout
// error will happen.  The Tic's default command timeout period
// is 1000 ms, but it can be changed or disabled in the Tic
// Control Center.
void resetCommandTimeout() {
  tic.resetCommandTimeout();
}

// Delays for the specified number of milliseconds while
// resetting the Tic's command timeout so that its movement does
// not get interrupted.
void delayWhileResettingCommandTimeout(uint32_t ms) {
  uint32_t start = millis();
  do
  {
    resetCommandTimeout();
  } while ((uint32_t)(millis() - start) <= ms);
}

// Accelerate the motor to a target speed using a given acceleration rate in rpm/s for SCK300P.
void accelerateToSpeed(int targetSpeed) {
  motorAccelerating = true;

  int currentSpeed = currentRPM;
  int cps = 4; // commands sent per second to SCK-300P
  int delayTime = round(1000/cps); // delay time in milliseconds for each rpm step change
  
  int step = round(motorAcc/cps);
  step = (targetSpeed > currentSpeed) ? step : -1*step;
  
  Serial.print("\nAccelerating to ");
  Serial.print(targetSpeed);
  Serial.print(" RPM @ ");
  Serial.print(motorAcc);
  Serial.println(" RPM/s\n");

  // get the start time in milliseconds
  unsigned long startTime = millis();

  while (currentSpeed != targetSpeed) {
    currentSpeed += step;

    // check if we have reached the target speed
    if (currentSpeed > targetSpeed) {
      currentSpeed = targetSpeed;
    } 
    
    // send command to the SCK-300P to set the speed in RPM
    sckCommand = "SetRPM," + String(currentSpeed) + "\r\n";
    MySerial1.print(sckCommand);

    // set the rpm value to the current speed
    currentRPM = currentSpeed;

    // print current speed every 100 rpms
    if (currentSpeed % 100 == 0) {
      Serial.print("Current Speed: ");
      Serial.println(currentSpeed);
    }

    delay(delayTime);
  }

  // get the end time in milliseconds
  unsigned long endTime = millis();
  
  Serial.print("\nTime to reach target speed: ");
  Serial.print(endTime - startTime);
  Serial.println(" ms\n");

  motorAccelerating = false;
}


// Function to handle client connection and process HTTP requests
void handleClient(WiFiClient &client) {
  // Wait until the client sends some data
  while (client.connected() && !client.available()) {
    delay(1);
  }

  // Read the first line of the request
  String request = client.readStringUntil('\r');
  client.flush();
  //Serial.println(request);

  // Handle the various endpoints based on the URL
  if (request.indexOf("GET / ") >= 0 || request.indexOf("GET /?") >= 0) {
    // Serve the main page
    sendHTML(client);
  } else if (request.indexOf("GET /setModel") >= 0) {
    String value = parseStringValue(request, "value=");
    Serial.print("SCK Model: ");
    Serial.println(value);

    if(value.equals("SCK-300P")) {
      setModeSCK300P(true);
    } else if(value.equals("SCK-300S")) {
      setModeSCK300S();
    }

    sendResponse(client, "OK");
  } else if (request.indexOf("GET /setSpeed") >= 0) {
    String value = parseStringValue(request, "value=");
    if(ticSCK) {
      runTicCommand("SetRPM", value);
    } else if(mimSCK) {
      desiredRPM = value.toInt();

      if(motorRunning) {
        // send command to set the speed in RPM
        sckCommand = "SetRPM," + value + "\r\n";
        MySerial1.print(sckCommand);
        delay(100);
      }
    }

    Serial.print("Set desired speed to ");
    Serial.println(value);
    sendResponse(client, "OK");
  } else if (request.indexOf("GET /setAcceleration") >= 0) {
    String value = parseStringValue(request, "value=");
    if(ticSCK) {
      runTicCommand("SetACC", value);
    } else if(mimSCK) {
      motorAcc = value.toInt();
    }
    
    Serial.print("Set acceleration to ");
    Serial.println(value);
    sendResponse(client, "OK");
  } else if (request.indexOf("GET /start") >= 0) {
    if(ticSCK) {
      stepOn();
    } else if(mimSCK) {
      motorRunning = true;

      if(motorAcc == 0) {
        // send to turn the motor on because the speed was already set
        sckCommand = "BLDCon\r\n";
        MySerial1.print(sckCommand);
        delay(100);

        // send command to set the speed in RPM
        sckCommand = "SetRPM," + String(desiredRPM) + "\r\n";
        MySerial1.print(sckCommand);
        delay(100);
      } else {
        // accelerate to the desired speed
        sckCommand = "BLDCon\r\n";
        MySerial1.print(sckCommand); 
        delay(100);
        accelerateToSpeed(desiredRPM);
      }
    }

    Serial.println("Motor started");
    sendResponse(client, "OK");
  } else if (request.indexOf("GET /stop") >= 0) {
    if(ticSCK) {
      stepOff();
    } else if(mimSCK) {
      // implement the stop for the SCK-300P
      sckCommand = "BLDCoff\r\n";
      MySerial1.print(sckCommand);
      delay(100);
    }

    // reset the current rpm value and set the motor running to false
    currentRPM = 0;
    motorRunning = false;

    Serial.println("Motor stopped");
    sendResponse(client, "OK");
  } else if (request.indexOf("GET /getSpeed") >= 0) {
    if(ticSCK) {
      runTicCommand("GetRPM", "0");
    } else if(mimSCK) {
      if(!motorAccelerating) {
        sckCommand = "GetRPM\r\n";
        MySerial1.print(sckCommand);
      }
    }

    sendResponse(client, String(currentRPM));
  } else {
    // If unknown, serve the main page
    sendHTML(client);
  }

  delay(1);
  client.stop();
}

// Utility function to parse integer value from the request URL
int parseValue(String req, String key) {
  int index = req.indexOf(key);
  if (index != -1) {
    String sub = req.substring(index + key.length());
    int end = sub.indexOf(' ');
    if (end == -1) end = sub.length();
    return sub.substring(0, end).toInt();
  }
  return 0;
}

// Utility function to parse string value from the request URL
String parseStringValue(String req, String key) {
  int index = req.indexOf(key);
  if (index != -1) {
    String sub = req.substring(index + key.length());
    int end = sub.indexOf(' ');
    if (end == -1) end = sub.length();
    return sub.substring(0, end);
  }
  return "";
}

// Send the HTML page to the client
void sendHTML(WiFiClient &client) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: text/html");
  client.println();
  client.println(index_html);
  client.println();
}

// Send a plain text response
void sendResponse(WiFiClient &client, String response) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: text/plain");
  client.println("Access-Control-Allow-Origin: *");
  client.println();
  client.println(response);
}
