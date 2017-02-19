package team25core;

/*
 * FTC Team 25: cmacfarl, January 12, 2017
 */

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.matrices.OpenGLMatrix;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackables;

public class NavigateToTargetTask extends RobotTask {

    enum TargetState {
        WAITING,
        FIND_TARGET,
        LOST_TARGET,
        INITIAL_APPROACH,
        FINAL_APPROACH,
        AT_TARGET,
        ALIGNED,
    };

    public enum FindMethod {
        APPROACH_STRAIGHT,
        ROTATE_RIGHT,
        ROTATE_LEFT,
    };

    public enum Targets {
        BLUE_NEAR(0),
        RED_FAR(1),
        BLUE_FAR(2),
        RED_NEAR(3);

        public int targetId;
        Targets(int id) {
            this.targetId = id;
        }
    }

    public enum EventKind {
        FOUND_TARGET,
        TIMEOUT,
    }

    public class NavigateToTargetEvent extends RobotEvent {

        public EventKind kind;

        public NavigateToTargetEvent(RobotTask task, EventKind k)
        {
            super(task);
            kind = k;
        }

        @Override
        public String toString()
        {
            return (super.toString() + "NavigateToTarget Event " + kind);
        }
    }

    protected RobotNavigation nav;
    protected Drivetrain drivetrain;
    protected ElapsedTime timer;
    protected int timeout;
    protected Gamepad gamepad;
    protected boolean pressed;
    protected Targets target;
    protected TargetState state;
    protected FindMethod findMethod;

    protected double relativeBearing;
    protected double robotBearing;
    protected double linearDistanceFromTarget;
    protected double strafeDistanceFromTarget;

    protected float mmPerInch        = 25.4f;
    protected float mmBotWidth       = 18 * mmPerInch;            // ... or whatever is right for your robot
    protected float mmFTCFieldWidth  = (12*12 - 2) * mmPerInch;   // the FTC field is ~11'10" center-to-center of the glass panels

    protected final static String ROBOT_TAG = "Nav: ";

    public NavigateToTargetTask(Robot robot, Drivetrain drivetrain, Targets target, int timeout, Gamepad gamepad)
    {
        super(robot);

        this.timeout = timeout;
        this.gamepad = gamepad;
        this.drivetrain = drivetrain;
        this.target = target;
        this.findMethod = FindMethod.APPROACH_STRAIGHT;

        robotBearing = 0;
        linearDistanceFromTarget = 0;
        strafeDistanceFromTarget = 0;
    }

    public void init(VuforiaTrackables targets, VuforiaLocalizer.Parameters parameters, OpenGLMatrix phoneLocationOnRobot)
    {
        nav = new RobotNavigation(this.robot, drivetrain);
        nav.initVuforia(targets, parameters, phoneLocationOnRobot);
        nav.activateTracking();
        nav.targetsAreVisible();
        nav.addNavTelemetry();

        // Uncomment this to tune the gain parameters if the defaults are unacceptable.
        // nav.setGainParams(0.0, 0.0, 0.0);

        setState(TargetState.WAITING);
    }

    protected void setState(TargetState state)
    {
        this.state = state;
        RobotLog.i(ROBOT_TAG + "Entering state: " + state.toString());
    }

    @Override
    public void start()
    {
        // Noop
    }

    @Override
    public void stop()
    {
        robot.removeTask(this);
        drivetrain.stop();
    }

    /**
     * Must call when you want the robot to actively start moving to the target.
     * Prior to this, the robot is just sending telemetry back to the driver station.
     * This allows drivers to know if the robot can see a target during the init phase.
     */
    public void findTarget()
    {
        timer = new ElapsedTime(ElapsedTime.Resolution.SECONDS);
        setState(TargetState.FIND_TARGET);
    }

    public void setFindMethod(FindMethod findMethod)
    {
        this.findMethod = findMethod;
    }

    protected void doFindMethod()
    {
        switch (findMethod) {
        case APPROACH_STRAIGHT:
            drivetrain.straight(0.3);
            break;
        case ROTATE_RIGHT:
            drivetrain.turnRight(0.06);
            break;
        case ROTATE_LEFT:
            drivetrain.turnLeft(0.06);
            break;
        }
    }

    @Override
    public boolean timeslice()
    {
        boolean visible;

        visible = nav.targetIsVisible(target.targetId);

        if (gamepad.a) {
            /*
             * Pause.
             */
            drivetrain.stop();
            nav.targetsAreVisible();
            nav.addNavTelemetry();
            return false;
        }

        if ((timer != null) && (timer.time() > timeout)) {
            robot.queueEvent(new NavigateToTargetEvent(this, EventKind.TIMEOUT));
            return true;
        }

        if (visible) {
            /*
             * Cache the last known robot position.
             */
            relativeBearing = nav.getRelativeBearing();
            robotBearing = nav.getRobotBearing();
            linearDistanceFromTarget = nav.getDistance();
            strafeDistanceFromTarget = nav.getStrafe();
        }

        switch (state) {
        case WAITING:
            if (visible) {
                drivetrain.stop();
                nav.addNavTelemetry();
            } else {
                robot.telemetry.addLine("Can't see the target");
            }
            break;
        case FIND_TARGET:
            if (visible) {
                drivetrain.stop();
                nav.addNavTelemetry();
                setState(TargetState.INITIAL_APPROACH);
            } else {
                doFindMethod();
            }
            break;
        case LOST_TARGET:
            if (visible) {
                setState(TargetState.INITIAL_APPROACH);
            } else if (relativeBearing > 0){
                drivetrain.turnRight(0.06);
            } else {
                drivetrain.turnLeft(0.06);
            }
            break;
        case INITIAL_APPROACH:
            if (!visible) {
                state = TargetState.LOST_TARGET;
            } else if (nav.cruiseControl(200)) {
                setState(TargetState.FINAL_APPROACH);
            }
            break;
        case FINAL_APPROACH:
            if (visible) {
                if (nav.cruiseControl(200)) {
                    setState(TargetState.AT_TARGET);
                }
            } else {
                RobotLog.i("Lost target %f", nav.getRelativeBearing());
                setState(TargetState.LOST_TARGET);
            }
            break;
        case AT_TARGET:
            if ((robotBearing < 1.5) && (robotBearing > -1.5)) {
                setState(TargetState.ALIGNED);
            } else if (nav.getRobotBearing() > 0) {
                drivetrain.turnLeft(0.10);
            } else {
                drivetrain.turnRight(0.10);
            }
            break;
        case ALIGNED:
            nav.addNavTelemetry();
            drivetrain.stop();
            break;
        }
        return false;
    }
}
