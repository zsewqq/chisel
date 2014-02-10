package Chisel

class ioQueueFame1[T <: Data](data: T) extends Bundle
{
	val host_valid = Bool(OUTPUT)
	val host_ready = Bool(INPUT)
	val target = new DecoupledIO(data)
}

class QueueFame1[T <: Data] (val entries: Int)(data: => T) extends Module
{
	val io = new Bundle{
		val deq = new ioQueueFame1(data)
		val enq = new ioQueueFame1(data).flip()
		val tracker_reg0 = UInt(OUTPUT, log2Up(entries))
    	val tracker_reg1 = UInt(OUTPUT, log2Up(entries))
    	val tracker_reg2 = UInt(OUTPUT, log2Up(entries))
    	val tracker_reg3 = UInt(OUTPUT, log2Up(entries))
	}
	val was_reset = Reg(init = Bool(true))
	was_reset := Bool(false)
	
	val target_queue = new Queue(data, entries)
	val tracker = Module(new Fame1QueueTracker(entries, entries))
	
	target_queue.io.enq.valid := io.enq.host_valid && io.enq.target.valid
	target_queue.io.enq.bits := io.enq.target.bits
	io.enq.target.ready := target_queue.io.enq.ready
	
	io.deq.target.valid := tracker.io.entry_avail && target_queue.io.deq.valid
	io.deq.target.bits := target_queue.io.deq.bits
	target_queue.io.deq.ready := io.deq.host_ready && io.deq.target.ready && tracker.io.entry_avail
	
	tracker.io.tgt_queue_count := target_queue.io.count
	tracker.io.produce := io.enq.host_valid && io.enq.host_ready
	tracker.io.consume := io.deq.host_valid && io.deq.host_ready
	tracker.io.tgt_enq := target_queue.io.enq.valid && target_queue.io.enq.ready
	tracker.io.tgt_deq := io.deq.target.valid && target_queue.io.deq.ready
	when(was_reset){
		tracker.io.produce := Bool(true)
		tracker.io.consume := Bool(false)
		tracker.io.tgt_enq := Bool(false)
	}
	io.enq.host_ready := !tracker.io.full && target_queue.io.enq.ready 
	io.deq.host_valid := !tracker.io.empty
	
	//debug
	io.tracker_reg0 := tracker.io.reg0
	io.tracker_reg1 := tracker.io.reg1
	io.tracker_reg2 := tracker.io.reg2
	io.tracker_reg3 := tracker.io.reg3
}

class ioFame1QueueTracker() extends Bundle{
	val tgt_queue_count = UInt(INPUT)
	val produce = Bool(INPUT)
	val consume = Bool(INPUT)
	val tgt_enq = Bool(INPUT)
	val tgt_deq = Bool(INPUT)
	val empty = Bool(OUTPUT)
	val full = Bool(OUTPUT)
	val entry_avail = Bool(OUTPUT)
	val reg0 = UInt(OUTPUT)
	val reg1 = UInt(OUTPUT)
	val reg2 = UInt(OUTPUT)
	val reg3 = UInt(OUTPUT)
}

class Fame1QueueTracker(num_tgt_entries: Int, num_tgt_cycles: Int) extends Module{
	val io = new ioFame1QueueTracker()
	val aregs = Vec.fill(num_tgt_cycles){ Reg(init = UInt(0, width = log2Up(num_tgt_entries))) }
	val tail_pointer = Reg(init = UInt(0, width = log2Up(num_tgt_cycles)))
	//debug
	io.reg0 := aregs(0)
	io.reg1 := aregs(1)
	io.reg2 := aregs(2)
	io.reg3 := aregs(3)
	
	val next_tail_pointer = UInt()
	tail_pointer := next_tail_pointer
	next_tail_pointer := tail_pointer
	when(io.produce && !io.consume){
		next_tail_pointer := tail_pointer + UInt(1)
	}.elsewhen(!io.produce && io.consume){
		next_tail_pointer := tail_pointer - UInt(1)
	}
	for (i <- 1 until num_tgt_cycles - 1){
		val next_reg_val = UInt()
		aregs(i) := next_reg_val
		next_reg_val := aregs(i)
		when(UInt(i) === tail_pointer){
			when(io.produce && io.tgt_enq && !io.consume){
				next_reg_val := aregs(i - 1) + UInt(1)
			}.elsewhen(io.produce && !io.tgt_enq && !io.consume){
				next_reg_val := aregs(i - 1)
			}
		}.elsewhen(UInt(i) === tail_pointer - UInt(1)){
			when(io.produce && io.tgt_enq && io.consume && io.tgt_deq){
			}.elsewhen(io.produce && io.tgt_enq && io.consume && !io.tgt_deq){
				next_reg_val := aregs(i) + UInt(1)
			}.elsewhen(io.produce && !io.tgt_enq && io.consume && io.tgt_deq){
				next_reg_val := aregs(i) - UInt(1)
			}
		}.otherwise{
			when(io.produce && io.tgt_enq && io.consume && io.tgt_deq){
				next_reg_val := aregs(i + 1) - UInt(1)
			}.elsewhen(io.produce && io.tgt_enq && io.consume && !io.tgt_deq){
				next_reg_val := aregs(i + 1)
			}.elsewhen(io.produce && !io.tgt_enq && io.consume && io.tgt_deq){
				next_reg_val := aregs(i + 1) - UInt(1)
			}.elsewhen(io.produce && !io.tgt_enq && io.consume && !io.tgt_deq){
				next_reg_val := aregs(i + 1)
			}.elsewhen(!io.produce && io.consume && io.tgt_deq){
				next_reg_val := aregs(i + 1) - UInt(1)
			}.elsewhen(!io.produce && io.consume && !io.tgt_deq){
				next_reg_val := aregs(i + 1)
			}
		}
	}
	val next_reg_val0 = UInt()
	aregs(0) := next_reg_val0
	next_reg_val0 := aregs(0)
	when(UInt(0) === tail_pointer){
		when(io.produce && io.tgt_enq && !io.consume){
			next_reg_val0 := io.tgt_queue_count + UInt(1)
		}.elsewhen(io.produce && !io.tgt_enq && io.consume && io.tgt_deq){
		}.elsewhen(io.produce && !io.tgt_enq && io.consume && !io.tgt_deq){
		}.elsewhen(io.produce && !io.tgt_enq && !io.consume){
			next_reg_val0 := io.tgt_queue_count
		}
	}.elsewhen(UInt(0) === tail_pointer - UInt(1)){
		when(io.produce && io.tgt_enq && io.consume && !io.tgt_deq){
			next_reg_val0 := aregs(0) + UInt(1)
		}.elsewhen(io.produce && !io.tgt_enq && io.consume && io.tgt_deq){
			next_reg_val0 := aregs(0) - UInt(1)
		}.elsewhen(io.produce && !io.tgt_enq && io.consume && !io.tgt_deq){
		}
	}.otherwise{
		when(io.produce && io.tgt_enq && io.consume && io.tgt_deq){
			next_reg_val0 := aregs(1) - UInt(1)
		}.elsewhen(io.produce && io.tgt_enq && io.consume && !io.tgt_deq){
			next_reg_val0 := aregs(1)
		}.elsewhen(io.produce && !io.tgt_enq && io.consume && io.tgt_deq){
			next_reg_val0 := aregs(1) - UInt(1)
		}.elsewhen(io.produce && !io.tgt_enq && io.consume && !io.tgt_deq){
			next_reg_val0 := aregs(1)
		}.elsewhen(!io.produce && io.consume && io.tgt_deq){
			next_reg_val0 := aregs(1) - UInt(1)
		}.elsewhen(!io.produce && io.consume && !io.tgt_deq){
			next_reg_val0 := aregs(1)
		}
	}
	val next_reg_val_last = UInt()
	aregs(num_tgt_cycles - 1) := next_reg_val_last
	next_reg_val_last := aregs(num_tgt_cycles - 1)
	when(UInt(num_tgt_cycles - 1) === tail_pointer){
		when(io.produce && io.tgt_enq && io.consume && !io.tgt_deq){
		}.elsewhen(io.produce && io.tgt_enq && !io.consume){
			next_reg_val_last := aregs(num_tgt_cycles - 1 - 1) + UInt(1)
		}.elsewhen(io.produce && !io.tgt_enq && !io.consume){
			next_reg_val_last := aregs(num_tgt_cycles - 1 - 1)
		}
	}.elsewhen(UInt(num_tgt_cycles - 1) === tail_pointer - UInt(1)){
		when(io.produce && io.tgt_enq && io.consume && !io.tgt_deq){
			next_reg_val_last := aregs(num_tgt_cycles - 1) + UInt(1)
		}.elsewhen(io.produce && !io.tgt_enq && io.consume && io.tgt_deq){
			next_reg_val_last := aregs(num_tgt_cycles - 1) - UInt(1)
		}.elsewhen(io.produce && !io.tgt_enq && io.consume && !io.tgt_deq){
		}
	}
	io.full := tail_pointer === UInt(num_tgt_cycles)
	io.empty := tail_pointer === UInt(0)
	io.entry_avail := aregs(0) != UInt(0)
}