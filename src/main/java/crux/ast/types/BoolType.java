package crux.ast.types;

/**
 * Types for Booleans values This should implement the equivalent methods along with and,or, and not
 * equivalent will check if the param is instance of BoolType
 */
public final class BoolType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;
  // Override all logical operations, assign
  // if types are different, call super // error
  // otherwise return new booltype/voidtype
  // override equivalent
  @Override
  public Type and (Type that){
    if (that.getClass()!= BoolType.class)
      return super.and(that);
    return new BoolType();
  }

  @Override
  public Type or (Type that) {
    if (that.getClass()!= BoolType.class)
      return super.or(that);
    return new BoolType();
  }

  @Override
  public Type not(){
    if (this.getClass()!= BoolType.class)
      return super.not();
    else
      return new BoolType();
  }

  @Override
  public Type assign(Type that){
    if (that.getClass()!= BoolType.class)
      return super.assign(that);
    return new BoolType();
  }
  @Override
  public boolean equivalent(Type that){
    if (that.getClass()!= BoolType.class){
      return false;
    }
    return true;
  }
  @Override
  public String toString() {
    return "bool";
  }
}
