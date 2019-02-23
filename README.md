# Patriot Vision

### Patriot Vision, FRC 4131 The Iron Patriot's vision solution for the 2019 FRC Game, DEEP SPACE

This is a multi-camera streaming and OpenCV pipeline based on and for use with the FRC Raspberry Pi image, for FRC Team 4131's 2019 season.

This program runs on the WPILib Rapsberry Pi image. It publishes camera streams to the CameraServer based on the web dashboard configs. It also puts the images through a GRIP generated OpenCV pipeline, which identifies retro-reflective vision targets. The targets are paired up based on left and right orientation, so the two pieces of tape around each objective are paired up. If an odd number of targets are found, one additional 'phantom' target is generated offscreen based off of the orientation of the unmatched target. If multiple pairs are found, the one closest to the center is selected and bounding boxes are drawn around them. The values of the centers of the bounding boxes around those targets are sent to the RoboRio via NetworkTables. The RoboRio calculates the offset, and using its mecanum base strafes to align itself automatically. It can also drive itself forward into the target automatically based on the distance between the targets.
  
This system not only allows us to view camera streams during the sandstorm and teleop periods, but it also can, at the push of a button, laterally align itself with a hatch or cargo goal, and drive forward into scoring position, competely automated.
