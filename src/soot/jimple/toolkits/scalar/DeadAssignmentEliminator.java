/* Soot - a J*va Optimization Framework
 * Copyright (C) 1997-1999 Raja Vallee-Rai
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/*
 * Modified by the Sable Research Group and others 1997-1999.  
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */






package soot.jimple.toolkits.scalar;

import soot.*;
import soot.jimple.*;
import soot.toolkits.scalar.*;
import soot.util.*;
import soot.toolkits.graph.*;
import java.util.*;

public class DeadAssignmentEliminator extends BodyTransformer
{
    private static DeadAssignmentEliminator instance = new DeadAssignmentEliminator();
    private DeadAssignmentEliminator() {}

    public static DeadAssignmentEliminator v() { return instance; }

    /** Eliminates dead code in a linear fashion.  Complexity is linear 
        with respect to the statements.
        
        Does not work on grimp code because of the check on the right hand
        side for side effects. 
    */
    
    protected void internalTransform(Body b, String phaseName, Map options)
    {
        JimpleBody body = (JimpleBody)b;
        if(Main.isVerbose)
            System.out.println("[" + body.getMethod().getName() +
                "] Eliminating dead code...");
        
        if(Main.isProfilingOptimization)
            Main.deadCodeTimer.start();

        Set essentialStmts = new HashSet();
        LinkedList toVisit = new LinkedList();
        Chain units = body.getUnits();
        
        // Make a first pass through the statements, noting 
        // the statements we must absolutely keep. 
        {
            Iterator stmtIt = units.iterator();
            
            while(stmtIt.hasNext()) 
            {
                Stmt s = (Stmt) stmtIt.next();
                boolean isEssential = true;
                 
                if(s instanceof NopStmt)
                    isEssential = false;
                 
                if(s instanceof AssignStmt)
                {
                    AssignStmt as = (AssignStmt) s;
                    
                    if(as.getLeftOp() instanceof Local)
                    {
                        Value rhs = as.getRightOp();
                    
                        isEssential = false;

                        if(rhs instanceof InvokeExpr ||
                           rhs instanceof InstanceFieldRef ||
                           rhs instanceof ArrayRef)
                        {
                           // Note that InstanceFieldRef, ArrayRef, InvokeExpr all can
                           // have side effects (like throwing a null pointer exception)
                    
                            isEssential = true;
                        }
                        else if(rhs instanceof DivExpr || 
                            rhs instanceof RemExpr)
                        {
                            BinopExpr expr = (BinopExpr) rhs;
                            
                            if(expr.getOp1().getType().equals(IntType.v()) ||
                                expr.getOp2().getType().equals(IntType.v()) ||
                               expr.getOp1().getType().equals(LongType.v()) ||
                                expr.getOp2().getType().equals(LongType.v()))
                            {
                                // Can trigger a division by zero   
                                isEssential = true;    
                            }        
                        }
                    }
                }
                
                if(isEssential)
                {
                    essentialStmts.add(s);
                    toVisit.addLast(s);                    
                }                     
            }
        }
        
        // Add all the statements which are used to compute values
        // for the essential statements, recursively
        {
            CompleteUnitGraph graph = new CompleteUnitGraph(body);
            LocalDefs defs = new SimpleLocalDefs(graph);
            
            while(!toVisit.isEmpty())
            {
                Stmt s = (Stmt) toVisit.removeFirst();
                Iterator boxIt = s.getUseBoxes().iterator();
                                
                while(boxIt.hasNext())
                {
                    ValueBox box = (ValueBox) boxIt.next();
                    
                    if(box.getValue() instanceof Local)
                    {
                        Iterator defIt = defs.getDefsOfAt(
                            (Local) box.getValue(), s).iterator();
                        
                        while(defIt.hasNext())
                        {
                            // Add all the definitions as essential stmts
                            
                            Stmt def = (Stmt) defIt.next();
                            
                            if(!essentialStmts.contains(def))
                            {
                                essentialStmts.add(def);
                                toVisit.addLast(def);
                            }    
                        }         
                    }
                }
            }
        }
        
        // Remove some dead statements
        {
            Iterator stmtIt = units.iterator();
            
            while(stmtIt.hasNext())
            {
                Stmt s = (Stmt) stmtIt.next();
                
                if(!essentialStmts.contains(s))
                    stmtIt.remove();
                else if(s instanceof AssignStmt &&
                    ((AssignStmt) s).getLeftOp() == 
                    ((AssignStmt) s).getRightOp() &&
                    ((AssignStmt) s).getLeftOp() instanceof Local)
                {
                    // Stmt is of the form a = a which is useless
                    
                    stmtIt.remove();
                }   
            }
        }
        
        if(Main.isProfilingOptimization)
            Main.deadCodeTimer.end();

    }
}







