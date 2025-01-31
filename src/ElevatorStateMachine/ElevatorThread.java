package ElevatorStateMachine;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;


/**
 * ElevatorThread implements the elevator state machine. The states are IDLE, STOPPED, MOVING_UP, MOVING_DOWN.
 *
 * @author  Mahtab Ameli
 * @version Iteration 4
 */
public class ElevatorThread implements Runnable {
    public enum ElevatorState {
        IDLE,
        STOPPED,
        MOVING_UP,
        MOVING_DOWN,
    }

    private int elevatorNumber;     // Elevator identifier number
    private ElevatorState state;    // Elevator's current state
    private boolean doorOpen;       // true if door is open, false if closed
    private int currentFloor;       // Elevator's current floor as signalled by the arrival sensor
    private List<Integer> floors;   // list of 5 floors that have access to the elevator
    private boolean stopSignal;     // signal set to true when scheduler makes a command to stop at approaching floor
    private int arrivalSignal;      // integer indicates the floor number being approached by the elevator
    private int destination = 0;    // destination floor requested by the scheduler
    DatagramPacket sendPacket, receivePacket; // Datagram Packets for UDP communication with the scheduler thread
    DatagramSocket sendReceiveSocket;         // Datagram socket for sending and receiving UDP communication to/from the scheduler thread
    private boolean timerInterrupted = false;



    /**
     * Constructor for the class.
     */
    public ElevatorThread(int elevatorNumber) {
        this.elevatorNumber = elevatorNumber;
        this.doorOpen = true;   // the elevator door is initially open
        this.currentFloor = 1;  // elevator starts at floor #1
        this.arrivalSignal = 1;
        this.floors = new ArrayList<Integer>();
        this.stopSignal = false;
        this.state = ElevatorState.IDLE;
        this.populateFloors();
        // Create a Datagram socket for both sending and receiving messages via UDP communication
        try {
            sendReceiveSocket = new DatagramSocket(69);
        } catch (SocketException se) {   // if socket creation fails
            se.printStackTrace();
            System.exit(1);
        }
    }



    /**
     * Populates the list of floors that the elevator will move between.
     */
    private void populateFloors() {
        floors.clear();
        floors.addAll(Arrays.asList(new Integer[]{1, 2, 3, 4, 5}));
    }



    /**
     * Increments floors one by one updates arrivalSignal after reaching new floor.
     */
    public void incrementFloor() {
        int topFloor = floors.size();
        int i = currentFloor;
        if (i < topFloor) {
            i++;
            currentFloor = i;
            arrivalSignal = i;
            System.out.println("\nCURRENT FLOOR: " + currentFloor);
        }
    }



    /**
     * Decrements floors one by one updates arrivalSignal after reaching new floor.
     */
    public void decrementFloor(){
        int bottomFloor = 1;
        int i = currentFloor;
        if (i > bottomFloor) {
            i--;
            arrivalSignal = i;
            currentFloor = i;
            System.out.println("\nCURRENT FLOOR: " + currentFloor);
        }
    }



    /**
     * Creates and returns a Datagram Packet using given input parameters.
     */
    public DatagramPacket createMessagePacket(byte typeByte, int floorNumber, String sentence) throws UnknownHostException {
        byte[] messageTypeBytes = new byte[] { 0x0, typeByte};
        byte[] floorNumberBytes = new byte[] {(byte) (floorNumber & 0xFF)};
        byte[] sentenceBytes = sentence.getBytes();

        ByteBuffer bb = ByteBuffer.allocate(messageTypeBytes.length + floorNumberBytes.length + sentenceBytes.length);

        //make read request
        bb.put(messageTypeBytes);
        bb.put(floorNumberBytes);
        bb.put(sentenceBytes);

        byte[] message = bb.array();
        InetAddress schedulerAddress = InetAddress.getByName("localhost");
        int schedulerPort = 1003;

        DatagramPacket sendPacket = new DatagramPacket(message, message.length, schedulerAddress, schedulerPort);
        return sendPacket;
    }



    /**
     * Process string message from scheduler containing stop signal.
     * @param stopSignalMessage
     */
    private boolean processStopSignalMessage (String stopSignalMessage){
        int signal = Integer.valueOf(stopSignalMessage);
        if (signal == 0) { // if signal is 0, return true.
            return true;
        }
        return false;
    }



    /**
     * Process string message from scheduler containing destination floor number for elevator to move to.
     * @param destFloorMessage
     */
    private void processDestinationFloorMessage (String destFloorMessage) {
        this.destination = Integer.valueOf(destFloorMessage);
        if (destination == currentFloor) {
            System.out.println("The elevator is already at destination floor " + destination + ".");
            state = ElevatorState.IDLE;
        } else if (destination > currentFloor) {
            System.out.println("Initiating move up from floor " + currentFloor + " to " + destination + "...");
            state = ElevatorState.MOVING_UP;
        } else if (destination < currentFloor) {
            System.out.println("Initiating move down from floor " + currentFloor + " to " + destination + "...");
            state = ElevatorState.MOVING_DOWN;
        } else {
            destination = -20; // invalid destination
            state = ElevatorState.IDLE;
        }
    }


    /**
     * Handles floor transition timeout by killing the ElevatorThread if there is a fault.
     * @param timeoutMillis
     * @throws InterruptedException
     */
    public synchronized void handleTimeout(long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long elapsedTime = 0;

        while (!timerInterrupted) {
            long remainingTime = timeoutMillis - elapsedTime;
            if (remainingTime <= 0) {
                break;
            }
            wait(remainingTime);
            elapsedTime = System.currentTimeMillis() - startTime;
        }

        // if after waiting, message to scheduler is still not acknowledged, kill ElevatorThread
        if (!timerInterrupted) {
            // handle timeout case
            System.out.println("Floor Transition Timeout!");
            //todo kill the thread
            System.exit(1);
        }
    }

    private boolean isConditionMet() {
        // check if the condition is met
        return true;
    }

    /**
     * ElevatorThread's run() method.
     */
    @Override
    public void run() {

        while (true) {

            switch (state) {

                /**
                 * Elevator state: IDLE
                 */
                case IDLE:

                    // Send move request to the scheduler and wait to hear back on destination floor
                    System.out.println("Elevator State: IDLE");

                    try {
                        // Create move request datagram packet
                        DatagramPacket moveRequestPacket = this.createMessagePacket((byte) 0x03, currentFloor, "");
                        // Send message to scheduler
                        sendReceiveSocket.send(moveRequestPacket);
                        // Wait for a response from the scheduler for the destination floor to move to
                        byte[] responseBytes = new byte[1024];
                        DatagramPacket moveRequestReceivePacket = new DatagramPacket(responseBytes, responseBytes.length);
                        sendReceiveSocket.receive(receivePacket);
                        String destinationFloorMessage = new String(responseBytes, 0, moveRequestReceivePacket.getLength());
                        System.out.println("Scheduler response to move request: " + destinationFloorMessage);
                        this.processDestinationFloorMessage(destinationFloorMessage);
                        // close port
                        //sendReceiveSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;


                /**
                 * Elevator state: MOVING_UP
                 */
                case MOVING_UP:

                    System.out.println("Elevator State: MOVING UP");

                    int floorDifference = destination - currentFloor;

                    // move up to the destination floor one floor at a time
                    for (int i = 0; i < floorDifference; i++) {
                        incrementFloor(); // go up 1 floor
                        try {
                            // Create arrival sensor message as elevator approaches next floor up
                            DatagramPacket arriveUpSignalPacket = this.createMessagePacket((byte) 0x01, arrivalSignal, "");
                            // Send arrival sensor message to scheduler
                            // start timer
                            handleTimeout(5000);
                            sendReceiveSocket.send(arriveUpSignalPacket);
                            // Wait for a response from the scheduler on whether to stop at this floor
                            byte[] responseBytes = new byte[1024];
                            DatagramPacket arriveUpReceivePacket = new DatagramPacket(responseBytes, responseBytes.length);
                            sendReceiveSocket.receive(arriveUpReceivePacket);
                            //floor transition timer was interrupted
                            timerInterrupted = true;
                            String stopSignalMessage = new String(responseBytes, 0, receivePacket.getLength());
                            System.out.println("Scheduler response to arrival sensor: " + stopSignalMessage);

                            this.stopSignal = processStopSignalMessage(stopSignalMessage);

                            // if stop is not requested, keep moving up. else, stop the elevator
                            if (!stopSignal) {continue;}

                            // if stop is requested
                            this.doorOpen = true;
                            System.out.println("\nElevator has stopped at " + currentFloor + ".");
                            //wait some time for load/unload, then close door and continue moving up
                            try {
                                Thread.sleep(2000); // elevator door stays open for 5 seconds
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            this.doorOpen = false;
                            // send a message to scheduler communicating that the door was open and closed
                                try {
                                    //create door opening and closing (after stopping at a new floor) to scheduler
                                    String stringMessage = "The door has opened and closed.";
                                    DatagramPacket doorClosedSendPacket = createMessagePacket((byte) 0x04, currentFloor, stringMessage);
                                    // Send door closed message to the scheduler
                                    sendReceiveSocket.send(doorClosedSendPacket);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            // reset stop signal to false until scheduler sets it to true again
                            stopSignal = false;
                        }
                        catch (IOException | InterruptedException e) {e.printStackTrace();}
                    }
                    break;


                /**
                 * Elevator state: MOVING_DOWN
                 */
                case MOVING_DOWN:
                    System.out.println("Elevator State: MOVING DOWN");
                    floorDifference = currentFloor - destination;
                    for (int i = 0; i < floorDifference; i++) {
                        decrementFloor();
                        try {
                            // Create arrival sensor message
                            DatagramPacket arriveDownSendPacket = createMessagePacket((byte) 0x01, arrivalSignal, "");
                            // Send arrival sensor message to scheduler
                            // start timer
                            handleTimeout(5000);
                            sendReceiveSocket.send(arriveDownSendPacket);
                            // Wait for a response from the scheduler on whether to stop at this floor
                            byte[] responseBytes = new byte[1024];
                            DatagramPacket arriveDownReceivePacket = new DatagramPacket(responseBytes, responseBytes.length);
                            sendReceiveSocket.receive(arriveDownReceivePacket);
                            // floor transition timer was interrupted
                            timerInterrupted = true;

                            String stopSignalMessage = new String(responseBytes, 0, receivePacket.getLength());
                            System.out.println("Scheduler response to arrival sensor: " + stopSignalMessage);

                            this.stopSignal = processStopSignalMessage(stopSignalMessage);

                            // if stop is not requested, move on to next iteration of loop
                            if (!stopSignal) {
                                continue;
                            }

                            System.out.println("\nElevator has stopped at " + currentFloor + ".");
                            this.doorOpen = true;
                            //wait some time for load/unload, then close door and continue moving down
                            try {
                                Thread.sleep(2000); // elevator door stays open for 5 seconds
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            this.doorOpen = false; // close door
                            // send a message to scheduler communicating that the door was open and closed
                            try {
                                //create door opening and closing (after stopping at a new floor) to scheduler
                                String stringMessage = "The door has opened and closed.";
                                DatagramPacket doorClosedSendPacket = createMessagePacket((byte) 0x06, currentFloor, stringMessage);
                                // Send door closed message to the scheduler
                                sendReceiveSocket.send(doorClosedSendPacket);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            // reset stop signal to false until scheduler sets it to true again
                            stopSignal = false;
                        }
                        catch (IOException | InterruptedException e) {e.printStackTrace();}
                    }
                    break;
            }
        }
    }
}
