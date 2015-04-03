// used for vis interaction (pan, zoom, rotate)
import peasy.*;
import java.util.Map;
PeasyCam cam;
PMatrix3D baseMat; // used for peasycam + HUD + lights fix 

// used for HUDs
import controlP5.*;
ControlP5 cp5;
ControlGroup descriptionBox; 
Textlabel descriptionText;
ControlGroup usageBox; 
Textlabel usageText;
ControlGroup jobBox; 
Textlabel jobText;
ControlGroup titleBox; 
Textlabel titleText;
ControlGroup abstractBox; 
Textarea abstractText;

// used for tuio rotation support
ArcBall arcball; 

// separate jobs array into three arrays depending on slot count
ArrayList<Job> smallJobs = new ArrayList<Job>();
ArrayList<Job> mediumJobs = new ArrayList<Job>();
ArrayList<Job> largeJobs = new ArrayList<Job>();
ArrayList<Job> allJobs = new ArrayList<Job>();

// to easily associate job 'IDs' with jobs for cross referencing
// with project
// key is String, value is JSONObject
HashMap<String, JSONObject> hash = new HashMap<String, JSONObject>();

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
private String jobAbstract;

boolean FULLSCREEN = true;
boolean USE_TUIO = true;

void setup() {
  if (FULLSCREEN) size(displayWidth, displayHeight, OPENGL); // run from "Sketch -> Present"
  else size(1100, 700, OPENGL);
  baseMat = g.getMatrix(baseMat);
  cp5 = new ControlP5(this);
  cp5.setAutoDraw(false);

  if (USE_TUIO) {
    arcball = new ArcBall(width/2, height/2, min(width - 20, height - 20) * 0.8);
    initTUIO(width, height);
  } 
  else {
    // peasycam setup 
    cam = new PeasyCam(this, 0, 0, 0, 2000);
    cam.setResetOnDoubleClick(false);
  }

  // used to highlight selected job
  wireSphere = createShape(SPHERE, 1); 
  wireSphere.setFill(false);
  wireSphere.setStroke(color(255, 150));

  // separate method that can be re-called to restart sketch
  initSketch();

  if (smallJobs.size()==0 && mediumJobs.size()==0 && largeJobs.size()==0) {
    println("\nJSON file has no running jobs. Nothing to draw. Exiting sketch.");
    noLoop();
    exit();
  } 
  else {
    createHUDs();
   // noCursor();
  }
}

void initSketch() {
  parseDetails();
  parseFile();

  // viewing the entire queue at once is cool but not as useful.
  // breaking the queue into three smaller helixes should make it easier
  // for the user to search the visualization for specific jobs
  //  println("smallJobs size = " + smallJobs.size());
  //  println("mediumJobs size = " + mediumJobs.size());
  //  println("largeJobs size = " + largeJobs.size());
  //  println("allJobs size = " + allJobs.size());

  smallJobsHelix = new Helix(smallJobs, getMaxSlotsPosition(smallJobs)); 
  smallJobsHelix.createHelix();  
  mediumJobsHelix = new Helix(mediumJobs, getMaxSlotsPosition(mediumJobs)); 
  mediumJobsHelix.createHelix();
  largeJobsHelix = new Helix(largeJobs, getMaxSlotsPosition(largeJobs)); 
  largeJobsHelix.createHelix();
  allJobsHelix = new Helix(allJobs, getMaxSlotsPosition(allJobs)); 
  allJobsHelix.createHelix();
} 

void draw() {
  //println(mouseX);
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
  } 
  else {
    background(0);
    smooth(8);

    // save peasycam matrix and reset original
    pushMatrix();
    g.setMatrix(baseMat);
    ambientLight(40, 40, 40);
    directionalLight(255, 255, 255, -150, 40, -140);
    popMatrix();

    if (USE_TUIO) {
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
    rotz += .0009;
    huds();
  }
} 

// Call parseDetails() before parseFile()
// Puts project 
void parseDetails() {
  //Load JSON containing details about each job
  JSONArray jsonDetails =  loadJSONArray("projectDetails.json");

  for (int i = 0; i < jsonDetails.size(); i++) {
    JSONObject project = jsonDetails.getJSONObject(i);

    String id = project.getString("projectCode");     
    hash.put(id, project);
  }
}

void parseFile() {
  // Load an JSON 
  JSONArray json = loadJSONArray("queue.json");

  int count = 0;

  for (int i=0; i < json.size(); i++ ) {    
    JSONObject job = json.getJSONObject(i); 
    //println(job);
    //println();
    JSONArray state = job.getJSONArray("State");
    String ipf = splitTokens(state.toString().replaceAll("\\[", "").replaceAll("\\]", "").replace("\"", "").replaceAll("\\n", ""), ", ")[0];
    String ipfStatus = splitTokens(ipf, ":")[1];

    if (ipfStatus.equals("running")) { // only process running jobs
      int num = job.getInt("LocalIDFromManager");
      String name = job.getString("Name", "");
      String owner = job.getString("LocalOwner");
      String startTime = job.getString("StartTime").replaceFirst(".$", ""); // make sure to remove trailing 'Z' from startTime 
      String queue = job.getString("Queue");
      int slotNum = job.getInt("RequestedSlots");

      //Save job to add details
      //Job localJob = new Job(num, name, owner, startTime, queue, slotNum);

      //get extension object to get local account
      JSONObject extension = job.getJSONObject("Extension");
      String id = extension.getString("LocalAccount");

      //Checks if project ID from projectDetails.json in parseDetails()
      //matches job ID from queue.json, adds details if true
      if (hash.containsKey(id)) {
        //        println("Hashmap has job key " + id + "!");
        JSONObject project = hash.get(id);
        String pi = project.getString("pi");
        //        int cores = project.getInt("cores"); //UNUSED: cores per project, each project can have several jobs,
        String fieldOfStudy = project.getString("fos");
        String institution = project.getString("inst");
        String abstractt =  project.getString("abstract");
        //localJob.addDetails(pi, fieldOfStudy, institution, abstractt);

        // create job in appropriate list depending on slot count
        if (slotNum < smallJobsUpperBound) smallJobs.add(new Job(num, name, owner, startTime, queue, slotNum, pi, fieldOfStudy, institution, abstractt));
        else if (slotNum > (smallJobsUpperBound-1) && slotNum < mediumJobsUpperBound) mediumJobs.add(new Job(num, name, owner, startTime, queue, slotNum, pi, fieldOfStudy, institution, abstractt));
        else largeJobs.add(new Job(num, name, owner, startTime, queue, slotNum, pi, fieldOfStudy, institution, abstractt));        

        //add job to allJobs array list
        allJobs.add(new Job(num, name, owner, startTime, queue, slotNum, pi, fieldOfStudy, institution, abstractt));
      }

      //      if(id.equals("TG-MCA01S027")){
      //        count+= slotNum;
      //        //println("add " + slotNum + " to count");
      //      }
      //println("Adding job " + id + " to hashmap");
    }
  }  
  //println();
  //println("Number of slots requested for TG-MCA01S027: " + count);
}

int getMaxSlotsPosition(ArrayList<Job> jobs) {
  if (jobs.size() == 0) return -1;
  else {
    int maxSlots = jobs.get(0).getSlots();
    int maxPos = 0;
    for (int i=1; i<jobs.size(); i++) {
      if (jobs.get(i).getSlots() > maxSlots) {
        maxSlots = jobs.get(i).getSlots();
        maxPos = i;
      }
    }
    return maxPos;
  }
}

int getMinSlotsPosition(ArrayList<Job> jobs) {
  if (jobs.size() == 0) return -1;
  else {
    int minSlots = jobs.get(0).getSlots();
    int minPos = 0;
    for (int i=1; i<jobs.size(); i++) {
      if (jobs.get(i).getSlots() < minSlots) {
        minSlots = jobs.get(i).getSlots();
        minPos = i;
      }
    }
    return minPos;
  }
}

int selectedJob(ArrayList<Job> jobs, Helix helix) {
  for (int i=0; i<jobs.size(); i++) { 
    float tolerance = 10;
    float sphereRadius = jobs.get(i).getSphereRadius(); 
    float x = jobs.get(i).getX();
    float y = jobs.get(i).getY();
    float z = jobs.get(i).getZ();
    float theta = jobs.get(i).getTheta();

    for (int j=0; j<jobs.get(i).getNodeCount(); j++) {
      float sphereCenterX = screenX(x, y, z);
      if (mouseX <= (sphereCenterX+tolerance) && mouseX >= (sphereCenterX-tolerance)) {
        float sphereCenterY = screenY(x, y, z);
        if (mouseY <= (sphereCenterY+tolerance) && mouseY >= (sphereCenterY-tolerance)) {
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

void highlightJobNodes(int index, ArrayList<Job> jobs, Helix helix) {
  float x, y;
  float z = jobs.get(index).getZ();
  float theta = jobs.get(index).getTheta();
  for (int i=0; i<jobs.get(index).getNodeCount(); i++) {
    x = helix.getHelixRadius()*cos(theta);
    y = helix.getHelixRadius()*sin(theta);
    z += helix.getDeltaZ();

    pushMatrix();
    translate(x, y, z);
    scale(jobs.get(index).getSphereRadius()*1.1);
    shape(wireSphere);
    popMatrix();

    theta += asin((jobs.get(index).getSphereRadius()*2)/helix.getHelixRadius());
  }
}

void createHUDs() {
  // change the default font to Verdana
  PFont p = createFont("Times-Roman", 40);
  cp5.setControlFont(p);

  //  jobInfo = "Job #" + (highlighter1+1) + "\n\n" +
  //            "Job Number:         " + smallJobs.get(highlighter1).getJobNum() + "\n" +
  //            "Job Name:             " + smallJobs.get(highlighter1).getJobName() + "\n" +
  //            "TACC User:           " + smallJobs.get(highlighter1).getJobOwner() + "\n" +
  //            "Requested Time:   " + smallJobs.get(highlighter1).getAllocatedTime() + "\n" +
  //            "Elapsed Time:        " + smallJobs.get(highlighter1).getElapsedTime() + "\n" +
  //            "Requested CPUs:  " + smallJobs.get(highlighter1).getSlots() + "\n";

  jobInfo = "Job #" + (highlighter1+1) + "\n\n" +
    "Job Number:         " + smallJobs.get(highlighter1).getJobNum() + "\n" +
    "Job Name:             " + smallJobs.get(highlighter1).getJobName() + "\n" +
    "TACC User:           " + smallJobs.get(highlighter1).getJobOwner() + "\n" +
    "Requested Time:   " + smallJobs.get(highlighter1).getAllocatedTime() + "\n" +
    "Elapsed Time:        " + smallJobs.get(highlighter1).getElapsedTime() + "\n" +
    "Requested CPUs:  " + smallJobs.get(highlighter1).getSlots() + "\n" +
    "PI:        " + smallJobs.get(highlighter1).getPi() + "\n" +
    "Institution:   " + smallJobs.get(highlighter1).getInstitution() + "\n" +
    "Field Of Study:  " + smallJobs.get(highlighter1).getFos() + "\n";

  jobAbstract = "Abstract: " + smallJobs.get(highlighter1).getAbstract();

  // Visualization Description
  descriptionBox = cp5.addGroup("descriptionBox", width/295, height/111, width * 2/10);
  descriptionBox.setBackgroundHeight((int)(height/5.67));
  descriptionBox.setBackgroundColor(color(0, 175));
  descriptionBox.hideBar();
  System.out.println(displayHeight);
  System.out.println(displayWidth);


  descriptionText = cp5.addTextlabel("descriptionBoxLabel", description, width/295, height/111);
  descriptionText.moveTo(descriptionBox);

  // Visualization Interaction
  usageBox = cp5.addGroup("usageBox", width/295, (int)(height/3.18), (int)(width/7.1));
  usageBox.setBackgroundHeight((int)(height/8.57));
  usageBox.setBackgroundColor(color(0, 175));
  usageBox.hideBar();

  usageText = cp5.addTextlabel("usageBoxLabel", usage, width/295, height/111);
  usageText.moveTo(usageBox);

  // Job Information
  jobBox = cp5.addGroup("jobBox", width/295, (int)(height/1.74), (int)(width/5.36));
  jobBox.setBackgroundHeight((int)(height/4.46));
  jobBox.setBackgroundColor(color(0, 175));
  jobBox.hideBar();

  jobText = cp5.addTextlabel("jobBoxLabel", jobInfo, width/295, height/111);
  jobText.moveTo(jobBox);

  // Title Information
  titleBox = cp5.addGroup("titleBox", (int)(width/2.37), height/111, (int)(width/6.41));
  titleBox.setBackgroundHeight((int)(height/10.61));
  titleBox.setBackgroundColor(color(0, 175));
  titleBox.hideBar();

  titleText = cp5.addTextlabel("titleTextLabel", title, width/295, height/111);
  titleText.moveTo(titleBox); 

  // Abstract Info in scrollable textarea
  abstractBox = cp5.addGroup("abstractBox", width * 3/4, (int)(height/1.71), 0);
  //abstractBox.setBackgroundHeight((int)(height/4.46));
  //abstractBox.setBackgroundColor(255);
  abstractBox.hideBar();

  abstractText = cp5.addTextarea("abstractText")
                    .setPosition(width * 3/4, (int)(height/1.71))
                    .setSize((int)(width/5.31), (int)(height/7.43))
                    .setColorBackground(color(0, 175));
                   
  abstractText.setText(jobAbstract);
}

// update HUD with highlighted job's info
void updateHUD(Helix helix, ArrayList<Job> jobs, int jobIndex, String helixDescription) {  
  jobInfo = "Job #" + (jobIndex+1) + "\n\n" +
    "Job Number:         " + jobs.get(jobIndex).getJobNum() + "\n" +
    "Job Name:             " + jobs.get(jobIndex).getJobName() + "\n" +
    "TACC User:           " + jobs.get(jobIndex).getJobOwner() + "\n" +
    "Requested Time:   " + jobs.get(jobIndex).getAllocatedTime() + "\n" +
    "Elapsed Time:        " + jobs.get(jobIndex).getElapsedTime() + "\n" +
    "Requested CPUs:  " + jobs.get(jobIndex).getSlots() + "\n" +
    "PI:        " + jobs.get(jobIndex).getPi() + "\n" +
    "Institution:   " + jobs.get(jobIndex).getInstitution() + "\n" +
    "Field Of Study:  " + jobs.get(jobIndex).getFos() + "\n";

  jobText.setValue(jobInfo);

  jobAbstract = "Abstract: " + jobs.get(jobIndex).getAbstract() + "\n";

  abstractText.setText(jobAbstract);

  title = "TACC STAMPEDE SUPERCOMPUTER QUEUE\n\n" + 
    helixDescription + "\n" +
    "job count = " + helix.getRunningJobCount();

  titleText.setValue(title);
}

void huds() {
  hint(DISABLE_DEPTH_TEST);
  if (USE_TUIO) {
    pushMatrix();
    resetMatrix();
    applyMatrix(baseMat); 
    cp5.draw();
    popMatrix();
  } 
  else {
    cam.beginHUD();
    cp5.draw();
    cam.endHUD();
  }
  hint(ENABLE_DEPTH_TEST);
}

void keyPressed() {
  if (key == CODED) {
    if (keyCode == UP) {  
      if (helixType == 1 && mediumJobs.size() > 0) helixType = 2;
      else if (helixType == 2 && largeJobs.size() > 0) helixType = 3;
      else if (helixType == 3 && allJobs.size() > 0) helixType = 4;
      else if (helixType == 4 && smallJobs.size() > 0) helixType = 1;
    } 
    else if (keyCode == DOWN) {
      if (helixType == 4 && largeJobs.size() > 0) helixType = 3;
      else if (helixType == 3 && mediumJobs.size() > 0) helixType = 2;
      else if (helixType == 2 && smallJobs.size() > 0) helixType = 1;
      else if (helixType == 1 && allJobs.size() > 0) helixType = 4;
    }
  }
}

// SELECTION BY MOUSE
void mousePressed() {
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

