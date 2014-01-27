package wyscript.error;

import static wyscript.util.SyntaxError.outputSourceError;
import static wyscript.util.SyntaxError.outputSuggestion;

import java.util.List;
import java.util.Map;

import wyscript.Main;
import wyscript.lang.Expr;
import wyscript.lang.Stmt;
import wyscript.lang.Type;
import wyscript.lang.WyscriptFile.FunDecl;
import wyscript.lang.WyscriptFile.Parameter;
import wyscript.util.Attribute;

/**
 * Class that takes error data generated by the type checker and uses it to generate
 * error messages and code suggestions for the user.
 *
 * @author Daniel Campbell
 *
 */
public class TypeErrorHandler {



	public static void handle(List<TypeErrorData> errors, Map<String, Type> userTypes) {
		for (TypeErrorData data : errors) {

			String msg = "";
			String suggestion = "";

			switch (data.type()) {

			case BAD_FIELD_ACCESS:
				Expr.RecordAccess ra = (Expr.RecordAccess) data.found();
				msg = String.format("Error: cannot access field %s of non-record expression %s", ra.getName(), ra.getSource());
				suggestion = null; //Can't make a suggestion without more info of user's intent
				break;

			case BAD_FOR_LIST:
				msg = String.format("Error: for loop expression %s invalid, must be a list type", data.found());
				suggestion = "[" + data.found() + "]";
				break;

			case BAD_FUNC_PARAMS:
				FunDecl fd = (FunDecl) data.expected();
				Expr.Invoke ei = (Expr.Invoke) data.found();
				msg = String.format("Error: function call %s has incorrect number of paramaters", ei);

				String params = "(";
				boolean first = true;

				for (Parameter p : fd.parameters) {
					if (!first)
						params += ", ";
					first = false;
					params += getExampleOfType(p.type, userTypes);
				}
				params += ")";
				suggestion = ei.getName() + params;
				break;

			case BAD_NEXT:
				msg = "Error: Next statement must be inside switch case/default body";
				suggestion = null; //clearing the statement leaves a blank suggestion - just skip making a suggestion
				break;

			case BAD_SWITCH_TYPE:
				msg = "Error: switch expression may not be a record, tuple or reference type";
				suggestion = "[" + data.found() + "]";
				break;

			case DUPLICATE_VARIABLE:
				Stmt.VariableDeclaration vd = (Stmt.VariableDeclaration) data.expected();
				msg = "Error: variable with name " + vd.getName() + " has already been declared";
				suggestion = vd.getType() + " " + vd.getName() + "Copy";
				if (vd.getExpr() != null) {
					String typeChar = "";
					if (vd.getType() instanceof Type.Strung)
						typeChar = "\"";
					else if (vd.getType() instanceof Type.Char)
						typeChar = "'";
					suggestion += String.format(" = %s%s%s", typeChar, vd.getExpr(), typeChar);
				}
				break;

			case MISSING_FIELD:
				Expr.RecordAccess e = (Expr.RecordAccess) data.found();
				msg = String.format("Error: expression %s does not have field %s", e.getSource(), e.getName());
				suggestion = null; //Can't make a suggestion without more info of user's intent
				break;

			case MISSING_RETURN:
				FunDecl f = (FunDecl) data.expected();
				msg = String.format("Error: non-void function %s must return a value of type %s", f.name, f.ret);
				suggestion = "\nreturn " + getExampleOfType(f.ret, userTypes);
				break;

			case TYPE_MISMATCH:
				Type ex = (Type) data.expected();
				String type = (ex instanceof Type.List) ? "list" : "record";
				Type t = data.found().attribute(Attribute.Type.class).type;
				msg = String.format("Error: %s has type %s, expected instance of %s type", data.found(), t, type);
				suggestion = getExampleOfType(ex, userTypes);
				break;

			case SUBTYPE_MISMATCH:
				//in this case due to parameter constraints we have stored the found variable in the expected field
				//and stored the expected type in a dummy cast expression
				Type found = data.expected().attribute(Attribute.Type.class).type;
				Type expected = ((Expr.Cast)data.found()).getType();
				msg = String.format("Error: %s has type %s, expected %s or subtype of %s", data.expected(), found, expected, expected);
				suggestion = getExampleOfType(expected, userTypes);
				break;

			case UNDECLARED_VARIABLE:
				msg = "Error: variable " + data.found().toString() + " has not been declared";
				suggestion = null; //No way of determining variable's intended type
				break;

			case BAD_TUPLE_ASSIGN:
				msg = "Error: tuple " + data.found() + " contains expr " + data.expected() + " that cannot be assigned to";
				Type fnd = data.found().attribute(Attribute.Type.class).type;
				suggestion = getExampleOfType(fnd, userTypes);
				break;
			}

			outputSourceError(Main.errout, msg, data.filename(), data.start(), data.end());
			if (suggestion != null)
				outputSuggestion(Main.errout, suggestion, data.filename(), data.start(), data.end());
		}
		throw new HandledException();
	}

	/**
	 * Returns the string representation of an example value for a given type.
	 */
	private static String getExampleOfType(Type t, Map<String, Type> userTypes) {
		String s = "";
		if (t instanceof Type.Int)
			s += "0";
		else if (t instanceof Type.Strung)
			s += "\"\"";
		else if (t instanceof Type.Real)
			s += "0.1";
		else if (t instanceof Type.Char)
			s += "'a'";
		else if (t instanceof Type.Union)
			s += getExampleOfType(((Type.Union)t).getBounds().get(0), userTypes);
		else if (t instanceof Type.Null)
			s += "null";
		else if (t instanceof Type.List)
			s += "[]";
		else if (t instanceof Type.Named)
			s += getExampleOfType(userTypes.get(((Type.Named) t).getName()), userTypes);
		else if (t instanceof Type.Bool) {
			s += "true";
		}
		else if (t instanceof Type.Record){
			Type.Record r = (Type.Record) t;
			s += "{";
			boolean first = true;
			for (String name : r.getFields().keySet()) {
				if (!first)
					s += ", ";
				first = false;
				s += name + " : " + getExampleOfType(r.getFields().get(name), userTypes);
			}
			s += "}";
		}
		else if (t instanceof Type.Reference) {
			s += "new " + getExampleOfType(((Type.Reference)t).getType(), userTypes);
		}
		else if (t instanceof Type.Tuple) {
			s += "(";
			boolean first = true;
			for (Type tup : ((Type.Tuple)t).getTypes()) {
				if (!first)
					s += ", ";
				first = false;
				s += getExampleOfType(tup, userTypes);
			}
			s += ")";
		}
		return s;
	}

}
