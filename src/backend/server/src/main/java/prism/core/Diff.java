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
    private BitSet splitterRel = new BitSet();

    public Diff(Project project, Model left, Model right) throws Exception{
        System.out.println("starting diff");
        this.project =  project;
        this.left = left;
        this.right = right;
        this.parserleft = left.getModelParser();
        this.parserright = right.getModelParser();
        sanityCheck();
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


    public Map<String, Object> getExtendedFingerprints() throws Exception {
        Map<String, Object> response = new HashMap<>();


        response.put("numStatesRef", getNodesLeft());
        response.put("numStatesNew", getNodesRight());
        response.put("numTransitionsRef", getTransitionsLeft());
        response.put("numTransitionsNew", getTransitionsRight());
        response.put("variableComparison", getDistributions());

        return response;
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
            if (!isLeft && (srcName.equals("t5") || trgName.equals("t5"))) {
                System.out.println("ALARM: t5 im RECHTEN Modell gefunden!");
                System.out.println("Kante: " + srcName + " -> " + trgName);
            }
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
        // only for debuggiing
        String targetName = "t5";
        Integer targetId = stringToInt.get(targetName);

        if (targetId != null) {
            System.out.println("--- DEBUG FÜR TRANSITION " + targetName + " ---");
            System.out.println("Vergebene ID: " + targetId);
            System.out.println("In Modell Links (idsInL): " + idsInL.get(targetId));
            System.out.println("In Modell Rechts (idsInR): " + idsInR.get(targetId));
        } else {
            System.out.println("DEBUG: Transition " + targetName + " wurde gar nicht erst in stringToInt gefunden!");
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
            this.splitterRel.clear();
            BitSet splitter = worklist.remove(0); //take out first element
            calcSplitterRel(splitter); //calc its predecessors
            ListIterator<BitSet> it = partitions.listIterator();
            while (it.hasNext()) {
                BitSet candidate = it.next();
                splitBlockIfNessesary(it, candidate,  worklist, splitter);
            }
        }
        this.finalPartitions = partitions;
        //now compare if theres only bitsets left with one node of each model
        return getColorDiff(partitions);
    }

    public void calcSplitterRel(BitSet splitter){
        for (int i = splitter.nextSetBit(0); i >= 0; i = splitter.nextSetBit(i+1)) {
            if (predecessors.get(i)!= null){
                for (int pred : predecessors.get(i)) {
                    this.splitterRel.set(pred);
                }
            }
        }
    }
    public void splitBlockIfNessesary(ListIterator<BitSet> it, BitSet candidate,  List<BitSet> worklist, BitSet splitter){
        BitSet refined = (BitSet) candidate.clone();
        refined.and(this.splitterRel); //filter all nodes with a successor in the splitter

        if (!refined.isEmpty() && refined.cardinality()<candidate.cardinality()){
            boolean splitterIsMixed = containsModel(splitter, true) && containsModel(splitter, false);
            // if true, there are nodes that have a successore in the splitter and some that don't
            // thats why they can't be compared and need to be splitted
            candidate.andNot(this.splitterRel); //nodes that can't reach the splitter
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

    public HashMap<String, String> getColorDiff(List<BitSet> partitions) throws Exception {
        int[] stateToBlock = new int[512];
        for (int b = 0; b < partitions.size(); b++) {
            BitSet block = partitions.get(b);
            for (int s = block.nextSetBit(0); s >= 0; s = block.nextSetBit(s + 1)) {
                if (s >= stateToBlock.length){
                    stateToBlock = Arrays.copyOf(stateToBlock, Math.max(stateToBlock.length *2, s + 1));
                }
                stateToBlock[s] = b;
            }
        }


        HashMap<String, String> colorSwitch = new HashMap<>();
        BitSet red = new BitSet(); // any node not in m2
        BitSet green = new BitSet(); //any node new in m2
        BitSet violet = new BitSet(); // node that misses edge and causes avalanche of red/green states on parents
        BitSet halo = new BitSet(); // nodes on trace to violet

        for (int b = 0; b < partitions.size(); b++) {
            boolean inL = partitions.get(b).intersects(idsInL);
            boolean inR = partitions.get(b).intersects(idsInR);
            if (inL && !inR) {
                red.set(b);
            }
            if (!inL && inR) {
                green.set(b);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int idR = idsInR.nextSetBit(0); idR >= 0; idR = idsInR.nextSetBit(idR + 1)) {
                if (violet.get(idR) || halo.get(idR)) continue;
                int currentBlock = stateToBlock[idR];
                int idL = findPartnerInBlock(currentBlock, idsInL);
                if (idL != -1) {
                    if (isViolet(idR, idL, stateToBlock, green, red)) {
                        violet.set(idR);
                        changed = true;
                    }
                    else if (leadsToUnstable(idR, violet, halo)) {
                        halo.set(idR);
                        changed = true;
                    }
                }
            }
        }
        transferToColorMap(colorSwitch, green, partitions, "green");
        transferToColorMapForRed(colorSwitch, red, partitions, "red");
        transferToIdSet(colorSwitch, violet, "violet");
        transferToIdSet(colorSwitch, halo, "halo");
        this.left.setColors(colorSwitch);
        this.right.setColors(colorSwitch);
        parserleft.getGraph();
        parserright.getGraph();
        System.out.println("TEST FÜR COLORSWITCH" + colorSwitch);
        return colorSwitch;
    }

    private boolean isViolet(int idR, int idL, int[] stateToBlock, BitSet greenBlocks, BitSet redBlocks) {

        Map<Integer, Integer> countsL = getBlockDistribution(idL, stateToBlock);
        Map<Integer, Integer> countsR = getBlockDistribution(idR, stateToBlock);

        if (!countsL.equals(countsR)) {
            return true;
        }

        for (int blockIdx : countsR.keySet()) {
            if (greenBlocks.get(blockIdx)) return true;
        }
        for (int blockIdx : countsL.keySet()) {
            if (redBlocks.get(blockIdx)) return true;
        }

        return false;
    }

    private Map<Integer, Integer> getBlockDistribution(int stateId, int[] stateToBlock) {
        Map<Integer, Integer> dist = new HashMap<>();
        List<Integer> targets = this.successors.getOrDefault(stateId, new ArrayList<>());

        for (Integer tId : targets) {
            int bIdx = stateToBlock[tId];
            dist.put(bIdx, dist.getOrDefault(bIdx, 0) + 1);
        }
        return dist;
    }

    private void transferToColorMapForRed(Map<String, String> map, BitSet redBlocks, List<BitSet> partitions, String color) {
        for (int b = redBlocks.nextSetBit(0); b >= 0; b = redBlocks.nextSetBit(b + 1)) {
            BitSet statesInBlock = partitions.get(b);
            for (int s = statesInBlock.nextSetBit(0); s >= 0; s = statesInBlock.nextSetBit(s + 1)) {
                if (idsInL.get(s)) {
                    String name = intToString.get(s);
                    if (name != null && !nameExistsInR(name)) {
                        map.put(name, color);
                    }
                }
            }
        }
    }

    private boolean nameExistsInR(String name) {
        Integer id = stringToInt.get(name);
        return id != null && idsInR.get(id);
    }

    private void transferToColorMap(Map<String, String> map, BitSet targetBlocks, List<BitSet> partitions, String color) {
        for (int b = targetBlocks.nextSetBit(0); b >= 0; b = targetBlocks.nextSetBit(b + 1)) {
            BitSet statesInBlock = partitions.get(b);
            for (int s = statesInBlock.nextSetBit(0); s >= 0; s = statesInBlock.nextSetBit(s + 1)) {
                if (idsInR.get(s)) {
                    String name = intToString.get(s);
                    if (name != null) map.put(name, color);
                }
            }
        }
    }

    private void transferToIdSet(Map<String, String> map, BitSet stateIds, String color) {
        for (int s = stateIds.nextSetBit(0); s >= 0; s = stateIds.nextSetBit(s + 1)) {
            String name = intToString.get(s);
            if (name != null) {
                map.put(name, color);
            }
        }
    }

    private int findPartnerInBlock(int blockIdx, BitSet idsInModel) {
        BitSet block = finalPartitions.get(blockIdx);
        BitSet partners = (BitSet) block.clone();
        partners.and(idsInModel);
        return partners.nextSetBit(0);
    }

    private boolean leadsToUnstable(int idR, BitSet violet, BitSet halo) {
        List<Integer> targets = this.successors.getOrDefault(idR, new ArrayList<>());

        for (Integer tId : targets) {
            if (violet.get(tId) || halo.get(tId)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPure(BitSet block){
        boolean hasL = block.intersects(idsInL) && !block.intersects(idsInR);
        boolean hasR = block.intersects(idsInR) && !block.intersects(idsInL);
        return hasL || hasR;
    }

    public boolean containsModel(BitSet block, boolean isLeft){
        BitSet modelIds = isLeft ? idsInL : idsInR;
        return block.intersects(modelIds);
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
    private void sanityCheck() throws Exception {
        int edgesLeft = parserleft.getGraph().getEdges().size();
        int edgesRight = parserright.getGraph().getEdges().size();

        System.out.println("--- SANITY CHECK ---");
        System.out.println("Edges links (alt): " + edgesLeft);
        System.out.println("Edges rechts (neu): " + edgesRight);

        if (edgesRight >= edgesLeft && edgesLeft > 0) {
            System.out.println("WARNUNG: Das neue Modell hat mehr oder gleich viele Kanten wie das alte!");
        }

        // Check, ob t5 im Quelltext der Modelle vorkommt
        String leftSource = parserleft.getModulesFile().toString();
        String rightSource = parserright.getModulesFile().toString();

        System.out.println("t5 in Source Links: " + leftSource.contains("t5"));
        System.out.println("t5 in Source Rechts: " + rightSource.contains("t5"));
        System.out.println("---------------------");
    }

}
        