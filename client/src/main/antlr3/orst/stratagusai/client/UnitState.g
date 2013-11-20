/* 
 * UnitState.g defines the UnitState grammar and generates the lexer and
 * parser.
 *
 */
grammar UnitState;
options {
    // output AST for use in UnitStateEval evaluator.
    output=AST;
    ASTLabelType=CommonTree;
}

tokens {
    UNIT_STATE;
    UNITS;
    UNIT;
    PLAYER;
    LABELED_VAL;
    EVENTS;
    BUILT;
    TRAINED;
    DIED;
}

@header {
package orst.stratagusai.client;
}

@lexer::header {
package orst.stratagusai.client;
}

@members {
    public static void main(String[] args) throws Exception {
        UnitStateLexer lex = new UnitStateLexer(new ANTLRFileStream(args[0]));
       	CommonTokenStream tokens = new CommonTokenStream(lex);

        UnitStateParser parser = new UnitStateParser(tokens);
 
        try {
            parser.unit_state();
        } catch (RecognitionException e)  {
            e.printStackTrace();
        }
    }
}

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

/* top level */

unit_state
    : '(' units player+ events? ')' -> ^(UNIT_STATE units player+ events?);

units
    : '(' unit* ')' -> ^(UNITS unit*);

/*( 0 . #s(unit player-id 0 type 23 loc (2 15) hp 60 r-amt 0 kills 0 armor 20 dmg 0 piercing-dmg 0 status 1 status-args ())) */
unit
    : '(' INT '.' '#s(' 'unit' 'player-id' INT 'type' INT 'loc' '(' INT INT ')' 'hp' INT 'r-amt' INT 'kills' INT 'armor' INT 'dmg' INT 'piercing-dmg' INT 'status' INT  'status-args' '(' INT? INT? ')' ')' ')' ->
      ^(UNIT INT '.' '(' 'unit' 'player-id' INT 'type' INT 'loc' '(' INT INT ')' 'hp' INT 'r-amt' INT 'kills' INT 'armor' INT 'dmg' INT 'piercing-dmg' INT 'status' INT 'status-args' '(' INT? INT? ')' ')' );

/* (:player :id 0 :gold 2000 :wood 1000 :oil 1000 :supply 0 :demand 5) */
player
    : '(' ':player' ':id' INT labeled_val+ ')' -> ^(PLAYER INT labeled_val+);

labeled_val
    : LABEL INT -> ^(LABELED_VAL LABEL INT);

events
    : '(' ':events' event* ')' -> ^(EVENTS event*);

event
    : '(' 'built' INT INT ')' -> ^(BUILT INT INT)
    | '(' 'trained' INT INT ')' -> ^(TRAINED INT INT)
    | '(' 'died' INT ')' -> ^(DIED INT);

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

LABEL   : ':'NAME;

NAME	:('a'..'z'|'A'..'Z'|'_')(options{greedy=true;}:	'-'|'a'..'z'|'A'..'Z'|'_'|'0'..'9')*
	;

INT	: '-'?(DIGIT)+ ;

STRING : '\''.*'\'';

WHITESPACE : ( '\t' | ' ' | '\r' | '\n'| '\u000C' )+ 	{ skip(); } ;

fragment DIGIT	: '0'..'9' ;
