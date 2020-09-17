

// I2C device class (I2Cdev) and MPU6050 class using DMP (MotionApps v2.0)
// 6/21/2012 by Jeff Rowberg <jeff@rowberg.net>
// Updates should (hopefully) always be available at https://github.com/jrowberg/i2cdevlib

/* ============================================
I2Cdev device library code is placed under the MIT license
Copyright (c) 2012 Jeff Rowberg

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
===============================================
*/
#include <jm_Scheduler.h>
#include <mcp_can.h>
#include <SPI.h>
#include "I2Cdev.h"
#include "canmessages.h"
#include "MPU6050_6Axis_MotionApps20.h"

// Arduino Wire library is required if I2Cdev I2CDEV_ARDUINO_WIRE implementation
// is used in I2Cdev.h
#if I2CDEV_IMPLEMENTATION == I2CDEV_ARDUINO_WIRE
    #include "Wire.h"
#endif
#define INTERRUPT_PIN 3  // interrupt pin for MPU6050
#define DEBUG_MODE FALSE


// create class objects
MPU6050 mpu; // MPU6050's I2C address is 0x68
MCP_CAN CAN0(10); // Set CS to pin 10 for the CAN module

//define the global variables that will hold the different CAN messages data
GYROSCOPE_DATA GyroscopeMsg;
LINEAR_ACCEL_G_DATA LinearAccelGMsg;
LINEAR_ACCEL_MPS2_DATA LinearAccelMPS2Msg;



jm_Scheduler commsScheduler;
#if DEBUG_MODE == TRUE
jm_Scheduler debugScheduler;  
#endif 

// MPU control/status vars
bool dmpReady = false;  // set true if DMP init was successful
uint8_t mpuIntStatus;   // holds actual interrupt status byte from MPU
uint8_t devStatus;      // return status after each device operation (0 = success, !0 = error)
uint16_t packetSize;    // expected DMP packet size (default is 42 bytes)
uint16_t fifoCount;     // count of all bytes currently in FIFO
uint8_t fifoBuffer[64]; // FIFO storage buffer
// orientation/motion vars
Quaternion q;           // [w, x, y, z]         quaternion container
VectorInt16 aa;         // [x, y, z]            accel sensor measurements
VectorInt16 aaReal;     // [x, y, z]            gravity-free accel sensor measurements
VectorInt16 aaWorld;    // [x, y, z]            world-frame accel sensor measurements
VectorFloat gravity;    // [x, y, z]            gravity vector
float euler[3];         // [psi, theta, phi]    Euler angle container
float ypr[3];           // [yaw, pitch, roll]   yaw/pitch/roll container and gravity vector


// ================================================================
// ===               INTERRUPT DETECTION ROUTINE                ===
// ================================================================

volatile bool mpuInterrupt = false;     // indicates whether MPU interrupt pin has gone high
void dmpDataReady() {
    mpuInterrupt = true;
}

//define the functions which will send the CAN messages on the CAN bus
void Gyroscope(){
  /* Send the Gyroscope data on the CAN bus */
  static uint8_t lastSentTime;
  
  lastSentTime = lastSentTime + COMMS_PROCESS_CYCLE_T_MS;
  if(lastSentTime >= cycleTime[GYROSCOPE]){
    GyroscopeMsg.data = 0;
    GyroscopeMsg.xAxis = (uint16_t) ((ypr[2] * 180/M_PI) + COMMS_ANGLE_OFFSET);
    GyroscopeMsg.yAxis = (uint16_t) ((ypr[1] * 180/M_PI) + COMMS_ANGLE_OFFSET);
    GyroscopeMsg.zAxis = (uint16_t) ((ypr[0] * 180/M_PI) + COMMS_ANGLE_OFFSET);
    (void) CAN0.sendMsgBuf(GYROSCOPE_ID, STANDARD_FRAME, GYROSCOPE_DLC, GyroscopeMsg.byteArray); 
    //Serial.print(F("Sent the LinearAccelG data on the CAN bus (cycle time: "));
    //Serial.print(lastSentTime);
    //Serial.println(F(")"));
    lastSentTime = 0;
  }
  
}
void LinearAccelG(){
  /* Send the LinearAccelG data on the CAN bus */
  static uint8_t lastSentTime;
  lastSentTime = lastSentTime + COMMS_PROCESS_CYCLE_T_MS;
  if(lastSentTime >= cycleTime[LINEAR_ACCEL_G]){
    // both messages share the same raw data and MPS2 is sent more frequently    
    // so it makes sense to simply copy that data
    LinearAccelGMsg.data = LinearAccelMPS2Msg.data; 
    (void) CAN0.sendMsgBuf(LINEAR_ACCEL_G_ID, STANDARD_FRAME, LINEAR_ACCEL_G_DLC, LinearAccelGMsg.byteArray);
    //Serial.print(F("Sent the LinearAccelG data on the CAN bus (cycle time: "));
    //Serial.print(lastSentTime);
    //Serial.println(F(")"));
    lastSentTime = 0;
  }
}
void LinearAccelMPS2(){
  /* Send the LinearAccelMPS2 data on the CAN bus */
  static uint8_t lastSentTime;
  lastSentTime = lastSentTime + COMMS_PROCESS_CYCLE_T_MS;
  if(lastSentTime >= cycleTime[LINEAR_ACCEL_MPS2]){
    LinearAccelMPS2Msg.data = 0;
    LinearAccelMPS2Msg.xAxis = (uint16_t) (aaReal.x + COMMS_ACCEL_OFFSET);
    LinearAccelMPS2Msg.yAxis = (uint16_t) (aaReal.y + COMMS_ACCEL_OFFSET);
    LinearAccelMPS2Msg.zAxis = (uint16_t) (aaReal.z + COMMS_ACCEL_OFFSET);
    (void) CAN0.sendMsgBuf(LINEAR_ACCEL_MPS2_ID, STANDARD_FRAME, LINEAR_ACCEL_MPS2_DLC, LinearAccelMPS2Msg.byteArray);
    //Serial.print(F("Sent the LinearAccelG data on the CAN bus (cycle time: "));
    //Serial.print(lastSentTime);
    //Serial.println(F(")"));
    lastSentTime = 0;
  }
  
}
void ProcessComms(){
  for(uint8_t message = 0; message < NR_OF_MESSAGES;message++){
    /* Call the message's function using an index of the function array */
    CanMessageFunc[message]();
  }
}
#if DEBUG_MODE == TRUE
void DebugFun(){
    Serial.print("rotation xyz\t");
    Serial.print(GyroscopeMsg.xAxis);
    Serial.print("\t");
    Serial.print(GyroscopeMsg.yAxis);
    Serial.print("\t");
    Serial.println(GyroscopeMsg.zAxis);
    Serial.print("areal\t");
    Serial.print(LinearAccelMPS2Msg.xAxis);
    Serial.print("\t");
    Serial.print(LinearAccelMPS2Msg.yAxis);
    Serial.print("\t");
    Serial.println(LinearAccelMPS2Msg.zAxis);
    Serial.println("Binary Rep:");
    for(uint8_t index = 0; index < GYROSCOPE_DLC;index++){
      printBinary(GyroscopeMsg.byteArray[index]);
    }
    Serial.println("");
    
}

void printBinary(byte inByte)
{
  for (int b = 7; b >= 0; b--)
  {
    Serial.print(bitRead(inByte, b));
  }
}
#endif
// ================================================================
// ===                      INITIAL SETUP                       ===
// ================================================================

void setup() {
    
  
    // join I2C bus (I2Cdev library doesn't do this automatically)
#if I2CDEV_IMPLEMENTATION == I2CDEV_ARDUINO_WIRE
        Wire.begin();
        Wire.setClock(400000); // 400kHz I2C clock. Comment this line if having compilation difficulties
#elif I2CDEV_IMPLEMENTATION == I2CDEV_BUILTIN_FASTWIRE
        Fastwire::setup(400, true);
#endif

    Serial.begin(115200);
    while (!Serial); // wait for Leonardo enumeration, others continue immediately

    // Initialize MCP2515 running at 8MHz with a baudrate of 500kb/s and the masks and filters disabled.
    if(CAN0.begin(MCP_ANY, CAN_500KBPS, MCP_8MHZ) == CAN_OK) Serial.println("MCP2515 Initialized Successfully!");
    else Serial.println("Error Initializing MCP2515...");
  
    CAN0.setMode(MCP_NORMAL);   // Change to normal mode to allow messages to be transmitted

    // initialize device
    Serial.println(F("Initializing I2C devices..."));
    mpu.initialize();
    pinMode(INTERRUPT_PIN, INPUT);

    // verify connection
    Serial.println(F("Testing device connections..."));
    Serial.println(mpu.testConnection() ? F("MPU6050 connection successful") : F("MPU6050 connection failed"));

    /* wait for ready
    Serial.println(F("\nSend any character to begin DMP programming and demo: "));
    while (Serial.available() && Serial.read()); // empty buffer
    while (!Serial.available());                 // wait for data
    while (Serial.available() && Serial.read()); // empty buffer again
    */

    // load and configure the DMP
    Serial.println(F("Initializing DMP..."));
    devStatus = mpu.dmpInitialize();

    // Setting Gyro and Accel Offsets (different for every MPU6050)
    mpu.setXAccelOffset(X_ACCEL_OFFSET);
    mpu.setYAccelOffset(Y_ACCEL_OFFSET);
    mpu.setZAccelOffset(Z_ACCEL_OFFSET);
    mpu.setXGyroOffset(X_GYRO_OFFSET);
    mpu.setYGyroOffset(Y_GYRO_OFFSET);
    mpu.setZGyroOffset(Z_GYRO_OFFSET);
     

    // make sure it worked (returns 0 if so)
    if (devStatus == 0) {
        // turn on the DMP, now that it's ready
        Serial.println(F("Enabling DMP..."));
        mpu.setDMPEnabled(true);

        // enable Arduino interrupt detection
        Serial.print(F("Enabling interrupt detection (Arduino external interrupt "));
        Serial.print(digitalPinToInterrupt(INTERRUPT_PIN));
        Serial.println(F(")..."));
        attachInterrupt(digitalPinToInterrupt(INTERRUPT_PIN), dmpDataReady, RISING);
        mpuIntStatus = mpu.getIntStatus();

        // set our DMP Ready flag so the main loop() function knows it's okay to use it
        Serial.println(F("DMP ready! The Accelerometer values can be read"));
        dmpReady = true;

        // get expected DMP packet size for later comparison
        packetSize = mpu.dmpGetFIFOPacketSize();
    } else {
        // ERROR!
        // 1 = initial memory load failed
        // 2 = DMP configuration updates failed
        // (if it's going to break, usually the code will be 1)
        Serial.print(F("DMP Initialization failed (code "));
        Serial.print(devStatus);
        Serial.println(F(")"));
    }

    commsScheduler.start(ProcessComms, COMMS_PROCESS_CYCLE_T_MS*TIMESTAMP_1MS);
#if DEBUG_MODE == TRUE
    debugScheduler.start(DebugFun, 500*TIMESTAMP_1MS);
#endif
}



// ================================================================
// ===                    MAIN PROGRAM LOOP                     ===
// ================================================================

void loop() {
    // if programming failed, don't try to do anything
    if (!dmpReady) return;

    // wait for MPU interrupt or extra packet(s) available
    while (!mpuInterrupt && fifoCount < packetSize) {
        if (mpuInterrupt && fifoCount < packetSize) {
          // try to get out of the infinite loop 
          fifoCount = mpu.getFIFOCount();
        }  
    }

    // reset interrupt flag and get INT_STATUS byte
    mpuInterrupt = false;
    mpuIntStatus = mpu.getIntStatus();

    // get current FIFO count
    fifoCount = mpu.getFIFOCount();

    // check for overflow (this should never happen unless our code is too inefficient)
    if ((mpuIntStatus & _BV(MPU6050_INTERRUPT_FIFO_OFLOW_BIT)) || fifoCount >= 1024) {
        // reset so we can continue cleanly
        mpu.resetFIFO();
        fifoCount = mpu.getFIFOCount();
        //Serial.println(F("FIFO overflow!"));

    // otherwise, check for DMP data ready interrupt (this should happen frequently)
    } else if (mpuIntStatus & _BV(MPU6050_INTERRUPT_DMP_INT_BIT)) {
        // wait for correct available data length, should be a VERY short wait
        while (fifoCount < packetSize) fifoCount = mpu.getFIFOCount();

        // read a packet from FIFO
        mpu.getFIFOBytes(fifoBuffer, packetSize);
        
        // track FIFO count here in case there is > 1 packet available
        // (this lets us immediately read more without waiting for an interrupt)
        fifoCount -= packetSize;

        // display Euler angles in degrees
        mpu.dmpGetQuaternion(&q, fifoBuffer);
        mpu.dmpGetGravity(&gravity, &q);
        mpu.dmpGetYawPitchRoll(ypr, &q, &gravity);
        
        

        // display real acceleration, adjusted to remove gravity
        mpu.dmpGetQuaternion(&q, fifoBuffer);
        mpu.dmpGetAccel(&aa, fifoBuffer);
        mpu.dmpGetGravity(&gravity, &q);
        mpu.dmpGetLinearAccel(&aaReal, &aa, &gravity);
        
        
    }

     
    Serial.flush();
    yield();
}
