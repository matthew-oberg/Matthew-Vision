import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoSource;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.*;
import edu.wpi.first.vision.VisionPipeline;
import edu.wpi.first.vision.VisionThread;

import org.opencv.core.Mat;
import org.opencv.core.*;
import org.opencv.imgproc.*;

public final class Main {
	
	private static String configFile = "/boot/frc.json";

	@SuppressWarnings("MemberName")
	public static class CameraConfig {
		public String name;
		public String path;
		public JsonObject config;
		public JsonElement streamConfig;
	}

	private static int team;
	private static boolean server;
	private static List<CameraConfig> cameraConfigs = new ArrayList<>();
	
	private static final Object imgLock = new Object();
	
	public static void parseError(String str) {
		System.err.println("config error in '" + configFile + "': " + str);
	}

	public static boolean readCameraConfig(JsonObject config) {
		CameraConfig cam = new CameraConfig();

		JsonElement nameElement = config.get("name");
		if (nameElement == null) {
			parseError("could not read camera name");
		return false;
		}
		cam.name = nameElement.getAsString();

		JsonElement pathElement = config.get("path");
		if (pathElement == null) {
			parseError("camera '" + cam.name + "': could not read path");
			return false;
		}
		cam.path = pathElement.getAsString();

		cam.streamConfig = config.get("stream");

		cam.config = config;

		cameraConfigs.add(cam);
	
		return true;
	}

	@SuppressWarnings("PMD.CyclomaticComplexity")
	public static boolean readConfig() {
		JsonElement top;
		try {
			top = new JsonParser().parse(Files.newBufferedReader(Paths.get(configFile)));
		} catch (IOException ex) {
			System.err.println("could not open '" + configFile + "': " + ex);
			return false;
		}

		if (!top.isJsonObject()) {
		parseError("must be JSON object");
		return false;
		}
		
		JsonObject obj = top.getAsJsonObject();

		JsonElement teamElement = obj.get("team");
		if (teamElement == null) {
			parseError("could not read team number");
			return false;
		}
    
		team = teamElement.getAsInt();

		if (obj.has("ntmode")) {
			String str = obj.get("ntmode").getAsString();
			if ("client".equalsIgnoreCase(str)) {
				server = false;
			} else if ("server".equalsIgnoreCase(str)) {
				server = true;
			} else {
				parseError("could not understand ntmode value '" + str + "'");
			}
		}

		JsonElement camerasElement = obj.get("cameras");
		
		if (camerasElement == null) {
		parseError("could not read cameras");
		return false;
		}
		
		JsonArray cameras = camerasElement.getAsJsonArray();
		for (JsonElement camera : cameras) {
			if (!readCameraConfig(camera.getAsJsonObject())) {
				return false;
			}
		}

		return true;
	}

	public static VideoSource startCamera(CameraConfig config) {
		System.out.println("Starting camera '" + config.name + "' on " + config.path);
		CameraServer inst = CameraServer.getInstance();
		UsbCamera camera = new UsbCamera(config.name, config.path);
		MjpegServer server = inst.startAutomaticCapture(camera);

		Gson gson = new GsonBuilder().create();

		camera.setConfigJson(gson.toJson(config.config));
		camera.setConnectionStrategy(VideoSource.ConnectionStrategy.kKeepOpen);

		if (config.streamConfig != null) {
			server.setConfigJson(gson.toJson(config.streamConfig));
		}

		return camera;
	}
	
	public static class DetectDouble implements VisionPipeline {

		private Mat hsvThresholdOutput = new Mat();
		private ArrayList<MatOfPoint> findContoursOutput = new ArrayList<MatOfPoint>();
		private ArrayList<MatOfPoint> filterContoursOutput = new ArrayList<MatOfPoint>();

		static {
			System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		}

		@Override
		public void process(Mat source0) {

			// Step HSV_Threshold0:
			Mat hsvThresholdInput = source0;
			double[] hsvThresholdHue = {71.22302158273381, 93.99317406143345};
			double[] hsvThresholdSaturation = {0.0, 255.0};
			double[] hsvThresholdValue = {233.90287769784175, 255.0};
			hsvThreshold(hsvThresholdInput, hsvThresholdHue, hsvThresholdSaturation, hsvThresholdValue, hsvThresholdOutput);

			// Step Find_Contours0:
			Mat findContoursInput = hsvThresholdOutput;
			boolean findContoursExternalOnly = false;
			findContours(findContoursInput, findContoursExternalOnly, findContoursOutput);

			// Step Filter_Contours0:
			ArrayList<MatOfPoint> filterContoursContours = findContoursOutput;
			double filterContoursMinArea = 250.0;
			double filterContoursMinPerimeter = 0;
			double filterContoursMinWidth = 0;
			double filterContoursMaxWidth = 1000;
			double filterContoursMinHeight = 0;
			double filterContoursMaxHeight = 1000;
			double[] filterContoursSolidity = {49.46043165467626, 100};
			double filterContoursMaxVertices = 1000000;
			double filterContoursMinVertices = 0;
			double filterContoursMinRatio = 0;
			double filterContoursMaxRatio = 1000;
			filterContours(filterContoursContours, filterContoursMinArea, filterContoursMinPerimeter, filterContoursMinWidth, filterContoursMaxWidth, filterContoursMinHeight, filterContoursMaxHeight, filterContoursSolidity, filterContoursMaxVertices, filterContoursMinVertices, filterContoursMinRatio, filterContoursMaxRatio, filterContoursOutput);

		}

		/**
		 * This method is a generated getter for the output of a HSV_Threshold.
		 * @return Mat output from HSV_Threshold.
		 */
		public Mat hsvThresholdOutput() {
			return hsvThresholdOutput;
		}

		/**
		 * This method is a generated getter for the output of a Find_Contours.
		 * @return ArrayList<MatOfPoint> output from Find_Contours.
		 */
		public ArrayList<MatOfPoint> findContoursOutput() {
			return findContoursOutput;
		}

		/**
		 * This method is a generated getter for the output of a Filter_Contours.
		 * @return ArrayList<MatOfPoint> output from Filter_Contours.
		 */
		public ArrayList<MatOfPoint> filterContoursOutput() {
			return filterContoursOutput;
		}

		/**
		 * Segment an image based on hue, saturation, and value ranges.
		 *
		 * @param input The image on which to perform the HSL threshold.
		 * @param hue The min and max hue
		 * @param sat The min and max saturation
		 * @param val The min and max value
		 */
		private void hsvThreshold(Mat input, double[] hue, double[] sat, double[] val, Mat out) {
			Imgproc.cvtColor(input, out, Imgproc.COLOR_BGR2HSV);
			Core.inRange(out, new Scalar(hue[0], sat[0], val[0]), new Scalar(hue[1], sat[1], val[1]), out);
		}

		/**
		 * Sets the values of pixels in a binary image to their distance to the nearest black pixel.
		 * @param input The image on which to perform the Distance Transform.
		 */
		private void findContours(Mat input, boolean externalOnly, List<MatOfPoint> contours) {
			Mat hierarchy = new Mat();
			contours.clear();
			int mode;
			if (externalOnly) {
				mode = Imgproc.RETR_EXTERNAL;
			} else {
				mode = Imgproc.RETR_LIST;
			}
			int method = Imgproc.CHAIN_APPROX_SIMPLE;
			Imgproc.findContours(input, contours, hierarchy, mode, method);
		}

		/**
		 * Filters out contours that do not meet certain criteria.
		 * @param inputContours is the input list of contours
		 * @param output is the the output list of contours
		 * @param minArea is the minimum area of a contour that will be kept
		 * @param minPerimeter is the minimum perimeter of a contour that will be kept
		 * @param minWidth minimum width of a contour
		 * @param maxWidth maximum width
		 * @param minHeight minimum height
		 * @param maxHeight maximimum height
		 * @param minVertexCount minimum vertex Count of the contours
		 * @param maxVertexCount maximum vertex Count
		 * @param minRatio minimum ratio of width to height
		 * @param maxRatio maximum ratio of width to height
		 */
		private void filterContours(List<MatOfPoint> inputContours, double minArea, double minPerimeter, double minWidth, double maxWidth, double minHeight, double maxHeight, double[] solidity, double maxVertexCount, double minVertexCount, double minRatio, double maxRatio, List<MatOfPoint> output) {
			final MatOfInt hull = new MatOfInt();
			output.clear();

			for (int i = 0; i < inputContours.size(); i++) {
				final MatOfPoint contour = inputContours.get(i);
				final Rect bb = Imgproc.boundingRect(contour);
				if (bb.width < minWidth || bb.width > maxWidth)
					continue;
				if (bb.height < minHeight || bb.height > maxHeight)
					continue;
				final double area = Imgproc.contourArea(contour);
				if (area < minArea)
					continue;
				if (Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true) < minPerimeter)
					continue;
				Imgproc.convexHull(contour, hull);
				MatOfPoint mopHull = new MatOfPoint();
				mopHull.create((int) hull.size().height, 1, CvType.CV_32SC2);
				for (int j = 0; j < hull.size().height; j++) {
					int index = (int)hull.get(j, 0)[0];
					double[] point = new double[] { contour.get(index, 0)[0], contour.get(index, 0)[1]};
					mopHull.put(j, 0, point);
				}
				final double solid = 100 * area / Imgproc.contourArea(mopHull);
				if (solid < solidity[0] || solid > solidity[1])
					continue;
				if (contour.rows() < minVertexCount || contour.rows() > maxVertexCount)
					continue;
				final double ratio = bb.width / (double)bb.height;
				if (ratio < minRatio || ratio > maxRatio)
					continue;
				output.add(contour);
			}
		}
	}

	public static void main(String... args) {
		if (args.length > 0) {
			configFile = args[0];
		}

		if (!readConfig()) {
			return;
		}

		NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
		if (server) {
			System.out.println("Setting up NetworkTables server");
			ntinst.startServer();
		} else {
			System.out.println("Setting up NetworkTables client for team " + team);
			ntinst.startClientTeam(team);
		}
		
		NetworkTable table = ntinst.getTable("vision");
		NetworkTableEntry ballZeroX = table.getEntry("ballZeroX");
		NetworkTableEntry ballZeroY = table.getEntry("ballZeroY");
		NetworkTableEntry ballOneX = table.getEntry("ballOneX");
		NetworkTableEntry ballOneY = table.getEntry("ballOneY");
		NetworkTableEntry ballContoursCount = table.getEntry("ballContoursCount");
		NetworkTableEntry hatchZeroX = table.getEntry("hatchZeroX");
		NetworkTableEntry hatchZeroY = table.getEntry("hatchZeroY");
		NetworkTableEntry hatchOneX = table.getEntry("hatchOneX");
		NetworkTableEntry hatchOneY = table.getEntry("hatchOneY");
		NetworkTableEntry hatchContoursCount = table.getEntry("hatchContoursCount");

		List<VideoSource> cameras = new ArrayList<>();
		for (CameraConfig cameraConfig : cameraConfigs) {
			cameras.add(startCamera(cameraConfig));
		}

		if (cameras.size() >= 2) {
			VisionThread visionThreadBall = new VisionThread(cameras.get(0), new DetectDouble(), pipeline -> {
				if (pipeline.filterContoursOutput().size() >= 2) {
					Rect zero = Imgproc.boundingRect(pipeline.filterContoursOutput().get(0));
					Rect one = Imgproc.boundingRect(pipeline.filterContoursOutput().get(1));
					synchronized (imgLock) {
						ballZeroX.setDouble(zero.x + (zero.width / 2));
						ballZeroY.setDouble(zero.y + (zero.width / 2));
						ballOneX.setDouble(one.x + (one.width / 2));
						ballOneY.setDouble(one.y + (one.width / 2));
					}

					//x: (0, 320)
				} else {
					synchronized (imgLock) {
						ballZeroX.setDouble(Double.MAX_VALUE / 2);
						ballZeroY.setDouble(Double.MAX_VALUE / 2);
						ballOneX.setDouble(Double.MAX_VALUE / 2);
						ballOneY.setDouble(Double.MAX_VALUE / 2);
					}
				}
				synchronized (imgLock) {
					ballContoursCount.setDouble(pipeline.filterContoursOutput().size());
				}
			});
			visionThreadBall.start();

			VisionThread visionThreadHatch = new VisionThread(cameras.get(1), new DetectDouble(), pipeline -> {
				if (pipeline.filterContoursOutput().size() >= 2) {
					Rect zero = Imgproc.boundingRect(pipeline.filterContoursOutput().get(0));
					Rect one = Imgproc.boundingRect(pipeline.filterContoursOutput().get(1));
					synchronized (imgLock) {
						hatchZeroX.setDouble(zero.x + (zero.width / 2));
						hatchZeroY.setDouble(zero.y + (zero.width / 2));
						hatchOneX.setDouble(one.x + (one.width / 2));
						hatchOneY.setDouble(one.y + (one.width / 2));
					}
					
					//x: (0, 320)
				} else {
					synchronized (imgLock) {
						hatchZeroX.setDouble(Double.MAX_VALUE / 2);
						hatchZeroY.setDouble(Double.MAX_VALUE / 2);
						hatchOneX.setDouble(Double.MAX_VALUE / 2);
						hatchOneY.setDouble(Double.MAX_VALUE / 2);
					}
				}
				synchronized (imgLock) {
					hatchContoursCount.setDouble(pipeline.filterContoursOutput().size());
				}
			});
			visionThreadHatch.start();
		}
		
		while (true) {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ex) {
				return;
			}
		}
	}
	
	private Direction getDirection(MatOfPoint contour) {
		Rect rect = Imgproc.boundingRect(contour);
		
		Point left = null;
		Point right = null;
		for (Point p : contour.toArray()) {
			if (p.x == rect.x)
				left = p;
			if (p.x == rect.x + rect.width)
				right = p;
		}
		
		if (left == null || right == null || left.y > right.y) {
			return Direction.Right; // if it always says right, then left/right remain null
		} else {
			return Direction.Left;
		}
	}
	
	private enum Direction {
		Left, Right
	}
}