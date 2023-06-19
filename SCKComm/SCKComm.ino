/**
 * Sketch to echo usb to serial 1 and usb to connect to SCK-300 and TIC based SCK-300S
 * based boards
 */
#include <Tic.h>

TicSerial tic(Serial1);

bool ticSCK = false;
bool mimSCK = false;
bool blink = true;
uint32_t microStep = 4;
uint32_t motorAcc = 800; // 800 rpms per sec
uint32_t stepsPerRev = 96;
uint32_t maxRPM = 6000;
uint32_t velocity = 0;
bool moveUp = true; // move clockwise
uint32_t clksMax = (uint32_t)(maxRPM * stepsPerRev * microStep / 60.0);
uint32_t clksPrev = 0; // the previous clock speed

void setup() {
  Serial.begin(19200);
  Serial1.begin(19200);  

  pinMode(LED_BUILTIN, OUTPUT);
}

void loop() {
  if (Serial.available()) {      // If anything comes in Serial (USB),
    if(ticSCK) {
      String svalue = Serial.readStringUntil('\r\n');
      svalue.trim();

      if(svalue.length() > 2) {
        //Serial.print("Command/Value: ");
        //Serial.println(svalue);
        
        int ind1 = svalue.indexOf(',');
        String cmd = svalue.substring(0, ind1);
        String value = svalue.substring(ind1+1);
        
        runCommand(cmd, value);
      } else {
        // assume we just disconnecting
        if(svalue.equals("x")) {
          ticSCK = false;
          blink = true;
          Serial.println("TIC MODE DISCONNECT...");
        }
      }
    } else {
      char incomingByte = Serial.read();
      if(incomingByte == 'x') {
        ticSCK = true;
        blink = false;
        initTIC();
        digitalWrite(LED_BUILTIN, HIGH); // turn on led and leave it on
        Serial.println("TIC SCK-300S MODE ...");
      } else if(incomingByte == 'y') {
        if(!mimSCK) {
          blink = false;
          digitalWrite(LED_BUILTIN, HIGH); // turn on led and leave it on
          Serial.println("MIM SCK-300[P] MODE ...");
          mimSCK = true;
        } else {
          blink = true;
          mimSCK = false;
        }
      } else {
        Serial1.write(incomingByte);   // read it and send it out Serial1 (pins 0 & 1)
      }
    }
  }

  if (Serial1.available()) {     // If anything comes in Serial1 (pins 0 & 1)
    char incomingByte = Serial1.read();
    Serial.write(incomingByte);   // read it and send it out Serial (USB)
  }

  // blink the led fast if we not in SCK-300S TIC mode
  if(blink) {
    digitalWrite(LED_BUILTIN, HIGH);   // turn the LED on (HIGH is the voltage level)
    delay(100);                       // wait for a second
    digitalWrite(LED_BUILTIN, LOW);    // turn the LED off by making the voltage LOW  
    delay(100);
  }

  // this must be called at least every second
  if(ticSCK) {
    resetCommandTimeout();
  }
}

void initTIC() {
  tic.setProduct(TicProduct::T249);
  tic.haltAndSetPosition(0);

  setMicroStep(microStep);
  setAcceleration(motorAcc);
}

// run the specific commands
void runCommand(String cmd, String value) {
  //Serial.print("Command: " + cmd);
  //Serial.println(" Value: " + value);

  if(cmd.equals("GetVersion")) { // check board connection
    if(checkBoardConnected() == 0) {
      Serial.println("TIC_SCK v1.0.1");
    } else {
      Serial.println("ERROR, NO TIC Board Fond ...");
    }
  } else if(cmd.equals("SetMicro")) { // set the excitation
    microStep = value.toInt();
    setMicroStep(microStep);
  } else if(cmd.equals("SetSPR")) { // set the steps per revolution
    stepsPerRev = value.toInt();
  } else if(cmd.equals("SetMaxRPM")) { // set the steps per revolution
    maxRPM = value.toInt();
    clksMax = (uint32_t)(maxRPM * stepsPerRev * microStep / 60.0);
  } else if(cmd.equals("SetRPM")) { // set the steps per revolution
    int rpm = value.toInt();
    uint32_t clks = StepperRpmToClks(rpm);
    stepSetFreqRamp(clks);
  } else if(cmd.equals("GetRPM")) { // set the steps per revolution
    int round = value.toInt();
    int rpm = StepperClksToRpm(round);
    Serial.println(rpm);
  } else if(cmd.equals("SetACC")) { // set the steps per revolution
    int rpm = value.toInt();
    setAcceleration(rpm);
  } else if(cmd.equals("SetDIR")) { // set the direction
    int dir = value.toInt();
    if(dir == 0) {
      moveUp = true; // clockwise
    } else {
      moveUp = false; // counter-clockwise
    }
  } else if(cmd.equals("STEPoff")) { // set the steps per revolution
    stepOff(); 
  } else if(cmd.equals("STEPon")) { // set the steps per revolution
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
