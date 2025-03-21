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
      <input type="radio" name="model" value="SCK-300S" checked onclick="setModel('SCK-300S')">
      SCK-300S
    </label>
    
    <br><hr><br>

    <label>Desired Speed (RPM):</label>
    <input type="text" id="speedInput" value="0">
    <button onclick="setSpeed()">Set Speed</button>

    <label>Acceleration (RPM/s):</label>
    <input type="text" id="accelInput" value="0">
    <button onclick="setAcceleration()">Set Acceleration</button>

    <button class="adjust" onclick="changeSpeed(100)">Increase Speed</button>
    <button class="adjust" onclick="changeSpeed(-100)">Decrease Speed</button>
    
    <button class="start" onclick="startMotor()">Start Motor</button>
    <button class="stop" onclick="stopMotor()">Stop Motor</button>

    <h3>Current Speed:</h3>
    <div id="currentSpeed" class="blue-label">0 RPM</div>
  </div>

  <script>
    function setModel(model) {
      fetch("/setModel?value=" + model);
    }

    function setSpeed(){
      var speed = document.getElementById("speedInput").value;
      fetch("/setSpeed?value=" + speed);
    }

    function setAcceleration(){
      var accel = document.getElementById("accelInput").value;
      fetch("/setAcceleration?value=" + accel);
    }

    function changeSpeed(delta){
      var speedField = document.getElementById("speedInput");
      var currentVal = parseInt(speedField.value) || 0;
      var newVal = currentVal + delta;

      // make sure the speed is within the valid range
      if (newVal < 0) newVal = 0;
      if (newVal > 9000) newVal = 9000;

      speedField.value = newVal;

      fetch("/setSpeed?value=" + newVal);
    }

    function startMotor(){
      fetch("/start");
    }

    function stopMotor(){
      fetch("/stop");
    }

    function getCurrentSpeed(){
      fetch("/getSpeed")
      .then(response => response.text())
      .then(data => {
        document.getElementById("currentSpeed").innerText = data + " RPM";
      });
    }

    // set the default model to SCK-300S
    setModel("SCK-300S");

    // Update the current speed every second
    setInterval(getCurrentSpeed, 1000);
  </script>
</body>
</html>
)rawliteral";

#endif