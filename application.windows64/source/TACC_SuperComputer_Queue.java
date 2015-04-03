import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import peasy.*; 
import controlP5.*; 
import java.util.*; 
import java.text.*; 
import TUIO.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class TACC_SuperComputer_Queue extends PApplet {

// used for vis interaction (pan, zoom, rotate)

PeasyCam cam;
PMatrix3D baseMat; // used for peasycam + HUD + lights fix 

// used for HUDs

ControlP5 cp5;
ControlGroup descriptionBox; Textlabel descriptionText;
ControlGroup usageBox; Textlabel usageText;
ControlGroup jobBox; Textlabel jobText;
ControlGroup titleBox; Textlabel titleText;

// used for tuio rotation support
ArcBall arcball; 

// separate jobs array into three arrays depending on slot count
ArrayList<Job> smallJobs = new ArrayList<Job>();
ArrayList<Job> mediumJobs = new ArrayList<Job>();
ArrayList<Job> largeJobs = new ArrayList<Job>();
ArrayList<Job> allJobs = new ArrayList<Job>();

// split jobs into three helixes depending on slot count
Helix smallJobsHelix, mediumJobsHelix, largeJobsHelix, allJobsHelix;
int helixType = 1; // variable used to determine which helix to draw
float rotz = 0;

// each variable will keep track of which job to highlight in each helix
int highlighter1 = 0; // used with smallJobsHelix
int highlighter2 = 0; // used with mediumJobsHelix
int highlighter3 = 0; // used with largeJobsHelix
int highlighter4 = 0; // used with allJobsHelix

int smallJobsUpperBound = 100;
int mediumJobsUpperBound = 500;
int largeJobsUpperBound = 16385;

// use pshape sphere to highlight jobs in draw w/o affecting performance
PShape wireSphere; 
                           
private String description = "VISUALIZATION DESCRIPTION\n\n" +
                             "1. A job is a series of programs that run on Stampede\n" +
                             "2. Each job is represented by a group of same-colored spheres\n" +
                             "3. Each sphere represents 16 CPU cores\n" +
                             "4. Each cylinder represents allocated time\n" +
                             "5. Color along cylinder represents elapsed used\n";
                             
private String usage = "MULTITOUCH INTERACTION (if applicable)\n\n" +
                       "1 finger   - camera rotation\n" +
                       "2 fingers - pinch zoom in/out\n" +
                       "3 fingers - pan\n";                          
         
private String title = ""; 
private String jobInfo;

boolean FULLSCREEN = true;
boolean USE_TUIO = true;

public void setup() {
  if(FULLSCREEN) size(displayWidth, displayHeight, OPENGL); // run from "Sketch -> Present"
  else size(1100,700,OPENGL);
  baseMat = g.getMatrix(baseMat);
  cp5 = new ControlP5(this);
  cp5.setAutoDraw(false);
  
  if(USE_TUIO) {
    arcball = new ArcBall(width/2, height/2, min(width - 20, height - 20) * 0.8f);
    initTUIO(width, height);
  } else {
    // peasycam setup 
    cam = new PeasyCam(this, 0, 0, 0, 2000);
    cam.setResetOnDoubleClick(false);
  }
  
  // used to highlight selected job
  wireSphere = createShape(SPHERE,1); 
  wireSphere.setFill(false);
  wireSphere.setStroke(color(255,150));
  
  // separate method that can be re-called to restart sketch
  initSketch();
  
  if(smallJobs.size()==0 && mediumJobs.size()==0 && largeJobs.size()==0) {
    println("\nJSON file has no running jobs. Nothing to draw. Exiting sketch.");
    noLoop();
    exit();
  } else {
    createHUDs();
    noCursor();
  }
}

public void initSketch() {
  parseFile();
  
  // viewing the entire queue at once is cool but not as useful.
  // breaking the queue into three smaller helixes should make it easier
  // for the user to search the visualization for specific jobs
  println("smallJobs size = " + smallJobs.size());
  println("mediumJobs size = " + mediumJobs.size());
  println("largeJobs size = " + largeJobs.size());
  println("allJobs size = " + allJobs.size());
  
  smallJobsHelix = new Helix(smallJobs, getMaxSlotsPosition(smallJobs)); smallJobsHelix.createHelix();  
  mediumJobsHelix = new Helix(mediumJobs, getMaxSlotsPosition(mediumJobs)); mediumJobsHelix.createHelix();
  largeJobsHelix = new Helix(largeJobs, getMaxSlotsPosition(largeJobs)); largeJobsHelix.createHelix();
  allJobsHelix = new Helix(allJobs, getMaxSlotsPosition(allJobs)); allJobsHelix.createHelix();  
} 

public void draw() {
  if (minute() == 0 && second() == 30) {
    println("\nRestarting Sketch");
    smallJobs.clear(); 
    smallJobsHelix = null; 
    
    mediumJobs.clear(); 
    mediumJobsHelix = null; 
    
    largeJobs.clear(); 
    largeJobsHelix = null;
    
    allJobs.clear(); 
    allJobsHelix = null;
    
    initSketch();
  } else {
    background(0);
    smooth(8);
  
    // save peasycam matrix and reset original
    pushMatrix();
    g.setMatrix(baseMat);
    ambientLight(40, 40, 40);
    directionalLight(255, 255, 255, -150, 40, -140);
    popMatrix();
    
    if(USE_TUIO) {
      translate(posX, posY);
      scale(zoomScaler);
      arcball.update();
    }
       
    rotateZ(rotz);
    switch(helixType) {
      case 1: 
        smallJobsHelix.displayHelix();
        highlightJobNodes(highlighter1, smallJobs, smallJobsHelix);
        updateHUD(smallJobsHelix, smallJobs, highlighter1, "small jobs (<"+ smallJobsUpperBound +" cores)");
        break;
      case 2: 
        mediumJobsHelix.displayHelix();
        highlightJobNodes(highlighter2, mediumJobs, mediumJobsHelix);
        updateHUD(mediumJobsHelix, mediumJobs, highlighter2, "medium jobs (" + smallJobsUpperBound + "-" + (mediumJobsUpperBound-1) + " cores)");
        break;
      case 3: 
        largeJobsHelix.displayHelix();
        highlightJobNodes(highlighter3, largeJobs, largeJobsHelix);
        updateHUD(largeJobsHelix, largeJobs, highlighter3, "large jobs (>" + (mediumJobsUpperBound-1) + " cores)");
        break;
      case 4: 
        allJobsHelix.displayHelix();
        highlightJobNodes(highlighter4, allJobs, allJobsHelix);
        updateHUD(allJobsHelix, allJobs, highlighter4, "all jobs");
        break;
    }  
    rotz += .0009f;
    huds();
  } 
} 

public void parseFile() {
  // Load an JSON 
  JSONArray json = loadJSONArray("queue.json");

  for (int i=0; i < json.size(); i++ ) {    
    JSONObject job = json.getJSONObject(i); 
    println(job);
    println();
    JSONArray state = job.getJSONArray("State");
    String ipf = splitTokens(state.toString().replaceAll("\\[", "").replaceAll("\\]","").replace("\"","").replaceAll("\\n",""), ", ")[0];
    String ipfStatus = splitTokens(ipf, ":")[1];
    
    if(ipfStatus.equals("running")){ // only process running jobs
      int num = job.getInt("LocalIDFromManager");
      String name = job.getString("Name", "");
      String owner = job.getString("LocalOwner");
      String startTime = job.getString("StartTime").replaceFirst(".$",""); // make sure to remove trailing 'Z' from startTime 
      String queue = job.getString("Queue");
      int slotNum = job.getInt("RequestedSlots");
  
      // create job in appropriate list depending on slot count
      if(slotNum < smallJobsUpperBound) smallJobs.add(new Job(num, name, owner, startTime, queue, slotNum));
      else if(slotNum > (smallJobsUpperBound-1) && slotNum < mediumJobsUpperBound) mediumJobs.add(new Job(num, name, owner, startTime, queue, slotNum));
      else largeJobs.add(new Job(num, name, owner, startTime, queue, slotNum));
      
      // add to allJobs array list
      allJobs.add(new Job(num, name, owner, startTime, queue, slotNum));
    } 
  }
}

public int getMaxSlotsPosition(ArrayList<Job> jobs) {
  if (jobs.size() == 0) return -1;
  else {
    int maxSlots = jobs.get(0).getSlots();
    int maxPos = 0;
    for (int i=1; i<jobs.size(); i++) {
      if (jobs.get(i).getSlots() > maxSlots){
        maxSlots = jobs.get(i).getSlots();
        maxPos = i;
      }
    }
    return maxPos;
  }
}

public int getMinSlotsPosition(ArrayList<Job> jobs) {
  if (jobs.size() == 0) return -1;
  else {
    int minSlots = jobs.get(0).getSlots();
    int minPos = 0;
    for (int i=1; i<jobs.size(); i++) {
      if (jobs.get(i).getSlots() < minSlots){
        minSlots = jobs.get(i).getSlots();
        minPos = i;
      }
    }
    return minPos;
  }
}

public int selectedJob(ArrayList<Job> jobs, Helix helix) {
  for (int i=0; i<jobs.size(); i++) { 
    float tolerance = 10;
    float sphereRadius = jobs.get(i).getSphereRadius(); 
    float x = jobs.get(i).getX();
    float y = jobs.get(i).getY();
    float z = jobs.get(i).getZ();
    float theta = jobs.get(i).getTheta();
    
    for(int j=0; j<jobs.get(i).getNodeCount(); j++){
      float sphereCenterX = screenX(x,y,z);
      if(mouseX <= (sphereCenterX+tolerance) && mouseX >= (sphereCenterX-tolerance)) {
        float sphereCenterY = screenY(x,y,z);
        if(mouseY <= (sphereCenterY+tolerance) && mouseY >= (sphereCenterY-tolerance)) {
          return i;
        }
      }
    
      // move to next sphere in job
      theta += asin((sphereRadius*2)/helix.getHelixRadius());
      x = helix.getHelixRadius() * cos(theta);
      y = helix.getHelixRadius() * sin(theta);
      z += helix.getDeltaZ(); 
    }
  }
  
  return -1;  
} 

public void highlightJobNodes(int index, ArrayList<Job> jobs, Helix helix){
  float x,y;
  float z = jobs.get(index).getZ();
  float theta = jobs.get(index).getTheta();
  for(int i=0; i<jobs.get(index).getNodeCount(); i++){
    x = helix.getHelixRadius()*cos(theta);
    y = helix.getHelixRadius()*sin(theta);
    z += helix.getDeltaZ();
          
    pushMatrix();
    translate(x,y,z);
    scale(jobs.get(index).getSphereRadius()*1.1f);
    shape(wireSphere);
    popMatrix();
      
    theta += asin((jobs.get(index).getSphereRadius()*2)/helix.getHelixRadius());
  } 
}

public void createHUDs(){
  // change the default font to Verdana
  PFont p = createFont("Times-Roman",40);
  cp5.setControlFont(p);
  
  jobInfo = "Job #" + (highlighter1+1) + "\n\n" +
            "Job Number:         " + smallJobs.get(highlighter1).getJobNum() + "\n" +
            "Job Name:             " + smallJobs.get(highlighter1).getJobName() + "\n" +
            "TACC User:           " + smallJobs.get(highlighter1).getJobOwner() + "\n" +
            "Requested Time:   " + smallJobs.get(highlighter1).getAllocatedTime() + "\n" +
            "Elapsed Time:        " + smallJobs.get(highlighter1).getElapsedTime() + "\n" +
            "Requested CPUs:  " + smallJobs.get(highlighter1).getSlots() + "\n";
  
  // Visualization Description
  descriptionBox = cp5.addGroup("descriptionBox", 20, 20, 1220);
  descriptionBox.setBackgroundHeight(380);
  descriptionBox.setBackgroundColor(color(0,175));
  descriptionBox.hideBar();
  
  descriptionText = cp5.addTextlabel("descriptionBoxLabel", description, 20, 20);
  descriptionText.moveTo(descriptionBox);
  
  // Visualization Interaction
  usageBox = cp5.addGroup("usageBox", 20, 700, 830);
  usageBox.setBackgroundHeight(260);
  usageBox.setBackgroundColor(color(0,175));
  usageBox.hideBar();
  
  usageText = cp5.addTextlabel("usageBoxLabel", usage, 20, 20);
  usageText.moveTo(usageBox);
  
  // Job Information
  jobBox = cp5.addGroup("jobBox", 20, 1280, 1100);
  jobBox.setBackgroundHeight(380);
  jobBox.setBackgroundColor(color(0,175));
  jobBox.hideBar();
  
  jobText = cp5.addTextlabel("jobBoxLabel", jobInfo, 20, 20);
  jobText.moveTo(jobBox);
  
  // Title Information
  titleBox = cp5.addGroup("titleBox", width/2-460, 20, 920);
  titleBox.setBackgroundHeight(210);
  titleBox.setBackgroundColor(color(0,175));
  titleBox.hideBar();
  
  titleText = cp5.addTextlabel("titleTextLabel", title, 20, 20);
  titleText.moveTo(titleBox);  
}

// update HUD with highlighted job's info
public void updateHUD(Helix helix, ArrayList<Job> jobs, int jobIndex, String helixDescription){  
  jobInfo = "Job #" + (jobIndex+1) + "\n\n" +
            "Job Number:         " + jobs.get(jobIndex).getJobNum() + "\n" +
            "Job Name:             " + jobs.get(jobIndex).getJobName() + "\n" +
            "TACC User:           " + jobs.get(jobIndex).getJobOwner() + "\n" +
            "Requested Time:   " + jobs.get(jobIndex).getAllocatedTime() + "\n" +
            "Elapsed Time:        " + jobs.get(jobIndex).getElapsedTime() + "\n" +
            "Requested CPUs:  " + jobs.get(jobIndex).getSlots() + "\n";

  jobText.setValue(jobInfo);
  
  title = "TACC STAMPEDE SUPERCOMPUTER QUEUE\n\n" + 
          helixDescription + "\n" +
          "job count = " + helix.getRunningJobCount();
              
  titleText.setValue(title);
}

public void huds(){
  hint(DISABLE_DEPTH_TEST);
  if(USE_TUIO) {
    pushMatrix();
    resetMatrix();
    applyMatrix(baseMat); 
    cp5.draw();
    popMatrix();
  } else {
    cam.beginHUD();
    cp5.draw();
    cam.endHUD();
  }
  hint(ENABLE_DEPTH_TEST); 
}

public void keyPressed() {
  if (key == CODED){
    if (keyCode == UP) {  
      if(helixType == 1 && mediumJobs.size() > 0) helixType = 2;
      else if(helixType == 2 && largeJobs.size() > 0) helixType = 3;
      else if(helixType == 3 && allJobs.size() > 0) helixType = 4;
      else if(helixType == 4 && smallJobs.size() > 0) helixType = 1;
    } else if (keyCode == DOWN) {
      if(helixType == 4 && largeJobs.size() > 0) helixType = 3;
      else if(helixType == 3 && mediumJobs.size() > 0) helixType = 2;
      else if(helixType == 2 && smallJobs.size() > 0) helixType = 1;
      else if(helixType == 1 && allJobs.size() > 0) helixType = 4;  
    }  
  } 
}

// SELECTION BY MOUSE
public void mousePressed() {
  int pickedJob;
  switch(helixType) {
    case 1: 
      pickedJob = selectedJob(smallJobs, smallJobsHelix);
      highlighter1 = pickedJob < 0 ? highlighter1 : pickedJob;
      break;
    case 2: 
      pickedJob = selectedJob(mediumJobs, mediumJobsHelix);
      highlighter2 = pickedJob < 0 ? highlighter2 : pickedJob;
      break;
    case 3: 
      pickedJob = selectedJob(largeJobs, largeJobsHelix);
      highlighter3 = pickedJob < 0 ? highlighter3 : pickedJob;
      break;
    case 4: 
      pickedJob = selectedJob(allJobs, allJobsHelix);
      highlighter4 = pickedJob < 0 ? highlighter4 : pickedJob;
      break;
  }      
}
/**
 * ArcBall is all about smooth rotation of object for fun 
 * I should create two cubes each with a separate ArcBall....
 * http://wiki.processing.org/w/Arcball
 */

class ArcBall {
  float center_x, center_y, radius;
  PVector v_down, v_drag;
  Quat q_now, q_down, q_drag;
  PVector[] axisSet;
  int axis;

  ArcBall(float center_x, float center_y, float radius) {
    this.center_x = center_x;
    this.center_y = center_y;
    this.radius = radius;

    this.v_down = new PVector();
    this.v_drag = new PVector();

    this.q_now = new Quat();
    this.q_down = new Quat();
    this.q_drag = new Quat();

    this.axisSet = new PVector[] {
      new PVector(1.0f, 0.0f, 0.0f), 
      new PVector(0.0f, 1.0f, 0.0f), 
      new PVector(0.0f, 0.0f, 1.0f)
      };
      axis = -1;  // no constraints...
  }

  public void mousePressed(int mx, int my) {
    v_down = mouseToSphere(mx, my);
    q_down.copy(q_now);
    q_drag.reset();
  }

  public void mouseDragged(int mx, int my) {
    v_drag = mouseToSphere(mx, my);
    q_drag.set(PVector.dot(v_down, v_drag), v_down.cross(v_drag));
  }

  public void update() {
    q_now = Quat.mult(q_drag, q_down);
    applyQuat2Matrix(q_now);
  }

  public PVector mouseToSphere(float x, float y) {
    PVector v = new PVector();
    v.x = (x - center_x) / radius;
    v.y = (y - center_y) / radius;

    float mag = v.x * v.x + v.y * v.y;
    if (mag > 1.0f) {
      v.normalize();
    }
    else {
      v.z = sqrt(1.0f - mag);
    }
    return (axis == -1) ? v : constrainVector(v, axisSet[axis]);
  }

  public PVector constrainVector(PVector vector, PVector axis) {
    PVector res = PVector.sub(vector, PVector.mult(axis, PVector.dot(axis, vector)));
    res.normalize();
    return res;
  }

  public void applyQuat2Matrix(Quat q) {
    // instead of transforming q into a matrix and applying it...
    float[] aa = q.getValue();
    rotate(aa[0], aa[1], aa[2], aa[3]);
  }
}
// SimpleDateFormat only works in Processing 2.0 with these imports



class Cylinder {
  private PShape timeRod, cap1, cap2, cap3, bottomRod, topRod;
  private int c;
  private float r, bottomCapZ, h;
  private float ang = 0;
  private int sides = 25;
  private String jobType, jobStartTime;
  private float percentFull;

  Cylinder(int _c, String _jobType, String _jobStartTime, float _r) {
    c = _c;
    r = _r;
    bottomCapZ = r*5;
    jobType = _jobType;
    jobStartTime = _jobStartTime;
    createCylinder();
  }

  private void howMuchToColor() {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    Date currentDate = new Date();
    Date jobStartDate = null;
    try { 
      jobStartDate = dateFormat.parse(jobStartTime);
    } 
    catch(ParseException e) { 
      e.printStackTrace();
    }
    
    long elapsedTime = currentDate.getTime() - jobStartDate.getTime(); 

    if (jobType.equals("normal")) {                // normal         = 48hrs
      h = 200;
      if(elapsedTime > 1.72e8f) percentFull = 1; 
      else percentFull = elapsedTime/1.72e8f;
    } 
    else if (jobType.equals("development")) {      // development    = 04hrs
      h = 20;
      if(elapsedTime > 1.44e7f) percentFull = 1;
      else percentFull = elapsedTime/1.44e7f;  
    } 
    else if (jobType.equals("largemem")) {         // largemem       = 48hrs
      h = 200;
      if(elapsedTime > 1.72e8f) percentFull = 1;
      else percentFull = elapsedTime/1.72e8f;
    } 
    else if (jobType.equals("serial")) {           // serial         = 12hrs
      h = 60;
      if(elapsedTime > 4.32e7f) percentFull = 1;
      else percentFull = elapsedTime/4.32e7f;
    } 
    else if (jobType.equals("large")) {            // large          = 24hrs
      h = 100;
      if(elapsedTime > 8.64e7f) percentFull = 1;
      else percentFull = elapsedTime/8.64e7f;
    } 
    else if (jobType.equals("request")) {          // request        = 24hrs
      h = 100;
      if(elapsedTime > 8.64e7f) percentFull = 1;
      else percentFull = elapsedTime/8.64e7f;
    }
    else if (jobType.equals("normal-mic")) {       // request        = 24hrs
      h = 100;
      if(elapsedTime > 8.64e7f) percentFull = 1;
      else percentFull = elapsedTime/8.64e7f;
    }
    else if (jobType.equals("normal-2mic")) {      // request        = 24hrs
      h = 100;
      if(elapsedTime > 8.64e7f) percentFull = 1;
      else percentFull = elapsedTime/8.64e7f;
    } 
    else if (jobType.equals("gpu")) {              // gpu            = 24hrs
      h = 100;
      if(elapsedTime > 8.64e7f) percentFull = 1;      
      else percentFull = elapsedTime/8.64e7f;
    }
    else if (jobType.equals("gpudev")) {           // gpudev         = 04hrs
      h = 20;
      if(elapsedTime > 1.44e7f) percentFull = 1;      
      else percentFull = elapsedTime/1.44e7f;
    }
    else if (jobType.equals("vis")) {              // vis            = 08hrs
      h = 40;
      if(elapsedTime > 2.88e7f) percentFull = 1;      
      else percentFull = elapsedTime/2.88e7f;
    }  
    else if (jobType.equals("visdev")) {           // visdev         = 04hrs
      h = 20;
      if(elapsedTime > 1.44e7f) percentFull = 1;
      else percentFull = elapsedTime/1.44e7f;  
    }
  }

  private void createCylinder() {
    howMuchToColor(); //determine what portion of the cylinder needs to be colored in
    timeRod = createShape(GROUP);

    /************************************/
    /* Draw colored portion of Cylinder */
    /************************************/

    //cap 1
    cap1 = createShape(); 
    cap1.setFill(c);
    cap1.setStroke(false);
    cap1.beginShape();
    for (int i=0; i<=sides; i++) {
      float  px = cos(ang)*r;
      float  py = sin(ang)*r;
      cap1.vertex(px, py, bottomCapZ); 
      ang+=TWO_PI/sides;
    }
    cap1.endShape(); 
    timeRod.addChild(cap1);

    //body
    bottomRod = createShape();
    bottomRod.setFill(c);
    bottomRod.setStroke(false);
    bottomRod.beginShape(QUAD_STRIP);
    for (int i=0; i<=sides; i++) {
      float  px = cos(ang)*r;
      float  py = sin(ang)*r;
      bottomRod.vertex(px, py, bottomCapZ); 
      bottomRod.vertex(px, py, bottomCapZ+(h*percentFull)); 
      ang+=TWO_PI/sides;
    }
    bottomRod.endShape();
    timeRod.addChild(bottomRod);
    
    //cap2
    cap2 = createShape();
    cap2.setFill(c);
    cap2.setStroke(false);
    cap2.beginShape();
    for (int i=0; i<=sides; i++) {
      float  px = cos(ang)*r;
      float  py = sin(ang)*r;
      cap2.vertex(px, py, bottomCapZ+(h*percentFull)); 
      ang+=TWO_PI/sides;
    }
    cap2.endShape();
    timeRod.addChild(cap2);

    /************************************/
    /* Draw white portion of Cylinder   */
    /************************************/
    
    if(percentFull < 1){
      //body
      topRod = createShape(); 
      topRod.setFill(color(255));
      topRod.setStroke(false);
      topRod.beginShape(QUAD_STRIP);
      for (int i=0; i<=sides; i++) {
        float  px = cos(ang)*r;
        float  py = sin(ang)*r;
        topRod.vertex(px, py, bottomCapZ+(h*percentFull)); 
        topRod.vertex(px, py, bottomCapZ+h); 
        ang+=TWO_PI/sides;
      }
      topRod.endShape(); 
      timeRod.addChild(topRod);
  
      //cap3
      cap3 = createShape(); 
      cap3.setFill(color(255));
      cap3.setStroke(false);
      cap3.beginShape();
      for (int i=0; i<=sides; i++) {
        float  px = cos(ang)*r;
        float  py = sin(ang)*r;
        cap3.vertex(px, py, bottomCapZ+h); 
        ang+=TWO_PI/sides;
      }
      cap3.endShape();
      timeRod.addChild(cap3);
    }
  }
  
  public PShape getCylinder(){
    return timeRod; 
  }
}

class Helix {
  private final int SLOTS_PER_NODE = 16; // ranger=16, longhorn=16, lonestar=12, stampede=16
  private int runningJobCnt = 0;
  
  private ArrayList<Job> jobs;
  private int maxSlotsIndex;
  private float helixRadius;
  private float x,y,z, deltaZ;
  PShape helix;
  
  Helix(ArrayList<Job> _jobs, int _maxSlotsIndex) {
    jobs = _jobs;
    maxSlotsIndex = _maxSlotsIndex;
    helixRadius = 400;
    deltaZ = 2;
  }
  
  public void createHelix() {
    helix = createShape(GROUP);
    x = 0; y = 0; z = 0;
    float theta = 0;
    for (int i=0; i<jobs.size(); i++) { 
      runningJobCnt++;          
      int jobColor = color(random(0, 255), random(0, 255), random(0, 255)); // color running jobs      
      float thisSphereRadius = calculateRadius(jobs.get(i).getSlots(), jobs.get(maxSlotsIndex).getSlots()); 
      int nodesPerJob = jobs.get(i).getSlots()/SLOTS_PER_NODE;
      if(nodesPerJob == 0) nodesPerJob = 1; // jobs with less than SLOTS_PER_NODE cores get rounded to 1 node
      
      jobs.get(i).setStartCoordinates(x,y,z,theta);
      jobs.get(i).setNodeCount(nodesPerJob);
      jobs.get(i).setSphereRadius(thisSphereRadius);
    
      for (int j=0; j<nodesPerJob; j++) {  
  
        // convert from polar to cartesian coordinates
        x = helixRadius * cos(theta);
        y = helixRadius * sin(theta);
        z += deltaZ; 
                  
        // create cyliner+orb pshape
        PShape cylorb = createShape(GROUP);
        cylorb.translate(x, y, z); 
        cylorb.rotateY(PI/2);
        cylorb.rotateX(-theta);
        
        // create orb pshape
        PShape orb = createShape(SPHERE, thisSphereRadius);
        orb.setStroke(false);
        orb.setFill(jobColor);
        cylorb.addChild(orb);
        
        // create time cylinder
        Cylinder timeCylinder = new Cylinder(jobColor, jobs.get(i).getQueueName(), jobs.get(i).getStartTime(), thisSphereRadius/5);
        cylorb.addChild(timeCylinder.getCylinder()); 
        
        helix.addChild(cylorb);
        
        // distance between the radii of neighboring spheres dictates theta
        if ((j == nodesPerJob-1) && (i != jobs.size()-1)) {
          float nextSphereRadius = calculateRadius(jobs.get(i+1).getSlots(), jobs.get(maxSlotsIndex).getSlots()); 
          theta += asin((thisSphereRadius+nextSphereRadius)/helixRadius);  
        }else {
          theta += asin((thisSphereRadius*2)/helixRadius);
        }
      }       
    }
  }  
  
  private float calculateRadius(int jobSlots, int _maxSlots) {
    float minSlots = 1;   // x0
    float minRadius = 5;  // y0
  
    float maxSlots = _maxSlots; // x1
    float maxRadius = 20;   // y1
  
    // interpolate sphere radius
    return minRadius + (((jobSlots-minSlots)*maxRadius-(jobSlots-minSlots)*minRadius)/(maxSlots-minSlots));
  }
  
  public void displayHelix(){
    shape(helix); 
  }
  
  public float getHelixRadius() {
    return helixRadius; 
  }
  
  public float getDeltaZ() {
    return deltaZ; 
  }
  
  public int getRunningJobCount() {
    return runningJobCnt; 
  }
  
}
class Job {
  private int jobNum;
  private String jobName;
  private String jobOwner;
  private String jobStartTime;
  private String queueName;
  private int slots;
  
  private boolean positionAcquired = false;
  private float startX, startY, startZ, theta, radius;
  private int nodeCount;
  
  Job(int _jobNum, String _jobName, String _jobOwner, String _jobStartTime, String _queueName, int _slots){
    jobNum = _jobNum;
    jobName = _jobName;
    jobOwner = _jobOwner;
    jobStartTime = _jobStartTime;
    queueName = _queueName;
    slots = _slots;  
  }
  
  // XML Data
  public int getJobNum(){
    return jobNum; 
  }
  
  public String getJobName(){
    return jobName; 
  }
  
  public String getJobOwner(){
    return jobOwner; 
  }
  
  public String getStartTime(){
    return jobStartTime; 
  }
  
  public String getAllocatedTime(){
    String allocatedTime = "";
    if (queueName.equals("normal") || queueName.equals("largemem")) allocatedTime = "48 hours";
    else if (queueName.equals("large") || queueName.equals("request") || queueName.equals("normal-mic") || queueName.equals("normal-2mic") || queueName.equals("gpu")) allocatedTime = "24 hours";
    else if (queueName.equals("serial")) allocatedTime = "12 hours";
    else if (queueName.equals("vis")) allocatedTime = "08 hours";
    else if (queueName.equals("development") || queueName.equals("gpudev") || queueName.equals("visdev")) allocatedTime = "04 hours";
    return allocatedTime;
  }
  
  public String getElapsedTime(){
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    Date currentDate = new Date();
    Date jobStartDate = null;
    try {
      jobStartDate = dateFormat.parse(jobStartTime);
    }
    catch(ParseException e) {
      e.printStackTrace();
    }
    
    long difference = currentDate.getTime() - jobStartDate.getTime();
    int seconds = (int) (difference / 1000) % 60 ;
    int minutes = (int) ((difference / (1000*60)) % 60);
    int hours = (int) ((difference / (1000*60*60)) % 24);
    int days = (int) (difference / (1000*60*60*24));
    String elapsedTime = str(days) + " days " + str(hours) + " hours " + str(minutes) + " minutes " + str(seconds) + " seconds";
    return elapsedTime;
  }
  
  public String getQueueName(){
    return queueName; 
  }
  
  public int getSlots(){
    return slots; 
  }
  
  // Sphere Coordinates 
  public float getX() {
    return startX;
  }

  public float getY() {
    return startY;
  }

  public float getZ() {
    return startZ;
  }

  public float getTheta() {
    return theta;
  }

  public int getNodeCount() {
    return nodeCount;
  }  
  
  public float getSphereRadius() {
    return radius;
  }
  
  public void setStartCoordinates(float _startX, float _startY, float _startZ, float _theta) {
    if(!positionAcquired) {
      startX = _startX;
      startY = _startY;
      startZ = _startZ;
      theta = _theta;
      positionAcquired = true;
    }
  }
  
  public void setNodeCount(int _nodeCount){
    nodeCount = _nodeCount; 
  }
  
  public void setSphereRadius(float _radius){
    radius = _radius; 
  }
}

static class Quat{
  float w, x, y, z;

  Quat() {
    reset();
  }

  Quat(float w, float x, float y, float z) {
    this.w = w;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public final void reset(){
    this.w = 1.0f;
    this.x = 0.0f;
    this.y = 0.0f;
    this.z = 0.0f;
  }

  public void set(float w, PVector v) {
    this.w = w;
    this.x = v.x;
    this.y = v.y;
    this.z = v.z;
  }

  public void copy(Quat q){
    this.w = q.w;
    this.x = q.x;
    this.y = q.y;
    this.z = q.z;
  }

  public static Quat mult(Quat q1, Quat q2){
    Quat res = new Quat();
    res.w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z;
    res.x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y;
    res.y = q1.w * q2.y + q1.y * q2.w + q1.z * q2.x - q1.x * q2.z;
    res.z = q1.w * q2.z + q1.z * q2.w + q1.x * q2.y - q1.y * q2.x;
    return res;
  }

  public float[] getValue(){
    // transforming this quat into an angle and an axis vector...   

    float sa = (float) Math.sqrt(1.0f - w * w);
    if (sa < PConstants.EPSILON)
    {
      sa = 1.0f;
    }
    return new float[] {
      acos(w) * 2, x / sa, y / sa, z / sa
    };
  }
}
// we need to import the TUIO library
// and declare a TuioProcessing client variable

TuioProcessing tuioClient;
TuioCursor tuioCursor1 = null;
TuioCursor tuioCursor2 = null;
TuioCursor tuioCursor3 = null;

float startDistance, currDistance;
float prevZoomScaler, zoomScaler = .3f;
float startX, startY, posX, posY;

public void initTUIO(int w, int h) {
  // we create an instance of the TuioProcessing client
  // since we add "this" class as an argument the TuioProcessing class expects
  // an implementation of the TUIO callback methods (see below)
  tuioClient  = new TuioProcessing(this);
  posX = w/2;
  posY = h/2;
}

public float getDistance(TuioCursor tuioCursor1, TuioCursor tuioCursor2) {
  return dist(tuioCursor1.getScreenX(width), tuioCursor1.getScreenY(height), 
              tuioCursor2.getScreenX(width), tuioCursor2.getScreenY(height));
}

// these callback methods are called whenever a TUIO event occurs
// called when a cursor is added to the scene
public void addTuioCursor(TuioCursor tcur) { 
  if (tuioCursor1 == null) {
    tuioCursor1 = tcur;
    
    // simulates mouse click which will launch selectedJob() function
    mouseX = tuioCursor1.getScreenX(width);
    mouseY = tuioCursor1.getScreenY(height);
    
    // rotate
    arcball.mousePressed(tuioCursor1.getScreenX(width),tuioCursor1.getScreenY(height));     
  } 
  else if (tuioCursor2 == null) {
    // zoom
    tuioCursor2 = tcur;
    startDistance = getDistance(tuioCursor1, tuioCursor2);
    prevZoomScaler = zoomScaler;      
  }
  else if (tuioCursor3 == null) {
    // pan
    tuioCursor3 = tcur;
    startX = tuioCursor3.getScreenX(width);
    startY = tuioCursor3.getScreenY(height);
  } 
}

// called when a cursor is moved
public void updateTuioCursor (TuioCursor tcur) { 
  if (tuioCursor1 != null && tuioCursor2 != null && tuioCursor3 != null){
    // pan
    float currX = tuioCursor3.getScreenX(width);
    float currY = tuioCursor3.getScreenY(height);
    float dx = currX - startX;
    float dy = currY - startY;

    posX += dx;
    posY += dy;

    startX = currX;
    startY = currY;  
  }
  else if (tuioCursor1 != null && tuioCursor2 != null) {    
    // zoom
    currDistance = getDistance(tuioCursor1, tuioCursor2);
    zoomScaler = prevZoomScaler*(currDistance/startDistance);
  } 
  else if (tuioCursor1 != null) {
    // rotate
    arcball.mouseDragged(tcur.getScreenX(width),tcur.getScreenY(height));      
  } 
}

// called when a cursor is removed from the scene
public void removeTuioCursor(TuioCursor tcur) {
  if (tuioCursor3 != null && tuioCursor3.getCursorID() == tcur.getCursorID()) {
    // Remove 3nd cursor
    tuioCursor3 = null;
    
    // remove the remaining cursors to prevent jitter
    if (tuioCursor2 != null) tuioCursor2 = null;
    if (tuioCursor1 != null) tuioCursor1 = null;
  }
  
  if (tuioCursor2 != null && tuioCursor2.getCursorID() == tcur.getCursorID()) {
    // Remove 2nd cursor
    tuioCursor2 = null;
    
    // remove the remaining cursors to prevent jitter
    if (tuioCursor3 != null) tuioCursor3 = null;
    if (tuioCursor1 != null) tuioCursor1 = null;
  }

  if (tuioCursor1 != null && tuioCursor1.getCursorID() == tcur.getCursorID()) {
    // Remove 1st cursor
    tuioCursor1 = null;
    
    // remove the remaining cursors to prevent jitter
    if (tuioCursor3 != null) tuioCursor3 = null;
    if (tuioCursor2 != null) tuioCursor2 = null;
  } 
}

// called after each message bundle representing the end of an image frame
public void refresh(TuioTime bundleTime) { 
  redraw();
} 

// NOT NEEDED
public void addTuioObject(TuioObject tobj) {}
public void updateTuioObject(TuioObject tobj) {}  
public void removeTuioObject(TuioObject tobj) {}


  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "--full-screen", "--bgcolor=#666666", "--stop-color=#cccccc", "TACC_SuperComputer_Queue" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
