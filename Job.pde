class Job {
  private int jobNum;
  private String jobName;
  private String jobOwner;
  private String jobStartTime;
  private String queueName;
  private int slots;
  private String pi;
  private String fos;
  private String institution;
  private String abstractt;

  private boolean positionAcquired = false;
  private float startX, startY, startZ, theta, radius;
  private int nodeCount;
  
//  Job(int _jobNum, String _jobName, String _jobOwner, String _jobStartTime, String _queueName, int _slots){
//    jobNum = _jobNum;
//    jobName = _jobName;
//    jobOwner = _jobOwner;
//    jobStartTime = _jobStartTime;
//    queueName = _queueName;
//    slots = _slots;  
//  }

  Job(int _jobNum, String _jobName, String _jobOwner, String _jobStartTime, String _queueName, int _slots, String pi, String fos, String institution, String abstractt){
    jobNum = _jobNum;
    jobName = _jobName;
    jobOwner = _jobOwner;
    jobStartTime = _jobStartTime;
    queueName = _queueName;
    slots = _slots; 
    this.pi = pi;
    this.fos = fos;
    this.institution = institution;
    this.abstractt = abstractt; 
  }
  
//  public void addDetails(String pi, String fos, String institution, String abstractt) {
//    this.pi = pi;
//    this.fos = fos;
//    this.institution = institution;
//    this.abstractt = abstractt;
//  }
  
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
  
  //Only call after addDetails() has been called
  public String getPi(){
    return pi; 
  }
  
  //Only call after addDetails() has been called
  public String getFos(){
    return fos; 
  }
  
  //Only call after addDetails() has been called
  public String getInstitution(){
    return institution; 
  }
  
  //Only call after addDetails() has been called
  public String getAbstract(){
    return abstractt; 
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

  public void printJobData(){      
    println("Job num: "+jobNum);
    println("Job name: " + jobName);
    println("Job owner: " + jobOwner);
    println("Job start time: " + jobStartTime);
    println("Queue name: " + queueName);
    println("Cores requested: " + slots);
    println("PI: " + pi);
    println("Field of study: " + fos);
    println("Institution: " + institution);
    println("Abstract: " + abstractt);
    println();  
  }
}

