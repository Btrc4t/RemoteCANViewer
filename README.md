# RemoteCANViewer

This repository is a collection of multiple tools:

  - A python script which decodes DBC files (DBC is a descriptor for CAN networks) and sends CAN signals through IPv4 UDP, from a socketcan-enabled interface on a linux machine (Originally developed for Raspberry Pi with the PiCan2 adapter)
  - A Java 1.8 tool using JavaFX for the GUI which shows received signals
  - An example of a DBC file plus an example of an "ECU Node" running on Arduino (used just to showcase the project)


### Todos
 - Add documentation
 - Use arguments in the python scripts for IP and other possible inputs
 - Optimize python script, perhaps with ZeroMQ (there's a lot done without performance in mind)
 - Add parsers for other database descriptors
 - Find a better use case for it

License
----

This was my bachelor's degree project and I have not touched it in years, so it has plenty of shortcomings
If you do want to improve it, you're free to do that without any limitation


**Free Software, Hell Yeah!**
