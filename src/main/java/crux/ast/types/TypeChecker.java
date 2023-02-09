package crux.ast.types;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class will associate types with the AST nodes from Stage 2
 */
public final class TypeChecker {
  private final ArrayList<String> errors = new ArrayList<>();

  public ArrayList<String> getErrors() {
    return errors;
  }

  public void check(DeclarationList ast) {
    var inferenceVisitor = new TypeInferenceVisitor();
    inferenceVisitor.visit(ast);
  }

  /**
   * Helper function, should be used to add error into the errors array
   */
  private void addTypeError(Node n, String message) {
    errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
  }

  /**
   * Helper function, should be used to record Types if the Type is an ErrorType then it will call
   * addTypeError
   */
  private void setNodeType(Node n, Type ty) {
//    System.out.println(getType(n).toString());
    ((BaseNode) n).setType(ty);
    if (ty.getClass() == ErrorType.class) {
      var error = (ErrorType) ty;
      addTypeError(n, error.getMessage());
    }
//    System.out.println("0");
  }

  /**
   * Helper to retrieve Type from the map
   */
  public Type getType(Node n) {
    return ((BaseNode) n).getType();
  }


  /**
   * This calls will visit each AST node and try to resolve it's type with the help of the
   * symbolTable.
   */

    // start from declaration list, check types of ast nodes
    // look at typeinferencevisitor class // here
    // collect errors, when node has errortype, store the error in a list
    // check all returns in a function matches the return type and that there are not unreachable statements
  private final class TypeInferenceVisitor extends NullNodeVisitor<Void> {
    boolean lastStatementReturns = false; // set to true when see a return statement to check for unreachables
    // reset whenever entering a new function definition (or scope?)
    Symbol currentFunctionSymbol; // use for error recording
    Type currentFunctionReturnType; // use for return type check

    // check if the accessing variable is errortype // if so add to error list
    @Override
    public Void visit(VarAccess vaccess) {
//      if (vaccess.getSymbol().getType().getClass()==ErrorType.class)
//        addTypeError(vaccess,((ErrorType)(vaccess.getSymbol().getType())).getMessage());
      setNodeType(vaccess, vaccess.getSymbol().getType());
      return null;
    }

    // check if it is int or bool // only these two
    // update lastStatementReturns
    @Override
    public Void visit(ArrayDeclaration arrayDeclaration) {
      if (((ArrayType) arrayDeclaration.getSymbol().getType()).getBase().getClass()!=IntType.class &&
              ((ArrayType) arrayDeclaration.getSymbol().getType()).getBase().getClass()!=BoolType.class)
        addTypeError(arrayDeclaration,"Error with ArrayDeclaration");
      else
        setNodeType(arrayDeclaration, arrayDeclaration.getSymbol().getType());
      lastStatementReturns = false;
      return null;
    }

    // type check children (location, value) // make sure they match
      // by calling location.assign(value)
    // update AST node type field
    // update lastStatementReturns
    @Override
    public Void visit(Assignment assignment) {
        assignment.getLocation().accept(this);
        assignment.getValue().accept(this);
//        setNodeType(assignment.getLocation(),);
//        System.out.println(getType(assignment.getLocation())==null);
//        System.out.println(getType(assignment.getValue())==null);
//        System.out.println();
        Type e = getType(assignment.getLocation()).assign(getType(assignment.getValue()));
  //      Type e = getType(assignment.getLocation()).assign(getType(assignment.getValue()));

//        if (e.getClass()== ErrorType.class)
//          addTypeError(assignment,((ErrorType) e).getMessage());
        setNodeType(assignment,e);
//        setNodeType(assignment.getLocation(),getType(assignment.getLocation()));
//        setNodeType(assignment.getValue(),getType(assignment.getValue()));
        lastStatementReturns = false;
        return null;
    }

    // updateHasBreak but not in this quarter? save to last
    // update lastStatementReturns
    @Override
    public Void visit(Break brk) {
      lastStatementReturns = false;
      return null;
    }

    // type check children and match with our required types
    // by calling call.getCallee().getType().call(argumentList from above)
    // update ast node type fields to the result
    // update lastStatementReturns
    @Override
    public Void visit(Call call) {
      TypeList tl = new TypeList();
      for (Expression n : call.getArguments()){
        n.accept(this);
//        setNodeType(n,call.getCallee().getType().call(getType(n)));
        tl.append(getType(n));
      }
//      Type ret = call.getCallee().getType().call();
//      FuncType ft = (FuncType) call.getCallee().getType();
      //if ((FuncType)call.getCallee().getType()!=null)
      setNodeType(call,call.getCallee().getType().call(((FuncType)call.getCallee().getType()).getArgs()));
      lastStatementReturns = false;
      return null;
    }

    // type check children (declarations)
    @Override
    public Void visit(DeclarationList declarationList) {
      for (Node n : declarationList.getChildren()){
        n.accept(this);
      }
      return null;
    }

    // 2 possible argument types (int, bool)
    // visit function body
    // update currentFunctionSymbol and currentFunctionReturnType
    // enforce the signature of main (void main())
    // check if there exists return when not void using lastStatementReturns
    @Override
    public Void visit(FunctionDefinition functionDefinition) {
      currentFunctionSymbol = functionDefinition.getSymbol();
      currentFunctionReturnType = ((FuncType)functionDefinition.getSymbol().getType()).getRet();
      if (currentFunctionSymbol.getName().equals("main") &&
              (functionDefinition.getParameters().size()!=0 || currentFunctionReturnType.getClass()!= VoidType.class) ){
        addTypeError(functionDefinition,"Incorrect signature for main.");
      }
      for (Symbol param : functionDefinition.getParameters()){
        if (param.getType().getClass()!= IntType.class && param.getType().getClass()!=BoolType.class){
          addTypeError(functionDefinition,((ErrorType)param.getType()).getMessage());
        }
      }
      functionDefinition.getStatements().accept(this);
      if (lastStatementReturns && currentFunctionReturnType.getClass()==VoidType.class){
        addTypeError(functionDefinition,"VoidType functions returning non-void output.");
      }
      return null;
    }

    // check if condition is bool
    // visit else and then block
    // update lastStatementReturns using AND between the 2 lastStatementReturns
    @Override
    public Void visit(IfElseBranch ifElseBranch) {
      ifElseBranch.getCondition().accept(this);

      if (!getType(ifElseBranch.getCondition()).equivalent(new BoolType())){
        addTypeError(ifElseBranch,"If condition is not of Boolean type.");
        //setNodeType(ifElseBranch.getCondition(),getType(ifElseBranch.getCondition())); // might remove
      }
      boolean thenBranchRet = lastStatementReturns;
      boolean elseBranchRet = lastStatementReturns;
      ifElseBranch.getElseBlock().accept(this);
      ifElseBranch.getThenBlock().accept(this);

//      visit((StatementList)ifElseBranch.getThenBlock());
      thenBranchRet = lastStatementReturns;

//      visit((StatementList)ifElseBranch.getElseBlock());
      elseBranchRet = lastStatementReturns;

      lastStatementReturns = (thenBranchRet && elseBranchRet);
      return null;
    }

    // type check offset (must be int), make sure the base type = below
    // call (base type of array).index(type of offset)
    // update AST node type field to the result
    @Override
    public Void visit(ArrayAccess access) {
      if (access.getIndex()!=null){
        access.getIndex().accept(this);
        setNodeType(access,access.getBase().getType().index(getType(access.getIndex()))); // might remove
      } else {
        setNodeType(access, access.getBase().getType());
      }
      // array base type already checked when declaring
//      access.getBase().getType().index(getType(access.getIndex()));
//      setNodeType(access,getType(access)); // but we didnt change access so could cause issue, check when debugging
      return null;
    }

    // update AST node type field new BoolType/IntType
    @Override
    public Void visit(LiteralBool literalBool) {
      setNodeType(literalBool, new BoolType());
      return null;
    }

    // update AST node type field new BoolType/IntType
    @Override
    public Void visit(LiteralInt literalInt) {
      setNodeType(literalInt, new IntType());
      return null;
    }

    // update lastStatementReturns
    // visit loop body
    // condition is bool type
    @Override
    public Void visit(For forloop) {
      forloop.getCond().accept(this);
      //setNodeType(forloop.getCond(),new BoolType());
      if (getType(forloop.getCond()).getClass()!=BoolType.class){
//        System.out.println(getType(forloop.getCond()).toString());
        addTypeError(forloop,"For loop condition is not of Boolean type.");
      } else {
        forloop.getBody().accept(this);
        forloop.getIncrement().accept(this);
        forloop.getInit().accept(this);
      }
      //visit(forloop.getBody());

      return null;
    }

    // type check left and right first
    // call corresponding method depending on operator
    // update ast node type field to the result (setNodeType in TypeChecker)
    @Override
    public Void visit(OpExpr op) {
      Expression left = null;
      Expression right = null;
      if (op.getLeft()!=null){
        left = op.getLeft();
        left.accept(this);
      }
      if (op.getRight()!=null){
        right = op.getRight();
        right.accept(this);
      }
      ArrayList<String> comp = new ArrayList<String>(Arrays.asList(">=","<=","!=","==",">","<"));
      if (comp.contains(op.getOp().toString())){
        setNodeType(op, getType(left).compare(getType(right)));
      } else if (op.getOp().toString().equals("+")){
        setNodeType(op, getType(left).add(getType(right)));
      } else if (op.getOp().toString().equals("-")){
        setNodeType(op, getType(left).sub(getType(right)));
      } else if (op.getOp().toString().equals("*")){
        setNodeType(op, getType(left).mul(getType(right)));
      } else if (op.getOp().toString().equals("/")){
        setNodeType(op, getType(left).div(getType(right)));
      } else if (op.getOp().toString().equals("&&")){
        setNodeType(op, getType(left).and(getType(right)));
      } else if (op.getOp().toString().equals("||")){
        setNodeType(op, getType(left).or(getType(right)));
      } else if (op.getOp().toString().equals("!")){
        setNodeType(op, getType(left).not());
      }

      return null;
    }

    // type check the return value
    // check if return type matches currentFunctionReturnType
    // update lastStatemnetReturns to TRUE
    @Override
    public Void visit(Return ret) {
      ret.getValue().accept(this);
      if (!getType(ret.getValue()).equivalent(currentFunctionReturnType)){
        addTypeError(ret, "Return types do not match.");
      } else {
        setNodeType(ret, getType(ret.getValue()));
      }
      lastStatementReturns = true;
      return null;
    }

    // check if there is unreachable statement using lastStatementReturn
    // visit each statement
    // if lastStatementReturns is true and there are still more statements coming then its unreachable
    @Override
    public Void visit(StatementList statementList) {
      for (Node child : statementList.getChildren()){
          child.accept(this);
      }
      return null;
    }

    // 2 possible types (int & bool)
    // update lastStatementReturns
    @Override
    public Void visit(VariableDeclaration variableDeclaration) {
      if (variableDeclaration.getSymbol().getType().getClass()!=BoolType.class &&
              variableDeclaration.getSymbol().getType().getClass()!=IntType.class){
        addTypeError(variableDeclaration,"Variable is not of type Integer or Boolean.");
      }
      lastStatementReturns = false;
      return null;
    }
  }
}
