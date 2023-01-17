grammar Crux;
program
 : declarationList EOF
 ;

declarationList : declaration* ;

declaration
 : variableDeclaration
 | arrayDeclaration
 | functionDefinition
 ;

variableDeclaration
 : type Identifier SemiColon
 ;

arrayDeclaration
 : type Identifier OpenBracket Integer CloseBracket SemiColon
 ;

functionDefinition : type Identifier OpenParen parameterList CloseParen statementBlock ;

designator : Identifier | Identifier (OpenBracket expression0 CloseBracket)* ;
type : Identifier ;

literal : Integer | True | False ;

SemiColon: ';';
OpenParen: '(';
CloseParen: ')';
OpenBrace: '{';
CloseBrace: '}';
OpenBracket: '[';
CloseBracket: ']';
Add: '+';
Sub: '-';
Mul: '*';
Div: '/';
And: '&&';
Or: '||';
GreaterEqual: '>=';
LesserEqual: '<=';
NotEqual: '!=';
Equal: '==';
GreaterThan: '>';
LessThan: '<';
Assign: '=';
Comma: ',';
Exclamation: '!';
IF: 'if';
ELSE: 'else';
FOR: 'for';
BREAK: 'break';
RETURN: 'return';

Integer : '0' | [1-9] [0-9]* ;

True: 'true';
False: 'false';

Identifier : [a-zA-Z] [a-zA-Z0-9_]* ;
WhiteSpaces : [ \t\r\n]+ -> skip ;
Comment : '//' ~[\r\n]* -> skip ;

op0 : GreaterEqual | LesserEqual | NotEqual | Equal | GreaterThan | LessThan ;
op1 : Add | Sub | Or ;
op2 : Mul | Div | And ;

expression0
 : expression1 (op0 expression1)*
 ;

expression1
 : expression2 | expression1 op1 expression2
 ;

expression2
 : expression3 | expression2 op2 expression3
 ;

expression3
 : Exclamation expression3 | OpenParen expression0 CloseParen | designator | callExpression | literal
 ;

callExpression : Identifier OpenParen expressionList CloseParen ;

expressionList : | expression0 (Comma expression0)* ;

parameter: type Identifier;
parameterList: | parameter (Comma parameter)*;

assignmentStatement : designator Assign expression0 SemiColon ;
assignmentStatementNoSemi : designator Assign expression0 ;
callStatement : callExpression SemiColon;
ifStatement : IF expression0 statementBlock | IF expression0 statementBlock (ELSE statementBlock)* ;
forStatement : FOR OpenParen assignmentStatement expression0 SemiColon assignmentStatementNoSemi CloseParen statementBlock ;
breakStatement : BREAK SemiColon ;
returnStatement : RETURN expression0 SemiColon ;

statement
 : variableDeclaration
 | callStatement
 | assignmentStatement
 | ifStatement
 | forStatement
 | breakStatement
 | returnStatement
 ;

statementList : statement*;
statementBlock : OpenBrace statementList CloseBrace;