/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.runtime.instructions.cp;

import org.apache.sysml.lops.WeightedDivMM.WDivMMType;
import org.apache.sysml.lops.WeightedSigmoid.WSigmoidType;
import org.apache.sysml.lops.WeightedSquaredLoss.WeightsType;
import org.apache.sysml.lops.WeightedCrossEntropy.WCeMMType;
import org.apache.sysml.lops.WeightedUnaryMM.WUMMType;
import org.apache.sysml.runtime.DMLRuntimeException;
import org.apache.sysml.runtime.DMLUnsupportedOperationException;
import org.apache.sysml.runtime.controlprogram.context.ExecutionContext;
import org.apache.sysml.runtime.instructions.InstructionUtils;
import org.apache.sysml.runtime.matrix.data.MatrixBlock;
import org.apache.sysml.runtime.matrix.data.MatrixValue;
import org.apache.sysml.runtime.matrix.operators.Operator;
import org.apache.sysml.runtime.matrix.operators.QuaternaryOperator;

/**
 * 
 * 
 */
public class QuaternaryCPInstruction extends ComputationCPInstruction
{
	
	private CPOperand input4 = null;
 	private int _numThreads = -1;
	
	public QuaternaryCPInstruction(Operator op, CPOperand in1, CPOperand in2, CPOperand in3, CPOperand in4, CPOperand out, 
							       int k, String opcode, String istr )
	{
		super(op, in1, in2, in3, out, opcode, istr);
		
		input4 = in4;
		_numThreads = k;
	}

	/**
	 * 
	 * @param inst
	 * @return
	 * @throws DMLRuntimeException
	 */
	public static QuaternaryCPInstruction parseInstruction(String inst) 
		throws DMLRuntimeException
	{	
		String[] parts = InstructionUtils.getInstructionPartsWithValueType(inst);
		String opcode = parts[0];
		
		if( opcode.equalsIgnoreCase("wsloss") || opcode.equalsIgnoreCase("wdivmm") ) 
		{
			InstructionUtils.checkNumFields ( parts, 7 );
			
			CPOperand in1 = new CPOperand(parts[1]);
			CPOperand in2 = new CPOperand(parts[2]);
			CPOperand in3 = new CPOperand(parts[3]);
			CPOperand in4 = new CPOperand(parts[4]);
			CPOperand out = new CPOperand(parts[5]);
			int k = Integer.parseInt(parts[7]);
			
			if( opcode.equalsIgnoreCase("wsloss") )
				return new QuaternaryCPInstruction(new QuaternaryOperator(WeightsType.valueOf(parts[6])), in1, in2, in3, in4, out, k, opcode, inst);	
			else if( opcode.equalsIgnoreCase("wdivmm") )
				return new QuaternaryCPInstruction(new QuaternaryOperator(WDivMMType.valueOf(parts[6])), in1, in2, in3, in4, out, k, opcode, inst);				
		}
		else if( opcode.equalsIgnoreCase("wsigmoid") || opcode.equalsIgnoreCase("wcemm") )
		{
			InstructionUtils.checkNumFields ( parts, 6 );
			
			CPOperand in1 = new CPOperand(parts[1]);
			CPOperand in2 = new CPOperand(parts[2]);
			CPOperand in3 = new CPOperand(parts[3]);
			CPOperand out = new CPOperand(parts[4]);
			int k = Integer.parseInt(parts[6]);
			
			if( opcode.equalsIgnoreCase("wsigmoid") )
				return new QuaternaryCPInstruction(new QuaternaryOperator(WSigmoidType.valueOf(parts[5])), in1, in2, in3, null, out, k, opcode, inst);
			else if( opcode.equalsIgnoreCase("wcemm") ) 		
				return new QuaternaryCPInstruction(new QuaternaryOperator(WCeMMType.valueOf(parts[5])), in1, in2, in3, null, out, k, opcode, inst);
		}
		else if( opcode.equalsIgnoreCase("wumm") )
		{
			InstructionUtils.checkNumFields ( parts, 7 );
			
			String uopcode = parts[1];
			CPOperand in1 = new CPOperand(parts[2]);
			CPOperand in2 = new CPOperand(parts[3]);
			CPOperand in3 = new CPOperand(parts[4]);
			CPOperand out = new CPOperand(parts[5]);
			int k = Integer.parseInt(parts[7]);
			
			return new QuaternaryCPInstruction(new QuaternaryOperator(WUMMType.valueOf(parts[6]),uopcode), in1, in2, in3, null, out, k, opcode, inst);
		}
		
		throw new DMLRuntimeException("Unexpected opcode in QuaternaryCPInstruction: " + inst);
	}

	
	@Override
	public void processInstruction(ExecutionContext ec) 
		throws DMLRuntimeException, DMLUnsupportedOperationException
	{
		QuaternaryOperator qop = (QuaternaryOperator) _optr;
		
		MatrixBlock matBlock1 = ec.getMatrixInput(input1.getName());
		MatrixBlock matBlock2 = ec.getMatrixInput(input2.getName());
		MatrixBlock matBlock3 = ec.getMatrixInput(input3.getName());
		MatrixBlock matBlock4 = null;
		if( qop.hasFourInputs() ) {
			matBlock4 = ec.getMatrixInput(input4.getName());
		}
		
		//core execute
		MatrixValue out = matBlock1.quaternaryOperations(qop, matBlock2, matBlock3, matBlock4, new MatrixBlock(), _numThreads);
		
		//release inputs and output
		ec.releaseMatrixInput(input1.getName());
		ec.releaseMatrixInput(input2.getName());
		ec.releaseMatrixInput(input3.getName());
		if( qop.wtype1 != null || qop.wtype4 != null ) { //wsloss/wcemm
			if( qop.wtype1 != null && qop.wtype1.hasFourInputs() )
				ec.releaseMatrixInput(input4.getName());
			ec.setVariable(output.getName(), new DoubleObject(out.getValue(0, 0)));
		}
		else { //wsigmoid / wdivmm / wumm
			if( qop.wtype3 != null && qop.wtype3.hasFourInputs() )
				ec.releaseMatrixInput(input4.getName());
			ec.setMatrixOutput(output.getName(), (MatrixBlock)out);
		}
	}	
}
