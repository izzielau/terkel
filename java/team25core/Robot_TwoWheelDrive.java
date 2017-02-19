package team25core;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.Range;
import com.qualcomm.robotcore.util.RobotLog;

/**
 * This is NOT an opmode.
 *
 * This class defines all the specific hardware for a two wheel bot.
 *
 * This hardware class assumes the following device names have been configured on the robot:
 * Note:  All names are lower case and some have single spaces between words.
 *
 * Motor channel:  Left  drive motor:        "left drive"
 * Motor channel:  Right drive motor:        "right drive"
 * Motor channel:  Rear  drive motor:        "back drive"
 *
 * These motors correspond to three drive locations spaced 120 degrees around a circular robot.
 * Each motor is attached to an omni-wheel. Two wheels are in front, and one is at the rear of the robot.
 *
 * Robot motion is defined in three different axis motions:
 * - Axial    Forward/Backwards      +ve = Forward
 * - Lateral  Side to Side strafing  +ve = Right
 * - Yaw      Rotating               +ve = CCW
 */


public class Robot_TwoWheelDrive implements Robot_Drivetrain
{
    // Private Members
    private Robot myOpMode;

    private DcMotor  leftDrive      = null;
    private DcMotor  rightDrive     = null;

    private double  driveAxial      = 0 ;   // Positive is forward
    private double  driveLateral    = 0 ;   // Positive is right
    private double  driveYaw        = 0 ;   // Positive is CCW

    private final static double PIVOT_MULTIPLIER = 1.5;

    /* Constructor */
    public Robot_TwoWheelDrive(DcMotor right, DcMotor left) {
        // Define and Initialize Motors
        rightDrive       = right;
        leftDrive        = left;
    }


    /* Initialize standard Hardware interfaces */
    public void initDrive(Robot opMode) {

        // Save reference to Hardware map
        myOpMode = opMode;

        leftDrive.setDirection(DcMotor.Direction.FORWARD);
        rightDrive.setDirection(DcMotor.Direction.REVERSE);

        //use RUN_USING_ENCODERS because encoders are installed.
        setMode(DcMotor.RunMode.RUN_USING_ENCODER);

        // Stop all robot motion by setting each axis value to zero
        moveRobot(0,0,0) ;
    }

    public void manualDrive()  {
        // In this mode the Left stick moves the robot fwd & back, and Right & Left.
        // The Right stick rotates CCW and CW.

        //  (note: The joystick goes negative when pushed forwards, so negate it)
        setAxial(-myOpMode.gamepad1.left_stick_y);
        setLateral(myOpMode.gamepad1.left_stick_x);
        setYaw(-myOpMode.gamepad1.right_stick_x);
    }


    /***
     * void moveRobot(double axial, double lateral, double yaw)
     * Set speed levels to motors based on axes requests
     * @param axial     Speed in Fwd Direction
     * @param lateral   Speed in lateral direction (+ve to right)
     * @param yaw       Speed of Yaw rotation.  (+ve is CCW)
     */
    public void moveRobot(double axial, double lateral, double yaw) {
        setAxial(axial);
        setLateral(lateral);
        setYaw(yaw);
        moveRobot();
    }

    /***
     * void moveRobot()
     * This method will calculate the motor speeds required to move the robot according to the
     * speeds that are stored in the three Axis variables: driveAxial, driveLateral, driveYaw.
     * This code is setup for a three wheeled OMNI-drive but it could be modified for any sort of omni drive.
     *
     * The code assumes the following conventions.
     * 1) Positive speed on the Axial axis means move FORWARD.
     * 2) Positive speed on the Lateral axis means move RIGHT.
     * 3) Positive speed on the Yaw axis means rotate COUNTER CLOCKWISE.
     *
     * This convention should NOT be changed.  Any new drive system should be configured to react accordingly.
     */
    public void moveRobot() {
        // calculate required motor speeds to achieve axis motions
        double left;
        double right;

        if (driveLateral < 0) {
            left = -driveYaw + driveAxial;
            right = driveYaw + driveAxial + Math.abs(driveLateral);
        } else {
            left = -driveYaw + driveAxial + driveLateral;
            right = driveYaw + driveAxial;
        }
        // normalize all motor speeds so no values exceeds 100%.
        double max = Math.max(Math.abs(left), Math.abs(right));
        if (max > 1.0) {
            right /= max;
            left /= max;
        }

        // Set drive motor power levels.
        leftDrive.setPower(left);
        rightDrive.setPower(right);

        // Display Telemetry
        RobotLog.i("Axes   A[%+5.2f], L[%+5.2f], Y[%+5.2f]", driveAxial, driveLateral, driveYaw);
        RobotLog.i("Wheels L[%+5.2f], R[%+5.2f]", left, right);
    }


    public void setAxial(double axial)      {driveAxial = Range.clip(axial, -1, 1);}
    public void setLateral(double lateral)  {driveLateral = Range.clip(lateral, -1, 1); }
    public void setYaw(double yaw)          {driveYaw = Range.clip(yaw, -1, 1); }

    public void rotateRobot(double speed) {

        if (speed < 0) {
            leftDrive.setPower((1 / PIVOT_MULTIPLIER) * speed);
            rightDrive.setPower(-speed);
        } else {
            leftDrive.setPower(speed);
            rightDrive.setPower((1 / PIVOT_MULTIPLIER) * -speed);
        }
    }

    /***
     * void setMode(DcMotor.RunMode mode ) Set all drive motors to same mode.
     * @param mode    Desired Motor mode.
     */
    public void setMode(DcMotor.RunMode mode ) {
        leftDrive.setMode(mode);
        rightDrive.setMode(mode);
    }
}

