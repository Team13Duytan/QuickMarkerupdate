package chamdiemthi.datn.opencv_camera_1.scan;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.rantea.opencv_camera_1.R;

import chamdiemthi.datn.opencv_camera_1.app.Utils;
import chamdiemthi.datn.opencv_camera_1.models.BaiThi;
import chamdiemthi.datn.opencv_camera_1.models.DiemThi;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener {
    private static final String TAG = "CameraActivity";

    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(CameraActivity.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public CameraActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    BaiThi baiThi;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.tutorial1_surface_view);

        //
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.activity_camera);

        //dặt camera hiển thị
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        //
        mOpenCvCameraView.setCvCameraViewListener(this);
        this.imageProcessing = new ImageProcessing();

        //lấy vị trí của bài thi trong intent gửi đến
        int i = getIntent().getIntExtra(Utils.ARG_P_BAI_THI, 0);
        baiThi = Utils.dsBaiThi.get(i);
        Toast.makeText(this, "TOUCH TO START MARKING", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    private static int halfRect = 1000;
    //    public static float ratio;
    //    Mat clone;
    Mat[] corners;
    Mat[] corners1;
    int count = 0;
    Mat hierarchy;
    public ImageProcessing imageProcessing;
    //định dạng màu RGA
    Mat mRga;
    //định dạng màu RGA (bản sao)
    Mat mRga1;
    //kích thước hiển thị
    int myHeight;
    int myWidth;

    //hình chữ nhật
    Rect[] rects;
    //điểm bắt đầu
    int startX = 0;
    int startY = 0;

    //kích thước thử nghiệm (giấy thi, pixel)
    //kích thước cắt bởi 4 góc dấu chấm đen
    Template template;

    //khởi tạo khi bắt đầu chạy ứng dụng
    @Override
    public void onCameraViewStarted(int width, int height) {
        this.myWidth = width;
        this.myHeight = (this.myWidth * 9) / 16;
        this.startX = (width - this.myWidth) / 2; // add
        this.startY = (height - this.myHeight) / 2;
        this.mRga1 = new Mat(height, width, CvType.CV_8UC4);
        this.mRga = new Mat(this.myHeight, this.myWidth, CvType.CV_8UC4);
        int heightCal = this.myHeight / 4;
        int widthCal = (this.myHeight * 9) / 8;
        this.hierarchy = new Mat();
        this.corners = new Mat[4];
        this.corners1 = new Mat[4];
        this.rects = new Rect[4];
        this.rects[0] = new Rect(0, 0, heightCal, heightCal);
        this.rects[1] = new Rect(widthCal, 0, heightCal, heightCal);
        this.rects[2] = new Rect(0, this.myHeight - heightCal, heightCal, heightCal);
        this.rects[3] = new Rect(widthCal, this.myHeight - heightCal, heightCal, heightCal);
        this.btFPoint = new ArrayList<>();
        //tạo template quét 20 câu
        template = Template.createTemplate20();
    }

    @Override
    public void onCameraViewStopped() {
        //khi camera dừng, giải phòng các tấm nền
        this.mRga.release();
        this.mRga1.release();
        this.hierarchy.release();
    }

    //danh sách các điểm quét tìm thấy (tìm 4 điểm khung hình vuông)
    ArrayList<Point> btFPoint;

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        //lấy tấm nền từ camera
        this.mRga1 = inputFrame.rgba();
        //sao chép tấm nền
        this.mRga = this.mRga1.submat(this.startY, this.startY + this.myHeight, 0, this.myWidth);
        //tỉ lệ khung hình
        float rate = ((float) this.myWidth) / 1280.0f;
        //tìm kiếm 4 ô vuông với tỷ lệ
        getFourSquare(rate);
        //biến đếm số ô vuông tìm thấy
        this.count = 0;
        int[] check = new int[4];
        //khu vực quét
        int k = 0;
        btFPoint.clear();
        //bắt đầu quét. vòng while (quét tại 4 khu vực, nếu 4 khu vực tìm thấy 4 điểm => ok)
        while (k < 4) {
            //dan sách đường viền tìm thấy
            ArrayList<MatOfPoint> contours = new ArrayList();
            //xử lý đường viền hình ảnh
            Imgproc.findContours(this.corners1[k], contours, this.hierarchy, 1, 2, new Point(0.0d, 0.0d));
            int i = 0;
            //kiểm tra từng đường viền (giống như vòng for i++)
            while (i < contours.size()) {
                // tìm kiếm tọa độ 4 ô vuông
                getCountContour(contours, i, k, rate, check);
                if (this.count == 4) { //nếu tìm thấy cả 4 ô vuông tại 4 góc giấy
                    //xóa ds đường viền
                    contours.clear();
                    //dừng quét (nhảy sang vùng quét 5 => ko tồn tại)
                    k++;
                    //tọa độ 4 ô vuông
                    Point p0 = btFPoint.get(0);
                    Point p1 = btFPoint.get(1);
                    Point p2 = btFPoint.get(2);
                    Point p3 = btFPoint.get(3);

                    //vẽ khung hình vuông bằng cách nối các điểm
                    Imgproc.line(mRga1, p0, p1, new Scalar(255, 128, 128, 255), 3);
                    Imgproc.line(mRga1, p1, p3, new Scalar(255, 128, 128, 255), 3);
                    Imgproc.line(mRga1, p3, p2, new Scalar(255, 128, 128, 255), 3);
                    Imgproc.line(mRga1, p2, p0, new Scalar(255, 128, 128, 255), 3);

                    //tính toán chiều dài, rộng của khung vuông
                    double w = khoangCach(p0, p1), h = khoangCach(p0, p2);
                    //nếu người dùng chạm màn hình thì bắt đầu quét để lưu bài thi
                    if (touch) {
                        //reset trạng thái chạm
                        touch = false;
                        //lấy bài làm quét trong giấy thi
                        ArrayList<String> baiLam = template.scanBaiLam(w, h, p0, mRga);
                        //lấy mã đề quét trong giấy thi
                        String maDe = template.scanMaDe(w, h, p0, mRga1);
                        //lấy số báo danh quét trong giấy thi
                        String sbd = template.scanSBD(w, h, p0, mRga1);
                        //chuyển bài thi sang dạng hình ảnh bitmap để lưu trữ
                        Bitmap save = matToBitmap(mRga1);
                        //Tạo đối tượng điểm thi (để lưu trữ)
                        DiemThi diemThi = new DiemThi(sbd, baiThi.maBaiThi, maDe, save, baiLam.toArray(new String[baiLam.size()]));
                        //Cập nhật thông tin bài thi này trong bảng điểm thi
                        Utils.update(diemThi);
                    } else
                        //nếu không chạm thì quét và hiển thị bình thường
                        template.scan(w, h, p0, mRga1);
                } else {
                    contours.remove(i);
                    i++;
                }
            }
            contours.clear();
            k++;
        }
        for (k = 0; k < 4; k++) {
            check[k] = 0;
        }
        return this.mRga1;
    }

    public double khoangCach(Point p, Point p2) {
        return Math.sqrt(Math.pow(p.x - p2.x, 2) + Math.pow(p.y - p2.y, 2));
    }

    public void getFourSquare(float rate) {
        for (int i = 0; i < 4; i++) {
            this.corners[i] = this.mRga.submat(this.rects[i]);
            this.corners1[i] = this.corners[i].clone();
            this.corners[i].convertTo(this.corners1[i], -1, 1.0d, 100.0d);
            Imgproc.cvtColor(this.corners1[i], this.corners1[i], 6);
            Imgproc.GaussianBlur(this.corners1[i], this.corners1[i], new Size(3.0d, 3.0d), 2.0d);
            Imgproc.adaptiveThreshold(this.corners1[i], this.corners1[i], 255.0d, 0, 0, 31, (double) (5.0f * rate));
        }
    }
    // phát hiện ra viền của bài thi dựa trên 4 điểm đen
    // sử dụng MatOfPoint của openCV
    public void getCountContour(ArrayList<MatOfPoint> contours, int i, int k, float rate, int[] check) {
        Rect rect = Imgproc.boundingRect((MatOfPoint) contours.get(i));
        int area = (int) Imgproc.contourArea((Mat) contours.get(i));
        int w = rect.width;
        int h = rect.height;
        float ratio1 = ((float) w) / ((float) h);
        double r1 = ((double) (area - Core.countNonZero(this.corners1[k].submat(rect)))) / ((double) area);
        if (r1 > this.imageProcessing.getTH(rate) && ratio1 > 0.8f && ratio1 < 1.2f) {
            Rect rect2 = new Rect(this.rects[k].x + rect.x, this.rects[k].y + rect.y, rect.width, rect.height);
            if (check[k] == 0) {
                this.count++;
                Mat mat = this.mRga;
//                Point point = new Point((double) (this.rects[k].x + rect.x), (double) (this.rects[k].y + rect.y));
                Point point = new Point((double) ((rect.width + rect.x) + this.rects[k].x), (double) ((rect.height + rect.y) + this.rects[k].y));
                btFPoint.add(point);//add
                Imgproc.rectangle(mat, point, point, new Scalar(255.0d, 0.0d, 0.0d), 5);
//                this.points.add(this.imageProcessing.getPoint(rect2));
                if (Math.min(rect2.width, rect2.height) < halfRect) {
                    halfRect = Math.min(rect2.width, rect2.height) / 2;
                }
                check[k] = 1;
            }
        }
    }

    public Bitmap matToBitmap(Mat mat) {
        Bitmap bmp = null;
        Mat tmp = mat.clone();
        try {
            bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888);
            org.opencv.android.Utils.matToBitmap(tmp, bmp);
        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
        }
        org.opencv.android.Utils.matToBitmap(mat, bmp);

        return bmp;
    }



    int minH = 255, maxH = 0, minS = 255, maxS = 0, minV = 255, maxV = 0;
    boolean touch;

    //độ sáng tại điểm đen (tối đa 173)
    //độ sáng tại điểm đen (tối thiểu 173)
    //test dark
    //1 min: x,12,145
    //1 max: x,35,168
    //2 min: x,4,75
    //2 max: x,41,173
    //tối đa 41, 173

    //test light
    //1 min: x,48,253
    //1 max: x,52,255
    //2 min: x,2,181
    //2 max: x,58,255
    //tối thiểu: 2,181



    @Override
    public boolean onTouch(View v, MotionEvent event) {
        touch = true;
        return false;
    }
}
