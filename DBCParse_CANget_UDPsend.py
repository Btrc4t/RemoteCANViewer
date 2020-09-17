import socket
import subprocess
import time
import traceback
import struct
import sys
import os
import can
import curses
import binascii
from can import Message
from curses import wrapper
from bitstring import BitArray

canByteOrder = "big"

inputfile = open('commsMatrix_ArduinoECU.dbc', encoding="ISO-8859-1")
#global variables
signalIDGenerator = 0
udp_ip = "192.168.1.9"
udp_port = 7878
can_if = 'can0'
sock = None

class Signal:
  def __init__(self,canid,sgName,startBit,ln,ft,scale,offset,rngMin,rngMax,unitn,pduDLC):
    global udp_ip
    global udp_port
    self.udp_ip = udp_ip
    self.udp_port = udp_port
    self.sgName = sgName
    self.startBit = startBit
    self.bitLen = ln
    self.format = ft
    self.scale = scale
    self.offset = offset
    self.rangeMin = rngMin
    self.rangeMax = rngMax
    self.unitName = unitn
    self.littleEndian = None
    self.unsignedVar = None
    self.guessedUnitType = 3 #set initial value as invalid option
    self.pduDLC = pduDLC
    self.usedBits = 0
    #set guessedUnit as
    # 0 = boolean, 1 = int, 2 = float

    if '.' in self.scale:
      self.guessedUnitType = 2
      self.scale = float(self.scale)

    elif self.bitLen == 1:
      self.guessedUnitType = 0
      self.scale = int(self.scale)

    else:
      self.guessedUnitType = 1
      self.scale = int(self.scale)


    #set the variable format, little/Big endian and unsigned/signed

    if '1' in self.format:
      self.littleEndian = True
      #print(str(self.sgName)+"is little endian")

    else:
      self.littleEndian = False
      #print(str(self.sgName)+"is big endian")


    if '+' in self.format:
      self.unsignedVar = True
    else:
      self.unsignedVar = False


    #Maths
      #(2^bitLen-1)*scale+offset = max value in range
      #offset = min value in range
      #(InterpretedValue)*scale+offset = value to transmit


    if '.' in self.offset:
      self.offset = float(self.offset)
    else:
      self.offset = int(self.offset)

    if '.' in self.rangeMin:
      self.rangeMin = float(self.rangeMin)
    else:
      self.rangeMin = int(self.rangeMin)

    if '.' in self.rangeMax:
      self.rangeMax = float(self.rangeMax)
    else:
      self.rangeMax = int(self.rangeMax)

    global signalIDGenerator
    
    
    if signalIDGenerator == 256:
      print("\n\tWARNING: 255 signals were created (the maximum possible value), run the program with fewer arguments")
    else:
      #created signal with UDP ID: signalIDGenerator
      #print("set signal " + self.sgName+" to UDP ID: "+str(signalIDGenerator))
      self.udpid = signalIDGenerator
      signalIDGenerator+=1

    self.containerCanID = None
    self.udp_pkg = bytearray()
    self.lastSent_t = time.perf_counter()
    self.pkg_size = 0 # max is 98
    self.vartype = 3 # 0 = boolean, 1 = int, 2 = float 
    self.bitMsg = BitArray() #empty bit array which will store the message bits
    self.packFormat = ""
    self.payload = bytearray([])

  

  def setBitMsg(self,bMsg):
    self.bitMsg = bMsg

  def get_lastSent_t(self):
    return self.lastSent_t

  def get_sgName(self):
    return self.sgName

  def get_canid(self):
    return self.canid

  def get_udpid(self):
    return self.udpid

  def getFloatFromMessage(self,startbit,endbit):
    if canByteOrder is "big":
	  #data is sent as in this example:
      #00000001 00000010 00000100 00000000
      #0        1        2        3
      #and received as such (thanks to this program's own conversion)
      #00000000 00000100 00000010 00000001
      #3        2        1        0

	  #startbit is offset due to the change in the byte order
	  #but I think these changes should be reflected in the dbc (unsure but if that's the case, this can be removed)
      im = BitArray(self.bitMsg[(self.pduDLC * 8)-endbit:(self.pduDLC * 8)-startbit:1])
      
    else:
      im = BitArray(self.bitMsg[startbit:endbit:1])

    retval = None
    if self.littleEndian == True:
      if self.unsignedVar == True:
        #little endian unsigned
        retval = (im.uint * self.scale)+self.offset
      else:
        #little endian signed
        retval = (im.int * self.scale)+self.offset 
    else:
      if self.unsignedVar == True:
        #big endian unsigned
        retval = (im.uintbe * self.scale)+self.offset 
      else:
        #big endian signed
        retval = (im.intbe * self.scale)+self.offset 
    return bytearray(struct.pack("f", retval))



  def getIntFromMessage(self,startbit,endbit):
    if canByteOrder is "big":
	  #data is sent as in this example:
      #00000001 00000010 00000100 00000000
      #0        1        2        3
      #and received as such (thanks to this program's own conversion)
      #00000000 00000100 00000010 00000001
      #3        2        1        0

	  #startbit is offset due to the change in the byte order
	  #but I think these changes should be reflected in the dbc (unsure but if that's the case, this can be removed)
      im = BitArray(self.bitMsg[(self.pduDLC * 8)-endbit:(self.pduDLC * 8)-startbit:1])
      
    else:
      im = BitArray(self.bitMsg[startbit:endbit:1])
   

    
    retval = None
    if self.littleEndian == True:
      if self.unsignedVar == True:
        #little endian unsigned
        retval = (im.uint * self.scale)+self.offset
      else:
        #little endian signed
        retval = (im.int * self.scale)+self.offset 
    else:
      if self.unsignedVar == True:
        #big endian unsigned
        retval = (im.uintbe * self.scale)+self.offset 
      else:
        #big endian signed
        retval = (im.intbe * self.scale)+self.offset 

    return bytearray(struct.pack("I", retval))

  def getBooleanFromMessage(self,startbit,endbit):
    if canByteOrder is "big":
	  #data is sent as in this example:
      #00000001 00000010 00000100 00000000
      #0        1        2        3
      #and received as such (thanks to this program's own conversion)
      #00000000 00000100 00000010 00000001
      #3        2        1        0

	  #startbit is offset due to the change in the byte order
	  #but I think these changes should be reflected in the dbc (unsure but if that's the case, this can be removed)
      im = BitArray(self.bitMsg[(self.pduDLC * 8)-endbit:(self.pduDLC * 8)-startbit:1])
      
    else:
      im = BitArray(self.bitMsg[startbit:endbit:1])
      
    retval = None
    if(im.bool):
      retval = 0x01
    else:
      retval = 0x00
    

    return bytearray([retval])

  def setUsedBits(self, usedBits):
    self.usedBits = usedBits

  def appendPkg_sendWhenFull(self,scr):
    #set the data payload according to the type
      # 0 = boolean, 1 = int, 2 = float
    bits = BitArray(self.bitMsg[self.startBit:(self.startBit + self.bitLen):1])
    if self.guessedUnitType == 0 and len(self.payload) < 4:  
      var =  self.getBooleanFromMessage(self.startBit,self.startBit + self.bitLen)   
      self.payload = self.payload + var
      self.vartype = 0 #set bool
      if(len(self.payload) == 1) and (len(self.udp_pkg) < 2):
        #if this is the first boolean in the payload and the package was not previously initialized
        self.udp_pkg = getTimeStampBytes() + bytearray([self.pkg_size])
      try:
        scr.addstr(len(sys.argv) + self.udpid + 2,0,str(self.udpid)+" "+str(self.sgName)+" value: "+str(bits.bin)+" boolean",curses.color_pair(1))
      except:
        scr.addstr(len(sys.argv) + 1,0,"ERROR: NOT ENOUGH SCREEN SPACE",curses.color_pair(2))

    elif self.guessedUnitType == 1:
      var =  self.getIntFromMessage(self.startBit,self.startBit + self.bitLen) 
      self.payload = var
      self.vartype = 1 #set int
      try:
        scr.addstr(len(sys.argv) + self.udpid + 2,0,str(self.udpid)+" "+str(self.sgName)+" added bits: "+str(bits.bin)+" int: "+str(struct.unpack("I", var)),curses.color_pair(1))
      except:
        scr.addstr(len(sys.argv) + 1,0,"ERROR: NOT ENOUGH SCREEN SPACE",curses.color_pair(2))
    elif self.guessedUnitType == 2:
      var = self.getFloatFromMessage(self.startBit,self.startBit + self.bitLen)
      self.payload = var
      self.vartype = 2 #set float
      try:
        scr.addstr(len(sys.argv) + self.udpid + 2,0,str(self.udpid)+" "+str(self.sgName)+" added bits: "+str(bits.bin)+" float: "+str(struct.unpack("f", var)),curses.color_pair(1))
      except:
        scr.addstr(len(sys.argv) + 1,0,"ERROR: NOT ENOUGH SCREEN SPACE",curses.color_pair(2))

    #if a datapoint is ready
    if not len(self.payload) < 4:
      #start packing or append to package
      if len(self.udp_pkg) < 2:
        # package not initialized, start bytes need to be added 
        if not (self.vartype == 0):
          #in the case of boolean variables, they get the timestamp on their own, at the first byte, so this timestamp would be incorrect at this time
          #but otherwise, set timeStampBytes
          self.udp_pkg = getTimeStampBytes()
        self.pkg_size = self.pkg_size + 1       
        self.udp_pkg = self.udp_pkg + bytearray([self.pkg_size]) +  bytearray([self.udpid]) + self.payload      
        
      else:
        #package was previously initialized, just add data
        self.pkg_size = self.pkg_size + 1
        self.udp_pkg = self.udp_pkg + bytearray([self.udpid]) + self.payload


      #if a message wasn't sent in longer than 0.1 seconds or the package size is 98
      if ((time.perf_counter() - self.lastSent_t) > 0.1) or (self.pkg_size >= 98):
        #change pkg_size, append timestamp, vartype to packet and send it
        self.udp_pkg[8] = self.pkg_size
        self.udp_pkg = self.udp_pkg + getTimeStampBytes() + bytearray([self.vartype])
        global sock
        sock.sendto(self.udp_pkg,(self.udp_ip,self.udp_port))
        #after sending, lastSent_t is updated
        self.lastSent_t = time.perf_counter()
        self.udp_pkg = bytearray() #reset UDP package data        
        self.pkg_size = 0
        self.payload = bytearray([])


class PDU:

  def __init__(self,canid,pduName,pduSizeBytes,pduEcu):
    global udp_ip
    global udp_port
    self.udp_ip = udp_ip
    self.udp_port = udp_port
    self.canid = canid
    self.pduName = pduName
    self.pduSizeBytes = pduSizeBytes
    self.pduEcu = pduEcu
    self.bitMsg = BitArray() #empty bit array which will store the message bits
    self.lastSent_t = time.perf_counter() - 5.0
    self.udp_pkg = bytearray()
    self.pkg_size = 0
   
    self.Signals = [] #create empty list of signals
     #signal number to send currently, will be incremented and reset to 0 when it reaches the end (every signal needs to be packaged individually)
    self.sgNrToSend = 0

  def sendCfgPackage(self):
    if(time.perf_counter() - self.lastSent_t) > 3.0:
      #create udp packet
      cfgTS = bytearray([0xEE]*8)

      for index in range(0,len(self.Signals)):
	
        sigName = self.Signals[index].get_sgName()
        #get sigName as char array
        #always fill to 40 bytes, 0-39 max chars and 1-40 end chars (0x03)
        asciiOfSigName = bytearray(sigName.encode()[0:39]+bytearray([0x03]*(40 - len(sigName)))) 
        sigUDPID = bytearray([self.Signals[index].get_udpid()])
        
        if(len(self.udp_pkg) < 2):
          #package init
          self.udp_pkg = cfgTS + sigUDPID + asciiOfSigName
          self.pkg_size +=1
        else:
          #package was previously initialized, just add to it
          self.udp_pkg = self.udp_pkg + sigUDPID + asciiOfSigName
          self.pkg_size +=1
        if (self.pkg_size == 12) or (index == (len(self.Signals)-1)):
          #send and update internal time
          if self.pkg_size < 12:
            self.udp_pkg = self.udp_pkg + bytearray([0xFF]*(41 * (12 - self.pkg_size))) + cfgTS
          else:
            self.udp_pkg = self.udp_pkg + cfgTS 
			  
          
          global sock
          sock.sendto(self.udp_pkg,(self.udp_ip,self.udp_port))
          self.lastSent_t = time.perf_counter()
           
          
          #reset pkg
          self.udp_pkg = bytearray()
          self.pkg_size = 0

   
  def update_bitMsgFromBytes(self,byteArr):
    if canByteOrder is "big":
      byteArr = byteArr[::-1]
    self.bitMsg = BitArray(bytes = byteArr)

  def setUsedBits(self, usedBits):
    for index in range(0,len(self.Signals)):
      self.Signals[index].setUsedBits(usedBits)

  def append_sg(self,sig):
    sig.setBitMsg(self.bitMsg)
    self.Signals.append(sig)

  def get_canid(self):
    return self.canid

  def processAndSendAllPDUSignals(self,dataBytes,scr):
    
    self.update_bitMsgFromBytes(dataBytes)
    for index in range(0,len(self.Signals)):
      self.Signals[index].setBitMsg(self.bitMsg)
      self.Signals[index].appendPkg_sendWhenFull(scr)
  def addCANMsg(self,pos,msg,scr):
    prototype = str(msg.timestamp)+"\t"+str(self.pduEcu)+"\t"+str(self.canid)+"\t"+str(msg.dlc)+"\t"+str(self.bitMsg.bin)
    scr.addstr(pos,0,prototype,curses.color_pair(3))



def getPDUByCanID(canid,pdus):
  try:
    return [item for item in pdus if item.get_canid() == canid][0]
    print("searched for PDU with CAN ID "+str(canid)+" and found it")
  except:
    print("searched for PDU with CAN ID "+str(canid)+" and found nothing")
    return None

def getTimeStampBytes():
  return bytearray(struct.pack("d",time.perf_counter()))


def main(screen):
#Phase 0: Initialize interfaces
  #initialize UDP Socket
  try:
    global sock
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    #initialize CAN interface
    global can_if
    bus = can.interface.Bus(channel=can_if, bustype='socketcan_native')

    #init terminal window using curses
    curses.noecho()
    curses.start_color()
    curses.use_default_colors()
    screen.clear()
    screen.refresh()
    curses.init_pair(2,curses.COLOR_RED, curses.COLOR_WHITE)
    curses.init_pair(1,curses.COLOR_CYAN, curses.COLOR_BLACK)
    curses.init_pair(3,curses.COLOR_YELLOW, curses.COLOR_BLACK)
  except:
    print("\nExecution failure")
    curses.endwin()
    print(e)
    traceback.print_exc()

#Phase 1: Read and interpret DBC file


  PDUs = [] #create an empty array of PDUs


  detailReadMode = 0
  for i in range(1): inputfile.readline() # skip first line
  pduDLC = 0
  usedBits = 0
  for line in inputfile:

    for prog_arg in range(1,len(sys.argv)):
      #id is passed as an argument to the program, must be converted to integer, 
      #that's how it's stored in the .dbc
      id = int(sys.argv[prog_arg],16)
      
      #if line starts with BO_ it is a PDU
      if (line.startswith("BO_ "+str(id))) and detailReadMode == 0: 
        #because we are interested in the SG_ lines that follow after a BO_ line,
        #we set detailReadMode for this signal ID
        detailReadMode = id
        #Create a PDU with the following details
        x = line.split() 
        pduDLC = int(x[3],16)
        PDUs.append(PDU(                #Create a new PDU with the following 
          int(x[1],10),                   #PDU ID
          x[2],                           #PDU Name
          pduDLC,                         #PDU Size in Bytes
          x[4]))                          #PDU ECU

        continue #to next line, skip the code which would test this BO_ line if it is an SG_ and obviously fail.

        
      #if SG_ is in line and we're processing for set ID
      if ("SG_" in line) and detailReadMode==id:
        #Save this signal in the relevant PDU
        x = line.split() 
        bitLen = int(x[3].split('|')[1].split('@')[0],10)
        usedBits = usedBits + bitLen
        getPDUByCanID(detailReadMode,PDUs).append_sg(#Append Signal to PDU with ID matched to current parsing ID
          Signal(detailReadMode,                        #Signal's Container PDU CAN ID
          x[1],                                         #Signal Name
          int(x[3].split('|')[0],10),                   #Start Bit
          bitLen,                                       #Length in bits
          x[3].split('|')[1].split('@')[1],             #Format
          x[4].split(',')[0].strip('('),                #Scale
          x[4].split(',')[1].strip(')'),                #Offset
          x[5].split('|')[0].strip('['),                #Minimum Value
          x[5].split('|')[1].strip(']'),                #Maximum Value
          x[6].strip('"'),                             #Unit of Measurement
          pduDLC))                                       #Parent PDU DLC
       

      elif not any(substr in line for substr in ["SG_","BO_"]):
        #cancel detailed read mode, this line is not relevant, relevant lines have passed.
        if detailReadMode > 0:
          getPDUByCanID(detailReadMode,PDUs).setUsedBits(usedBits)
        detailReadMode = 0
        pduDLC = 0
        usedBits = 0


  inputfile.close()

#At this point we have collected all relevant signals in the PDUs passed as arguments
  

  try:
    while True:
      message = bus.recv(2.0) #gets CAN messages, parameter is timeout in seconds ( bus.recv implements blocking read)
      if message is None:
        screen.addstr(0,0,"Timeout receiving messages, check interface",curses.color_pair(2))
        screen.refresh()
      else:
        screen.addstr(0,0,"Receiving messages:                        ",curses.color_pair(1))
        for x in range(1,len(sys.argv)):
          id = int(sys.argv[x],16)
          #the filters on messages shown are the launch arguments
          if message.arbitration_id == id:            
            foundPDU = getPDUByCanID(id,PDUs)
            foundPDU.processAndSendAllPDUSignals(message.data,screen)
            foundPDU.sendCfgPackage()
            foundPDU.addCANMsg(x,message,screen)
          if x == len(sys.argv)-1:
            screen.refresh()
            #screen.addstr(len(sys.argv) + 1,0,"                              ",curses.color_pair(1))
  except Exception as e:
    print("\nExecution failure")
    curses.endwin()
    print(e)
    traceback.print_exc()


wrapper(main)
