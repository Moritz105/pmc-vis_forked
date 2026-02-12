package prism.core;

import parser.ast.Expression;
import parser.ast.ModulesFile;
import parser.ast.PropertiesFile;
import prism.*;
import prism.api.*;
import prism.core.Property.Property;
import prism.core.Scheduler.Criteria;
import prism.core.Scheduler.CriteriaSort;
import prism.core.Scheduler.Scheduler;
import prism.core.Utility.BaseState;
import prism.db.Database;
import prism.db.mappers.StateMapper;
import prism.db.mappers.TransitionMapper;
import prism.server.TaskManager;
import simulator.TransitionList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class Model implements Namespace {

    private final String version;
    private final Project parent;

    private final ModelParser parser;
    private final ModelChecker checker;
    private final Database database;
    private final ModulesFile modulesFile;

    private final String TABLE_STATES;
    private final String TABLE_TRANS;
    private final String TABLE_SCHED;

    private final Info info;

    //List of properties that have been project checked
    private final List<Property> properties;

    private final List<Scheduler> schedulers;

    private final Map<String, AP> APs;

    private final File outLog;

    private boolean built = false;

    public boolean debug;

    private HashMap<String, String> colors = new HashMap<>();

    public Model(File modelFile, String version, Project parent, boolean debug) throws Exception {

        this.parent = parent;
        this.outLog = parent.getLog();
        this.version = version;
        this.debug = debug;
        this.info = new Info(parent.getID());

        this.TABLE_STATES = String.format(TABLE_STATES_GEN, version);
        this.TABLE_TRANS = String.format(TABLE_TRANS_GEN, version);
        this.TABLE_SCHED = String.format(TABLE_SCHED_GEN, version);

        this.checker = new ModelChecker(this, modelFile, TABLE_STATES, TABLE_TRANS, TABLE_SCHED, String.format("%dm", parent.getCuddMaxMem()), parent.getNumIterations(), debug);
        this.modulesFile = checker.getModulesFile();
        this.parser = new ModelParser(this, modulesFile, debug);

        this.database = parent.getDatabase();

        this.properties = new ArrayList<>();
        this.schedulers = new ArrayList<>();

        this.APs = new HashMap<>();
        Map<String, Integer> usedShorts = new HashMap<>();
        Map<String, String> labelStyles = new HashMap<>();
        File styleFile = new File(parent.getPath() + STYLE_FILE);
        if (styleFile.exists() && styleFile.isFile() ){
            try(BufferedReader read = new BufferedReader(new FileReader(styleFile))){
                labelStyles = read.lines().collect(Collectors.toMap(l -> l.split(":")[0], l -> l.split(":")[1]));
            }
        }

        AP initial;
        if (labelStyles.containsKey(LABEL_INIT)){
            initial = new AP(labelStyles.get(LABEL_INIT),true);
        }else{
            initial = new AP("i", false);
        }
        APs.put(LABEL_INIT,  initial);

        AP deadlock;
        if (labelStyles.containsKey(LABEL_DEAD)){
            deadlock = new AP(labelStyles.get(LABEL_DEAD),true);
        }else{
            deadlock = new AP("d", false);
        }
        APs.put(LABEL_DEAD,  deadlock);

        for (int i = 0; i < modulesFile.getNumLabels(); i++){
            String name = modulesFile.getLabelName(i);
            AP ap;
            if (labelStyles.containsKey(name)){
                ap = new AP(labelStyles.get(name),true);
            }else{
                String shortName = name.substring(0, 1);
                if (!usedShorts.containsKey(shortName)) usedShorts.put(shortName, 0);
                int number = usedShorts.get(shortName);
                ap = new AP(shortName + number, false);
                usedShorts.replace(shortName, number + 1);
            }
            APs.put(name,  ap);
        }

        this.info.setStateEntry(OUTPUT_LABELS, APs);
        this.info.setStateEntry(OUTPUT_RESULTS, new TreeMap<>());
        this.info.setTransitionEntry(OUTPUT_RESULTS, new TreeMap<>());

        Map<String, VariableInfo> actionParameter = new TreeMap<>();
        actionParameter.put(ENTRY_T_OUT, new VariableInfo(ENTRY_T_OUT, VariableInfo.parseType("string"), 0,0));
        actionParameter.put(ENTRY_T_ACT, new VariableInfo(ENTRY_T_ACT, VariableInfo.parseType("string"), 0,0));
        actionParameter.put(ENTRY_T_PROB, new VariableInfo(ENTRY_T_PROB, VariableInfo.parseType("complex"), 0,0));
        this.info.setTransitionEntry(OUTPUT_ACTION, actionParameter);

        this.loadPropertyFiles();
        if (checker.isBuilt()) {
            this.setBuilt(true);
        }
    }

    public HashMap<String, String> getColors() {
        //System.out.println("colores in model:" + this.colors);
        return this.colors;
    }
    public void setColors(HashMap<String, String> colors) {
        this.colors = colors;
        //System.out.println("Colors setted" + colors);
    }

    //---Utility Functions---

    public String getID(){
        return String.format("%s_%s", this.parent.getID(), this.version);
    }

    public String getVersion(){
        return version;
    }

    public String getProjectID(){
        return parent.getID();
    }

    public ModelChecker getModelChecker() {
        return checker;
    }

    public ModelParser getModelParser() {
        return parser;
    }

    public TaskManager getTaskManager() {
        return parent.getTaskManager();
    }

    public String getTableStates(){
        return TABLE_STATES;
    }

    public String getTableTrans(){
        return TABLE_TRANS;
    }

    public String getTableSched(){
        return TABLE_SCHED;
    }

    public Database getDatabase(){
        return database;
    }

    public ModulesFile getModulesFile(){
        return modulesFile;
    }

    public File getLog(){
        return outLog;
    }

    public boolean isBuilt() {
        return this.built;
    }

    public void setBuilt(boolean built) {
        this.built = built;
    }

    public Info getInfo() {
        return info;
    }

    public long getSize() {
        return this.checker.getModel().getNumStates();
    }

    public boolean existsProperty(String name) {
        return properties.stream().anyMatch(p -> p.getName().equals(name));
    }

    public Optional<Property> getProperty(String name) {
        return properties.stream().filter(p -> p.getName().equals(name)).findFirst();
    }

    public String newProperty(PropertiesFile propertiesFile, int number) {
        parser.ast.Property prismProperty = propertiesFile.getPropertyObject(number);
        String name = prismProperty.getName() != null ? prismProperty.getName() : prismProperty.getExpression().toString();
        if (existsProperty(name)) return name;
        properties.add(Property.createProperty(this, properties.size(), propertiesFile, prismProperty));
        return name;
    }

    public Info getInformation() {
        Info outInfo = this.info.copy();
        for (Property p : properties) {
            if(p.getScheduler() == null){
                outInfo.setSchedulerEntry(p.getName(), VariableInfo.Status.missing);
            }
        }
        for (Scheduler s : schedulers){
            outInfo.setSchedulerEntry(s.getName(), VariableInfo.Status.ready);
        }
        return outInfo;
    }

    private boolean isDeadlocked(parser.State state) throws PrismException {
        TransitionList<Double> transitionList = new TransitionList<>(Evaluator.forDouble());
        checker.getUpdater().calculateTransitions(state, transitionList);
        return transitionList.isDeadlock();
    }

    private boolean isInitial(parser.State state) throws PrismLangException {
        if (modulesFile.getInitialStates() != null) {
            return modulesFile.getInitialStates().evaluateBoolean(modulesFile.getConstantValues(), state);
        } else {
            return modulesFile.getDefaultInitialState().equals(state);
        }
    }

    public TreeMap<String, AP> getLabelMap(parser.State state) throws Exception {
        TreeMap<String, AP> labels = new TreeMap<>();
        labels.put(LABEL_INIT, isInitial(state)?APs.get(LABEL_INIT): null);
        labels.put(LABEL_DEAD, isDeadlocked(state)?APs.get(LABEL_DEAD): null);
        for (int i = 0; i < modulesFile.getLabelList().size(); i++){
            String name = modulesFile.getLabelName(i);
            labels.put(name, modulesFile.getLabelList().getLabel(i).evaluateBoolean(modulesFile.getConstantValues(), state) ? APs.get(name) : null);
        }
        return labels;
    }

    public List<Property> getProperties() {
        return this.properties;
    }

    public List<Scheduler> getSchedulers() {
        return this.schedulers;
    }

    public List<String> getLabels(parser.State state) throws Exception {
        List<String> labels = new ArrayList<>();
        if (isInitial(state)) labels.add(LABEL_INIT);
        if (isDeadlocked(state)) labels.add(LABEL_DEAD);
        for (int i = 0; i < modulesFile.getLabelList().size(); i++){
            if (modulesFile.getLabelList().getLabel(i).evaluateBoolean(modulesFile.getConstantValues(), state)){
                labels.add(modulesFile.getLabelName(i));
            }
        }
        return labels;
    }

    public List<Transition> getAllTransitions() {
        return database.executeCollectionQuery(String.format("SELECT * FROM %s", TABLE_TRANS), new TransitionMapper(this));
    }

    public List<Transition> getOutgoingList(String stateID) {
        return database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s == %s ", TABLE_TRANS, ENTRY_T_OUT, stateID), new TransitionMapper(this));

    }

    public List<State> getStates(List<Long> stateIDs) {
        String stateString = stateIDs.stream().map(l -> Long.toString(l)).collect(Collectors.joining(","));
        return database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s in (%s)", TABLE_STATES, ENTRY_S_ID, stateString), new StateMapper(this));
    }

    public List<Long> getAllStates() {
        return database.executeCollectionQuery(String.format("SELECT %s FROM %s", ENTRY_S_ID, TABLE_STATES), Long.class);
    }

    public String getStateID(String stateDescription) {
        String stateName = parser.normalizeStateName(stateDescription);
        Optional<String> results = database.executeLookupQuery(String.format("SELECT %s FROM %s WHERE %s = '%s';", ENTRY_S_ID, TABLE_STATES, ENTRY_S_NAME, stateName), String.class);
        if (results.isEmpty()) return "-1";
        return results.get();
    }

    public String getStateName(String stateID) {
        Optional<String> results = database.executeLookupQuery(String.format("SELECT %s FROM %s WHERE %s = '%s';", ENTRY_S_NAME, TABLE_STATES, ENTRY_S_ID, stateID), String.class);
        if (results.isEmpty()) return null;
        return results.get();
    }

    public void checkProperty(String propertyName) throws PrismException {
        checker.checkModel(propertyName);
    }

    public void loadPropertyFile(File file) throws Exception {
        checker.parsePropertyFile(file.getPath());
    }

    public void loadPropertyFiles() throws Exception {
        boolean fileForModelCheckingFound = false;
        for (File file : Objects.requireNonNull(new File(parent.getPath()).listFiles())) {
            if (!Namespace.FILES_RESERVED.contains(file.getName()) && file.getName().endsWith(".props")) {
                fileForModelCheckingFound = true;
                if (this.debug) {
                    System.out.println("Property File: " + file);
                }
                checker.parsePropertyFile(file.getPath());
            }
        }
        if (this.debug && !fileForModelCheckingFound) {
            System.out.println("Warning: No Properties found!");
        }
        if (this.debug) {
            System.out.printf("Loading Properties in Model %s finished%n", this.getID());
        }
    }

    public List<String> getStatesByExpression(String expression) {
        List<String> members = new ArrayList<>();
        if (modulesFile.getLabelList().getLabelNames().contains(expression)) {
            for (String stateDescription : this.checker.getModel().getReachableStates().exportToStringList()) {
                try {
                    String id = this.getStateID(stateDescription);
                    BaseState state = new BaseState(id, stateDescription, this);
                    if (state.getLabels().contains(expression)) {
                        members.add(id);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return members;
        }
        for (String stateDescription : this.checker.getModel().getReachableStates().exportToStringList()) {
            try {
                String id = this.getStateID(stateDescription);
                BaseState state = new BaseState(id, stateDescription, this);
                if (state.checkForProperty(expression)) {
                    members.add(id);
                }
            } catch (prism.PrismLangException e) {
                throw new RuntimeException(e);
            }
        }
        return members;
    }

    public List<String> getInitialStates() throws Exception {
        List<String> initials = new ArrayList<>();

        if (modulesFile.getInitialStates() != null) {
            Expression initialExpression = modulesFile.getInitialStates();
            for (parser.State state : modulesFile.createVarList().getAllStates()) {
                if (initialExpression.evaluateBoolean(state)) {
                    initials.add(getStateID(state.toString(modulesFile)));
                }
            }
        } else {
            initials.add(this.getDefaultInitialState());
        }

        return initials;
    }

    public String getDefaultInitialState() throws Exception {
        if (modulesFile.getInitialStates() != null) {
            return getInitialStates().get(0);
        }
        return getStateID(modulesFile.getDefaultInitialState().toString(this.modulesFile));
    }

    public void addCustomScheduler(File description) throws Exception {
        List<Criteria> criterias = new ArrayList<>();
        //Create Criteria List by reading the file

        try(BufferedReader r = new BufferedReader(new FileReader(description))){
            while(r.ready()){
                String line = r.readLine();
                if (line == null){
                    break;
                }
                Matcher m = Criteria.CriteriaPattern.matcher(line);
                if (m.matches()){
                    switch (m.group(1)){
                        case "SORT":
                        default:
                            Optional<Property> p = this.getProperty(m.group(2));
                            if(p.isEmpty()) {
                                throw new Exception(m.group(2) + " not a checked property");
                            }
                            String collumnName = p.get().getPropertyCollumn();
                            if (m.group(3).equals(" ASC"))
                                criterias.add(new CriteriaSort(collumnName, CriteriaSort.Direction.ASC));
                            else
                                criterias.add(new CriteriaSort(collumnName, CriteriaSort.Direction.DESC));
                    }
                }
            }

        }

        Scheduler custom = Scheduler.createScheduler(this, description.getName(), schedulers.size(), criterias);
        this.schedulers.add(custom);
    }

    public void addScheduler(Scheduler scheduler){
        schedulers.add(scheduler);
    }

    public void printScheduler(String pathName, boolean limit) throws Exception {
        checker.modelCheckAll();
        int i = 0;
        for (Property p : properties){
            p.printScheduler(String.format("%s/sched_%s.csv", pathName, i), limit);
            i++;
        }
    }

    public void clearTables() throws Exception {
        schedulers.clear();
        for (Property p : properties) {
            p.clear();
        }
        checker.reset();
        this.database.execute(String.format("DROP TABLE IF EXISTS %s", TABLE_PANES));
    }

    // --- API Functions ---

    public Graph getInitialNodes() {
        if (!built){
            try {
                return parser.getInitialNodes();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try {
            List<State> initials = database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s = 1", TABLE_STATES, ENTRY_S_INIT), new StateMapper(this));

            return new Graph(this, initials, new ArrayList<>());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Graph getGraph() {
        if (!built) {
            try {
                return parser.getGraph();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        List<State> states = database.executeCollectionQuery(String.format("SELECT * FROM %s", TABLE_STATES), new StateMapper(this));
        List<Transition> transitions = database.executeCollectionQuery(String.format("SELECT * FROM %s", TABLE_TRANS), new TransitionMapper(this));
        return new Graph(this, states, transitions);
    }

    public Graph getSubGraph(List<String> stateIDs) {
        if (!built) {
            try {
                return parser.getSubGraph(stateIDs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        List<String> stringIds = new ArrayList<>(stateIDs);
        String stateID = stateIDs.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
        List<State> states = database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s in (%s)", TABLE_STATES, ENTRY_S_ID, stateID) , new StateMapper(this));
        List<Transition> transitions = database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s IN (%s)", TABLE_TRANS, ENTRY_T_OUT, stateID), new TransitionMapper(this));
        List<Transition> transitionsOut = new ArrayList<>();
        for (Transition t : transitions){
            Set<String> reach = new HashSet<>(t.getProbabilityDistribution().keySet());
            stringIds.forEach(reach::remove);
            if (reach.isEmpty()){
                transitionsOut.add(t);
            }
        }
        return new Graph(this, states, transitionsOut);
    }

    public Graph getState(String stateID) {
        if (!built) {
            try {
                List<String> stateIDs = new ArrayList<>();
                stateIDs.add(stateID);
                return parser.getSubGraph(stateIDs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Optional<State> results = database.executeLookupQuery(String.format("SELECT * FROM %s WHERE %s = %s", TABLE_STATES, ENTRY_S_ID, stateID), new StateMapper(this));
        if (results.isEmpty()) return null;
        List<State> states = new ArrayList<>();
        states.add(results.get());
        return new Graph(this, states, new ArrayList<>());
    }

    public Graph getOutgoing(List<String> stateIDs) {
        if (!built) {
            try {
                return parser.getOutgoing(stateIDs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String stateID = stateIDs.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
        List<Transition> transitions = database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s IN (%s)", TABLE_TRANS, ENTRY_T_OUT, stateID), new TransitionMapper(this));
        //System.out.println(transitions.size());
        Set<String> statesOfInterest = new HashSet<>();
        for (Transition t : transitions) {
            statesOfInterest.add(t.getSource());
            statesOfInterest.addAll(new ArrayList<>(t.getProbabilityDistribution().keySet()));
        }
        String stateString = statesOfInterest.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
        List<State> states = database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s in (%s)", TABLE_STATES, ENTRY_S_ID, stateString), new StateMapper(this));
        return new Graph(this, states, transitions);
    }

    public Graph resetGraph(List<String> stateIDs, List<String> unexploredStateIDs){
        if (!built) {
            try {
                return parser.resetGraph(stateIDs, unexploredStateIDs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String stateID = stateIDs.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
        List<Transition> transitions = database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s IN (%s)", TABLE_TRANS, ENTRY_T_OUT, stateID), new TransitionMapper(this));
        Set<String> statesOfInterest = new HashSet<>();
        for (String unStateID : unexploredStateIDs){
            statesOfInterest.add(unStateID);
        }
        for (Transition t : transitions) {
            statesOfInterest.add(t.getSource());
            statesOfInterest.addAll(new ArrayList<>(t.getProbabilityDistribution().keySet()));
        }
        String stateString = statesOfInterest.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
        List<State> states = database.executeCollectionQuery(String.format("SELECT * FROM %s WHERE %s in (%s)", TABLE_STATES, ENTRY_S_ID, stateString), new StateMapper(this));
        return new Graph(this, states, transitions);
    }

}

