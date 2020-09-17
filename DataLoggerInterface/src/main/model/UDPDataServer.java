package main.model;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.chart.LineChart;
import main.model.Signal;
import main.data.seriesreducer.*;


public final class UDPDataServer implements Runnable {

	private volatile boolean done = false;
	private volatile boolean configPkg = false;
	private DatagramSocket socket;
	private byte[] buf = new byte[Constants.instance().MAX_UDP_PKG_SIZE];
	private int port;
	private ObservableList<Signal> targetSigList;
	private static ExecutorService dtExecRef;
	private Map<Integer,String> signalDictionary = new HashMap<Integer, String>();
	private static LineChart<Double, Double> sigGraphRef;
	
	
	@SuppressWarnings("unchecked")
	public UDPDataServer(int port, ObservableList<Signal> sigList,ExecutorService dtExec,LineChart<?, ?> signal_graph) {
		this.port=port;
		this.targetSigList = sigList; 
		UDPDataServer.dtExecRef = dtExec;
		UDPDataServer.sigGraphRef = (LineChart<Double, Double>) signal_graph;
		try {
			socket = new DatagramSocket(this.port);
			done = false;

		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	public void shutdown() {
		done = true;
		socket.close(); /* NOTE that this WILL throw an error, because the execution halts on waiting for a new packet
		and if that packet never comes, this thread never ends, so it is better to force close the socket, and garbage collection should take care of the rest */

		Platform.runLater(new Runnable(){
			@Override
			public void run() {
				/* If the signal is to be changed automatically, then use the server to set the Refresh Rate */
				for(int i = 0;i<targetSigList.size();i++) {
					targetSigList.get(i).destroy();
				}
				targetSigList.clear();
				sigGraphRef.getData().clear();
			}
		});
	}

	private static int getIntFromBytes(byte[] arr, int startIndex,int bytesToGet, boolean endianConvNeeded) {
		ByteBuffer wrapped = ByteBuffer.allocate(bytesToGet);

		byte[] reducedArray = new byte[bytesToGet];
		for(int i = 0; i < bytesToGet; i++) {
			reducedArray[i] = arr[i+startIndex];
		}
		wrapped = ByteBuffer.wrap(reducedArray);
		if(!endianConvNeeded) {
			wrapped.order(ByteOrder.LITTLE_ENDIAN);			
		}else {
			wrapped.order(ByteOrder.BIG_ENDIAN);
		}
		wrapped.rewind();
		return wrapped.getInt();
	}
	
	private static Entry<Integer, String> getIDandASCIIfromBytes(byte[] arr, int startIndex,int bytesToGet, boolean endianConvNeeded) {
		ByteBuffer wrapped = ByteBuffer.allocate(bytesToGet);

		byte[] reducedArray = new byte[bytesToGet];
		for(int i = 0; i < bytesToGet; i++) {
			reducedArray[i] = arr[i+startIndex];
		}
		wrapped = ByteBuffer.wrap(reducedArray);
		if(!endianConvNeeded) {
			wrapped.order(ByteOrder.LITTLE_ENDIAN);			
		}else {
			wrapped.order(ByteOrder.BIG_ENDIAN);
		}
		wrapped.rewind();
		byte sigID = wrapped.get();
		StringBuilder sb = new StringBuilder((Constants.instance().CFGPOINT_SIZE - 1));
		for(int i = 0; i < (Constants.instance().CFGPOINT_SIZE - 1); i++) {
			char crtCh = (char) wrapped.get();
			try {
				if(crtCh!=0x03) {
					sb.append(crtCh);
				}
				
			} catch(Exception e) {
				System.out.println("Invalid package error");
			}
		}
		
		Map.Entry<Integer, String> retV = new SignalEntry<Integer, String>(sigID & 0xFF,sb.toString());

		
		
		 return retV;
	}
	
	
	private static float getFloatFromBytes(byte[] arr, int startIndex,int bytesToGet, boolean endianConvNeeded) {
		ByteBuffer wrapped = ByteBuffer.allocate(bytesToGet);

		byte[] reducedArray = new byte[bytesToGet];
		for(int i = 0; i < bytesToGet; i++) {
			reducedArray[i] = arr[i+startIndex];
		}
		wrapped = ByteBuffer.wrap(reducedArray);
		if(!endianConvNeeded) {
			wrapped.order(ByteOrder.LITTLE_ENDIAN);			
		}else {
			wrapped.order(ByteOrder.BIG_ENDIAN);
		}
		wrapped.rewind();
		return wrapped.getFloat();
	}



	private byte getByteFromBytes(byte[] arr, int index) {
		return arr[index];
	}

	private static double getTimestampFromBytes(byte[] arr, int startIndex,int bytesToGet, boolean endianConvNeeded) {
		ByteBuffer wrapped;
		byte[] reducedArray = new byte[bytesToGet];
		for(int i = 0; i < bytesToGet; i++) {
			reducedArray[i] = arr[i+startIndex];
		}
		wrapped = ByteBuffer.wrap(reducedArray);
		if(!endianConvNeeded) {
			wrapped.order(ByteOrder.LITTLE_ENDIAN);			
		}else {
			wrapped.order(ByteOrder.BIG_ENDIAN);
		}
		wrapped.rewind();
		return wrapped.getDouble();
	}


	private static void addDataToSigID(int data, int id,ObservableList<Signal> targetSigList) {
		FutureTask<Integer> dtTask = new FutureTask<>(new DataTransfer(data, id, targetSigList));
		dtExecRef.submit(dtTask);
	}
	
	private static void addDataToSigID(float data, int id,ObservableList<Signal> targetSigList) {
		FutureTask<Integer> dtTask = new FutureTask<>(new DataTransfer(data, id, targetSigList));
		dtExecRef.submit(dtTask);
	}
	
	private static void addDataToSigID(boolean data, int id,ObservableList<Signal> targetSigList) {
		FutureTask<Integer> dtTask = new FutureTask<>(new DataTransfer(data, id, targetSigList));
		dtExecRef.submit(dtTask);
	}

	private void setRRofSigID(byte id, ObservableList<Signal> targetSigList2, double rr) {

		for(int i = 0;i<targetSigList.size();i++) {
			int index = i;
			/* test if signal ID is present among the populated signal list */
			if(targetSigList.get(i).getSignalID() == (int)id ) {
				Platform.runLater(new Runnable(){
					@Override
					public void run() {
						/* If the signal is to be changed automatically, then use the server to set the Refresh Rate */
						if(targetSigList.get(index).isAutoRR()) {
							targetSigList.get(index).setSignalRR(rr);
						}
					}
				});
			}
		}
	}

	@Override public synchronized void run() {
		boolean timerStarted=false;
		
		long startTime = System.currentTimeMillis();
		long endTime;
		long totalTime;
		
		while (!done) {
			DatagramPacket packet 
			= new DatagramPacket(buf, buf.length);
			try {
				socket.receive(packet);
			} catch (IOException e) {
				// TODO This throws an error if socket is closed in shutdown
				// e.printStackTrace();
			}


			InetAddress address = packet.getAddress();
			int port = packet.getPort();
			byte receivedID = 0;
			int dataP = 0;
			double packetAvgSampleT=0;
			byte receivedIDCheck=0;
			double stopTS=0;
			int nrOfDatapoints = 0;
			
			int receivedInt=0;
			boolean receivedBool=false;
			float receivedFloat=0;
			byte varType = 0;
			
			if(port != -1) { /* then socket has successfully opened */
				packet = new DatagramPacket(buf, buf.length, address, port);

				double startTS = getTimestampFromBytes(packet.getData(), 0,8,false);
				if(packet.getData().length == Constants.instance().MAX_UDP_PKG_SIZE) {
					/* Could be a config package, check */
					/* we subtract the size of the timestamp 8 to get the start byte correctly */
					stopTS = getTimestampFromBytes(packet.getData(), Constants.instance().MAX_UDP_PKG_SIZE - 8,8,false); 
					
					/* If timestamps are equal, this is a config frame
					 * During normal signal transfer, timestamps will never be equal */
					
					/* Don't compare with 0, can't be sure that the arithmetic operation will create a 0.0 exactly */
					if(!(Math.abs(stopTS-startTS) > Constants.instance().DOUBLE_COMPARISON_NEAR0)) {
						this.configPkg = true;
					}
				}
				
				
				if(!configPkg) {
					nrOfDatapoints = getByteFromBytes(packet.getData(), 8);
					
					if(nrOfDatapoints <= Constants.instance().MAX_PKG_DATAPOINTS) {
						varType = getByteFromBytes(packet.getData(), 17+((nrOfDatapoints)*5));
						if(Constants.instance().SV_DEBUG_EN) {
							System.out.println("varType is "+ varType);
						}
						for(dataP = 0; dataP < nrOfDatapoints; dataP++) {

							/* dataP*5 will be the offset */
							receivedID = getByteFromBytes(packet.getData(), 9+(dataP*5));
							if(varType == Constants.instance().BOOL_T) {	
								/* Boolean data type; Uses all of the 4 bytes as individual boolean values */
								Integer receivedBoolBytes = getIntFromBytes(packet.getData(), 10+(dataP*5),4,false); /* true if the int is 1, false otherwise) */
								for (byte b : ByteBuffer.allocate(4).putInt(receivedBoolBytes).array()) {
									receivedBool = (b == 0x01); /* if received byte is 1, then receivedBool will be true, otherwise false */
									addDataToSigID(receivedBool, receivedID,targetSigList);
								}

							}else if(varType == Constants.instance().INT_T) {
								/* Integer data type */
								receivedInt = getIntFromBytes(packet.getData(), 10+(dataP*5),4,false);
								addDataToSigID(receivedInt, receivedID,targetSigList);
							}else if(varType == Constants.instance().FLOAT_T) {
								/* Float data type */
								receivedFloat = getFloatFromBytes(packet.getData(), 10+(dataP*5),4,false);
								addDataToSigID(receivedFloat, receivedID,targetSigList);
							}
							
							

							if(0 == dataP) {
								/* All of the messages which will be received need to come with the same ID within one package, to be able to calculate sample time */
								/* Assign the ID which will verify every */
								receivedIDCheck = receivedID;
							}else {
								if(receivedIDCheck != receivedID) {
									System.out.println("Got ID "+receivedID+"; expected ID: "+receivedIDCheck);
									System.out.println("Got package with mixed signals or corrupted data");
								}
							}


							/* Benchmark code */
							if(Constants.instance().SV_DEBUG_EN) {
								if(!timerStarted && (int) receivedID == 1) {
									startTime = System.currentTimeMillis();
									timerStarted = true;
								}
							}

						}
						stopTS = getTimestampFromBytes(packet.getData(), 9+(dataP*5),8,false);
						
						
					}else {
						/* Invalid package */
						System.out.println("Got package with more bytes than the protocol is made to receive, datapoints: "+nrOfDatapoints);
					}
					
				}else {
					
					/* Config Package Case */
					
					/* Example of manually adding signals
					for(int i = 1; i <= 16; i++) {
						observableSigList.add(new Signal("Signal "+i,i,Constants.instance().DEFAULT_REFRESH_RATE,(LineChart<Double, Double>) signal_graph));
					}
					*/
					
					nrOfDatapoints = Constants.instance().MAX_CFG_DATAPOINTS; /*  */
					for(int dataPoint = 0; dataPoint < nrOfDatapoints; dataPoint++) {
						Entry<Integer, String> entry = getIDandASCIIfromBytes(packet.getData(), /* Get ascii  */
								8+(Constants.instance().CFGPOINT_SIZE*dataPoint) /* from index */
								,Constants.instance().CFGPOINT_SIZE,			/* number of bytes */
								false);											/* endianess conversion */
						
						
						
						if(	entry.getKey() < 255 
							&& !signalDictionary.containsKey(entry.getKey())) {
							/* Add entry to dictionary if it isn't contained */
							signalDictionary.put(entry.getKey(), entry.getValue());
							/* Add the signal from the FX UI Thread */
							Platform.runLater(new Runnable(){
								@Override
								public void run() {
									/* If the signal is to be changed automatically, then use the server to set the Refresh Rate */
									targetSigList.add(new Signal(entry.getValue(),entry.getKey(),Constants.instance().DEFAULT_REFRESH_RATE,(LineChart<Double, Double>) sigGraphRef));							
								}
							});							
						}
					}
					this.configPkg = false;
				}
				
				



				


				if(Constants.instance().SV_DEBUG_EN) {
					System.out.println("nrOfDatapoints: "+nrOfDatapoints);
					System.out.printf("startTS: %f\n", startTS);
					System.out.printf("stopTS: %f\n", stopTS);
					double deltaMs =(stopTS*100)-(startTS*100);
					System.out.printf("miliseconds passed in package assembly (Python's end): %f\n", deltaMs);
				}			
				/* Calulate and set average sampling time to signal receivedID*/
				
				
				
				if(varType == Constants.instance().BOOL_T) {
					/* If the variable is a boolean, the number of datapoints is 4 times higher, so effective refresh rate is 4 times faster */
					packetAvgSampleT = (stopTS-startTS)/(nrOfDatapoints*4);
					setRRofSigID(receivedID,targetSigList,packetAvgSampleT);
				}else {
					packetAvgSampleT = (stopTS-startTS)/nrOfDatapoints;
					setRRofSigID(receivedID,targetSigList,packetAvgSampleT);
				}
				
				
				if(Constants.instance().SV_DEBUG_EN) {
					System.out.printf("packet's average sample time for one datapoint: %f\n\n", packetAvgSampleT);
					if(timerStarted && (int) receivedID == 0) {
						endTime   = System.currentTimeMillis();
						totalTime = endTime - startTime;
						timerStarted=false;
						System.out.println("Total time benchmark (ms): "+totalTime);
					}
				}

			}
		}
	}
}


