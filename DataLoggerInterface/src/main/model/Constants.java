package main.model;
/*
 * This class contains constants that are common across platforms.  Only
 * primitive types should be used here to avoid ambiguity.
 *
 * It follows the Singleton design pattern and takes advantage of the 
 * properties of the Java Virtual Machine such that initialization of the
 * class instance will be done in a thread safe manner.
 */

public class Constants {   
    
	
	private Constants() {}
    
    private static class LazyHolder {
        private static final Constants INSTANCE = new Constants();
    }
    
    public static Constants instance() {
        return LazyHolder.INSTANCE;
    }
    /*
     * Constants
     */
    
    
    /* 
     *  IPv4 and IPv6 define a minimum reassembly buffer size, the minimum datagram size that we are guaranteed any implementation must support.
     *   For IPv4, this is 576 bytes. IPv6 raises this to 1,500 bytes. 
     *   With IPv4, for example, we have no idea whether a given destination can accept a 577-byte datagram or not. 
     *   Therefore, many IPv4 applications that use UDP (e.g., DNS, RIP, TFTP, BOOTP, SNMP) prevent applications from generating 
     *   IP datagrams that exceed this size.
     *   The usable data of a 576byte IPv4 datagram is 508 bytes
     *   */
    double DEFAULT_REFRESH_RATE = 0.0002; /* Refresh rate that populates all the signal refresh rates at initialization */
    double DOUBLE_COMPARISON_NEAR0 = 0.000000000000002;
    int MAX_UDP_PKG_SIZE = 508;
    int DATAPOINT_SIZE = 5;
    int CFGPOINT_SIZE = 41;
    int TIMESTAMP_SIZE = 8;
    int PKGINFO_SIZE = 1;
    int SIG_ID_SIZE = 1;
    int MAX_PKG_DATAPOINTS = (MAX_UDP_PKG_SIZE - (2*TIMESTAMP_SIZE)-PKGINFO_SIZE) / DATAPOINT_SIZE;
    int MAX_CFG_DATAPOINTS = (MAX_UDP_PKG_SIZE - (2*TIMESTAMP_SIZE)) / CFGPOINT_SIZE;
    public int GRAPH_REFRESH_POINTS_TRIGGER = (int) 1000; // (miliseconds between linechart updates)
    
    String IPADDRESS_PATTERN ="^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"; /* Regex that matches IP's */
    String DEFAULT_ADDR ="127.0.0.1";
    String DEFAULT_PORT ="7878";
    String PORT_PATTERN = "^[0-9]{1,6}$"; /* Regex that matches Ports */
    public String DOUBLE_PATTERN="\\d*|\\d+\\.\\d*";/* Regex that matches floating point numbers */
    String SV_STARTED = "Status: Server Started";
    String SV_NOT_STARTED = "Status: Server Not Started";
    String SV_CONNECT = "Connect";
    String SV_DISCONNECT = "Disconnect";
	
	int CHART_RES_SLEEP_T = 500; /* Sleep time (ms) for the thread that checks if chart area should be resized - LineChartWatcher */
    int MAXCHARTWINDOW = 30; /* Max number of seconds visible on the chart at one time for any signal */
    double SAFETY_CHART_SPACE = DEFAULT_REFRESH_RATE*10000; /* Space to the right of the last datapoint, measured in datapoints */
    int CHART_DIVISIONS = 10; /* Time (X Axis) number of divisions shown */
    protected boolean SV_DEBUG_EN = false; /* if true, some additional info is shown in the console */
    protected boolean DEBUG_EN = false; /* if true, some additional info is shown in the console */
    public byte BOOL_T = 0;
	public byte INT_T = 1;
	public byte FLOAT_T = 2;
	public double BOOL_EPSILON = 0.90; /* The lower the number, the more accurate the displayed graph values of Boolean Signals */
	public double NORMAL_EPSILON = 0.90; /* The lower the number, the more accurate the displayed graph values of Integer and Float Signals */
}
