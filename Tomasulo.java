package mypackage;

import java.util.ArrayList;

/**
 * @author Huizhe
 * It now works well with four FP operations, daddi, and Load/Store instructions 
 */
public class Tomasulo {
	// constants
	public static final int n_stations = 7;
	
	public static final int LD = 0;		// l.d
	public static final int ST = 1;		// s.d
	public static final int ADDI = 2;		// daddi
	public static final int ADDD1 = 3;		// add.d / sub.d
	public static final int ADDD2 = 4;		// add.d / sub.d
	public static final int MULD1 = 5;		// mul.d / div.d
	public static final int MULD2 = 6;		// mul.d / div.d
	
	// components
	
	private Freg freg;
	private Reg reg;
	private Object[] mem;
	
	private Station[] stations;
	
	private CDB bus;
	
	// bookkeeping
	private Output output;
	
	private float CPI;
	
	private int clk_cycles;	// clock_cycls passed
	
	// constructor
 	public Tomasulo(){
		freg = new Freg();
		reg = new Reg();
		
		mem = new Object[1000];
		/*	cat's original version
		for (int i=0; i<1000; i++){
			if(i%3 == 0){
				mem[i] = i;
			}else if(i%3 ==1){
				mem[i] = (float)i/5;
			}else{
				Random rd = new Random();
				mem[i] = rd.nextFloat();
			}
		}*/
		
		// mark: pig
		for (int i = 0; i < 1000; i++){
			mem[i] = i;
		}
		
		bus = new CDB(reg, freg, mem);
		
		stations = new Station[n_stations];
		for(int i = 0; i < n_stations; i++){
			stations[i] = new Station(i, bus);
		}
		
		output = new Output();
		CPI = 0;
		clk_cycles = 0;
    }
	
	public void runCPUSimulator(ArrayList<ArrayList<String>> Op){
        boolean busy_station = false;	// are there any busy stations?
        bus.instruction_queue = Op;
        
        //for (int k = 0; k < 10; k++, clk_cycles++){
		for(; bus.PC < bus.instruction_queue.size() || busy_station; clk_cycles++){	// while more instruction coming or busy stations
			//System.out.println("PC: " + bus.PC + " === size: " + bus.instruction_queue.size());

			System.out.println("======= cycle " + (clk_cycles+1) + " begin =======");
			
			if (bus.PC < bus.instruction_queue.size()){
				bus.issued = false;
			}else{
				bus.issued = true;
			}
			
			// each station deals with this cycle
			for(int i = 0; i < n_stations; i++){
					stations[i].one_cycle();
			}
			
	
			// modify busy_station;
			busy_station = false;
			for(int i = 0; i < n_stations; i++){
				if (stations[i].is_busy()){
					busy_station = true;
					break;
				}
			}
			
			bus.new_cycle();
			
			System.out.println("======== cycle " + (clk_cycles+1) + " end ========");
		}
		
		clk_cycles--;
		CPI = (float)clk_cycles / bus.instruction_queue.size(); 
		
		bus.freg.printFreg();
		System.out.println("mem[150]: " + Float.parseFloat(String.valueOf(bus.mem[150])));
		System.out.println("average CPI: " + CPI);
	}
	
	public static void main(String[] args) {
        //标准指令集的位置，eg：instruction set/MIPS64.txt
        String instructionSetFile = "Input/MIPS64.txt";
        //测试指令集的位置，eg：instructions/test.txt
        String instructionsFile = "Input/test.txt";
        Input input = new Input(instructionSetFile, instructionsFile);
        ArrayList<ArrayList<String>> instructions = input.getInstrs();
        
        Tomasulo test = new Tomasulo();
        test.runCPUSimulator(instructions);

	}
}



//reservation station
class Station{
	
	// operations
	public boolean is_busy(){return Busy;}
	
	public void one_cycle(){

		// if this station is idle and no instruction is already issued in this cycle, 
		// then try to get an instruction issued here
		if ( (!Busy) && (!bus.issued)){		
			ArrayList<String> next_instruction = bus.instruction_queue.get(bus.PC);
			String opcode = next_instruction.get(0);
			if (is_match(opcode)){	// issue!
				issue(next_instruction);
			}
		}else if (Busy && stage.equals("ISSUE")){	// waiting on operands or going to
			stage_issue();
		}else if (Busy && stage.equals("EX")){	// executing or going to
			stage_ex();
		}else if (Busy && stage.equals("WB")){	// going to writing back
			stage_wb();
		}
	}
	

	// constants
	public static final int READY = 100; 
	
	// tags
	
	private String Op;	// the operation to perform on source operands S1 and S2
	private int Qj;		// station id that will produce source operand S1, READY indicating ready to use
	private int Qk;		// station id that will produce source operand S2, READY indicating ready to use
	private float Vj;	// the value of source operand S1
	private float Vk;	// the value of source operand S2
	private int A;		// Initially the immediate field, after calculation the effective address
	
	private boolean Busy;
	
	// the data bus
	private CDB bus;
	
	// internal use
	private String stage;		// in which of the four stages (ISSUE, EX, WB) is the station
	private int id;				// its id
	private int cycles_left;
	private float result;
	
	// constructor
	public Station(int station_id, CDB cdb){
		id = station_id;
		bus = cdb;	// connect the station to the common data bus
		
		Op = null;
		Qj = Qk = READY;
		Vj = Vk = 0;
		A = -1;		//used by the bus to determine whether it is a s.d instruction
		Busy = false;
		stage = "IDLE";
		cycles_left = 0;
		result = 0;
	}
	
	// check if and opcode matches the function of this station
	
	private boolean is_match(String opcode){	
		boolean match = false;
		if (opcode.equals("add.d") || opcode.equals("sub.d")){
			if (id == Tomasulo.ADDD1 || id == Tomasulo.ADDD2)
				match = true;
		}else if (opcode.equals("mul.d") || opcode.equals("div.d")){
			if (id == Tomasulo.MULD1 || id == Tomasulo.MULD2)
				match = true;
		}else if (opcode.equals("daddi")){
			if (id == Tomasulo.ADDI)
				match = true;
		}else if (opcode.equals("s.d")){
			if (id == Tomasulo.ST)
				match = true;
		}else if (opcode.equals("l.d")){
			if (id == Tomasulo.LD)
				match = true;
		}else {
			System.out.println("== Station.is_match == this should not happen");
		}
		
		return match;
	}
	

	private void issue(ArrayList<String> instruction){
		System.out.println("station " + id + " being issued with one instruction.");
		bus.issued = true;
		bus.PC ++;
		
		Busy = true;
		stage = "ISSUE";
		
		Op = instruction.get(0);
		
		if ( (Op.equals("add.d")) || (Op.equals("sub.d")) || (Op.equals("mul.d")) || (Op.equals("div.d")) ){
			if ( (Op.equals("add.d")) || Op.equals("sub.d")){
				cycles_left = 3;
			}else
				cycles_left = 6;
			
			// check operand 1
			int index = Integer.parseInt(instruction.get(4));
			int depend = bus.freg.getFregFlag(index);
			if (depend == READY){	// operand 1 ready
				Qj = READY;
				Vj = bus.freg.getFreg(index);
			}else{										// operand 1 depends on another station
				// check bus
				Message msg = bus.search_station(depend);
				if( msg!=null ){
					Qj = READY;
					Vj = msg.result;
				}else
					Qj = depend;	// wait on that station;
			}
			
			// check operand 2
			index = Integer.parseInt(instruction.get(6));
			depend = bus.freg.getFregFlag(index);
			if (depend == READY){	// operand 1 ready
				Qk = READY;
				Vk = bus.freg.getFreg(index);
			}else{										// operand 1 depends on another station
				// check bus
				Message msg = bus.search_station(depend);
				if( msg!=null ){
					Qk = READY;
					Vk = msg.result;
				}else
					Qk = depend;	// wait on that station;
			}
			
			// if they are ready, enter stage EX
			if ( (Qj == READY) && (Qk == READY)){	// both operands ready
				stage = "EX";
			}

			// set register dependency flag
			int reg_dst = Integer.parseInt(instruction.get(2));
			bus.freg.setFregFlag(reg_dst, id);
			System.out.println("station " + id + ": F" + reg_dst + " now depends on it.");
			
		}else if (Op.equals("daddi")){
			cycles_left = 2;	// how many?
				
			// check operand 1
			int index = Integer.parseInt(instruction.get(4));
			int depend = bus.reg.getRegFlag(index);
			if (depend == READY){	// operand 1 ready
				Qj = READY;
				Vj = bus.reg.getReg(index);
			}else{										// operand 1 depends on another station
				// check bus
				Message msg = bus.search_station(depend);
				if( msg!=null ){
					Qj = READY;
					Vj = msg.result;
				}else
					Qj = depend;	// wait on that station;
			}
			
			// operand 2 always ready
			Qk = READY;
			Vk = Integer.parseInt(instruction.get(6));
			
			
			// if they are ready, enter stage EX
			if ( (Qj == READY) && (Qk == READY)){	// both operands ready
				stage = "EX";
			}

			// set register dependency flag
			int reg_dst = Integer.parseInt(instruction.get(2));
			bus.reg.setRegFlag(reg_dst, id);
			System.out.println("station " + id + ": R" + reg_dst + " now depends on it.");	
			
		}else if (Op.equals("s.d")){
			cycles_left = 1;
			
			// check operand 1
			
			int index = Integer.parseInt(instruction.get(2));
			int depend = bus.freg.getFregFlag(index);
			if (depend == READY){	// operand ready
				Qj = READY;
				Vj = bus.freg.getFreg(index);
			}else{					// operand depends on another station
				// check bus
				Message msg = bus.search_station(depend);
				if( msg!=null ){
					Qj = READY;
					Vj = msg.result;
				}else
					Qj = depend;	// wait on that station;
			}	
			
			// check operand 2
			
			index = Integer.parseInt(instruction.get(6));
			depend = bus.reg.getRegFlag(index);
			if (depend == READY){	// operand 1 ready
				Qk = READY;
				Vk = bus.reg.getReg(index);
			}else{										// operand 1 depends on another station
				// check bus
				Message msg = bus.search_station(depend);
				if( msg!=null ){
					Qk = READY;
					Vk = msg.result;
				}else
					Qk = depend;	// wait on that station;
			}
			
			// if they are ready, enter stage EX
			if ( (Qj == READY) && (Qk == READY)){	// both operands ready
				stage = "EX";
			}
			
			// no registers' value depend on s.d instruction
			// but we have to calculation A, into which memory unit the bus stores result
			// second part of A comes from Rx, which will accumulated to A in stage EX
			A = Integer.parseInt(instruction.get(4));	
			
		}else if (Op.equals("l.d")){
			cycles_left = 1;
			
			// check operand 1
			Qj = READY;
			Vj = Integer.parseInt(instruction.get(4));
			
			// check operand 2
			int index = Integer.parseInt(instruction.get(6));
			int depend = bus.reg.getRegFlag(index);
			if (depend == READY){	// operand 2 ready
				Qk = READY;
				Vk = bus.reg.getReg(index);
			}else{					// operand 2 depends on another station
				// check bus
				Message msg = bus.search_station(depend);
				if( msg!=null ){
					Qk = READY;
					Vk = msg.result;
				}else
					Qk = depend;	// wait on that station;
			}			
			
			// if they are ready, enter stage EX
			if ( (Qj == READY) && (Qk == READY)){	// both operands ready
				stage = "EX";
			}
			
			// set register dependency flag
			int reg_dst = Integer.parseInt(instruction.get(2));
			bus.freg.setFregFlag(reg_dst, id);
			System.out.println("station " + id + ": F" + reg_dst + " now depends on it.");
						
		}else {
			System.out.println("== Station.is_match == this should not happen");
		}
	}
	
	
	private void stage_issue(){
		System.out.println("station " + id + " in ISSUE.");
			
			// check operand 1
			Message msg;
			if (Qj != READY){
				msg = bus.search_station(Qj);
				if (msg!=null){		// found
					Qj = READY;
					Vj = msg.result;
				}
			}// endif
			
			// check operand 2
			if (Qk != READY){
				msg = bus.search_station(Qk);
				if (msg!=null){		// found
					Qk = READY;
					Vk = msg.result;
				}
			}// endif
			
			// if both operands ready, do execution
			if ( (Qj == READY) && (Qk == READY)){
				stage = "EX";
				stage_ex();
			}
	}

	private void stage_ex(){
		System.out.println("station " + id + " in EX, with " + cycles_left + " cycles left.");
		cycles_left--;
		if (cycles_left == 0) {
			stage = "WB";
			
			// calculate
			if (Op.equals("add.d")){
				result = Vj + Vk;
			}else if (Op.equals("sub.d")){
				result = Vj - Vk;
			}else if (Op.equals("mul.d")){
				result = Vj * Vk;
			}else if (Op.equals("div.d")){
				result = Vj / Vk;
			}else if (Op.equals("l.d")){
				int tmp = (int)Vj + bus.reg.getReg((int)Vk);	// not used by bus
				result = Float.parseFloat(String.valueOf(bus.mem[tmp]));
			}else if (Op.equals("s.d")){
				// store result into address A
				result = Vj;
				A += bus.reg.getReg((int)Vk);
			}else if (Op.equals("daddi")){
				result = Vj + Vk;
			}
			
		}
	}

	private void stage_wb(){
		System.out.println("station " + id + " in WB");
		
		// send message to bus
		Message msg = new Message(id, result, A, false);
		bus.receive_msg(msg);
		
		stage = "IDLE";
		Busy = false;
		
		
	}
}



// common data bus 
class CDB{
	public Reg reg;
	public Freg freg;
	public Object[] mem;
	
	ArrayList<ArrayList<String>> instruction_queue;
	
	private ArrayList<Message> msgs;
	
	public boolean issued;	// whether an instruction is issued in this cycle;
	
	public int PC;
	
	public CDB(Reg r, Freg f, Object[] m){
		reg = r;
		freg = f;
		mem = m;
		
		msgs = new ArrayList<Message>();
		instruction_queue = null;	// will be initialized in runCPUsimulator
		issued = false;
		PC = 0;
	}
	
	public Message search_station(int station_id){
		Message msg;
		for (int i = 0; i < msgs.size(); i++){
			msg = msgs.get(i);
			if ( (msg.station_id == station_id) && msg.valid){	// found
				return msg;
			}
		}
		return null;
	}
	
	public void receive_msg(Message msg){
		msgs.add(msg);
	}
	
	public void new_cycle(){
		Message msg;
		for(int i = 0; i < msgs.size(); i++){
			msg = msgs.get(i);
			if (msg.valid){		// valid msg, remove it
				msgs.remove(i);i--;
			}
			else{				// invalid msg, make it valid and do store operation
				if (msg.addr != -1){				// a s.d instruction
					System.out.println("Storing " + msg.result + " into mem[" + msg.addr + "]");
					mem[msg.addr] = msg.result; 
				}else {								// not a s.d instruction
					for(int j = 0; j < 32; j++){
						if (msg.addr == -1) {	// not a s.d instruction
							if ( freg.getFregFlag(j) == msg.station_id ){	// write register
								System.out.println("F" + j + " is being written");
								freg.setFregFlag(j, Station.READY);
								freg.setFreg(j, msg.result);
								break;
							}
							if ( reg.getRegFlag(j) == msg.station_id ){		// write register
							System.out.println("R" + j + " is being written");
								reg.setRegFlag(j, Station.READY);
								reg.setReg(j, (int)msg.result);
								break;
							}
						}
					}
				}
				msg.valid = true;
			}
		}
	}
}



// sent by a station after EX stage to the CDB
class Message{
	public int station_id;	// who has sent this message
	public float result;	// the result
	public boolean valid;	// the message is invalid until one cycle after it's sent
	public int addr;		// address to which a value should be stored, only used by s.d instruction
	public Message(int id, float rs, int a, boolean val){
		station_id = id;
		result = rs;
		valid = val;
		addr = a;
	}
}











































class Reg{
	public Reg(){
		regReal = new int[32];
		regFlag = new int[32];
		for (int i=0; i<32; i++){
			regReal[i] = i; 			// mark: pig
			regFlag[i] = Station.READY;	// mark: pig
		}	
	}
	
	public int[] getReg(){
		return regReal;
	}
	
	public int getReg(int i){
		return regReal[i];
	}
	
	public int[] getRegFlag(){
		return regFlag;
	}
	
	public int getRegFlag(int i){
		return regFlag[i];
	}
	
	public void setReg(int[] reg){
		for(int i=0; i<32; i++){
			regReal[i] = reg[i];
		}
	}
	
	public void setReg(int i, int reg){
		regReal[i] = reg;
	}
	
	public void setRegFlag(int[] newregFlag){
		for(int i=0; i<32; i++){
			regFlag[i] = newregFlag[i];
		}
	}
	
	public void setRegFlag(int i, int newregFlag){
		regFlag[i] = newregFlag;
	}
    
    //v1.1, 加了一个打印reg的方法
    public void printReg(){
		String toPrint = "";
		for(int i=0; i<32; i++){
			toPrint = toPrint + "R" + i + ":" + regReal[i] + "   ";
		}
		System.out.println(toPrint);
	}
	
	//the content of the regs
	private int[] regReal;
	//to show whether a reg is busy or not, 0 stands for not busy
	private int[] regFlag;
}

class Freg{
	public Freg(){
		fregReal = new float[32];
		fregFlag = new int[32];
		for (int i=0; i<32; i++){
			fregReal[i] = i; 				// mark: pig
			fregFlag[i] = Station.READY;	// mark: pig
		}	
	}
	
	public float[] getFreg(){
		return fregReal;
	}
	
	public float getFreg(int i){
		return fregReal[i];
	}
	
	public int[] getFregFlag(){
		return fregFlag;
	}
	
	public int getFregFlag(int i){
		return fregFlag[i];
	}
	
	public void setFreg(float[] freg){
		for(int i=0; i<32; i++){
			fregReal[i] = freg[i];
		}
	}
	
	public void setFreg(int i, float freg){
		fregReal[i] = freg;
	}
	
	public void setFregFlag(int[] newfregFlag){
		for(int i=0; i<32; i++){
			fregFlag[i] = newfregFlag[i];
		}
	}
	
	public void setFregFlag(int i, int newfregFlag){
		fregFlag[i] = newfregFlag;
	}
    
    //v1.1, 加了一个打印freg的方法
    public void printFreg(){
		String toPrint = "";
		for(int i=0; i<32; i++){
			toPrint = toPrint + "F" + i + ":" + fregReal[i] + "   ";
		}
		System.out.println(toPrint);
	}
	
	//the content of the fregs
	private float[] fregReal;
	//to show whether a freg is busy or not, 0 stands for not busy
	private int[] fregFlag;
}

