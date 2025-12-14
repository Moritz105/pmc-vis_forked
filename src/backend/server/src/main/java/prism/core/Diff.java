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
        Map<String, List<String>> mapping = new HashMap<>();

        for (Map.Entry entry : start.entrySet()) {
            VariableInfo s_var = (VariableInfo) entry.getValue();
            for (Map.Entry entrie : comp.entrySet()) {
                VariableInfo c_var = (VariableInfo) entrie.getValue();

                if (s_var.getType() == c_var.getType()) {
                    if (s_var.getMin() == c_var.getMin() && s_var.getMax() == c_var.getMax()) {
                        mapping.computeIfAbsent(s_var.getVariableName(), k -> new ArrayList<>()).add(c_var.getVariableName());

                    }

                } else {
                    System.out.println("Keine mapbare Variable");
                }
            }

        }System.out.println(mapping);
        //calc all possibilities
        List<Map<String, String>> all_pos = getDistribution(mapping, new ArrayList<>(mapping.keySet()), 0, new HashMap<>(), new HashSet<>(), new ArrayList<>());
        System.out.println(all_pos);

        if (left.getInitialNodes().getStates().size()==right.getInitialNodes().getStates().size()) {
            System.out.println("Gleiche Anzahl an Startknoten");
        }
        if (parserleft.getGraph().getTransitions().size()==parserright.getGraph().getTransitions().size()) {
            System.out.println("Gleiche Anzahl an Transitions");
        }
        if (parserleft.getGraph().getStates().size()==parserright.getGraph().getStates().size()) {
            System.out.println("Gleiche Anzahl an Knoten");
        }

    }

    public List<Map<String, String>> getDistribution( Map<String, List<String>> mapping, List<String> elements, int index, Map<String, String> current, Set<String> visited, List<Map<String, String>> distributions){
        elements.sort(Comparator.comparingInt(v -> mapping.get(v).size()));

        //terminante and add config if all var have been visited
        if (index == elements.size()){
            distributions.add(new HashMap<>(current));
            return distributions;
        }
        String var = elements.get(index);

        for (String check : mapping.get(var)){
            if (visited.contains(check)) continue;
            current.put(var, check);
            visited.add(check);

            getDistribution(mapping, elements, index+1, current, visited, distributions);

            current.remove(var);
            visited.remove(check);
        }return distributions;
    }
        
}
        