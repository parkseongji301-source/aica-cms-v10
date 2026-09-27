import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
public class GracefulStop {
 public static void agentmain(String args,Instrumentation instrumentation){new Thread(()->System.exit(0),"checkpoint-shutdown").start();}
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(args[1]);}finally{vm.detach();}}
}
