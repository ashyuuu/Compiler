package crux.ast.types;

/**
 * The field args is a TypeList with a type for each param. The type ret is the type of the function
 * return. The function return could be int, bool, or void. This class should implement the call
 * method.
 */
public final class FuncType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  private TypeList args;
  private Type ret;

  public FuncType(TypeList args, Type returnType) {
    this.args = args;
    this.ret = returnType;
  }

  public Type getRet() {
    return ret;
  }

  public TypeList getArgs() {
    return args;
  }

  // Override call operations
  // if args are different, call super // typechecker
  // otherwise return function's return type
  // override equivalent
  @Override
  public Type call(Type args){
    if (!this.args.equivalent(args))
      return super.call(args);
    return this.ret;
  }

  @Override
  public boolean equivalent(Type that){
    if (that.getClass()!= FuncType.class || !this.args.equivalent(((FuncType)that).getArgs()) ||
        !this.ret.equivalent(((FuncType)that).getRet())){
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "func(" + args + "):" + ret;
  }
}
