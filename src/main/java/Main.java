import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

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
import edu.wpi.first.vision.VisionPipeline;
import edu.wpi.first.vision.VisionThread;

import org.opencv.core.Mat;
import org.opencv.core.*;
import org.opencv.core.Core.*;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.*;
import org.opencv.objdetect.*;

public final class Main {
	private static String configFile = "/boot/frc.json";

	@SuppressWarnings("MemberName")
	public static class CameraConfig {
		public String name;
		public String path;
		public JsonObject config;
		public JsonElement streamConfig;
	}

	public static int team;
	public static boolean server;
	public static List<CameraConfig> cameraConfigs = new ArrayList<>();
	
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
	
	public static class MyPipeline implements VisionPipeline {
		public int val;

		@Override
		public void process(Mat mat) {
		val += 1;
		}
	}
	
	public static class DetectDouble implements VisionPipeline {

		//Outputs
		private Mat hsvThresholdOutput = new Mat();
		private ArrayList<MatOfPoint> findContoursOutput = new ArrayList<MatOfPoint>();

		static {
			System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		}

		@Override
		public void process(Mat source0) {
			Mat hsvThresholdInput = source0;
			double[] hsvThresholdHue = {74.46043165467626, 120.1023890784983};
			double[] hsvThresholdSaturation = {32.10431654676259, 76.58703071672356};
			double[] hsvThresholdValue = {210.97122302158272, 255.0};
			hsvThreshold(hsvThresholdInput, hsvThresholdHue, hsvThresholdSaturation, hsvThresholdValue, hsvThresholdOutput);

			Mat findContoursInput = hsvThresholdOutput;
			boolean findContoursExternalOnly = false;
			findContours(findContoursInput, findContoursExternalOnly, findContoursOutput);

		}

		public Mat hsvThresholdOutput() {
			return hsvThresholdOutput;
		}

		public ArrayList<MatOfPoint> findContoursOutput() {
			return findContoursOutput;
		}

		private void hsvThreshold(Mat input, double[] hue, double[] sat, double[] val,
			Mat out) {
			Imgproc.cvtColor(input, out, Imgproc.COLOR_BGR2HSV);
			Core.inRange(out, new Scalar(hue[0], sat[0], val[0]),
			new Scalar(hue[1], sat[1], val[1]), out);
		}

		private void findContours(Mat input, boolean externalOnly,
			List<MatOfPoint> contours) {
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

		List<VideoSource> cameras = new ArrayList<>();
		for (CameraConfig cameraConfig : cameraConfigs) {
			cameras.add(startCamera(cameraConfig));
		}

		if (cameras.size() >= 1) {
			VisionThread visionThread = new VisionThread(cameras.get(0), new DetectDouble(), pipeline -> {});	
			visionThread.start();
		}

		for (;;) {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ex) {
				return;
			}
		}
	}
}