package main.model;

import java.util.ArrayList;
import java.util.List;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;


import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;


import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import javafx.scene.layout.HBox;


import main.model.Constants;

public class Signal {
		private final SimpleStringProperty signalName;
		private final SimpleIntegerProperty signalID;
		private final SimpleDoubleProperty signalRR;
		private final HBox signalCtrl;
		CheckBox sigDisp;
		Label sigName;
		TextField sigRR;
		CheckBox sigAutoRate;
		List<Data<Double, Double>> series;
		Series<Double, Double> simplerSeries;
		LineChart<Double, Double> sig_graph;
		long time;
		boolean isBoolSignal;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		public Signal(String sName, Integer sID,double sRR,LineChart<Double, Double> signal_graph) {
			this.sig_graph = signal_graph;
			this.series = new ArrayList<Data<Double,Double>>();
			
			this.simplerSeries = new Series<Double, Double>();
			this.simplerSeries.setName(sID+":"+sName);
			isBoolSignal = false;

			
			
			this.signalCtrl = new HBox();
			this.signalCtrl.setPadding(new Insets(10, 0, 0, 25));
			this.signalCtrl.setSpacing(10);        	
			this.sigDisp = new CheckBox("Display");   
			this.sigName = new Label(sID+":"+sName);
			this.sigName.setMinWidth(300);
			this.sigRR = new TextField();
			this.sigRR.setPrefWidth(70);
			this.sigAutoRate = new CheckBox("Auto"); 
			this.signalCtrl.getChildren().addAll(sigDisp,sigName,sigRR,sigAutoRate);
			this.signalName = new SimpleStringProperty(sName);
			this.signalID = new SimpleIntegerProperty(sID);
			this.signalRR = new SimpleDoubleProperty(sRR);
			this.time = System.currentTimeMillis();
			
			sigName.setPrefWidth(70);

			sigDisp.selectedProperty().addListener(new ChangeListener<Boolean>() {
				public void changed(ObservableValue<? extends Boolean> ov,
						Boolean was_selected, Boolean is_selected) {
					if(is_selected) {
						
						signal_graph.getData().add(simplerSeries);
						
					} else {
						for(int i = 0; i<signal_graph.getData().size(); i++) {
							/* Remove that signal only if it's present on signal_graph */
							if(signal_graph.getData().get(i).getName().equals(sID+":"+sName)) {
								signal_graph.getData().remove(i);
							}
						}
					}
				}
			});

			/* the refreshInterval text field is made to accept only digits and the '.' character
			 * to make casting to double easier */
			Pattern pattern = Pattern.compile(Constants.instance().DOUBLE_PATTERN);
			TextFormatter formatter = new TextFormatter((UnaryOperator<TextFormatter.Change>) change -> {
				return pattern.matcher(change.getControlNewText()).matches() ? change : null;
			});

			sigRR.setTextFormatter(formatter);    		
			sigRR.textProperty().addListener((observable, oldValue, newValue) -> {
				if(Double.valueOf(newValue) < 100  && !this.sigAutoRate.isSelected()) {    	
					/* Set signal Refresh Rate to the one specified if Auto Rate is unchecked. 
					 * The auto rate is set by calculated sample time with the message Timestamps*/
					this.setSignalRR(Double.valueOf(newValue));
				}    			
			});

			/* On init use the default hardcoded refresh rate, mark the Auto checkbox and disable the textfield */
			sigAutoRate.setSelected(true);
			sigRR.setDisable(true);    		
			sigAutoRate.selectedProperty().addListener(new ChangeListener<Boolean>() {
				public void changed(ObservableValue<? extends Boolean> ov,
						Boolean was_selected, Boolean is_selected) {

						sigRR.setDisable(is_selected);              	   
					
				}
			});
		}

		public String getSignalName() {
			return signalName.get();
		}

		public void setSignalName(String fName) {
			signalName.set(fName);
		}

		public int getSignalID() {
			return signalID.get();
		}
		public void setSignalID(int fName) {
			signalID.set(fName);
		}

		public Double getSignalRR() {
			return signalRR.get();
		}
		public void setSignalRR(Double fName) {        	
			if(sigAutoRate.isSelected()) {
				signalRR.set(fName);
				sigRR.setText(String.valueOf(fName));
			}else{
				signalRR.set(fName);
			}

		}
		public HBox getSignalCtrl() {
			return signalCtrl;
		}

		public void setSignalDisplayCheckmark(boolean b) {
			this.sigDisp.setDisable(!b);			
		}
		
		public void addToSeries(double value) {
			if(this.isBoolSignal) {
				this.isBoolSignal = false;
			}
			
			int lastPointIndex = series.size();
			if(lastPointIndex == 0) {
				series.add(new Data<Double, Double>((double) lastPointIndex, value));
			}else {
				Double prevPoint = (Double) series.get(lastPointIndex-1).getXValue();
				series.add(new Data<Double, Double>(prevPoint + signalRR.get(), value));				
			}
		}
		
		public void addToSeries(boolean boolData) {
			
			if(!this.isBoolSignal) {
				this.isBoolSignal = true;
			}
			double value = (boolData) ? 1 : 0; /* if boolData == true, then value is 1, else 0 */
			int lastPointIndex = series.size();
			if(lastPointIndex == 0) {
				series.add(new Data<Double, Double>((double) lastPointIndex, value));
			}else {
				Double prevPoint = (Double) series.get(lastPointIndex-1).getXValue();
				series.add(new Data<Double, Double>(prevPoint + signalRR.get(), value));				
			}
		}
		
		public void updateSimpleSeries() {
			

				long timeNow = System.currentTimeMillis();
				
				if( ((timeNow - this.time) > Constants.instance().GRAPH_REFRESH_POINTS_TRIGGER) && series.size() > 2) {
					/* Calculate a simpler series every X seconds */
					
					
					/* remove series and add again */
					for (int i = 0; i < sig_graph.getData().size();i++) {
						if(sig_graph.getData().get(i).getName().contentEquals(sigName.getText())) {
							
							//if(this.isBoolSignal) {
								//simplerSeries = SeriesReducer.reduce(series, Constants.instance().BOOL_EPSILON);
							//}else {
								//simplerSeries = SeriesReducer.reduce(series, Constants.instance().NORMAL_EPSILON);
							//}
							
							//simplerSeries.setName(sigID.getText());
							//sig_graph.getData().get(i).setData(FXCollections.observableArrayList(series));
							
							if( (int) (Constants.instance().MAXCHARTWINDOW/this.signalRR.get()) < series.size()) {
								//if(this.isBoolSignal) {
									//simplerSeries = SeriesReducer.reduce(series, Constants.instance().BOOL_EPSILON);
								//}else {
									//simplerSeries = SeriesReducer.reduce(series, Constants.instance().NORMAL_EPSILON);
								//}
								simplerSeries.setData(FXCollections.observableArrayList(series.subList((int) (series.size() - (Constants.instance().MAXCHARTWINDOW/this.signalRR.get())), series.size())));
								sig_graph.getData().set(i, simplerSeries);
							}else {
								sig_graph.getData().get(i).getData().clear();
								sig_graph.getData().get(i).setData(FXCollections.observableArrayList(series));
							}
							
							
						}
					}
					this.time = System.currentTimeMillis();
				}
				
		}
		
		
		
		public boolean wantsDisplay() {
			return this.sigDisp.isSelected();		
		}
		
		public boolean isAutoRR() {
			return this.sigAutoRate.isSelected();		
		}



		public void displaySeries() {
			
			if(sig_graph.getData().indexOf(simplerSeries) == -1) {
				sig_graph.getData().add(this.simplerSeries);
			}			
		}
		
		public void destroy() {
			signalCtrl.getChildren().clear();
		}

		
	}