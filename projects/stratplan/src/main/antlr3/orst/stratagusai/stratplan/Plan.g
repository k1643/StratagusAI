/* 
 * Plan.g defines the strategic plan grammar and generates the lexer and
 * parser.
 *
 */
grammar Plan;
options {
    // output AST for use in StrategyEval evaluator.
    output=AST;
    ASTLabelType=CommonTree;
}

@header {
package orst.stratagusai.stratplan;
}

@lexer::header {
package orst.stratagusai.stratplan;
}

@members {
    public static void main(String[] args) throws Exception {
        PlanLexer lex = new PlanLexer(new ANTLRFileStream(args[0]));
       	CommonTokenStream tokens = new CommonTokenStream(lex);

        PlanParser parser = new PlanParser(tokens);
 
        try {
            parser.plan();
        } catch (RecognitionException e)  {
            e.printStackTrace();
        }
    }
}

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

/* top level */

plan
    : '(' ':plan' NAME ':player' INT group_spec* task* ')' ;

group_spec
    : '(' ':group-spec' INT ':type' NAME units_spec* (':initial-units' '(' INT* ')')? ')';

units_spec
    : NAME INT;

task
    : '(' ':task' NAME task_args
          ':type' NAME
          (':using' INT)?
          (':start' start_triggers)?
          (':end'   end_triggers)? ')' ;

task_args
    : '(' group_arg? region_arg? ')';

group_arg
    : '(' ':group' INT ')' ;

task_arg
    : ((NAME INT)|NAME);

region_arg
    : '(' ':region' INT ')';

start_triggers
    : '(' ':trigger' trigger* ')' ;

end_triggers
    : '(' ':trigger' trigger* ')' ;

trigger
    : '(' ('start' | 'end') NAME ')' ;

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

NAME	:('a'..'z'|'A'..'Z'|'_')(options{greedy=true;}:	'-'|'a'..'z'|'A'..'Z'|'_'|'0'..'9')*
	;

INT	: (DIGIT)+ ;

WHITESPACE : ( '\t' | ' ' | '\r' | '\n'| '\u000C' )+ 	{ skip(); } ;

fragment DIGIT	: '0'..'9' ;

LINE_COMMENT
    : '#' ~('\n'|'\r')* '\r'? '\n' {skip();}
    ;
