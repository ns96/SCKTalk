#ifndef INDEX_H
#define INDEX_H

// The HTML page is stored in program memory
const char index_html[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Motor Control</title>
  <style>
    body {
      font-family: Arial, sans-serif;
      margin: 20px;
      background-color: #f2f2f2;
    }
    .container {
      max-width: 400px;
      margin: auto;
      background-color: #fff;
      padding: 20px;
      box-shadow: 0 0 10px rgba(0,0,0,0.1);
    }
    input[type="text"] {
      width: calc(100% - 22px);
      padding: 10px;
      margin: 5px 0;
      font-size: 16px;
    }
    button {
      width: 100%;
      padding: 10px;
      margin: 5px 0;
      font-size: 16px;
      border: none;
      border-radius: 4px;
    }
    button.start {
      background-color: #4CAF50;
      color: white;
    }
    button.stop {
      background-color: #f44336;
      color: white;
    }
    button.adjust {
      background-color: #008CBA;
      color: white;
    }
    .blue-label {
      background-color: #2196F3;
      color: white;
      padding: 10px;
      text-align: center;
      font-size: 20px;
      margin-top: 10px;
      border-radius: 4px;
    }
  </style>
</head>
<body>
  <div class="container">
    <h2>Motor Control</h2>

    <!-- Add radio buttons for SCK-300 and SCK-300S -->
    <label>Select Model: </label>
    <label>
      <input type="radio" name="model" value="SCK-300P" onclick="setModel('SCK-300P')">
      SCK-300P
    </label>
    <label>
      <input type="radio" name="model" value="SCK-300S" onclick="setModel('SCK-300S')">
      SCK-300S
    </label>
    
    <br><hr><br>

    <label>Desired Speed (RPM):</label>
    <input type="text" id="speedInput" value="3000">
    <button onclick="setSpeed()">Set Speed</button>

    <label>Acceleration (RPM/s):</label>
    <input type="text" id="accelInput" value="500">
    <button onclick="setAcceleration()">Set Acceleration</button>

    <button class="adjust" onclick="changeSpeed(100)">Increase Speed</button>
    <button class="adjust" onclick="changeSpeed(-100)">Decrease Speed</button>
    
    <button class="start" onclick="startMotor()">Start Motor</button>
    <button class="stop" onclick="stopMotor()">Stop Motor</button>

    <h3>Current Speed:</h3>
    <div id="currentSpeed" class="blue-label">0 RPM</div>

    <h3>Time Running:</h3>
    <div id="timeRunning" class="blue-label">0 seconds</div>
  </div>

  <script>
    // define the current model
    var currentModel = "SCK-300S";
    var accelerating = false;

    // Define the valid speed ranges for each model
    var minSpeedSCK300P = 200;
    var maxSpeedSCK300P = 9000;
    var minSpeedSCK300S = 10;
    var maxSpeedSCK300S = 5000;

    // define variables to store interval ids
    var speedInterval;
    var timerInterval;

    // function to set the current model

    function setModel(model) {
      currentModel = model;
      fetch("/setModel?value=" + model);
    }

    function setSpeed(){
      var speed = document.getElementById("speedInput").value;
      let validSpeed = isValidSpeed(speed);
      document.getElementById("speedInput").value = validSpeed;
      
      fetch("/setSpeed?value=" + validSpeed);
    }

    // function to check that speed is valid for the current model and return a valid speed
    function isValidSpeed(speed) {
      speed = parseInt(speed);
      if (isNaN(speed)) return 0;
    
      if (currentModel === "SCK-300P") {
        if (speed < minSpeedSCK300P) return minSpeedSCK300P;
        if (speed > maxSpeedSCK300P) return maxSpeedSCK300P;
      } else if (currentModel === "SCK-300S") {
        if (speed < minSpeedSCK300S) return minSpeedSCK300S;
        if (speed > maxSpeedSCK300S) return maxSpeedSCK300S;
      } else {
        if (speed < 0) return 0;
        if (speed > 9000) return 9000;
      }

      return speed;
    }

    function setAcceleration(){
      var accel = document.getElementById("accelInput").value;
      
      // check if the acceleration is a valid number
      accel = parseInt(accel);
      
      if (isNaN(accel)) {
        document.getElementById("accelInput").value = 0;
        acclelerating = false;
      } else {
        fetch("/setAcceleration?value=" + accel);
        accelerating = true;
        document.getElementById("currentSpeed").innerText = " ... ";
      }      
    }

    function changeSpeed(delta){
      var speedField = document.getElementById("speedInput");
      var currentVal = parseInt(speedField.value) || 0;
      var newVal = currentVal + delta;

      // make sure the speed is within the valid range based on the current model
      let validSpeed = isValidSpeed(newVal);
      speedField.value = validSpeed; 

      fetch("/setSpeed?value=" + validSpeed);
    }

    function startMotor(){
      fetch("/start");

      // start interval to get the current speed every second
      speedInterval = setInterval(() => {
        getCurrentSpeed();
      }, 1000);

      // add a timer to update the time running every second
      var timeRunning = 0;
      timerInterval = setInterval(() => {
        timeRunning++;
        document.getElementById("timeRunning").innerText = timeRunning + " Seconds";
      }, 1000);
    }

    function stopMotor(){
      fetch("/stop");

      // stop the timer and reset the time running
      clearInterval(speedInterval);
      clearInterval(timerInterval);

      document.getElementById("currentSpeed").innerText = "0 RPM";
      document.getElementById("timeRunning").innerText = "0 Seconds";
    }

    function getCurrentSpeed(){
      fetch("/getSpeed")
      .then(response => response.text())
      .then(data => {
        // trim data to remove any new line characters
        data = data.trim();
        document.getElementById("currentSpeed").innerText = data + " RPM";
      });
    }

    // set the default model to SCK-300S
    //setModel("SCK-300S");
  </script>
</body>
</html>
)rawliteral";

#endif