package main.model;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import main.model.Signal;

public class LineChartWatcher implements Runnable {

	private double xWindowMax;
	private double crtMax = Constants.instance().SAFETY_CHART_SPACE;//0.000010
	private double crtMinRR = Constants.instance().DEFAULT_REFRESH_RATE;//0.000010
	private NumberAxis xAxisRef;
	private LineChart<?, ?> signalGraphRef;
	private volatile boolean done;
	private ObservableList<Signal> targetSigList;

	public LineChartWatcher(double maxChartWindow,NumberAxis xAx,LineChart<?, ?> sg, ObservableList<Signal> sgList) {
		this.xWindowMax = maxChartWindow;
		this.xAxisRef = xAx;
		this.signalGraphRef = sg;
		done = false;
		this.targetSigList = sgList;
	}

	public void shutdown() {
		done = true;
	}

	/* This task runs on a separate thread, separate from the FX User Interface */
	@Override
	public void run() {

		while(!done) {
			try {
				/* Sleep for 500ms, this doesn't need to use the processor all the time */
				Thread.sleep(Constants.instance().CHART_RES_SLEEP_T);
				/* Call update to simplify shown series */

				for(int i = 0;i<targetSigList.size();i++) {
					int index = i;
					Platform.runLater(new Runnable(){
						@Override
						public void run() {
							/* Adding the datapoint to the FX UI LineChart */
							targetSigList.get(index).updateSimpleSeries();
						}
					});

				}




			} catch (InterruptedException e) {
				/* Interrupted from sleep, not an issue */
				if(Constants.instance().DEBUG_EN) {
					System.out.println("Interrupted from sleep");
				}
			}
			
			
			

			for(int shownSig = 0;shownSig < this.signalGraphRef.getData().size(); shownSig++) {
				int index = shownSig;
				
				Platform.runLater(new Runnable(){
					@Override
					public void run() {
						/* Resizing the FX UI LineChart */
						int datasize = signalGraphRef.getData().get(index).getData().size();
						
						if(datasize > 0 ) {
							
							if(signalGraphRef.getData().size() > 1) {
								double oldMax = crtMax;
								if(Double.valueOf(signalGraphRef.getData().get(index).getData().get(datasize-1).getXValue().toString()) < oldMax )  {
									/* Nothing to do */
								}else {
									crtMax = Double.valueOf(signalGraphRef.getData().get(index).getData().get(datasize-1).getXValue().toString());
								}
							}else {
								crtMax = Double.valueOf(signalGraphRef.getData().get(index).getData().get(datasize-1).getXValue().toString());
							}
							if(Constants.instance().DEBUG_EN) {
								System.out.println("CrtMax is now: "+crtMax);
							}
						}
					}
				});
					
				
				
			}
			



			/* Set the upper bound of X Axis to fit the last data points present on the graph */
			if(xAxisRef.getUpperBound() != (crtMax*crtMinRR + Constants.instance().SAFETY_CHART_SPACE)) {
				Platform.runLater(new Runnable(){
					@Override
					public void run() {
						if(Constants.instance().DEBUG_EN) {
							System.out.println(crtMax + " * " + crtMinRR + " + " + Constants.instance().SAFETY_CHART_SPACE);
							System.out.println("Set new UpperBound to "+(crtMax+Constants.instance().SAFETY_CHART_SPACE));
						}
						xAxisRef.setUpperBound(crtMax + crtMax/20);
						xAxisRef.setTickUnit(crtMax/Constants.instance().CHART_DIVISIONS);
					}
				});
			}





			if((xAxisRef.getUpperBound() - xAxisRef.getLowerBound()) > (xWindowMax + Constants.instance().SAFETY_CHART_SPACE)) {
				/* Changes to the linechart need to be passed to the FX UI thread, cannot change the elements on current thread 
				 * That is the role of Platform.runLater() */
				Platform.runLater(new Runnable(){
					@Override
					public void run() {
						if(Constants.instance().DEBUG_EN) {
							System.out.println(xAxisRef.getUpperBound()+" - "+xWindowMax+" - "+Constants.instance().SAFETY_CHART_SPACE);
							System.out.println("Set new LowerBound to "+(xAxisRef.getUpperBound() - xWindowMax - Constants.instance().SAFETY_CHART_SPACE));
						}
						xAxisRef.setLowerBound((xAxisRef.getUpperBound() - xWindowMax - Constants.instance().SAFETY_CHART_SPACE));
					}
				});
			}
		}
	}
}