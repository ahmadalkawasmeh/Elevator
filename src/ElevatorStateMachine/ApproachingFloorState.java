package ElevatorStateMachine;

/**
 * ApproachingFloorState is a concrete state of the elevator state machine.
 * At this state the elevator is about to approach the next floor while moving up or down.
 *
 * @author  Mahtab Ameli
 * @version Iteration 2
 */
public class ApproachingFloorState extends ElevatorState {

    /**
     * Constructor for the class.
     * @param elevator
     */
    public ApproachingFloorState(ElevatorStateMachine elevator) {
        context = elevator;
        context.setDoorOpen(false);
        context.setCartStationary(true);
    }

    @Override
    /**
     * 1 of 2 valid transitions from this state: (ApproachingFloor -> ApproachingFloor)
     *
     */
    public void handleApproachingFloor() throws InterruptedException {

            System.out.println("***** From " + context.getState() + "State.handleApproachingFloor() *****");
            System.out.println("... Approaching next floor ... ");
            System.out.println("Floor #" + context.getArrivalSignal());
            context.setState(new ApproachingFloorState(context));

            System.out.println("Transitioned back to:\t" + context.getState());
            System.out.println("**************************************************************");

            if ((!context.getStopSignal())) {
                if ((context.getArrivalSignal() < 5) &&
                        (context.getMovingDirection().equals(ElevatorStateMachine.Direction.UP))) {
                    context.incrementFloor();
                }
                else if ( (context.getArrivalSignal() > 1) &&
                        (context.getMovingDirection().equals(ElevatorStateMachine.Direction.DOWN))) {
                    context.decrementFloor();
                }
            }
    }

    @Override
    /**
     * 2 of 2 valid transitions from this state: (ApproachingFloor -> Stopped)
     */
    public void handleStopping() {
        if (context.getStopSignal()) {
            System.out.println("***** From " + context.getState() + "State.handleStopping() *****");
            System.out.println("... Elevator stopping ...");
            context.setState(new StoppedState(context));
            System.out.println("Transitioned to:\t" + context.getState());
            System.out.println("**************************************************************");
        }
        context.setStopSignal(false);
    }

    public String toString() {
        return "ApproachingFloor";
    }
}
