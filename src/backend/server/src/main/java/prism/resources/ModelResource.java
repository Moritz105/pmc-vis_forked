package prism.resources;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.setup.Environment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import prism.api.Message;
import prism.server.PRISMServerConfiguration;
import prism.server.TaskManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;


/**
 * Main Resource of the Application
 */
@Path("/{project_id}")
@Produces(MediaType.APPLICATION_JSON)
public class ModelResource extends Resource {

    public ModelResource(Environment environment, PRISMServerConfiguration configuration, TaskManager tasks){
        super(environment, configuration, tasks);
    }

    @GET
    @Timed
    @Operation(summary = "Returns entire graph")
    public Response createUpperGraph(
            @Parameter(description = "identifier of project")
            @PathParam("project_id") String projectID,
            @QueryParam("version") Optional<String> version
    ) {
        try{
            if (!tasks.containsProject(projectID)) return error(new Message(String.format("Project %s not found", projectID)));
            if (version.isPresent()) return ok(tasks.getProject(projectID).getGraph(version.get()));
            return ok(tasks.getProject(projectID).getGraph());
        } catch (Exception e) {
            return error(e);
        }
    }

    @Path("/node:{id}")
    @GET
    @Timed(name="node")
    @Operation(summary = "Returns single node", description = "Returns single Node Object with identifier 'id'")
    public Response getNode(
            @Parameter(description = "identifier of project")
            @PathParam("project_id") String projectID,
            @Parameter(description = "Identifier of target node", required = true)
            @PathParam("id") String nodeID,
            @QueryParam("version") Optional<String> version
    ) {
        if(version.isPresent()) return ok(tasks.getProject(projectID).getState(nodeID, version.get()));
        return ok(tasks.getProject(projectID).getState(nodeID));
    }

    @Path("/subgraph")
    @GET
    @Timed(name="subgraph")
    @Operation(summary = "Returns interconnected subgraph of all given nodes", description = "Returns single Node Object with identifier 'id'")
    public Response getSubGraph(
            @Parameter(description = "identifier of project") @PathParam("project_id") String projectID,
            @Parameter(description = "Identifier of target node", required = true) @QueryParam("id") List<String> nodeIDs,
            @QueryParam("version") Optional<String> version
    ) {
        refreshProject(projectID);
        if(version.isPresent()) return ok(tasks.getProject(projectID).getSubGraph(nodeIDs, version.get()));
        return ok(tasks.getProject(projectID).getSubGraph(nodeIDs));
    }

    @Path("/reset")
    @GET
    @Timed(name="subgraph")
    @Operation(summary = "Returns interconnected subgraph of all given nodes", description = "Returns single Node Object with identifier 'id'")
    public Response resetGraph(
            @Parameter(description = "identifier of project") @PathParam("project_id") String projectID,
            @Parameter(description = "Identifier of target node", required = true) @QueryParam("id") List<String> nodeIDs,
            @Parameter(description = "Identifier of target node that is not explored", required = true) @QueryParam("idu") List<String> unexploredNodeIDs,
            @QueryParam("version") Optional<String> version
    ) {
        refreshProject(projectID);
        if(version.isPresent()) return ok(tasks.getProject(projectID).resetGraph(nodeIDs, unexploredNodeIDs, version.get()));
        return ok(tasks.getProject(projectID).resetGraph(nodeIDs, unexploredNodeIDs));
    }

    @Path("/outgoing")
    @GET
    @Timed(name="outgoing")
    @Operation(summary = "Returns all outgoing edges", description = "Returns all edges starting in state 'id'")
    public Response getOutgoing(
            @Parameter(description = "identifier of project") @PathParam("project_id") String projectID,
            @Parameter(description = "Identifier of target node", required = true) @QueryParam("id") List<String> nodeIDs,
            @QueryParam("version") Optional<String> version
    ) {
        refreshProject(projectID);
        if (!tasks.containsProject(projectID)) return error(String.format("project %s not open", projectID));
        if(version.isPresent()) return ok(tasks.getProject(projectID).getOutgoing(nodeIDs, version.get()));
        return ok(tasks.getProject(projectID).getOutgoing(nodeIDs));
    }

    @Path("/initial")
    @GET
    @Timed(name="initial")
    @Operation(summary = "Returns all initial nodes", description = "Returns all nodes that are marked as initial states")
    public Response getInitial(
            @Parameter(description = "identifier of project")
            @PathParam("project_id") String projectID,
            @QueryParam("version") String version
    ) {
        refreshProject(projectID);
        return ok(tasks.getProject(projectID).getInitialNodes(tasks.getProject(projectID).defaultVersion()));
    }

    @Path("/compare")
    @GET
    @Timed(name="compare")
    @Operation(summary = "Returns all initial nodes of second model", description = "Returns all nodes that are marked as initial states for second model")
    public Response getCompareInitial(
            @Parameter(description = "identifier of project")
            @PathParam("project_id") String projectID,
            @QueryParam("version") String version
    ) {
        refreshProject(projectID);
        return ok(tasks.getProject(projectID).getInitialNodes(tasks.getProject(projectID).getSecondV()));

    }

    @Path("/files")
    @GET
    @Timed(name="initial")
    @Operation(summary = "Returns the project files", description = "Returns the filename of all files that are currently placed in the project")
    public Response getFileStructure(
            @Parameter(description = "identifier of project")
            @PathParam("project_id") String projectID
    ) {
        refreshProject(projectID);
        if (!tasks.containsProject(projectID)) return ok(new ArrayList<String>());
        return ok(tasks.getProject(projectID).getFileStructure());
    }

    @Path("/file:{file}")
    @GET
    @Timed(name="initial")
    @Operation(summary = "Returns the project files", description = "Returns the filename of all files that are currently placed in the project")
    public Response getFileContent(
            @Parameter(description = "identifier of project")
            @PathParam("project_id") String projectID,
            @Parameter(description = "id of the targeted file")
            @PathParam("file") int fileID
    ) {
        refreshProject(projectID);
        return ok(tasks.getProject(projectID).getFileContent(fileID));
    }

    @Path("/fingerprints")
    @GET
    @Timed(name="fingerprints")
    @Operation(summary = "Returns fingerprints for what if analysis", description = "Returns data for comparison of 2 graphs")
    public Response getFingerprints (
            @Parameter(description = "identifier of project")
            @PathParam("project_id") String projectID
    ) throws Exception{
        refreshProject(projectID);

        return ok(tasks.getProject(projectID).getFingerprints());

    }

    @Path("/coloredDiff")
    @GET
    @Timed(name="coloredDiff")
    @Operation(summary = "Returns coloring for differences", description = "Returns different nodes of 2 graphs")
    public Response getColoredDiff (
            @Parameter(description = "identifier of project")
            @PathParam("project_id") String projectID
    ) throws Exception{
        refreshProject(projectID);

        return ok(tasks.getProject(projectID).getColoring());

    }

//    @Path("/view:{type}")
//    @GET
//    @Operation(summary = "Creates a views", description = "Creates a new view in the project")
//    public Response createView(
//            @Parameter(description = "identifier of project")
//            @PathParam("project_id") String projectID,
//            @PathParam("type") ViewType type,
//            @QueryParam("param") List<String> parameters,
//            @QueryParam("limit_expression") String expression,
//            @QueryParam("limit_data") String data
//    ) {
//        try {
//            tasks.getProject(projectID).createView(type, parameters);
//            return ok("Created View");
//
//        } catch (Exception e) {
//            System.out.println(e.getCause());
//            e.printStackTrace();
//            return error(e);
//        }
//    }
}
