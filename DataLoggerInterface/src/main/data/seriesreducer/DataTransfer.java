package main.data.seriesreducer;


import java.util.concurrent.Callable;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import main.model.Constants;
import main.model.Signal;

public class DataTransfer implements Callable<Integer> {

	private int intData;
	private boolean boolData;
	private float flData;
	private int id;
	private byte varType;
	private ObservableList<Signal> targetSigList;

        /* This DataAcquisition task gets the signal as a parameter */
        public DataTransfer(int data, int id,ObservableList<Signal> targetSigList) {
            this.intData = data;
            this.id = id;
            this.targetSigList = targetSigList;
            this.varType = Constants.instance().INT_T;
        }
        
        public DataTransfer(boolean data, int id,ObservableList<Signal> targetSigList) {
            this.boolData = data;
            this.id = id;
            this.targetSigList = targetSigList;
            this.varType = Constants.instance().BOOL_T;
        }
        
        public DataTransfer(float data, int id,ObservableList<Signal> targetSigList) {
            this.flData = data;
            this.id = id;
            this.targetSigList = targetSigList;
            this.varType = Constants.instance().FLOAT_T;
        }

        
        /* This task runs on a separate thread, separate from the FX User Interface */
		@Override
        public Integer call() throws Exception {
        	for(int i = 0;i<targetSigList.size();i++) {
    			int index = i;	
    			if(targetSigList.get(index).getSignalID() == (int)id ) {
    				Platform.runLater(new Runnable(){
    					@Override
    					public void run() {
    						/* Adding the datapoint to the series */
    						
    						if(varType == Constants.instance().BOOL_T) {	
    		    				/* Boolean data type */
    							targetSigList.get(index).addToSeries(boolData);
    						}else if(varType == Constants.instance().INT_T) {
    							/* Integer data type */
    							targetSigList.get(index).addToSeries(intData);
    						}else if(varType == Constants.instance().FLOAT_T) {
    							/* Float data type */
    							targetSigList.get(index).addToSeries(flData);
    						}
    						
    						
    					}
    				});
    				
    			}
    		}
			return 0;
        }
    }