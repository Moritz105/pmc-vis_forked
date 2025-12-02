package prism.core;
import com.google.common.math.BigIntegerMath;
import parser.State;
import parser.VarList;
import parser.ast.Expression;
import parser.ast.ModulesFile;
import parser.ast.RewardStruct;
import parser.type.Type;
import parser.type.TypeBool;
import parser.type.TypeDouble;
import parser.type.TypeInt;

import prism.api.*;
import prism.core.*;
import simulator.Choice;
import simulator.TransitionList;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.HashMap;

import javax.ws.rs.client.Client;
import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
 
public class Diff {
    Project project;
    ModelParser parserleft;
    ModelParser parserright;
    Model model_1;
    Model model_2;
    private Map<String,VariableInfo> start = new TreeMap<>();
    private Map<String,VariableInfo> comp = new TreeMap<>();    

    public Diff(Project project) throws Exception{

        this.project =  project;
        for (File f : this.project.getPropertyFiles()) {
            String fileEnding = f.getName().substring(f.getName().lastIndexOf("."));
            if (fileEnding.equals(".prism")&&this.model_1==null) {
                this.model_1 = project.getModel(project.createModel(f));
            }else if (fileEnding.equals(".prism")) {
                this.model_2 = project.getModel(project.createModel(f));
            }
        }
        
    }

    public void comparestructure(Model left, Model right) throws Exception{
        this.parserleft = left.getModelParser();
        this.parserright = right.getModelParser();
        this.start = (Map<String,VariableInfo>) left.getInfo().getStateEntry(Namespace.OUTPUT_VARIABLES);
        this.comp = (Map<String,VariableInfo>) right.getInfo().getStateEntry(Namespace.OUTPUT_VARIABLES);
        //bekomme ich hier die richtige Map? weil info ein zweites Mal neu definiert wird


        for (Map.Entry entry : start.entrySet()) {
            VariableInfo s_var = (VariableInfo) entry.getValue();
            for (Map.Entry entrie : comp.entrySet()){
                VariableInfo c_var = (VariableInfo) entrie.getValue();
                boolean min=false;
                boolean max=false;
                if (s_var.getType()==c_var.getType()) {
                    System.out.println("Var " + s_var.getVariableName() + c_var.getVariableName() + " gleicher Typ");
                    if (s_var.getMin()==c_var.getMin()) {
                        System.out.println("Var gleiche min: " + s_var.getMin());
                        min=true;
                    }
                    if (s_var.getMax()==c_var.getMax()) {
                        System.out.println("Var gleiche max: " + s_var.getMax());
                        max=true;
                    }
                    if (min&&max) {
                        System.out.println("Variable " + s_var.getVariableName() + c_var.getVariableName() + " identisch");
                    }
                }else{System.out.println("Funktion als falsche if aufgeführt");}
            }

        } 
        if (parserleft.getInitialNodes().getStates().size()==parserright.getInitialNodes().getStates().size()) {
            System.out.println("Gleiche Anzahl an Startknoten");
        }



        
    }
        
}
        