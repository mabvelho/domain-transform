package application;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class WindowController {

	// FXML variables
	
	@FXML
	private Button btnLoadImage;
	@FXML
	private Button btnFilterImage;
	@FXML
	private Button btnRestartImage;
	@FXML
	private Button btnCaptureVideo;
	@FXML
	private Button btnRestartVideo;

	@FXML
	private TextField textFieldCameraStatus;
	@FXML

	private TextField textFieldImageStatus;

	@FXML
	private ImageView imageViewSource;
	@FXML
	private ImageView imageViewTarget;
	@FXML
	private ImageView cameraFrameViewSource;
	@FXML
	private ImageView cameraFrameViewTarget;

	@FXML
	private CheckBox checkBoxFilterVideo;

	@FXML
	private Slider sliderImageSigmaSpace;
	@FXML
	private Slider sliderImageSigmaRange;
	@FXML
	private Slider sliderVideoSigmaSpace;
	@FXML
	private Slider sliderVideoSigmaRange;

	// OpenCV: video capture
	private VideoCapture capture = new VideoCapture();
	// Timer for video stream
	private ScheduledExecutorService timer;

	// Booleans for behavior control
	private boolean isCameraActive = false;
	private boolean isImageLoaded = false;

	private static int cameraId = 0;

	private final double sigmaSpaceDefault = 60;
	private final double sigmaRangeDefault = 0.4;

	private String imageErrorMessage = "Image status: Image could not be loaded.";
	private String imageOkMessage = "Image status: Image successfully loaded.";
	private String imageDefaultMessage = "Image status:";

	private String cameraErrorMessage = "Camera status: Not connected or undetected. Please try again with a camera connected.";
	private String cameraOkMessage = "Camera status: Connected.";
	private String cameraDefaultMessage = "Camera status:";

	private BufferedImage loadedImage = null;

	public void initialize() {
		this.capture = new VideoCapture();
		this.isCameraActive = false;
	}

	// behavior for button on-click usage
	@FXML
	protected void captureVideo(ActionEvent event) {

		// this.currentFrame.setFitWidth(600);
		this.cameraFrameViewSource.setPreserveRatio(true);
		this.cameraFrameViewTarget.setPreserveRatio(true);

		if (!this.isCameraActive) {
			// start capturing video

			this.capture.open(cameraId);

			// test for opening success
			if (this.capture.isOpened()) {
				System.out.println(cameraOkMessage);
				textFieldCameraStatus.setText(cameraOkMessage);

				this.isCameraActive = true;

				// 30 FPS
				Runnable frameGrabber = new Runnable() {

					@Override
					public void run() {

						// Camera Source display

						// copy a single frame from the webcam
						Mat sourceFrame = copyFrame();

						// show the frame if it exists
						if (sourceFrame.width() != 0 || sourceFrame.height() != 0) {
							Image imageToShow = ImageHandler.matToImage(sourceFrame);
							// show the frame
							updateImageView(cameraFrameViewSource, imageToShow);
						} else {
							// if there is no frame, erase it
							updateImageView(cameraFrameViewSource, null);
						}

						// Target Video display

						// grab and process a single frame from the webcam if Filter Video is on
						if (checkBoxFilterVideo.isSelected()) {
							Mat targetFrame = grabFrame();
							// convert and show the frame if it exists
							if (targetFrame.width() != 0 || targetFrame.height() != 0) {
								Image imageToShow = ImageHandler.matToImage(targetFrame);
								// show the frame
								updateImageView(cameraFrameViewTarget, imageToShow);
							} else {
								// if there is no frame, erase it
								updateImageView(cameraFrameViewTarget, null);
							}
						} else {
							updateImageView(cameraFrameViewTarget, null);
						}

					}
				};

				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

				// update the camera content
				this.btnCaptureVideo.setText("Stop Video Capture");
			} else {
				// log the error
				System.err.println(cameraErrorMessage);
				textFieldCameraStatus.setText(cameraErrorMessage);
			}
		} else {
			// the camera is not active at this point
			this.isCameraActive = false;
			// update again the camera content
			this.btnCaptureVideo.setText("Capture Video");
			updateImageView(cameraFrameViewSource, null);
			updateImageView(cameraFrameViewTarget, null);

			// stop the timer
			this.stopAcquisition();
		}
	}

	@FXML
	protected void loadImage(ActionEvent event) {
		try {
			FileChooser fileChooser = new FileChooser();
			ExtensionFilter extensionFilter = new ExtensionFilter("Image Files", "*.jpg", "*.png");
			fileChooser.getExtensionFilters().add(extensionFilter);
			fileChooser.setTitle("Image Search");
			File imageFile = fileChooser.showOpenDialog(App.stage);

			// image file location found
			if (imageFile != null) {
				isImageLoaded = true;
				loadedImage = ImageIO.read(imageFile);
				Image image = SwingFXUtils.toFXImage(loadedImage, null);

				// update source image view with loaded image
				updateImageView(imageViewSource, image);

				// empty filtered image view
				updateImageView(imageViewTarget, null);

				// display success message
				textFieldImageStatus.setText(imageOkMessage);
			}
		} catch (IOException exception) {
			textFieldImageStatus.setText(imageErrorMessage);
			System.err.println("Exception during image loading: " + exception.toString());
		}
	}

	@FXML
	protected void filterImage(ActionEvent event) {
		if (isImageLoaded) {
			updateImageView(imageViewTarget, null);
			
			// instantiate imageHandler for methods
			ImageHandler imageHandler = new ImageHandler();
			
			// copy loaded image
			BufferedImage imageCopy = loadedImage;		
			
			// get sigma space and range value from application sliders
			double sigmaSpace = sliderImageSigmaSpace.getValue();
			double sigmaRange = sliderImageSigmaRange.getValue();

			// filter image through Domain Transform with all info gathered
			BufferedImage filteredImage = imageHandler.NormalizedConvolution(imageCopy, sigmaSpace, sigmaRange);			
			Image convertedFilteredimage = SwingFXUtils.toFXImage(filteredImage, null);
			
			updateImageView(imageViewTarget, convertedFilteredimage);
		} else {
			if (imageViewTarget != null) {
				updateImageView(imageViewTarget, null);
			}

		}
	}

	@FXML
	protected void restartImage(ActionEvent event) {
		loadedImage = null;
		sliderImageSigmaSpace.setValue(sigmaSpaceDefault);
		sliderImageSigmaRange.setValue(sigmaRangeDefault);
		updateImageView(imageViewSource, null);
		updateImageView(imageViewTarget, null);
		textFieldImageStatus.setText(imageDefaultMessage);
	}

	@FXML
	protected void restartVideo(ActionEvent event) throws InterruptedException {
		sliderVideoSigmaSpace.setValue(sigmaSpaceDefault);
		sliderVideoSigmaRange.setValue(sigmaRangeDefault);
		checkBoxFilterVideo.setSelected(false);
		if (isCameraActive) {
			captureVideo(event);
		}
		Thread.sleep(100);
		textFieldCameraStatus.setText(cameraDefaultMessage);
		updateImageView(cameraFrameViewSource, null);
		updateImageView(cameraFrameViewTarget, null);

	}

	// auxiliary function for captureVideo
	private Mat copyFrame() {

		Mat frame = new Mat();

		// check if the capture is open
		if (this.capture.isOpened()) {
			try {
				// read the current frame
				this.capture.read(frame);
			} catch (Exception e) {
				// log the error
				System.err.println("Exception during source image elaboration: " + e);
			}

		}
		return frame;
	}

	// auxiliary function for captureVideo
	private Mat grabFrame() {

		Mat frame = new Mat();

		// check if the capture is open
		if (this.capture.isOpened()) {
			try {
				// read the current frame
				this.capture.read(frame);

				// if the frame is not empty, process it
				if (!frame.empty()) {

					// filtering process
					if (checkBoxFilterVideo.isSelected()) {

						// enable sigma space and range sliders
						if (sliderVideoSigmaSpace.isDisable() == true) {
							sliderVideoSigmaSpace.setDisable(false);
						}
						if (sliderVideoSigmaRange.isDisable() == true) {
							sliderVideoSigmaRange.setDisable(false);
						}

						// get sigma space and range value from application sliders
						double sigmaSpace = sliderVideoSigmaSpace.getValue();
						double sigmaRange = sliderVideoSigmaRange.getValue();

						// filter frame through Domain Transform
						ImageHandler imageHandler = new ImageHandler();

						int width = frame.width();
						int height = frame.height();

						BufferedImage filteredFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
						BufferedImage frameImageCopy = ImageHandler.matToBufferedImage(frame);
						filteredFrame = imageHandler.NormalizedConvolution(frameImageCopy, sigmaSpace, sigmaRange);

						frame = imageHandler.bufferedImageToMat(filteredFrame);
					} else {

						// disable sigma space and range sliders
						if (sliderVideoSigmaSpace.isDisable() == false) {
							sliderVideoSigmaSpace.setDisable(true);
						}
						if (sliderVideoSigmaRange.isDisable() == false) {
							sliderVideoSigmaRange.setDisable(true);
						}
					}

				}
			} catch (Exception e) {
				// log the error
				System.err.println("Exception during image elaboration: " + e);
			}

			// if capture is disabled, disable filtering option
		}
		return frame;
	}

	// Stop acquisition from the camera when video capture has been stopped
	protected void setClosed() {
		this.stopAcquisition();
	}

	private void stopAcquisition() {
		if (this.timer != null && !this.timer.isShutdown()) {
			try {
				// stop the timer
				this.timer.shutdown();
				this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// log any exception
				System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
			}
		}

		if (this.capture.isOpened()) {
			// release the camera
			this.capture.release();
		}
	}

	private void updateImageView(ImageView view, Image image) {
		Platform.runLater(() -> {
			view.imageProperty().set(image);
		});
	}
}
