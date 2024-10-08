package plugins.VDM2UML;

import com.fujitsu.vdmj.lex.Token;
import com.fujitsu.vdmj.tc.definitions.TCAccessSpecifier;
import com.fujitsu.vdmj.tc.definitions.TCClassDefinition;
import com.fujitsu.vdmj.tc.definitions.TCDefinition;
import com.fujitsu.vdmj.tc.definitions.TCExplicitFunctionDefinition;
import com.fujitsu.vdmj.tc.definitions.TCExplicitOperationDefinition;
import com.fujitsu.vdmj.tc.definitions.TCImplicitFunctionDefinition;
import com.fujitsu.vdmj.tc.definitions.TCImplicitOperationDefinition;
import com.fujitsu.vdmj.tc.definitions.TCInstanceVariableDefinition;
import com.fujitsu.vdmj.tc.definitions.TCTypeDefinition;
import com.fujitsu.vdmj.tc.definitions.TCValueDefinition;
import com.fujitsu.vdmj.tc.definitions.visitors.TCDefinitionVisitor;
import com.fujitsu.vdmj.tc.types.TCType;

public class UMLGenerator extends TCDefinitionVisitor<Object, PlantBuilder>
{
	@Override
	public Object caseDefinition(TCDefinition node, PlantBuilder arg)
	{
		return null;
	}

	@Override
	public Object caseClassDefinition(TCClassDefinition node, PlantBuilder arg)
	{
		/**
		 * Create class definition 
		 */
		
		arg.defs.append("class ");
		arg.defs.append(node.name.getName());
		arg.defs.append("\n{\n");

		for (TCDefinition def: node.definitions)
		{
			def.apply(this, arg);
		}

		/**
		 * Create association to superclass if class is subclass
		 */

		for (TCType inheritedClass : node.supertypes)
		{
			arg.asocs.append(inheritedClass.toString());
			arg.asocs.append(" <|-- ");
			arg.asocs.append(node.name.getName());
			arg.asocs.append("\n");
		}

		arg.defs.append("}\n\n");
		return null;
	}
	
	@Override
	public Object caseInstanceVariableDefinition(TCInstanceVariableDefinition node, PlantBuilder arg)
	{	
		TCType type = node.getType();
		UMLType umlType = new UMLType(PlantBuilder.env, false);
		type.apply(new UMLAssociationCheckVisitor(), umlType);

		String visibility = visibility(node.accessSpecifier);
		String varName = node.name.getName();
		String className = node.classDefinition.name.getName();

		if (umlType.isAsoc)
		{
			/* 
			 * Create instance variable as association 
			 */

			arg.asocs.append(className);
			if (!umlType.qualifier.isEmpty())
			{
				arg.asocs.append(" \"[");
				arg.asocs.append(removeExcessSpaces(umlType.qualifier));
				arg.asocs.append("]\"");
			}
			arg.asocs.append(" --> ");
			if (!umlType.multiplicity.isEmpty())
			{
				arg.asocs.append("\"");
				arg.asocs.append(umlType.multiplicity);
				arg.asocs.append("\" ");
			}
			arg.asocs.append(removeExcessSpaces(umlType.endClass));
			arg.asocs.append(" : ");
			if (!visibility.isEmpty())
			{
				arg.asocs.append(visibility);
			}
			arg.asocs.append(varName);
			arg.asocs.append("\n");
		} 
		else 
		{
			/*
			 * Create instance variable as attribute in class 
			 */

			type = node.getType();
			UMLType umlInstanceType = new UMLType(PlantBuilder.env, false);
			type.apply(new UMLTypeVisitor(), umlInstanceType);

			arg.defs.append("\t");
			if (!visibility.isEmpty())
			{
				arg.defs.append(visibility);
			}
			arg.defs.append(varName + " : " + removeExcessSpaces(umlInstanceType.inClassType));
			arg.defs.append("\n");
		}
		
		return null;
	}
	
	@Override
	public Object caseTypeDefinition(TCTypeDefinition node, PlantBuilder arg)
	{
		/**
		 * Create type definition 
		 */

		TCType type = node.getType();
		UMLType umlType = new UMLType(PlantBuilder.env, true);
		umlType.namedType = node.name.getName();
		type.apply(new UMLTypeVisitor(), umlType);

		arg.defs.append("\t");
		arg.defs.append(visibility(node.accessSpecifier));
		arg.defs.append(node.name.getName());
		arg.defs.append(" : ");
		arg.defs.append(removeExcessSpaces(umlType.inClassType));
		arg.defs.append(" <<type>>");
		arg.defs.append("\n");

		return null; 
	}

	@Override
	public Object caseValueDefinition(TCValueDefinition node, PlantBuilder arg)
	{
		/**
		 * Create value definition 
		 */
		// TODO: Show what the value is?

		for (TCDefinition def : node.getDefinitions()) 
		{
			TCType type = def.getType();
			UMLType umlType = new UMLType(PlantBuilder.env, false);
			type.apply(new UMLTypeVisitor(), umlType);
			
			arg.defs.append("\t");
			arg.defs.append(visibility(def.accessSpecifier));
			arg.defs.append(def.name.getName());
			arg.defs.append(" : ");
			arg.defs.append(removeExcessSpaces(umlType.inClassType));
			arg.defs.append(" <<value>>");
			arg.defs.append("\n");
		}

		return null;
	}
	
	@Override
	public Object caseExplicitFunctionDefinition(TCExplicitFunctionDefinition node, PlantBuilder arg)
	{
		/**
		 * Create explicit function definition 
		 */

		TCType type = node.getType();
		UMLType umlType = new UMLType(PlantBuilder.env, false);
		type.apply(new UMLTypeVisitor(), umlType);

		arg.defs.append("\t");
		arg.defs.append(visibility(node.accessSpecifier));
		arg.defs.append(node.name.getName());
		arg.defs.append("(");
		arg.defs.append(removeExcessSpaces(umlType.paramsType));
		arg.defs.append(")");
		if (!(umlType.returnType.equals("") || umlType.returnType.equals("()")))
		{
			arg.defs.append(" : ");
			arg.defs.append(removeExcessSpaces(umlType.returnType));
		}
		arg.defs.append(" <<function>>");
		arg.defs.append("\n");

		return null;
	}
	
	@Override
	public Object caseExplicitOperationDefinition(TCExplicitOperationDefinition node, PlantBuilder arg)
	{	
		/**
		 * Create explicit operation definition 
		 */

		TCType type = node.getType();
		UMLType umlType = new UMLType(PlantBuilder.env, false);
		type.apply(new UMLTypeVisitor(), umlType);

		arg.defs.append("\t");
		arg.defs.append(visibility(node.accessSpecifier));
		arg.defs.append(node.name.getName());
		arg.defs.append("(");
		arg.defs.append(removeExcessSpaces(umlType.paramsType));
		arg.defs.append(")");
		if (!(umlType.returnType.equals("") || umlType.returnType.equals("()")))
		{
			arg.defs.append(" : ");
			arg.defs.append(removeExcessSpaces(umlType.returnType));
		}
		arg.defs.append("\n");

		return null;
	}

	public Object caseImplicitFunctionDefinition(TCImplicitFunctionDefinition node, PlantBuilder arg) {
		/**
		 * Create implicit function definition 
		 */

		TCType type = node.getType();
		UMLType umlType = new UMLType(PlantBuilder.env, false);
		type.apply(new UMLTypeVisitor(), umlType);

		arg.defs.append("\t");
		arg.defs.append(visibility(node.accessSpecifier));
		arg.defs.append(node.name.getName());
		arg.defs.append("(");
		arg.defs.append(removeExcessSpaces(umlType.paramsType));
		arg.defs.append(")");
		if (!(umlType.returnType.equals("") || umlType.returnType.equals("()")))
		{
			arg.defs.append(" : ");
			arg.defs.append(removeExcessSpaces(umlType.returnType));
		}
		arg.defs.append(" <<function>>");
		arg.defs.append("\n");

		return null;
	}
	  
	public Object caseImplicitOperationDefinition(TCImplicitOperationDefinition node, PlantBuilder arg) {
		/**
		 * Create implicit operation definition 
		 */

		TCType type = node.getType();
		UMLType umlType = new UMLType(PlantBuilder.env, false);
		type.apply(new UMLTypeVisitor(), umlType);

		arg.defs.append("\t");
		arg.defs.append(visibility(node.accessSpecifier));
		arg.defs.append(node.name.getName());
		arg.defs.append("(");
		arg.defs.append(removeExcessSpaces(umlType.paramsType));
		arg.defs.append(")");
		if (!(umlType.returnType.equals("") || umlType.returnType.equals("()")))
		{
			arg.defs.append(" : ");
			arg.defs.append(removeExcessSpaces(umlType.returnType));
		}
		arg.defs.append(" <<function>>");
		arg.defs.append("\n");

		return null;
	}

	private String visibility(TCAccessSpecifier access)
	{	
		/**
		 * Create get visibility token as string
		 */

		String res = "";

		if (access.access == Token.PUBLIC)
			res += "+";
		else if (access.access == Token.PRIVATE)
			res += "-";
		else if (access.access == Token.PROTECTED)
			res += "#";
		
		return res;
	}

	private String removeExcessSpaces(String before)
	{
		// For removing excess blank spaces

		String after = before.trim().replaceAll(" +", " ");
		return after;
	}

	static public StringBuilder buildBoiler(String name) 
	{
		// For building boiler plate source code for the puml file

		StringBuilder boiler = new StringBuilder();

		boiler.append("@startuml ");
		boiler.append(name);
		boiler.append("\n\n");
		boiler.append("hide empty members\n");
		boiler.append("skinparam Shadowing false\n");
		boiler.append("skinparam classAttributeIconSize 0\n");
		boiler.append("skinparam ClassBorderThickness 0.5\n");
		boiler.append("skinparam groupInheritance 5\n");
		boiler.append("skinparam class {\n");
		boiler.append("\tBackgroundColor AntiqueWhite\n");
		boiler.append("\tArrowColor Black\n");
		boiler.append("\tBorderColor Black\n}\n");
		boiler.append("skinparam defaultTextAlignment center\n");
		boiler.append("skinparam linetype ortho\n");
		boiler.append("skinparam Dpi 300\n");
		boiler.append("' skinparam backgroundColor transparent\n\n");
        
		return boiler;
	}
}