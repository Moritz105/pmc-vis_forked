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
import java.util.stream.Collectors;

public class Diff {
    Project project;
    Model left;
    Model right;
    ModelParser parserleft;
    ModelParser parserright;
    private Map<String,VariableInfo> start = new TreeMap<>();
    private Map<String,VariableInfo> comp = new TreeMap<>();
    private List<Map<String, String>> possibilities = new ArrayList<>();
    private Map<Integer, List<Integer>> predecessors = new HashMap<>();
    private Map<Integer, List<Integer>> successors = new HashMap<>();
    private Map<String, Integer> stringToInt = new HashMap<>();
    private Map<Integer, String> intToString = new HashMap<>();
    private int nextID = 0;
    private int[] degree = new int[512];
    private BitSet idsInL = new BitSet();
    private BitSet idsInR = new BitSet();
    private Set<Integer> realCauses = new HashSet<>();
    private Map<String, prism.api.State> stringToState = new HashMap<>();
    private List<BitSet> finalPartitions;

    public Diff(Project project, Model left, Model right) throws Exception{
        System.out.println("starting diff");
        this.project =  project;
        this.left = left;
        this.right = right;
        this.parserleft = left.getModelParser();
        this.parserright = right.getModelParser();
        this.start = (Map<String,VariableInfo>) left.getInfo().getStateEntry(Namespace.OUTPUT_VARIABLES);
        this.comp = (Map<String,VariableInfo>) right.getInfo().getStateEntry(Namespace.OUTPUT_VARIABLES);
        this.possibilities = compareVariables();
        buildPredecessorMap();
        this.predecessors = predecessors;
        System.out.println(predecessors);
        //System.out.println(possibilities);

    }

    public  List<Map<String, String>> compareVariables() throws Exception{

        Map<String, List<String>> mapping = new HashMap<>();

        for (Map.Entry entry : start.entrySet()) {
            VariableInfo s_var = (VariableInfo) entry.getValue();
            for (Map.Entry entrie : comp.entrySet()) {
                VariableInfo c_var = (VariableInfo) entrie.getValue();

                if (s_var.getType() == c_var.getType()) {
                    if (s_var.getMin() == c_var.getMin() && s_var.getMax() == c_var.getMax()) {
                        mapping.computeIfAbsent(s_var.getVariableName(), k -> new ArrayList<>()).add(c_var.getVariableName());
                    }
                }
            }
        }//System.out.println(mapping);
        return calcDistribution(mapping, new ArrayList<>(mapping.keySet()), 0, new HashMap<>(), new HashSet<>(), new ArrayList<>());
    }

    public List<Map<String, String>> calcDistribution( Map<String, List<String>> mapping, List<String> elements, int index, Map<String, String> current, Set<String> visited, List<Map<String, String>> distributions){
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

            calcDistribution(mapping, elements, index+1, current, visited, distributions);

            current.remove(var);
            visited.remove(check);
        }return distributions;
    }

    public List<Map<String, String>> getDistributions(){
        return possibilities;
    }

    public int getInitNodesLeft() throws Exception{
        return parserleft.getInitialNodes().getStates().size();
    }
    public int getInitNodesRight() throws Exception{
        return parserright.getInitialNodes().getStates().size();
    }

    public int getTransitionsLeft() throws Exception{
        return parserleft.getGraph().getTransitions().size();
    }

    public int getTransitionsRight() throws Exception{
        return parserright.getGraph().getTransitions().size();
    }

    public int getNodesLeft() throws Exception{
        return parserleft.getGraph().getStates().size();
    }

    public int getNodesRight() throws Exception{
        return parserright.getGraph().getStates().size();
    }
    //Zum Vergleich berechnen wir die Vorgänger
    public void buildPredecessorMap() throws Exception{
        fillPredeccessor(parserleft, true);
        fillPredeccessor(parserright, false);
    }
    public void fillPredeccessor(ModelParser parser, boolean isLeft) throws Exception{
        System.out.println("entered filling predessessors");
        prism.api.Graph graph = parser.getGraph();
        System.out.println("parser.getgraph() finished");
        for (Edge e: graph.getEdges()){
            String srcName = parser.normalizeStateName(e.getSource());
            String trgName = parser.normalizeStateName(e.getTarget());
            //System.out.println("src:" +  srcName + " trg:" + trgName);
            int srcId = getOrCreateId(srcName);
            int trgId = getOrCreateId(trgName);
            if (isLeft){
                idsInL.set(srcId);
                idsInL.set(trgId);
            }else{
                idsInR.set(srcId);
                idsInR.set(trgId);
            }
            if (srcId >= degree.length){
                degree = Arrays.copyOf(degree, Math.max(degree.length *2, srcId + 1));
            }
            degree[srcId]++;
            successors.computeIfAbsent(srcId, k -> new ArrayList<>()).add(trgId);
            predecessors.computeIfAbsent(trgId, k -> new ArrayList<>()).add(srcId);
        }
    }

    public int getOrCreateId(String normalized) throws Exception{
        return stringToInt.computeIfAbsent(normalized, k -> {
            int id = nextID++;
            intToString.put(id, k);
            return id;
        });
    }

    public List<BitSet> createOrderByDegree(){
        Map<Integer,BitSet> partitionMap = new HashMap<>();
        for (int i = 0; i<nextID; i++){
            int oDegree = degree[i];
            BitSet group = partitionMap.computeIfAbsent(oDegree,k -> new BitSet());
            group.set(i);
        }
        return new ArrayList<>(partitionMap.values());
    }

    public Map<String, String> matchNodes() throws Exception{
        List<BitSet> partitions = createOrderByDegree();
        List<BitSet> worklist = new LinkedList<>(partitions);
        System.out.println("Start matching");
        while (!worklist.isEmpty()){
            BitSet splitter = worklist.remove(0); //take out first element
            BitSet splitterRel = calcSplitterRel(splitter); //calc its predecessors
            ListIterator<BitSet> it = partitions.listIterator();
            while (it.hasNext()) {
                BitSet candidate = it.next();
                splitBlockIfNessesary(it, candidate, splitterRel, worklist, splitter);
            }
        }
        this.finalPartitions = partitions;
        //now compare if theres only bitsets left with one node of each model
        return getColorDiff(partitions);
    }

    public BitSet calcSplitterRel(BitSet splitter){
        BitSet splitterRel = new BitSet();
        for (int i = splitter.nextSetBit(0); i >= 0; i = splitter.nextSetBit(i+1)) {
            if (predecessors.get(i)!= null){
                for (int pred : predecessors.get(i)) {
                    splitterRel.set(pred);
                }
            }
        }return splitterRel;
    }
    public void splitBlockIfNessesary(ListIterator<BitSet> it, BitSet candidate, BitSet splitterRel, List<BitSet> worklist, BitSet splitter){
        BitSet refined = (BitSet) candidate.clone();
        refined.and(splitterRel); //filter all nodes with a successor in the splitter

        if (!refined.isEmpty() && refined.cardinality()<candidate.cardinality()){
            boolean splitterIsMixed = containsModel(splitter, true) && containsModel(splitter, false);
            // if true, there are nodes that have a successore in the splitter and some that don't
            // thats why they can't be compared and need to be splitted
            candidate.andNot(splitterRel); //nodes that can't reach the splitter
            if (splitterIsMixed){
                if (isPure(candidate) || isPure(refined)){
                    for (int i = refined.nextSetBit(0); i >= 0; i = refined.nextSetBit(i+1)) {
                        realCauses.add(i);
                    }
                }
            }
            it.add(refined); //add new BitSet with nodes that can reach the splitter
            updateWorklist(worklist, candidate, refined);
        }
    }

    public void updateWorklist(List<BitSet> worklist, BitSet missingSplitter , BitSet havingSplitter) {
        if (worklist.contains(missingSplitter)){
            //block hasn't been used as splitter, but now we know ist actually 2 blocks so we need to add both
            //missingSplit block is already modified due to condidate.andNot, so no adding needed
            worklist.add(havingSplitter);
        }else{ //block was already succesfully used as splitter.
            // so we now need to add the smaller part (bc its faster) back to the worklist
            // that works bc of paige tarjan alg
            if (havingSplitter.cardinality() <= missingSplitter.cardinality()){
                worklist.add(havingSplitter);
            }else{
                worklist.add(missingSplitter);
            }
        }
    }

    public HashMap<String, String> getColorDiff(List<BitSet> partitions) throws Exception{
        Map<String, List<String>> colorDiff = new HashMap<>();
        List<String> red=  new  ArrayList<>(); // any node not in m2
        List<String> green = new  ArrayList<>(); //any node new in m2
        List<String> blue = new   ArrayList<>(); // node that misses edge and causes avalanche of red/green states on parents
        List<String> halo = new  ArrayList<>(); // nodes on trace to blue

        for (String name : stringToInt.keySet()){
            boolean inL = existsInModel(name, idsInL);
            boolean inR = existsInModel(name, idsInR);

            if (inL && !inR){red.add(name);}
            else if(!inL && inR){green.add(name);}
        }
        boolean changed = true;
        while (changed){
            changed = false;
            for (String name : stringToInt.keySet()){
                if (existsInModel(name, idsInL) && existsInModel(name, idsInR) && !blue.contains(name) && !halo.contains(name)){
                    Classification res = classifyDivergeence(name, red, green, blue, halo);
                    if (res==Classification.BLUE){
                        blue.add(name);
                        changed = true;
                    }else if (res == Classification.HALO){
                        halo.add(name);
                        changed = true;
                    }
                }
            }
        }
        colorDiff.put("red", red);
        colorDiff.put("green", green);
        colorDiff.put("blue", blue);
        colorDiff.put("halo", halo);
        System.out.println("colorDiff:"+colorDiff);
        HashMap<String, String> colorSwitch = new HashMap<>();
        for (Map.Entry entry: colorDiff.entrySet()){
            String hue = (String) entry.getKey();
            List<String> nodes = (List<String>) entry.getValue();
            for (String node : nodes){
                colorSwitch.put(node, hue);
            }
        }
        this.left.setColors(colorSwitch);
        this.right.setColors(colorSwitch);
        parserleft.getGraph();
        parserright.getGraph();
        return colorSwitch;
    }

    public boolean existsInModel(String name, BitSet modelIds){
        Integer id = stringToInt.get(name);
        return id != null && modelIds.get(id);
    }

    public boolean containsModel(BitSet block, boolean isLeft){
        BitSet modelIds = isLeft ? idsInL : idsInR;
        return block.intersects(modelIds);
    }

    public boolean isPure(BitSet block){
        boolean hasL = block.intersects(idsInL) && !block.intersects(idsInR);
        boolean hasR = block.intersects(idsInR) && !block.intersects(idsInL);
        return hasL || hasR;
    }

    //functions for debugging
    public Map<String, Integer> getStringToInt(){
        return stringToInt;
    }

    public Map<Integer, String> getIntToString() {
        return intToString;
    }

    public Map<String, prism.api.State> getStringToState(){return stringToState;}

    public Integer getNextID(){
        return nextID++;
    }

    public int[] getDegree() {
        return this.degree;
    }
    public List<Integer> getPredecessorsOf(int nodeID) {
        if (this.predecessors != null && this.predecessors.containsKey(nodeID)) {
            return this.predecessors.get(nodeID);
        }
        return new ArrayList<>();
    }

    private enum Classification {NONE, BLUE, HALO}

    private Classification classifyDivergeence(String name, List<String> red, List<String> green, List<String> blue, List<String> halo) {
        Integer id = stringToInt.get(name);
        List<Integer> targets = successors.getOrDefault(id, new ArrayList<>());
        boolean leadsToChange = false;
        boolean leadsToHalo = false;

        for (Integer targetId : targets) {
            String targetName = intToString.get(targetId);
            if (targetName == null){continue;}

            if (red.contains(targetName)||green.contains(targetName)){
                return Classification.BLUE;
            }
            if (blue.contains(targetName)||halo.contains(targetName)){
                leadsToHalo = true;
            }
        }
        return leadsToHalo ? Classification.HALO : Classification.NONE;
    }
}
        