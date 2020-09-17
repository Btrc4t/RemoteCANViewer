package main.model;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import javafx.fxml.FXML;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;

import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import main.data.seriesreducer.SeriesReducer;
import main.model.Constants;
import main.model.Signal;

public class InterfaceModel implements Initializable{
	@FXML
	private AnchorPane anchorPane;

	@FXML
	private LineChart<?, ?> signal_graph;

	@FXML
	private NumberAxis xAxis;

	@FXML
	private NumberAxis yAxis;

	@FXML
	private Button connectBtn;

	@FXML
	private AnchorPane configTabAnchor;

	@FXML
	private ScrollPane configScrollPane;

	@FXML
	private CheckBox displayAllSg;


	@FXML
	private TextField netPort;

	@FXML
	private Label statusIndicator;


	
	
	UDPDataServer udpDataServer;
	ObservableList<Signal> observableSigList;
	boolean udpSvStarted=false;
	ExecutorService dataTransferExecutor = Executors.newFixedThreadPool(1);

	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void initialize(URL arg0, ResourceBundle arg1) {
		
		/* check if all FXML elements are loaded correctly */
		assert anchorPane != null : "fx:id=\"anchorPane\" was not injected: check your FXML file 'sdbg_ui.fxml'.";
		assert signal_graph != null : "fx:id=\"signal_graph\" was not injected: check your FXML file 'sdbg_ui.fxml'.";
		assert xAxis != null : "fx:id=\"xAxis\" was not injected: check your FXML file 'sdbg_ui.fxml'.";
		assert yAxis != null : "fx:id=\"yAxis\" was not injected: check your FXML file 'sdbg_ui.fxml'.";
		assert connectBtn != null : "fx:id=\"connectBtn\" was not injected: check your FXML file 'sdbg_ui.fxml'.";
		assert configTabAnchor != null : "fx:id=\"configTabAnchor\" was not injected: check your FXML file 'sdbg_ui.fxml'.";
		assert configScrollPane != null : "fx:id=\"configScrollPane\" was not injected: check your FXML file 'sdbg_ui.fxml'.";
		assert displayAllSg != null : "fx:id=\"displayAllSg\" was not injected: check your FXML file 'sdbg_ui.fxml'.";


		/* Sets the signal_graph x and y axis to adjust automatically to the lines being displayed */
		xAxis.setAutoRanging(false);
		yAxis.setAutoRanging(true);
		signal_graph.setAnimated(false);
		
		
		
		/* the netPort textfield is made to accept only 6 digits
		 * to make casting to int (representing a port) easier */
		Pattern portPattern = Pattern.compile(Constants.instance().PORT_PATTERN);
		TextFormatter portFormatter = new TextFormatter((UnaryOperator<TextFormatter.Change>) change -> {
			return portPattern.matcher(change.getControlNewText()).matches() ? change : null;
		});
		


		netPort.setTextFormatter(portFormatter);
		netPort.setText(Constants.instance().DEFAULT_PORT);

		/* Don't create symbols for each point in a signal graph */
		signal_graph.setCreateSymbols(false);
		signal_graph.setTitle("Signals");

		VBox signalBox = new VBox();
		configScrollPane.setContent(signalBox);

		/* This list takes care of updating the Scrollable Pane on it's own
		 * It checks every element added to the list, so we only need to add to/remove from this list */
		observableSigList = FXCollections.observableList( new ArrayList<Signal>());
		observableSigList.addListener(new ListChangeListener() {
			@Override
			public void onChanged(ListChangeListener.Change change) {
				while (change.next()) {	
					if(change.wasAdded()) {
						ListIterator addedIter = change.getAddedSubList().listIterator();
						while(addedIter.hasNext()) {
							Signal crt = (Signal) addedIter.next();
							signalBox.getChildren().add(crt.getSignalCtrl());
						}	
					}
					if(change.wasRemoved()) {
						ListIterator remIter = change.getRemoved().listIterator();
						while(remIter.hasNext()) {
							Signal crt = (Signal) remIter.next();		        			
							signalBox.getChildren().remove(crt.getSignalCtrl());	        			
						}	
					}
				}
			}
		});

		
		
		/* LineChartWatcher takes care of resizing the xAxis to fit the signals present.	*/
		LineChartWatcher sigGraphWatcher = new LineChartWatcher(Constants.instance().MAXCHARTWINDOW, xAxis,signal_graph,observableSigList);
		Thread lcWatcherThread;
		lcWatcherThread = new Thread(sigGraphWatcher);
		lcWatcherThread.start();
		
	

		displayAllSg.selectedProperty().addListener(new ChangeListener<Boolean>() {
			public void changed(ObservableValue<? extends Boolean> ov,
					Boolean was_selected, Boolean is_selected) {
				if(is_selected) {
					for(int i = 0; i < observableSigList.size(); i++) {
						
						/*
						FutureTask<Integer> task = new FutureTask<>(new DataAcquisition((Integer) observableSigList.get(i).getSignalID(),observableSigList.get(i).getSignalRR(),observableSigList.get(i).getSignal_maxValue()));
						lineChartExecutor.submit(task); 
						*/
						observableSigList.get(i).displaySeries();
						/* Disable Checkboxes for individual signal display since Display All is selected */
						observableSigList.get(i).setSignalDisplayCheckmark(false);
					}                	    
				}else {
					for(int i = 0; i < observableSigList.size(); i++) { 
						/* Re-enable Checkboxes for individual signal display */
						observableSigList.get(i).setSignalDisplayCheckmark(true);                      	   

					}
					/* If signal from list isn't selected to be displayed */
					for(int i1 = 0; i1 < signal_graph.getData().size(); i1++) {
						String signalName = signal_graph.getData().get(i1).getName();                    			   
						/* Regex that keeps only digits, to extract the ID:  signalName.replaceAll("\\D+",""); */
						int signalID = Integer.parseInt(signalName.substring(0, signalName.indexOf(':')));

						if(!observableSigList.get(getIDIndex_fromObsList(observableSigList, signalID)).wantsDisplay()) {
							signal_graph.getData().remove(i1);
							i1--;
						}
					}
				}
			}
		});
	}
	
	public int getIDIndex_fromObsList(ObservableList<Signal> obsL, int ID) {
		for(int index=0;index<obsL.size();index++) {
			if( obsL.get(index).getSignalID() == ID) {
				return index;
			}
		}
		return -1;
	}

	@FXML
	void initServer(ActionEvent event) {

		Thread serverThread;
		
		if(!udpSvStarted) {
			udpDataServer = new UDPDataServer(Integer.valueOf(netPort.getText()), observableSigList, dataTransferExecutor,  signal_graph);
			serverThread = new Thread(udpDataServer);
			serverThread.start();
			netPort.setDisable(true);
			statusIndicator.setText(Constants.instance().SV_STARTED);
			connectBtn.setText(Constants.instance().SV_DISCONNECT);
			udpSvStarted = true;
		}else {
			udpDataServer.shutdown();
			statusIndicator.setText(Constants.instance().SV_NOT_STARTED);
			connectBtn.setText(Constants.instance().SV_CONNECT);
			netPort.setDisable(false);
			udpSvStarted = false;
		}
			
		
	}

}









