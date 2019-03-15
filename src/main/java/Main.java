/*----------------------------------------------------------------------------*/
/* Copyright (c) 2019 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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
import edu.wpi.first.vision.VisionRunner;
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

		public Mat hsvThresholdOutput() {
			return hsvThresholdOutput;
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

		public ArrayList<MatOfPoint> findContoursOutput() {
			return findContoursOutput;
		}

		public ArrayList<MatOfPoint> filterContoursOutput() {
			return filterContoursOutput;
		}

		private void hsvThreshold(Mat input, double[] hue, double[] sat, double[] val, Mat out) {
			Imgproc.cvtColor(input, out, Imgproc.COLOR_BGR2HSV);
			Core.inRange(out, new Scalar(hue[0], sat[0], val[0]), new Scalar(hue[1], sat[1], val[1]), out);
		}

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
					double[] point = new double[] {contour.get(index, 0)[0], contour.get(index, 0)[1]};
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

	private static class Contour implements Comparable<Contour> {
	    private Direction direction;
	    private Point center;

	    public Contour(MatOfPoint mat) {
			Rect rect = Imgproc.boundingRect(mat);
			this.direction = getDirection(mat);
			this.center = new Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0);
		}

	    public Contour(Direction direction, Point center) {
            this.direction = direction;
            this.center = center;
        }

        @Override
        public int compareTo(Contour o) {
            return Double.compare(this.center.x, o.center.x);
        }
    }

	private static class ContourPair {
	    private Contour left;
	    private Contour right;

	    private boolean isComplete() {
	        return left != null && right != null;
        }

        private double center() {
            return (left.center.x + right.center.x) / 2;
        }

        private double error(double centerX) {
            double heightErr = Math.abs(left.center.y - right.center.y);
            double centerErr = Math.abs(center() - centerX);
            return heightErr + centerErr;
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
		System.out.println("Setting up NetworkTables client for team " + team);
		ntinst.startClientTeam(team);

		NetworkTable table = ntinst.getTable("vision");

		NetworkTableEntry hatchZeroX = table.getEntry("hatchZeroX");
		NetworkTableEntry hatchZeroY = table.getEntry("hatchZeroY");
		NetworkTableEntry hatchOneX = table.getEntry("hatchOneX");
		NetworkTableEntry hatchOneY = table.getEntry("hatchOneY");
		NetworkTableEntry hatchContoursCount = table.getEntry("hatchContoursCount");
		NetworkTableEntry hatchLR = table.getEntry("hatchLR"); // TODO: update

		NetworkTableEntry cargoZeroX = table.getEntry("cargoZeroX");
		NetworkTableEntry cargoZeroY = table.getEntry("cargoZeroY");
		NetworkTableEntry cargoOneX = table.getEntry("cargoOneX");
		NetworkTableEntry cargoOneY = table.getEntry("cargoOneY");
		NetworkTableEntry cargoContoursCount = table.getEntry("cargoContoursCount");
		NetworkTableEntry cargoLR = table.getEntry("cargoLR");

		List<VideoSource> cameras = new ArrayList<>();
		for (CameraConfig cameraConfig : cameraConfigs) {
			cameras.add(startCamera(cameraConfig));
		}

		if (cameras.size() >= 2) { // both cameras exist
			VisionThread visionThreadCargo = new VisionThread(cameras.get(0), new DetectDouble(),
					constructListener(cargoZeroX, cargoZeroY, cargoOneX, cargoOneY, cargoContoursCount, cargoLR, ntinst));
			visionThreadCargo.start();

			VisionThread visionThreadHatch = new VisionThread(cameras.get(1), new DetectDouble(),
					constructListener(hatchZeroX, hatchZeroY, hatchOneX, hatchOneY, hatchContoursCount, hatchLR, ntinst));
			visionThreadHatch.start();

            try {
                visionThreadCargo.join();
				visionThreadHatch.join();
            } catch (InterruptedException ex) {
                visionThreadCargo.interrupt();
                visionThreadHatch.interrupt();
                System.exit(0);
            }
        }
	}

	private static VisionRunner.Listener<DetectDouble> constructListener(
			NetworkTableEntry zeroX, NetworkTableEntry zeroY,
			NetworkTableEntry oneX, NetworkTableEntry oneY,
			NetworkTableEntry contoursCount, NetworkTableEntry leftRight,
			NetworkTableInstance ntinst) {
		return pipeline -> {
			ArrayList<Contour> contours = pipeline.filterContoursOutput().stream()
					.map(Contour::new).sorted().collect(Collectors.toCollection(ArrayList::new));

			String directions = contours.stream().map(c -> c.direction.toString())
					.reduce("", (acc, next) -> acc + next);

			synchronized (imgLock) {
				leftRight.setString(directions);
			}

			if (!contours.isEmpty() && contours.get(0).direction == Direction.RIGHT)
				contours.add(0, new Contour(Direction.LEFT, new Point(-10, 0))); // off screen to left

			if (!contours.isEmpty() && contours.get(contours.size() - 1).direction == Direction.LEFT)
				contours.add(new Contour(Direction.RIGHT, new Point(400, 0))); // off screen to right

			ArrayList<ContourPair> pairs = new ArrayList<>();
			ContourPair currentPair = new ContourPair();
			for (Contour contour : contours) {
				if (contour.direction == Direction.LEFT) {
					currentPair = new ContourPair();
					currentPair.left = contour;
				} else {
					currentPair.right = contour;
					if (currentPair.isComplete()) {
						pairs.add(currentPair);
					}
				}
			}

			double centerCargo = 160; // should always be close enough to center
			pairs.sort(Comparator.comparingDouble(o -> o.error(centerCargo)));

			if (!pairs.isEmpty()) {
				ContourPair pair = pairs.get(0);
				synchronized (imgLock) {
					zeroX.setDouble(pair.left.center.x);
					zeroY.setDouble(pair.left.center.y);
					oneX.setDouble(pair.right.center.x);
					oneY.setDouble(pair.right.center.y);
				}
				//x: (0, 320)
			} else {
				synchronized (imgLock) {
					zeroX.setDouble(Double.MAX_VALUE / 2);
					zeroY.setDouble(Double.MAX_VALUE / 2);
					oneX.setDouble(Double.MAX_VALUE / 2);
					oneY.setDouble(Double.MAX_VALUE / 2);
				}
			}
			synchronized (imgLock) {
				contoursCount.setDouble(pipeline.filterContoursOutput().size());
				if (!ntinst.isConnected()) {
					ntinst.startClientTeam(team);
				}
			}
		};
	}
	
	private static Direction getDirection(MatOfPoint contour) {
		Point left = new Point(Double.MAX_VALUE, 0);
		Point right = new Point(Double.MIN_VALUE, 0);
		for (Point p : contour.toArray()) {
			if (p.x < left.x)
				left = p;
			if (p.x > right.x)
				right = p;
		}
		
		if (left.y > right.y) {
			return Direction.LEFT;
		} else {
			return Direction.RIGHT;
		}
	}
	
	private enum Direction {
        LEFT, RIGHT
	}
}