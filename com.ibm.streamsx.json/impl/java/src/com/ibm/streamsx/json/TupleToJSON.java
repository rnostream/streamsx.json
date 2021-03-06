//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.json;

import java.util.Arrays;
import java.util.logging.Logger;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.Type;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streamsx.json.converters.TupleToJSONConverter;
import com.ibm.streamsx.json.converters.TupleTypeVerifier;

@InputPorts(@InputPortSet(cardinality=1, optional=false))
@OutputPorts(@OutputPortSet(cardinality=1, optional=false))
@PrimitiveOperator(name="TupleToJSON", description=TupleToJSON.DESC)
@Libraries("lib/com.ibm.streamsx.json.converters.jar")
public class TupleToJSON extends AbstractOperator {

	private String jsonStringAttribute = null;
	private static final String defaultJsonStringAttribute = "jsonString"; //$NON-NLS-1$
	private static final String ROOT_ATTRIBUTE_PARAM = "inputAttribute"; //$NON-NLS-1$
	TupleAttribute<Tuple,?> rootAttr = null;
	private String rootAttribute = null;
	private Type rootAttributeType =null;
	private boolean wasPrefixToIgnoreSpecified = false;
	private String prefixToIgnore = null;
	
	private static Logger l = Logger.getLogger(TupleToJSON.class.getCanonicalName());

	@Parameter(name=ROOT_ATTRIBUTE_PARAM,
			optional = true,
			description="Input stream attribute to be used as the root of the JSON object. Required type for attribute is `tuple`, `list` or `set`.  Default is the input tuple.  This parameter specfies the attribute, not its name, and is preferred to the `rootAttribute` parameter which specifies the attribute's name.")
	public void setRoot(TupleAttribute<Tuple,?> in) {
		rootAttr = in;
	}
	
	@Parameter(optional=true, 
			description="Name of the output stream attribute where the JSON string will be populated. Default is `jsonString`.")
	public void setJsonStringAttribute(String value) {
		this.jsonStringAttribute = value;
	}
	@Parameter(optional=true, 
			description="Deprecated. Name of the input stream attribute to be used as the root of the JSON object. Replaced by parameter `inputAttribute` which specifies the attribute rather than the attribute's name. Required type is `tuple`, `list` or `set`. Default is the input tuple.")
	public void setRootAttribute(String value) {
		this.rootAttribute = value;
	}

	@Parameter(optional=true, description=
			"Specifies a string that, if present, is removed from the start of an attribute name." +
			"You can use this method for JSON that contains elements or attributes with SPL or C++ keywords." +
			"For example:\\n" +
			"\\n" + 
			"    stream <rstring jsonString> A = TupleToJSON(Input) {\\n" + 
			"      param ignorePrefix : \\\"__\\\";\\n" +
			"    }\\n" +
			"\\n" +
			"This example accepts JSON of the following form: \\n" +
			"\\n" +
			"    {\\\"graph\\\" : \\\"value\\\"}\\n" +
			"\\n" +
			"Since graph is an SPL keyword, stream<rstring graph> as input schema of TupleToJSON is not valid SPL.")
	public void setPrefixToIgnore(String value) {
		this.prefixToIgnore = value;
		wasPrefixToIgnoreSpecified=true;
	}
	
	@Override
	public void initialize(OperatorContext op) throws Exception {
		super.initialize(op);
		
		StreamSchema ssop = getOutput(0).getStreamSchema();
		if (jsonStringAttribute == null) {
			// If we haven't set it using an argument, then...
			if (ssop.getAttributeCount() > 1) {
				// when there's more than one attribute, use the default jsonString
				jsonStringAttribute = defaultJsonStringAttribute;
			}
			else {
				// Only one attribute; so it's clear what to use.
				jsonStringAttribute = ssop.getAttribute(0).getName();
			}
		}
		TupleTypeVerifier.verifyAttributeType(ssop, jsonStringAttribute, 
				Arrays.asList(MetaType.RSTRING, MetaType.USTRING));

		StreamSchema ssip = getInput(0).getStreamSchema();
	
		if (rootAttr != null) {
			rootAttribute = rootAttr.getAttribute().getName();
		}
		
		if(rootAttribute!=null) {
			rootAttributeType = TupleTypeVerifier.verifyAttributeType(ssip, rootAttribute, 
					Arrays.asList(MetaType.TUPLE, MetaType.LIST, MetaType.BLIST, MetaType.SET, MetaType.BSET));
			l.log(TraceLevel.INFO, "Will use source attribute: " + rootAttribute); //$NON-NLS-1$
		}
	}

	public void process(StreamingInput<Tuple> stream, Tuple tuple) throws Exception 	{
		StreamingOutput<OutputTuple> ops = getOutput(0);
		final String jsonData;
		if(rootAttribute == null) 
			jsonData = TupleToJSONConverter.convertTuple(tuple);
		else {
			if(rootAttributeType.getMetaType() == MetaType.TUPLE)
				jsonData = TupleToJSONConverter.convertTuple(tuple.getTuple(rootAttribute));
			else 
				jsonData = TupleToJSONConverter.convertArray(tuple, rootAttribute);
		}
		OutputTuple op = ops.newTuple();
		op.assign(tuple);//copy over all relevant attributes form the source tuple
		op.setString(jsonStringAttribute, ((wasPrefixToIgnoreSpecified) ? jsonData.replaceAll("\""+this.prefixToIgnore, "\"") : jsonData));
		ops.submit(op);
	}

	static final String DESC = 
			"This operator converts incoming tuples to JSON String." + //$NON-NLS-1$
			" Note that any matching attributes from the input stream will be copied over to the output." + //$NON-NLS-1$
			" If an attribute, with the same name as the JSON string output attribute exists in the input stream, " + //$NON-NLS-1$
			"it will be overwritten by the JSON String that is generated." ; //$NON-NLS-1$
}
