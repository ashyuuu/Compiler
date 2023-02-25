package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;

import java.lang.reflect.Array;
import java.rmi.NoSuchObjectException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class InstPair {
  Instruction start;
  Instruction end;
//  LocalVar val;
  Variable val;
  public InstPair(Instruction instr){
    start = instr;
    end = instr;
    val = null;
  }

  public InstPair(Instruction s, Instruction e){
    start = s;
    end = e;
    val = null;
  }

  public InstPair(Instruction s, Variable v){
    start = s;
    end = s;
    val = v;
  }

  public InstPair(Instruction s, Instruction e, Variable v){
    start = s;
    end = e;
    val = v;
  }
}


/**
 * Convert AST to IR and build the CFG
 */
public final class ASTLower implements NodeVisitor<InstPair> {
  private Program mCurrentProgram = null; // update when visiting delcarationList
  private Function mCurrentFunction = null; // update when function is defined / CFG is constructed

  private Map<Symbol, LocalVar> mCurrentLocalVarMap = null; // keeps track of local var in curr func
  private boolean isAssign = false;

  /**
   * A constructor to initialize member variables
   */
  public ASTLower() {}

  public Program lower(DeclarationList ast) {
    visit(ast);
    return mCurrentProgram;
  }

  @Override
  public InstPair visit(DeclarationList declarationList) {
    // create new instance of Program class for mCurrentProgram
    mCurrentProgram = new Program();
    // for each declaration:
    for (var n : declarationList.getChildren()){
      // visit each declaration
      n.accept(this);
    }
    return null;
  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {
    // create function instance
    mCurrentFunction = new Function(functionDefinition.getSymbol().getName(), (FuncType) functionDefinition.getSymbol().getType());
    // create new hashmap<symbol, variable> for mcurrentlocalvarmap
    mCurrentLocalVarMap = new HashMap<Symbol,LocalVar>();
    List<LocalVar> args = new ArrayList<LocalVar>();
    // for each argument
    for (var arg : functionDefinition.getParameters()){
      // create localVar using mcurrentfunction.gettempvar() and put them in a list
      LocalVar v = mCurrentFunction.getTempVar(arg.getType());
      args.add(v);
      // put the variable above to mCurrentLocalVarMap with correct symbol
      mCurrentLocalVarMap.put(arg,v);
    }

    // set arguments for mCurrentFunction
    mCurrentFunction.setArguments(args);
    // add mCurrentFunction to the function list in mCurrentProgram
    mCurrentProgram.addFunction(mCurrentFunction);
    // visit function body
    InstPair v = functionDefinition.getStatements().accept(this);
    // set the start node of the mCurrentFunction
    mCurrentFunction.setStart(v.start);
    // dump mCurrentFunction and mCurrentLocalVarMap
    mCurrentFunction = null;
    mCurrentLocalVarMap = null;
    return null;
  }

  @Override
  public InstPair visit(StatementList statementList) {
    // start with nopInst
    NopInst head = new NopInst();
    if (statementList.getChildren().size()==0)
      return new InstPair(head);
    Instruction tail = head;
    // for each statement:
    for (var v : statementList.getChildren()){
      // visit each statement and connect them // "string them together"
      InstPair p = v.accept(this);
      tail.setNext(0, p.start);
      tail=p.end;
    }
    // return InstPair with start and end of the statementList
    return new InstPair(head, tail);
    // noValue for InstaPair
  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {
    // If mCurrentFunction is null, this is a global variable. Add a GlobalDecl to mCurrentProgram.
    if (mCurrentFunction == null){
      Symbol s = variableDeclaration.getSymbol();
      IntegerConstant i = IntegerConstant.get(mCurrentProgram,1);
      GlobalDecl gd = new GlobalDecl(s,i);
      mCurrentProgram.addGlobalVar(gd);
    } else {
      // Otherwise, it is a local variable. Allocate a temp var and add it to mCurrentLocalVarMap.
      Symbol s = variableDeclaration.getSymbol();
      LocalVar loco = mCurrentFunction.getTempVar(s.getType());
      mCurrentLocalVarMap.put(s, loco);
    }

    // No instructions need to be done.
//    return null;
    // Return an InstPair of a NopInst if you don’t want to do null checks in visit(StatmentList).
    return new InstPair(new NopInst());
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    // Add a GlobalDecl to mCurrentProgram.
    ArrayType arrt = ((ArrayType)arrayDeclaration.getSymbol().getType());
    long ind = arrt.getExtent();
    GlobalDecl gd = new GlobalDecl(arrayDeclaration.getSymbol(), IntegerConstant.get(mCurrentProgram, ind));
    mCurrentProgram.addGlobalVar(gd);
    // No instructions need to be done.
    return new InstPair(new NopInst());
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    // Check if it is local or global by checking mCurrentLocalVarMap
    if (mCurrentLocalVarMap.get(name.getSymbol()) == null){
      // If global: use AddressAt and LoadInst
      if (isAssign){
//        LocalVar loco = mCurrentFunction.getTempVar(name.getType());
        AddressVar loca = mCurrentFunction.getTempAddressVar(name.getSymbol().getType());
        AddressAt addrAt = new AddressAt(loca,name.getSymbol());
//        LoadInst ld = new LoadInst(loco, loca);
//        addrAt.setNext(0,ld);
//      return null;
        isAssign=false;
        return new InstPair(addrAt, loca);
      }else {
        AddressVar loca = mCurrentFunction.getTempAddressVar(name.getSymbol().getType());
        AddressAt addrAt = new AddressAt(loca,name.getSymbol());
        LocalVar loco = mCurrentFunction.getTempVar(name.getType());
        LoadInst ld = new LoadInst(loco, loca);
        addrAt.setNext(0,ld);
        return new InstPair(addrAt, ld, loca); // mid ld/addrat
      }

    } else {
      // If local: return empty InstPair with LocalVar as value
      NopInst n = new NopInst();
      return new InstPair(n,n,mCurrentLocalVarMap.get(name.getSymbol()));
    }
    // For all cases, InstPair val is LocalVar
  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    isAssign = true;
    InstPair lhs = assignment.getLocation().accept(this);
    isAssign = false;
    InstPair rhs = assignment.getValue().accept(this);

    if (lhs.val instanceof LocalVar){
//      Symbol s = ((VarAccess)assignment.getLocation()).getSymbol();
      lhs.end.setNext(0, rhs.start);
      if (rhs.val instanceof LocalVar){
        CopyInst c = new CopyInst((LocalVar) lhs.val,rhs.val);
        rhs.end.setNext(0, c);
        return new InstPair(lhs.start, c);
      } else{
        CopyInst c = new CopyInst((LocalVar) lhs.val,((LoadInst)rhs.end).getDst());
        rhs.end.setNext(0, c);
        return new InstPair(lhs.start, c);
      }

    } else { // if (lhs.val instanceof AddressVar)
//      System.out.println(lhs.val==null);
      lhs.end.setNext(0,rhs.start);
//      System.out.println((LocalVar)rhs.val==null);
//      System.out.println((AddressVar)lhs.val==null);
      if (rhs.val instanceof LocalVar){
        StoreInst st = new StoreInst((LocalVar)rhs.val,(AddressVar)lhs.val);
        rhs.end.setNext(0,st);
//      isAssign = false;
        return new InstPair(lhs.start,st);
      }else{
        StoreInst st = new StoreInst(((LoadInst)rhs.end).getDst(),(AddressVar)lhs.val);
        rhs.end.setNext(0,st);
//      isAssign = false;
        return new InstPair(lhs.start,st);
      }

    }
  }

  /**
   * Lower a Call
   */
  @Override
  public InstPair visit(Call call) {
    // similar to statementList
    // visit each argument and connect them
    NopInst head = new NopInst();
    List<LocalVar> args = new ArrayList<LocalVar>();
    Instruction temp = head;
    boolean first = true;
    for (Expression arg : call.getArguments()){
//      System.out.println(isAssign);
      InstPair t = arg.accept(this);
      if (first){
        first = false;
        head.setNext(0, t.start);
      } else {
        temp.setNext(0, t.start);
      }
      temp = t.end;
      if (t.val instanceof AddressVar){
        args.add(((LoadInst)t.end).getDst());
      } else
        args.add((LocalVar) t.val);
//      if (t.val instanceof LocalVar)
//        args.add((LocalVar) ((Variable)t.val));
//      else
//        args.add(mCurrentFunction.getTempVar())
    }
//    head.setNext(0,temp);
    // depending on the return value, use diff callinst constructor
    // if return value exist, returning instpair's val is same as destvar in constructor
    FuncType ft = (FuncType)call.getCallee().getType();
    if (ft.getRet() instanceof VoidType){
      CallInst c = new CallInst(call.getCallee(),args);
      temp.setNext(0,c);
      return new InstPair(head,c);
    }
    LocalVar addy = mCurrentFunction.getTempVar(ft);
    CallInst ci = new CallInst(addy,call.getCallee(),args);
    temp.setNext(0, ci);
    return new InstPair(head,ci,addy);
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) { // TODO check this
    // postorder traversal
    // create instrpair for lhs and rhs by visiting
    InstPair lhs = operation.getLeft().accept(this);
    // concatenate the pairs and add op inst to the end
    // lhs > rhs > op
    LocalVar loco = mCurrentFunction.getTempVar(operation.getType());
    String op = operation.getOp().toString();
    LocalVar v;
    if (lhs.val instanceof AddressVar){
      v = ((LoadInst)lhs.end).getDst();
    } else {
      v = (LocalVar) lhs.val;
    }
    if (op.equals("!")) {
      UnaryNotInst mNot = new UnaryNotInst(loco, (LocalVar) lhs.val);
      lhs.end.setNext(0, mNot);
      return new InstPair(lhs.start, mNot, loco);
    }
    LocalVar v1;
    InstPair rhs = operation.getRight().accept(this);
    if (rhs.val instanceof AddressVar){
      v1 = ((LoadInst)rhs.end).getDst();
    } else {
      v1 = (LocalVar) rhs.val;
    }

    if (op.equals(">=")){
      CompareInst c = new CompareInst(loco, CompareInst.Predicate.GE, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,c);
      return new InstPair(lhs.start, c, loco);
    } else if (op.equals(">")){
      CompareInst c = new CompareInst(loco, CompareInst.Predicate.GT, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,c);
      return new InstPair(lhs.start, c, loco);
    } else if (op.equals("<=")){
      CompareInst c = new CompareInst(loco, CompareInst.Predicate.LE, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,c);
      return new InstPair(lhs.start, c, loco);
    } else if (op.equals("<")){
      CompareInst c = new CompareInst(loco, CompareInst.Predicate.LT, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,c);
      return new InstPair(lhs.start, c, loco);
    } else if (op.equals("==")){
      CompareInst c = new CompareInst(loco, CompareInst.Predicate.EQ, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,c);
      return new InstPair(lhs.start, c, loco);
    } else if (op.equals("!=")){
      CompareInst c = new CompareInst(loco, CompareInst.Predicate.NE, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,c);
      return new InstPair(lhs.start, c, loco);
    } else if (op.equals("+")){
      BinaryOperator bin = new BinaryOperator(BinaryOperator.Op.Add,loco, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,bin);
      return new InstPair(lhs.start, bin, loco);
    } else if (op.equals("-")){
      BinaryOperator bin = new BinaryOperator(BinaryOperator.Op.Sub,loco, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,bin);
      return new InstPair(lhs.start, bin, loco);
    } else if (op.equals("*")){
      BinaryOperator bin = new BinaryOperator(BinaryOperator.Op.Mul, loco, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,bin);
      return new InstPair(lhs.start, bin, loco);
    } else if (op.equals("/")){
      BinaryOperator bin = new BinaryOperator(BinaryOperator.Op.Div, loco, v, v1);
      lhs.end.setNext(0,rhs.start);
      rhs.end.setNext(0,bin);
      return new InstPair(lhs.start, bin, loco);
    } else {
      JumpInst jump = new JumpInst(v);
      lhs.end.setNext(0,jump);
      if (op.equals("&&")){
        // so A && B > if (A) then B else false
//        CopyInst copy1 = new CopyInst(loco, BooleanConstant.get(mCurrentProgram,false));
        CopyInst copy1 = new CopyInst(loco, v);
        CopyInst copy2 = new CopyInst(loco, v1);
        jump.setNext(0, copy1);
        jump.setNext(1, rhs.start);
        rhs.end.setNext(0,copy2);
        NopInst merge = new NopInst();
        copy1.setNext(0, merge);
        copy2.setNext(0, merge);
        return new InstPair(lhs.start, merge, loco);
      } else if (op.equals("||")){
        // A || B > if (A) then true else B
        CopyInst copy1 = new CopyInst(loco, v);
        CopyInst copy2 = new CopyInst(loco, v1);
        jump.setNext(0, rhs.start);
        jump.setNext(1, copy1);
        rhs.end.setNext(0,copy2);
        NopInst merge = new NopInst();
        copy1.setNext(0, merge);
        copy2.setNext(0, merge);
        return new InstPair(lhs.start, merge, loco);
      }
    }
    // boolean operators > special if else
    // if a is false then a && b is false
    // if a is true then a && b is always b
    return null;
  }

  private InstPair visit(Expression expression) {
    // Visit each operand and add edges between each InstPairs
    return expression.accept(this);
    // value is the destination passed in to the instructions
    // check constructors to see what values r needed
    // opexpr > binaryoperator/compareinst/unarynotinst
    // call > callinst
    // arrayaccess/varaccess(global) > addressat+loadinst
    // varaccess(local) > get localvar from mcurrentlocalvarmap
  }

  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    // If global:  use AddressAt and LoadInst
    // For all cases, InstPair val is LocalVar

    if (isAssign){
//        LocalVar loco = mCurrentFunction.getTempVar(name.getType());
      isAssign=false;
      InstPair index = access.getIndex().accept(this);
      AddressVar loca = mCurrentFunction.getTempAddressVar(access.getType());
      if (index.val instanceof LocalVar){
        AddressAt addrAt = new AddressAt(loca,access.getBase(), (LocalVar) index.val);
        index.end.setNext(0,addrAt);
//        LoadInst ld = new LoadInst(loco, loca);
//        addrAt.setNext(0,ld);
//      return null;
        return new InstPair(index.start, addrAt, loca);
      } else {
        AddressAt addrAt = new AddressAt(loca,access.getBase(), ((LoadInst) index.end).getDst());
        index.end.setNext(0,addrAt);
//        LoadInst ld = new LoadInst(loco, loca);
//        addrAt.setNext(0,ld);
//      return null;
        return new InstPair(index.start, addrAt, loca);
      }

    }else {
      InstPair index = access.getIndex().accept(this);
      AddressVar loca = mCurrentFunction.getTempAddressVar(access.getType());
      if (index.val instanceof LocalVar) {
        AddressAt addrAt = new AddressAt(loca, access.getBase(), (LocalVar) index.val);
        index.end.setNext(0, addrAt);
        LocalVar loco = mCurrentFunction.getTempVar(access.getType());
        LoadInst ld = new LoadInst(loco, loca);
        addrAt.setNext(0, ld);
        return new InstPair(index.start, ld, loca); // mid ld/addrat
      } else {
        AddressAt addrAt = new AddressAt(loca, access.getBase(), ((LoadInst) index.end).getDst());
        index.end.setNext(0, addrAt);
        LocalVar loco = mCurrentFunction.getTempVar(access.getType());
        LoadInst ld = new LoadInst(loco, loca);
        addrAt.setNext(0, ld);
        return new InstPair(index.start, ld, loca); // mid ld/addrat
      }
    }
//    InstPair index = access.getIndex().accept(this);
//    LocalVar loco = mCurrentFunction.getTempVar(access.getType());
//    AddressVar addr1 = mCurrentFunction.getTempAddressVar(access.getType());
//    AddressAt addrAt = new AddressAt(addr1,access.getBase(),(LocalVar) index.val);
//    index.end.setNext(0,addrAt);
//    LoadInst ld = new LoadInst(loco,addr1);
//    addrAt.setNext(0,ld);
//    return new InstPair(index.start, ld, loco);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {
    // Create LocalVar using getTempVar for destVar
    // Use IntegerConstant.get() / BooleanConstant.get() for source
    // Returning InstPair consists of one CopyInst with value of
    LocalVar loco = mCurrentFunction.getTempVar(new BoolType());
    CopyInst ci = new CopyInst(loco, BooleanConstant.get(mCurrentProgram,literalBool.getValue()));
    return new InstPair(ci,ci,loco);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    // Create LocalVar using getTempVar for destVar
    // Use IntegerConstant.get() / BooleanConstant.get() for source
    // Returning InstPair consists of one CopyInst with value of LocalVar
//    return new InstPair(new NopInst(), (Variable)IntegerConstant.get(mCurrentProgram,literalInt.getValue()));
    LocalVar loco = mCurrentFunction.getTempVar(new IntType());
    CopyInst ci = new CopyInst(loco, IntegerConstant.get(mCurrentProgram,literalInt.getValue()));
    return new InstPair(ci,ci,loco);
  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    // Visit return value node
    // Use value of the returning InstPair for ReturnInst constructor argument
    InstPair r = ret.getValue().accept(this);
    ReturnInst reti = new ReturnInst((LocalVar) r.val);
    r.end.setNext(0,reti);
    return new InstPair(r.start,reti);
  }
  public Stack<Instruction> breaking = new Stack<Instruction>();
  /**
   * Break Node
   */
  @Override
  public InstPair visit(Break brk) {
    var exit = breaking.pop();
    for (var n : brk.getChildren()){
      n.accept(this);
    }
    return new InstPair(exit, new NopInst());
  }

  /**
   * Implement If Then Else statements.
   */
  @Override
  public InstPair visit(IfElseBranch ifElseBranch) {
    // first get the instruction pair for the condition
    InstPair cond = ifElseBranch.getCondition().accept(this);
    // add jump instruction to it
    JumpInst jump = new JumpInst((LocalVar) cond.val);
    cond.end.setNext(0,jump);
    // 2 children:  set to else and then branch (jumpInst.next[0] > cont, jumpInst.next[1] > jump)
    InstPair then = ifElseBranch.getThenBlock().accept(this);
    InstPair elsa = ifElseBranch.getElseBlock().accept(this);
    jump.setNext(0,elsa.start);
    jump.setNext(1,then.start);
    // eventually merge back // just nopInst
    NopInst merge = new NopInst();
    elsa.end.setNext(0,merge);
    then.end.setNext(0,merge);
    return new InstPair(cond.start, merge);
  }

  /**
   * Implement for loops.
   */
  @Override
  public InstPair visit(For loop) {
    // Create a NopInsts for the loop exit and add it to a stack accessible from other methods
    NopInst exit = new NopInst();
    breaking.push(exit);
    // return with visiting break
    // Visit init, condition, body, and increment
    InstPair init = loop.getInit().accept(this);
    InstPair cond = loop.getCond().accept(this);
    init.end.setNext(0,cond.start);
    JumpInst jump = new JumpInst((LocalVar) cond.val);
    cond.end.setNext(0,jump);
    InstPair body = loop.getBody().accept(this);
    InstPair incr = loop.getIncrement().accept(this);
    body.end.setNext(0,incr.start);
    jump.setNext(0,exit);
    jump.setNext(1,body.start);
    incr.end.setNext(0,cond.start);
    // Pop loop exit from stack before returning
    return new InstPair(init.start,exit);
  }
}
