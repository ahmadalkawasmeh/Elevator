package Threads;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

/** *
 * Class Scheduler used to translate data between Floors and Elevators.
 *
 * @author Josh Fuller
 */
public class SchedulerThread implements Runnable{


    SchedulerState state;

    private final DatagramSocket receiveSocket;
    private DatagramSocket sendSocket;
    private boolean floorEventReceived = false;

    public ArrayList<int[]> getSchedulerTasks() {
        return schedulerTasks;
    }

    private ArrayList<int[]> schedulerTasks;


    int currentPort;

    byte[] currentData;


    public enum SchedulerState {
        IDLE,
        PROCESSING_FLOOR_EVENT,
        PROCESSING_ARRIVAL_SENSOR,
        PROCESSING_MOVE_REQUEST,
        DISPATCHING_TO_FLOOR,
        SENDING_STOP_COMPLETE
    }

    public enum messageType {
        ARRIVAL_SENSOR,
        FLOOR_EVENT,
        MOVE_REQUEST,
        STOP_FINISHED,
        ERROR
    }

    public SchedulerThread(){
        state = SchedulerState.IDLE;

        this.schedulerTasks = new ArrayList<>();

        // Create a DatagramSocket on port 23
        try {
            receiveSocket = new DatagramSocket(1003);
            sendSocket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

    }

    public ArrayList<int[]> getEventList(){ //getter for tasks, for testing purposes
        return schedulerTasks;
    }

    public void idleState(){
        state = SchedulerState.IDLE;
    }

    public void processingFloorState(){
        state = SchedulerState.PROCESSING_FLOOR_EVENT;
    }

    public void processingMoveRequestState(){
        state = SchedulerState.PROCESSING_MOVE_REQUEST;
    }

    public void sendingStopCompleteState(){ state = SchedulerState.SENDING_STOP_COMPLETE;}

    public void processingSensorRequestState(){
        state = SchedulerState.PROCESSING_ARRIVAL_SENSOR;
    }

    public void dispatchingToFloorState(){
        state = SchedulerState.DISPATCHING_TO_FLOOR;
    }

    public SchedulerState getState(){
        return state;
    }


    /**
     * Takes the floor message and translates it into a list of tasks, the first task being the pickup floor and the
     * second task being the destination floor.
     *
     * @param tasks
     */
    public void sortElevatorTasks(byte[] tasks){
        int counter = 2;      // Starts at 2 because first 2 bytes are the message type
        while(tasks[counter] != 0 || tasks[counter+1] != 0) {
            int task1 = tasks[counter] & 0xff;
            int task2 = tasks[counter+1] & 0xff;
            schedulerTasks.add(new int[]{task1,task2});
            counter += 2;
        }
        System.out.print("SCHEDULER TASKS FROM MESSAGE: ");
        for (int[] pair : schedulerTasks) {
            System.out.print(Arrays.toString(pair) + " ");
        }
        System.out.println();
    }


    /**
     * Converts an int to a byte array for message purposes
     *
     * @param value Int value to convert
     * @return the value of the int to convert to a byte array
     */
    public static byte[] intToByteArray(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) (value >>> (i * 8));
        }
        return bytes;
    }

    /**
     * Scans a list of integer arrays (start floor, stop floor) to see if the start floor is at the current floor, and returns the pair as a byte array if true
     *
     * @param value Int value to convert
     * @return the value of the int to convert to a byte array
     */
    public byte[] findBothIntArray(int value, ArrayList<int[]> arrayList) {
        for (int[] array : arrayList) {
            if (array[0] == value) {
                byte[] byteArray = new byte[array.length];
                for (int i = 0; i < array.length; i++) {
                    byteArray[i] = (byte) array[i];
                }
                return byteArray;
            }
        }
        return null;
    }

    /**
     * Scans a list of integer arrays (start floor, stop floor) to see if the start floor is at the current floor, and returns the SECOND VALUE (destination floor) as a byte array if true
     *
     * @param value Int value to convert
     * @return the value of the int to convert to a byte array
     */
    public byte[] findSingleIntArray(int value, ArrayList<int[]> arrayList) {
        for (int[] array : arrayList) {
            if (array[0] == value) { //if found, send the related floor
                byte[] byteArray = new byte[1];
                byteArray[0] = (byte) array[1];
                return byteArray;
            }
        }
        byte[] byteArray = {0x0}; //if not found, send 0
        return byteArray;
    }

    /**
     * Closes sockets so tests don't throw bind exceptions, bot used in class
     */
    public void closeSocket(){
        receiveSocket.close();
        sendSocket.close();
    }

    /**
     * Parses through received messages to get their type, for easy switch statement implementation
     *
     * @param byteArray The messageType in byte[]
     * @return What message has been received
     */
    public static messageType parseByteArrayForType(byte[] byteArray) {

        messageType type = messageType.ERROR; // default value
        //System.out.println(byteArray[0]);
        System.out.println("SCHEDULER RECEIVED MESSAGE BYTE: " + byteArray[1]);
        // check first two bytes
        if (byteArray[0] == 0x0 && byteArray[1] == 0x1) {
            type = messageType.ARRIVAL_SENSOR;
        } else if (byteArray.length >= 2 && byteArray[0] == 0x0 && byteArray[1] == 0x2) {
            type = messageType.FLOOR_EVENT;
        } else if (byteArray.length >= 2 && byteArray[0] == 0x0 && byteArray[1] == 0x3) {
            type = messageType.MOVE_REQUEST;
        } else if (byteArray.length >= 2 && byteArray[0] == 0x0 && byteArray[1] == 0x4) {
            type = messageType.STOP_FINISHED;
        }

        return type;
    }

    /**
     * Converts the packet data's floor number into an integer.
     *
     * @param byteArray the received packet data containing request information
     * @return floorNum the floor number an elevator requests to go to
     */
    public static int parseByteArrayForFloorNum(byte[] byteArray) {
        return byteArray[3] & 0xff; // get 4th byte as int
    }

    /**
     * Waits for the elevator or floor to send a packet to the scheduler and receives that packet.
     *
     * @return receivePacket A packet sent from the elevator or floor
     */
    public DatagramPacket receivePacket(){
        DatagramPacket tempPacket;

        // Create a DatagramPacket to receive data from client
        byte[] receiveData = new byte[1024];
        tempPacket = new DatagramPacket(receiveData, receiveData.length);

        try {
            receiveSocket.receive(tempPacket); //Receive from anywhere
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Scheduler Received packet containing message (from port: " + tempPacket.getPort() + ") " + Arrays.toString(tempPacket.getData()));
        return tempPacket;
    }

    /** *
     * Creates a byte array based on the message type and floor number provided, to send to floor. 05 to indicate
     * that the elevator is about to stop, and 06 to indicate a stop has been completed
     *
     * @param type what type of message to send
     * @param floorNum the floor number to which the message is tied
     *
     * @return byteArrayToSend The finished byte array
     *
     * @author Josh Fuller
     */
    public byte[] createByteArray(byte type, byte floorNum){
        // Create data to send to server
        byte[] msgType = new byte[] { 0x0, type };
        byte[] floorNumInMsg = new byte[] {floorNum};

        //make combined data msg buffer for send/rcv
        ByteBuffer bb1 = ByteBuffer.allocate(msgType.length + floorNumInMsg.length);

        //make read request
        bb1.put(msgType);
        bb1.put(floorNumInMsg);

        byte[] byteArrayToSend = bb1.array();

        return byteArrayToSend;
    }

    /** *
     * Returns the byte array equivalent of the first element of the event list, when an elevator does a move_request
     *
     * @param intArrayList the event list
     *
     * @return byteArray The finished byte array
     *
     */
    public byte[] getNextMoveRequestEvent(ArrayList<int[]> intArrayList) {
        if(intArrayList.isEmpty()) {
            byte[] byteArray = {0x1,0x1};
            System.out.println("\n ------------------------------------------------------------");
            System.out.println("FLOOR EVENT TASK LIST COMPLETE, INITIATING DEFAULT CYCLE");
            System.out.println(" ------------------------------------------------------------\n");
            System.out.println(" ------------------------------------------------------------\n");
            return byteArray;
        }
        byte[] byteArray = new byte[2];
        int[] firstIntArray = intArrayList.get(0);
        byteArray[0] = (byte) firstIntArray[0];
        byteArray[1] = (byte) firstIntArray[1];
        intArrayList.remove(0);
        return byteArray;
    }

    /** *
     * The runnable portion of scheduler, responsible for acting as the translator from floor/elevator and back
     *
     * @author Josh Fuller
     */
    @Override
    public void run() {

        //initialise everything as null to start, so it is inside scope in case IDLE is skipped, though that is not possible practically
        DatagramPacket receivedPacket = null;

        // Get server IP address
        InetAddress IPAddress;
        try {
            IPAddress = InetAddress.getByName("localhost"); //edit with ip
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        while(true){

            switch(state) {
                case IDLE:
                    //System.out.println("GOING TO RECEIVE IN SCHED");
                    receivedPacket = receivePacket();
                    //System.out.println("PERFORMED RECEIVE IN SCHED");
                    currentData = receivedPacket.getData();

                    //System.out.println("TESTING SAVING THE DATA: " + Arrays.toString(receivedPacket.getData()) + " VERSUS " + Arrays.toString(currentData));

                    messageType messageType = parseByteArrayForType(receivedPacket.getData());
                    System.out.println("SCHEDULER RECEIVED MESSAGE TYPE: " + messageType);
                    //System.out.println("SCHEDULER RECEIVED FROM PORT: " + receivedPacket.getPort());
                    currentPort = receivedPacket.getPort();
                    //System.out.println("CURRENT PORT SAVED TO " + currentPort);
                    //based on message type, go to state
                    if (messageType == SchedulerThread.messageType.FLOOR_EVENT) {
                        processingFloorState();
                    } else if(messageType == SchedulerThread.messageType.ARRIVAL_SENSOR) {
                        processingSensorRequestState();
                        //System.out.println("SCHEDULER: MESSAGE TYPE ARRIVAL SENSOR FROM FLOOR: " + parseByteArrayForFloorNum(currentData));
                    } else if(messageType == SchedulerThread.messageType.MOVE_REQUEST) {
                        processingMoveRequestState();
                    } else if(messageType == SchedulerThread.messageType.STOP_FINISHED) {
                        //System.out.println("SCHEDULER: MESSAGE TYPE STOP_COMPLETE FROM ELEVATOR " + currentPort + " AT FLOOR " + parseByteArrayForFloorNum(currentData));
                        sendingStopCompleteState();
                    }
                    else if (messageType == SchedulerThread.messageType.ERROR) {
                        System.out.println("ERROR DETECTED IN SCHEDULER MESSAGE WITH TYPE: " + messageType);
                    }


                    break;

                case PROCESSING_FLOOR_EVENT:
                    floorEventReceived = true;                  // Allows for move requests to happen
                    sortElevatorTasks(currentData);


                    System.out.println("SCHEDULER... FLOOR EVENT DATA: " + Arrays.toString(currentData));
                    idleState();
                    break;

                case PROCESSING_MOVE_REQUEST:
                    if(floorEventReceived){

                        byte[] destinationFloorMessage = getNextMoveRequestEvent(schedulerTasks);
                        System.out.println("\nSCHEDULER RESPONSE TO MOVE REQUEST IS FLOOR SET: " + Arrays.toString(destinationFloorMessage));

                        System.out.println("SCHEDULER SENDING MESSAGE BACK TO PORT: " + currentPort + "\n");

                        DatagramPacket sendElevatorMovePacket = new DatagramPacket(destinationFloorMessage, destinationFloorMessage.length, IPAddress, currentPort);

                        try {
                            sendSocket.send(sendElevatorMovePacket);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                    } else {
                        System.out.println("NO EVENT LIST YET, RETURNING TO IDLE AND SENDING DEFAULT (FLOOR 1*)");
                        byte[] destinationFloorMessage = {0x1,0x1};
                        DatagramPacket sendElevatorMovePacket = new DatagramPacket(destinationFloorMessage, destinationFloorMessage.length, IPAddress, currentPort);
                        try {
                            sendSocket.send(sendElevatorMovePacket);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    idleState();
                    break;

                case PROCESSING_ARRIVAL_SENSOR:

                    int currentArrivingFloorNum = parseByteArrayForFloorNum(currentData); //gets the 4th byte as the floor num, message = 2 bytes mode + 0 byte + floor num byte

                    byte[] stopFloorMessage = findSingleIntArray(currentArrivingFloorNum, schedulerTasks);

                    DatagramPacket sendElevatorStopPacket = new DatagramPacket(stopFloorMessage, stopFloorMessage.length, IPAddress, currentPort);//SEND TO ELEVATOR TAT ASKED TO MOVE
                    try {
                        sendSocket.send(sendElevatorStopPacket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }


                    if(stopFloorMessage[0] != 0x0){
                        System.out.println("SENDING START_STOP TO FLOOR");
                        dispatchingToFloorState();
                    } else{
                        System.out.println("SCHEDULER: ELEVATOR SHOULD NOT STOP UPON ARRIVAL");
                        idleState();
                    }


                    break;

                case DISPATCHING_TO_FLOOR: //Case where the elevator is about to stop (the floor should start its timer)

                    int floorNumber = parseByteArrayForFloorNum(currentData); //the floor it stopped at, have to do it again might not be init

                    byte sendFloorData = (byte) floorNumber; //the floor data to send to the floor

                    byte[] aboutToStopMessage = createByteArray((byte) 5, sendFloorData); //msg type + floor num

                    DatagramPacket sendFloorPacket = new DatagramPacket(aboutToStopMessage, aboutToStopMessage.length, IPAddress, 2529);//SEND TO FLOOR

                    try {
                        sendSocket.send(sendFloorPacket);//SEND TO FLOOR
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }


                    //go to idle state
                    idleState();
                    break;

                case SENDING_STOP_COMPLETE:// the case where the elevator successfully let passengers on/off (the floor should stop its timer)

                    //System.out.println("INSIDE CASE SENDING_STOP_COMPLETE");

                    int floorNumberStoppedAt = parseByteArrayForFloorNum(currentData); //the floor it stopped at, have to do it again might not be init

                    byte byteEq = (byte) floorNumberStoppedAt;

                    byte[] completeStopMessage = createByteArray((byte) 6, byteEq);

                    DatagramPacket sendFloorSecondPacket = new DatagramPacket(completeStopMessage, completeStopMessage.length, IPAddress, 2530);



                    try {
                        sendSocket.send(sendFloorSecondPacket);//SEND TO FLOOR
                        System.out.println("STOP_COMPLETE SENT TO FLOOR, CONTAINS " + Arrays.toString(completeStopMessage));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    //go to idle state
                    idleState();
                    break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}

        }
    }
}