package main.data.seriesreducer;

import java.util.List;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;

public class SeriesReducer {

    /**
     * Reduces number of points in given series using Ramer-Douglas-Peucker algorithm.
     * 
     * @param list
     *          initial, ordered list of points (objects implementing the {@link Point} interface)
     * @param epsilon
     *          allowed margin of the resulting curve, has to be > 0
     */
    public static <P extends Data<Double,Double>> Series<Double, Double> reduce(List<Data<Double, Double>> list, double epsilon) {
        if (epsilon < 0) {
            throw new IllegalArgumentException("Epsilon cannot be less then 0.");
        }
        double furthestPointDistance = 0.0;
        int furthestPointIndex = 0;
        Line<P> line = new Line<P>(list.get(0), list.get(list.size() - 1));
        for (int i = 1; i < list.size() - 1; i++) {
            double distance = line.distance(list.get(i));
            if (distance > furthestPointDistance ) {
                furthestPointDistance = distance;
                furthestPointIndex = i;
            }
        }
        if (furthestPointDistance > epsilon) {
        	Series<Double, Double> reduced1 = reduce(list.subList(0, furthestPointIndex+1), epsilon);
        	Series<Double, Double> reduced2 = reduce(list.subList(furthestPointIndex, list.size()), epsilon);
        	Series<Double, Double> result = reduced1;
            result.getData().addAll(reduced2.getData().subList(1, reduced2.getData().size()));
            return result;
        } else {
            return line.asList();
        }
    }
    
}
