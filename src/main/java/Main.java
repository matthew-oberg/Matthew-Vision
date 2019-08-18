/*----------------------------------------------------------------------------*/
/* Copyright (c) 2019 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import com.google.gson.Gson;
import edu.wpi.cscore.VideoSource;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.VisionRunner;
import edu.wpi.first.vision.VisionThread;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static camera.CameraUtils.*;

public final class Main {

	private static final Object imgLock = new Object();
	private static final Gson gson = new Gson();

	private static final ContourPair defaultPair = new ContourPair();

	private enum Direction {
		LEFT, RIGHT
	}

	private static class VisionDescription {
		public ContourPair contourPair;
		public int contourCount;
		public String leftRight;
	}

	private static class Contour implements Comparable<Contour> {
	    private Direction direction;
	    private Point center;

	    public Contour(MatOfPoint mat) {
			Rect rect = Imgproc.boundingRect(mat);
			this.direction = getDirection(mat);
			this.center = new Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0);
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

//	    public ContourPair() {
//	        left = new Contour(Direction.LEFT, new Point());
//	        right = new Contour(Direction.RIGHT, new Point());
//        }

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

	private static VisionRunner.Listener<DetectDouble> constructListener(
			NetworkTableEntry description, NetworkTableInstance ntinst) {
		return pipeline -> {
			ArrayList<Contour> contours = pipeline.filterContoursOutput().stream()
					.map(Contour::new).sorted().collect(Collectors.toCollection(ArrayList::new));

			String directions = contours.stream().map(c -> c.direction.toString())
					.reduce("", (acc, next) -> acc + next);

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

			VisionDescription desc = new VisionDescription();
			desc.contourCount = pipeline.filterContoursOutput().size();
			desc.leftRight = directions;
			desc.contourPair = pairs.isEmpty() ? new ContourPair() : pairs.get(0);

			String json = gson.toJson(desc);

			synchronized (imgLock) {
				description.setString(json);
				if (!ntinst.isConnected()) {
					ntinst.startClientTeam(team);
				}
			}
		};
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

		NetworkTableEntry hatchDescription = table.getEntry("hatchDescription");
		NetworkTableEntry ballDescription = table.getEntry("ballDescription");

		List<VideoSource> cameras = new ArrayList<>();
		for (CameraConfig cameraConfig : cameraConfigs) {
			cameras.add(startCamera(cameraConfig));
		}

		if (cameras.size() >= 2) { // both cameras exist
			VisionThread visionThreadCargo = new VisionThread(cameras.get(0), new DetectDouble(),
					constructListener(hatchDescription, ntinst));
			visionThreadCargo.start();

			VisionThread visionThreadHatch = new VisionThread(cameras.get(1), new DetectDouble(),
					constructListener(ballDescription, ntinst));
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


}