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

import edu.wpi.first.*;
import edu.wpi.cscore.*;

import org.opencv.core.Mat;

public class Main {
		
		UsbCamera camera0 = new UsbCamera("camera0", 0);
		UsbCamera camera1 = new UsbCamera("camera1", 1);
		
		int camera;
		
		public static void main(String[] args) {
			camera0.setVideoMode(new VideoMode(VideoMode.PixelFormat.kMJPEG, 240, 200, 20));
			camera1.setVideoMode(new VideoMode(VideoMode.PixelFormat.kMJPEG, 240, 200, 20));
			
			SmartDashboard.putNumber("camera", 0);
			
			while (true) {
				run();
			}
		}
		
		public static void run() {
			camera = SmartDashboard.getNumber("camera", 0);
			switch (camera) {
				case 0:
					break;
				case 1:
					break;
				default:
					System.out.println("Invalid camera number!");
					return;
			}
			
		}
}