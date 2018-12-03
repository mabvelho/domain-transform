package application;

import org.opencv.core.Core;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class App extends Application {
	
	static Stage stage = null;
	
	@Override
	public void start(Stage primaryStage) {
		stage = primaryStage;
		int screenWidth = 1280;
		int screenHeight = 720;

		try {
			// load FXML from fxml file
			FXMLLoader loader = new FXMLLoader(getClass().getResource("window.fxml"));
			// store the root element for controller usage
			BorderPane root = (BorderPane) loader.load();

			// Load scene, size defined by variables
			Scene scene = new Scene(root, screenWidth, screenHeight);

			// Get style definitions from .css
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());

			
			stage.setTitle("Domain Transform");
			stage.setScene(scene);
			stage.setResizable(false);			
			stage.show();

			WindowController controller = loader.getController();
			stage.setOnCloseRequest((new EventHandler<WindowEvent>() {
				public void handle(WindowEvent we) {
					controller.setClosed();
				}
			}));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		launch(args);
	}
}
