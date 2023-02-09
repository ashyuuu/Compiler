package crux.ast.types;

/**
 * Types for Integers values. This should implement the equivalent methods along with add, sub, mul,
 * div, and compare. The method equivalent will check if the param is an instance of IntType.
 */
public final class IntType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;
  // Override all arithmetic operations, comparison, assign
  // if types are different, call super // error
  // otherwise return new inttype/booltype/voidtype
  // override equivalent

  @Override
  public Type add(Type that){
    if (that.getClass()!= IntType.class){
      return super.add(that);
    }
    return new IntType();
  }

  @Override
  public Type sub(Type that){
    if (that.getClass()!= IntType.class){
      return super.sub(that);
    }
    return new IntType();
  }

  @Override
  public Type mul(Type that){
    if (that.getClass()!= IntType.class){
      return super.mul(that);
    }
    return new IntType();
  }

  @Override
  public Type div(Type that){
    if (that.getClass()!= IntType.class){
      return super.div(that);
    }
    return new IntType();
  }

  @Override
  public Type compare(Type that){
    if (that.getClass()!= IntType.class)
      return super.compare(that);
    return new BoolType();
  }

  @Override
  public Type assign(Type that){
    if (that.getClass()!= IntType.class)
      return super.assign(that);
    return new IntType();
  }

  @Override
  public boolean equivalent(Type that){
    if (that.getClass()!= IntType.class){
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "int";
  }
}
