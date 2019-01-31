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
import edu.wpi.first.vision.VisionPipeline;
import edu.wpi.first.vision.VisionThread;

import org.opencv.core.Mat;



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
			VisionThread visionThread = new VisionThread(cameras.get(0), new MyPipeline(), pipeline -> {});	
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