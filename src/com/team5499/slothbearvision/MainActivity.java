package com.team5499.slothbearvision;

import java.util.Collections;
import java.util.Vector;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

public class MainActivity extends Activity implements CvCameraViewListener {
	
	private static final String TAG = "SlothBearVision::MainActivity";
	private CameraBridgeViewBase mOpenCvCameraView;
	private int mWidth;
	private int mHeight;
	private static double nexus6fov = .9569960048;
	
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this){
		@Override
		public void onManagerConnected(int status){
			switch (status){
			case LoaderCallbackInterface.SUCCESS:
			{	Log.i(TAG, "OPENCV Loaded Successfully");
				mOpenCvCameraView.enableView();
			}break;
			default:
			{
				super.onManagerConnected(status);
			}break;
		}
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		Log.i(TAG, "creating");
		mOpenCvCameraView = (CameraBridgeViewBase) new JavaCameraView(this, CameraBridgeViewBase.CAMERA_ID_BACK);
		setContentView(mOpenCvCameraView);
		mOpenCvCameraView.setCvCameraViewListener(this);
		mOpenCvCameraView.enableFpsMeter();
	//	mOpenCvCameraView.setMaxFrameSize(480, 320);
	}

	@Override
	public void onCameraViewStarted(int width, int height) {
		mWidth  = width;
		mHeight = height;
	}

	@Override
	public void onCameraViewStopped() {
		// TODO Auto-generated method stub
		
	}
	
	private MatOfPoint hullToPoints(MatOfInt hull, MatOfPoint contour){
		Vector<Point> pts = new Vector<Point>();
		for(int i=0; i<hull.total();i++){
			pts.add(contour.toList().get((int) hull.get(i,0)[0]));
		}
		
		MatOfPoint matOfPts = new MatOfPoint();
		matOfPts.fromList(pts);
		return matOfPts;
	}
	
	private double getHullHeight(MatOfPoint hull){
		Vector<Double> hullHeights = new Vector<Double>();
		for (int i=0; i<hull.total(); i++){
			hullHeights.add(hull.get(i,0)[1]);
		}
		double max = Collections.max(hullHeights);
		double min = Collections.min(hullHeights);
		return Math.abs(max - min);
	}
	private double getAngleOffset(Point centroid, double imgWidth, double camerafov){
		double centroidx = centroid.x - (imgWidth / 2);
		double centroidprop = centroidx / imgWidth;
		return Math.atan(centroidprop * Math.tan(camerafov/2)) * 180.0 / Math.PI;
	}
	private double findDist(double objHft, double objHPix, double imgWPix, double camerafov){
		double w = 0.5 * (objHft / objHPix) * imgWPix;
		return w / Math.tan(camerafov / 2);
	}

	@Override
	public Mat onCameraFrame(Mat inputFrame) {
		// TODO Auto-generated method stub
		Mat hsv = new Mat();
		Imgproc.cvtColor(inputFrame, hsv, Imgproc.COLOR_BGR2HSV );
		Vector<Mat> hsvchans = new Vector<Mat>();
		Core.split(hsv, hsvchans);
		Mat hue = hsvchans.get(2);
		Mat thresh = new Mat();
		Imgproc.threshold(hue, thresh, 240, 255, Imgproc.THRESH_BINARY_INV);
		Vector<MatOfPoint> contours = new Vector<MatOfPoint>();
		Mat hierarchy = new Mat();
		Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);
		
		Vector<MatOfPoint> polygons = new Vector<MatOfPoint>();
		Vector<MatOfPoint> hulls = new Vector<MatOfPoint>();
		Vector<Point> centroids = new Vector<Point>();
		Vector<Double> areas = new Vector<Double>();
		
		for(int i=0; i<contours.size();i++){
		//	Log.i(TAG, ((Integer)contours.size()).toString());
			double area = Imgproc.contourArea(contours.get(i));
			areas.add(area);
			if (area >= 1000){
				MatOfPoint2f polygon2f = new MatOfPoint2f();
				MatOfPoint2f contour2f = new MatOfPoint2f();
				contours.get(i).convertTo(contour2f, CvType.CV_32F);
				Imgproc.approxPolyDP(contour2f, polygon2f, 1, true);
				MatOfPoint polygon = new MatOfPoint();
				polygon2f.convertTo(polygon, CvType.CV_32S);
				polygons.add(polygon);
				MatOfInt hull = new MatOfInt();
				Imgproc.convexHull(contours.get(i), hull);
			//	Log.i(TAG, (contours.get(i).toList()).toString());
				MatOfPoint hullpoints = hullToPoints(hull, contours.get(i));
				hulls.add(hullpoints);
				Moments moments = Imgproc.moments(hullpoints);
				Point centroid = new Point(moments.get_m10() / moments.get_m00(), moments.get_m01() / moments.get_m00());
				centroids.add(centroid);
			}
		}
		for(int j=0; j<polygons.size();j++){
			//	Log.i(TAG, polygons.get(j).toString());
			double arearatio = areas.get(j) / Imgproc.contourArea(hulls.get(j));
			if(Math.abs(arearatio - .3333333) <= .2){
				Imgproc.drawContours(thresh, polygons, j, new Scalar(127), 50);
				Core.circle(thresh, centroids.get(j), 20, new Scalar(127), 10);
				Log.i(TAG, "Contour drawn");
//				Log.i(TAG, "hulls col" + (hulls.get(j).col(0).get(0, 0)[1]));
				double h = getHullHeight(hulls.get(j));
				double d = findDist(1, h, inputFrame.size().height, nexus6fov);
				double theta = getAngleOffset(centroids.get(j), inputFrame.size().height, nexus6fov);
				Log.e(TAG, "height" + h);
				Log.e(TAG, "dist" + d);
				Log.e(TAG, "theta" + theta);				
			}	
		}
		
		return thresh;
	}
	@Override
	public void onResume(){
		super.onResume();
		Log.i(TAG, "Resuming");
		boolean success = OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8, this, mLoaderCallback);
		Log.i(TAG, ((Boolean)success).toString());
	}
}
