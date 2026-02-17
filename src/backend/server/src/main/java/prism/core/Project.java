package prism.core;

import prism.api.*;
import prism.db.Database;
import prism.db.mappers.PaneMapper;
import prism.server.PRISMServerConfiguration;
import prism.server.TaskManager;

import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Representation of the project on the backend side. Contains Structural Information, Project Checker Access and Database Connection
 */
public class Project implements Namespace{

    private final String id;

    private final TaskManager taskManager;

    private final Database database;

    public final boolean debug;
    public final long cuddMaxMem;
    public final int numIterations;
    private final String rootDir;

    private final File outLog;

    private Set<File> propertyFiles;

    private Map<String, Model> models;
    private String newestVersion;
    private String secondNewestVersion;
    private Map<String, String> colorCache = new HashMap<>();

    private Diff diff;

    public static Project reset(Project original) throws Exception {
        return new Project(original.id, original.rootDir, original.taskManager, original.database, original.cuddMaxMem, original.numIterations, original.debug);
    }

    public Project(String id, String rootDir, TaskManager taskManager, Database database, PRISMServerConfiguration config) throws Exception {
        this(id, rootDir, taskManager, database, config.getCUDDMaxMem(), config.getIterations(), config.getDebug());
    }

    public Project(String id, String rootDir, TaskManager taskManager, Database database, long cuddMaxMem, int numIterations, boolean debug) throws Exception {
        this.id = id;
        this.taskManager = taskManager;
        this.debug = debug;
        this.cuddMaxMem = cuddMaxMem;
        this.numIterations = numIterations;
        this.rootDir = rootDir;

        this.outLog =  new File(String.format("%s/%s/", rootDir, id) + LOG_FILE);
        Files.deleteIfExists(outLog.toPath());

        this.database = database;
        this.propertyFiles = new HashSet<>();
        this.models = new HashMap<>();

        addAllFiles();

        if (this.models.size()>1){
            this.diff = new Diff(this, models.get(secondNewestVersion), models.get(newestVersion));
        }
    }

    private void addAllFiles() throws Exception {
        for (File file : Objects.requireNonNull(new File(String.format("%s/%s", rootDir, id)).listFiles())) {
            if (debug) System.out.println("Adding file " + file.getName());
            addFile(file);
        }
    }

    public void addFile(File file) throws Exception {
        String fileEnding = file.getName().substring(file.getName().lastIndexOf("."));
        switch (fileEnding) {
            case ".profeat":
                File translatedFile = new File(file.getPath().replace(fileEnding, ".prism"));
                addFile(ModelChecker.translateProFeat(file, translatedFile));
                return;
            case ".prop":
            case ".props":
                addPropertyFile(file);
                break;
            case ".mdp":
            case ".prism":
                createModel(file);
                break;
            default:
                if (debug){
                    System.out.println("Ignored: " + file.getName());
                }
        }
    }

    public Model getModel(String version) throws Exception {
        return models.get(version);
    }

    public Model getDefaultModel() throws Exception {
        if (newestVersion == null) { return null;}
        return getModel(this.newestVersion);
    }

    public void addPropertyFile(File file) throws Exception {
        propertyFiles.add(file);
        for (Model m : this.models.values()) {
            m.loadPropertyFile(file);
        }
    }

    public String createModel(File modelFile) throws Exception {
        String modelName = modelFile.getName().split("\\.")[0];
        return createModel(modelFile, modelName);
    }

    public String createModel(File modelFile, String version) throws Exception {
        Model m = new Model(modelFile, version, this, debug);
        models.put(version, m);
        if (newestVersion == null) {
            this.secondNewestVersion = version;
        }else{
            this.secondNewestVersion = newestVersion;
        }
        this.newestVersion = version;
        for (File f : propertyFiles) {
            m.loadPropertyFile(f);
        }
        return version;
    }

    public String getPath(){
        return String.format("%s/%s/", rootDir, id);
    }

    public File getLog(){
        return this.outLog;
    }

    public int getNumIterations() {
        return numIterations;
    }

    public long getCuddMaxMem() {
        return cuddMaxMem;
    }

    public void refreshProject(){
        //TODO Establish Behaviour for modified files
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public void removeFiles() throws Exception {
        File directory = new File(String.format("%s/%s", rootDir, id));
        if (directory.exists()) {
            for (File file : Objects.requireNonNull(directory.listFiles())) {
                file.delete();
            }
            directory.delete();
        }
    }

    //Generic Getter
    public String getID() {
        return id;
    }

    public String getRoot() {
        return this.rootDir;
    }

    public Database getDatabase() {
        return this.database;
    }

    public String defaultVersion(){
        return newestVersion;
    }

    public Model defaultModel(){
        if (newestVersion == null){
            return null;
        }
        return models.get(defaultVersion());
    }

    public List<String> getFileStructure() {

        List<String> structure = new ArrayList<>();

        for (File file : Objects.requireNonNull(new File(String.format("%s/%s", rootDir, id)).listFiles())) {
            String fileName = file.getName();
            if (!Namespace.FILES_INVISIBLE.contains(fileName)){
                structure.add(file.getName());
            }
        }
        structure.sort(String::compareTo);
        return structure;
    }

    public Map<String, String> getFileContent(int fileID) {
        List<String> structure = getFileStructure();
        String fileName = getFileStructure().get(fileID);

        File file = new File(String.format("%s/%s/%s", rootDir, id, fileName));
        StringBuilder content = new StringBuilder();
        try(BufferedReader r = new BufferedReader(new FileReader(file))){
            while (r.ready()) {
                content.append(r.readLine()).append("\n");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, String> map = new TreeMap<>();
        map.put("name", fileName);
        map.put("content", content.toString());
        map.put("language", Namespace.getLanguage(fileName));
        return map;
    }

    // --- API Functions ---

    public Graph getInitialNodes(){
        return getInitialNodes(this.defaultVersion());
    }

    public Graph getInitialNodes(String version){
        return models.get(version).getInitialNodes();
    }

    public Graph getGraph(){
        return getGraph(this.defaultVersion());
    }

    public Graph getGraph(String version){
        return models.get(version).getGraph();
    }

    public Graph getSubGraph(List<String> stateIDs){
        return getSubGraph(stateIDs, this.defaultVersion());
    }

    public Graph getSubGraph(List<String> stateIDs, String version){
        return models.get(version).getSubGraph(stateIDs);
    }

    public Graph getOutgoing(List<String> stateIDs){
        return getOutgoing(stateIDs, this.defaultVersion());
    }

    public Graph getOutgoing(List<String> stateIDs, String version){
        return models.get(version).getOutgoing(stateIDs);
    }

    public Graph getState(String stateID){
        return getState(stateID, this.defaultVersion());
    }

    public Graph getState(String stateID, String version){
        return models.get(version).getState(stateID);
    }

    public Graph resetGraph(List<String> exploredStateIDs, List<String> unexploredStateIDs){
        return resetGraph(exploredStateIDs, unexploredStateIDs, this.defaultVersion());
    }

    public Graph resetGraph(List<String> exploredStateIDs, List<String> unexploredStateIDs, String version){
        return models.get(version).resetGraph(exploredStateIDs, unexploredStateIDs);
    }

    public void clearTables() throws Exception {
        for (Model m : models.values()) {
            m.clearTables();
        }
    }

    public Set<File> getPropertyFiles() {
        return propertyFiles;
    }

    public Map<String, Object> getFingerprints() throws Exception{
        if (diff==null){
            this.diff = new Diff(this, models.get(secondNewestVersion), models.get(newestVersion));
        }
        return diff.getExtendedFingerprints();
    }

    public String getSecondV (){return this.secondNewestVersion;}

    public  Map<String, Model> getModels() {return this.models;}

    public Map<String, String> getColoring() throws Exception{
        if (diff==null){
            this.diff = new Diff(this, models.get(secondNewestVersion), models.get(newestVersion));
        }
        return diff.matchNodes();
    }

    /*public Graph applyColors(Graph graph) {
        try {
            if (graph == null || colorCache.isEmpty()) {
                return graph;
            }
            for (prism.api.State state : graph.getStates()) {
                String name = state.getName();
                String color = colorCache.getOrDefault(name, colorCache.get(name.replace("(", "").replace(")", "")));
                if (color != null) {
                    state.setDiffColor(color);
                }
            }
        }catch(Exception e){
            System.err.println("Fehler beim Einfärben"+ e.getMessage());
        }return  graph;
    }*/

//    public TreeMap<String, String> modelCheckAllStatistical(long maxPathLength, String simulationMethod, boolean parallel, Optional<String> schedulerName) throws Exception {
//        TreeMap<String, String> info = new TreeMap<>();
//
//        Optional<Scheduler> scheduler = Optional.empty();
//        //Find scheduler
//        if (schedulerName.isPresent()){
//            for (Scheduler sched : schedulers){
//                if (sched.getName().equals(schedulerName.get())){
//                    scheduler = Optional.of(sched);
//                    break;
//                }
//            }
//            if (scheduler.isEmpty()){
//                System.out.println("Could not find scheduler " + schedulerName.get());
//            }
//        }
//
//        for (File file : Objects.requireNonNull(new File(String.format("%s/%s", rootDir, id)).listFiles())) {
//            if (!Namespace.FILES_RESERVED.contains(file.getName())) {
//                //Match simulation Method
//                List<Result[]> r = modelParser.modelCheckSimulator(file, null, maxPathLength, simulationMethod, parallel, scheduler);
//                StringBuilder out = new StringBuilder();
//
//                //for (int i = 0; i < r.length; i++){
//                //    out.append("-----------------------");
//                //    out.append(r.toString());
//                //}
//
//                //info.put(file.getName(), out.toString());
//            }
//        }
//        if (this.debug) {
//            System.out.printf("Statistical Model Checking in Project %s finished%n", id);
//        }
//        return info;
//    }
//    public Graph getInitialNodes(List<Integer> viewIDs) {
//        List<View> activeViews = this.getViews(viewIDs);
//        if (views == null || views.isEmpty() || activeViews.isEmpty()) return this.getInitialNodes();
//        try {
//            StringBuilder identifierStates = null;
//            StringBuilder groupStates = null;
//            StringBuilder blankStates = null;
//            for (View c : activeViews){
//                if (identifierStates == null){
//                    identifierStates = new StringBuilder();
//                    groupStates = new StringBuilder();
//                    blankStates = new StringBuilder();
//                }else{
//                    identifierStates.append(" || '" + C_CONCAT_SYMBOL + "' || ");
//                    groupStates.append(", ");
//                    blankStates.append(" AND ");
//                }
//                identifierStates.append(c.getCollumn());
//                groupStates.append(c.getCollumn());
//                blankStates.append(String.format("%s = '%s'", c.getCollumn(), Namespace.ENTRY_C_BLANK));
//            }
//
//            identifierStates.append(String.format("|| CASE WHEN %s THEN %s ELSE '+' END", blankStates.toString(), ENTRY_S_ID));
//            groupStates.append(String.format(", CASE WHEN %s THEN %s ELSE 1 END", blankStates.toString(), ENTRY_S_ID));
//
//            List<State> initials = database.executeCollectionQuery(String.format("SELECT %s as %s, GROUP_CONCAT(%s,';') AS %s FROM %s WHERE %s = 1 GROUP BY %s", identifierStates.toString(), ENTRY_C_NAME, ENTRY_S_ID, ENTRY_C_SUB, TABLE_STATES, ENTRY_S_INIT, groupStates.toString()), new StateMapper(this, activeViews));
//
//            return new Graph(this, initials, new ArrayList<>());
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public Graph getGraph(List<Integer> viewIDs) {
//        List<View> activeViews = this.getViews(viewIDs);
//        if (views == null || views.isEmpty() || activeViews.isEmpty()) return this.getGraph();
//        try {
//            StringBuilder identifierStates = null;
//            StringBuilder groupStates = null;
//            StringBuilder blankStates = null;
//            for (View c : activeViews){
//                if (identifierStates == null){
//                    identifierStates = new StringBuilder();
//                    groupStates = new StringBuilder();
//                    blankStates = new StringBuilder();
//                }else{
//                    identifierStates.append(" || '" + C_CONCAT_SYMBOL + "' || ");
//                    groupStates.append(", ");
//                    blankStates.append(" AND ");
//                }
//                identifierStates.append(c.getCollumn());
//                groupStates.append(c.getCollumn());
//                blankStates.append(String.format("%s = '%s'", c.getCollumn(), Namespace.ENTRY_C_BLANK));
//            }
//
//            identifierStates.append(String.format("|| CASE WHEN %s THEN %s ELSE '' END", blankStates.toString(), ENTRY_S_ID));
//            groupStates.append(String.format(", CASE WHEN %s THEN %s ELSE 1 END", blankStates.toString(), ENTRY_S_ID));
//
//            List<State> states = database.executeCollectionQuery(String.format("SELECT %s as %s, GROUP_CONCAT(%s,';') AS %s FROM %s GROUP BY %s", identifierStates, ENTRY_C_NAME, ENTRY_S_ID, ENTRY_C_SUB, TABLE_STATES, groupStates), new StateMapper(this, activeViews));
//
//            Map<String, String> reverseView = database.executeCollectionQuery(String.format("SELECT %s, %s AS %s FROM %s", ENTRY_S_ID, identifierStates, ENTRY_C_NAME, TABLE_STATES), new PairMapper<>(ENTRY_S_ID, ENTRY_C_NAME, String.class, String.class)).stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
//
//            List<Transition> transitions = database.executeCollectionQuery(String.format("SELECT min(%s) AS %s, %s AS %s, %s, GROUP_CONCAT(%s,';') AS %s FROM %s JOIN %s ON %s = %s GROUP BY %s, %s", ENTRY_T_ID, ENTRY_T_ID, identifierStates, ENTRY_T_OUT, ENTRY_T_ACT, ENTRY_T_PROB, ENTRY_T_PROB, TABLE_TRANS, TABLE_STATES, ENTRY_S_ID, ENTRY_T_OUT, groupStates, ENTRY_T_ACT), new TransitionMapper(this, activeViews, reverseView));
//            return new Graph(this, states, transitions);
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }
//    }
//
//
//    public Graph getSubGraph(List<String> stateIDs, List<Integer> viewIDs) {
//        List<View> activeViews = this.getViews(viewIDs);
//        if (views == null || views.isEmpty() || activeViews.isEmpty()) return this.getSubGraph(stateIDs);
//
//        try {
//            StringBuilder identifierStates = null;
//            StringBuilder groupStates = null;
//            StringBuilder blankStates = null;
//            for (View c : activeViews){
//                if (identifierStates == null){
//                    identifierStates = new StringBuilder();
//                    groupStates = new StringBuilder();
//                    blankStates = new StringBuilder();
//                }else{
//                    identifierStates.append(" || '" + C_CONCAT_SYMBOL + "' || ");
//                    groupStates.append(", ");
//                    blankStates.append(" AND ");
//                }
//                identifierStates.append(c.getCollumn());
//                groupStates.append(c.getCollumn());
//                blankStates.append(String.format("%s = '%s'", c.getCollumn(), Namespace.ENTRY_C_BLANK));
//            }
//
//            identifierStates.append(String.format("|| CASE WHEN %s THEN %s ELSE '' END", blankStates.toString(), ENTRY_S_ID));
//            groupStates.append(String.format(", CASE WHEN %s THEN %s ELSE 1 END", blankStates.toString(), ENTRY_S_ID));
//
//            String stateID = stateIDs.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
//
//            List<State> states = database.executeCollectionQuery(String.format("SELECT %s as %s, GROUP_CONCAT(%s,';') AS %s FROM %s GROUP BY %s", identifierStates, ENTRY_C_NAME, ENTRY_S_ID, ENTRY_C_SUB, TABLE_STATES, groupStates), new StateMapper(this, activeViews));
//            Set<String> stringIDs = states.stream().map(State::getId).collect(Collectors.toSet());
//            Map<String, String> reverseView = database.executeCollectionQuery(String.format("SELECT %s, %s AS %s FROM %s", ENTRY_S_ID, identifierStates, ENTRY_C_NAME, TABLE_STATES), new PairMapper<>(ENTRY_S_ID, ENTRY_C_NAME, String.class, String.class)).stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
//
//            List<Transition> transitions = database.executeCollectionQuery(String.format("SELECT min(%s) AS %s, %s AS %s, %s, GROUP_CONCAT(%s,';') AS %s FROM %s JOIN %s ON %s = %s WHERE %s IN (%s) GROUP BY %s, %s", ENTRY_T_ID, ENTRY_T_ID, identifierStates, ENTRY_T_OUT, ENTRY_T_ACT, ENTRY_T_PROB, ENTRY_T_PROB , TABLE_TRANS, TABLE_STATES, ENTRY_S_ID, ENTRY_T_OUT ,ENTRY_T_OUT, stateID, groupStates, ENTRY_T_ACT), new TransitionMapper(this, activeViews, reverseView));
//            List<Transition> transitionsOut = new ArrayList<>();
//            for (Transition t : transitions){
//                Set<String> reach = new HashSet<>(t.getProbabilityDistribution().keySet());
//                stringIDs.forEach(reach::remove);
//                if (reach.isEmpty()){
//                    transitionsOut.add(t);
//                }
//            }
//
//            return new Graph(this, states, transitionsOut);
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }
//    }
//
//    public Graph getOutgoing(List<String> stateIDs, List<Integer> viewIDs) {
//        List<View> activeViews = this.getViews(viewIDs);
//        if (views == null || views.isEmpty() || activeViews.isEmpty()) return this.getOutgoing(stateIDs);
//
//        try{
//            StringBuilder identifierStates = null;
//            StringBuilder groupStates = null;
//            StringBuilder blankStates = null;
//            for (View c : activeViews){
//                if (identifierStates == null){
//                    identifierStates = new StringBuilder();
//                    groupStates = new StringBuilder();
//                    blankStates = new StringBuilder();
//                }else{
//                    identifierStates.append(" || '" + C_CONCAT_SYMBOL + "' || ");
//                    groupStates.append(", ");
//                    blankStates.append(" AND ");
//                }
//                identifierStates.append(c.getCollumn());
//                groupStates.append(c.getCollumn());
//                blankStates.append(String.format("%s = '%s'", c.getCollumn(), Namespace.ENTRY_C_BLANK));
//            }
//
//            identifierStates.append(String.format("|| CASE WHEN %s THEN %s ELSE '' END", blankStates.toString(), ENTRY_S_ID));
//            groupStates.append(String.format(", CASE WHEN %s THEN %s ELSE 1 END", blankStates.toString(), ENTRY_S_ID));
//
//            Map<String, String> reverseView = database.executeCollectionQuery(String.format("SELECT %s, %s AS %s FROM %s", ENTRY_S_ID, identifierStates, ENTRY_C_NAME, TABLE_STATES), new PairMapper<>(ENTRY_S_ID, ENTRY_C_NAME, String.class, String.class)).stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
//
//            String stateID = stateIDs.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
//
//            List<Transition> transitions = database.executeCollectionQuery(String.format("SELECT min(%s) AS %s, %s AS %s, %s, GROUP_CONCAT(%s,';') AS %s FROM %s JOIN %s ON %s = %s WHERE %s IN (%s) GROUP BY %s, %s", ENTRY_T_ID, ENTRY_T_ID, identifierStates, ENTRY_T_OUT, ENTRY_T_ACT, ENTRY_T_PROB, ENTRY_T_PROB , TABLE_TRANS, TABLE_STATES, ENTRY_S_ID, ENTRY_T_OUT ,ENTRY_T_OUT, stateID, groupStates, ENTRY_T_ACT), new TransitionMapper(this, activeViews, reverseView));
//
//            Set<String> statesOfInterest = new HashSet<>();
//            for (Transition t : transitions) {
//                statesOfInterest.add(t.getSource());
//                statesOfInterest.addAll(new ArrayList<>(t.getProbabilityDistribution().keySet()));
//            }
//            String stateString = statesOfInterest.stream().map(s -> String.format("'%s'", s)).collect(Collectors.joining(","));
//
//            List<State> states = database.executeCollectionQuery(String.format("SELECT %s as %s, GROUP_CONCAT(%s,';') AS %s FROM %s WHERE %s IN (%s) GROUP BY %s", identifierStates, ENTRY_C_NAME, ENTRY_S_ID, ENTRY_C_SUB, TABLE_STATES, ENTRY_C_NAME, stateString, groupStates), new StateMapper(this, activeViews));
//            return new Graph(this, states, transitions);
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }
//    }

    // ---Pane Functions---

    public Pane retrievePanes(List<String> paneIDs){
        if(!paneTableExists()) return null;
        Pane result = null;
        for(String paneID : paneIDs){
            Optional<Pane> pane = this.database.executeLookupQuery(String.format("SELECT * FROM %s WHERE %s = '%s'", TABLE_PANES, ENTRY_P_ID, paneID), new PaneMapper());
            if(pane.isPresent()){
                if (result == null){
                    result = pane.get();
                }else{
                    result.join(pane.get());
                }
            }else{
                throw new RuntimeException("Pane not found");
            }
        }
        return result;
    }

    public void storePane(String paneID, String content) throws SQLException {
        if(!paneTableExists()) createPaneTable();
        this.database.execute(String.format("INSERT OR REPLACE INTO %s (%s,%s) VALUES(%s,'%s')", TABLE_PANES, ENTRY_P_ID, ENTRY_P_CONTENT, paneID, content));
    }

    private boolean paneTableExists() {
        return this.database.question(String.format("SELECT name FROM sqlite_schema WHERE type='table' AND name='%s'", TABLE_PANES));
    }

    private void createPaneTable() throws SQLException {
        this.database.execute(String.format("CREATE TABLE %s (%s INTEGER PRIMARY KEY NOT NULL, %s TEXT)", TABLE_PANES, ENTRY_P_ID, ENTRY_P_CONTENT));
    }

    public List<String> storedPanes(){
        return this.database.executeCollectionQuery(String.format("SELECT %s FROM %s", ENTRY_P_ID, TABLE_PANES), String.class);
    }

    /*
    public Graph getIncoming(long stateID) {
        List<State> states = database.executeCollectionQuery(String.format("SELECT %s.* FROM %s LEFT JOIN %s ON %s.%s = %s.%s WHERE %s.%s = %s OR %s.%s = %s", TABLE_STATES, TABLE_STATES, TABLE_TRANS, TABLE_STATES, ENTRY_S_ID, TABLE_TRANS, ENTRY_T_OUT, TABLE_TRANS, ENTRY_T_IN, stateID, TABLE_STATES, ENTRY_S_ID, stateID), new StateMapper(parent,0));
        List<Transition> transitions = database.executeCollectionQuery(String.format("SELECT *, GROUP_CONCAT(%s || ':' || %s, ';') as %s FROM %s WHERE %s == %s GROUP BY %s, %s",ENTRY_T_IN, ENTRY_T_PROB, ENTRY_T_MAP, TABLE_TRANS, ENTRY_T_IN, stateID, ENTRY_T_OUT, ENTRY_T_ACT), new TransitionMapper());
        return new Graph(states, transitions);
    }*/

    // Views

//    public void createView(ViewType type, List<String> parameters) throws Exception {
//        int viewID = views.size();
//
//
//        //Filters a few Control Commands encode as view types
//        switch(type){
//            case Clear: {
//                if (!views.isEmpty()) {
//                    clearViews();
//                }
//            }
//            case Remove: {
//                if (!views.isEmpty()) {
//                    if (parameters.size() != 1) throw new Exception("need to provide number of View you want to remove!");
//                    int n = Integer.parseInt(parameters.get(0));
//                    if (n < 0 || n >= views.size()) throw new Exception("Could not find View number " + n);
//                    View viewN = views.get(n);
//                    getDatabase().execute(String.format("ALTER TABLE %s DROP COLUMN %s", getStateTableName(), viewN.getCollumn()));
//                    views.remove(viewN);
//                    organizeIds();
//                }
//            }
//            default: {
//                View v = View.createView(this, type, parameters, viewID);
//                if (views.contains(v)) throw new Exception("View already exists");
//                v.buildView();
//                views.add(v);
//
//            }
//        }
//    }

//    public void organizeIds() {
//        long i = 0;
//        for (View view : views) {
//            view.setId(i);
//            i++;
//        }
//    }

//    public ViewType columnToViewType(String columnName) {
//        for (ViewType viewType : ViewType.values()) {
//            if (columnName.contains(viewType.name())) {
//                return viewType;
//            }
//        }
//        return null;
//    }
//
//    public List<View> getViews(){
//        return this.views;
//    }
//
//    public List<View> getViews(List<Integer> ids){
//        return views.stream().filter(v -> ids.contains((int) v.getId())).collect(Collectors.toList());
//    }

    // adds DummyView internally for each View in DB for displaying and modifying views
    // can also
    // * clear all views on startup
    // * build views on all models
    //      -> Clear needed before!
    //      -> Caution: Model() crashes if too much time needed by buildView()
//    private void makeViewDbAndViewInternalConsistent(){
//        try {
//            //insert dummy views into "List<View> views" for each views that is still in DB
//            List<String> viewsInDb = database
//                    .executeCollectionQuery(String.format("SELECT name FROM pragma_table_info('%s')", TABLE_STATES))
//                    .stream()
//                    .flatMap(map -> map.values().stream().map(String::valueOf))
//                    .filter(columnName -> columnName.contains("View"))
//                    .collect(Collectors.toList());
//            int viewId = 0;
//            for (String viewString : viewsInDb) {
//                ViewType viewType = columnToViewType(viewString);
//                views.add(new DummyView(this, viewId, viewType, viewString));
//                viewId++;
//            }
//        } finally {}
//    }
//
//    private void clearViews()  {
//        if (views.isEmpty()) {
//            return;
//        }
//        List<String> clearTableQry = List.of();
//        try {
//            // create SQL String for each View and execute them with executeBatch
//            clearTableQry = views.stream()
//                            .map(view -> String.format("ALTER TABLE %s DROP COLUMN %s", getStateTableName(), view.getCollumn()))
//                            .collect(Collectors.toList());
//            getDatabase().executeBatch(clearTableQry);
//        } catch (Exception e) {
//            if (clearTableQry.isEmpty()) {
//                throw new RuntimeException("clearTableQry in project.clearViews() was empty!", e);
//            }
//            else {
//                throw new RuntimeException(e);
//            }
//
//        }
//        views = new ArrayList<>();
//    }
//
//    public void removeViewFromDbByColName(String columnNameView) {
//        try {
//            getDatabase().execute(String.format("ALTER TABLE %s DROP COLUMN %s;", TABLE_STATES, columnNameView));
//            List<View> viewsWithName = views
//                    .stream()
//                    .filter(view -> view.getCollumn().equals(columnNameView))
//                    .collect(Collectors.toList());
//            if (viewsWithName.size() == 0) {
//                throw new Exception("No View with that name found in model.views");
//            }
//            if (viewsWithName.size() > 1) {
//                System.out.println("There is more than one views with this name in model.views! The View with the lowest id, will be removed.");
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }



//    public void removeFromViews(View view) {
//        int i = views.indexOf(view);
//        if (i > -1) views.remove(i);
//    }
}
