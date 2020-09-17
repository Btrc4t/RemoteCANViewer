package main;

import java.io.IOException;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class InterfaceLauncher extends Application {
	  public static void main(String[] args) {
	    Application.launch(args);
	  }

	  @Override
	  public void start(Stage stage) throws IOException {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("view/sdbg_ui.fxml"));
	    Parent root =  (Parent) loader.load();
	   
	    // Load the FXML document
	    try {
		    Scene scene = new Scene(root);
		    stage.setScene(scene);
		    stage.setTitle("UDP Data Logger Interface");
		    stage.show();
		    stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
	            @Override
	            public void handle(WindowEvent we) {
	                System.exit(0);
	            }
	        });
	    }catch(Exception ex) {
	    	 System.out.println( "Exception on FXMLLoader.load()" );
	         System.out.println( "  * url: " + root );
	         System.out.println( "  * " + ex );
	         System.out.println( "    ----------------------------------------\n" );
	         throw ex;
	    }
	    
	  }
}
