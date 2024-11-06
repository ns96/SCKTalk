import serial
import time

class MiMTalkLight:
    def __init__(self, test_mode=False):
        self.test_mode = test_mode
        self.serial = None
        self.RESPONSE_DELAY_MS = 0.2  # The response delay in seconds (200 ms)
        self.run_ramp = False

    def set_test_mode(self, test):
        self.test_mode = test

    def connect(self, port_name, baudrate=19200):
        if self.test_mode:
            return
        
        self.serial = serial.Serial(port_name, baudrate, timeout=1)
        time.sleep(2)  # Set to TIC Mode
        self.send_command("y")

    def send_command(self, command, wait_for_response=True):
        if self.test_mode:
            return "OK"
        
        try:
            command += "\r\n"
            self.serial.write(command.encode())
            if wait_for_response:
                return self.read_response()
            return ""
        except Exception as e:
            print("COMM/IO Error. Ignore ...")
            return None

    def read_response(self):
        try:
            time.sleep(self.RESPONSE_DELAY_MS)
            if self.test_mode:
                return "TESTMODE,0:TT"

            response = self.serial.read(128).decode().strip()
            return response
        except Exception as e:
            print(f"Error: {e}")
            return "ERROR"

    def get_version(self):
        return self.send_command("GetVersion")

    def kick_start(self, kickstart):
        try:
            self.send_command(f"SetPWM,{kickstart}", wait_for_response=False)
            time.sleep(0.05)
            print("Kick Started ...\n\n")
        except Exception as e:
            print(f"Error: {e}")

    def set_rpm(self, desired_rpm):
        self.send_command(f"SetRPM,{desired_rpm}")

    def ramp_to_rpm(self, desired_rpm):
        if desired_rpm <= 500:
            self.send_command(f"SetRPM,{desired_rpm}")
            print(f"Setting Desired RPM Directly: {desired_rpm}")
        else:
            step = 300
            for i in range(500, desired_rpm + step, step):
                speed = min(i, desired_rpm)
                print(f"Setting Speed {speed} index: {i}")
                self.send_command(f"SetRPM,{speed}")

    def ramp_to_rpm_with_accel(self, desired_rpm, acceleration, current_rpm):
        try:
            self.run_ramp = True
            rpm_diff = abs(desired_rpm - current_rpm)
            time_to_desired_rpm = (rpm_diff / acceleration) * 1000
            print(f"\nTime to Desired RPM (ms): {int(time_to_desired_rpm)}")

            time_total = 0
            cps = 4  # Commands per second
            delay_ms = max(1000 // cps - int(self.RESPONSE_DELAY_MS * 1000), 0)

            step = int(acceleration / cps)
            start_rpm = max(step, current_rpm) if current_rpm == 0 else current_rpm

            for i in range(start_rpm, desired_rpm + step, step):
                if not self.run_ramp:
                    break
                speed = min(i, desired_rpm)
                self.send_command(f"SetRPM,{speed}")
                time.sleep(delay_ms / 1000)
                time_total += delay_ms + int(self.RESPONSE_DELAY_MS * 1000)

            print(f"Time Actually Taken (ms): {int(time_total)}\n")
            return int(time_total) // 1000
        except Exception as e:
            print(f"Error: {e}")
            return 0

    def stop_ramp(self):
        self.run_ramp = False

    def get_rpm(self, round_to=0):
        if self.test_mode:
            return -1
        response = self.send_command("GetRPM")
        rpm = int(self.get_response_value(response))
        if round_to > 0:
            rpm = int(round(rpm / round_to) * round_to)
        return rpm

    def motor_on(self):
        self.send_command("BLDCon")

    def motor_off(self):
        self.send_command("BLDCoff")

    def print(self, string):
        print(string)

    def get_response_value(self, response):
        idx1 = response.find(",") + 1
        idx2 = response.find(":")
        return response[idx1:idx2]

    def close(self):
        if self.test_mode:
            return
        try:
            self.send_command("y")
            time.sleep(0.5)
        except Exception as e:
            print(f"Error: {e}")
        finally:
            if self.serial:
                self.serial.close()

    def set_motor_parameters(self):
        print("Setting Motor Parameters")
        print(self.send_command("SetStartPWM,0"))
        print(self.send_command("SetSlope,930"))
        print(self.send_command("SetIntercept,350"))

    def set_motor_parameters_custom(self, start_pwm, slope, intercept):
        response = f"{self.send_command(f'SetStartPWM,{start_pwm}')} / "
        response += f"{self.send_command(f'SetSlope,{slope}')} / "
        response += f"{self.send_command(f'SetIntercept,{intercept}')}"
        return response

    def is_connected(self):
        return self.serial and self.serial.is_open

if __name__ == "__main__":
    mim_talk = MiMTalkLight()
    mim_talk.connect("COM3")
    response = mim_talk.get_version()
    print(f"Connection response: {response}")

    if "MIM" in response:
        print("Connected to MIM\n")
        mim_talk.set_motor_parameters()
        mim_talk.motor_on()

        for i in range(0, 1001, 50):
            if i == 0:
                print("Step\tRPM")
                mim_talk.send_command("SetRPM,1200")
            time.sleep(2)
            rpm = mim_talk.send_command("GetRPM")
            rpm = mim_talk.get_response_value(rpm)
            print(f"{i}\t{rpm}")

        mim_talk.send_command("BLDCoff")

    mim_talk.close()