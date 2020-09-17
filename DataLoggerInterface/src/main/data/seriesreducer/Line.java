package main.data.seriesreducer;

import javafx.scene.chart.XYChart.Data;
/*
public class Line<P extends Point> {
    
    private P start;
    private P end;
    
    private double dx;
    private double dy;
    private double sxey;
    private double exsy;
    private double length;
    
    public Line(P start, P end) {
        this.start = start;
        this.end = end;
        dx = start.getX() - end.getX();
        dy = start.getY() - end.getY();
        sxey = start.getX() * end.getY();
        exsy = end.getX() * start.getY();
        length = Math.sqrt(dx*dx + dy*dy);
    }
    

    public List<P> asList() {
        return Arrays.asList(start, end);
    }
    
    double distance(P p) {
        return Math.abs(dy * p.getX() - dx * p.getY() + sxey - exsy) / length;
    }
}
*/
import javafx.scene.chart.XYChart.Series;

public class Line<P extends Data<Double,Double>> {
    
    private Data<Double, Double> start;
    private Data<Double, Double> end;
    
    private double dx;
    private double dy;
    private double sxey;
    private double exsy;
    private double length;
    
    public Line(Data<Double, Double> data, Data<Double, Double> data2) {
        this.start = data;
        this.end = data2;
        dx = data.getXValue() - data2.getXValue();
        dy = data.getYValue() - data2.getYValue();
        sxey = data.getXValue() * data2.getYValue();
        exsy = data2.getXValue() * data.getYValue();
        length = Math.sqrt(dx*dx + dy*dy);
    }
    

    public Series<Double, Double> asList() {
    	Series<Double, Double> retS = new Series<Double, Double>();
    	retS.getData().add(start);
    	retS.getData().add(end);
        return retS;
    }
    
    double distance(Data<Double, Double> data) {
        return Math.abs(dy * data.getXValue() - dx * data.getYValue() + sxey - exsy) / length;
    }
    
}


