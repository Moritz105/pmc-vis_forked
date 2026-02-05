package prism.cli;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.jdbi.v3.core.Jdbi;

import prism.core.Project;
import prism.db.Database;
import prism.server.PRISMServerConfiguration;
import prism.server.TaskManager;
import prism.core.Diff;
import prism.core.Utility.Timer;
import prism.core.Model;
import prism.*;
import prism.api.VariableInfo;
import prism.core.Property.Property;
import prism.core.Utility.Prism.Updater;
import prism.db.Batch;
import prism.server.Task;
import prism.api.Graph;
import prism.db.Database;

import javax.ws.rs.client.Client;
import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import parser.ast.ModulesFile;
import java.util.*;
import java.util.stream.Collectors;


public class whatiftest extends ConfiguredCommand<PRISMServerConfiguration> {

    private final Prism what;
    public whatiftest() {

        super("test", "testing reasons");
        this.what = new Prism(new PrismPrintStreamLog(System.out));
    }

    @Override
    public void configure(Subparser subparser){
        super.configure(subparser);

        subparser.addArgument("-st", "--first")
                .dest("first_model")
                .type(String.class)
                .required(true)
                .help("The first prism model");

        subparser.addArgument("-nd", "--second")
                .dest("second_model")
                .type(String.class)
                .required(true)
                .help("The second prism model");

        subparser.addArgument("-p", "--properties")
                .dest("properties")
                .type(String.class)
                .required(true)
                .help("The property file");

    }
    

    @Override 
    public void run(Bootstrap bootstrap, Namespace namespace, PRISMServerConfiguration configuration) throws Exception {

        final JdbiFactory factory = new JdbiFactory();
        DataSourceFactory dbfactory = configuration.getDataSourceFactory();
        String projectID = "temp";
        String rootDir = configuration.getPathTemplate();

        try {
            Files.createDirectory(Paths.get(String.format("%s/%s", rootDir, projectID)));
        } catch (FileAlreadyExistsException e) {
            System.out.println("temp was not deleted");
            removeDir(new File(String.format("%s/%s", rootDir, projectID)));
            Files.createDirectory(Paths.get(String.format("%s/%s", rootDir, projectID)));
        }

        File model_1 = new File(String.format("%s/%s/%s_1", rootDir, projectID, prism.core.Namespace.PROJECT_MODEL));
        File model_2 = new File(String.format("%s/%s/%s_2", rootDir, projectID, prism.core.Namespace.PROJECT_MODEL));
        File propertyFile = new File(String.format("%s/%s/%s", rootDir, projectID, "properties.props"));

        copyFile(new File((String) namespace.get("properties")), propertyFile);
        dbfactory.setUrl(String.format("jdbc:sqlite:%s/%s/%s", configuration.getPathTemplate(), projectID, prism.core.Namespace.DATABASE_FILE));


        final Jdbi jdbi = factory.build(new Environment("temp"), dbfactory, projectID);
        Database database = new Database(jdbi, configuration.getDebug());

        TaskManager taskManager = new TaskManager();

        Project project = new Project(projectID, configuration.getPathTemplate(), taskManager, database, configuration.getCUDDMaxMem(), configuration.getIterations(), configuration.getDebug());

        copyFile(new File((String) namespace.get("first_model")), model_1);

        copyFile(new File((String) namespace.get("second_model")), model_2);
        project.createModel(model_1, "1");
        project.createModel(model_2, "2");

        // 1. Modelle laden
        Model m1 = project.getModel("1");
        Model m2 = project.getModel("2");

        // 2. Diff-Instanz erstellen
        Diff diff = new Diff(project, m1, m2);
        System.out.println("--- Starte Diff-Validierung ---");

        // 3. Echte Daten ziehen (korrigierter Zugriff)
        // Wir holen uns die Liste aller Zustände vom Graph
        List<prism.api.State> statesList = m1.getModelParser().getGraph().getStates();

        if (statesList != null && !statesList.isEmpty()) {
            // Wir nehmen den Namen des ersten verfügbaren Zustands
            String realStateName = statesList.get(0).toString();
            String normalized = m1.getModelParser().normalizeStateName(realStateName);

            // ID-Mapping Check
            int id = diff.getOrCreateId(normalized);

            // Test: Liefert der un-normalisierte Name dieselbe ID?
            if (id == diff.getOrCreateId(realStateName)) {
                System.out.println("SUCCESS: ID-Mapping korrekt für Zustand: " + normalized + " (ID: " + id + ")");
            } else {
                System.out.println("ERROR: ID-Mapping Inkonsistenz!");
            }
        } else {
            System.out.println("WARNUNG: Graph hat keine Zustände.");
        }

        // 4. Den Paige-Tarjan Algorithmus ausführen
        Map<String, String> colors = diff.matchNodes();

        // 5. Ergebnisse ausgeben
        System.out.println("--- DIFF STATISTIK ---");

// Da colors nun Map<ID, Farbe> ist, gruppieren wir nach den Werten (Farben)
        Map<String, List<String>> groupedByColor = colors.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));

        groupedByColor.forEach((color, states) -> {
            // 'none' ignorieren wir meistens in der Statistik, um das Wichtige zu sehen
            if (!"none".equalsIgnoreCase(color)) {
                System.out.println(color.toUpperCase() + ": " + states.size() + " Elemente.");
                System.out.println("   Beispiele: " + states.stream().limit(5).collect(Collectors.toList()));
            }
        });

// Kurze Zusammenfassung für 'none'
        int neutralCount = groupedByColor.getOrDefault("none", Collections.emptyList()).size();
        System.out.println("NEUTRAL (unverändert): " + neutralCount + " Elemente.");

        // 6. Aufräumen
        project.removeFiles();
    }
     private void copyFile(File inFile, File outFile){
        try (
                InputStream in = new BufferedInputStream(
                        new FileInputStream(inFile));
                OutputStream out = new BufferedOutputStream(
                        new FileOutputStream(outFile))) {

            byte[] buffer = new byte[1024];
            int lengthRead;
            while ((lengthRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, lengthRead);
                out.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeDir(File dir) throws Exception {
        if (dir.exists()) {
            for (File file : Objects.requireNonNull(dir.listFiles())) {
                if (file.isDirectory()){
                    removeDir(file);
                }else{
                    file.delete();
                }
            }
            dir.delete();
        }
    }

    private ModulesFile convertModulesFile (File modelFile, Project project) throws Exception{
        try (prism.core.Utility.Timer parse = new prism.core.Utility.Timer("parsing project", project.getLog())) {
            ModulesFile modulesFile = what.parseModelFile(modelFile, ModelType.MDP);
            //prism.loadPRISMModel(modulesFile);
            return modulesFile;
        } catch (FileNotFoundException e) {
            throw new Exception(e.getMessage());
        }
    }
}