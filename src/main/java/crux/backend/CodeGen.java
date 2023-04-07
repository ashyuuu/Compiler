package crux.backend;

import crux.ast.SymbolTable.Symbol;
import crux.ir.*;
import crux.ir.insts.*;
import crux.printing.IRValueFormatter;

import java.util.*;

/**
 * Convert the CFG into Assembly Instructions
 */
public final class CodeGen extends InstVisitor {
  private final Program p;
  private final CodePrinter out;
  private HashMap<Variable, Integer> varIndexMap;
  private HashMap<Instruction, String> labelMap;
  private int[] varIndex = new int[1];
  private int slots;
  private final IRValueFormatter irFormat = new IRValueFormatter();
  private int numLocalVar = 1;
  private void printInstructionInfo(Instruction i){
    var info = String.format("/* %s */", i.format(irFormat));
    out.printCode(info);
  }

  public CodeGen(Program p) {
    this.p = p;
    // Do not change the file name that is outputted or it will
    // break the grader!
    out = new CodePrinter("a.s");
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  public void genCode() { // DONE
    // for each global var, allocate them on stack using ".comm VarName, Size, 8"
    for (Iterator<GlobalDecl> glob_it = p.getGlobals(); glob_it.hasNext();){
      GlobalDecl g = glob_it.next();
      String name = g.getSymbol().getName();
      var size = ((IntegerConstant) g.getNumElement()).getValue() * 8;
      // code here
      out.printCode(".comm " + name + ", " + size + ", 8");
    }
    // for each function, prologue, arguments on stack, linearize body
    int count[] = new int[1];
    for (Iterator<Function> func_it = p.getFunctions(); func_it.hasNext();){
      Function f = func_it.next();
      genCode(f, count);
    }
    out.close();
  }

  private void genCode(Function f, int count []){
    // Assign labels to jump targets
    varIndexMap = new HashMap<>();
    varIndex[0] = 1;
    numLocalVar = 1;
    // declare function and label
    // public void visit(CallInst)

    // print prologue such that stack is 16 bytes alligned
    // create empty slot when its not a multiple of 2
    // numslots = # of temp var used in function
    // function::getNumTempVars
    slots = f.getNumTempVars() + f.getNumTempAddressVars();
    if(slots % 2 != 0){
      slots++;           //if not even
    }

    // keep track of which variable at which slot using varIndex & varIndexMap
    // out.printCode("enter $(8 * " + numslots + "), $0");
    out.printCode(".globl "+f.getName());
    out.printLabel(f.getName()+":");
    out.printCode("enter $(8 * " + slots + "), $0");
    // move arguments from registers and stack to local variables
    labelMap = f.assignLabels(count);
    List<LocalVar> args = f.getArguments();
    int s = args.size()-6;
    if (s%2==1)
      s++;
    for (int i = 0; i < args.size(); i++) {
      if (i == 0) {
        out.printCode("movq %rdi, " + helper(args.get(i)) + "(%rbp)");
      } else if (i == 1) {
        out.printCode("movq %rsi, " + helper(args.get(i)) + "(%rbp)");
      } else if (i == 2) {
        out.printCode("movq %rdx, " + helper(args.get(i)) + "(%rbp)");
      } else if (i == 3) {
        out.printCode("movq %rcx, " + helper(args.get(i)) + "(%rbp)");
      } else if (i == 4) {
        out.printCode("movq %r8, " + helper(args.get(i)) + "(%rbp)");
      } else if (i == 5) {
        out.printCode("movq %r9, " + helper(args.get(i)) + "(%rbp)");
      } else {
        if (i == 6){
          s-=1;
          out.printCode("subq $" + (s*8) + ", %rsp");
        }
        out.printCode("movq " + (16+(args.size()-i-1)*8) + "(%rbp), %r10");
        out.printCode("movq %r10, "+helper(args.get(i))+"(%rbp)");
        out.printCode("addq $8, %rsp");
        if (i==args.size()-1&&i%2==0)
          out.printCode("addq $8, %rsp");
      }
    }
//    if (s%2==1)
//      out.printCode("addq $8, %rsp");
//    for (int j = 6; j < args.size(); j++){
//      out.printCode("movq " + (16+(j-6)*8) + "(%rbp), %r10");
//      out.printCode("movq %r10, "+helper(args.get(j))+"(%rbp)");
//      out.printCode("addq $8, %rsp");
//    }
    Stack<Instruction> toVisit = new Stack<>();
    HashSet<Instruction> visited = new HashSet<>();
    toVisit.push(f.getStart());
    // generate code for function body
    // linearize cfg using jumps and labels
    // use dfs traversal
    while (!toVisit.isEmpty()){
      Instruction first = toVisit.pop();
      if (labelMap.containsKey(first))
        out.printLabel(labelMap.get(first)+":");
      first.accept(this);
      Instruction next = first.getNext(0);
      Instruction nextN = first.getNext(1);
      if (nextN != null && !visited.contains(nextN)){
        visited.add(nextN);
        toVisit.push(nextN);
      }
      if (next != null && !visited.contains(next)){
        visited.add(next);
        toVisit.push(next);
      } else if (next != null && (toVisit.isEmpty()||next!=toVisit.peek())){
        out.printCode("jmp "+labelMap.get(next));
      } else{
        // epilogue
        out.printCode("leave");
        out.printCode("ret");
      }
    }
  }

  private int helper(Variable v){
    int output = 0;
//    for (Variable va : varIndexMap.keySet()){
//      out.printCode("/* InLoop */ ");
//      if (va.getName().equals(v.getName())){
//        out.printCode("/* In */ ");
//        return varIndexMap.get(v)*-8;
//      }
//      out.printCode("/* SuccessIncr */ ");
//    }
//    out.printCode("/* Out */ ");
//    varIndexMap.put(v, numLocalVar);
//    output = numLocalVar*-8;
//    numLocalVar++;
//    return output;
//    out.printCode("/*  */");
//    for (Variable va : varIndexMap.keySet()){
//      out.printCode(va.getName());
//    }
//    out.printCode(v.getName());
    if(varIndexMap.containsKey(v)){
//      out.printCode("/* In */ ");
      return varIndexMap.get(v)*-8;
    }
    else{
//      out.printCode("/* Else */ ");
      varIndexMap.put(v, numLocalVar);
      output = numLocalVar*-8;
      numLocalVar++;
    }
    return output;
  }

  public void visit(AddressAt i) {
    out.printCode("/* AddressAt */ ");

//    printInstructionInfo(i);
//    int offset = helper(i.getOffset());
    // if offset is null: movq VarName@GOTPCREL(%rip), %r11

    if (i.getOffset() == null){
      out.printCode("movq " + i.getBase().getName() + "@GOTPCREL(%rip), %r11");
      out.printCode("movq %r11, " + helper(i.getDst()) + "(%rbp)");
    } else {
      // else imulq $8, %r10
      //    addq %r10, %r11
      out.printCode("movq " + helper(i.getOffset()) + "(%rbp), %r11");
      out.printCode("imulq $8, %r11");
      out.printCode("movq " + i.getBase().getName() + "@GOTPCREL(%rip), %r10"); //
      out.printCode("addq %r10, %r11");
      out.printCode("movq %r11, " + helper(i.getDst()) + "(%rbp)");
    }
  }

  public void visit(BinaryOperator i) {
    out.printCode("/* BinaryOp */ ");
//    printInstructionInfo(i);
    String op = i.getOperator().toString();
    if (op.equals("Div")){
      out.printCode("movq "+ helper(i.getLeftOperand()) +"(%rbp), %rax");
      out.printCode("cqto");
      out.printCode("idivq "+ helper(i.getRightOperand()) +"(%rbp)");
      out.printCode("movq %rax, "+ helper(i.getDst())+"(%rbp)");
      return;
    }
    out.printCode("movq "+ helper(i.getLeftOperand()) +"(%rbp), %r10");
    if (op.equals("Add")){
      out.printCode("addq "+ helper(i.getRightOperand()) +"(%rbp), %r10");
    } else if (op.equals("Sub")){
      out.printCode("subq "+ helper(i.getRightOperand()) +"(%rbp), %r10");
    } else if (op.equals("Mul")){
      out.printCode("imulq "+ helper(i.getRightOperand()) +"(%rbp), %r10");
    }
    out.printCode("movq %r10, "+ helper(i.getDst())+"(%rbp)");
  }

  public void visit(CompareInst i) {
//    printInstructionInfo(i);
    out.printCode("/* CompareInst */ ");
    out.printCode("movq $0, %rax");
    out.printCode("movq $1, %r10");
    out.printCode("movq " + helper(i.getLeftOperand()) + "(%rbp), %r11");
    out.printCode("cmp " + helper(i.getRightOperand()) + "(%rbp), %r11");
    if (i.getPredicate()==CompareInst.Predicate.GE){
      out.printCode("cmovge %r10, %rax");
    } else if (i.getPredicate()==CompareInst.Predicate.GT){
      out.printCode("cmovg %r10, %rax");
    } if (i.getPredicate()==CompareInst.Predicate.LE){
      out.printCode("cmovle %r10, %rax");
    } else if (i.getPredicate()==CompareInst.Predicate.LT){
      out.printCode("cmovl %r10, %rax");
    } if (i.getPredicate()==CompareInst.Predicate.EQ){
      out.printCode("cmove %r10, %rax");
    } else if (i.getPredicate()==CompareInst.Predicate.NE){
      out.printCode("cmovne %r10, %rax");
    }
    out.printCode("movq %rax, " + helper(i.getDst()) + "(%rbp)");
  }

  public void visit(CopyInst i) {
    out.printCode("/* CopyInst */ ");
//    printInstructionInfo(i);
    // movq src, dst

    if (i.getSrcValue() instanceof BooleanConstant) {
      if (((BooleanConstant) i.getSrcValue()).getValue()) {
        out.printCode("movq $1, " + helper(i.getDstVar()) + "(%rbp)");
      } else {
        out.printCode("movq $0, " + helper(i.getDstVar()) + "(%rbp)");
      }
    } else if (i.getSrcValue() instanceof IntegerConstant){
      out.printCode("movq $"+((IntegerConstant)i.getSrcValue()).getValue()+", %r10");
      out.printCode("movq %r10, " + helper(i.getDstVar()) + "(%rbp)");
    } else if (i.getSrcValue() instanceof LocalVar){
      out.printCode("movq " + helper((LocalVar)i.getSrcValue()) + "(%rbp), %r10");
      out.printCode("movq %r10, " + helper(i.getDstVar()) + "(%rbp)");
    }

    // Use $x for constants (e.g. $8 for 8, $1 for true, $0 for false)
    // Use 0(%reg) for addresses (e.g. 0(%r11) if the address is stored in %r11)
    // cant copy from a mem location, copy the source to a register first
  }

  public void visit(JumpInst i) {
    out.printCode("/* JumpInst */ ");
//    printInstructionInfo(i);
    out.printCode("movq " + helper(i.getPredicate()) + "(%rbp), %r10");
    out.printCode("cmp $1, %r10");
    out.printCode("je " + labelMap.get(i.getNext(1)));
  }

  public void visit(LoadInst i) {
    out.printCode("/* LoadInst */ ");
//    printInstructionInfo(i);
    out.printCode("movq " + helper(i.getSrcAddress()) + "(%rbp), %r10");
    out.printCode("movq (%r10), %r11");
    out.printCode("movq %r11, " + helper(i.getDst()) + "(%rbp)");
  }

  public void visit(NopInst  i) {
//    out.printCode("/* NopInst */ ");
  }

  public void visit(StoreInst i) {
    out.printCode("/* StoreInst */ ");
//    printInstructionInfo(i);
    out.printCode("movq " + helper(i.getSrcValue()) + "(%rbp), %r11");
    out.printCode("movq " + helper(i.getDestAddress()) + "(%rbp), %r10");
    out.printCode("movq %r11, (%r10)");
  }

  public void visit(ReturnInst i) {
    out.printCode("/* ReturnInst */ ");
//    printInstructionInfo(i);
    out.printCode("movq " + helper(i.getReturnValue()) + "(%rbp), %rax");
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(CallInst i) {
    out.printCode("/* CallInst */ ");
//    printInstructionInfo(i);

    // store parameters in designated registers or stack
    // argument 1-6 register, 7-n on stack
    // start from the very last one
    // make sure it is still 16 bytes alligned if u put arguments on stack
    List<LocalVar> args = i.getParams();
    int s = args.size()-6;
    if (s%2==1)
      s++;
    for (int j = 0; j < args.size(); j++) {
      if (j == 0) {
        out.printCode("movq " + helper(args.get(j)) + "(%rbp), %rdi");
      } else if (j == 1) {
        out.printCode("movq " + helper(args.get(j)) + "(%rbp), %rsi");
      } else if (j == 2) {
        out.printCode("movq " + helper(args.get(j)) + "(%rbp), %rdx");
      } else if (j == 3) {
        out.printCode("movq " + helper(args.get(j)) + "(%rbp), %rcx");
      } else if (j == 4) {
        out.printCode("movq " + helper(args.get(j)) + "(%rbp), %r8");
      } else if (j == 5) {
        out.printCode("movq " + helper(args.get(j)) + "(%rbp), %r9");
      } else {
        if (j == 6){
          s-=1;
          out.printCode("subq $" + (s*8) + ", %rsp");
        }
        out.printCode("movq " + helper(args.get(j)) + "(%rbp), %r10");
        out.printCode("movq %r10, "+ (16+(j-6)*8) + "(%rbp)");
        out.printCode("addq $8, %rsp");
        if (j==args.size()-1&&j%2==0)
          out.printCode("addq $8, %rsp");
      }
    }
//    if (s%2==1)
//      out.printCode("addq $8, %rsp");
//    int j = 0;
//    for (int k = i.getParams().size()-1; k > 5;k--, j++){
//        tot--;
//        out.printCode("movq " + helper(i.getParams().get(i.getParams().size()-1-j)) + "(%rbp), %r10");
//        out.printCode("movq %r10, " + (16+tot*8) + "(%rbp)");
//    }

    // call instruction in x86 : call *fName*
    // jump to function, create new frame return address and previous %rbp
    // save return value if exists from %rax to the address of the call's dst var
    out.printCode("call " + i.getCallee().getName());
    if(i.getDst() != null){
      out.printCode("movq %rax, " + helper(i.getDst()) + "(%rbp)");
    }
  }

  public void visit(UnaryNotInst i) {
    out.printCode("/* UnaryNotInst */ ");
    printInstructionInfo(i);
    out.printCode("movq " + helper(i.getInner()) + "(%rbp), %r11");
    out.printCode("movq $1, %r10");
    out.printCode("subq %r11, %r10");
    out.printCode("movq %r10, " + helper(i.getDst()) + "(%rbp)");
  }
}
